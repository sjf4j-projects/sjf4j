package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Compiles string and numeric constraints without generating Java statements. */
final class ScalarCompiler {
    /** JSON scalar equality is distinct from Java equals (numeric types compare by value). */
    private static SchemaCheck.LiteralCheck literals(Object raw, boolean enumeration) {
        if (enumeration && JsonType.of(raw) != JsonType.ARRAY) return null;
        List<Object> values = new ArrayList<Object>();
        int n = enumeration ? Nodes.sizeInArray(raw) : 1;
        for (int i = 0; i < n; i++) {
            Object item = enumeration ? Nodes.getInArray(raw, i) : raw;
            JsonType type = JsonType.of(item);
            if (type == JsonType.STRING) values.add(Nodes.toString(item));
            else if (type == JsonType.NUMBER || type == JsonType.INTEGER) values.add(Nodes.toNumber(item));
            else if (type == JsonType.BOOLEAN) values.add(Nodes.toBoolean(item));
            else if (type == JsonType.NULL) values.add(null);
            else return null;
        }
        return new SchemaCheck.LiteralCheck(values);
    }

    private static SchemaCheck combine(SchemaCheck first, List<SchemaCheck> following) {
        if (following.isEmpty()) return first;
        if (first == null && following.size() == 1) return following.get(0);
        List<SchemaCheck> all = new ArrayList<SchemaCheck>();
        if (first != null) all.add(first);
        all.addAll(following);
        return new SchemaCheck.AllCheck(all);
    }

    SchemaCheck compileLiteralsOnly(JsonObject schema, SchemaValue value) {
        if (value.shape != SchemaValue.Shape.STRING
                && value.shape != SchemaValue.Shape.NUMBER
                && value.shape != SchemaValue.Shape.DYNAMIC
                && value.shape != SchemaValue.Shape.BOOLEAN) return null;
        for (String key : schema.keySet()) {
            if (!key.equals("const") && !key.equals("enum") && !SchemaCompiler.isAnnotation(key))
                return null;
        }
        return literalChecks(schema, null);
    }

    private SchemaCheck literalChecks(JsonObject schema, SchemaCheck first) {
        List<SchemaCheck> checks = new ArrayList<SchemaCheck>();
        if (schema.containsKey("const")) {
            SchemaCheck.LiteralCheck check = literals(schema.getNode("const"), false);
            if (check == null) return null;
            checks.add(check);
        }
        if (schema.containsKey("enum")) {
            SchemaCheck.LiteralCheck check = literals(schema.getNode("enum"), true);
            if (check == null) return null;
            checks.add(check);
        }
        return combine(first, checks);
    }

    SchemaCheck compile(JsonObject schema, SchemaValue value, String kind) {
        if ("string".equals(kind)) {
            if (value.shape != SchemaValue.Shape.STRING && value.shape != SchemaValue.Shape.DYNAMIC) return null;
            for (String key : schema.keySet()) {
                if (!key.equals("type") && !key.equals("minLength") && !key.equals("maxLength")
                        && !key.equals("enum") && !key.equals("const")
                        && !key.equals("format") && !SchemaCompiler.isAnnotation(key)) return null;
            }
            Integer min = schema.getInt("minLength");
            Integer max = schema.getInt("maxLength");
            if ((schema.containsKey("minLength") && (min == null || min < 0))
                    || (schema.containsKey("maxLength") && (max == null || max < 0))) return null;
            Object enumeration = schema.getNode("enum");
            List<String> choices = null;
            if (enumeration != null) {
                if (JsonType.of(enumeration) != JsonType.ARRAY) return null;
                choices = new ArrayList<String>();
                for (int i = 0; i < Nodes.sizeInArray(enumeration); i++) {
                    Object item = Nodes.getInArray(enumeration, i);
                    if (!(item instanceof String)) return null;
                    choices.add((String) item);
                }
            }
            SchemaCheck.StringCheck string = new SchemaCheck.StringCheck(min, max, choices,
                    schema.getString("format"));
            if (!schema.containsKey("const")) return string;
            SchemaCheck.LiteralCheck constant = literals(schema.getNode("const"), false);
            return constant == null ? null : combine(string, Arrays.<SchemaCheck>asList(constant));
        }

        if ("number".equals(kind) || "integer".equals(kind)) {
            if (value.shape != SchemaValue.Shape.NUMBER && value.shape != SchemaValue.Shape.DYNAMIC) return null;
            for (String key : schema.keySet()) {
                if (!key.equals("type") && !key.equals("minimum") && !key.equals("maximum")
                        && !key.equals("exclusiveMinimum") && !key.equals("exclusiveMaximum")
                        && !key.equals("const") && !key.equals("enum")
                        && !SchemaCompiler.isAnnotation(key)) return null;
            }
            List<SchemaCheck.NumericBound> bounds = new ArrayList<SchemaCheck.NumericBound>();
            for (String key : new String[]{"minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum"}) {
                Object limit = schema.getNode(key);
                if (limit == null) continue;
                if (!(limit instanceof Number)) return null;
                bounds.add(new SchemaCheck.NumericBound(key, (Number) limit));
            }
            SchemaCheck.NumberCheck number = new SchemaCheck.NumberCheck("integer".equals(kind), bounds);
            return literalChecks(schema, number);
        }

        return null;
    }
}
