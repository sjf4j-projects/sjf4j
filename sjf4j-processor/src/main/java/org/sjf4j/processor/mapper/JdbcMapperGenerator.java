package org.sjf4j.processor.mapper;

import org.sjf4j.annotation.mapper.jdbc.JdbcMapperOptions;
import org.sjf4j.annotation.mapper.jdbc.SingleResultPolicy;
import org.sjf4j.annotation.mapper.jdbc.ColumnProjectionPolicy;
import org.sjf4j.annotation.mapper.ArrayPolicy;
import org.sjf4j.annotation.mapper.ObjectPolicy;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.processor.GeneratedClass;
import org.sjf4j.processor.GeneratorUtil;
import org.sjf4j.processor.NameAllocator;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.SourceWriter;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Emits direct {@code ResultSet} mappers at the processor boundary. Unlike compiled node mapping,
 * this generator does not traverse source nodes: generated rows read JDBC columns directly and use
 * only the small shared scalar-conversion helpers where needed.
 */
public final class JdbcMapperGenerator {
    private final ProcessorContext ctx;
    private NameAllocator helperNames;
    private String primitiveNullHelper;
    private String temporalTypeHelper;
    private MappingCreators creators;

    public JdbcMapperGenerator(ProcessorContext ctx) {
        this.ctx = ctx;
    }

    public void generate(TypeElement iface) {
        primitiveNullHelper = null;
        temporalTypeHelper = null;
        if (!validateInterface(iface)) return;
        creators = new MappingCreators(ctx, iface, this::error);
        if (!creators.valid()) return;
        for (Element member : iface.getEnclosedElements()) {
            if (member.getKind() == ElementKind.METHOD && member.getModifiers().contains(Modifier.ABSTRACT)
                    && !creators.validateMethod((ExecutableElement) member)) return;
        }

        GeneratedClass out = new GeneratedClass(ctx, iface, GeneratorUtil.COMPILED_IMPL_POSTFIX);
        helperNames = new NameAllocator();
        for (Element member : ctx.elements.getAllMembers(iface)) {
            if (member.getKind() == ElementKind.METHOD) {
                helperNames.reserve(member.getSimpleName().toString());
            }
        }

        Set<String> names = new HashSet<String>();
        for (Element member : iface.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;
            if (!member.getModifiers().contains(Modifier.ABSTRACT)) continue;

            ExecutableElement method = (ExecutableElement) member;
            String name = method.getSimpleName().toString();
            if (!names.add(name)) {
                error(method, "@CompiledJdbcMapper does not support overloaded mapper method names");
                return;
            }
            if (!generateMethod(iface, method, method.getReturnType(), out)) return;
        }
        out.emit();
    }

    private boolean validateInterface(TypeElement iface) {
        if (!iface.getTypeParameters().isEmpty()) {
            error(iface, "@CompiledJdbcMapper interfaces must not declare type parameters");
            return false;
        }
        for (Element member : iface.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;

            ExecutableElement method = (ExecutableElement) member;
            if (!method.getTypeParameters().isEmpty()) {
                error(method, "@CompiledJdbcMapper methods must not declare type parameters");
                return false;
            }
        }
        for (Element member : ctx.elements.getAllMembers(iface)) {
            if (member.getKind() != ElementKind.METHOD) continue;
            if (!member.getModifiers().contains(Modifier.ABSTRACT)) continue;
            if (member.getEnclosingElement().equals(iface)) continue;
            error(member, "Inherited abstract mapper methods are not supported; declare methods directly");
            return false;
        }
        return true;
    }

    private boolean generateMethod(TypeElement iface, ExecutableElement method, TypeMirror returnType, GeneratedClass out) {
        if (!jdbcAnnotationsOnly(method)) return false;
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            error(method, "@CompiledJdbcMapper does not support update methods");
            return false;
        }
        boolean currentRow = method.getParameters().size() == 2;
        if ((method.getParameters().size() != 1 && !currentRow)
                || !is(method.getParameters().get(0).asType(), "java.sql.ResultSet")
                || (currentRow && method.getParameters().get(1).asType().getKind() != TypeKind.INT)) {
            error(method, "@CompiledJdbcMapper methods must have ResultSet or ResultSet, int parameters");
            return false;
        }

        TypeMirror row = returnType;
        boolean list = false;
        if (isDeclared(row, "java.util.List")) {
            List<? extends TypeMirror> arguments = ((DeclaredType) row).getTypeArguments();
            if (arguments.size() != 1 || arguments.get(0).getKind() != TypeKind.DECLARED) {
                error(method, "@CompiledJdbcMapper List results must declare an element type");
                return false;
            }
            list = true;
            row = arguments.get(0);
        }
        if (currentRow && list) {
            error(method, "@CompiledJdbcMapper current-row methods do not support List results");
            return false;
        }

        JdbcMapperOptions options = method.getAnnotation(JdbcMapperOptions.class);
        if (options != null) {
            if (list && options.singleResult() != SingleResultPolicy.FAIL_ON_MULTIPLE) {
                error(method, "@JdbcMapperOptions.singleResult is supported only on non-List @CompiledJdbcMapper methods");
                return false;
            }
            if (currentRow && options.singleResult() != SingleResultPolicy.FAIL_ON_MULTIPLE) {
                error(method, "@JdbcMapperOptions.singleResult does not apply to current-row methods");
                return false;
            }
        }

