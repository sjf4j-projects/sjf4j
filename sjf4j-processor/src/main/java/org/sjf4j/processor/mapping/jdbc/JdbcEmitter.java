package org.sjf4j.processor.mapping.jdbc;

import org.sjf4j.processor.mapping.target.TargetCreationEmitter;
import org.sjf4j.processor.mapping.target.TargetPlan;
import org.sjf4j.processor.mapping.target.TargetAccessEmitter;
import org.sjf4j.processor.code.MethodSignature;
import org.sjf4j.processor.code.JavaExpressions;
import org.sjf4j.processor.CompilationContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.access.ResolvedStep;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.discovery.ValueCodecMetadata;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.mapping.CreatorResolver;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits direct ResultSet mapping code from compiled JDBC mapping plans.
 *
 * <p>All semantic resolution must be completed by {@link JdbcCompiler}
 * before this emitter is invoked.</p>
 */
public final class JdbcEmitter {

    private final CompilationContext context;
    private final TypeSystem types;
    private final TargetAccessEmitter targetAccess;

    public JdbcEmitter(CompilationContext context) {
        this.context = context;
        this.types = context.types;
        this.targetAccess = new TargetAccessEmitter(types);
    }

    /*
     * --------------------------------------------------------------
     * Emit
     * --------------------------------------------------------------
     */

