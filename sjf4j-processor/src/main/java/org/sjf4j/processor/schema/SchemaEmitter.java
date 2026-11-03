package org.sjf4j.processor.schema;

import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.NameAllocator;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits the existing native fail-fast predicates from structured SchemaCheck
 * trees. Fallback references are allocated by SchemaFallbackEmitter.
 */
final class SchemaEmitter {
    static final String DIAGNOSTIC_FAILURE = "__SJF4J_VALIDATION_FAILURE__";
    private String instancePath = "org.sjf4j.path.PathSegment.Root.INSTANCE";
    private final GeneratedClass generated;
    private final String helperPrefix;
    private NameAllocator locals;
    private final Map<String, String> decimalConstants = new LinkedHashMap<String, String>();
    private int helperCount;

    SchemaEmitter(GeneratedClass generated, String helperPrefix, NameAllocator locals) {
        this.generated = generated;
        this.helperPrefix = helperPrefix;
        this.locals = locals;
    }

    private String fail(String action, SchemaCheck.Diagnostic d) {
        if (!DIAGNOSTIC_FAILURE.equals(action)) return action;
        return "throw org.sjf4j.schema.CompiledUtil.validationFailure("
                + literal(d == null || d.keyword == null ? "schema" : d.keyword) + ", "
                + (d == null || d.schemaUri == null ? "null" : literal(d.schemaUri)) + ", "
                + literal(d == null || d.keywordPointer == null ? "" : d.keywordPointer)
                + ", " + instancePath + ");";
    }

    List<String> emitRoot(SchemaCheck check, SchemaValue value, String variable,
                          SchemaFallbackEmitter.Plan plan, String strictFormat,
                          String onFailure) {
        // Root and nested checks share the same null/type applicability rules.
        // Static POJOs must reject null for type:object and skip object-only
        // keywords on null when no object type is required.
        List<String> lines = new ArrayList<String>();
        emitValue(lines, plan, check, value, variable, helperPrefix, strictFormat, 0, onFailure);
        return lines;
    }

    List<String> emit(SchemaCheck.ObjectCheck check, String variable, SchemaFallbackEmitter.Plan plan,
                      String strictFormat, String onFailure) {
        return emit(check, variable, plan, strictFormat, "", onFailure);
    }

