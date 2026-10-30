package org.sjf4j.processor.schema;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

/**
 * Emits the existing native fail-fast predicates from structured SchemaCheck
 * trees. Fallback references are allocated by SchemaFallbackEmitter.
 */
final class SchemaEmitter {
    List<String> emit(SchemaCheck.ObjectCheck check, String variable, SchemaFallbackEmitter.Plan plan,
                      String strictFormat) {
        return emit(check, variable, plan, strictFormat, "");
    }

    private List<String> emit(SchemaCheck.ObjectCheck check, String variable,
                              SchemaFallbackEmitter.Plan plan, String strictFormat, String prefix) {
        List<String> statements = new ArrayList<String>();
        if (check.dynamic) {
            for (SchemaCheck.Required required : check.required) {
                if (check.nullAsMissing) {
                    statements.add("if (org.sjf4j.Nodes.getInObject(" + variable + ", "
                            + literal(required.name) + ") == null) return false;");
                } else {
                    statements.add("if (!org.sjf4j.Nodes.keySetInObject(" + variable
                            + ").contains(" + literal(required.name) + ")) return false;");
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
                if (check.nullAsMissing) {
                    unknown += " && org.sjf4j.Nodes.getInObject(" + variable + ", _key) != null";
                }
                statements.add("    if (" + unknown + ") return false;");
                statements.add("}");
            }
        } else if (check.nullAsMissing) {
            for (SchemaCheck.Required required : check.required) {
                if (required.staticAccess != null) {
                    statements.add("if (" + variable + "." + required.staticAccess + " == null) return false;");
                }
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
            if (check.nullAsMissing && property.value.nullable) {
                statements.add("if (" + local + " != null) {");
            }
            List<String> lines = new ArrayList<String>();
            emitValue(lines, plan, property.check, property.value, local,
                    prefix + "SUB_" + index, strictFormat, 0);
            for (String line : lines) {
                statements.add((check.dynamic || check.nullAsMissing ? "    " : "") + line);
            }
            if (check.nullAsMissing && property.value.nullable) statements.add("}");
            if (check.dynamic) statements.add("}");
            index++;
        }
        return statements;
    }

    private void emitValue(List<String> lines, SchemaFallbackEmitter.Plan plan,
                           SchemaCheck check, SchemaValue value, String expression,
                           String planName, String strictFormat, int depth) {
        switch (check.kind) {
            case PASS:
                return;
            case FALLBACK:
                SchemaFallbackEmitter.Subplan sub = plan.subplan(
                        ((SchemaCheck.Fallback) check).uri, planName);
                lines.add("if (!" + sub.reference() + ".isValid("
                        + expression + ", " + strictFormat + ")) return false;");
                return;
            case STRING:
                emitString(lines, (SchemaCheck.StringCheck) check, value, expression, planName, depth,
                        strictFormat);
                return;
            case NUMBER:
                emitNumber(lines, (SchemaCheck.NumberCheck) check, value, expression, planName, depth);
                return;
            case ARRAY:
                emitArray(lines, plan, (SchemaCheck.ArrayCheck) check, value, expression,
                        planName, strictFormat, depth);
                return;
            case ONE_OF:
                emitOneOf(lines, plan, (SchemaCheck.OneOfCheck) check, expression,
                        planName, strictFormat, depth);
                return;
            default:
                throw new IllegalStateException("unsupported compiled value kind: " + check.kind);
        }
    }

    private void emitOneOf(List<String> lines, SchemaFallbackEmitter.Plan plan,
                           SchemaCheck.OneOfCheck check, String expression, String name,
                           String strictFormat, int depth) {
        SchemaFallbackEmitter.Subplan full = plan.subplan(check.uri, name);
        // Explicit strict format assertions are delegated as a unit: some
        // specialized domain values (UUID) have a format assertion in the schema.
        if (!"false".equals(strictFormat)) {
            lines.add("if (" + strictFormat + ") {");
            lines.add("    if (!" + full.reference() + ".isValid(" + expression
                    + ", true)) return false;");
            lines.add("} else {");
        }
        String indent = "false".equals(strictFormat) ? "" : "    ";
        lines.add(indent + "if (" + expression + " == null) return false;");
        int n = 0;
        for (SchemaCheck.OneOfCheck.Subtype subtype : check.subtypes) {
            String typed = "_typed" + depth + "_" + name + "_" + n;
            lines.add(indent + (n == 0 ? "if" : "else if") + " ("
                    + expression + ".getClass() == " + subtype.type + ".class) {");
            lines.add(indent + "    " + subtype.type + " " + typed
                    + " = (" + subtype.type + ") " + expression + ";");
            for (String source : emit(subtype.check, typed, plan, strictFormat, typed + "_")) {
                // Shared local naming is safe: every subtype occupies a block.
                lines.add(indent + "    " + source);
            }
            lines.add(indent + "}");
            n++;
        }
        lines.add(indent + "else {");
        lines.add(indent + "    if (!" + full.reference()
                + ".isValid(" + expression + ", false)) return false;");
        lines.add(indent + "}");
        if (!"false".equals(strictFormat)) lines.add("}");
    }

    private void emitString(List<String> lines, SchemaCheck.StringCheck check,
                            SchemaValue value, String expression, String planName, int depth,
                            String strictFormat) {
        String text = expression;
        String temp = "_text" + depth + "_" + planName;
        if (value.shape == SchemaValue.Shape.STRING) {
            if (value.nullable) lines.add("if (" + expression + " == null) return false;");
            if (!"java.lang.String".equals(value.type.toString())
                    && (check.minLength != null || check.maxLength != null || check.choices != null)) {
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
        if (check.format != null && !"false".equals(strictFormat)) {
            lines.add("if (" + strictFormat + " && !org.sjf4j.schema.FormatValidator.of("
                    + literal(check.format) + ").validate(" + expression + ".toString())) return false;");
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

    private void emitArray(List<String> lines, SchemaFallbackEmitter.Plan plan,
                           SchemaCheck.ArrayCheck check, SchemaValue value, String expression,
                           String planName, String strictFormat, int depth) {
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
        emitValue(childLines, plan, check.items, check.itemValue, itemVar,
                planName + "_ITEM", strictFormat, depth + 1);
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

