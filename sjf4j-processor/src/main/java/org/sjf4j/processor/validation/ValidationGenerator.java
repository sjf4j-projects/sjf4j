package org.sjf4j.processor.validation;

import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.NodeKind;
import org.sjf4j.processor.property.Property;
import org.sjf4j.processor.property.PropertyAccess;
import javax.lang.model.element.Modifier;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * First-stage compiled validator generator.
 *
 * Generates typed POJO property accessors for the root PropertiesEvaluator.
 * Other evaluators keep the existing SchemaPlan runtime behavior and state.
 */
public final class ValidationGenerator {

    private final ProcessorContext context;
    private final SchemaResourceResolver resources;

    public ValidationGenerator(ProcessorContext context) {
        this.context = context;
        this.resources = new SchemaResourceResolver(context);
    }

    public void generate(TypeElement iface) {
        if (!iface.getTypeParameters().isEmpty()) {
            context.error(iface, "@CompiledValidator cannot declare type parameters");
            return;
        }

        GeneratedClass generated = GeneratedClass.forInterface(context, iface);
        // CompiledInstances.create(Class) currently requests a RuntimeContext
        // constructor for all generated implementations.
        generated.enableRuntimeContext();

        List<SchemaField> fields = new ArrayList<SchemaField>();
        Map<String, String> bySnapshot = new LinkedHashMap<String, String>();
        List<ResolvedMethod> methods = context.methods.abstractMethods(iface);

        for (ResolvedMethod method : methods) {
            generateMethod(method, generated, fields, bySnapshot);
        }
        if (!generated.isValid()) return;

        generated.addHelper(out -> {
            out.beginBlock("private static final class Fallback");
            for (SchemaField field : fields) {
                String registryName = field.name.replace("PLAN_", "REGISTRY_");
                out.line("static final org.sjf4j.schema.SchemaRegistry " + registryName
                        + " = org.sjf4j.schema.SchemaRegistry.fromDocuments(");
                out.indent();
                out.line(stringArray(field.snapshot.uris) + ",");
                out.line(stringArray(field.snapshot.documents) + ");");
                out.dedent();
                String planExpr = registryName + ".resolve(java.net.URI.create("
                        + javaLiteral(field.snapshot.selectedUri) + "))";
                if (field.accessor != null) {
                    planExpr = "org.sjf4j.schema.CompiledSchemaSupport.specialize("
                            + planExpr + ", new " + field.name.replace("PLAN_", "ACCESS_") + "())";
                }
                out.line("static final org.sjf4j.schema.SchemaPlan " + field.name
                        + " = " + planExpr + ";");
            }
            out.endBlock();
        });
        for (SchemaField field : fields) {
            if (field.accessor != null) emitAccessor(generated, field);
        }
        generated.write();
    }