    private List<String> emit(SchemaCheck.ObjectCheck check, String variable,
                              SchemaFallbackEmitter.Plan plan, String strictFormat,
                              String prefix, String onFailure) {
        List<String> statements = new ArrayList<String>();
        if (check.minProperties != null || check.maxProperties != null) {
            String count = check.jsonObject ? variable + ".size()"
                    : check.dynamic
                    ? "org.sjf4j.node.NodeWalker.sizeInObject(" + variable + ")"
                    : Integer.toString(check.staticPropertyCount);
            if (check.minProperties != null) {
                statements.add("if (" + count + " < " + check.minProperties + ") " + fail(onFailure, check.minPropertiesDiagnostic));
            }
            if (check.maxProperties != null) {
                statements.add("if (" + count + " > " + check.maxProperties + ") " + fail(onFailure, check.maxPropertiesDiagnostic));
            }
        }
        if (check.dynamic) {
            // propertyNames is an independent assertion over each key string.
            // No property value is read, and no name is marked as covered.
            if (check.propertyNamesCheck != null
                    && check.propertyNamesCheck.kind != SchemaCheck.Kind.PASS) {
                String key = locals.newName("propertyName");
                statements.add("for (String " + key + " : " + (check.jsonObject
                        ? variable + ".keySet()"
                        : "org.sjf4j.Nodes.keySetInObject(" + variable + ")") + ") {");
                String previousPath = instancePath;
                instancePath = "new org.sjf4j.path.PathSegment.Name(" + previousPath + ", " + key + ")";
                List<String> nameChecks = new ArrayList<String>();
                emitValue(nameChecks, plan, check.propertyNamesCheck, check.elementValue,
                        key, prefix + "PROPERTY_NAME", strictFormat, 0, onFailure);
                instancePath = previousPath;
                for (String statement : nameChecks) statements.add("    " + statement);
                statements.add("}");
            }
            // Each dependentSchemas branch checks the containing object, not
            // its trigger value. Local additionalProperties coverage is untouched.
            if (!check.dependentSchemas.isEmpty()) {
                String names = locals.newName("presentNames");
                statements.add("java.util.Set<String> " + names + " = "
                        + (check.jsonObject ? variable + ".keySet()"
                        : "org.sjf4j.Nodes.keySetInObject(" + variable + ")") + ";");
                int branch = 0;
                for (SchemaCheck.DependentSchema dependency : check.dependentSchemas) {
                    statements.add("if (" + names + ".contains(" + literal(dependency.trigger) + ")) {");
                    List<String> nested = new ArrayList<String>();
                    emitValue(nested, plan, dependency.check, check.elementValue, variable,
                            prefix + "DEPENDENT_SCHEMA_" + branch++, strictFormat, 0, onFailure);
                    for (String statement : nested) statements.add("    " + statement);
                    statements.add("}");
                }
            }
            for (SchemaCheck.Required required : check.required) {
                String contains = check.jsonObject
                        ? variable + ".containsKey(" + literal(required.name) + ")"
                        : "org.sjf4j.Nodes.keySetInObject(" + variable
                                + ").contains(" + literal(required.name) + ")";
                statements.add("if (!" + contains + ") " + fail(onFailure, check.requiredDiagnostic));
            }
            if (!check.dependentRequired.isEmpty()) {
                String keys = locals.newName("keys");
                statements.add("java.util.Set<String> " + keys + " = "
                        + (check.jsonObject ? variable + ".keySet()"
                        : "org.sjf4j.Nodes.keySetInObject(" + variable + ")") + ";");
                for (SchemaCheck.DependentRequired dependency : check.dependentRequired) {
                    StringBuilder missing = new StringBuilder();
                    for (String name : dependency.dependencies) {
                        if (missing.length() != 0) missing.append(" || ");
                        missing.append("!").append(keys).append(".contains(")
                                .append(literal(name)).append(")");
                    }
                    statements.add("if (" + keys + ".contains(" + literal(dependency.trigger)
                            + ") && (" + missing + ")) " + fail(onFailure, check.dependentRequiredDiagnostic));
                }
            }
            // Coverage belongs only to sibling properties and patternProperties.
            // Applicators and dependentSchemas do not contribute to this set.
            if (check.additionalPropertiesFalse || check.additionalPropertiesCheck != null
                    || !check.patternProperties.isEmpty()) {
                String key = locals.newName("key");
                String covered = locals.newName("covered");
                statements.add("for (String " + key + " : " + (check.jsonObject ? variable + ".keySet()"
                        : "org.sjf4j.Nodes.keySetInObject(" + variable + ")") + ") {");
                StringBuilder named = new StringBuilder();
                for (String allowed : check.allowedNames) {
                    if (named.length() != 0) named.append(" || ");
                    named.append(key).append(".equals(").append(literal(allowed)).append(")");
                }
                statements.add("    boolean " + covered + " = "
                        + (named.length() == 0 ? "false" : named.toString()) + ";");
                int patternIndex = 0;
                for (SchemaCheck.PatternProperty pattern : check.patternProperties) {
                    String regex = generated.names().field("PROPERTY_PATTERN");
                    generated.addField(out -> out.line("private static final java.util.regex.Pattern "
                            + regex + " = java.util.regex.Pattern.compile(" + literal(pattern.javaPattern) + ");"));
                    statements.add("    if (" + regex + ".matcher(" + key + ").find()) {");
                    statements.add("        " + covered + " = true;");
                    if (pattern.check.kind != SchemaCheck.Kind.PASS) {
                        String element = locals.newName("element");
                        statements.add("        Object " + element + " = " + (check.jsonObject
                                ? variable + ".getNode(" + key + ")"
                                : "org.sjf4j.schema.CompiledUtil.objectPropertyValue(" + variable + ", " + key + ")") + ";");
                        String previousPath = instancePath;
                        instancePath = "new org.sjf4j.path.PathSegment.Name(" + previousPath + ", " + key + ")";
                        List<String> children = new ArrayList<String>();
                        emitValue(children, plan, pattern.check, check.elementValue, element,
                                prefix + "PATTERN_" + patternIndex, strictFormat, 0, onFailure);
                        instancePath = previousPath;
                        for (String statement : children) statements.add("        " + statement);
                    }
                    statements.add("    }");
                    patternIndex++;
                }
                if (check.additionalPropertiesFalse) {
                    statements.add("    if (!" + covered + ") " + fail(onFailure, check.additionalPropertiesDiagnostic));
                } else if (check.additionalPropertiesCheck != null
                        && check.additionalPropertiesCheck.kind != SchemaCheck.Kind.PASS) {
                    String element = locals.newName("additional");
                    statements.add("    if (!" + covered + ") {");
                    statements.add("        Object " + element + " = " + (check.jsonObject
                            ? variable + ".getNode(" + key + ")"
                            : "org.sjf4j.schema.CompiledUtil.objectPropertyValue(" + variable + ", " + key + ")") + ";");
                    String previousPath = instancePath;
                    instancePath = "new org.sjf4j.path.PathSegment.Name(" + previousPath + ", " + key + ")";
                    List<String> children = new ArrayList<String>();
                    emitValue(children, plan, check.additionalPropertiesCheck, check.elementValue,
                            element, prefix + "ADDITIONAL", strictFormat, 0, onFailure);
                    instancePath = previousPath;
                    for (String statement : children) statements.add("        " + statement);
                    statements.add("    }");
                }
                statements.add("}");
            }
        }

        int index = 0;
        for (SchemaCheck.PropertyCheck property : check.properties) {
            String local = locals.newName(property.name);
            String access = check.jsonObject
                    ? variable + ".getNode(" + literal(property.name) + ")"
                    : check.dynamic
                    ? "org.sjf4j.schema.CompiledUtil.objectPropertyValue(" + variable + ", " + literal(property.name) + ")"
                    : variable + "." + property.staticAccess;
            statements.add((property.codecOwner == null ? property.value.localType() : "Object")
                    + " " + local + " = " + access + ";");
            String checkedValue = local;
            if (property.codecOwner != null && property.check.kind != SchemaCheck.Kind.PASS) {
                String codecField = generated.names().field(property.name.toUpperCase(java.util.Locale.ROOT) + "_CODEC");
                generated.addField(out -> out.line("private static final org.sjf4j.value.ValueInfo "
                        + codecField + " = org.sjf4j.meta.TypeRegistry.requireRegisteredPojoInfo("
                        + property.codecOwner + ".class).getPropertyNoAlias("
                        + literal(property.name) + ").valueInfo;"));
                checkedValue = locals.newName("rawValue");
                String encodedType = property.value.shape == SchemaValue.Shape.STRING
                        || property.value.shape == SchemaValue.Shape.NUMBER
                        || property.value.shape == SchemaValue.Shape.BOOLEAN
                        ? property.value.type.toString() : "Object";
                String encoded = codecField + ".valueToRaw(" + local + ")";
                if (!"Object".equals(encodedType)) {
                    encoded = "(" + encodedType + ") " + encoded;
                }
                statements.add(encodedType + " " + checkedValue + " = " + local
                        + " == null ? null : " + encoded + ";");
            }
            if (check.dynamic) {
                String contains = check.jsonObject
                        ? variable + ".containsKey(" + literal(property.name) + ")"
                        : "org.sjf4j.Nodes.keySetInObject(" + variable
                                + ").contains(" + literal(property.name) + ")";
                statements.add("if (" + contains + ") {");
            }
            List<String> lines = new ArrayList<String>();
            String parentPath = instancePath;
            instancePath = "new org.sjf4j.path.PathSegment.Name("
                    + parentPath + ", " + literal(property.name) + ")";
            emitValue(lines, plan, property.check, property.value, checkedValue,
                    prefix + "SUB_" + index, strictFormat, 0, onFailure);
            instancePath = parentPath;
            for (String line : lines) {
                statements.add((check.dynamic ? "    " : "") + line);
            }
            if (check.dynamic) statements.add("}");
            index++;
        }
        return statements;
    }