    public void emit(JdbcCompiler.CompiledMethod method, GeneratedClass generated) {
        if (method.plan().list()) {
            String name = method.plan().method().getSimpleName().toString();
            String helper = generated.names().helperMethod(
                    "prepare" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
            generated.addMethod(out -> emitListUsingPrepared(out, method, helper));
            generated.addHelper(helper, out -> emitPreparedMethod(out, method, helper));
        } else {
            generated.addMethod(out -> emitMethod(out, method));
        }
    }

    /*
     * List uses the same prepared, per-row execution path as JdbcRowMapper.
     * The helper is private and method-specific: equal row types may still have
     * different source names, column policies, creators or mapping rules.
     */
    private void emitListUsingPrepared(JavaWriter out, JdbcCompiler.CompiledMethod method,
                                       String helper) {
        JdbcPlan plan = method.plan();
        String rs = plan.inputParameter().getSimpleName().toString();
        String type = plan.rowType().toString();
        NameAllocator names = new NameAllocator();
        for (VariableElement parameter : plan.method().getParameters()) {
            names.reserve(parameter.getSimpleName().toString());
        }
        String result = names.newName("result");
        String rowMapper = names.newName("rowMapper");
        String metadata = names.newName("metadata");

        out.blank();
        out.line("@Override");
        out.line(MethodSignature.override(plan.method(), plan.methodType(), false) + " {");
        out.indent();
        out.line("org.sjf4j.util.Asserts.notNull(" + rs + ", " + JavaWriter.stringLiteral(rs) + ");");
        out.line("try {");
        out.indent();
        out.line("java.util.List<" + type + "> " + result
                + " = new java.util.ArrayList<" + type + ">();");
        if (method.kind() == JdbcCompiler.CompiledMethod.Kind.MAP) {
            // Preserve empty Map List semantics: no column labels are read.
            out.line("java.sql.ResultSetMetaData " + metadata + " = " + rs + ".getMetaData();");
            out.line("if (!" + rs + ".next()) return " + result + ";");
            out.line("org.sjf4j.mapping.JdbcRowMapper<" + type + "> " + rowMapper + " = "
                    + helper + "(" + metadata + ");");
            out.line("do {");
            out.indent();
            out.line(result + ".add(" + rowMapper + ".map(" + rs + "));");
            out.dedent();
            out.line("} while (" + rs + ".next());");
        } else {
            out.line("org.sjf4j.mapping.JdbcRowMapper<" + type + "> " + rowMapper + " = "
                    + helper + "(" + rs + ".getMetaData());");
            out.line("while (" + rs + ".next()) {");
            out.indent();
            out.line(result + ".add(" + rowMapper + ".map(" + rs + "));");
            out.dedent();
            out.line("}");
        }
        out.line("return " + result + ";");
        out.dedent();
        out.line("} catch (java.sql.SQLException e) {");
        out.indent();
        out.line("throw new org.sjf4j.exception.BindingException("
                + JavaWriter.stringLiteral("failed to map JDBC ResultSet in " + plan.method().getSimpleName())
                + ", e);");
        out.dedent();
        out.line("}");
        out.dedent();
        out.line("}");
    }

    /*
     * Prepare resolves the JDBC column layout exactly once and returns a
     * stateless, reusable row mapper. It never retains the metadata or ResultSet.
     */
    private void emitPreparedMethod(JavaWriter out, JdbcCompiler.CompiledMethod method,
                                    String helperName) {
        JdbcPlan plan = method.plan();
        NameAllocator names = new NameAllocator();
        if (helperName == null) {
            for (VariableElement parameter : plan.method().getParameters()) {
                names.reserve(parameter.getSimpleName().toString());
            }
        } else {
            names.reserve("metadata");
        }

        String metadata = helperName == null
                ? plan.inputParameter().getSimpleName().toString() : "metadata";
        String rowSet = names.newName("rowSet");
        out.blank();
        if (helperName == null) {
            out.line("@Override");
            out.line(MethodSignature.override(plan.method(), plan.methodType(), false) + " {");
        } else {
            out.line("private org.sjf4j.mapping.JdbcRowMapper<" + plan.rowType() + "> "
                    + helperName + "(java.sql.ResultSetMetaData " + metadata + ") {");
        }
        out.indent();
        out.line("org.sjf4j.util.Asserts.notNull(" + metadata + ", "
                + JavaWriter.stringLiteral(metadata) + ");");
        out.line("try {");
        out.indent();

        String columnCount = null;
        String labels = null;
        String consumed = null;
        Map<String, String> indexes = Collections.emptyMap();

        if (method.kind() == JdbcCompiler.CompiledMethod.Kind.MAP || method.jojo()) {
            columnCount = names.newName("columnCount");
            labels = names.newName("labels");
            String index = names.newName("column");
            out.line("int " + columnCount + " = " + metadata + ".getColumnCount();");
            out.line("String[] " + labels + " = new String[" + columnCount + " + 1];");
            out.line("for (int " + index + " = 1; " + index + " <= " + columnCount
                    + "; " + index + "++) {");
            out.indent();
            out.line(labels + "[" + index + "] = " + metadata + ".getColumnLabel(" + index + ");");
            out.dedent();
            out.line("}");
        }

        if (method.kind() == JdbcCompiler.CompiledMethod.Kind.OBJECT) {
            indexes = emitColumnIndexes(out, names, metadata,
                    method.jojo() ? method.consumedColumns() : method.readColumns(),
                    method.columnNaming(), method.presentOnly());
        }

        if (method.jojo()) {
            consumed = emitConsumedColumns(out, names, columnCount, indexes, method.consumedColumns());
        }

        out.line("return " + rowSet + " -> {");
        out.indent();
        out.line("org.sjf4j.util.Asserts.notNull(" + rowSet + ", "
                + JavaWriter.stringLiteral(rowSet) + ");");
        out.line("try {");
        out.indent();

        String row = emitRow(out, names, method, rowSet, metadata, columnCount, null,
                indexes, columnCount, labels, consumed);
        out.line("return " + row + ";");

        out.dedent();
        out.line("} catch (java.sql.SQLException e) {");
        out.indent();
        out.line("throw new org.sjf4j.exception.BindingException("
                + JavaWriter.stringLiteral("failed to map JDBC row in " + plan.method().getSimpleName())
                + ", e);");
        out.dedent();
        out.line("}");
        out.dedent();
        out.line("};");

        out.dedent();
        out.line("} catch (java.sql.SQLException e) {");
        out.indent();
        out.line("throw new org.sjf4j.exception.BindingException("
                + JavaWriter.stringLiteral("failed to prepare JDBC mapper in " + plan.method().getSimpleName())
                + ", e);");
        out.dedent();
        out.line("}");

        out.dedent();
        out.line("}");
    }

    private void emitMethod(JavaWriter out, JdbcCompiler.CompiledMethod method) {
        if (method.plan().prepared()) {
            emitPreparedMethod(out, method, null);
            return;
        }
        JdbcPlan plan = method.plan();
        NameAllocator names = new NameAllocator();

        for (VariableElement parameter : plan.method().getParameters()) {
            names.reserve(parameter.getSimpleName().toString());
        }

        String resultSet = plan.inputParameter().getSimpleName().toString();

        out.line("");
        out.line("@Override");
        out.line(MethodSignature.override(plan.method(), plan.methodType(), false) + " {");

        out.indent();

        out.line(
                "org.sjf4j.util.Asserts.notNull(" +
                        resultSet +
                        ", " +
                        JavaWriter.stringLiteral(resultSet) +
                        ");");

        out.line("try {");
        out.indent();

        // Cursor-consuming single-target methods return null before inspecting columns.
        if (plan.single()) {
            out.line("if (!" + resultSet + ".next()) {");
            out.indent();
            out.line("return null;");
            out.dedent();
            out.line("}");
        }

        String metadata = null;
        String columns = null;
        String columnCount = null;
        String labels = null;
        String consumed = null;

        Map<String, String> indexes = Collections.emptyMap();

        /*
         * Map rows always require ResultSetMetaData because their keys come
         * directly from runtime column labels.
         *
         * PRESENT_ONLY and JOJO remainder mapping also need metadata once per
         * mapper invocation.
         */
        if (method.kind() == JdbcCompiler.CompiledMethod.Kind.MAP
                || method.kind() == JdbcCompiler.CompiledMethod.Kind.OBJECT) {
            metadata = names.newName("metadata");

            out.line(
                    "java.sql.ResultSetMetaData " +
                            metadata +
                            " = " +
                            resultSet +
                            ".getMetaData();");
        }

        if (method.jojo()) {

            columnCount = names.newName("columnCount");
            labels = names.newName("labels");
            String index = names.newName("column");

            out.line(
                    "int " +
                            columnCount +
                            " = " +
                            metadata +
                            ".getColumnCount();");

            out.line(
                    "java.lang.String[] " +
                            labels +
                            " = new java.lang.String[" +
                            columnCount +
                            " + 1];");

            out.line(
                    "for (int " +
                            index +
                            " = 1; " +
                            index +
                            " <= " +
                            columnCount +
                            "; " +
                            index +
                            "++) {");

            out.indent();

            out.line(
                    labels +
                            "[" +
                            index +
                            "] = " +
                            metadata +
                            ".getColumnLabel(" +
                            index +
                            ");");

            out.dedent();
            out.line("}");
        }

        if (method.kind() == JdbcCompiler.CompiledMethod.Kind.OBJECT) {
            indexes = emitColumnIndexes(out, names, metadata,
                    method.jojo() ? method.consumedColumns() : method.readColumns(),
                    method.columnNaming(), method.presentOnly());
        }

        if (method.jojo()) {
            consumed = emitConsumedColumns(out, names, columnCount, indexes, method.consumedColumns());
        }

        // List methods are generated separately via a prepared JdbcRowMapper.
        emitSingle(out, names, method, resultSet, metadata, columnCount, columns,
                indexes, columnCount, labels, consumed);

        out.dedent();
        out.line(
                "} catch (java.sql.SQLException e) {");

        out.indent();

        out.line(
                "throw new org.sjf4j.exception.BindingException(" +
                        JavaWriter.stringLiteral(
                                "failed to map JDBC ResultSet in " +
                                        plan.method()
                                                .getSimpleName()) +
                        ", e);");

        out.dedent();
        out.line("}");

        out.dedent();
        out.line("}");
    }

    /*
     * --------------------------------------------------------------
     * Method Shell
     * --------------------------------------------------------------
     */

    /*
     * --------------------------------------------------------------
     * Cursor
     * --------------------------------------------------------------
     */

    private void emitSingle(
            JavaWriter out,
            NameAllocator names,
            JdbcCompiler.CompiledMethod method,
            String resultSet,
            String metadata,
            String mapColumnCount,
            String columns,
            Map<String, String> indexes,
            String columnCount,
            String labels,
            String consumed) {

        String row =
                emitRow(
                        out,
                        names,
                         method,
                         resultSet,
                         metadata,
                        mapColumnCount,
                        columns,
                        indexes,
                        columnCount,
                        labels,
                        consumed);

        if (!method.firstResult()) {

            out.line(
                    "if (" +
                            resultSet +
                            ".next()) {");

            out.indent();

            out.line(
                    "throw new org.sjf4j.exception.BindingException(" +
                            JavaWriter.stringLiteral(
                                    "JDBC mapper expected a single row but ResultSet contains multiple rows") +
                            ");");

            out.dedent();
            out.line("}");
        }

        out.line(
                "return " +
                        row +
                        ";");
    }

    /*
     * --------------------------------------------------------------
     * Row
     * --------------------------------------------------------------
     */

    private String emitRow(
            JavaWriter out,
            NameAllocator names,
            JdbcCompiler.CompiledMethod method,
            String resultSet,
            String metadata,
            String mapColumnCount,
            String columns,
            Map<String, String> indexes,
            String columnCount,
            String labels,
            String consumed) {

        if (method.kind() ==
                JdbcCompiler.CompiledMethod.Kind.MAP) {

            return emitMapRow(
                    out,
                     names,
                     resultSet,
                    metadata,
                    mapColumnCount,
                    labels);
        }

        return emitObjectRow(
                out,
                names,
                method,
                resultSet,
                columns,
                indexes,
                columnCount,
                labels,
                consumed);
    }

    /*
     * --------------------------------------------------------------
     * Map Row
     * --------------------------------------------------------------
     */

    private String emitMapRow(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            String metadata,
            String columnCount,
            String labels) {

        if (columnCount == null) {
            columnCount =
                    names.newName(
                            "columnCount");

            out.line(
                    "int " +
                            columnCount +
                            " = " +
                            metadata +
                            ".getColumnCount();");
        }

        String result =
                names.newName(
                        "row");

        String index =
                names.newName(
                        "column");

        String label =
                names.newName(
                        "label");

        out.line(
                "java.util.Map<String, Object> " +
                        result +
                        " = new java.util.LinkedHashMap<String, Object>(" +
                        columnCount +
                        ");");

        out.line(
                "for (int " +
                        index +
                        " = 1; " +
                        index +
                        " <= " +
                        columnCount +
                        "; " +
                        index +
                        "++) {");

        out.indent();

        out.line(
                "String " +
                        label +
                        " = " +
                        (labels == null
                                ? metadata + ".getColumnLabel(" + index + ")"
                                : labels + "[" + index + "]") +
                        ";");

        out.line(
                result +
                        ".put(" +
                        label +
                        ", " +
                        resultSet +
                        ".getObject(" +
                        index +
                        "));");

        out.dedent();
        out.line("}");

        return result;
    }

    /*
     * --------------------------------------------------------------
     * Object Row
     * --------------------------------------------------------------
     */

    private String emitObjectRow(
            JavaWriter out,
            NameAllocator names,
            JdbcCompiler.CompiledMethod method,
            String resultSet,
            String columns,
            Map<String, String> indexes,
            String columnCount,
            String labels,
            String consumed) {

        CreatorResolver.Creation creation =
                method.creation();

        List<String> constructorValues =
                new ArrayList<String>(
                        method.constructorArguments()
                                .size());

        for (JdbcCompiler.ConstructorArgument argument :
                method.constructorArguments()) {

            constructorValues.add(
                    emitValue(
                            out,
                            names,
                            resultSet,
                            argument.value(),
                            indexes));
        }

        String target =
                names.newName(
                        "row");

        switch (creation.kind()) {
            case NO_ARGS:
                out.line(method.targetType() + " " + target + " = " +
                        TargetCreationEmitter.constructor(method.targetType().toString(), Collections.emptyList()) + ";");
                break;

            case CONSTRUCTOR:
                out.line(method.targetType() + " " + target + " = " +
                        TargetCreationEmitter.constructor(method.targetType().toString(), constructorValues) + ";");
                break;

            case FACTORY:
                out.line(method.targetType() + " " + target + " = " +
                        TargetCreationEmitter.factory(creation, true) + ";");
                break;

            default:
                throw new AssertionError(
                        creation.kind());
        }

        for (JdbcCompiler.Assignment assignment :
                method.assignments()) {

            if (method.presentOnly()) {

                out.line(
                        "if (" +
                                presentColumns(
                                        columns,
                                        assignment.value(),
                                        indexes) +
                                ") {");

                out.indent();
            }

            String value =
                    emitValue(
                            out,
                            names,
                            resultSet,
                            assignment.value(),
                            indexes);

            emitTargetWrite(
                    out,
                    names,
                    target,
                    assignment.target(),
                    value);

            if (method.presentOnly()) {

                out.dedent();
                out.line("}");
            }
        }

        if (method.jojo()) {
            emitDynamicColumns(
                    out,
                    names,
                    target,
                    resultSet,
                    columnCount,
                    labels,
                    consumed);
        }

        return target;
    }

    /*
     * --------------------------------------------------------------
     * PRESENT_ONLY
     * --------------------------------------------------------------
     */

    private Map<String, String> emitColumnIndexes(
            JavaWriter out, NameAllocator names, String metadata,
            List<String> sourceColumns, org.sjf4j.annotation.mapping.jdbc.ColumnNamingPolicy policy,
            boolean presentOnly) {

        if (sourceColumns.isEmpty()) return Collections.emptyMap();

        StringBuilder namesLiteral = new StringBuilder("new String[]{");
        for (int i = 0; i < sourceColumns.size(); i++) {
            if (i != 0) namesLiteral.append(", ");
            namesLiteral.append(JavaWriter.stringLiteral(sourceColumns.get(i)));
        }
        namesLiteral.append('}');

        String resolved = names.newName("columnIndexes");
        String policyExpr = "org.sjf4j.annotation.mapping.jdbc.ColumnNamingPolicy." + policy.name();
        out.line("int[] " + resolved + " = org.sjf4j.mapping.MappingSupport.resolve("
                + metadata + ", " + namesLiteral + ", " + policyExpr + ", " + !presentOnly + ");");

        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < sourceColumns.size(); i++) {
            String name = sourceColumns.get(i);
            String index = names.newName("idx_" + name);
            out.line("int " + index + " = " + resolved + "[" + i + "];");
            result.put(name, index);
        }
        return result;
    }

