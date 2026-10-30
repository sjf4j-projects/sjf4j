package org.sjf4j.processor.schema;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

/**
 * Emits the existing native fail-fast predicates from structured SchemaCheck
 * trees. Fallback helper-class emission remains in SchemaMethodGenerator.
 */
final class SchemaEmitter {
    static final class Subplan {
        final String fieldName;
        final String uri;

        Subplan(String fieldName, String uri) {
            this.fieldName = fieldName;
            this.uri = uri;
        }
    }

    static final class Result {
        final List<String> statements = new ArrayList<String>();
        final List<Subplan> subplans = new ArrayList<Subplan>();
    }

    Result emit(SchemaCheck.ObjectCheck check, String variable, String planClass,
                String strictFormat) {
        Result result = new Result();
        if (check.dynamic) {
            for (SchemaCheck.Required required : check.required) {
                if (check.nullAsMissing) {
                    result.statements.add("if (org.sjf4j.Nodes.getInObject(" + variable + ", "
                            + literal(required.name) + ") == null) return false;");
                } else {
                    result.statements.add("if (!org.sjf4j.Nodes.keySetInObject(" + variable
                            + ").contains(" + literal(required.name) + ")) return false;");
                }
            }
            if (check.additionalPropertiesFalse) {
                result.statements.add("for (String _key : org.sjf4j.Nodes.keySetInObject(" + variable + ")) {");
                StringBuilder condition = new StringBuilder();
                for (String allowed : check.allowedNames) {
                    if (condition.length() != 0) condition.append(" && ");
                    condition.append("!_key.equals(").append(literal(allowed)).append(")");
                }
                String unknown = condition.length() == 0 ? "true" : condition.toString();
                if (check.nullAsMissing) {
                    unknown += " && org.sjf4j.Nodes.getInObject(" + variable + ", _key) != null";
                }
                result.statements.add("    if (" + unknown + ") return false;");
                result.statements.add("}");
            }
        } else if (check.nullAsMissing) {
            for (SchemaCheck.Required required : check.required) {
                result.statements.add("if (" + variable + "." + required.staticAccess + " == null) return false;");
            }
        }

        int index = 0;
        for (SchemaCheck.PropertyCheck property : check.properties) {
            String local = "_property" + index;
            String access = check.dynamic
                    ? "org.sjf4j.Nodes.getInObject(" + variable + ", " + literal(property.name) + ")"
                    : variable + "." + property.staticAccess;
            result.statements.add(property.value.localType() + " " + local + " = " + access + ";");
            if (check.dynamic) {
                result.statements.add("if (org.sjf4j.Nodes.keySetInObject(" + variable
                        + ").contains(" + literal(property.name) + ")) {");
            }
            if (check.nullAsMissing && property.value.nullable) {
                result.statements.add("if (" + local + " != null) {");
            }
            List<String> lines = new ArrayList<String>();
            emitValue(lines, result.subplans, property.check, property.value, local,
                    planClass, "SUB_" + index, strictFormat, 0);
            for (String line : lines) {
                result.statements.add((check.dynamic || check.nullAsMissing ? "    " : "") + line);
            }
            if (check.nullAsMissing && property.value.nullable) result.statements.add("}");
            if (check.dynamic) result.statements.add("}");
            index++;
        }
        return result;
    }

    private void emitValue(List<String> lines, List<Subplan> subplans,
                           SchemaCheck check, SchemaValue value, String expression,
                           String planClass, String planName, String strictFormat, int depth) {
        switch (check.kind) {
            case PASS:
                return;
            case FALLBACK:
                Subplan sub = new Subplan(planName, ((SchemaCheck.Fallback) check).uri);
                subplans.add(sub);
                lines.add("if (!" + planClass + "." + sub.fieldName + ".isValid("
                        + expression + ", " + strictFormat + ")) return false;");
                return;
            case STRING:
                emitString(lines, (SchemaCheck.StringCheck) check, value, expression, planName, depth);
                return;
            case NUMBER:
                emitNumber(lines, (SchemaCheck.NumberCheck) check, value, expression, planName, depth);
                return;
            case ARRAY:
                emitArray(lines, subplans, (SchemaCheck.ArrayCheck) check, value, expression,
                        planClass, planName, strictFormat, depth);
                return;
            default:
                throw new IllegalStateException("unsupported compiled value kind: " + check.kind);
        }
    }