    private void emitValue(List<String> lines, SchemaFallbackEmitter.Plan plan,
                           SchemaCheck check, SchemaValue value, String expression,
                           String planName, String strictFormat, int depth,
                           String onFailure) {
        switch (check.kind) {
            case PASS:
                return;
            case TYPE:
                emitType(lines, (SchemaCheck.TypeCheck) check, value, expression, onFailure);
                return;
            case SHAPE_GUARD:
                SchemaCheck.ShapeGuard guard = (SchemaCheck.ShapeGuard) check;
                // A known Java projection is only an emission fast path; it
                // never determines whether a schema keyword can compile.
                boolean knownShape = ("OBJECT".equals(guard.jsonType) && value.shape == SchemaValue.Shape.OBJECT)
                        || ("STRING".equals(guard.jsonType) && value.shape == SchemaValue.Shape.STRING)
                        || ("NUMBER".equals(guard.jsonType) && value.shape == SchemaValue.Shape.NUMBER)
                        || ("ARRAY".equals(guard.jsonType) && value.isArrayLike());
                // Even when this Java value cannot have the required shape,
                // the schema was fully analyzed before we fold away emission.
                // Avoid unreachable primitive code such as value.getClass().
                if (!knownShape && value.type != null && value.type.getKind().isPrimitive()) return;
                String condition = knownShape
                        ? (value.nullable ? expression + " != null" : "true")
                        : "org.sjf4j.JsonType.of(" + expression
                          + ") == org.sjf4j.JsonType." + guard.jsonType;
                List<String> guarded = new ArrayList<String>();
                emitValue(guarded, plan, guard.child, guard.value, expression,
                        planName + "_", strictFormat, depth, onFailure);
                if ("true".equals(condition)) {
                    lines.addAll(guarded);
                } else {
                    lines.add("if (" + condition + ") {");
                    for (String statement : guarded) lines.add("    " + statement);
                    lines.add("}");
                }
                return;
            case FALLBACK:
                SchemaFallbackEmitter.Subplan sub = plan.subplan(
                        ((SchemaCheck.Fallback) check).uri, planName);
                if (DIAGNOSTIC_FAILURE.equals(onFailure)) {
                    // Preserve the enclosing instance location across a real
                    // fallback without executing the root SchemaPlan.
                    String fallbackError = locals.newName("fallbackError");
                    lines.add("try { " + sub.reference() + ".requireValid(" + expression
                            + ", " + strictFormat + "); } catch (org.sjf4j.schema.ValidationException " + fallbackError + ") {"
                            + " throw org.sjf4j.schema.CompiledUtil.rebaseFailure(" + fallbackError + ", "
                            + instancePath + "); }");
                } else {
                    lines.add("if (!" + sub.reference() + ".isValid("
                            + expression + ", " + strictFormat + ")) " + onFailure);
                }
                return;
            case STRING:
                emitString(lines, (SchemaCheck.StringCheck) check, value, expression, planName, depth,
                        strictFormat, onFailure);
                return;
            case NUMBER:
                emitNumber(lines, (SchemaCheck.NumberCheck) check, value, expression, planName, depth, onFailure);
                return;
            case ARRAY:
                emitArray(lines, plan, (SchemaCheck.ArrayCheck) check, value, expression,
                        planName, strictFormat, depth, onFailure);
                return;
            case ONE_OF:
                emitOneOf(lines, plan, (SchemaCheck.OneOfCheck) check, value, expression,
                        planName, strictFormat, depth, onFailure);
                return;
            case GENERIC_ONE_OF:
                emitGenericOneOf(lines, plan, (SchemaCheck.GenericOneOfCheck) check,
                        value, expression, planName, strictFormat, onFailure);
                return;
            case IF_THEN_ELSE:
                emitConditional(lines, plan, (SchemaCheck.ConditionalCheck) check,
                        value, expression, strictFormat, onFailure);
                return;
            case ALL_OF:
            case ANY_OF:
            case NOT:
                emitApplicator(lines, plan, (SchemaCheck.ApplicatorCheck) check,
                        value, expression, strictFormat, onFailure);
                return;
            case OBJECT:
                SchemaCheck.ObjectCheck object = (SchemaCheck.ObjectCheck) check;
                // Logical @NodeValue instances remain leaves even if their
                // serialized raw representation is a Map or List. Never unwrap
                // them to traverse the raw encoding as an object.
                // Dynamic Nodes traversal does not imply an unknown JSON
                // shape: a final POJO may still be a known object even when
                // direct field access is unavailable.
                boolean unknownShape = object.dynamic
                        && value.shape != SchemaValue.Shape.OBJECT;
                if (object.typeRequired) {
                    if (unknownShape) {
                        lines.add("if (org.sjf4j.JsonType.of(" + expression
                                + ") != org.sjf4j.JsonType.OBJECT) " + fail(onFailure, object.typeDiagnostic));
                    } else if (value.nullable) {
                        lines.add("if (" + expression + " == null) " + fail(onFailure, object.typeDiagnostic));
                    }
                    lines.addAll(emit(object, expression, plan, strictFormat, planName + "_", onFailure));
                } else if (unknownShape || value.nullable) {
                    String applies = unknownShape
                            ? "org.sjf4j.JsonType.of(" + expression + ") == org.sjf4j.JsonType.OBJECT"
                            : expression + " != null";
                    lines.add("if (" + applies + ") {");
                    for (String statement : emit(object, expression, plan, strictFormat,
                            planName + "_", onFailure)) lines.add("    " + statement);
                    lines.add("}");
                } else {
                    lines.addAll(emit(object, expression, plan, strictFormat, planName + "_", onFailure));
                }
                return;
            case REJECT:
                // Keep following checks reachable in Java source; JIT folds this branch.
                lines.add("if (java.lang.Boolean.TRUE) " + fail(onFailure, check.diagnostic));
                return;
            case LITERAL:
                emitLiteral(lines, (SchemaCheck.LiteralCheck) check, value, expression, onFailure);
                return;
            case ALL:
                for (SchemaCheck child : ((SchemaCheck.AllCheck) check).checks) {
                    emitValue(lines, plan, child, value, expression, planName, strictFormat, depth, onFailure);
                }
                return;
            default:
                throw new IllegalStateException("unsupported compiled value kind: " + check.kind);
        }
    }