    private String emitConsumedColumns(
            JavaWriter out,
            NameAllocator names,
            String columnCount,
            Map<String, String> indexes,
            List<String> consumedColumns) {

        String consumed =
                names.newName(
                        "consumed");

        out.line(
                "boolean[] " +
                        consumed +
                        " = new boolean[" +
                        columnCount +
                        " + 1];");

        for (String column :
                consumedColumns) {

            String index =
                    indexes.get(column);

            out.line(
                    "if (" +
                            index +
                            " != 0) { " +
                            consumed +
                            "[" +
                            index +
                            "] = true; }");
        }

        return consumed;
    }

    private void emitDynamicColumns(
            JavaWriter out,
            NameAllocator names,
            String target,
            String resultSet,
            String columnCount,
            String labels,
            String consumed) {

        String dynamic =
                names.newName(
                        "dynamic");

        String column =
                names.newName(
                        "column");

        out.line(
                "java.util.LinkedHashMap<String, Object> " +
                        dynamic +
                        " = " +
                        "org.sjf4j.InternalAccess.dynamicMembers(" +
                        target +
                        ");");

        out.line(
                "for (int " +
                        column +
                        " = 1; " +
                        column +
                        " <= " +
                        columnCount +
                        "; " +
                        column +
                        "++) {");

        out.indent();

        out.line(
                "if (!" +
                        consumed +
                        "[" +
                        column +
                        "]) {");

        out.indent();

        out.line(
                "if (" +
                        dynamic +
                        " == null) {");

        out.indent();

        out.line(
                dynamic +
                        " = new java.util.LinkedHashMap<String, Object>();");

        out.line(
                "org.sjf4j.InternalAccess.dynamicMembers(" +
                        target +
                        ", " +
                        dynamic +
                        ");");

        out.dedent();
        out.line("}");

        out.line(
                dynamic +
                        ".put(" +
                        labels +
                        "[" +
                        column +
                        "], " +
                        resultSet +
                        ".getObject(" +
                        column +
                        "));" );

        out.dedent();
        out.line("}");

        out.dedent();
        out.line("}");
    }