    private void emitString(List<String> lines, SchemaCheck.StringCheck check,
                            SchemaValue value, String expression, String planName, int depth) {
        String text = expression;
        String temp = "_text" + depth + "_" + planName;
        if (value.shape == SchemaValue.Shape.STRING) {
            if (value.nullable) lines.add("if (" + expression + " == null) return false;");
            if (!"java.lang.String".equals(value.type.toString())) {
                lines.add("String " + temp + " = "
                        + (value.enumType ? expression + ".name()" : "String.valueOf(" + expression + ")") + ";");
                text = temp;
            }
        } else {
            lines.add("if (" + expression + " == null || org.sjf4j.JsonType.rawOf("
                    + expression + ".getClass()) != org.sjf4j.JsonType.STRING) return false;");
            lines.add("String " + temp + " = org.sjf4j.Nodes.toString(" + expression + ");");
            text = temp;
        }
        if (check.minLength != null || check.maxLength != null) {
            String length = "_length" + depth + "_" + planName;
            lines.add("int " + length + " = " + text
                    + ".codePointCount(0, " + text + ".length());");
            if (check.minLength != null) lines.add("if (" + length + " < " + check.minLength + ") return false;");
            if (check.maxLength != null) lines.add("if (" + length + " > " + check.maxLength + ") return false;");
        }
        if (check.choices != null) {
            StringBuilder condition = new StringBuilder();
            for (String choice : check.choices) {
                if (condition.length() != 0) condition.append(" && ");
                condition.append("!").append(text).append(".equals(")
                        .append(literal(choice)).append(")");
            }
            if (condition.length() == 0) lines.add("return false;");
            else lines.add("if (" + condition + ") return false;");
        }
    }

    private void emitNumber(List<String> lines, SchemaCheck.NumberCheck check,
                            SchemaValue value, String expression, String planName, int depth) {
        String number = expression;
        if (value.shape == SchemaValue.Shape.NUMBER) {
            if (value.nullable) lines.add("if (" + expression + " == null) return false;");
        } else {
            lines.add("if (" + expression + " == null || org.sjf4j.JsonType.rawOf("
                    + expression + ".getClass()) != org.sjf4j.JsonType.NUMBER) return false;");
            number = "_number" + depth + "_" + planName;
            lines.add("Number " + number + " = org.sjf4j.Nodes.toNumber(" + expression + ");");
        }
        if (check.integer) {
            lines.add("if (!org.sjf4j.util.Numbers.isSemanticInteger(" + number + ")) return false;");
        }
        for (SchemaCheck.NumericBound bound : check.bounds) {
            String comparison = "org.sjf4j.util.Numbers.compare(" + number + ", "
                    + decimalConstant(bound.limit) + ")";
            String operator = bound.keyword.equals("minimum") ? " < 0"
                    : bound.keyword.equals("maximum") ? " > 0"
                    : bound.keyword.equals("exclusiveMinimum") ? " <= 0" : " >= 0";
            lines.add("if (" + comparison + operator + ") return false;");
        }
    }

    private void emitArray(List<String> lines, List<Subplan> subplans,
                           SchemaCheck.ArrayCheck check, SchemaValue value, String expression,
                           String planClass, String planName, String strictFormat, int depth) {
        boolean knownCollection = value.shape == SchemaValue.Shape.COLLECTION;
        boolean knownArray = value.shape == SchemaValue.Shape.ARRAY;
        if (value.nullable) {
            if (value.shape == SchemaValue.Shape.DYNAMIC) {
                lines.add("if (" + expression + " == null || org.sjf4j.JsonType.rawOf("
                        + expression + ".getClass()) != org.sjf4j.JsonType.ARRAY) return false;");
            } else {
                lines.add("if (" + expression + " == null) return false;");
            }
        }
        String size = knownCollection ? expression + ".size()"
                : knownArray ? expression + ".length"
                : "org.sjf4j.Nodes.sizeInArray(" + expression + ")";
        if (check.minItems != null) lines.add("if (" + size + " < " + check.minItems + ") return false;");
        if (check.maxItems != null) lines.add("if (" + size + " > " + check.maxItems + ") return false;");
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
        emitValue(childLines, subplans, check.items, check.itemValue, itemVar,
                planClass, planName + "_ITEM", strictFormat, depth + 1);
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
        return SchemaMethodGenerator.javaLiteral(text);
    }
}