    /** Each branch is a normal static predicate; the caller counts successes. */
    private void emitGenericOneOf(List<String> lines, SchemaFallbackEmitter.Plan plan,
                                  SchemaCheck.GenericOneOfCheck check, SchemaValue value,
                                  String expression, String planName, String strictFormat, String onFailure) {
        String matches = locals.newName("matchCount");
        lines.add("int " + matches + " = 0;");
        for (SchemaCheck branch : check.branches) {
            if (branch.kind == SchemaCheck.Kind.REJECT) continue;
            String predicate = branchPredicate(plan, branch, value, expression, strictFormat);
            lines.add("if (" + predicate + " && ++" + matches + " > 1) " + fail(onFailure, check.diagnostic));
        }
        lines.add("if (" + matches + " != 1) " + fail(onFailure, check.diagnostic));
    }

    /** Evaluate the if predicate once; only the selected branch is an assertion. */
    private void emitConditional(List<String> lines, SchemaFallbackEmitter.Plan plan,
                                 SchemaCheck.ConditionalCheck check, SchemaValue value,
                                 String expression, String strictFormat, String onFailure) {
        String condition = branchPredicate(plan, check.condition, value, expression, strictFormat);
        lines.add("if (" + condition + ") {");
        if (check.thenCheck != null && check.thenCheck.kind != SchemaCheck.Kind.PASS) {
            if (DIAGNOSTIC_FAILURE.equals(onFailure)) {
                List<String> direct = new ArrayList<String>();
                emitValue(direct, plan, check.thenCheck, value, expression,
                        helperPrefix + "_then_" + helperCount++, strictFormat, 0, onFailure);
                for (String line : direct) lines.add("    " + line);
            } else {
                lines.add("    if (!(" + branchPredicate(plan, check.thenCheck, value, expression,
                        strictFormat) + ")) " + onFailure);
            }
        }
        lines.add("} else {");
        if (check.elseCheck != null && check.elseCheck.kind != SchemaCheck.Kind.PASS) {
            if (DIAGNOSTIC_FAILURE.equals(onFailure)) {
                List<String> direct = new ArrayList<String>();
                emitValue(direct, plan, check.elseCheck, value, expression,
                        helperPrefix + "_else_" + helperCount++, strictFormat, 0, onFailure);
                for (String line : direct) lines.add("    " + line);
            } else {
                lines.add("    if (!(" + branchPredicate(plan, check.elseCheck, value, expression,
                        strictFormat) + ")) " + onFailure);
            }
        }
        lines.add("}");
    }