    /*
     * --------------------------------------------------------------
     * Column Read
     * --------------------------------------------------------------
     */

    private String emitValue(JavaWriter out, NameAllocator names, String resultSet,
                             JdbcCompiler.Value value, Map<String, String> indexes) {

        if (value.read() != null) return emitColumnRead(out, names, resultSet, value.read(), indexes);

        JdbcCompiler.ComputedValue compute = value.compute();
        List<String> arguments = new ArrayList<String>(compute.reads().size());

        for (JdbcCompiler.ColumnRead read : compute.reads()) {
            arguments.add(emitColumnRead(out, names, resultSet, read, indexes));
        }

        String result = names.newName("computed");

        out.line(
                JavaExpressions.boxedType(types, 
                        compute.targetType()) +
                        " " +
                        result +
                        " = this." +
                        compute.helper() +
                        "(" +
                        join(arguments) +
                        ");");

        return result;
    }

    private String presentColumns(String columns, JdbcCompiler.Value value, Map<String, String> indexes) {
        List<JdbcCompiler.ColumnRead> reads = value.read() != null
                ? Collections.singletonList(value.read()) : value.compute().reads();
        StringBuilder out = new StringBuilder();

        for (JdbcCompiler.ColumnRead read : reads) {
            if (out.length() != 0) {
                out.append(" && ");
            }

            String index = indexes.get(read.column());

            if (index != null) {
                out.append(index)
                        .append(" != 0");
            } else {
                out.append(columns).append(".contains(")
                        .append(JavaWriter.stringLiteral(read.column())).append(")");
            }
        }

        return out.length() == 0 ? "true" : out.toString();
    }