    private void generateMethod(ResolvedMethod resolved, GeneratedClass generated,
                                List<SchemaField> fields, Map<String, String> bySnapshot) {
        ExecutableElement method = resolved.declaration();
        ExecutableType signature = resolved.type();

        if (signature.getParameterTypes().size() != 1 ||
                method.getParameters().size() != 1 ||
                !context.types.isFullyResolved(signature.getParameterTypes().get(0)) ||
                !context.types.isFullyResolved(signature.getReturnType())) {
            fail(generated, method, "@CompiledValidator methods require exactly one fully resolved value parameter");
            return;
        }

        TypeMirror result = signature.getReturnType();
        String resultName = result.toString();
        boolean isBoolean = result.getKind() == TypeKind.BOOLEAN;
        boolean isVoid = result.getKind() == TypeKind.VOID;
        boolean isResult = "org.sjf4j.schema.ValidationResult".equals(resultName);

        if (!isBoolean && !isVoid && !isResult) {
            fail(generated, method, "@CompiledValidator method return type must be boolean, void, or ValidationResult");
            return;
        }

        TypeMirror parameterType = signature.getParameterTypes().get(0);
        List<Binding> bindings = resolveBindings(method, parameterType);
        if (bindings.isEmpty()) {
            fail(generated, method, "missing @ValidJsonSchema on method, parameter or parameter type");
            return;
        }

        List<String> names = new ArrayList<String>();
        for (Binding binding : bindings) {
            SchemaResourceResolver.Snapshot snapshot;
            try {
                snapshot = resources.resolve(binding.annotation, binding.ownerName, binding.originName);
            } catch (RuntimeException e) {
                fail(generated, method, "cannot compile @ValidJsonSchema: " + e.getMessage());
                return;
            }
            String signatureKey = snapshotKey(snapshot) + "\u0001" + parameterType.toString();
            String name = bySnapshot.get(signatureKey);
            if (name == null) {
                name = "PLAN_" + fields.size();
                fields.add(new SchemaField(name, snapshot, propertyAccess(parameterType), parameterType.toString()));
                bySnapshot.put(signatureKey, name);
            }
            names.add("Fallback." + name);
        }

        String type = parameterType.toString();
        String value = method.getParameters().get(0).getSimpleName().toString();

        generated.addMethod(out -> {
            out.line("@Override");
            out.beginBlock("public " + resultName + " " + method.getSimpleName()
                    + "(" + type + " " + value + ")");
            if (!parameterType.getKind().isPrimitive()) out.line("if (" + value + " == null) "
                    + (isBoolean ? "return true;" :
                       isVoid ? "return;" : "return org.sjf4j.schema.ValidationResult.SUCCESS;"));
            if (isBoolean) {
                for (String plan : names) {
                    out.line("if (!" + plan + ".isValid(" + value + ", true)) return false;");
                }
                out.line("return true;");
            } else if (isVoid) {
                for (String plan : names) {
                    out.line(plan + ".requireValid(" + value + ", true);");
                }
            } else {
                out.line("org.sjf4j.schema.ValidationResult result;");
                for (String plan : names) {
                    out.line("result = " + plan + ".validate(" + value + ", true, true);");
                    out.line("if (!result.isValid()) return result;");
                }
                out.line("return org.sjf4j.schema.ValidationResult.SUCCESS;");
            }
            out.endBlock();
        });
    }

    private List<Binding> resolveBindings(ExecutableElement method, TypeMirror parameterType) {
        List<Binding> result = new ArrayList<Binding>();

        ValidJsonSchema methodSchema = method.getAnnotation(ValidJsonSchema.class);
        if (methodSchema != null) {
            String id = method.getEnclosingElement().toString() + "/" + method.getSimpleName();
            result.add(new Binding(methodSchema, ownerName(parameterType), id));
            return result;
        }

        VariableElement param = method.getParameters().get(0);
        ValidJsonSchema parameterSchema = param.getAnnotation(ValidJsonSchema.class);
        if (parameterSchema != null) {
            String id = method.getEnclosingElement().toString() + "/" + method.getSimpleName();
            result.add(new Binding(parameterSchema, ownerName(parameterType), id));
            return result;
        }

        Element element = context.typeUtils.asElement(parameterType);
        if (!(element instanceof TypeElement)) return result;
        collectTypeBindings((TypeElement) element, result);
        return result;
    }

    private void collectTypeBindings(TypeElement type, List<Binding> result) {
        TypeMirror parent = type.getSuperclass();
        if (parent instanceof DeclaredType) {
            Element parentType = ((DeclaredType) parent).asElement();
            if (parentType instanceof TypeElement &&
                    !"java.lang.Object".equals(parentType.toString())) {
                collectTypeBindings((TypeElement) parentType, result);
            }
        }

        ValidJsonSchema schema = type.getAnnotation(ValidJsonSchema.class);
        if (schema != null) {
            String name = type.getQualifiedName().toString();
            result.add(new Binding(schema, name, name));
        }
    }

    private String ownerName(TypeMirror parameterType) {
        Element e = context.typeUtils.asElement(parameterType);
        return e instanceof TypeElement ? ((TypeElement) e).getQualifiedName().toString()
                : parameterType.toString();
    }


    /**
     * Generate direct public getter calls only. A runtime metadata check ensures
     * that names and getters exactly match OBNT's readable properties.
     */
    private List<Property> propertyAccess(TypeMirror type) {
        if (context.types.nodeKind(type) != NodeKind.OBJECT_POJO) return null;
        Map<String, Property> found = context.properties.resolve(type);
        if (found.isEmpty()) return null;
        List<Property> properties = new ArrayList<Property>();
        for (Property property : found.values()) {
            PropertyAccess read = property.read();
            if (read == null || !read.isMethod() ||
                    !read.member().getModifiers().contains(Modifier.PUBLIC)) continue;
            if (!((ExecutableElement) read.member()).getThrownTypes().isEmpty()) continue;
            properties.add(property);
        }
        return properties.isEmpty() ? null : properties;
    }