    /** allOf uses AND, anyOf short-circuits on the first success, not inverts. */
    private void emitApplicator(List<String> lines, SchemaFallbackEmitter.Plan plan,
                                SchemaCheck.ApplicatorCheck check, SchemaValue value,
                                String expression, String strictFormat, String onFailure) {
        if (check.kind == SchemaCheck.Kind.ALL_OF) {
            for (SchemaCheck branch : check.branches) {
                if (branch.kind == SchemaCheck.Kind.PASS) continue;
                if (branch.kind == SchemaCheck.Kind.REJECT) {
                    // Keep the return non-constant so following generated checks
                    // remain reachable to javac; the JIT folds this fail-fast path.
                    lines.add("if (java.lang.Boolean.TRUE) " + fail(onFailure, check.diagnostic));
                } else if (DIAGNOSTIC_FAILURE.equals(onFailure)) {
                    emitValue(lines, plan, branch, value, expression,
                            helperPrefix + "_all_" + helperCount++, strictFormat, 0, onFailure);
                } else {
                    lines.add("if (!(" + branchPredicate(plan, branch, value, expression, strictFormat)
                            + ")) " + onFailure);
                }
            }
        } else if (check.kind == SchemaCheck.Kind.NOT) {
            lines.add("if (" + branchPredicate(plan, check.branches.get(0), value,
                    expression, strictFormat) + ") " + fail(onFailure, check.diagnostic));
        } else {
            List<String> predicates = new ArrayList<String>();
            for (SchemaCheck branch : check.branches) {
                if (branch.kind == SchemaCheck.Kind.PASS) return;
                if (branch.kind != SchemaCheck.Kind.REJECT) {
                    predicates.add(branchPredicate(plan, branch, value, expression, strictFormat));
                }
            }
            if (predicates.isEmpty()) {
                lines.add(fail(onFailure, check.diagnostic));
            } else {
                lines.add("if (!(" + String.join(" || ", predicates) + ")) " + fail(onFailure, check.diagnostic));
            }
        }
    }

    /** Reusable, statically dispatched boolean predicate for nested applicators. */
    private String branchPredicate(SchemaFallbackEmitter.Plan plan, SchemaCheck branch,
                                   SchemaValue value, String expression, String strictFormat) {
        if (branch.kind == SchemaCheck.Kind.PASS) return "true";
        if (branch.kind == SchemaCheck.Kind.REJECT) return "false";
        String helper = generated.names().helperMethod("matchesSchema");
        List<String> body = new ArrayList<String>();
        // A nested boolean helper owns an independent method-level namespace.
        NameAllocator parentLocals = locals;
        locals = generated.names().helperLocals("value", "strictFormat");
        try {
            emitValue(body, plan, branch, value, "value", helper, "strictFormat", 0,
                    "return false;");
        } finally {
            locals = parentLocals;
        }
        boolean alwaysRejects = !body.isEmpty()
                && "return false;".equals(body.get(body.size() - 1));
        String parameterType = value.type == null ? "Object" : value.type.toString();
        generated.addHelper(out -> {
            out.beginBlock("private static boolean " + helper + "("
                    + parameterType + " value, boolean strictFormat)");
            for (String source : body) out.line(source);
            if (!alwaysRejects) out.line("return true;");
            out.endBlock();
        });
        return helper + "(" + expression + ", " + strictFormat + ")";
    }

    /** Avoid runtime shape detection when the Java type already fixes the JSON kind. */
    private void emitType(List<String> lines, SchemaCheck.TypeCheck check,
                          SchemaValue value, String expression, String onFailure) {
        StringBuilder matches = new StringBuilder();
        for (String type : check.types) {
            String branch;
            if ("null".equals(type)) {
                branch = value.nullable ? expression + " == null" : "false";
                if (value.shape == SchemaValue.Shape.DYNAMIC || value.shape == SchemaValue.Shape.OTHER) {
                    branch += " || (" + expression + " != null && org.sjf4j.JsonType.of("
                            + expression + ") == org.sjf4j.JsonType.NULL)";
                }
            } else {
                String name = "integer".equals(type) ? "NUMBER" : type.toUpperCase(java.util.Locale.ROOT);
                String known = value.shape == SchemaValue.Shape.STRING ? "STRING"
                        : value.shape == SchemaValue.Shape.NUMBER ? "NUMBER"
                        : value.shape == SchemaValue.Shape.BOOLEAN ? "BOOLEAN"
                        : value.shape == SchemaValue.Shape.OBJECT ? "OBJECT"
                        : value.isArrayLike() ? "ARRAY" : null;
                String shape;
                if (known != null) {
                    shape = known.equals(name) ? "true" : "false";
                } else {
                    shape = "org.sjf4j.JsonType.of(" + expression
                            + ") == org.sjf4j.JsonType." + name;
                }
                if ("integer".equals(type) && !"false".equals(shape) && !isNativeInteger(value)) {
                    shape = "(" + shape + " && org.sjf4j.util.Numbers.isSemanticInteger("
                            + (value.shape == SchemaValue.Shape.NUMBER ? expression
                               : "org.sjf4j.Nodes.toNumber(" + expression + ")") + "))";
                }
                branch = value.nullable ? "(" + expression + " != null && " + shape + ")" : shape;
            }
            if (matches.length() > 0) matches.append(" || ");
            matches.append("(").append(branch).append(")");
        }
        lines.add("if (!(" + matches + ")) " + fail(onFailure, check.diagnostic));
    }