        boolean map = isMap(row);
        if (options != null && map && options.columnProjection() != ColumnProjectionPolicy.REQUIRE_ALL) {
            error(method, "@JdbcMapperOptions.columnProjection is supported only on POJO @CompiledJdbcMapper results");
            return false;
        }
        if (map) {
            for (Mapping mapping : method.getAnnotationsByType(Mapping.class)) {
                if (!autoMarker(mapping)) {
                    error(method, "@CompiledJdbcMapper Map<String,Object> results do not support @Mapping");
                    return false;
                }
            }
        }

        MappingCreators.Match creator = map ? null : creators.forMethod(method, row);
        if (!map && !creators.valid()) return false;
        ColumnProjectionPolicy columnPolicy = options == null ? ColumnProjectionPolicy.REQUIRE_ALL : options.columnProjection();
        RowPlan plan = map ? null : plan(iface, method, creator == null ? row : creator.type, creator, columnPolicy == ColumnProjectionPolicy.PRESENT_ONLY);
        if (!map && plan == null) return false;
        if (!map && columnPolicy == ColumnProjectionPolicy.PRESENT_ONLY && plan.constructor) {
            error(method, "@JdbcMapperOptions.columnProjection=PRESENT_ONLY requires a mutable POJO target");
            return false;
        }

