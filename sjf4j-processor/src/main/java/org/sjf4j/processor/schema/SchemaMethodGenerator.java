package org.sjf4j.processor.schema;

import org.sjf4j.annotation.schema.ValidJsonSchema;
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
 * Compiles and emits individual methods of a compiled validator.
 *
 * <p>Phase one keeps the existing keyword compilation, direct checks and
 * fallback emission together to preserve generated-code behavior. Interface
 * orchestration belongs to {@link SchemaGenerator}.</p>
 */
final class SchemaMethodGenerator {

    private final ProcessorContext context;
    private final SchemaResourceResolver resources;
    private final List<SchemaField> fields = new ArrayList<SchemaField>();
    private final Map<String, String> bySnapshot = new LinkedHashMap<String, String>();

    SchemaMethodGenerator(ProcessorContext context) {
        this.context = context;
        this.resources = new SchemaResourceResolver(context);
    }

    /** Emits shared fallback plans after all methods have been analyzed. */
    void emitFallbacks(GeneratedClass generated) {
        generated.addHelper(out -> {
            out.beginBlock("private static final class Fallback");
            for (SchemaField field : fields) {
                if (!field.usedFallback) continue;
                out.beginBlock("private static final class " + field.name);
                String registry = "REGISTRY";
                out.line("private static final org.sjf4j.schema.SchemaRegistry " + registry
                        + " = new org.sjf4j.schema.SchemaRegistry();");
                out.line("static final org.sjf4j.schema.SchemaPlan ROOT = load();");
                for (SchemaEmitter.Subplan sub : field.subplans) {
                    out.line("static final org.sjf4j.schema.SchemaPlan " + sub.fieldName
                            + " = " + registry + ".resolve(java.net.URI.create("
                            + javaLiteral(sub.uri) + "));");
                }
                out.beginBlock("private static org.sjf4j.schema.SchemaPlan load()");
                out.beginBlock("try");
                out.line("org.sjf4j.schema.SchemaLoader loader = new org.sjf4j.schema.SchemaLoader.Local(org.sjf4j.schema.SchemaRegistry.DEFAULT_JSON_SCHEMA_DIR);");
                for (int i = 0; i < field.snapshot.uris.length; i++) {
                    String uri = "java.net.URI.create(" + javaLiteral(field.snapshot.uris[i]) + ")";
                    if (i == 0 && !field.annotation.value().isEmpty()) {
                        out.line(registry + ".index(" + uri
                                + ", org.sjf4j.schema.JsonSchema.fromJson("
                                + field.annotationExpression + ".value()));");
                    } else {
                        out.line(registry + ".index(" + uri + ", loader.load(" + uri + "));");
                    }
                }
                out.line("return " + registry + ".resolve(java.net.URI.create("
                        + javaLiteral(field.snapshot.selectedUri) + "));");
                out.endBlock();
                out.beginBlock("catch (Exception e)");
                out.line("throw new ExceptionInInitializerError(e);");
                out.endBlock();
                out.endBlock();
                out.endBlock();
            }
            out.endBlock();
        });
    }

