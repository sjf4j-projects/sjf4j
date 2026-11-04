package org.sjf4j.processor.schema;

import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.method.ResolvedMethod;
import org.sjf4j.schema.SchemaRegistry;

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
import java.util.Map;

/**
 * Compiles and emits individual methods of a compiled validator.
 *
 * <p>Method contracts and schema bindings remain here; semantic compilation,
 * source emission and fallback plan management are delegated.</p>
 */
final class SchemaMethodGenerator {

    private final ProcessorContext context;
    private final SchemaResourceResolver resources;
    private final SchemaFallbackEmitter fallbacks;
    private int methodSerial;

    SchemaMethodGenerator(ProcessorContext context, GeneratedClass generated,
                          String registryDir, String registryBaseUri,
                          Map<String, SchemaRegistry> registryCache) {
        this.context = context;
        this.resources = new SchemaResourceResolver(context, registryDir, registryBaseUri, registryCache);
        this.fallbacks = new SchemaFallbackEmitter(generated.names(), registryDir, registryBaseUri);
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

        // Aggregated diagnostics intentionally use the runtime SchemaPlan.
        // An explicit fallback=true is required for this return type.
        if (isResult && !allowFallback) {
            fail(generated, method,
                    "ValidationResult methods require @CompiledValidator(fallback=true): "
                    + "aggregating validation is implemented by SchemaPlan");
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
                snapshot = resources.resolve(binding.annotation, binding.ownerName, binding.originName, method);
            } catch (RuntimeException e) {
                fail(generated, method, "cannot compile @ValidJsonSchema: " + e.getMessage());
                return;
            }
            plans.add(fallbacks.bind(snapshot, binding.annotation,
                    binding.annotationExpression));
        }

        NameAllocator locals = generated.names().locals(method);
        String type = parameterType.toString();
        String value = method.getParameters().get(0).getSimpleName().toString();
        String strictParam = parameterCount == 2
                ? method.getParameters().get(1).getSimpleName().toString() : "false";
        String parameterDeclaration = type + " " + value + (parameterCount == 2
                ? ", boolean " + strictParam : "");

        // Each inherited type binding is an independent assertion. Compile all
        // bindings before emitting anything: a single unsupported schema means
        // the method falls back as a whole, preserving the existing contract.
        SchemaFallbackEmitter.Plan bound = plans.size() == 1 ? plans.get(0) : null;
        String booleanLiteral = null;
        SchemaCheck direct = null;
        String fallbackReason = null;
        List<SchemaCheck> directBindings = new ArrayList<SchemaCheck>();
        if (isBoolean || isVoid) {
            for (int i = 0; i < plans.size(); i++) {
                SchemaFallbackEmitter.Plan plan = plans.get(i);
                String literal = compileBoolean(plan.snapshot);
                SchemaCheck compiled;
                if (literal != null) {
                    compiled = "true".equals(literal) ? new SchemaCheck.Pass()
                            : new SchemaCheck.Reject(
                                    SchemaCompiler.schemaDiagnostic(plan.snapshot.selectedUri, "false"));
                } else {
                    SchemaCompiler compiler = new SchemaCompiler(context);
                    compiled = compiler.compileRoot(parameterType, plan.snapshot);
                    if (compiled == null || compiled.hasFallback()) {
                        fallbackReason = "schema binding " + bindings.get(i).ownerName
                                + " (" + plan.snapshot.selectedUri + ") requires runtime fallback: "
                                + compiler.fallbackReason();
                        break;
                    }
                }
                directBindings.add(compiled);
            }
            if (directBindings.size() == plans.size()) {
                if (plans.size() == 1) {
                    direct = directBindings.get(0);
                    if (direct.kind == SchemaCheck.Kind.PASS) {
                        booleanLiteral = "true";
                        direct = null;
                    } else if (isBoolean && direct.kind == SchemaCheck.Kind.REJECT) {
                        booleanLiteral = "false";
                        direct = null;
                    }
                }
            } else {
                directBindings.clear();
            }
        }

