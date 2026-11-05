package org.sjf4j.processor.schema;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.schema.BooleanSchema;

/**
 * Compilation-only projection of simple allOf coverage into the local
 * unevaluated* check. Branch assertions remain in place: their coverage is
 * usable only on the allOf success path, when each branch has succeeded.
 *
 * <p>This intentionally handles neither conditional coverage nor another
 * unevaluated* inside a child. It never creates runtime tracking state.
 */
final class StaticAllOfCoverage {
    private StaticAllOfCoverage() {}

    static JsonObject project(JsonObject schema) {
        boolean object = schema.containsKey("unevaluatedProperties");
        boolean array = schema.containsKey("unevaluatedItems");
        if (object == array || !schema.containsKey("allOf")) return null;
        if (object && schema.containsKey("additionalProperties")) return null;

        Object rawBranches = schema.getNode("allOf");
        if (JsonType.of(rawBranches) != JsonType.ARRAY) return null;
        int count = Nodes.sizeInArray(rawBranches);
        if (count == 0) return null;

        JsonObject names = new JsonObject();
        JsonObject patterns = new JsonObject();
        int prefixLength = 0;
        boolean allCovered = false;
        for (int i = 0; i < count; i++) {
            Object raw = Nodes.getInArray(rawBranches, i);
            if (Boolean.TRUE.equals(raw)
                    || (raw instanceof BooleanSchema && ((BooleanSchema) raw).booleanValue()))
                continue;
            if (JsonType.of(raw) != JsonType.OBJECT) return null;
            JsonObject child = SchemaCompiler.schemaObject(raw);
            for (String key : child.keySet()) {
                if (key.equals("type")) {
                    if (!((object ? "object" : "array").equals(child.getNode(key)))) return null;
                } else if (object && (key.equals("properties") || key.equals("patternProperties"))) {
                    Object rawMap = child.getNode(key);
                    if (JsonType.of(rawMap) != JsonType.OBJECT) return null;
                    JsonObject selected = key.equals("properties") ? names : patterns;
                    for (String name : Nodes.keySetInObject(rawMap)) selected.put(name, true);
                } else if (object && key.equals("additionalProperties")) {
                    if (!Boolean.TRUE.equals(child.getNode(key))) return null;
                    allCovered = true;
                } else if (array && key.equals("prefixItems")) {
                    Object rawPrefix = child.getNode(key);
                    if (JsonType.of(rawPrefix) != JsonType.ARRAY) return null;
                    prefixLength = Math.max(prefixLength, Nodes.sizeInArray(rawPrefix));
                } else if (array && key.equals("items")) {
                    if (!Boolean.TRUE.equals(child.getNode(key))) return null;
                    allCovered = true;
                } else if (!isHarmlessAnnotation(key)) {
                    return null;
                }
            }
        }

        JsonObject projected = new JsonObject();
        for (String key : schema.keySet()) {
            if (!allCovered || !(object ? "unevaluatedProperties" : "unevaluatedItems").equals(key))
                projected.put(key, schema.getNode(key));
        }
        if (allCovered) return projected;

        if (object) {
            if (!mergeNames(projected, "properties", names)
                    || !mergeNames(projected, "patternProperties", patterns)) return null;
        } else if (prefixLength != 0) {
            Object rawPrefix = schema.getNode("prefixItems");
            if (rawPrefix != null && JsonType.of(rawPrefix) != JsonType.ARRAY) return null;
            JsonArray prefix = new JsonArray();
            if (rawPrefix != null) {
                for (int i = 0, n = Nodes.sizeInArray(rawPrefix); i < n; i++)
                    prefix.add(Nodes.getInArray(rawPrefix, i));
            }
            while (prefix.size() < prefixLength) prefix.add(true);
            projected.put("prefixItems", prefix);
        }
        return projected;
    }

    private static boolean mergeNames(JsonObject into, String keyword, JsonObject extra) {
        if (extra.isEmpty()) return true;
        Object original = into.getNode(keyword);
        if (original != null && JsonType.of(original) != JsonType.OBJECT) return false;
        JsonObject joined = new JsonObject();
        if (original != null) {
            for (String name : Nodes.keySetInObject(original))
                joined.put(name, Nodes.getInObject(original, name));
        }
        for (String name : extra.keySet()) {
            if (!joined.containsKey(name)) joined.put(name, true);
        }
        into.put(keyword, joined);
        return true;
    }

    private static boolean isHarmlessAnnotation(String key) {
        switch (key) {
            case "$comment": case "title": case "description": case "default":
            case "examples": case "readOnly": case "writeOnly": case "deprecated":
                return true;
            default:
                return false;
        }
    }
}