    /** Analyzes and generates one validator method, sharing plans across the interface. */
    void generate(ResolvedMethod resolved, GeneratedClass generated,
                  boolean allowFallback, boolean nullAsMissing) {
        ExecutableElement method = resolved.declaration();
        ExecutableType signature = resolved.type();

        int parameterCount = signature.getParameterTypes().size();
        if (parameterCount < 1 || parameterCount > 2 ||
                method.getParameters().size() != parameterCount ||
                (parameterCount == 2 && signature.getParameterTypes().get(1).getKind() != TypeKind.BOOLEAN) ||
                !context.types.isFullyResolved(signature.getParameterTypes().get(0)) ||
                !context.types.isFullyResolved(signature.getReturnType())) {
            fail(generated, method, "@CompiledValidator methods require a value and an optional primitive boolean strictFormat parameter");
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
            names.add("Fallback." + name + ".ROOT");
        }

        String type = parameterType.toString();
        String value = method.getParameters().get(0).getSimpleName().toString();
        String strictParam = parameterCount == 2
                ? method.getParameters().get(1).getSimpleName().toString() : "true";
        String parameterDeclaration = type + " " + value + (parameterCount == 2
                ? ", boolean " + strictParam : "");

        // Compile a semantic tree before emitting Java. Only a complete
        // root check is eligible for the native boolean path.
        SchemaField bound = null;
        String booleanLiteral = null;
        SchemaCheck.ObjectCheck direct = null;
        if (isBoolean && bindings.size() == 1) {
            String selected = names.get(0).substring("Fallback.".length(),
                    names.get(0).length() - ".ROOT".length());
            for (SchemaField field : fields) {
                if (field.name.equals(selected)) bound = field;
            }
            if (bound != null) {
                booleanLiteral = compileBoolean(bound.snapshot);
                if (booleanLiteral == null) {
                    direct = new SchemaCompiler(context).compileObject(
                            parameterType, bound.snapshot, nullAsMissing);
                }
            }
        }

        boolean fallback = booleanLiteral == null &&
                (direct == null || direct.hasFallback());
        if (fallback && !allowFallback) {
            fail(generated, method, "@CompiledValidator(fallback=false) cannot fully compile this schema and method");
            return;
        }
        SchemaEmitter.Result emitted = direct == null ? null
                : new SchemaEmitter().emit(direct, value, "Fallback." + bound.name, strictParam);
        if (emitted != null) {
            for (SchemaEmitter.Subplan sub : emitted.subplans) {
                boolean present = false;
                for (SchemaEmitter.Subplan existing : bound.subplans) {
                    if (existing.fieldName.equals(sub.fieldName)) { present = true; break; }
                }
                if (!present) bound.subplans.add(sub);
            }
            if (direct.hasFallback()) bound.usedFallback = true;
        } else if (fallback) {
            for (SchemaField field : fields) {
                if (names.contains("Fallback." + field.name + ".ROOT")) field.usedFallback = true;
            }
        }

        final String literal = booleanLiteral;
        final SchemaEmitter.Result checks = emitted;
        generated.addMethod(out -> {
            out.line("@Override");
            out.beginBlock("public " + resultName + " " + method.getSimpleName()
                    + "(" + parameterDeclaration + ")");
            if (!parameterType.getKind().isPrimitive()) out.line("if (" + value + " == null) "
                    + (isBoolean ? "return true;" :
                       isVoid ? "return;" : "return org.sjf4j.schema.ValidationResult.SUCCESS;"));
            if (literal != null) {
                out.line("return " + literal + ";");
            } else if (checks != null) {
                for (String statement : checks.statements) out.line(statement);
                out.line("return true;");
            } else if (isBoolean) {
                for (String plan : names) {
                    out.line("if (!" + plan + ".isValid(" + runtimeInput(value, nullAsMissing) + ", " + strictParam + ")) return false;");
                }
                out.line("return true;");
            } else if (isVoid) {
                for (String plan : names) {
                    out.line(plan + ".requireValid(" + runtimeInput(value, nullAsMissing) + ", " + strictParam + ");");
                }
            } else if (names.size() == 1) {
                out.line("return " + names.get(0) + ".validate(" + runtimeInput(value, nullAsMissing) + ", false, " + strictParam + ");");
            } else {
                // All annotated schemas participate; report all failures in
                // parent-to-child order instead of stopping at the first failure.
                out.line("java.util.List<org.sjf4j.schema.ValidationMessage> messages = new java.util.ArrayList<>();");
                out.line("boolean valid = true;");
                out.line("org.sjf4j.schema.ValidationResult result;");
                for (String plan : names) {
                    out.line("result = " + plan + ".validate(" + runtimeInput(value, nullAsMissing) + ", false, " + strictParam + ");");
                    out.line("if (!result.isValid()) valid = false;");
                    out.line("messages.addAll(result.getMessages());");
                }
                out.line("return messages.isEmpty() && valid ? org.sjf4j.schema.ValidationResult.SUCCESS");
                out.line("        : new org.sjf4j.schema.ValidationResult(valid, messages,");
                out.line("                messages.isEmpty() ? null : messages.get(messages.size() - 1));");
            }
            out.endBlock();
        });
    }

    private static String runtimeInput(String value, boolean nullAsMissing) {
        return nullAsMissing
                ? "org.sjf4j.schema.SchemaValidationInput.omitNullMembers(" + value + ")"
                : value;
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
    private String compileBoolean(SchemaResourceResolver.Snapshot snapshot) {
        String literal = snapshot.documents[0].trim();
        if (!"true".equals(literal) && !"false".equals(literal)) {
            Object parsed = org.sjf4j.Sjf4j.global().fromJson(literal);
            if (!org.sjf4j.JsonType.of(parsed).isObject()) return null;
            org.sjf4j.JsonObject os = org.sjf4j.Nodes.toJsonObject(parsed);
            if (os.size() != 1 || os.getString("$ref") == null) return null;
            java.net.URI base = java.net.URI.create(snapshot.uris[0]);
            java.net.URI target = base.resolve(os.getString("$ref"));
            if (target.getFragment() != null) return null;
            for (int i = 0; i < snapshot.uris.length; i++) {
                if (snapshot.uris[i].equals(target.toString())) {
                    literal = snapshot.documents[i].trim();
                    break;
                }
            }
        }
        if (!"true".equals(literal) && !"false".equals(literal)) return null;
        return literal;
    }

    /**
     * Compiles a closed, statically readable POJO against an object schema.
     * Root metadata and local definitions do not affect object evaluation.
     * Unsupported property constraints are delegated to a cached fragment plan,
     * never discarded.
     */
    private static String snapshotKey(SchemaResourceResolver.Snapshot snapshot) {
        StringBuilder key = new StringBuilder();
        key.append(snapshot.selectedUri).append('\u0000');
        for (int i = 0; i < snapshot.uris.length; i++) {
            key.append(snapshot.uris[i]).append('\u0000')
                    .append(snapshot.documents[i]).append('\u0000');
        }
        return key.toString();
    }

    static String javaLiteral(String value) {
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

    private static final class SchemaField {
        final String name;
        final SchemaResourceResolver.Snapshot snapshot;
        final ValidJsonSchema annotation;
        final String annotationExpression;
        final List<SchemaEmitter.Subplan> subplans = new ArrayList<SchemaEmitter.Subplan>();
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
