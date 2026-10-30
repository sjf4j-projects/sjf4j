package org.sjf4j.processor.validation;

import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.NodeKind;
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

        boolean allowFallback = iface.getAnnotation(CompiledValidator.class).fallback();
        for (ResolvedMethod method : methods) {
            generateMethod(method, generated, fields, bySnapshot, allowFallback);
        }
        if (!generated.isValid()) return;

        generated.addHelper(out -> {
            out.beginBlock("private static final class Fallback");
            for (SchemaField field : fields) {
                if (!field.usedFallback) continue;
                out.beginBlock("private static final class " + field.name);
                String registry = "REGISTRY";
                out.line("private static final org.sjf4j.schema.SchemaRegistry " + registry
                        + " = new org.sjf4j.schema.SchemaRegistry();");
                out.line("static final org.sjf4j.schema.SchemaPlan ROOT = load();");
                for (Subplan sub : field.subplans) {
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
        generated.write();
    }

    private void generateMethod(ResolvedMethod resolved, GeneratedClass generated,
                                List<SchemaField> fields, Map<String, String> bySnapshot, boolean allowFallback) {
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

        // Select the fast path before source emission, so fallback=false
        // produces an annotation-processing error rather than hidden runtime work.
        SchemaField bound = null;
        String booleanLiteral = null;
        DirectCheck direct = null;
        if (isBoolean && bindings.size() == 1) {
            String selected = names.get(0).substring("Fallback.".length(),
                    names.get(0).length() - ".ROOT".length());
            for (SchemaField field : fields) {
                if (field.name.equals(selected)) bound = field;
            }
            if (bound != null) {
                booleanLiteral = compileBoolean(bound.snapshot);
                if (booleanLiteral == null) {
                    direct = prepareDirect(parameterType, value, bound, strictParam);
                }
            }
        }

        boolean fallback = booleanLiteral == null &&
                (direct == null || !direct.subplans.isEmpty());
        if (fallback && !allowFallback) {
            fail(generated, method, "@CompiledValidator(fallback=false) cannot fully compile this schema and method");
            return;
        }
        if (direct != null) {
            for (Subplan sub : direct.subplans) {
                boolean present = false;
                for (Subplan existing : bound.subplans) {
                    if (existing.fieldName.equals(sub.fieldName)) { present = true; break; }
                }
                if (!present) bound.subplans.add(sub);
            }
            if (!direct.subplans.isEmpty()) bound.usedFallback = true;
        } else if (fallback) {
            for (SchemaField field : fields) {
                if (names.contains("Fallback." + field.name + ".ROOT")) field.usedFallback = true;
            }
        }

        final String literal = booleanLiteral;
        final DirectCheck checks = direct;
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
                    out.line("if (!" + plan + ".isValid(" + value + ", " + strictParam + ")) return false;");
                }
                out.line("return true;");
            } else if (isVoid) {
                for (String plan : names) {
                    out.line(plan + ".requireValid(" + value + ", " + strictParam + ");");
                }
            } else if (names.size() == 1) {
                out.line("return " + names.get(0) + ".validate(" + value + ", false, " + strictParam + ");");
            } else {
                // All annotated schemas participate; report all failures in
                // parent-to-child order instead of stopping at the first failure.
                out.line("java.util.List<org.sjf4j.schema.ValidationMessage> messages = new java.util.ArrayList<>();");
                out.line("boolean valid = true;");
                out.line("org.sjf4j.schema.ValidationResult result;");
                for (String plan : names) {
                    out.line("result = " + plan + ".validate(" + value + ", false, " + strictParam + ");");
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
    private DirectCheck prepareDirect(TypeMirror type, String variable,
                                      SchemaField field, String strictFormat) {
        SchemaResourceResolver.Snapshot snapshot = field.snapshot;
        if (snapshot.documents.length != 1 ||
                !snapshot.selectedUri.equals(snapshot.uris[0])) return null;
        if (context.types.nodeKind(type) != NodeKind.OBJECT_POJO) return null;
        TypeElement owner = context.types.typeElement(type);
        if (owner == null || !owner.getModifiers().contains(Modifier.FINAL) ||
                !owner.getModifiers().contains(Modifier.PUBLIC) ||
                owner.getAnnotation(org.sjf4j.annotation.node.NodeObject.class) != null) return null;

        Object parsed = org.sjf4j.Sjf4j.global().fromJson(snapshot.documents[0]);
        if (org.sjf4j.JsonType.of(parsed) != org.sjf4j.JsonType.OBJECT) return null;
        org.sjf4j.JsonObject root = org.sjf4j.Nodes.toJsonObject(parsed);
        for (String key : root.keySet()) {
            if (!key.equals("type") && !key.equals("properties") && !key.equals("required")
                    && !key.equals("additionalProperties") && !isAnnotation(key)) return null;
        }
        if (!"object".equals(root.getString("type"))) return null;
        Object raw = root.getNode("properties");
        if (org.sjf4j.JsonType.of(raw) != org.sjf4j.JsonType.OBJECT) return null;
        org.sjf4j.JsonObject propertyMap = org.sjf4j.Nodes.toJsonObject(raw);
        Map<String, Property> javaProperties = context.properties.resolve(type);
        if (javaProperties.isEmpty()) return null;

        // POJOs expose declared readable properties even when their values are
        // null. A required property therefore needs a getter, not a non-null value.
        Object required = root.getNode("required");
        if (required != null) {
            if (org.sjf4j.JsonType.of(required) != org.sjf4j.JsonType.ARRAY) return null;
            for (Object name : org.sjf4j.Nodes.toJsonArray(required)) {
                if (!(name instanceof String) || !javaProperties.containsKey(name)) return null;
            }
        }
        Object additional = root.getNode("additionalProperties");
        if (additional != null) {
            if (!(additional instanceof Boolean)) return null;
            if (Boolean.FALSE.equals(additional) && !propertyMap.keySet().containsAll(javaProperties.keySet())) {
                // Extra Java properties would always violate additionalProperties:false;
                // use the complete runtime plan rather than silently ignoring them.
                return null;
            }
        }
        for (String name : propertyMap.keySet()) {
            if (!javaProperties.containsKey(name)) {
                // An absent POJO property does not trigger the properties keyword.
                // It is safe when it is not required.
                if (isRequired(required, name)) return null;
            }
        }
        for (Property property : javaProperties.values()) {
            if (property.read() == null ||
                    !property.read().member().getModifiers().contains(Modifier.PUBLIC) ||
                    (property.read().isMethod() &&
                            !((ExecutableElement) property.read().member()).getThrownTypes().isEmpty()) ||
                    !property.name().matches("[a-zA-Z0-9_]+")) return null;
        }

        List<String> statements = new ArrayList<String>();
        List<Subplan> subplans = new ArrayList<Subplan>();
        int directCount = 0;
        int index = 0;
        for (Map.Entry<String, Property> entry : javaProperties.entrySet()) {
            String name = entry.getKey();
            if (!propertyMap.containsKey(name)) continue;
            String local = "_property" + index;
            String access = entry.getValue().read().memberName()
                    + (entry.getValue().read().isMethod() ? "()" : "");
            List<String> checks = new ArrayList<String>();
            List<Subplan> nested = new ArrayList<Subplan>();
            Object propertySchema = propertyMap.getNode(name);
            boolean supported = compileValue(checks, nested, propertySchema, entry.getValue().read().type(), local,
                    "Fallback." + field.name, "SUB_" + index,
                    snapshot.selectedUri + "#/properties/" + name, strictFormat, 0);
            statements.add("Object " + local + " = " + variable + "." + access + ";");
            if (supported) {
                directCount++;
                statements.addAll(checks);
                subplans.addAll(nested);
            } else {
                // The whole property must be checked by the fallback plan: a
                // partially emitted predicate could change JSON Schema semantics.
                subplans.add(new Subplan("SUB_" + index,
                        snapshot.selectedUri + "#/properties/" + name));
                statements.add("if (!Fallback." + field.name + ".SUB_" + index
                        + ".isValid(" + local + ", " + strictFormat + ")) return false;");
            }
            index++;
        }
        if (directCount == 0) return null;
        return new DirectCheck(statements, subplans);
    }

    /** Only JSON Schema annotation / definition keywords may be skipped. */
    private static boolean isAnnotation(String key) {
        return key.equals("$schema") || key.equals("$id") || key.equals("title")
                || key.equals("description") || key.equals("default") || key.equals("examples")
                || key.equals("$comment") || key.equals("deprecated") || key.equals("readOnly")
                || key.equals("writeOnly") || key.equals("definitions") || key.equals("$defs");
    }

    private static boolean isRequired(Object required, String key) {
        if (required == null) return false;
        for (Object value : org.sjf4j.Nodes.toJsonArray(required)) {
            if (key.equals(value)) return true;
        }
        return false;
    }

    /**
     * Generates value constraints without materializing a generic node tree.
     * On an unsupported subtree this method returns false; the caller emits a
     * full subtree fallback instead. Localized array-items fallback remains
     * possible without discarding the array's direct size checks.
     */
    private boolean compileValue(List<String> lines, List<Subplan> subplans,
                                 Object rawSchema, TypeMirror declaredType,
                                 String value, String planClass,
                                 String planName, String schemaUri,
                                 String strictFormat, int depth) {
        if (depth > 8) return false;
        if (rawSchema instanceof Boolean) {
            if (!((Boolean) rawSchema).booleanValue()) lines.add("return false;");
            return true;
        }
        if (org.sjf4j.JsonType.of(rawSchema) != org.sjf4j.JsonType.OBJECT) return false;
        org.sjf4j.JsonObject schema = org.sjf4j.Nodes.toJsonObject(rawSchema);
        String kind = schema.getString("type");
        if (kind == null) return false;

        if ("string".equals(kind)) {
            // Custom value codecs may expose a different JSON shape. Do not
            // bypass their conversion by guessing from a Java field type.
            if (!isNativeString(declaredType)) return false;
            for (String key : schema.keySet()) {
                if (!key.equals("type") && !key.equals("minLength") && !key.equals("maxLength")
                        && !key.equals("enum") && !isAnnotation(key)) return false;
            }
            Integer min = schema.getInt("minLength");
            Integer max = schema.getInt("maxLength");
            if ((schema.containsKey("minLength") && (min == null || min < 0))
                    || (schema.containsKey("maxLength") && (max == null || max < 0))) return false;
            Object enumeration = schema.getNode("enum");
            List<String> choices = new ArrayList<String>();
            if (enumeration != null) {
                if (org.sjf4j.JsonType.of(enumeration) != org.sjf4j.JsonType.ARRAY) return false;
                for (Object e : org.sjf4j.Nodes.toJsonArray(enumeration)) {
                    if (!(e instanceof String)) return false;
                    choices.add((String) e);
                }
            }
            // String and Enum have different native Java types but the same
            // JSON string representation. Use the enum name, never toString().
            String textName = "_text" + depth + "_" + planName;
            lines.add("if (!(" + value + " instanceof String) && !(" + value
                    + " instanceof Enum) && !(" + value + " instanceof Character)) return false;");
            lines.add("String " + textName + " = " + value + " instanceof String ? (String) "
                    + value + " : " + value + " instanceof Enum ? ((Enum<?>) " + value
                    + ").name() : " + value + ".toString();");
            if (min != null || max != null) {
                String len = "_length" + depth + "_" + planName;
                lines.add("int " + len + " = " + textName
                        + ".codePointCount(0, " + textName + ".length());");
                if (min != null) lines.add("if (" + len + " < " + min + ") return false;");
                if (max != null) lines.add("if (" + len + " > " + max + ") return false;");
            }
            if (enumeration != null) {
                StringBuilder condition = new StringBuilder();
                for (String choice : choices) {
                    if (condition.length() != 0) condition.append(" && ");
                    condition.append("!").append(textName).append(".equals(")
                            .append(javaLiteral(choice)).append(")");
                }
                if (condition.length() == 0) lines.add("return false;");
                else lines.add("if (" + condition + ") return false;");
            }
            return true;
        }

        if ("number".equals(kind) || "integer".equals(kind)) {
            if (!isNativeNumber(declaredType)) return false;
            for (String key : schema.keySet()) {
                if (!key.equals("type") && !key.equals("minimum") && !key.equals("maximum")
                        && !key.equals("exclusiveMinimum") && !key.equals("exclusiveMaximum")
                        && !isAnnotation(key)) return false;
            }
            lines.add("if (!(" + value + " instanceof Number)) return false;");
            if ("integer".equals(kind)) {
                lines.add("if (!org.sjf4j.util.Numbers.isSemanticInteger((Number) "
                        + value + ")) return false;");
            }
            for (String key : new String[]{"minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum"}) {
                Object limit = schema.getNode(key);
                if (limit == null) continue;
                if (!(limit instanceof Number)) return false;
                String bound = decimalConstant((Number) limit);
                String comparison = "org.sjf4j.util.Numbers.compare((Number) " + value + ", " + bound + ")";
                String operator = key.equals("minimum") ? " < 0"
                        : key.equals("maximum") ? " > 0"
                        : key.equals("exclusiveMinimum") ? " <= 0" : " >= 0";
                lines.add("if (" + comparison + operator + ") return false;");
            }
            return true;
        }

        if ("array".equals(kind)) {
            if (declaredType == null || !(declaredType instanceof DeclaredType)) return false;
            String rawCollection = context.typeUtils.erasure(declaredType).toString();
            if (!rawCollection.equals("java.util.List") && !rawCollection.equals("java.util.Set")
                    && !rawCollection.equals("java.util.Collection")) return false;
            for (String key : schema.keySet()) {
                if (!key.equals("type") && !key.equals("items") && !key.equals("minItems")
                        && !key.equals("maxItems") && !isAnnotation(key)) return false;
            }
            Integer min = schema.getInt("minItems");
            Integer max = schema.getInt("maxItems");
            if ((schema.containsKey("minItems") && (min == null || min < 0))
                    || (schema.containsKey("maxItems") && (max == null || max < 0))) return false;
            lines.add("if (!(" + value + " instanceof java.util.Collection)) return false;");
            String size = "((java.util.Collection<?>) " + value + ").size()";
            if (min != null) lines.add("if (" + size + " < " + min + ") return false;");
            if (max != null) lines.add("if (" + size + " > " + max + ") return false;");
            if (!schema.containsKey("items")) return true;
            Object itemSchema = schema.getNode("items");
            String itemVar = "_item" + depth + "_" + planName;
            List<String> itemLines = new ArrayList<String>();
            List<Subplan> itemPlans = new ArrayList<Subplan>();
            List<? extends TypeMirror> args = ((DeclaredType) declaredType).getTypeArguments();
            TypeMirror itemType = args.size() == 1 ? args.get(0) : null;
            boolean directItems = compileValue(itemLines, itemPlans, itemSchema, itemType, itemVar,
                    planClass, planName + "_ITEM", schemaUri + "/items", strictFormat, depth + 1);
            lines.add("for (Object " + itemVar + " : (java.util.Collection<?>) " + value + ") {");
            if (directItems) {
                for (String line : itemLines) lines.add("    " + line);
                subplans.addAll(itemPlans);
            } else {
                String sub = planName + "_ITEM";
                subplans.add(new Subplan(sub, schemaUri + "/items"));
                lines.add("    if (!" + planClass + "." + sub + ".isValid("
                        + itemVar + ", " + strictFormat + ")) return false;");
            }
            lines.add("}");
            return true;
        }
        return false;
    }

    private boolean isNativeString(TypeMirror type) {
        if (type == null) return false;
        String name = type.toString();
        if (name.equals("java.lang.String") || name.equals("java.lang.Character")
                || name.equals("char")) return true;
        TypeElement element = context.types.typeElement(type);
        return element != null && element.getKind() == javax.lang.model.element.ElementKind.ENUM;
    }

    private static boolean isNativeNumber(TypeMirror type) {
        if (type == null) return false;
        switch (type.toString()) {
            case "byte": case "short": case "int": case "long":
            case "float": case "double":
            case "java.lang.Byte": case "java.lang.Short":
            case "java.lang.Integer": case "java.lang.Long":
            case "java.lang.Float": case "java.lang.Double":
            case "java.math.BigDecimal": case "java.math.BigInteger":
                return true;
            default:
                return false;
        }
    }

    private static String decimalConstant(Number number) {
        String decimal = number.toString();
        if ("0".equals(decimal) || "0.0".equals(decimal)) return "java.math.BigDecimal.ZERO";
        if ("1".equals(decimal) || "1.0".equals(decimal)) return "java.math.BigDecimal.ONE";
        return "new java.math.BigDecimal(" + javaLiteral(decimal) + ")";
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

    private static final class DirectCheck {
        final List<String> statements;
        final List<Subplan> subplans;

        DirectCheck(List<String> statements, List<Subplan> subplans) {
            this.statements = statements;
            this.subplans = subplans;
        }
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
