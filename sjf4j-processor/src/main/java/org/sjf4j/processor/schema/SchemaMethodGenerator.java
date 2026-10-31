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
import java.util.List;

/**
 * Compiles and emits individual methods of a compiled validator.
 *
 * <p>Method contracts and schema bindings remain here; semantic compilation,
 * source emission and fallback plan management are delegated.</p>
 */
final class SchemaMethodGenerator {

    private final ProcessorContext context;
    private final SchemaResourceResolver resources;
    private final SchemaFallbackEmitter fallbacks = new SchemaFallbackEmitter();
    private int methodSerial;

    SchemaMethodGenerator(ProcessorContext context) {
        this.context = context;
        this.resources = new SchemaResourceResolver(context);
    }

    void emitFallbacks(GeneratedClass generated) {
        fallbacks.emit(generated);
    }

    /** Analyzes and generates one validator method, sharing plans across the interface. */
    void generate(ResolvedMethod resolved, GeneratedClass generated,
                  boolean allowFallback) {
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

        List<SchemaFallbackEmitter.Plan> plans = new ArrayList<SchemaFallbackEmitter.Plan>();
        for (Binding binding : bindings) {
            SchemaResourceResolver.Snapshot snapshot;
            try {
                snapshot = resources.resolve(binding.annotation, binding.ownerName, binding.originName);
            } catch (RuntimeException e) {
                fail(generated, method, "cannot compile @ValidJsonSchema: " + e.getMessage());
                return;
            }
            plans.add(fallbacks.bind(snapshot, binding.annotation,
                    binding.annotationExpression));
        }

        String type = parameterType.toString();
        String value = method.getParameters().get(0).getSimpleName().toString();
        String strictParam = parameterCount == 2
                ? method.getParameters().get(1).getSimpleName().toString() : "false";
        String parameterDeclaration = type + " " + value + (parameterCount == 2
                ? ", boolean " + strictParam : "");

        // Compile a semantic tree before emitting Java. Only a complete
        // root check is eligible for the native boolean path.
        SchemaFallbackEmitter.Plan bound = null;
        String booleanLiteral = null;
        SchemaCheck direct = null;
        if (isBoolean && plans.size() == 1) {
            bound = plans.get(0);
            booleanLiteral = compileBoolean(bound.snapshot);
            if (booleanLiteral == null) {
                SchemaCompiler compiler = new SchemaCompiler(context);
                direct = compiler.compileObject(parameterType, bound.snapshot);
                if (direct == null) direct = compiler.compileRoot(parameterType, bound.snapshot);
                if (direct != null && direct.kind == SchemaCheck.Kind.REJECT) {
                    booleanLiteral = "false";
                    direct = null;
                }
            }
        }

        boolean fallback = booleanLiteral == null &&
                (direct == null || direct.hasFallback());
        if (fallback && !allowFallback) {
            fail(generated, method, "@CompiledValidator(fallback=false) cannot fully compile this schema and method");
            return;
        }
        List<String> emitted = direct == null ? null
                : new SchemaEmitter(generated, "M" + methodSerial++).emitRoot(
                        direct, SchemaValue.of(context, parameterType), value, bound, strictParam);
        if (fallback && emitted == null) {
            for (SchemaFallbackEmitter.Plan plan : plans) plan.useRoot();
        }

        final String literal = booleanLiteral;
        final List<String> checks = emitted;
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
                for (String statement : checks) out.line(statement);
                out.line("return true;");
            } else if (isBoolean) {
                for (SchemaFallbackEmitter.Plan plan : plans) {
                    out.line("if (!" + plan.rootReference() + ".isValid(" + value + ", " + strictParam + ")) return false;");
                }
                out.line("return true;");
            } else if (isVoid) {
                for (SchemaFallbackEmitter.Plan plan : plans) {
                    out.line(plan.rootReference() + ".requireValid(" + value + ", " + strictParam + ");");
                }
            } else if (plans.size() == 1) {
                out.line("return " + plans.get(0).rootReference() + ".validate(" + value + ", false, " + strictParam + ");");
            } else {
                // All annotated schemas participate; report all failures in
                // parent-to-child order instead of stopping at the first failure.
                out.line("java.util.List<org.sjf4j.schema.ValidationResult.Error> errors = new java.util.ArrayList<>();");
                out.line("boolean valid = true;");
                out.line("org.sjf4j.schema.ValidationResult result;");
                for (SchemaFallbackEmitter.Plan plan : plans) {
                    out.line("result = " + plan.rootReference() + ".validate(" + value + ", false, " + strictParam + ");");
                    out.line("if (!result.isValid()) valid = false;");
                    out.line("errors.addAll(result.getErrors());");
                }
                out.line("return errors.isEmpty() && valid ? org.sjf4j.schema.ValidationResult.SUCCESS");
                out.line("        : new org.sjf4j.schema.ValidationResult(valid, errors);");
            }
            out.endBlock();
        });
    }

    private List<Binding> resolveBindings(ExecutableElement method, TypeMirror parameterType) {
        List<Binding> result = new ArrayList<Binding>();

        String methodLookup = method.getEnclosingElement().toString() + ".class.getMethod("
                + SchemaEmitter.javaLiteral(method.getSimpleName().toString()) + ", "
                + context.typeUtils.erasure(parameterType) + ".class"
                + (method.getParameters().size() == 2 ? ", boolean.class" : "") + ")";
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


}