    private String emitColumnRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            Map<String, String> indexes) {

        String column =
                indexes.get(
                        read.column());

        if (column == null) {
            column = JavaWriter.stringLiteral(
                    read.column());
        }

        switch (read.kind()) {
            case STRING:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getString");

            case BOOLEAN:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getBoolean",
                        "boolean",
                        "java.lang.Boolean");

            case BYTE:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getByte",
                        "byte",
                        "java.lang.Byte");

            case SHORT:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getShort",
                        "short",
                        "java.lang.Short");

            case INT:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getInt",
                        "int",
                        "java.lang.Integer");

            case LONG:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getLong",
                        "long",
                        "java.lang.Long");

            case FLOAT:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getFloat",
                        "float",
                        "java.lang.Float");

            case DOUBLE:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getDouble",
                        "double",
                        "java.lang.Double");

            case CHARACTER:
                return emitCharacterRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column);

            case BIG_DECIMAL:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getBigDecimal");

            case BYTES:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getBytes");

            case DATE:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getDate");

            case TIME:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getTime");

            case TIMESTAMP:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getTimestamp");

            case ENUM:
                return emitEnumRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column);

            case OBJECT:
                return emitObjectRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column);

            case TYPED_OBJECT:
                return emitTypedObjectRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column);

            case NODE_VALUE:
                return emitNodeValueRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column);

            case DIRECT_CODEC:
                return emitDirectCodecRead(out, names, resultSet, read, column);

            default:
                throw new AssertionError(
                        read.kind());
        }
    }

    private String emitReferenceRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            String column,
            String getter) {

        String value =
                names.newName("value_" + read.column());

        out.line(
                read.targetType() +
                        " " +
                        value +
                        " = " +
                        resultSet +
                        "." +
                        getter +
                        "(" +
                        column +
                        ");");

        return value;
    }

    private String emitPrimitiveRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            String column,
            String getter,
            String primitiveType,
            String wrapperType) {

        String raw =
                names.newName("raw_" + read.column());

        out.line(
                primitiveType +
                        " " +
                        raw +
                        " = " +
                        resultSet +
                        "." +
                        getter +
                        "(" +
                        column +
                        ");");

        if (read.primitive()) {

            out.line(
                    "if (" +
                            resultSet +
                            ".wasNull()) {");

            out.indent();

            out.line(
                    "throw new org.sjf4j.exception.BindingException(" +
                            JavaWriter.stringLiteral(
                                    "SQL NULL cannot be mapped to primitive target from column '" +
                                            read.column() +
                                            "'") +
                            ");");

            out.dedent();
            out.line("}");

            return raw;
        }

        String value =
                names.newName("value_" + read.column());

        out.line(
                read.targetType() +
                        " " +
                        value +
                        " = " +
                        resultSet +
                        ".wasNull() ? null : " +
                        wrapperType +
                        ".valueOf(" +
                        raw +
                        ");");

        return value;
    }

    private String emitCharacterRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            String column) {

        String raw =
                names.newName("raw_" + read.column());

        out.line(
                "java.lang.String " +
                        raw +
                        " = " +
                        resultSet +
                        ".getString(" +
                        column +
                        ");");

        if (read.primitive()) {

            out.line(
                    "if (" +
                            raw +
                            " == null) {");

            out.indent();

            out.line(
                    "throw new org.sjf4j.exception.BindingException(" +
                            JavaWriter.stringLiteral(
                                    "SQL NULL cannot be mapped to primitive char from column '" +
                                            read.column() +
                                            "'") +
                            ");");

            out.dedent();
            out.line("}");

            emitCharacterLengthCheck(
                    out,
                    raw,
                    read.column());

            String value =
                    names.newName("value_" + read.column());

            out.line(
                    "char " +
                            value +
                            " = " +
                            raw +
                            ".charAt(0);");

            return value;
        }

        String value =
                names.newName("value_" + read.column());

        out.line(
                "java.lang.Character " +
                        value +
                        " = null;");

        out.line(
                "if (" +
                        raw +
                        " != null) {");

        out.indent();

        emitCharacterLengthCheck(
                out,
                raw,
                read.column());

        out.line(
                value +
                        " = java.lang.Character.valueOf(" +
                        raw +
                        ".charAt(0));");

        out.dedent();
        out.line("}");

        return value;
    }

    private void emitCharacterLengthCheck(
            JavaWriter out,
            String raw,
            String column) {

        out.line(
                "if (" +
                        raw +
                        ".length() != 1) {");

        out.indent();

        out.line(
                "throw new org.sjf4j.exception.BindingException(" +
                        JavaWriter.stringLiteral(
                                "JDBC column '" +
                                        column +
                                        "' cannot be mapped to char because its string value does not contain exactly one character") +
                        ");");

        out.dedent();
        out.line("}");
    }

    private String emitEnumRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            String column) {

        String raw =
                names.newName("raw_" + read.column());

        String value =
                names.newName("value_" + read.column());

        out.line(
                "java.lang.String " +
                        raw +
                        " = " +
                        resultSet +
                        ".getString(" +
                        column +
                        ");");

        out.line(
                read.targetType() +
                        " " +
                        value +
                        " = " +
                        raw +
                        " == null ? null : " +
                        read.targetType() +
                        ".valueOf(" +
                        raw +
                        ");");

        return value;
    }

    private String emitObjectRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            String column) {

        String value =
                names.newName("value_" + read.column());

        out.line(
                "java.lang.Object " +
                        value +
                        " = " +
                        resultSet +
                        ".getObject(" +
                        column +
                        ");");

        return value;
    }

    private String emitTypedObjectRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            String column) {

        String value =
                names.newName("value_" + read.column());

        out.line(
                read.targetType() +
                        " " +
                        value +
                        " = " +
                        resultSet +
                        ".getObject(" +
                        column +
                        ", " +
                        JavaExpressions.boxedClassLiteral(types, context.typeUtils, 
                                read.targetType()) +
                        ");");

        return value;
    }

    private String emitDirectCodecRead(JavaWriter out, NameAllocator names,
                                      String resultSet, JdbcCompiler.ColumnRead read,
                                      String column) {
        ValueCodecMetadata codec = read.codec();
        String rawType = codec.rawType.toString();
        String raw = names.newName("raw_" + read.column());
        String value = names.newName("value_" + read.column());

        // Read the declared wire type directly; never route through NodeMapper.
        // String is supported by every JDBC driver, whereas getObject(index,
        // String.class) is optional. JDBC 4.1 typed getObject handles boxed
        // scalar conversions; Map/List remain driver-owned raw values.
        String expression;
        if ("java.lang.String".equals(rawType)) {
            expression = resultSet + ".getString(" + column + ")";
        } else if ("java.math.BigDecimal".equals(rawType)) {
            expression = resultSet + ".getBigDecimal(" + column + ")";
        } else if ("java.math.BigInteger".equals(rawType)) {
            String decimal = names.newName("decimal_" + read.column());
            out.line("java.math.BigDecimal " + decimal + " = "
                    + resultSet + ".getBigDecimal(" + column + ");");
            expression = decimal + " == null ? null : " + decimal + ".toBigIntegerExact()";
        } else if ("java.lang.Boolean".equals(rawType)
                || "java.lang.Byte".equals(rawType)
                || "java.lang.Short".equals(rawType)
                || "java.lang.Integer".equals(rawType)
                || "java.lang.Long".equals(rawType)
                || "java.lang.Float".equals(rawType)
                || "java.lang.Double".equals(rawType)) {
            expression = resultSet + ".getObject(" + column + ", " + rawType + ".class)";
        } else {
            expression = "(" + rawType + ") " + resultSet + ".getObject(" + column + ")";
        }
        out.line(rawType + " " + raw + " = " + expression + ";");
        out.line(read.targetType() + " " + value + " = "
                + codec.declaration.getEnclosingElement() + "."
                + codec.declaration.getSimpleName() + ".rawToValue(" + raw + ");");
        return value;
    }

    private String emitNodeValueRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            String column) {

        String raw =
                names.newName("raw_" + read.column());

        String value =
                names.newName("value_" + read.column());

        out.line(
                "java.lang.Object " +
                        raw +
                        " = " +
                        resultSet +
                        ".getObject(" +
                        column +
                        ");");

        // NodeMapper.to(Class<T>) is already typed for non-parameterized targets.
        // Parameterized targets still require a cast from their erased class token.
        boolean generic = read.targetType() instanceof javax.lang.model.type.DeclaredType
                && !((javax.lang.model.type.DeclaredType) read.targetType())
                        .getTypeArguments().isEmpty();
        out.line(read.targetType() + " " + value + " = "
                + (generic ? "(" + read.targetType() + ") " : "")
                + "org.sjf4j.node.NodeMapper.to(" + raw + ", "
                + JavaExpressions.boxedClassLiteral(types, context.typeUtils, read.targetType()) + ", org.sjf4j.NodeConfig.DEFAULT);");

        return value;
    }

    /*
     * --------------------------------------------------------------
     * Target Write
     * --------------------------------------------------------------
     */

    private void emitTargetWrite(
            JavaWriter out,
            NameAllocator names,
            String root,
            TargetPlan target,
            String value) {

        List<ResolvedStep> steps =
                target.steps();

        String owner =
                root;

        for (int i = 0;
             i < steps.size() - 1;
             i++) {

            ResolvedStep step =
                    steps.get(i);

            NodeAccess access =
                    step.access();

            String next =
                    names.newName(
                            "parent");

            out.line(
                    JavaExpressions.boxedType(types, 
                            access.readType()) +
                            " " +
                            next +
                            " = " +
                            readExpression(
                                    owner,
                                    step) +
                            ";");

            out.line(
                    "if (" +
                            next +
                            " == null) {");

            out.indent();

            out.line(
                    "throw new org.sjf4j.exception.BindingException(" +
                            JavaWriter.stringLiteral(
                                    "JDBC target path parent is null: " +
                                            target.expression()) +
                            ");");

            out.dedent();
            out.line("}");

            owner =
                    next;
        }

        writeValue(
                out,
                owner,
                steps.get(
                        steps.size() - 1),
                value);
    }

    private String readExpression(String owner, ResolvedStep step) {
        return targetAccess.read(owner, step.segment(), null, step.access(),
                TargetAccessEmitter.ReadPolicy.CAST_CONTAINER);
    }

    private void writeValue(JavaWriter out, String owner, ResolvedStep step, String value) {
        targetAccess.write(out, owner, step.segment(), null, step.access(), value,
                TargetAccessEmitter.IndexPolicy.SET_ONLY);
    }

    /*
     * --------------------------------------------------------------
     * Creator
     * --------------------------------------------------------------
     */

    /*
     * --------------------------------------------------------------
     * Type / Path Helpers
     * --------------------------------------------------------------
     */

    private String cast(TypeMirror type, String expression) {
        if (type == null || types.isObject(type)) return expression;
        return JavaExpressions.cast(JavaExpressions.boxedType(types, type), expression);
    }

    private String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i != 0) {
                out.append(", ");
            }

            out.append(values.get(i));
        }

        return out.toString();
    }

}