    private void emitLiteral(List<String> lines, SchemaCheck.LiteralCheck check,
                             SchemaValue value, String expression, String onFailure) {
        if (check.values.isEmpty()) {
            lines.add(fail(onFailure, check.diagnostic));
            return;
        }
        StringBuilder disjunction = new StringBuilder();
        for (Object literal : check.values) {
            if (disjunction.length() > 0) disjunction.append(" || ");
            String expected;
            if (literal == null) expected = "null";
            else if (literal instanceof String) expected = javaLiteral((String) literal);
            else if (literal instanceof Boolean) expected = ((Boolean) literal) ? "Boolean.TRUE" : "Boolean.FALSE";
            else expected = decimalConstantRef((Number) literal);
            disjunction.append("org.sjf4j.Nodes.equals(").append(expected).append(", ")
                    .append(expression).append(")");
        }
        lines.add("if (!(" + disjunction + ")) " + fail(onFailure, check.diagnostic));
    }

    private void emitOneOf(List<String> lines, SchemaFallbackEmitter.Plan plan,
                           SchemaCheck.OneOfCheck check, SchemaValue value, String expression, String name,
                           String strictFormat, int depth, String onFailure) {
        // A mapping is only a hint. When the schema proves the discriminator
        // branches mutually exclusive, check the actual JSON-visible property
        // before executing just the matching branch; otherwise count every
        // potentially matching oneOf branch for this concrete Java type.
        // Null must participate in the generic oneOf branch count: an explicit
        // null branch can match, and multiple matching branches must still fail.
        int n = 0;
        for (SchemaCheck.OneOfCheck.Subtype subtype : check.subtypes) {
            String simpleType = subtype.type.substring(subtype.type.lastIndexOf('.') + 1);
            String typed = locals.newName(Character.toLowerCase(simpleType.charAt(0)) + simpleType.substring(1));
            lines.add((n == 0 ? "if" : "else if") + " (" + expression + " != null && "
                    + expression + ".getClass() == " + subtype.type + ".class) {");
            lines.add("    " + subtype.type + " " + typed
                    + " = (" + subtype.type + ") " + expression + ";");
            if (subtype.guardedBranch >= 0) {
                String accessor = typed + "." + subtype.discriminatorAccess;
                lines.add("    if (" + literal(subtype.discriminatorValue)
                        + ".equals(" + accessor + ")) {");
                SchemaCheck branch = subtype.check.branches.get(subtype.guardedBranch);
                String matches = branchPredicate(plan, branch, subtype.value, typed, strictFormat);
                lines.add("        if (!(" + matches + ")) " + fail(onFailure, check.diagnostic));
                lines.add("    } else {");
            }
            List<String> candidateLines = new ArrayList<String>();
            emitGenericOneOf(candidateLines, plan, subtype.check, subtype.value, typed,
                    name + "_subtype", strictFormat, onFailure);
            for (String source : candidateLines) {
                lines.add((subtype.guardedBranch >= 0 ? "        " : "    ") + source);
            }
            if (subtype.guardedBranch >= 0) lines.add("    }");
            lines.add("}");
            n++;
        }
        lines.add("else {");
        if (check.generic != null) {
            List<String> genericLines = new ArrayList<String>();
            emitGenericOneOf(genericLines, plan, check.generic, value, expression,
                    name + "_generic", strictFormat, onFailure);
            for (String source : genericLines) lines.add("    " + source);
        } else {
            SchemaFallbackEmitter.Subplan full = plan.subplan(check.uri, name);
            if (DIAGNOSTIC_FAILURE.equals(onFailure)) {
                String fallbackError = locals.newName("fallbackError");
                lines.add("    try { " + full.reference() + ".requireValid("
                        + expression + ", " + strictFormat + "); }"
                        + " catch (org.sjf4j.schema.ValidationException " + fallbackError + ") {"
                        + " throw org.sjf4j.schema.CompiledUtil.rebaseFailure(" + fallbackError + ", "
                        + instancePath + "); }");
            } else {
                lines.add("    if (!" + full.reference()
                        + ".isValid(" + expression + ", " + strictFormat + ")) " + onFailure);
            }
        }
        lines.add("}");
    }