    private void emitAccessor(GeneratedClass generated, SchemaField field) {
        generated.addHelper(out -> {
            String clazz = field.accessorType;
            String name = field.name.replace("PLAN_", "ACCESS_");
            out.beginBlock("private static final class " + name
                    + " implements org.sjf4j.schema.CompiledPropertyAccess");
            out.line("private static final String[] NAMES = "
                    + stringArray(propertyStrings(field.accessor, false)) + ";");
            out.line("private static final String[] GETTERS = "
                    + stringArray(propertyStrings(field.accessor, true)) + ";");
            out.line("@Override public Class<?> type() { return " + clazz + ".class; }");
            out.line("@Override public String[] names() { return NAMES; }");
            out.line("@Override public String[] getterNames() { return GETTERS; }");
            out.line("@Override public Object read(Object node, int index) {");
            out.indent();
            out.line(clazz + " value = (" + clazz + ") node;");
            out.beginBlock("switch (index)");
            for (int i = 0; i < field.accessor.size(); i++) {
                String getter = field.accessor.get(i).read().memberName();
                out.line("case " + i + ": return value." + getter + "();");
            }
            out.line("default: throw new IllegalArgumentException(\"unknown compiled property index: \" + index);");
            out.endBlock();
            out.dedent();
            out.line("}");
            out.endBlock();
        });
    }

    private static String[] propertyStrings(List<Property> properties, boolean getter) {
        String[] values = new String[properties.size()];
        for (int i = 0; i < values.length; i++) {
            Property p = properties.get(i);
            values[i] = getter ? p.read().memberName() : p.name();
        }
        return values;
    }

    private static String snapshotKey(SchemaResourceResolver.Snapshot snapshot) {
        StringBuilder key = new StringBuilder();
        key.append(snapshot.selectedUri).append('\u0000');
        for (int i = 0; i < snapshot.uris.length; i++) {
            key.append(snapshot.uris[i]).append('\u0000')
                    .append(snapshot.documents[i]).append('\u0000');
        }
        return key.toString();
    }

    private static String stringArray(String[] strings) {
        StringBuilder result = new StringBuilder("new String[]{");
        for (int i = 0; i < strings.length; i++) {
            if (i > 0) result.append(", ");
            result.append(javaLiteral(strings[i]));
        }
        return result.append('}').toString();
    }

    private static String javaLiteral(String value) {
        StringBuilder sb = new StringBuilder();
        int limit = 6000;
        boolean longValue = value.length() > limit;
        if (longValue) sb.append("new StringBuilder(").append(value.length()).append(")");
        for (int start = 0; start < value.length(); start += limit) {
            if (longValue) sb.append(".append(");
            else if (start > 0) sb.append(" + ");
            sb.append('"');
            for (int i = start; i < Math.min(value.length(), start + limit); i++) {
                char ch = value.charAt(i);
                if (ch == '\\') sb.append("\\\\");
                else if (ch == '"') sb.append("\\\"");
                else if (ch == '\n') sb.append("\\n");
                else if (ch == '\r') sb.append("\\r");
                else if (ch == '\t') sb.append("\\t");
                else if (ch < 32 || ch == 0x2028 || ch == 0x2029) {
                    String hex = Integer.toHexString(ch);
                    sb.append("\\u");
                    for (int j = hex.length(); j < 4; j++) sb.append('0');
                    sb.append(hex);
                } else sb.append(ch);
            }
            sb.append('"');
            if (longValue) sb.append(")");
        }
        if (longValue) sb.append(".toString()");
        return sb.length() == 0 ? "\"\"" : sb.toString();
    }

    private void fail(GeneratedClass generated, Element method, String message) {
        context.error(method, message);
        generated.invalidate();
    }

    private static final class Binding {
        final ValidJsonSchema annotation;
        final String ownerName;
        final String originName;

        Binding(ValidJsonSchema annotation, String ownerName, String originName) {
            this.annotation = annotation;
            this.ownerName = ownerName;
            this.originName = originName;
        }
    }

    private static final class SchemaField {
        final String name;
        final SchemaResourceResolver.Snapshot snapshot;
        final List<Property> accessor;
        final String accessorType;

        SchemaField(String name, SchemaResourceResolver.Snapshot snapshot,
                    List<Property> accessor, String accessorType) {
            this.name = name;
            this.snapshot = snapshot;
            this.accessor = accessor;
            this.accessorType = accessorType;
        }
    }
}