        boolean fallback = (isResult || directBindings.isEmpty())
                && booleanLiteral == null;
        if (fallback) {
            if (fallbackReason == null) {
                fallbackReason = isResult
                        ? "ValidationResult aggregation requires runtime SchemaPlan"
                        : "unsupported schema construct requires runtime evaluation";
            }
            if (!allowFallback) {
                fail(generated, method, "@CompiledValidator(fallback=false) cannot fully compile "
                        + "this schema and method: " + fallbackReason
                        + "; set @CompiledValidator(fallback=true) to enable runtime fallback");
                return;
            }
            context.messager.printMessage(javax.tools.Diagnostic.Kind.WARNING,
                    "@CompiledValidator(fallback=true) runtime fallback: " + fallbackReason, method);
        }
        // SchemaEmitter maintains its own helper names per root. Emit bindings
        // in inheritance order with one shared local-name allocator.
        final String failure = isVoid
                ? SchemaEmitter.DIAGNOSTIC_FAILURE : "return false;";
        List<String> emitted = null;
        if (!fallback && booleanLiteral == null) {
            emitted = new ArrayList<String>();
            for (int i = 0; i < directBindings.size(); i++) {
                emitted.addAll(new SchemaEmitter(generated, "M" + methodSerial++, locals).emitRoot(
                        directBindings.get(i), SchemaValue.of(context, parameterType), value,
                        plans.get(i), strictParam, failure));
            }
        }
        if (fallback) {
            for (SchemaFallbackEmitter.Plan plan : plans) plan.useRoot();
        }

        final String falseSchemaUri = bound == null ? null : bound.snapshot.selectedUri.split("#", 2)[0];
        final String literal = booleanLiteral;
        final List<String> checks = emitted;
        // Allocate before source emission, sharing the method's local scope
        // with all inlined native checks.
        final String errors = isResult && plans.size() > 1 ? locals.newName("errors") : null;
        final String valid = isResult && plans.size() > 1 ? locals.newName("valid") : null;
        final String resultVar = isResult && plans.size() > 1 ? locals.newName("result") : null;
        generated.addMethod(out -> {
            out.line("@Override");
            out.beginBlock("public " + resultName + " " + method.getSimpleName()
                    + "(" + parameterDeclaration + ")");
            // Null is a JSON instance. The schema, not an unconditional shortcut,
            // decides whether it is valid for all three validator contracts.
            if (literal != null) {
                if (isVoid) {
                    if ("false".equals(literal)) {

                        out.line("throw org.sjf4j.schema.ValidationException.failure("
                                + "\"false\", " + SchemaEmitter.javaLiteral(falseSchemaUri)
                                + ", \"\", org.sjf4j.path.PathSegment.Root.INSTANCE);");
                    }
                } else {
                    out.line("return " + literal + ";");
                }
            } else if (checks != null) {
                for (String statement : checks) out.line(statement);
                if (isBoolean) out.line("return true;");
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
                out.line("java.util.List<org.sjf4j.schema.ValidationResult.Message> " + errors + " = new java.util.ArrayList<>();");
                out.line("boolean " + valid + " = true;");
                out.line("org.sjf4j.schema.ValidationResult " + resultVar + ";");
                for (SchemaFallbackEmitter.Plan plan : plans) {
                    out.line(resultVar + " = " + plan.rootReference() + ".validate(" + value + ", false, " + strictParam + ");");
                    out.line("if (!" + resultVar + ".isValid()) " + valid + " = false;");
                    out.line(errors + ".addAll(" + resultVar + ".getErrors());");
                }
                out.line("return " + errors + ".isEmpty() && " + valid + " ? org.sjf4j.schema.ValidationResult.SUCCESS");
                out.line("        : new org.sjf4j.schema.ValidationResult(" + valid + ", " + errors + ");");
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
