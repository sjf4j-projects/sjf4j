package org.sjf4j.processor.schema;

import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.annotation.schema.ValidatorOptions;
import org.sjf4j.processor.GeneratedClass;
import org.sjf4j.processor.GeneratorUtil;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.SourceWriter;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.ObjectSchema;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.SchemaRegistry;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Generates fast-path schema validators for {@code @CompiledSchemaValidator}. */
public final class SchemaValidatorGenerator {
    private final ProcessorContext ctx;

    public SchemaValidatorGenerator(ProcessorContext ctx) {
        this.ctx = ctx;
    }

    /** Generates an implementation for one validator interface. */
    public void generate(TypeElement iface) {
        GeneratedClass target = new GeneratedClass(ctx, iface, GeneratorUtil.COMPILED_IMPL_POSTFIX);

        State state = new State(target);
        for (Element member : iface.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement) member;
            if (!method.getModifiers().contains(Modifier.ABSTRACT)) continue;
            _genMethod(method, target, state);
        }
        target.emit();
    }

    private void _genMethod(ExecutableElement method, GeneratedClass target, State state) {
        List<? extends VariableElement> params = method.getParameters();
        if (params.size() != 1) {
            _error(method, target, "@CompiledSchemaValidator method must have exactly one parameter");
            return;
        }
        VariableElement param = params.get(0);
        TypeElement sourceType = GeneratorUtil.asTypeElement(param.asType());
        if (sourceType == null) {
            _error(method, target, "@CompiledSchemaValidator method parameter must be a declared POJO type");
            return;
        }

        ReturnKind returnKind = _returnKind(method.getReturnType());
        if (returnKind == null) {
            _error(method, target, "@CompiledSchemaValidator method must return boolean, void, or org.sjf4j.schema.ValidationResult");
            return;
        }

        ValidatorOptions options = method.getAnnotation(ValidatorOptions.class);
        boolean strictFormat = options == null || options.strictFormat();
        boolean fallback = options == null || options.fallback();

        List<ValidJsonSchema> schemas = _schemas(sourceType);
        if (schemas.isEmpty()) {
            _error(method, target, "@CompiledSchemaValidator method parameter type '" + sourceType.getQualifiedName() + "' must declare @ValidJsonSchema");
            return;
        }

        state.strictFormat = strictFormat;
        PlanResult plan = _compilePlans(method, target, state, sourceType, param.asType(), schemas);
        if (!plan.supported && !fallback) {
            _error(method, target, "@CompiledSchemaValidator method requires runtime fallback: " + plan.reason);
            return;
        }
        if (returnKind == ReturnKind.RESULT && !fallback) {
            _error(method, target, "ValidationResult methods require runtime fallback diagnostics");
            return;
        }

        if (_needsRuntimeValidator(returnKind, plan)) _ensureValidatorField(target, state, strictFormat);
        target.addMethod(out -> _emitMethod(out, method, param, returnKind, strictFormat, plan));
    }

    private boolean _needsRuntimeValidator(ReturnKind returnKind, PlanResult plan) {
        return !plan.supported || returnKind == ReturnKind.RESULT;
    }

    private void _ensureValidatorField(GeneratedClass target, State state, boolean strictFormat) {
        if (strictFormat) {
            if (state.strictValidatorField) return;
            state.strictValidatorField = true;
            target.addField(out -> out.line("private static final org.sjf4j.schema.SchemaValidator _VALIDATOR_STRICT = new org.sjf4j.schema.SchemaValidator(null, null, true);"));
        } else {
            if (state.lenientValidatorField) return;
            state.lenientValidatorField = true;
            target.addField(out -> out.line("private static final org.sjf4j.schema.SchemaValidator _VALIDATOR_LENIENT = new org.sjf4j.schema.SchemaValidator(null, null, false);"));
        }
    }

    private void _emitMethod(SourceWriter out, ExecutableElement method, VariableElement param,
                             ReturnKind returnKind, boolean strictFormat, PlanResult plan) {
        out.line("");
        out.line("@Override");
        out.line("public " + method.getReturnType() + " " + method.getSimpleName() + "(" + param.asType() + " " + param.getSimpleName() + ") {");
        out.indent();
        String p = param.getSimpleName().toString();
        String validator = strictFormat ? "_VALIDATOR_STRICT" : "_VALIDATOR_LENIENT";
        if (returnKind == ReturnKind.BOOLEAN) {
            out.line("if (" + p + " == null) return true;");
            if (plan.supported) out.line("return " + plan.methodName + "(" + p + ");");
            else out.line("return " + validator + ".validate(" + p + ").isValid();");
        } else if (returnKind == ReturnKind.VOID) {
            out.line("if (" + p + " == null) return;");
            if (plan.supported) {
                out.line("if (!" + plan.methodName + "(" + p + ")) throw new org.sjf4j.schema.SchemaException(\"schema validation failed\");");
            } else {
                out.line(validator + ".requireValid(" + p + ");");
            }
        } else {
            out.line("if (" + p + " == null) return org.sjf4j.schema.ValidationResult.SUCCESS;");
            if (plan.supported) out.line("if (" + plan.methodName + "(" + p + ")) return org.sjf4j.schema.ValidationResult.SUCCESS;");
            out.line("return " + validator + ".validate(" + p + ");");
        }
        out.dedent();
        out.line("}");
    }

    private ReturnKind _returnKind(TypeMirror type) {
        if (type.getKind() == TypeKind.VOID) return ReturnKind.VOID;
        if (type.getKind() == TypeKind.BOOLEAN) return ReturnKind.BOOLEAN;
        TypeElement result = ctx.elements.getTypeElement("org.sjf4j.schema.ValidationResult");
        if (result != null && ctx.types.isSameType(ctx.types.erasure(type), ctx.types.erasure(result.asType()))) return ReturnKind.RESULT;
        return null;
    }

    private List<ValidJsonSchema> _schemas(TypeElement type) {
        List<ValidJsonSchema> r = new ArrayList<ValidJsonSchema>();
        _schemas(type, r);
        return r;
    }

    private void _schemas(TypeElement type, List<ValidJsonSchema> out) {
        TypeMirror parent = type.getSuperclass();
        if (parent != null && parent.getKind() == TypeKind.DECLARED) {
            TypeElement p = GeneratorUtil.asTypeElement(parent);
            if (p != null && !p.getQualifiedName().contentEquals(Object.class.getName())) _schemas(p, out);
        }
        ValidJsonSchema ann = type.getAnnotation(ValidJsonSchema.class);
        if (ann != null) out.add(ann);
    }

    private PlanResult _compilePlans(ExecutableElement method, GeneratedClass target, State state, TypeElement sourceType,
                                     TypeMirror sourceMirror, List<ValidJsonSchema> schemas) {
        List<String> calls = new ArrayList<String>();
        String reason = null;
        for (ValidJsonSchema schema : schemas) {
            String inline = schema.value();
            if (inline.length() == 0) {
                reason = "schema ref or convention requires runtime SchemaValidator";
                break;
            }
            SchemaPlan plan;
            try {
                JsonSchema jsonSchema = JsonSchema.fromJson(inline);
                String rootUri = "sjf4j:/" + method.getEnclosingElement() + "/" + method.getSimpleName() + "/";
                if (jsonSchema instanceof ObjectSchema) ((ObjectSchema) jsonSchema).setRetrievalUri(java.net.URI.create(rootUri));
                plan = new SchemaRegistry().register(jsonSchema);
                state.currentRootJson = inline;
                state.currentRootUri = rootUri;
            } catch (RuntimeException e) {
                reason = "cannot compile inline schema: " + e.getMessage();
                break;
            }
            String helper = "_valid" + state.nextHelper++;
            CompileResult cr = _compileHelper(target, state, helper, sourceType, sourceMirror, plan);
            if (!cr.supported) {
                reason = cr.reason;
                break;
            }
            calls.add(helper);
        }
        if (reason != null) return new PlanResult(false, null, reason);
        if (calls.size() == 1) return new PlanResult(true, calls.get(0), null);
        String root = "_valid" + state.nextHelper++;
        target.addHelper(out -> {
            out.line("");
            out.line("private static boolean " + root + "(" + sourceMirror + " _v) {");
            out.indent();
            for (String call : calls) out.line("if (!" + call + "(_v)) return false;");
            out.line("return true;");
            out.dedent();
            out.line("}");
        });
        return new PlanResult(true, root, null);
    }

    private CompileResult _compileHelper(GeneratedClass target, State state, String name, TypeElement type, TypeMirror mirror, SchemaPlan plan) {
        List<String> lines = new ArrayList<String>();
        CompileResult cr = _emitPlan(state, lines, "_v", mirror, plan, true);
        if (!cr.supported) return cr;
        target.addHelper(out -> {
            out.line("");
            out.line("private static boolean " + name + "(" + mirror + " _v) {");
            out.indent();
            for (String line : lines) out.line(line);
            if (!_terminalReturn(lines)) out.line("return true;");
            out.dedent();
            out.line("}");
        });
        return CompileResult.OK;
    }

    private String _compileSubHelper(State state, TypeMirror mirror, SchemaPlan plan) {
        String name = "_valid" + state.nextHelper++;
        List<String> lines = new ArrayList<String>();
        CompileResult cr = _emitPlan(state, lines, "_v", mirror, plan, mirror.getKind().isPrimitive());
        if (!cr.supported) {
            String field = _localPlanField(state, plan);
            if (field == null) {
                state.unsupportedReason = cr.reason;
                return null;
            }
            lines.clear();
            lines.add("return " + field + ".isValid(_v, " + state.strictFormat + ");");
        }
        state.target.addHelper(out -> {
            out.line("");
            out.line("private static boolean " + name + "(" + _paramType(mirror) + " _v) {");
            out.indent();
            for (String line : lines) out.line(line);
            if (!_terminalReturn(lines)) out.line("return true;");
            out.dedent();
            out.line("}");
        });
        return name;
    }

    private boolean _terminalReturn(List<String> lines) {
        if (lines.isEmpty()) return false;
        return lines.get(lines.size() - 1).trim().startsWith("return ");
    }

    private CompileResult _emitPlan(State state, List<String> out, String var, TypeMirror type, SchemaPlan plan, boolean nonNull) {
        try {
            if (SchemaPlanIntrospector.booleanSchema(plan)) {
                if (SchemaPlanIntrospector.booleanValue(plan)) return CompileResult.OK;
                out.add("return false;");
                return CompileResult.OK;
            }
            if (!state.inProgress.add(plan)) return CompileResult.unsupported("recursive/cyclic $ref requires runtime fallback");
            try {
                Object[] evaluators = SchemaPlanIntrospector.evaluators(plan);
                for (Object e : evaluators) {
                    CompileResult cr = _emitEvaluator(state, out, var, type, e, nonNull, plan);
                    if (!cr.supported) return cr;
                    if (_terminalReturn(out)) return CompileResult.OK;
                    if (_provesNonNull(e)) nonNull = true;
                }
                return CompileResult.OK;
            } finally {
                state.inProgress.remove(plan);
            }
        } catch (RuntimeException e) {
            return CompileResult.unsupported(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private CompileResult _emitEvaluator(State state, List<String> out, String var, TypeMirror type, Object e, boolean nonNull, SchemaPlan currentPlan) {
        String n = e.getClass().getSimpleName();
        if ("TypeEvaluator".equals(n)) {
            String single = (String) SchemaPlanIntrospector.field(e, "type");
            String[] many = (String[]) SchemaPlanIntrospector.field(e, "types");
            String check = _typeCheckExpr(var, type, single != null ? new String[]{single} : many, nonNull);
            if ("false".equals(check)) out.add("return false;");
            else if ((var + " != null").equals(check)) out.add("if (" + var + " == null) return false;");
            else if ((var + " == null").equals(check)) out.add("if (" + var + " != null) return false;");
            else if (!"true".equals(check)) out.add("if (!(" + check + ")) return false;");
            return CompileResult.OK;
        }
        if ("RefEvaluator".equals(n)) {
            SchemaPlan p = (SchemaPlan) SchemaPlanIntrospector.field(e, "plan");
            if (p == null) return CompileResult.unsupported("$ref requires runtime fallback");
            return _emitPlan(state, out, var, type, p, nonNull);
        }
        if ("RequiredEvaluator".equals(n)) {
            String[] required = (String[]) SchemaPlanIntrospector.field(e, "required");
            Map<String, String[]> dependentRequired = (Map<String, String[]>) SchemaPlanIntrospector.field(e, "dependentRequired");
            ReadsResult rr = null;
            if (dependentRequired != null) {
                rr = _reads(type);
                if (_runtimeReadableObject(type)) {
                    out.add("if (org.sjf4j.JsonType.of(" + var + ") == org.sjf4j.JsonType.OBJECT) {");
                    for (Map.Entry<String, String[]> entry : dependentRequired.entrySet()) {
                        out.add("  if (org.sjf4j.node.Nodes.containsInObject(" + var + ", \"" + GeneratorUtil.escape(entry.getKey()) + "\")) {");
                        for (String key : entry.getValue()) {
                            out.add("    if (!org.sjf4j.node.Nodes.containsInObject(" + var + ", \"" + GeneratorUtil.escape(key) + "\")) return false;");
                        }
                        out.add("  }");
                    }
                    out.add("}");
                } else {
                    boolean failed = false;
                    for (Map.Entry<String, String[]> entry : dependentRequired.entrySet()) {
                        if (!rr.reads.containsKey(entry.getKey())) continue;
                        for (String key : entry.getValue()) {
                            if (!rr.reads.containsKey(key)) {
                                failed = true;
                                break;
                            }
                        }
                        if (failed) break;
                    }
                    if (failed) {
                        out.add("return false;");
                        return CompileResult.OK;
                    }
                }
            }
            if (required != null) {
                if (rr == null) rr = _reads(type);
                boolean missing = false;
                for (String key : required) {
                    if (!rr.reads.containsKey(key)) {
                        missing = true;
                        break;
                    }
                }
                if (missing) {
                    if (_runtimeReadableObject(type)) {
                        out.add("if (org.sjf4j.JsonType.of(" + var + ") == org.sjf4j.JsonType.OBJECT) {");
                        for (String key : required) {
                            out.add("  if (!org.sjf4j.node.Nodes.containsInObject(" + var + ", \"" + GeneratorUtil.escape(key) + "\")) return false;");
                        }
                        out.add("}");
                    } else {
                        out.add("return false;");
                    }
                }
            }
            return CompileResult.OK;
        }
        if ("ObjectEvaluator".equals(n)) {
            int min = ((Integer) SchemaPlanIntrospector.field(e, "minProperties")).intValue();
            int max = ((Integer) SchemaPlanIntrospector.field(e, "maxProperties")).intValue();
            CompileJsonKind kind = _knownJsonKind(type);
            if (kind != CompileJsonKind.UNKNOWN && kind != CompileJsonKind.OBJECT) return CompileResult.OK;
            if (!_runtimeReadableObject(type) && kind == CompileJsonKind.OBJECT) {
                int size = _reads(type).javaNames.size();
                if ((min >= 0 && size < min) || (max >= 0 && size > max)) out.add("return false;");
                return CompileResult.OK;
            }
            String guard = _knownValueGuard(kind, CompileJsonKind.OBJECT, var, nonNull, "org.sjf4j.JsonType.OBJECT");
            if (guard != null) out.add(guard + " {");
            String indent = guard != null ? "  " : "";
            if (min >= 0 || max >= 0) {
                String size = "_os" + state.nextLocal++;
                out.add(indent + "int " + size + " = org.sjf4j.node.Nodes.sizeInObject(" + var + ");");
                if (min >= 0) out.add(indent + "if (" + size + " < " + min + ") return false;");
                if (max >= 0) out.add(indent + "if (" + size + " > " + max + ") return false;");
            }
            if (guard != null) out.add("}");
            return CompileResult.OK;
        }
        if ("PropertiesEvaluator".equals(n)) {
            Map<String, SchemaPlan> properties = (Map<String, SchemaPlan>) SchemaPlanIntrospector.field(e, "properties");
            Object patterns = SchemaPlanIntrospector.field(e, "patterns");
            SchemaPlan additional = (SchemaPlan) SchemaPlanIntrospector.field(e, "additionalPropertiesPlan");
            if (patterns != null) return _emitLocalPlanReturn(state, out, var, currentPlan, "patternProperties");
            ReadsResult rr = _reads(type);
            Map<String, Read> reads = rr.reads;
            if (properties != null) {
                for (Map.Entry<String, SchemaPlan> entry : properties.entrySet()) {
                    Read read = reads.get(entry.getKey());
                    if (read == null) {
                        if (_runtimeReadableObject(type)) return _emitLocalPlanReturn(state, out, var, currentPlan, "properties on runtime object type");
                        continue;
                    }
                    String temp = "_p" + state.nextLocal++;
                    List<String> subLines = new ArrayList<String>();
                    CompileResult cr = _emitPlan(state, subLines, temp, read.type, entry.getValue(), read.type.getKind().isPrimitive());
                    if (!cr.supported) {
                        String field = _localPlanField(state, entry.getValue());
                        if (field == null) return cr;
                        subLines.clear();
                        subLines.add("if (!" + field + ".isValid(" + temp + ", " + state.strictFormat + ")) return false;");
                    }
                    if (!subLines.isEmpty()) {
                        out.add(_paramType(read.type) + " " + temp + " = " + read.code(var) + ";");
                        out.addAll(subLines);
                    }
                }
            }
            if (additional != null && _isBooleanFalse(additional)) {
                Set<String> allowed = properties == null ? new HashSet<String>() : properties.keySet();
                if (!_runtimeReadableObject(type)) {
                    for (String javaName : rr.javaNames) {
                        String jsonName = rr.javaToJsonName.get(javaName);
                        if (jsonName != null && !allowed.contains(jsonName)) out.add("return false;");
                    }
                }
            } else if (additional != null && !_isBooleanTrue(additional)) {
                return _emitLocalPlanReturn(state, out, var, currentPlan, "additionalProperties schema");
            }
            return CompileResult.OK;
        }
        if ("StringEvaluator".equals(n)) {
            int min = ((Integer) SchemaPlanIntrospector.field(e, "minLength")).intValue();
            int max = ((Integer) SchemaPlanIntrospector.field(e, "maxLength")).intValue();
            CompileJsonKind kind = _knownJsonKind(type);
            if (kind != CompileJsonKind.UNKNOWN && kind != CompileJsonKind.STRING) return CompileResult.OK;
            String guard = _knownValueGuard(kind, CompileJsonKind.STRING, var, nonNull, "org.sjf4j.JsonType.STRING");
            if (guard != null) out.add(guard + " {");
            String indent = guard != null ? "  " : "";
            String sv = _stringValueExpr(var, type);
            if (min >= 0 && max >= 0) {
                String len = "_l" + state.nextLocal++;
                out.add(indent + "int " + len + " = org.sjf4j.schema.SchemaUtil.stringIcuLength(" + sv + ");");
                out.add(indent + "if (" + len + " < " + min + ") return false;");
                out.add(indent + "if (" + len + " > " + max + ") return false;");
            } else {
                if (min >= 0) out.add(indent + "if (org.sjf4j.schema.SchemaUtil.stringIcuLength(" + sv + ") < " + min + ") return false;");
                if (max >= 0) out.add(indent + "if (org.sjf4j.schema.SchemaUtil.stringIcuLength(" + sv + ") > " + max + ") return false;");
            }
            if (guard != null) out.add("}");
            return CompileResult.OK;
        }
        if ("NumberEvaluator".equals(n)) {
            boolean hasMin = ((Boolean) SchemaPlanIntrospector.field(e, "hasMinimum")).booleanValue();
            boolean hasMax = ((Boolean) SchemaPlanIntrospector.field(e, "hasMaximum")).booleanValue();
            boolean hasExMin = ((Boolean) SchemaPlanIntrospector.field(e, "hasExclusiveMinimum")).booleanValue();
            boolean hasExMax = ((Boolean) SchemaPlanIntrospector.field(e, "hasExclusiveMaximum")).booleanValue();
            CompileJsonKind kind = _knownJsonKind(type);
            if (kind != CompileJsonKind.UNKNOWN && kind != CompileJsonKind.NUMBER && kind != CompileJsonKind.INTEGER_NUMBER) return CompileResult.OK;
            String guard = _knownValueGuard(kind, CompileJsonKind.NUMBER, var, nonNull, "org.sjf4j.JsonType.NUMBER");
            if (guard != null) out.add(guard + " {");
            String indent = guard != null ? "  " : "";
            String nv;
            int checks = (hasMin ? 1 : 0) + (hasMax ? 1 : 0) + (hasExMin ? 1 : 0) + (hasExMax ? 1 : 0);
            if (checks <= 1) {
                nv = _numberDoubleExpr(var, type);
            } else {
                nv = "_n" + state.nextLocal++;
                out.add(indent + "double " + nv + " = " + _numberDoubleExpr(var, type) + ";");
            }
            if (hasMin) out.add(indent + "if (" + nv + " < " + SchemaPlanIntrospector.field(e, "minimum") + ") return false;");
            if (hasMax) out.add(indent + "if (" + nv + " > " + SchemaPlanIntrospector.field(e, "maximum") + ") return false;");
            if (hasExMin) out.add(indent + "if (" + nv + " <= " + SchemaPlanIntrospector.field(e, "exclusiveMinimum") + ") return false;");
            if (hasExMax) out.add(indent + "if (" + nv + " >= " + SchemaPlanIntrospector.field(e, "exclusiveMaximum") + ") return false;");
            if (guard != null) out.add("}");
            return CompileResult.OK;
        }
        if ("ArrayEvaluator".equals(n)) {
            int min = ((Integer) SchemaPlanIntrospector.field(e, "minItems")).intValue();
            int max = ((Integer) SchemaPlanIntrospector.field(e, "maxItems")).intValue();
            boolean unique = ((Boolean) SchemaPlanIntrospector.field(e, "uniqueItems")).booleanValue();
            CompileJsonKind kind = _knownJsonKind(type);
            if (kind != CompileJsonKind.UNKNOWN && kind != CompileJsonKind.ARRAY) return CompileResult.OK;
            String guard = _knownValueGuard(kind, CompileJsonKind.ARRAY, var, nonNull, "org.sjf4j.JsonType.ARRAY");
            if (guard != null) out.add(guard + " {");
            String indent = guard != null ? "  " : "";
            if (min >= 0 || max >= 0) {
                String size = "_s" + state.nextLocal++;
                out.add(indent + "int " + size + " = " + _arraySizeExpr(var, type) + ";");
                if (min >= 0) out.add(indent + "if (" + size + " < " + min + ") return false;");
                if (max >= 0) out.add(indent + "if (" + size + " > " + max + ") return false;");
            }
            if (unique) {
                String seen = "_u" + state.nextLocal++;
                String it = "_it" + state.nextLocal++;
                String item = "_uitem" + state.nextLocal++;
                String old = "_uold" + state.nextLocal++;
                out.add(indent + "java.util.ArrayList<Object> " + seen + " = new java.util.ArrayList<Object>();");
                out.add(indent + "for (java.util.Iterator<Object> " + it + " = org.sjf4j.node.Nodes.iteratorInArray(" + var + "); " + it + ".hasNext(); ) {");
                out.add(indent + "  Object " + item + " = " + it + ".next();");
                out.add(indent + "  for (Object " + old + " : " + seen + ") if (org.sjf4j.node.Nodes.equals(" + old + ", " + item + ")) return false;");
                out.add(indent + "  " + seen + ".add(" + item + ");");
                out.add(indent + "}");
            }
            if (guard != null) out.add("}");
            return CompileResult.OK;
        }
        if ("ItemsEvaluator".equals(n)) {
            SchemaPlan items = (SchemaPlan) SchemaPlanIntrospector.field(e, "itemsPlan");
            Object prefix = SchemaPlanIntrospector.field(e, "prefixItemsPlans");
            if (prefix != null) return _emitLocalPlanReturn(state, out, var, currentPlan, "prefixItems");
            if (items == null) return CompileResult.OK;
            TypeMirror elem = _elementType(type);
            if (elem == null) return _emitLocalPlanReturn(state, out, var, currentPlan, "items on non-list type");
            String helper = _compileSubHelper(state, elem, items);
            if (helper == null) return CompileResult.unsupported(state.unsupportedReason);
            String item = "_i" + state.nextLocal++;
            CompileJsonKind kind = _knownJsonKind(type);
            if (kind != CompileJsonKind.UNKNOWN && kind != CompileJsonKind.ARRAY) return CompileResult.OK;
            String guard = _knownValueGuard(kind, CompileJsonKind.ARRAY, var, nonNull, "org.sjf4j.JsonType.ARRAY");
            if (guard != null) out.add(guard + " {");
            out.add((guard != null ? "  " : "") + "for (" + _paramType(elem) + " " + item + " : " + var + ") { if (!" + helper + "(" + item + ")) return false; }");
            if (guard != null) out.add("}");
            return CompileResult.OK;
        }
        if ("ContainsEvaluator".equals(n)) {
            SchemaPlan contains = (SchemaPlan) SchemaPlanIntrospector.field(e, "containsPlan");
            if (contains == null) return CompileResult.OK;
            TypeMirror elem = _elementType(type);
            if (elem == null) return _emitLocalPlanReturn(state, out, var, currentPlan, "contains on non-list type");
            String helper = _compileSubHelper(state, elem, contains);
            if (helper == null) return CompileResult.unsupported(state.unsupportedReason);
            int min = ((Integer) SchemaPlanIntrospector.field(e, "minContains")).intValue();
            int max = ((Integer) SchemaPlanIntrospector.field(e, "maxContains")).intValue();
            CompileJsonKind kind = _knownJsonKind(type);
            if (kind != CompileJsonKind.UNKNOWN && kind != CompileJsonKind.ARRAY) return CompileResult.OK;
            String guard = _knownValueGuard(kind, CompileJsonKind.ARRAY, var, nonNull, "org.sjf4j.JsonType.ARRAY");
            if (guard != null) out.add(guard + " {");
            String indent = guard != null ? "  " : "";
            String matches = "_c" + state.nextLocal++;
            String item = "_ci" + state.nextLocal++;
            out.add(indent + "int " + matches + " = 0;");
            out.add(indent + "for (" + _paramType(elem) + " " + item + " : " + var + ") { if (" + helper + "(" + item + ")) " + matches + "++; }");
            out.add(indent + "if (" + matches + " < " + min + ") return false;");
            if (max >= 0) out.add(indent + "if (" + matches + " > " + max + ") return false;");
            if (guard != null) out.add("}");
            return CompileResult.OK;
        }
        if ("PropertyNamesEvaluator".equals(n)) {
            return _emitLocalPlanReturn(state, out, var, currentPlan, "propertyNames");
        }
        if ("AllOfEvaluator".equals(n) || "AnyOfEvaluator".equals(n) || "OneOfEvaluator".equals(n)) {
            String field = "AllOfEvaluator".equals(n) ? "allOfPlans" : ("AnyOfEvaluator".equals(n) ? "anyOfPlans" : "oneOfPlans");
            SchemaPlan[] plans = (SchemaPlan[]) SchemaPlanIntrospector.field(e, field);
            List<String> helpers = new ArrayList<String>();
            for (SchemaPlan p : plans) {
                String h = _compileSubHelper(state, type, p);
                if (h == null) return CompileResult.unsupported(state.unsupportedReason);
                helpers.add(h);
            }
            if ("AllOfEvaluator".equals(n)) {
                for (String h : helpers) out.add("if (!" + h + "(" + var + ")) return false;");
            } else if ("AnyOfEvaluator".equals(n)) {
                StringBuilder b = new StringBuilder("if (!(");
                for (int i = 0; i < helpers.size(); i++) {
                    if (i != 0) b.append(" || ");
                    b.append(helpers.get(i)).append("(").append(var).append(")");
                }
                out.add(b.append(") ) return false;").toString());
            } else {
                String m = "_m" + state.nextLocal++;
                out.add("int " + m + " = 0;");
                for (String h : helpers) out.add("if (" + h + "(" + var + ") && ++" + m + " > 1) return false;");
                out.add("if (" + m + " != 1) return false;");
            }
            return CompileResult.OK;
        }
        if ("NotEvaluator".equals(n)) {
            SchemaPlan p = (SchemaPlan) SchemaPlanIntrospector.field(e, "notPlan");
            String h = _compileSubHelper(state, type, p);
            if (h == null) return CompileResult.unsupported(state.unsupportedReason);
            out.add("if (" + h + "(" + var + ")) return false;");
            return CompileResult.OK;
        }
        if ("IfThenElseEvaluator".equals(n)) {
            SchemaPlan ifPlan = (SchemaPlan) SchemaPlanIntrospector.field(e, "ifPlan");
            SchemaPlan thenPlan = (SchemaPlan) SchemaPlanIntrospector.field(e, "thenPlan");
            SchemaPlan elsePlan = (SchemaPlan) SchemaPlanIntrospector.field(e, "elsePlan");
            if (ifPlan == null) return CompileResult.OK;
            String ih = _compileSubHelper(state, type, ifPlan);
            String th = thenPlan == null ? null : _compileSubHelper(state, type, thenPlan);
            String eh = elsePlan == null ? null : _compileSubHelper(state, type, elsePlan);
            if (ih == null || (thenPlan != null && th == null) || (elsePlan != null && eh == null)) return CompileResult.unsupported(state.unsupportedReason);
            if (th != null) out.add("if (" + ih + "(" + var + ") && !" + th + "(" + var + ")) return false;");
            if (eh != null) out.add("if (!" + ih + "(" + var + ") && !" + eh + "(" + var + ")) return false;");
            return CompileResult.OK;
        }
        if ("PatternEvaluator".equals(n)) {
            String pattern = (String) SchemaPlanIntrospector.field(e, "pattern");
            String field = "_PATTERN" + state.nextField++;
            state.target.addField(outField -> outField.line("private static final java.util.regex.Pattern " + field + " = org.sjf4j.schema.SchemaUtil.compileRegexPattern(\"" + GeneratorUtil.escape(pattern) + "\", \"pattern\");"));
            CompileJsonKind kind = _knownJsonKind(type);
            if (kind == CompileJsonKind.UNKNOWN) {
                out.add("if (org.sjf4j.JsonType.of(" + var + ") == org.sjf4j.JsonType.STRING && !" + field + ".matcher(org.sjf4j.node.Nodes.toString(" + var + ")).find()) return false;");
            } else if (kind == CompileJsonKind.STRING) {
                String prefix = nonNull ? "" : var + " != null && ";
                out.add("if (" + prefix + "!" + field + ".matcher(" + _stringValueExpr(var, type) + ").find()) return false;");
            }
            return CompileResult.OK;
        }
        if ("FormatEvaluator".equals(n)) {
            boolean assertion = ((Boolean) SchemaPlanIntrospector.field(e, "assertion")).booleanValue();
            if (!assertion && !state.strictFormat) return CompileResult.OK;
            String format = (String) SchemaPlanIntrospector.field(e, "format");
            String field = _formatField(state, format);
            CompileJsonKind kind = _knownJsonKind(type);
            if (kind == CompileJsonKind.UNKNOWN) {
                out.add("if (org.sjf4j.JsonType.of(" + var + ") == org.sjf4j.JsonType.STRING && !" + field + ".validate(org.sjf4j.node.Nodes.toString(" + var + "))) return false;");
            } else if (kind == CompileJsonKind.STRING) {
                String prefix = nonNull ? "" : var + " != null && ";
                out.add("if (" + prefix + "!" + field + ".validate(" + _stringValueExpr(var, type) + ")) return false;");
            }
            return CompileResult.OK;
        }
        if ("ContentEvaluator".equals(n)) {
            return _emitLocalPlanReturn(state, out, var, currentPlan, "contentEncoding/contentMediaType");
        }
        if ("DependenciesEvaluator".equals(n)) {
            return _emitLocalPlanReturn(state, out, var, currentPlan, "dependencies");
        }
        if ("DependentSchemasEvaluator".equals(n)) {
            return _emitLocalPlanReturn(state, out, var, currentPlan, "dependentSchemas");
        }
        if ("ConstEvaluator".equals(n)) {
            String literal = _literal(SchemaPlanIntrospector.field(e, "constValue"));
            if (literal == null) return _emitLocalPlanReturn(state, out, var, currentPlan, "const value");
            out.add("if (!org.sjf4j.node.Nodes.equals(" + literal + ", " + var + ")) return false;");
            return CompileResult.OK;
        }
        if ("EnumEvaluator".equals(n)) {
            Object[] values = (Object[]) SchemaPlanIntrospector.field(e, "enumValues");
            String matched = "_e" + state.nextLocal++;
            out.add("boolean " + matched + " = false;");
            for (Object value : values) {
                String literal = _literal(value);
                if (literal == null) return _emitLocalPlanReturn(state, out, var, currentPlan, "enum value");
                out.add("if (org.sjf4j.node.Nodes.equals(" + literal + ", " + var + ")) " + matched + " = true;");
            }
            out.add("if (!" + matched + ") return false;");
            return CompileResult.OK;
        }
        if ("MultipleOfEvaluator".equals(n)) {
            return _emitLocalPlanReturn(state, out, var, currentPlan, "multipleOf");
        }
        if ("UnevaluatedEvaluator".equals(n)) {
            return _emitLocalPlanReturn(state, out, var, currentPlan, "unevaluatedProperties/unevaluatedItems");
        }
        return CompileResult.unsupported(n);
    }

    private String _typeCheckExpr(String var, TypeMirror type, String[] schemaTypes, boolean nonNull) {
        if (schemaTypes == null || schemaTypes.length == 0) return "true";

        String known = _knownTypeCheckExpr(var, type, schemaTypes, nonNull);
        if (known != null) return known;

        StringBuilder b = new StringBuilder();
        for (String schemaType : schemaTypes) {
            if (b.length() != 0) b.append(" || ");
            b.append('(').append(_runtimeTypeCheckExpr(var, schemaType)).append(')');
        }
        return b.toString();
    }

    private boolean _provesNonNull(Object e) {
        if (!"TypeEvaluator".equals(e.getClass().getSimpleName())) return false;
        String single = (String) SchemaPlanIntrospector.field(e, "type");
        if (single != null) return !"null".equals(single);
        String[] many = (String[]) SchemaPlanIntrospector.field(e, "types");
        if (many == null || many.length == 0) return false;
        for (String type : many) if ("null".equals(type)) return false;
        return true;
    }

    private String _knownTypeCheckExpr(String var, TypeMirror type, String[] schemaTypes, boolean nonNull) {
        CompileJsonKind kind = _knownJsonKind(type);
        if (kind == CompileJsonKind.UNKNOWN) return null;

        boolean nullable = !nonNull && !type.getKind().isPrimitive();
        boolean allowsNull = false;
        boolean matchesValue = false;
        boolean needsIntegerCheck = false;
        for (String schemaType : schemaTypes) {
            if ("null".equals(schemaType)) {
                allowsNull = true;
            } else if (_knownMatches(kind, schemaType)) {
                matchesValue = true;
            } else if ("integer".equals(schemaType) && kind == CompileJsonKind.NUMBER) {
                needsIntegerCheck = true;
            }
        }

        if (matchesValue) {
            if (!nullable || nonNull || allowsNull) return "true";
            return var + " != null";
        }
        if (needsIntegerCheck) {
            String integerCheck = "org.sjf4j.node.Numbers.isSemanticInteger(" + _numberValueExpr(var, type) + ")";
            if (nonNull) return integerCheck;
            if (allowsNull) return var + " == null || (" + var + " != null && " + integerCheck + ")";
            return var + " != null && " + integerCheck;
        }
        if (allowsNull) return nonNull ? "false" : var + " == null";
        return "false";
    }

    private boolean _knownMatches(CompileJsonKind kind, String schemaType) {
        if ("object".equals(schemaType)) return kind == CompileJsonKind.OBJECT;
        if ("array".equals(schemaType)) return kind == CompileJsonKind.ARRAY;
        if ("string".equals(schemaType)) return kind == CompileJsonKind.STRING;
        if ("boolean".equals(schemaType)) return kind == CompileJsonKind.BOOLEAN;
        if ("number".equals(schemaType)) return kind == CompileJsonKind.NUMBER || kind == CompileJsonKind.INTEGER_NUMBER;
        if ("integer".equals(schemaType)) return kind == CompileJsonKind.INTEGER_NUMBER;
        return false;
    }

    private String _knownValueGuard(CompileJsonKind actual, CompileJsonKind expected, String var, boolean nonNull, String runtimeExpected) {
        if (actual == expected || (expected == CompileJsonKind.NUMBER && actual == CompileJsonKind.INTEGER_NUMBER)) {
            return nonNull ? null : "if (" + var + " != null)";
        }
        return "if (org.sjf4j.JsonType.of(" + var + ") == " + runtimeExpected +
                (expected == CompileJsonKind.NUMBER ? " || org.sjf4j.JsonType.of(" + var + ") == org.sjf4j.JsonType.INTEGER" : "") + ")";
    }

    private String _runtimeTypeCheckExpr(String var, String schemaType) {
        if ("null".equals(schemaType)) return var + " == null";
        String jt = "org.sjf4j.JsonType.of(" + var + ")";
        if ("integer".equals(schemaType)) {
            return jt + " == org.sjf4j.JsonType.INTEGER || (" + jt + " == org.sjf4j.JsonType.NUMBER && " +
                    "org.sjf4j.node.Numbers.isSemanticInteger(org.sjf4j.node.Nodes.toNumber(" + var + ")))";
        }
        return jt + " == " + _jsonTypeConstant(schemaType);
    }

    private String _jsonTypeConstant(String schemaType) {
        if ("object".equals(schemaType)) return "org.sjf4j.JsonType.OBJECT";
        if ("array".equals(schemaType)) return "org.sjf4j.JsonType.ARRAY";
        if ("string".equals(schemaType)) return "org.sjf4j.JsonType.STRING";
        if ("number".equals(schemaType)) return "org.sjf4j.JsonType.NUMBER";
        if ("boolean".equals(schemaType)) return "org.sjf4j.JsonType.BOOLEAN";
        return "org.sjf4j.JsonType.UNKNOWN";
    }

    private CompileJsonKind _knownJsonKind(TypeMirror type) {
        if (type == null) return CompileJsonKind.UNKNOWN;
        TypeKind kind = type.getKind();
        if (kind == TypeKind.BOOLEAN) return CompileJsonKind.BOOLEAN;
        if (kind == TypeKind.BYTE || kind == TypeKind.SHORT || kind == TypeKind.INT || kind == TypeKind.LONG) return CompileJsonKind.INTEGER_NUMBER;
        if (kind == TypeKind.FLOAT || kind == TypeKind.DOUBLE) return CompileJsonKind.NUMBER;
        if (kind == TypeKind.CHAR) return CompileJsonKind.STRING;
        if (kind == TypeKind.ARRAY) return CompileJsonKind.ARRAY;
        if (kind != TypeKind.DECLARED) return CompileJsonKind.UNKNOWN;

        TypeMirror boxed = GeneratorUtil.boxed(ctx, type);
        String qn = ctx.types.erasure(boxed).toString();
        if ("java.lang.Object".equals(qn)) return CompileJsonKind.UNKNOWN;
        if ("java.lang.String".equals(qn) || "java.lang.Character".equals(qn)) return CompileJsonKind.STRING;
        if ("java.lang.Boolean".equals(qn)) return CompileJsonKind.BOOLEAN;
        if ("java.lang.Byte".equals(qn) || "java.lang.Short".equals(qn) || "java.lang.Integer".equals(qn) ||
                "java.lang.Long".equals(qn) || "java.math.BigInteger".equals(qn)) return CompileJsonKind.INTEGER_NUMBER;
        if ("java.lang.Float".equals(qn) || "java.lang.Double".equals(qn) || "java.math.BigDecimal".equals(qn) ||
                "java.lang.Number".equals(qn)) return CompileJsonKind.NUMBER;
        if (GeneratorUtil.isAssignableErasure(ctx, boxed, ctx.mapType) || GeneratorUtil.isAssignableErasure(ctx, boxed, ctx.jsonObjectType)) return CompileJsonKind.OBJECT;
        if (GeneratorUtil.isAssignableErasure(ctx, boxed, ctx.listType) || GeneratorUtil.isAssignableErasure(ctx, boxed, ctx.jsonArrayType)) return CompileJsonKind.ARRAY;
        TypeElement setType = ctx.elements.getTypeElement("java.util.Set");
        if (setType != null && GeneratorUtil.isAssignableErasure(ctx, boxed, setType.asType())) return CompileJsonKind.ARRAY;

        TypeElement element = GeneratorUtil.asTypeElement(boxed);
        if (element == null) return CompileJsonKind.UNKNOWN;
        if (_hasAnnotation(element, "org.sjf4j.annotation.node.NodeValue") ||
                _hasAnnotation(element, "org.sjf4j.annotation.node.OneOf")) return CompileJsonKind.UNKNOWN;
        if (element.getKind() == ElementKind.ENUM) return CompileJsonKind.STRING;
        if (element.getKind() == ElementKind.INTERFACE || element.getModifiers().contains(Modifier.ABSTRACT)) return CompileJsonKind.UNKNOWN;
        return _isPojoCandidateName(element.getQualifiedName().toString()) ? CompileJsonKind.OBJECT : CompileJsonKind.UNKNOWN;
    }

    private boolean _isPojoCandidateName(String name) {
        return !(name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.") ||
                name.startsWith("jdk.") || name.startsWith("com.fasterxml.jackson.") ||
                name.startsWith("tools.jackson.") || name.startsWith("com.google.gson."));
    }

    private String _localPlanField(State state, SchemaPlan plan) {
        if (state.currentRootJson == null || state.currentRootUri == null) return null;
        String pointer = SchemaPlanIntrospector.pointer(plan);
        String key = state.currentRootUri + "\n" + state.currentRootJson + "#" + pointer;
        String field = state.localPlanFields.get(key);
        if (field != null) return field;

        int id = state.nextField++;
        field = "_PLAN" + id;
        String helper = "_schemaPlan" + id;
        state.localPlanFields.put(key, field);
        final String f = field;
        final String h = helper;
        final String rootJson = state.currentRootJson;
        final String rootUri = state.currentRootUri;
        final String fragment = pointer;
        state.target.addField(out -> out.line("private static final org.sjf4j.schema.SchemaPlan " + f + " = " + h + "();"));
        state.target.addHelper(out -> {
            out.line("");
            out.line("private static org.sjf4j.schema.SchemaPlan " + h + "() {");
            out.indent();
            out.line("org.sjf4j.schema.JsonSchema _schema = org.sjf4j.schema.JsonSchema.fromJson(\"" + GeneratorUtil.escape(rootJson) + "\");");
            out.line("if (_schema instanceof org.sjf4j.schema.ObjectSchema) ((org.sjf4j.schema.ObjectSchema) _schema).setRetrievalUri(java.net.URI.create(\"" + GeneratorUtil.escape(rootUri) + "\"));");
            out.line("org.sjf4j.schema.SchemaRegistry _registry = new org.sjf4j.schema.SchemaRegistry();");
            out.line("_registry.register(_schema);");
            out.line("return _registry.resolve(java.net.URI.create(\"" + GeneratorUtil.escape(rootUri + "#" + fragment) + "\"));");
            out.dedent();
            out.line("}");
        });
        return field;
    }

    private CompileResult _emitLocalPlanReturn(State state, List<String> out, String var, SchemaPlan plan, String reason) {
        String field = _localPlanField(state, plan);
        if (field == null) return CompileResult.unsupported(reason);
        out.add("if (!" + field + ".isValid(" + var + ", " + state.strictFormat + ")) return false;");
        return CompileResult.OK;
    }

    private boolean _runtimeReadableObject(TypeMirror type) {
        if (type == null || type.getKind() != TypeKind.DECLARED) return false;
        TypeMirror boxed = GeneratorUtil.boxed(ctx, type);
        String qn = ctx.types.erasure(boxed).toString();
        if ("java.lang.Object".equals(qn)) return true;
        if (GeneratorUtil.isAssignableErasure(ctx, boxed, ctx.mapType) ||
                GeneratorUtil.isAssignableErasure(ctx, boxed, ctx.jsonObjectType)) return true;
        TypeElement element = GeneratorUtil.asTypeElement(boxed);
        return element != null && (element.getKind() == ElementKind.INTERFACE ||
                element.getModifiers().contains(Modifier.ABSTRACT) ||
                _hasAnnotation(element, "org.sjf4j.annotation.node.OneOf"));
    }

    private String _stringValueExpr(String var, TypeMirror type) {
        TypeKind kind = type == null ? TypeKind.OTHER : type.getKind();
        if (kind == TypeKind.CHAR) return "java.lang.String.valueOf(" + var + ")";
        if (kind == TypeKind.DECLARED) {
            String qn = ctx.types.erasure(GeneratorUtil.boxed(ctx, type)).toString();
            if ("java.lang.String".equals(qn)) return var;
            if ("java.lang.Character".equals(qn)) return "java.lang.String.valueOf(" + var + ")";
        }
        return "org.sjf4j.node.Nodes.toString(" + var + ")";
    }

    private String _numberValueExpr(String var, TypeMirror type) {
        if (_knownJsonKind(type) == CompileJsonKind.NUMBER || _knownJsonKind(type) == CompileJsonKind.INTEGER_NUMBER) return var;
        return "org.sjf4j.node.Nodes.toNumber(" + var + ")";
    }

    private String _numberDoubleExpr(String var, TypeMirror type) {
        TypeKind kind = type == null ? TypeKind.OTHER : type.getKind();
        if (kind.isPrimitive()) return var;
        if (_knownJsonKind(type) == CompileJsonKind.NUMBER || _knownJsonKind(type) == CompileJsonKind.INTEGER_NUMBER) return var + ".doubleValue()";
        return "org.sjf4j.node.Nodes.toNumber(" + var + ").doubleValue()";
    }

    private String _arraySizeExpr(String var, TypeMirror type) {
        if (type != null && type.getKind() == TypeKind.ARRAY) return var + ".length";
        if (type != null && type.getKind() == TypeKind.DECLARED) {
            TypeMirror boxed = GeneratorUtil.boxed(ctx, type);
            TypeElement setType = ctx.elements.getTypeElement("java.util.Set");
            if (GeneratorUtil.isAssignableErasure(ctx, boxed, ctx.listType) ||
                    GeneratorUtil.isAssignableErasure(ctx, boxed, ctx.jsonArrayType) ||
                    (setType != null && GeneratorUtil.isAssignableErasure(ctx, boxed, setType.asType()))) {
                return var + ".size()";
            }
        }
        return "org.sjf4j.node.Nodes.sizeInArray(" + var + ")";
    }

    private boolean _hasAnnotation(TypeElement element, String name) {
        for (javax.lang.model.element.AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(name)) return true;
        }
        return false;
    }

    private String _formatField(State state, String format) {
        String field = state.formatFields.get(format);
        if (field != null) return field;
        field = "_FORMAT" + state.nextField++;
        state.formatFields.put(format, field);
        String f = field;
        state.target.addField(out -> out.line("private static final org.sjf4j.schema.FormatValidator " + f + " = org.sjf4j.schema.FormatValidator.of(\"" + GeneratorUtil.escape(format) + "\");"));
        return field;
    }

    private String _literal(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + GeneratorUtil.escape((String) value) + "\"";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) return value.toString();
        if (value instanceof Long) return value + "L";
        if (value instanceof Float) return value + "F";
        if (value instanceof Double) return value.toString();
        return null;
    }

    private boolean _isBooleanFalse(SchemaPlan plan) {
        return SchemaPlanIntrospector.booleanSchema(plan) && !SchemaPlanIntrospector.booleanValue(plan);
    }

    private boolean _isBooleanTrue(SchemaPlan plan) {
        return SchemaPlanIntrospector.booleanSchema(plan) && SchemaPlanIntrospector.booleanValue(plan);
    }

    private ReadsResult _reads(TypeMirror owner) {
        TypeElement type = GeneratorUtil.asTypeElement(owner);
        Map<String, Read> r = new LinkedHashMap<String, Read>();
        Set<String> javaNames = new LinkedHashSet<String>();
        Map<String, String> javaToJsonName = new LinkedHashMap<String, String>();
        if (type == null) return new ReadsResult(r, javaNames, javaToJsonName);
        if ("RECORD".equals(type.getKind().name())) {
            ReadsResult recordResult = _recordReads(type);
            if (recordResult != null) {
                // Enrich with @NodeProperty from accessor methods (record component
                // elements may not expose annotations directly in all JDK versions).
                _enrichNodeProperty(type, recordResult.reads, recordResult.javaNames, recordResult.javaToJsonName);
                return recordResult;
            }
        }
        for (Element member : ctx.elements.getAllMembers(type)) {
            Set<Modifier> m = member.getModifiers();
            if (!m.contains(Modifier.PUBLIC) || m.contains(Modifier.STATIC)) continue;
            if (member.getKind() == ElementKind.FIELD) {
                String javaName = member.getSimpleName().toString();
                _addRead(r, javaNames, javaToJsonName, javaName,
                    new Read(null, javaName, ctx.types.asMemberOf((DeclaredType) owner, member)), member);
            } else if (member.getKind() == ElementKind.METHOD) {
                ExecutableElement e = (ExecutableElement) member;
                if (!e.getParameters().isEmpty()) continue;
                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, e);
                if (mt.getReturnType().getKind() == TypeKind.VOID) continue;
                String n = e.getSimpleName().toString();
                if (n.equals("getClass")) continue;
                if (n.startsWith("get") && n.length() > 3) {
                    String javaName = GeneratorUtil.decap(n.substring(3));
                    _addRead(r, javaNames, javaToJsonName, javaName, new Read(e, null, mt.getReturnType()), member);
                } else if (n.startsWith("is") && n.length() > 2) {
                    String javaName = GeneratorUtil.decap(n.substring(2));
                    _addRead(r, javaNames, javaToJsonName, javaName, new Read(e, null, mt.getReturnType()), member);
                }
            }
        }
        return new ReadsResult(r, javaNames, javaToJsonName);
    }

    /**
     * Enriches a {@code ReadsResult} built from record components with
     * {@code @NodeProperty} names found on record accessor methods.
     * Record component elements may not expose annotations directly
     * in all JDK versions, but accessor methods do.
     */
    private void _enrichNodeProperty(TypeElement type, Map<String, Read> reads,
                                     Set<String> javaNames, Map<String, String> javaToJsonName) {
        for (Element member : ctx.elements.getAllMembers(type)) {
            if (member.getKind() != ElementKind.METHOD) continue;
            ExecutableElement e = (ExecutableElement) member;
            if (!e.getParameters().isEmpty()) continue;
            String javaName = e.getSimpleName().toString();
            if (!reads.containsKey(javaName)) continue; // Not a record component accessor
            String npName = GeneratorUtil.nodePropertyName(e, null);
            if (npName != null && !npName.equals(javaName)) {
                reads.put(npName, reads.get(javaName));
                javaToJsonName.put(javaName, npName);
            } else if (javaToJsonName.get(javaName) == null) {
                javaToJsonName.put(javaName, javaName);
            }
        }
        // Fill remaining javaToJsonName entries with Java names
        for (String javaName : javaNames) {
            if (!javaToJsonName.containsKey(javaName)) {
                javaToJsonName.put(javaName, javaName);
            }
        }
    }

    private static void _addRead(Map<String, Read> r, Set<String> javaNames, Map<String, String> javaToJsonName,
                                  String javaName, Read read, Element element) {
        r.put(javaName, read);
        javaNames.add(javaName);
        String npName = GeneratorUtil.nodePropertyName(element, null);
        if (npName != null && !npName.equals(javaName)) {
            r.put(npName, read);
            javaToJsonName.put(javaName, npName);
        } else {
            javaToJsonName.put(javaName, javaName);
        }
    }

    @SuppressWarnings("unchecked")
    private ReadsResult _recordReads(TypeElement type) {
        try {
            Method method = TypeElement.class.getMethod("getRecordComponents");
            Object value = method.invoke(type);
            if (!(value instanceof List)) return null;

            Map<String, Read> r = new LinkedHashMap<String, Read>();
            Set<String> javaNames = new LinkedHashSet<String>();
            Map<String, String> javaToJsonName = new LinkedHashMap<String, String>();
            for (Object component : (List<Object>) value) {
                if (!(component instanceof Element)) return null;
                Element e = (Element) component;
                String name = e.getSimpleName().toString();
                r.put(name, new Read(name, e.asType()));
                javaNames.add(name);
            }
            return new ReadsResult(r, javaNames, javaToJsonName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private TypeMirror _elementType(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) return ((ArrayType) type).getComponentType();
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.listType)) return GeneratorUtil.listValueType(ctx, type);
        return null;
    }

    private String _paramType(TypeMirror type) {
        return type.getKind().isPrimitive() ? type.toString() : GeneratorUtil.localTypeName(ctx, type);
    }

    private void _error(Element element, GeneratedClass target, String message) {
        ctx.error(element, target.originName() + ": " + message);
    }

    private enum ReturnKind { BOOLEAN, VOID, RESULT }

    private enum CompileJsonKind { OBJECT, ARRAY, STRING, BOOLEAN, NUMBER, INTEGER_NUMBER, UNKNOWN }

    private static final class State {
        final GeneratedClass target;
        int nextHelper;
        int nextLocal;
        int nextField;
        String unsupportedReason;
        final Set<SchemaPlan> inProgress = Collections.newSetFromMap(new IdentityHashMap<SchemaPlan, Boolean>());
        final Map<String, String> formatFields = new LinkedHashMap<String, String>();
        final Map<String, String> localPlanFields = new LinkedHashMap<String, String>();
        String currentRootJson;
        String currentRootUri;
        boolean strictFormat;
        boolean strictValidatorField;
        boolean lenientValidatorField;
        State(GeneratedClass t) { target = t; }
    }

    private static final class Read {
        final ExecutableElement method;
        final String methodName;
        final String field;
        final TypeMirror type;
        Read(ExecutableElement m, String f, TypeMirror t) { method = m; methodName = null; field = f; type = t; }
        Read(String m, TypeMirror t) { method = null; methodName = m; field = null; type = t; }
        String code(String root) {
            if (method != null) return root + "." + method.getSimpleName() + "()";
            if (methodName != null) return root + "." + methodName + "()";
            return root + "." + field;
        }
    }

    private static final class ReadsResult {
        final Map<String, Read> reads;
        final Set<String> javaNames;
        final Map<String, String> javaToJsonName;
        ReadsResult(Map<String, Read> reads, Set<String> javaNames, Map<String, String> javaToJsonName) {
            this.reads = reads;
            this.javaNames = javaNames;
            this.javaToJsonName = javaToJsonName;
        }
    }

    private static final class PlanResult {
        final boolean supported;
        final String methodName;
        final String reason;
        PlanResult(boolean s, String m, String r) { supported = s; methodName = m; reason = r; }
    }

    private static final class CompileResult {
        static final CompileResult OK = new CompileResult(true, null);
        final boolean supported;
        final String reason;
        CompileResult(boolean s, String r) { supported = s; reason = r; }
        static CompileResult unsupported(String reason) { return new CompileResult(false, reason); }
    }
}
