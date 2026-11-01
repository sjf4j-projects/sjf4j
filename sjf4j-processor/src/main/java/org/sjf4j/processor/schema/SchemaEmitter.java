package org.sjf4j.processor.schema;

import org.sjf4j.processor.code.GeneratedClass;

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
    private final GeneratedClass generated;
    private final String helperPrefix;
    private final Map<String, String> decimalConstants = new LinkedHashMap<String, String>();
    private int helperCount;

    SchemaEmitter(GeneratedClass generated, String helperPrefix) {
        this.generated = generated;
        this.helperPrefix = helperPrefix;
    }

    List<String> emitRoot(SchemaCheck check, SchemaValue value, String variable,
                          SchemaFallbackEmitter.Plan plan, String strictFormat,
                          String onFailure) {
        // A dynamic root can contain a non-object even when its schema has
        // object-only keywords. Keep the fast path for known object types, but
        // emit the shape/applicability guard for Object-typed roots.
        if (check.kind == SchemaCheck.Kind.OBJECT
                && value.shape != SchemaValue.Shape.DYNAMIC) {
            return emit((SchemaCheck.ObjectCheck) check, variable, plan, strictFormat, onFailure);
        }
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
            String count = check.dynamic
                    ? "org.sjf4j.node.NodeWalker.sizeInObject(" + variable + ")"
                    : Integer.toString(check.staticPropertyCount);
            if (check.minProperties != null) {
                statements.add("if (" + count + " < " + check.minProperties + ") " + onFailure);
            }
            if (check.maxProperties != null) {
                statements.add("if (" + count + " > " + check.maxProperties + ") " + onFailure);
            }
        }
        if (check.dynamic) {
            for (SchemaCheck.Required required : check.required) {
                statements.add("if (!org.sjf4j.Nodes.keySetInObject(" + variable
                        + ").contains(" + literal(required.name) + ")) " + onFailure);
            }
            if (!check.dependentRequired.isEmpty()) {
                String keys = "_keys_" + helperPrefix + "_" + helperCount++;
                statements.add("java.util.Set<String> " + keys
                        + " = org.sjf4j.Nodes.keySetInObject(" + variable + ");");
                for (SchemaCheck.DependentRequired dependency : check.dependentRequired) {
                    StringBuilder condition = new StringBuilder();
                    for (String name : dependency.dependencies) {
                        if (condition.length() != 0) condition.append(" || ");
                        condition.append("!").append(keys).append(".contains(")
                                .append(literal(name)).append(")");
                    }
                    statements.add("if (" + keys + ".contains(" + literal(dependency.trigger)
                            + ") && (" + condition + ")) " + onFailure);
                }
            }
            if (check.additionalPropertiesFalse) {
                statements.add("for (String _key : org.sjf4j.Nodes.keySetInObject(" + variable + ")) {");
                StringBuilder condition = new StringBuilder();
                for (String allowed : check.allowedNames) {
                    if (condition.length() != 0) condition.append(" && ");
                    condition.append("!_key.equals(").append(literal(allowed)).append(")");
                }
                String unknown = condition.length() == 0 ? "true" : condition.toString();
                statements.add("    if (" + unknown + ") " + onFailure);
                statements.add("}");
            }
        }

        int index = 0;
        for (SchemaCheck.PropertyCheck property : check.properties) {
            String local = prefix + "_property" + index;
            String access = check.dynamic
                    ? "org.sjf4j.Nodes.getInObject(" + variable + ", " + literal(property.name) + ")"
                    : variable + "." + property.staticAccess;
            statements.add(property.value.localType() + " " + local + " = " + access + ";");
            if (check.dynamic) {
                statements.add("if (org.sjf4j.Nodes.keySetInObject(" + variable
                        + ").contains(" + literal(property.name) + ")) {");
            }
            List<String> lines = new ArrayList<String>();
            emitValue(lines, plan, property.check, property.value, local,
                    prefix + "SUB_" + index, strictFormat, 0, onFailure);
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
            case FALLBACK:
                SchemaFallbackEmitter.Subplan sub = plan.subplan(
                        ((SchemaCheck.Fallback) check).uri, planName);
                lines.add("if (!" + sub.reference() + ".isValid("
                        + expression + ", " + strictFormat + ")) " + onFailure);
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
                emitOneOf(lines, plan, (SchemaCheck.OneOfCheck) check, expression,
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
                boolean unknownShape = object.dynamic && value.shape == SchemaValue.Shape.DYNAMIC;
                if (object.typeRequired) {
                    if (value.nullable) lines.add("if (" + expression + " == null) " + onFailure);
                    if (unknownShape) {
                        lines.add("if (org.sjf4j.JsonType.rawOf(" + expression
                                + ".getClass()) != org.sjf4j.JsonType.OBJECT) " + onFailure);
                    }
                    lines.addAll(emit(object, expression, plan, strictFormat, planName + "_", onFailure));
                } else if (unknownShape || value.nullable) {
                    String applies = unknownShape
                            ? expression + " != null && org.sjf4j.JsonType.rawOf("
                                    + expression + ".getClass()) == org.sjf4j.JsonType.OBJECT"
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
                lines.add("if (java.lang.Boolean.TRUE) " + onFailure);
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
        String matches = "_matches_" + planName + "_" + helperCount++;
        lines.add("int " + matches + " = 0;");
        for (SchemaCheck branch : check.branches) {
            if (branch.kind == SchemaCheck.Kind.REJECT) continue;
            String predicate = branchPredicate(plan, branch, value, expression, strictFormat);
            lines.add("if (" + predicate + " && ++" + matches + " > 1) " + onFailure);
        }
        lines.add("if (" + matches + " != 1) " + onFailure);
    }

    /** Evaluate the if predicate once; only the selected branch is an assertion. */
    private void emitConditional(List<String> lines, SchemaFallbackEmitter.Plan plan,
                                 SchemaCheck.ConditionalCheck check, SchemaValue value,
                                 String expression, String strictFormat, String onFailure) {
        String condition = branchPredicate(plan, check.condition, value, expression, strictFormat);
        lines.add("if (" + condition + ") {");
        if (check.thenCheck != null && check.thenCheck.kind != SchemaCheck.Kind.PASS) {
            lines.add("    if (!(" + branchPredicate(plan, check.thenCheck, value, expression,
                    strictFormat) + ")) " + onFailure);
        }
        lines.add("} else {");
        if (check.elseCheck != null && check.elseCheck.kind != SchemaCheck.Kind.PASS) {
            lines.add("    if (!(" + branchPredicate(plan, check.elseCheck, value, expression,
                    strictFormat) + ")) " + onFailure);
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
                    lines.add("if (java.lang.Boolean.TRUE) " + onFailure);
                } else {
                    lines.add("if (!(" + branchPredicate(plan, branch, value, expression, strictFormat)
                            + ")) " + onFailure);
                }
            }
        } else if (check.kind == SchemaCheck.Kind.NOT) {
            lines.add("if (" + branchPredicate(plan, check.branches.get(0), value,
                    expression, strictFormat) + ") " + onFailure);
        } else {
            List<String> predicates = new ArrayList<String>();
            for (SchemaCheck branch : check.branches) {
                if (branch.kind == SchemaCheck.Kind.PASS) return;
                if (branch.kind != SchemaCheck.Kind.REJECT) {
                    predicates.add(branchPredicate(plan, branch, value, expression, strictFormat));
                }
            }
            if (predicates.isEmpty()) {
                lines.add(onFailure);
            } else {
                lines.add("if (!(" + String.join(" || ", predicates) + ")) " + onFailure);
            }
        }
    }

    /** Reusable, statically dispatched boolean predicate for nested applicators. */
    private String branchPredicate(SchemaFallbackEmitter.Plan plan, SchemaCheck branch,
                                   SchemaValue value, String expression, String strictFormat) {
        if (branch.kind == SchemaCheck.Kind.PASS) return "true";
        if (branch.kind == SchemaCheck.Kind.REJECT) return "false";
        String helper = "_schema_" + helperPrefix + "_" + helperCount++;
        List<String> body = new ArrayList<String>();
        // Nested boolean branch helpers must retain boolean return semantics.
        emitValue(body, plan, branch, value, "_value", helper, "_strictFormat", 0,
                "return false;");
        boolean alwaysRejects = !body.isEmpty()
                && "return false;".equals(body.get(body.size() - 1));
        String parameterType = value.type == null ? "Object" : value.type.toString();
        generated.addHelper(out -> {
            out.beginBlock("private static boolean " + helper + "("
                    + parameterType + " _value, boolean _strictFormat)");
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
                    branch += " || (" + expression + " != null && org.sjf4j.JsonType.rawOf("
                            + expression + ".getClass()) == org.sjf4j.JsonType.NULL)";
                }
            } else {
                String name = "integer".equals(type) ? "NUMBER" : type.toUpperCase(java.util.Locale.ROOT);
                String known = value.shape == SchemaValue.Shape.STRING ? "STRING"
                        : value.shape == SchemaValue.Shape.NUMBER ? "NUMBER"
                        : value.shape == SchemaValue.Shape.BOOLEAN ? "BOOLEAN"
                        : value.isArrayLike() ? "ARRAY" : null;
                String shape;
                if (known != null) {
                    shape = known.equals(name) ? "true" : "false";
                } else {
                    shape = "org.sjf4j.JsonType.rawOf(" + expression
                            + ".getClass()) == org.sjf4j.JsonType." + name;
                }
                if ("integer".equals(type) && !"false".equals(shape)) {
                    shape = "(" + shape + " && org.sjf4j.util.Numbers.isSemanticInteger("
                            + (value.shape == SchemaValue.Shape.NUMBER ? expression
                               : "org.sjf4j.Nodes.toNumber(" + expression + ")") + "))";
                }
                branch = value.nullable ? "(" + expression + " != null && " + shape + ")" : shape;
            }
            if (matches.length() > 0) matches.append(" || ");
            matches.append("(").append(branch).append(")");
        }
        lines.add("if (!(" + matches + ")) " + onFailure);
    }

    private void emitLiteral(List<String> lines, SchemaCheck.LiteralCheck check,
                             SchemaValue value, String expression, String onFailure) {
        if (check.values.isEmpty()) {
            lines.add(onFailure);
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
        lines.add("if (!(" + disjunction + ")) " + onFailure);
    }

    private void emitOneOf(List<String> lines, SchemaFallbackEmitter.Plan plan,
                           SchemaCheck.OneOfCheck check, String expression, String name,
                           String strictFormat, int depth, String onFailure) {
        // A subtype's compiled checks already handle strictFormat, including
        // format assertions. Only unknown runtime classes need the full plan.
        SchemaFallbackEmitter.Subplan full = plan.subplan(check.uri, name);
        lines.add("if (" + expression + " == null) " + onFailure);
        int n = 0;
        for (SchemaCheck.OneOfCheck.Subtype subtype : check.subtypes) {
            String typed = "_typed" + depth + "_" + name + "_" + n;
            lines.add((n == 0 ? "if" : "else if") + " ("
                    + expression + ".getClass() == " + subtype.type + ".class) {");
            lines.add("    " + subtype.type + " " + typed
                    + " = (" + subtype.type + ") " + expression + ";");
            for (String source : emit(subtype.check, typed, plan, strictFormat, typed + "_", onFailure)) {
                lines.add("    " + source);
            }
            lines.add("}");
            n++;
        }
        lines.add("else {");
        lines.add("    if (!" + full.reference()
                + ".isValid(" + expression + ", " + strictFormat + ")) " + onFailure);
        lines.add("}");
    }

    private void emitString(List<String> lines, SchemaCheck.StringCheck check,
                            SchemaValue value, String expression, String planName, int depth,
                            String strictFormat, String onFailure) {
        String text = expression;
        String temp = "_text" + depth + "_" + planName;
        if (value.shape == SchemaValue.Shape.STRING) {
            if (value.nullable) lines.add("if (" + expression + " == null) " + onFailure);
            if (!"java.lang.String".equals(value.type.toString())
                    && (check.minLength != null || check.maxLength != null
                        || check.choices != null || check.format != null
                        || check.pattern != null)) {
                lines.add("String " + temp + " = "
                        + (value.enumType ? expression + ".name()" : "String.valueOf(" + expression + ")") + ";");
                text = temp;
            }
        } else {
            lines.add("if (" + expression + " == null || org.sjf4j.JsonType.rawOf("
                    + expression + ".getClass()) != org.sjf4j.JsonType.STRING) " + onFailure);
            lines.add("String " + temp + " = org.sjf4j.Nodes.toString(" + expression + ");");
            text = temp;
        }
        if (check.minLength != null || check.maxLength != null) {
            String length = "_length" + depth + "_" + planName;
            lines.add("int " + length + " = " + text
                    + ".codePointCount(0, " + text + ".length());");
            if (check.minLength != null) lines.add("if (" + length + " < " + check.minLength + ") " + onFailure);
            if (check.maxLength != null) lines.add("if (" + length + " > " + check.maxLength + ") " + onFailure);
        }
        if (check.pattern != null) {
            String holder = "_pattern_" + helperPrefix + "_" + helperCount++;
            generated.addHelper(out -> out.line("private static final java.util.regex.Pattern "
                    + holder + " = org.sjf4j.schema.SchemaUtil.compileRegexPattern("
                    + literal(check.pattern) + ", \"pattern\", null, null);"));
            lines.add("if (!" + holder + ".matcher(" + text + ").find()) " + onFailure);
        }
        if (check.format != null && !"false".equals(strictFormat)) {
            lines.add("if (" + strictFormat + " && !org.sjf4j.schema.FormatValidator.of("
                    + literal(check.format) + ").validate(" + text + ")) " + onFailure);
        }
        if (check.choices != null) {
            StringBuilder condition = new StringBuilder();
            for (String choice : check.choices) {
                if (condition.length() != 0) condition.append(" && ");
                condition.append("!").append(text).append(".equals(")
                        .append(literal(choice)).append(")");
            }
            if (condition.length() == 0) lines.add(onFailure);
            else lines.add("if (" + condition + ") " + onFailure);
        }
    }

    private void emitNumber(List<String> lines, SchemaCheck.NumberCheck check,
                            SchemaValue value, String expression, String planName, int depth, String onFailure) {
        String number = expression;
        if (value.shape == SchemaValue.Shape.NUMBER) {
            if (value.nullable) lines.add("if (" + expression + " == null) " + onFailure);
        } else {
            lines.add("if (" + expression + " == null || org.sjf4j.JsonType.rawOf("
                    + expression + ".getClass()) != org.sjf4j.JsonType.NUMBER) " + onFailure);
            number = "_number" + depth + "_" + planName;
            lines.add("Number " + number + " = org.sjf4j.Nodes.toNumber(" + expression + ");");
        }
        boolean nativeInteger = isNativeInteger(value);
        if (check.integer && !nativeInteger) {
            lines.add("if (!org.sjf4j.util.Numbers.isSemanticInteger(" + number + ")) " + onFailure);
        }
        for (SchemaCheck.NumericBound bound : check.bounds) {
            String operator = bound.keyword.equals("minimum") ? " <"
                    : bound.keyword.equals("maximum") ? " >"
                    : bound.keyword.equals("exclusiveMinimum") ? " <=" : " >=";
            Long exactLimit = nativeInteger ? exactLong(bound.limit) : null;
            if (exactLimit != null) {
                // int/long/byte/short and their wrappers compare without boxing.
                lines.add("if (" + number + operator + " " + exactLimit + "L) " + onFailure);
            } else {
                String comparison = "org.sjf4j.util.Numbers.compare(" + number + ", "
                        + decimalConstantRef(bound.limit) + ")";
                lines.add("if (" + comparison + operator + " 0) " + onFailure);
            }
        }
        if (check.multipleOf != null) {
            Long exactDivisor = nativeInteger ? exactLong(check.multipleOf) : null;
            if (exactDivisor != null && exactDivisor > 0L) {
                if (exactDivisor > 1L) {
                    lines.add("if (" + number + " % " + exactDivisor + "L != 0) " + onFailure);
                }
            } else {
                lines.add("if (org.sjf4j.util.Numbers.normalizeDecimal(" + number
                        + ").remainder(" + decimalConstantRef(check.multipleOf)
                        + ").signum() != 0) " + onFailure);
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
        String field = "_decimal_" + helperPrefix + "_" + helperCount++;
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
        if (value.nullable) {
            if (value.shape == SchemaValue.Shape.DYNAMIC) {
                lines.add("if (" + expression + " == null || org.sjf4j.JsonType.rawOf("
                        + expression + ".getClass()) != org.sjf4j.JsonType.ARRAY) " + onFailure);
            } else {
                lines.add("if (" + expression + " == null) " + onFailure);
            }
        }
        String size = knownCollection ? expression + ".size()"
                : knownArray ? expression + ".length"
                : "org.sjf4j.Nodes.sizeInArray(" + expression + ")";
        if (check.minItems != null) lines.add("if (" + size + " < " + check.minItems + ") " + onFailure);
        if (check.maxItems != null) lines.add("if (" + size + " > " + check.maxItems + ") " + onFailure);
        if (check.items == null) return;

        String itemVar = "_item" + depth + "_" + planName;
        TypeMirror itemType = value.itemType();
        if (knownCollection || knownArray) {
            String itemJavaType = itemType == null || itemType.getKind() == TypeKind.WILDCARD
                    ? "Object" : itemType.toString();
            lines.add("for (" + itemJavaType + " " + itemVar + " : " + expression + ") {");
        } else {
            String iterator = "_iter" + depth + "_" + planName;
            lines.add("java.util.Iterator<Object> " + iterator
                    + " = org.sjf4j.Nodes.iteratorInArray(" + expression + ");");
            lines.add("while (" + iterator + ".hasNext()) {");
            lines.add("    Object " + itemVar + " = " + iterator + ".next();");
        }
        List<String> childLines = new ArrayList<String>();
        emitValue(childLines, plan, check.items, check.itemValue, itemVar,
                planName + "_ITEM", strictFormat, depth + 1, onFailure);
        for (String line : childLines) lines.add("    " + line);
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