    private void emitString(List<String> lines, SchemaCheck.StringCheck check,
                            SchemaValue value, String expression, String planName, int depth,
                            String strictFormat, String onFailure) {
        String text = expression;
        String temp;
        boolean uuidFormat = value.uuidType && "uuid".equals(check.format);
        boolean uriFormat = value.uriType
                && ("uri".equals(check.format) || "uri-reference".equals(check.format));
        boolean stringRequired = check.minLength != null || check.maxLength != null
                || check.choices != null || check.pattern != null
                || (check.format != null && !uuidFormat && !uriFormat);
        if (value.shape == SchemaValue.Shape.STRING) {
            if (value.nullable) lines.add("if (" + expression + " == null) " + fail(onFailure, check.typeDiagnostic));
            if (!"java.lang.String".equals(value.type.toString())
                    && stringRequired) {
                temp = locals.newName("text");
                lines.add("String " + temp + " = "
                        + (value.enumType ? expression + ".name()" : "String.valueOf(" + expression + ")") + ";");
                text = temp;
            }
        } else {
            lines.add("if (" + expression + " == null || org.sjf4j.JsonType.rawOf("
                    + expression + ".getClass()) != org.sjf4j.JsonType.STRING) " + fail(onFailure, check.typeDiagnostic));
            temp = locals.newName("text");
            lines.add("String " + temp + " = org.sjf4j.Nodes.toString(" + expression + ");");
            text = temp;
        }
        if (check.minLength != null || check.maxLength != null) {
            String length = locals.newName("length");
            lines.add("int " + length + " = " + text
                    + ".codePointCount(0, " + text + ".length());");
            if (check.minLength != null) lines.add("if (" + length + " < " + check.minLength + ") " + fail(onFailure, check.minLengthDiagnostic));
            if (check.maxLength != null) lines.add("if (" + length + " > " + check.maxLength + ") " + fail(onFailure, check.maxLengthDiagnostic));
        }
        if (check.pattern != null) {
            String holder = generated.names().field("PATTERN");
            generated.addHelper(out -> out.line("private static final java.util.regex.Pattern "
                    + holder + " = org.sjf4j.schema.SchemaUtil.compileRegexPattern("
                    + literal(check.pattern) + ", \"pattern\", null, null);"));
            lines.add("if (!" + holder + ".matcher(" + text + ").find()) " + fail(onFailure, check.patternDiagnostic));
        }
        if (check.format != null && !"false".equals(strictFormat)) {
            if (uuidFormat) {
                // UUID's standard Java representation always satisfies the uuid format.
            } else if (uriFormat) {
                lines.add("if (" + strictFormat
                        + " && !org.sjf4j.schema.FormatValidator.validateUri(" + expression
                        + ", " + "uri".equals(check.format) + ")) "
                        + fail(onFailure, check.formatDiagnostic));
            } else {
                lines.add("if (" + strictFormat + " && !org.sjf4j.schema.FormatValidator.of("
                        + literal(check.format) + ").validate(" + text + ")) "
                        + fail(onFailure, check.formatDiagnostic));
            }
        }
        if (check.choices != null) {
            StringBuilder condition = new StringBuilder();
            for (String choice : check.choices) {
                if (condition.length() != 0) condition.append(" && ");
                condition.append("!").append(text).append(".equals(")
                        .append(literal(choice)).append(")");
            }
            if (condition.length() == 0) lines.add(fail(onFailure, check.enumDiagnostic));
            else lines.add("if (" + condition + ") " + fail(onFailure, check.enumDiagnostic));
        }
    }

    private void emitNumber(List<String> lines, SchemaCheck.NumberCheck check,
                            SchemaValue value, String expression, String planName, int depth, String onFailure) {
        String number = expression;
        if (value.shape == SchemaValue.Shape.NUMBER) {
            if (value.nullable) lines.add("if (" + expression + " == null) " + fail(onFailure, check.typeDiagnostic));
        } else {
            lines.add("if (" + expression + " == null || org.sjf4j.JsonType.rawOf("
                    + expression + ".getClass()) != org.sjf4j.JsonType.NUMBER) " + fail(onFailure, check.typeDiagnostic));
            number = locals.newName("number");
            lines.add("Number " + number + " = org.sjf4j.Nodes.toNumber(" + expression + ");");
        }
        boolean nativeInteger = isNativeInteger(value);
        if (check.integer && !nativeInteger) {
            lines.add("if (!org.sjf4j.util.Numbers.isSemanticInteger(" + number + ")) " + fail(onFailure, check.typeDiagnostic));
        }
        for (SchemaCheck.NumericBound bound : check.bounds) {
            String operator = bound.keyword.equals("minimum") ? " <"
                    : bound.keyword.equals("maximum") ? " >"
                    : bound.keyword.equals("exclusiveMinimum") ? " <=" : " >=";
            Long exactLimit = nativeInteger ? exactLong(bound.limit) : null;
            if (exactLimit != null) {
                // int/long/byte/short and their wrappers compare without boxing.
                lines.add("if (" + number + operator + " " + exactLimit + "L) " + fail(onFailure, bound.diagnostic));
            } else {
                String comparison = "org.sjf4j.util.Numbers.compare(" + number + ", "
                        + decimalConstantRef(bound.limit) + ")";
                lines.add("if (" + comparison + operator + " 0) " + fail(onFailure, bound.diagnostic));
            }
        }
        if (check.multipleOf != null) {
            Long exactDivisor = nativeInteger ? exactLong(check.multipleOf) : null;
            if (exactDivisor != null && exactDivisor > 0L) {
                if (exactDivisor > 1L) {
                    lines.add("if (" + number + " % " + exactDivisor + "L != 0) " + fail(onFailure, check.multipleOfDiagnostic));
                }
            } else {
                lines.add("if (org.sjf4j.util.Numbers.normalizeDecimal(" + number
                        + ").remainder(" + decimalConstantRef(check.multipleOf)
                        + ").signum() != 0) " + fail(onFailure, check.multipleOfDiagnostic));
            }
        }
    }

