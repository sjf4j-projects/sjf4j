package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import java.util.ArrayList;
import java.util.List;

/** Compiles string and numeric constraints without generating Java statements. */
final class ScalarCompiler {
    SchemaCheck compile(JsonObject schema, SchemaValue value, String kind) {
        if ("string".equals(kind)) {
            if (value.shape != SchemaValue.Shape.STRING && value.shape != SchemaValue.Shape.DYNAMIC) return null;
            for (String key : schema.keySet()) {
                if (!key.equals("type") && !key.equals("minLength") && !key.equals("maxLength")
                        && !key.equals("enum") && !SchemaCompiler.isAnnotation(key)
                        && !(key.equals("format") && value.uuidType
                                && "uuid".equals(schema.getString("format")))) return null;
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
            return new SchemaCheck.StringCheck(min, max, choices,
                    value.uuidType ? schema.getString("format") : null);
        }

        if ("number".equals(kind) || "integer".equals(kind)) {
            if (value.shape != SchemaValue.Shape.NUMBER && value.shape != SchemaValue.Shape.DYNAMIC) return null;
            for (String key : schema.keySet()) {
                if (!key.equals("type") && !key.equals("minimum") && !key.equals("maximum")
                        && !key.equals("exclusiveMinimum") && !key.equals("exclusiveMaximum")
                        && !SchemaCompiler.isAnnotation(key)) return null;
            }
            List<SchemaCheck.NumericBound> bounds = new ArrayList<SchemaCheck.NumericBound>();
            for (String key : new String[]{"minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum"}) {
                Object limit = schema.getNode(key);
                if (limit == null) continue;
                if (!(limit instanceof Number)) return null;
                bounds.add(new SchemaCheck.NumericBound(key, (Number) limit));
            }
            return new SchemaCheck.NumberCheck("integer".equals(kind), bounds);
        }

        return null;
    }
}
