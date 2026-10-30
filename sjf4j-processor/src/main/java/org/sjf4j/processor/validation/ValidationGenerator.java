package org.sjf4j.processor.validation;

import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.NodeKind;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.ObjectSchema;
import org.sjf4j.processor.property.Property;
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
 * Emits direct Java property checks for fully understood simple schemas.
 * Every other schema uses the original SchemaPlan, preserving all runtime semantics.
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
                if (!field.usedFallback) continue;
                String registry = field.name.replace("PLAN_", "REGISTRY_");
                out.line("private static final org.sjf4j.schema.SchemaRegistry " + registry
                        + " = new org.sjf4j.schema.SchemaRegistry();");
                out.line("static final org.sjf4j.schema.SchemaPlan " + field.name
                        + " = load" + field.name + "();");
                for (Subplan sub : field.subplans) {
                    out.line("static final org.sjf4j.schema.SchemaPlan " + sub.fieldName
                            + " = " + registry + ".resolve(java.net.URI.create("
                            + javaLiteral(sub.uri) + "));");
                }
                out.beginBlock("private static org.sjf4j.schema.SchemaPlan load" + field.name + "()");
                out.beginBlock("try");
                for (int i = 0; i < field.snapshot.uris.length; i++) {
                    String uri = "java.net.URI.create(" + javaLiteral(field.snapshot.uris[i]) + ")";
                    if (i == 0 && !field.annotation.value().isEmpty()) {
                        out.line(registry + ".index(" + uri
                                + ", org.sjf4j.schema.JsonSchema.fromJson("
                                + field.annotationExpression + ".value()));");
                    } else {
                        java.net.URI resourceUri = java.net.URI.create(field.snapshot.uris[i]);
                        String resource;
                        if ("classpath".equals(resourceUri.getScheme())) {
                            resource = "org.sjf4j.schema.SchemaRegistry.class.getClassLoader()"
                                    + ".getResourceAsStream(" + javaLiteral(resourceUri.getPath().replaceFirst("^/", "")) + ")";
                        } else {
                            resource = "java.nio.file.Files.newInputStream(java.nio.file.Paths.get(" + uri + "))";
                        }
                        out.beginBlock("try (java.io.InputStream input = " + resource + ")");
                        out.line("if (input == null) throw new IllegalStateException("
                                + javaLiteral("schema resource not found: " + resourceUri) + ");");
                        out.line(registry + ".index(" + uri
                                + ", org.sjf4j.Sjf4j.global().fromJson(input, org.sjf4j.schema.JsonSchema.class));");
                        out.endBlock();
                    }
                }
                out.line("return " + registry + ".resolve(java.net.URI.create("
                        + javaLiteral(field.snapshot.selectedUri) + "));");
                out.endBlock();
                out.beginBlock("catch (Exception e)");
                out.line("throw new ExceptionInInitializerError(e);");
                out.endBlock();
                out.endBlock();
            }
            out.endBlock();
        });
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
            String signatureKey = snapshotKey(snapshot) + "\u0001" + parameterType.toString()
                    + "\u0001" + binding.annotationExpression;
            String name = bySnapshot.get(signatureKey);
            if (name == null) {
                name = "PLAN_" + fields.size();
                fields.add(new SchemaField(name, snapshot, binding.annotation, binding.annotationExpression));
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
            if (isBoolean && bindings.size() == 1) {
                SchemaField bound = null;
                String selected = names.get(0).substring("Fallback.".length());
                for (SchemaField field : fields) if (field.name.equals(selected)) bound = field;
                if (bound != null && emitBoolean(out, bound.snapshot)) {
                    out.endBlock();
                    return;
                }
                if (bound != null && emitDirect(out, parameterType, value, bound)) {
                    if (!bound.subplans.isEmpty()) bound.usedFallback = true;
                    out.line("return true;");
                    out.endBlock();
                    return;
                }
            }
            for (SchemaField field : fields) {
                if (names.contains("Fallback." + field.name)) field.usedFallback = true;
            }
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

        String methodLookup = method.getEnclosingElement().toString() + ".class.getMethod("
                + javaLiteral(method.getSimpleName().toString()) + ", "
                + context.typeUtils.erasure(parameterType) + ".class)";
        ValidJsonSchema methodSchema = method.getAnnotation(ValidJsonSchema.class);
        if (methodSchema != null) {
            String id = method.getEnclosingElement().toString() + "/" + method.getSimpleName();
            result.add(new Binding(methodSchema, ownerName(parameterType), id,
                    methodLookup + ".getAnnotation(org.sjf4j.annotation.schema.ValidJsonSchema.class)"));
            return result;
        }

        VariableElement param = method.getParameters().get(0);
        ValidJsonSchema parameterSchema = param.getAnnotation(ValidJsonSchema.class);
        if (parameterSchema != null) {
            String id = method.getEnclosingElement().toString() + "/" + method.getSimpleName();
            result.add(new Binding(parameterSchema, ownerName(parameterType), id,
                    methodLookup + ".getParameters()[0].getAnnotation(org.sjf4j.annotation.schema.ValidJsonSchema.class)"));
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
            result.add(new Binding(schema, name, name,
                    name + ".class.getDeclaredAnnotation(org.sjf4j.annotation.schema.ValidJsonSchema.class)"));
        }
    }

    private String ownerName(TypeMirror parameterType) {
        Element e = context.typeUtils.asElement(parameterType);
        return e instanceof TypeElement ? ((TypeElement) e).getQualifiedName().toString()
                : parameterType.toString();
    }




    /**
     * Boolean resource schemas, including a root that refers exclusively to a
     * boolean resource, can be emitted without instantiating the fallback.
     */
    private boolean emitBoolean(org.sjf4j.processor.code.JavaWriter out,
                                SchemaResourceResolver.Snapshot snapshot) {
        String literal = snapshot.documents[0].trim();
        if (!"true".equals(literal) && !"false".equals(literal)) {
            JsonSchema root = JsonSchema.fromJson(literal);
            if (!(root instanceof ObjectSchema)) return false;
            ObjectSchema os = (ObjectSchema) root;
            if (os.size() != 1 || os.getString("$ref") == null) return false;
            java.net.URI base = java.net.URI.create(snapshot.uris[0]);
            java.net.URI target = base.resolve(os.getString("$ref"));
            if (target.getFragment() != null) return false;
            for (int i = 0; i < snapshot.uris.length; i++) {
                if (snapshot.uris[i].equals(target.toString())) {
                    literal = snapshot.documents[i].trim();
                    break;
                }
            }
        }
        if (!"true".equals(literal) && !"false".equals(literal)) return false;
        out.line("return " + literal + ";");
        return true;
    }

    /**
     * Directly compile known String constraints. Other property subtrees use
     * their existing SchemaPlan via JSON Pointer, but only when the root
     * consists of type + properties (no context-dependent root keywords).
     */
    private boolean emitDirect(org.sjf4j.processor.code.JavaWriter out,
                               TypeMirror type, String variable, SchemaField field) {
        SchemaResourceResolver.Snapshot snapshot = field.snapshot;
        if (snapshot.documents.length != 1 ||
                !snapshot.selectedUri.equals(snapshot.uris[0])) return false;
        if (context.types.nodeKind(type) != NodeKind.OBJECT_POJO) return false;
        TypeElement owner = context.types.typeElement(type);
        if (owner == null || !owner.getModifiers().contains(Modifier.FINAL) ||
                !owner.getModifiers().contains(Modifier.PUBLIC) ||
                owner.getAnnotation(org.sjf4j.annotation.node.NodeObject.class) != null) return false;

        JsonSchema parsed = JsonSchema.fromJson(snapshot.documents[0]);
        if (!(parsed instanceof ObjectSchema)) return false;
        ObjectSchema root = (ObjectSchema) parsed;
        for (String key : root.keySet()) {
            if (!key.equals("type") && !key.equals("properties")) return false;
        }
        if (!"object".equals(root.getString("type"))) return false;
        Object raw = root.getNode("properties");
        if (raw == null) return false;
        org.sjf4j.JsonObject propertyMap = org.sjf4j.Nodes.toJsonObject(raw);
        Map<String, Property> javaProperties = context.properties.resolve(type);
        if (javaProperties.isEmpty() || propertyMap.size() != javaProperties.size()) return false;

        for (Property property : javaProperties.values()) {
            if (!propertyMap.containsKey(property.name()) ||
                    property.read() == null || !property.read().isMethod() ||
                    !property.read().member().getModifiers().contains(Modifier.PUBLIC) ||
                    !((ExecutableElement) property.read().member()).getThrownTypes().isEmpty() ||
                    !property.name().matches("[a-zA-Z0-9_]+")) return false;
        }

        java.util.List<String> statements = new java.util.ArrayList<String>();
        java.util.List<Subplan> subplans = new java.util.ArrayList<Subplan>();
        int directCount = 0;
        int index = 0;
        for (Map.Entry<String, Property> entry : javaProperties.entrySet()) {
            Object schemaValue = propertyMap.getNode(entry.getKey());
            org.sjf4j.JsonObject schema =
                    org.sjf4j.JsonType.of(schemaValue) == org.sjf4j.JsonType.OBJECT
                            ? org.sjf4j.Nodes.toJsonObject(schemaValue) : null;
            boolean stringSchema = schema != null &&
                    "string".equals(schema.getString("type")) &&
                    "java.lang.String".equals(entry.getValue().read().type().toString());
            if (stringSchema) {
                for (String key : schema.keySet()) {
                    if (!key.equals("type") && !key.equals("minLength") &&
                            !key.equals("maxLength")) stringSchema = false;
                }
            }
            Integer min = null;
            Integer max = null;
            if (stringSchema) {
                min = schema.getInt("minLength");
                max = schema.getInt("maxLength");
                if (min != null && min < 0 || max != null && max < 0) stringSchema = false;
            }

            String local = "_property" + index;
            statements.add((stringSchema ? "String " : "Object ") + local
                    + " = " + variable + "." + entry.getValue().read().memberName() + "();");
            if (stringSchema) {
                directCount++;
                statements.add("if (" + local + " == null) return false;");
                if (min != null || max != null) {
                    String count = "_length" + index;
                    statements.add("int " + count + " = " + local
                            + ".codePointCount(0, " + local + ".length());");
                    if (min != null) statements.add("if (" + count + " < " + min + ") return false;");
                    if (max != null) statements.add("if (" + count + " > " + max + ") return false;");
                }
            } else {
                String subName = field.name + "_SUB_" + index;
                String fragment = "#/properties/" + entry.getKey();
                subplans.add(new Subplan(subName, snapshot.selectedUri + fragment));
                statements.add("if (!Fallback." + subName + ".isValid(" + local
                        + ", true)) return false;");
            }
            index++;
        }
        if (directCount == 0) return false;
        field.subplans.addAll(subplans);
        for (String line : statements) out.line(line);
        return true;
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
        final String annotationExpression;

        Binding(ValidJsonSchema annotation, String ownerName, String originName,
                String annotationExpression) {
            this.annotation = annotation;
            this.ownerName = ownerName;
            this.originName = originName;
            this.annotationExpression = annotationExpression;
        }
    }

    private static final class Subplan {
        final String fieldName;
        final String uri;

        Subplan(String fieldName, String uri) {
            this.fieldName = fieldName;
            this.uri = uri;
        }
    }

    private static final class SchemaField {
        final String name;
        final SchemaResourceResolver.Snapshot snapshot;
        final ValidJsonSchema annotation;
        final String annotationExpression;
        final List<Subplan> subplans = new ArrayList<Subplan>();
        boolean usedFallback;

        SchemaField(String name, SchemaResourceResolver.Snapshot snapshot,
                    ValidJsonSchema annotation, String annotationExpression) {
            this.name = name;
            this.snapshot = snapshot;
            this.annotation = annotation;
            this.annotationExpression = annotationExpression;
        }
    }
}