        final TypeMirror finalRow = row;
        final RowPlan finalPlan = plan;
        final boolean finalList = list;
        final SingleResultPolicy policy = options == null
                ? SingleResultPolicy.FAIL_ON_MULTIPLE
                : options.singleResult();
        final String rowHelper = helperNames.local(method.getSimpleName() + "_Row");
        out.addMethod(writer -> emitMethod(writer, method, returnType, finalRow, finalPlan, map, finalList, policy,
                currentRow, rowHelper));
        if (map) {
            out.addHelper(writer -> emitMapRow(writer, rowHelper, finalList || currentRow));
        } else {
            // GeneratedClass emits these helpers once at class level, after all mapper methods.
            if (usesPrimitive(plan) && primitiveNullHelper == null) {
                primitiveNullHelper = helperNames.local("primitiveNull");
                out.addHelper(this::emitPrimitiveNullHelper);
            }
            if (usesTemporal(plan) && temporalTypeHelper == null) {
                temporalTypeHelper = helperNames.local("temporalType");
                out.addHelper(this::emitTemporalTypeHelper);
            }
            out.addHelper(writer -> emitRow(writer, finalRow, finalPlan, rowHelper, finalList || finalPlan.presentOnly));
        }
        return true;
    }

    private boolean jdbcAnnotationsOnly(ExecutableElement method) {
        for (AnnotationMirror annotation : method.getAnnotationMirrors()) {
            TypeElement element = (TypeElement) annotation.getAnnotationType().asElement();
            String name = element.getQualifiedName().toString();
            if (name.equals("org.sjf4j.annotation.mapper.Mapping")
                    || name.equals("org.sjf4j.annotation.mapper.Mappings")
                    || name.equals("org.sjf4j.annotation.mapper.MapperOptions")
                    || name.equals(JdbcMapperOptions.class.getName())
                    || name.equals("org.sjf4j.annotation.mapper.MappingCreator")
                    || name.equals("org.sjf4j.annotation.mapper.MappingCreators")) {
                continue;
            }
            if (name.startsWith("org.sjf4j.annotation.mapper.")) {
                String simpleName = name.substring(name.lastIndexOf('.') + 1);
                error(method, "@CompiledJdbcMapper does not support " + simpleName);
                return false;
            }
        }
        return true;
    }

    /**
     * Emits cursor-consuming entry-point code. Generated methods do not close caller-owned JDBC
     * resources. Driver {@code SQLException}s are translated to {@code BindingException}.
     */
    private void emitMethod(SourceWriter writer, ExecutableElement method, TypeMirror returnType, TypeMirror row, RowPlan plan,
                                boolean map, boolean list, SingleResultPolicy policy,
                                 boolean currentRow,
                             String rowHelper) {
        NameAllocator locals = new NameAllocator();
        locals.reserve("rs");
        String rows = locals.local("rows");
        String value = locals.local("value");
        writer.line("");
        writer.line("@Override");
        writer.line("public " + returnType + " " + method.getSimpleName()
                + "(java.sql.ResultSet rs" + (currentRow ? ", int rowNum" : "") + ") {");
        writer.indent();
        writer.line("if (rs == null) return null;");
        if (currentRow) {
            writer.line("try {"); writer.indent();
            if (map) {
                writer.line("java.sql.ResultSetMetaData meta = rs.getMetaData();");
                emitJdbcColumns(writer, "meta", "columns");
                writer.line("return " + rowHelper + "(rs, columns);");
            } else if (plan.presentOnly) {
                writer.line("java.sql.ResultSetMetaData meta = rs.getMetaData();");
                emitPresentIndexes(writer, plan);
                writer.line("return " + indexedCall(rowHelper, plan) + ";");
            } else writer.line("return " + rowHelper + "(rs);");
            writer.dedent();
            writer.line("} catch (java.sql.SQLException e) {");
            writer.indent();
            writer.line("throw new org.sjf4j.exception.BindingException(\"Failed to map JDBC result set\", e);");
            writer.dedent();
            writer.line("}");
            writer.dedent(); writer.line("}"); return;
        }
        boolean wrapSQLException = true;
        writer.line("try {"); writer.indent();
        if (list) {
            writer.line("java.util.List<" + row + "> " + rows
                    + " = new java.util.ArrayList<" + row + ">();");
            writer.line("if (!rs.next()) return " + rows + ";");
            if (map) {
                String meta = locals.local("meta");
                String count = locals.local("count");
                String columns = locals.local("columns");
                String index = locals.local("index");
                writer.line("java.sql.ResultSetMetaData " + meta + " = rs.getMetaData();");
                emitJdbcColumns(writer, meta, columns, count, index);
                writer.line("do {");
                writer.indent();
                writer.line(rows + ".add(" + rowHelper + "(rs, " + columns + "));");
                writer.dedent();
                writer.line("} while (rs.next());");
            } else {
                // Resolve columns once per list result set; row helpers then use driver-resolved indexes.
                List<String> columns = new ArrayList<String>(plan.properties.size());
                if (plan.presentOnly) {
                    writer.line("java.sql.ResultSetMetaData meta = rs.getMetaData();");
                    emitPresentIndexes(writer, plan);
                    for (int index = 0; index < plan.properties.size(); index++) columns.add("column" + index);
                } else for (int index = 0; index < plan.properties.size(); index++) {
                    Property property = plan.properties.get(index);
                    String column = locals.local("column" + index);
                    columns.add(column);
                    writer.line("int " + column + " = rs.findColumn(\"" + GeneratorUtil.escape(property.column) + "\");");
                }
                writer.line("do {");
                writer.indent();
                StringBuilder call = new StringBuilder(rows).append(".add(").append(rowHelper)
                        .append("(rs");
                for (int index = 0; index < plan.properties.size(); index++) {
                    call.append(", ").append(columns.get(index));
                }
                writer.line(call.append("));").toString());
                writer.dedent();
                writer.line("} while (rs.next());");
            }
            writer.line("return " + rows + ";");
        } else {
            // A single result consumes its first row and, for the default policy, its second-row check.
            writer.line("if (!rs.next()) return null;");
            if (map) {
                writer.line(row + " " + value + " = " + rowHelper + "(rs);");
            } else if (plan.presentOnly) {
                writer.line("java.sql.ResultSetMetaData meta = rs.getMetaData();");
                emitPresentIndexes(writer, plan);
                writer.line(row + " " + value + " = " + indexedCall(rowHelper, plan) + ";");
            } else writer.line(row + " " + value + " = " + rowHelper + "(rs);");
            if (policy == SingleResultPolicy.FAIL_ON_MULTIPLE) {
                writer.line("if (rs.next()) {");
                writer.indent();
                writer.line("throw new org.sjf4j.exception.BindingException("
                        + "\"Expected one JDBC row but found multiple\");");
                writer.dedent();
                writer.line("}");
            }
            writer.line("return " + value + ";");
        }
        if (wrapSQLException) {
            writer.dedent();
            writer.line("} catch (java.sql.SQLException e) {");
            writer.indent();
            writer.line("throw new org.sjf4j.exception.BindingException("
                    + "\"Failed to map JDBC result set\", e);");
            writer.dedent();
            writer.line("}");
        }
        writer.dedent();
        writer.line("}");
    }

    private void emitMapRow(SourceWriter writer, String rowHelper, boolean indexed) {
        writer.line("");
        writer.line("private java.util.Map<String, Object> " + rowHelper
                + "(java.sql.ResultSet rs" + (indexed ? ", String[] columns" : "")
                + ") throws java.sql.SQLException {");
        writer.indent();
        // List<Map> callers pass columns cached from metadata once; single maps obtain them for their row.
        if (!indexed) writer.line("java.sql.ResultSetMetaData meta = rs.getMetaData();");
        writer.line("java.util.Map<String, Object> value = new java.util.LinkedHashMap<String, Object>();");
        writer.line(indexed
                ? "for (int index = 0; index < columns.length; index++) {"
                : "for (int index = 1, count = meta.getColumnCount(); index <= count; index++) {");
        writer.indent();
        writer.line(indexed
                ? "value.put(columns[index], rs.getObject(index + 1));"
                : "value.put(meta.getColumnLabel(index), rs.getObject(index));");
        writer.dedent();
        writer.line("}");
        writer.line("return value;");
        writer.dedent();
        writer.line("}");
    }

    private void emitJdbcColumns(SourceWriter writer, String meta, String columns) {
        emitJdbcColumns(writer, meta, columns, "count", "index");
    }

    private void emitJdbcColumns(SourceWriter writer, String meta, String columns, String count, String index) {
        writer.line("int " + count + " = " + meta + ".getColumnCount();");
        writer.line("String[] " + columns + " = new String[" + count + "];");
        writer.line("for (int " + index + " = 0; " + index + " < " + count + "; " + index + "++) {");
        writer.indent();
        writer.line(columns + "[" + index + "] = " + meta + ".getColumnLabel(" + index + " + 1);");
        writer.dedent();
        writer.line("}");
    }

    private void emitPresentIndexes(SourceWriter writer, RowPlan plan) {
        for (int index = 0; index < plan.properties.size(); index++) {
            writer.line("int column" + index + " = -1;");
        }
        writer.line("for (int jdbcIndex = 1, jdbcCount = meta.getColumnCount(); jdbcIndex <= jdbcCount; jdbcIndex++) {");
        writer.indent();
        writer.line("String jdbcColumn = meta.getColumnLabel(jdbcIndex);");
        for (int index = 0; index < plan.properties.size(); index++) {
            writer.line("if (column" + index + " == -1 && jdbcColumn.equalsIgnoreCase(\""
                    + GeneratorUtil.escape(plan.properties.get(index).column) + "\")) column" + index + " = jdbcIndex;");
        }
        writer.dedent();
        writer.line("}");
    }

    private String indexedCall(String rowHelper, RowPlan plan) {
        StringBuilder value = new StringBuilder(rowHelper).append("(rs");
        for (int index = 0; index < plan.properties.size(); index++) value.append(", column").append(index);
        return value.append(')').toString();
    }

    private void emitRow(SourceWriter writer, TypeMirror type, RowPlan plan, String rowHelper,
                         boolean indexed) {
        writer.line("");
        writer.line("private " + type + " " + rowHelper
                + "(java.sql.ResultSet rs" + indexParameters(plan, indexed)
                + ") throws java.sql.SQLException {");
        writer.indent();
        if (plan.presentOnly) writer.line(plan.targetType + " target = " + (plan.create == null ? "new " + plan.targetType + "()" : plan.create) + ";");
        for (int index = 0; index < plan.properties.size(); index++) {
            Property property = plan.properties.get(index);
            String column = indexed ? "column" + index : "\"" + GeneratorUtil.escape(property.column) + "\"";
            if (plan.presentOnly) {
                writer.line("if (" + column + " != -1) {"); writer.indent();
            }
            String getter = property.helper == null ? jdbcGetter(property.type) : null;
            if (getter != null) {
                writer.line(typeName(property.type, true) + " value" + index + " = rs." + getter
                        + "(" + column + ");");
                if (property.type.getKind().isPrimitive()) {
                    writer.line("if (rs.wasNull()) " + primitiveNullHelper + "(\""
                            + GeneratorUtil.escape(property.column) + "\", \"" + property.type + "\");");
                }
                if (plan.presentOnly) {
                    emitAssignment(writer, property, index);
                    writer.dedent(); writer.line("}");
                }
                continue;
            }
            String raw = "raw" + index;
            writer.line("Object " + raw + " = rs.getObject(" + column + ");");
            String value = property.helper == null
                    ? convert(raw, property.type, property.column)
                    : (property.helper.statik ? property.helper.owner + "." : "this.")
                    + property.helper.name + "(" + raw + ")";
            writer.line(typeName(property.type, true) + " value" + index + " = " + value + ";");
            if (plan.presentOnly) {
                emitAssignment(writer, property, index);
                writer.dedent(); writer.line("}");
            }
        }
        if (plan.constructor) {
            StringBuilder call = new StringBuilder("return new ").append(plan.targetType).append('(');
            for (int index = 0; index < plan.properties.size(); index++) {
                if (index != 0) {
                    call.append(", ");
                }
                call.append("value").append(index);
            }
            writer.line(call.append(");").toString());
        } else {
            if (!plan.presentOnly) writer.line(plan.targetType + " target = " + (plan.create == null ? "new " + plan.targetType + "()" : plan.create) + ";");
            for (int index = 0; index < plan.properties.size(); index++) {
                Property property = plan.properties.get(index);
                if (plan.presentOnly) continue;
                emitAssignment(writer, property, index);
            }
            writer.line("return target;");
        }
        writer.dedent();
        writer.line("}");
    }

    private void emitAssignment(SourceWriter writer, Property property, int index) {
        String target = "target";
        for (Access parent : property.parents) {
            target += "." + parent.javaName + (parent.getter == null ? "" : "()");
            writer.line("if (" + target + " == null) throw new org.sjf4j.exception.JsonException(\"Missing target path parent: "
                    + GeneratorUtil.escape(property.path) + "\");");
        }
        writer.line(property.setter == null ? target + "." + property.javaName + " = value" + index + ";"
                : target + "." + property.setter.getSimpleName() + "(value" + index + ");");
    }

    private RowPlan plan(TypeElement iface, ExecutableElement method, TypeMirror type, MappingCreators.Match creator, boolean presentOnly) {
        TypeElement element = GeneratorUtil.asTypeElement(type);
        if (element == null || element.getKind() == ElementKind.INTERFACE
                || element.getModifiers().contains(Modifier.ABSTRACT)) {
            error(method, creator != null && creator.create != null
                    ? "@MappingCreator creator method must return a concrete mutable type"
                    : "@CompiledJdbcMapper requires a concrete POJO, record, constructor target, or Map<String,Object>");
            return null;
        }

        List<ExecutableElement> constructors = new ArrayList<ExecutableElement>();
        for (Element member : element.getEnclosedElements()) {
            if (member.getKind() == ElementKind.CONSTRUCTOR
                    && member.getModifiers().contains(Modifier.PUBLIC)) {
                constructors.add((ExecutableElement) member);
            }
        }
        boolean record = GeneratorUtil.isRecord(element);
        ExecutableElement noArguments = null;
        for (ExecutableElement constructor : constructors) {
            if (constructor.getParameters().isEmpty()) {
                noArguments = constructor;
            }
        }
        boolean constructorTarget = (creator == null || creator.create == null) && (record || noArguments == null);
        if (constructorTarget && constructors.size() != 1) {
            error(method, "JDBC target type must have a public no-args constructor, be a record, or have exactly one public constructor");
            return null;
        }

        LinkedHashMap<String, Property> values = new LinkedHashMap<String, Property>();
        if (constructorTarget) {
            ExecutableElement constructor = constructors.get(0);
            ExecutableType constructorType = (ExecutableType) ctx.types.asMemberOf((DeclaredType) type, constructor);
            for (int index = 0; index < constructor.getParameters().size(); index++) {
                String name = constructor.getParameters().get(index).getSimpleName().toString();
                TypeMirror parameterType = constructorType.getParameterTypes().get(index);
                values.put(name, new Property(name, name, parameterType, null));
            }
        } else {
            for (Element member : ctx.elements.getAllMembers(element)) {
                if (!member.getModifiers().contains(Modifier.PUBLIC)
                        || member.getModifiers().contains(Modifier.STATIC)) {
                    continue;
                }
                if (member.getKind() == ElementKind.FIELD
                        && !member.getModifiers().contains(Modifier.FINAL)) {
                    String name = member.getSimpleName().toString();
                    TypeMirror fieldType = ctx.types.asMemberOf((DeclaredType) type, member);
                    values.put(name, new Property(name, name, fieldType, null));
                }
                if (member.getKind() == ElementKind.METHOD) {
                    ExecutableElement setter = (ExecutableElement) member;
                    String name = GeneratorUtil.writablePropertyBase(setter);
                    if (name != null && setter.getParameters().size() == 1
                            && setter.getReturnType().getKind() == TypeKind.VOID) {
                        ExecutableType setterType = (ExecutableType) ctx.types.asMemberOf((DeclaredType) type, setter);
                        TypeMirror parameterType = setterType.getParameterTypes().get(0);
                        values.put(name, new Property(name, name, parameterType, setter));
                    }
                }
            }
        }

        Map<String, Mapping> mappings = new LinkedHashMap<String, Mapping>();
        List<Property> pathProperties = new ArrayList<Property>();
        Set<String> pathRoots = new HashSet<String>();
        Set<String> pathTargets = new HashSet<String>();
        for (Mapping mapping : method.getAnnotationsByType(Mapping.class)) {
            if (!validMapping(method, mapping)) return null;
            if (autoMarker(mapping)) continue;
            if (mapping.target().length() == 0) {
                error(method, "@Mapping requires a non-empty target");
                return null;
            }
            String rootName = rootPathName(mapping.target());
            if (rootName != null) {
                Mapping previous = mappings.put(rootName, mapping);
                if (previous != null) {
                    error(method, "Duplicate @Mapping target '" + rootName + "'");
                    return null;
                }
                if (!values.containsKey(rootName)) {
                    error(method, "Cannot map target property '" + rootName + "': target is not writable");
                    return null;
                }
                continue;
            }
            if (mapping.target().startsWith("$") || mapping.target().startsWith("/")) {
                if (constructorTarget) {
                    error(method, "@CompiledJdbcMapper target paths require a mutable target with a public no-args constructor");
                    return null;
                }
                if (mapping.ignore()) {
                    error(method, "@Mapping.ignore does not support target paths");
                    return null;
                }
                if (!pathTargets.add(pathTargetKey(mapping.target()))) {
                    error(method, "Duplicate @Mapping target '" + mapping.target() + "'");
                    return null;
                }
                Property property = pathProperty(iface, method, type, mapping);
                if (property == null) return null;
                pathProperties.add(property);
                if (!property.parents.isEmpty()) pathRoots.add(property.parents.get(0).name);
                continue;
            }
            if (!values.containsKey(mapping.target())) {
                error(method, "Cannot map target property '" + mapping.target() + "': target is not writable");
                return null;
            }
            if (mappings.put(mapping.target(), mapping) != null) {
                error(method, "Duplicate @Mapping target '" + mapping.target() + "'");
                return null;
            }
        }

        List<Property> properties = new ArrayList<Property>();
        for (Property property : values.values()) {
            if (pathRoots.contains(property.name) && !mappings.containsKey(property.name)) continue;
            Mapping mapping = mappings.get(property.name);
            if (mapping != null && mapping.ignore()) {
                if (constructorTarget) {
                    error(method, "Constructor and record target properties cannot be ignored");
                    return null;
                }
                continue;
            }
            String column = property.name;
            Helper helper = null;
            if (mapping != null) {
                if (mapping.compute().length() != 0) {
                    helper = helper(iface, method, mapping, property.type);
                    if (helper == null) return null;
                    column = jdbcSource(method, mapping.sources()[0]);
                    if (column == null) return null;
                } else if (mapping.source().length() != 0) {
                    column = jdbcSource(method, mapping.source());
                    if (column == null) return null;
                }
            }
            if (helper == null && !supported(property.type)) {
                error(method, "Unsupported JDBC target property '" + property.name + "' of type "
                        + property.type + "; use @Mapping(target=\"" + property.name
                        + "\", sources={\"column\"}, compute=\"this::helper\")");
                return null;
            }
            properties.add(new Property(property.name, property.javaName, property.type,
                    property.setter, column, helper));
        }
        properties.addAll(pathProperties);
        if (creator != null && creator.create != null && properties.isEmpty()) {
            error(method, "@MappingCreator creator method must return a concrete mutable type");
            return null;
        }
        return new RowPlan(constructorTarget, properties, type, creator == null ? null : creator.create, presentOnly);
    }

    private String rootPathName(String target) {
        if (!target.startsWith("$") && !target.startsWith("/")) return null;
        try {
            PathSegment[] segments = JsonPath.parse(target).segments();
            return segments.length == 2 && segments[0] instanceof PathSegment.Root
                    && segments[1] instanceof PathSegment.Name ? ((PathSegment.Name) segments[1]).name : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String pathTargetKey(String target) {
        try {
            return JsonPath.parse(target).toPointerExpr();
        } catch (RuntimeException e) {
            return target;
        }
    }

    private String jdbcSource(ExecutableElement method, String source) {
        if (!source.startsWith("$") && !source.startsWith("/")) return source;
        try {
            PathSegment[] segments = JsonPath.parse(source).segments();
            if (segments.length == 2 && segments[1] instanceof PathSegment.Name) {
                return ((PathSegment.Name) segments[1]).name;
            }
        } catch (RuntimeException e) {
            // Report all non-flat JDBC source expressions uniformly below.
        }
        error(method, "Nested JDBC source paths are unsupported: '" + source + "'");
        return null;
    }

    private Property pathProperty(TypeElement iface, ExecutableElement method, TypeMirror root, Mapping mapping) {
        PathSegment[] segments;
        try {
            segments = JsonPath.parse(mapping.target()).segments();
        } catch (RuntimeException e) {
            error(method, "Invalid target path '" + mapping.target() + "': " + e.getMessage());
            return null;
        }
        if (segments.length < 2 || !(segments[0] instanceof PathSegment.Root)) {
            error(method, "Target path requires a non-root child path");
            return null;
        }
        TypeMirror current = root;
        List<Access> parents = new ArrayList<Access>();
        for (int index = 1; index < segments.length; index++) {
            if (!(segments[index] instanceof PathSegment.Name)) {
                error(method, "JDBC target paths support property-name segments only");
                return null;
            }
            String name = ((PathSegment.Name) segments[index]).name;
            if (index == segments.length - 1) {
                Property write = writableProperty(current, name);
                if (write == null) {
                    error(method, "Cannot resolve writable target path property '" + name + "' on " + current);
                    return null;
                }
                String column = mapping.compute().length() != 0 ? jdbcSource(method, mapping.sources()[0])
                        : (mapping.source().length() == 0 ? name : jdbcSource(method, mapping.source()));
                if (column == null) return null;
                Helper helper = mapping.compute().length() == 0 ? null : helper(iface, method, mapping, write.type);
                if (mapping.compute().length() != 0 && helper == null) return null;
                if (helper == null && !supported(write.type)) {
                    error(method, "Unsupported JDBC target property '" + mapping.target() + "' of type " + write.type);
                    return null;
                }
                return new Property(mapping.target(), write.javaName, write.type, write.setter, column, helper, parents, mapping.target());
            }
            Access read = readableProperty(current, name);
            if (read == null) {
                error(method, "Cannot resolve readable target path property '" + name + "' on " + current);
                return null;
            }
            parents.add(read);
            current = read.type;
        }
        return null;
    }

    private Access readableProperty(TypeMirror parent, String name) {
        TypeElement element = GeneratorUtil.asTypeElement(parent);
        if (element == null) return null;
        for (Element member : ctx.elements.getAllMembers(element)) {
            if (!member.getModifiers().contains(Modifier.PUBLIC) || member.getModifiers().contains(Modifier.STATIC)) continue;
            if (member.getKind() == ElementKind.FIELD && member.getSimpleName().contentEquals(name))
                return new Access(name, name, ctx.types.asMemberOf((DeclaredType) parent, member), null);
            if (member.getKind() == ElementKind.METHOD) {
                ExecutableElement getter = (ExecutableElement) member;
                if (getter.getParameters().isEmpty() && name.equals(GeneratorUtil.readablePropertyBase(ctx, element, parent, getter)))
                    return new Access(name, getter.getSimpleName().toString(), ((ExecutableType) ctx.types.asMemberOf((DeclaredType) parent, getter)).getReturnType(), getter);
            }
        }
        return null;
    }

    private Property writableProperty(TypeMirror parent, String name) {
        TypeElement element = GeneratorUtil.asTypeElement(parent);
        if (element == null) return null;
        for (Element member : ctx.elements.getAllMembers(element)) {
            if (!member.getModifiers().contains(Modifier.PUBLIC) || member.getModifiers().contains(Modifier.STATIC)) continue;
            if (member.getKind() == ElementKind.FIELD && !member.getModifiers().contains(Modifier.FINAL)
                    && member.getSimpleName().contentEquals(name))
                return new Property(name, name, ctx.types.asMemberOf((DeclaredType) parent, member), null);
            if (member.getKind() == ElementKind.METHOD) {
                ExecutableElement setter = (ExecutableElement) member;
                if (setter.getParameters().size() == 1 && setter.getReturnType().getKind() == TypeKind.VOID
                        && name.equals(GeneratorUtil.writablePropertyBase(setter)))
                    return new Property(name, name, ((ExecutableType) ctx.types.asMemberOf((DeclaredType) parent, setter)).getParameterTypes().get(0), setter);
            }
        }
        return null;
    }

    private boolean validMapping(ExecutableElement method, Mapping mapping) {
        if (mapping.sources().length > 0 && mapping.compute().length() == 0) {
            error(method, "@Mapping.sources may be used only with @Mapping.compute");
            return false;
        }
        if (mapping.ignore() && (mapping.source().length() != 0 || mapping.compute().length() != 0
                || mapping.sources().length != 0 || mapping.array() != ArrayPolicy.SET
                || mapping.object() != ObjectPolicy.PUT)) {
            error(method, "@Mapping.ignore cannot be combined with source, sources, compute, array, or object");
            return false;
        }
        if (mapping.array() != ArrayPolicy.SET || mapping.object() != ObjectPolicy.PUT) {
            error(method, "@CompiledJdbcMapper does not support @Mapping.array or @Mapping.object");
            return false;
        }
        if (mapping.ignore() && mapping.target().length() == 0) {
            error(method, "@Mapping.ignore requires a non-empty target");
            return false;
        }
        if (mapping.compute().length() != 0 && mapping.source().length() != 0) {
            error(method, "@CompiledJdbcMapper compute mappings use sources, not source");
            return false;
        }
        if (mapping.sources().length > 0) {
            for (String source : mapping.sources()) {
                if ((source.startsWith("$") || source.startsWith("/")) && jdbcSource(method, source) == null) return false;
            }
        }
        return true;
    }

    private static boolean autoMarker(Mapping mapping) {
        return mapping.target().length() == 0
                && mapping.source().length() == 0
                && mapping.sources().length == 0
                && mapping.compute().length() == 0
                && mapping.array() == ArrayPolicy.SET
                && mapping.object() == ObjectPolicy.PUT
                && !mapping.ignore();
    }

    private Helper helper(TypeElement iface, ExecutableElement method, Mapping mapping, TypeMirror target) {
        if (!mapping.compute().startsWith("this::") || mapping.compute().length() <= 6
                || mapping.sources().length != 1 || mapping.sources()[0].length() == 0) {
            error(method, "@CompiledJdbcMapper supports compute only as this::helper with exactly one sources entry");
            return null;
        }
        String name = mapping.compute().substring(6);
        for (Element member : iface.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD || !member.getSimpleName().contentEquals(name)) continue;

            ExecutableElement candidate = (ExecutableElement) member;
            boolean callable = candidate.getModifiers().contains(Modifier.DEFAULT)
                    || candidate.getModifiers().contains(Modifier.STATIC);
            if (callable && candidate.getParameters().size() == 1
                    && is(candidate.getParameters().get(0).asType(), "java.lang.Object")
                    && GeneratorUtil.isAssignableBoxed(ctx, candidate.getReturnType(), target)) {
                boolean statik = candidate.getModifiers().contains(Modifier.STATIC);
                return new Helper(statik, name, iface.getQualifiedName().toString());
            }
        }
        error(method, "@CompiledJdbcMapper compute helper '" + mapping.compute()
                + "' must be a default/static one-argument Object method returning " + target);
        return null;
    }

    private String convert(String raw, TypeMirror type, String column) {
        String name = type.toString();
        String nullPrimitive = "(" + raw + " == null ? " + primitiveNullHelper + "(\""
                + GeneratorUtil.escape(column) + "\", \"" + name + "\") : ";
        if (type.getKind().isPrimitive()) return nullPrimitive + scalar(raw, name) + ")";
        if (name.equals("java.time.Instant")) return temporal(raw, "java.time.Instant", "java.sql.Timestamp", "toInstant", column);
        if (name.equals("java.time.LocalDateTime")) return temporal(raw, "java.time.LocalDateTime", "java.sql.Timestamp", "toLocalDateTime", column);
        if (name.equals("java.time.LocalDate")) return temporal(raw, "java.time.LocalDate", "java.sql.Date", "toLocalDate", column);
        if (name.equals("java.time.LocalTime")) return temporal(raw, "java.time.LocalTime", "java.sql.Time", "toLocalTime", column);
        if (name.equals("java.lang.String")) return "(" + raw + " == null ? null : org.sjf4j.node.Nodes.toString(" + raw + "))";
        if (name.equals("java.lang.Integer") || name.equals("java.lang.Long")
                || name.equals("java.lang.Short") || name.equals("java.lang.Byte")
                || name.equals("java.lang.Double") || name.equals("java.lang.Float")
                || name.equals("java.lang.Boolean") || name.equals("java.lang.Character")) {
            return "(" + raw + " == null ? null : " + scalar(raw, name) + ")";
        }
        TypeElement element = GeneratorUtil.asTypeElement(type);
        if (element != null && element.getKind() == ElementKind.ENUM) {
            return "(" + raw + " == null ? null : org.sjf4j.node.Nodes.toEnum(" + raw
                    + ", " + name + ".class))";
        }
        return "(" + name + ") " + raw;
    }

    private String scalar(String raw, String name) {
        if (name.equals("int") || name.equals("java.lang.Integer")) return "org.sjf4j.node.Nodes.toInt(" + raw + ")";
        if (name.equals("long") || name.equals("java.lang.Long")) return "org.sjf4j.node.Nodes.toLong(" + raw + ")";
        if (name.equals("short") || name.equals("java.lang.Short")) return "org.sjf4j.node.Nodes.toShort(" + raw + ")";
        if (name.equals("byte") || name.equals("java.lang.Byte")) return "org.sjf4j.node.Nodes.toByte(" + raw + ")";
        if (name.equals("double") || name.equals("java.lang.Double")) return "org.sjf4j.node.Nodes.toDouble(" + raw + ")";
        if (name.equals("float") || name.equals("java.lang.Float")) return "org.sjf4j.node.Nodes.toFloat(" + raw + ")";
        if (name.equals("java.math.BigDecimal")) return "org.sjf4j.node.Nodes.toBigDecimal(" + raw + ")";
        if (name.equals("java.math.BigInteger")) return "org.sjf4j.node.Nodes.toBigInteger(" + raw + ")";
        if (name.equals("boolean") || name.equals("java.lang.Boolean")) return "org.sjf4j.node.Nodes.toBoolean(" + raw + ")";
        return "org.sjf4j.node.Nodes.toChar(" + raw + ")";
    }

    private static String jdbcGetter(TypeMirror type) {
        if (type.toString().equals("java.lang.String")) return "getString";
        if (!type.getKind().isPrimitive()) return null;
        switch (type.getKind()) {
            case INT: return "getInt";
            case LONG: return "getLong";
            case SHORT: return "getShort";
            case BYTE: return "getByte";
            case DOUBLE: return "getDouble";
            case FLOAT: return "getFloat";
            case BOOLEAN: return "getBoolean";
            default: return null;
        }
    }

    private String temporal(String raw, String target, String jdbc, String conversion, String column) {
        return "(" + raw + " == null ? null : (" + raw + " instanceof " + target + " ? ("
                + target + ") " + raw + " : (" + raw + " instanceof " + jdbc + " ? (("
                + jdbc + ") " + raw + ")." + conversion + "() : " + temporalTypeHelper + "(\""
                + GeneratorUtil.escape(column) + "\", \"" + target + "\", " + raw + "))))";
    }

    private void emitPrimitiveNullHelper(SourceWriter writer) {
        writer.line("");
        writer.line("private static <T> T " + primitiveNullHelper + "(String column, String type) {");
        writer.indent();
        writer.line("throw new org.sjf4j.exception.BindingException(\"SQL NULL for column '\" + column + \"' mapped to primitive \" + type);");
        writer.dedent();
        writer.line("}");
    }

    private void emitTemporalTypeHelper(SourceWriter writer) {
        writer.line("");
        writer.line("private static <T> T " + temporalTypeHelper + "(String column, String type, Object value) {");
        writer.indent();
        writer.line("throw new org.sjf4j.exception.BindingException(\"Cannot map JDBC column '\" + column + \"' value of type \" + value.getClass().getName() + \" to \" + type);");
        writer.dedent();
        writer.line("}");
    }

    private boolean supported(TypeMirror type) {
        String name = type.toString();
        if (type.getKind().isPrimitive() || name.equals("java.lang.Object")
                || name.equals("java.lang.String") || name.equals("java.lang.Integer")
                || name.equals("java.lang.Long") || name.equals("java.lang.Short")
                || name.equals("java.lang.Byte") || name.equals("java.lang.Double")
                || name.equals("java.lang.Float") || name.equals("java.math.BigDecimal")
                || name.equals("java.math.BigInteger") || name.equals("java.lang.Boolean")
                || name.equals("java.lang.Character") || name.equals("java.time.Instant")
                || name.equals("java.time.LocalDateTime") || name.equals("java.time.LocalDate")
                || name.equals("java.time.LocalTime") || name.equals("java.util.UUID")
                || name.equals("byte[]") || name.equals("java.sql.Date")
                || name.equals("java.sql.Time") || name.equals("java.sql.Timestamp")
                || name.equals("java.time.OffsetDateTime")) {
            return true;
        }
        TypeElement element = GeneratorUtil.asTypeElement(type);
        return element != null && element.getKind() == ElementKind.ENUM;
    }

    private static String indexParameters(RowPlan plan, boolean indexed) {
        if (!indexed) return "";
        StringBuilder parameters = new StringBuilder();
        for (int index = 0; index < plan.properties.size(); index++) {
            parameters.append(", int column").append(index);
        }
        return parameters.toString();
    }

    private static boolean usesPrimitive(RowPlan plan) {
        for (Property property : plan.properties) if (property.type.getKind().isPrimitive()) return true;
        return false;
    }

    private static boolean usesTemporal(RowPlan plan) {
        for (Property property : plan.properties) {
            String type = property.type.toString();
            if (property.helper == null && (type.equals("java.time.Instant")
                    || type.equals("java.time.LocalDateTime") || type.equals("java.time.LocalDate")
                    || type.equals("java.time.LocalTime"))) return true;
        }
        return false;
    }

    private boolean isMap(TypeMirror type) {
        if (!isDeclared(type, "java.util.Map")) return false;

        List<? extends TypeMirror> arguments = ((DeclaredType) type).getTypeArguments();
        return arguments.size() == 2
                && arguments.get(0).toString().equals("java.lang.String")
                && arguments.get(1).toString().equals("java.lang.Object");
    }

    private boolean isDeclared(TypeMirror type, String name) {
        TypeElement element = ctx.elements.getTypeElement(name);
        return element != null && ctx.types.isSameType(ctx.types.erasure(type),
                ctx.types.erasure(element.asType()));
    }

    private boolean is(TypeMirror type, String name) {
        return type.toString().equals(name);
    }

    private String typeName(TypeMirror type, boolean box) {
        return box && type.getKind().isPrimitive()
                ? ctx.types.boxedClass((javax.lang.model.type.PrimitiveType) type).getQualifiedName().toString()
                : type.toString();
    }

    private void error(Element element, String message) {
        ctx.error(element, message);
    }

    private static final class RowPlan {
        final boolean constructor;
        final List<Property> properties;
        final TypeMirror targetType;
        final String create;
        final boolean presentOnly;

        RowPlan(boolean constructor, List<Property> properties, TypeMirror targetType, String create, boolean presentOnly) {
            this.constructor = constructor;
            this.properties = properties;
            this.targetType = targetType;
            this.create = create;
            this.presentOnly = presentOnly;
        }
    }

    private static final class Helper {
        final boolean statik;
        final String name;
        final String owner;

        Helper(boolean statik, String name, String owner) {
            this.statik = statik;
            this.name = name;
            this.owner = owner;
        }
    }

    private static final class Property {
        final String name;
        final String javaName;
        final String column;
        final TypeMirror type;
        final ExecutableElement setter;
        final Helper helper;
        final List<Access> parents;
        final String path;

        Property(String name, String javaName, TypeMirror type, ExecutableElement setter) {
            this(name, javaName, type, setter, name, null);
        }

        Property(String name, String javaName, TypeMirror type, ExecutableElement setter,
                  String column, Helper helper) {
            this.name = name;
            this.javaName = javaName;
            this.type = type;
            this.setter = setter;
            this.column = column;
            this.helper = helper;
            this.parents = java.util.Collections.emptyList();
            this.path = name;
        }

        Property(String name, String javaName, TypeMirror type, ExecutableElement setter,
                  String column, Helper helper, List<Access> parents, String path) {
            this.name = name;
            this.javaName = javaName;
            this.type = type;
            this.setter = setter;
            this.column = column;
            this.helper = helper;
            this.parents = parents;
            this.path = path;
        }
    }

    private static final class Access {
        final String name;
        final String javaName;
        final TypeMirror type;
        final ExecutableElement getter;

        Access(String name, String javaName, TypeMirror type, ExecutableElement getter) {
            this.name = name;
            this.javaName = javaName;
            this.type = type;
            this.getter = getter;
        }
    }
}