    /** Only Java integral primitives and wrappers are safe for direct long arithmetic. */
    private static boolean isNativeInteger(SchemaValue value) {
        if (value.shape != SchemaValue.Shape.NUMBER || value.type == null) return false;
        switch (value.type.toString()) {
            case "byte": case "short": case "int": case "long":
            case "java.lang.Byte": case "java.lang.Short":
            case "java.lang.Integer": case "java.lang.Long":
                return true;
            default:
                return false;
        }
    }

    /** Return null when a fractional or out-of-range constant needs decimal arithmetic. */
    private static Long exactLong(Number number) {
        try {
            return org.sjf4j.util.Numbers.normalizeDecimal(number).longValueExact();
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    /** Reuse decimal constants across checks in one generated validator method. */
    private String decimalConstantRef(Number number) {
        String expression = decimalConstant(number);
        if (expression.equals("java.math.BigDecimal.ZERO")
                || expression.equals("java.math.BigDecimal.ONE")) return expression;
        String key = number.toString();
        String cached = decimalConstants.get(key);
        if (cached != null) return cached;
        String field = generated.names().field("DECIMAL");
        decimalConstants.put(key, field);
        generated.addHelper(out -> out.line("private static final java.math.BigDecimal "
                + field + " = " + expression + ";"));
        return field;
    }

    private void emitArray(List<String> lines, SchemaFallbackEmitter.Plan plan,
                           SchemaCheck.ArrayCheck check, SchemaValue value, String expression,
                           String planName, String strictFormat, int depth,
                            String onFailure) {
        boolean knownCollection = value.shape == SchemaValue.Shape.COLLECTION;
        boolean knownArray = value.shape == SchemaValue.Shape.ARRAY;
        String subject = expression;
        if (value.shape == SchemaValue.Shape.DYNAMIC) {
            lines.add("if (org.sjf4j.JsonType.of(" + subject
                    + ") != org.sjf4j.JsonType.ARRAY) " + fail(onFailure, check.typeDiagnostic));
        } else if (value.nullable) {
            lines.add("if (" + subject + " == null) " + fail(onFailure, check.typeDiagnostic));
        }
        String size = knownCollection ? subject + ".size()"
                : knownArray ? subject + ".length"
                : "org.sjf4j.Nodes.sizeInArray(" + subject + ")";
        if (check.minItems != null) lines.add("if (" + size + " < " + check.minItems + ") " + fail(onFailure, check.minItemsDiagnostic));
        if (check.maxItems != null) lines.add("if (" + size + " > " + check.maxItems + ") " + fail(onFailure, check.maxItemsDiagnostic));
        if (check.items == null) return;

        boolean withPath = DIAGNOSTIC_FAILURE.equals(onFailure);
        String indexVar = withPath ? locals.newName("index") : null;
        if (withPath) lines.add("int " + indexVar + " = 0;");
        String itemVar = locals.newName("item");
        TypeMirror itemType = value.itemType();
        if (knownCollection || knownArray) {
            String itemJavaType = itemType == null || itemType.getKind() == TypeKind.WILDCARD
                    ? "Object" : itemType.toString();
            lines.add("for (" + itemJavaType + " " + itemVar + " : " + subject + ") {");
        } else {
            String iterator = locals.newName("iterator");
            lines.add("java.util.Iterator<Object> " + iterator
                    + " = org.sjf4j.Nodes.iteratorInArray(" + subject + ");");
            lines.add("while (" + iterator + ".hasNext()) {");
            lines.add("    Object " + itemVar + " = " + iterator + ".next();");
        }
        List<String> childLines = new ArrayList<String>();
        String parentPath = instancePath;
        if (withPath) instancePath = "new org.sjf4j.path.PathSegment.Index("
                + parentPath + ", " + indexVar + ")";
        emitValue(childLines, plan, check.items, check.itemValue, itemVar,
                planName + "_ITEM", strictFormat, depth + 1, onFailure);
        instancePath = parentPath;
        for (String line : childLines) lines.add("    " + line);
        if (withPath) lines.add("    " + indexVar + "++;");
        lines.add("}");
    }

    private static String decimalConstant(Number number) {
        String decimal = number.toString();
        if ("0".equals(decimal) || "0.0".equals(decimal)) return "java.math.BigDecimal.ZERO";
        if ("1".equals(decimal) || "1.0".equals(decimal)) return "java.math.BigDecimal.ONE";
        return "new java.math.BigDecimal(" + literal(decimal) + ")";
    }

    private static String literal(String text) {
        return javaLiteral(text);
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


}

