package org.sjf4j.integration.gson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.exception.NodeException;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.node.Types;

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/** Gson JsonElement implementation of the external node contract. */
public final class GsonExternalNode implements ExternalNode<JsonElement> {
    @Override
    public Class<JsonElement> rootType() {
        return JsonElement.class;
    }

    @Override
    public JsonType jsonType(JsonElement node) {
        if (node.isJsonNull()) return JsonType.NULL;
        if (node.isJsonObject()) return JsonType.OBJECT;
        if (node.isJsonArray()) return JsonType.ARRAY;
        if (node.isJsonPrimitive()) {
            JsonPrimitive value = node.getAsJsonPrimitive();
            if (value.isString()) return JsonType.STRING;
            if (value.isNumber()) return JsonType.NUMBER;
            if (value.isBoolean()) return JsonType.BOOLEAN;
        }
        return JsonType.UNKNOWN;
    }

    @Override
    public JsonType jsonTypeOfClass(Class<?> type) {
        if (JsonObject.class.isAssignableFrom(type)) return JsonType.OBJECT;
        if (JsonArray.class.isAssignableFrom(type)) return JsonType.ARRAY;
        if (JsonNull.class.isAssignableFrom(type)) return JsonType.NULL;
        return JsonType.UNKNOWN;
    }

    @Override
    public String toString(JsonElement node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isString()) {
            return node.getAsString();
        }
        throw expected("JsonPrimitive(String)", node);
    }

    @Override
    public String asString(JsonElement node) {
        return node.getAsString();
    }

    @Override
    public Number toNumber(JsonElement node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isNumber()) {
            return node.getAsNumber();
        }
        throw expected("JsonPrimitive(Number)", node);
    }

    @Override
    public Number asNumber(JsonElement node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isNumber()) {
            return node.getAsNumber();
        }
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isString()) {
            return Nodes.asNumber(node.getAsString());
        }
        return null;
    }

    @Override
    public Boolean toBoolean(JsonElement node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isBoolean()) {
            return node.getAsBoolean();
        }
        throw expected("JsonPrimitive(Boolean)", node);
    }

    @Override
    public Boolean asBoolean(JsonElement node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isBoolean()) {
            return node.getAsBoolean();
        }
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isString()) {
            return Nodes.asBoolean(node.getAsString());
        }
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isNumber()) {
            return Nodes.asBoolean(node.getAsNumber());
        }
        return null;
    }

    @Override
    public void forEachObject(JsonElement node, BiConsumer<String, Object> consumer) {
        for (Map.Entry<String, JsonElement> entry : object(node).entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean anyMatchObject(JsonElement node, BiPredicate<String, Object> predicate) {
        for (Map.Entry<String, JsonElement> entry : object(node).entrySet()) {
            if (predicate.test(entry.getKey(), entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean replaceInObject(JsonElement node, BiFunction<String, Object, Object> mapper) {
        boolean changed = false;
        for (Map.Entry<String, JsonElement> entry : object(node).entrySet()) {
            JsonElement oldValue = entry.getValue();
            JsonElement newValue = (JsonElement) mapper.apply(entry.getKey(), oldValue);
            if (newValue != oldValue) {
                entry.setValue(newValue);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean removeIfInObject(JsonElement node, BiPredicate<String, Object> predicate) {
        return object(node).entrySet().removeIf(entry -> predicate.test(entry.getKey(), entry.getValue()));
    }

    @Override
    public void forEachArray(JsonElement node, BiConsumer<Integer, Object> consumer) {
        JsonArray array = array(node);
        for (int i = 0, size = array.size(); i < size; i++) {
            consumer.accept(i, array.get(i));
        }
    }

    @Override
    public boolean anyMatchArray(JsonElement node, BiPredicate<Integer, Object> predicate) {
        JsonArray array = array(node);
        for (int i = 0, size = array.size(); i < size; i++) {
            if (predicate.test(i, array.get(i))) return true;
        }
        return false;
    }

    @Override
    public int sizeInObject(JsonElement node) {
        return object(node).size();
    }

    @Override
    public int sizeInArray(JsonElement node) {
        return array(node).size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> iteratorInArray(JsonElement node) {
        return (Iterator<Object>) (Iterator<?>) array(node).iterator();
    }

    @Override
    public boolean containsInObject(JsonElement node, String key) {
        return object(node).get(key) != null;
    }

    @Override
    public Object getInObject(JsonElement node, String key) {
        return object(node).get(key);
    }

    @Override
    public Object getInArray(JsonElement node, int idx) {
        return array(node).get(idx);
    }

    @Override
    public void getAccessInObject(JsonElement node, String key, Nodes.Access out) {
        JsonObject object = object(node);
        out.node = object.get(key);
        out.present = object.has(key);
    }

    @Override
    public void putAccessInObject(JsonElement node, String key, Nodes.Access out) {
        JsonObject object = object(node);
        out.node = object.get(key);
        out.type = JsonElement.class;
        out.puttable = false;
    }

    @Override
    public void getAccessInArray(JsonElement node, int idx, Nodes.Access out) {
        JsonArray array = array(node);
        out.node = null;
        out.present = false;
        idx = idx < 0 ? array.size() + idx : idx;
        if (idx >= 0 && idx < array.size()) {
            out.node = array.get(idx);
            out.present = true;
        }
    }

    @Override
    public void putAccessInArray(JsonElement node, Integer idx, Nodes.Access out) {
        JsonArray array = array(node);
        out.type = JsonElement.class;
        out.node = null;
        out.puttable = false;
        if (idx == null) return;
        idx = idx < 0 ? array.size() + idx : idx;
        if (idx >= 0 && idx < array.size()) {
            out.node = array.get(idx);
        }
    }


    private static JsonObject object(JsonElement node) {
        if (node instanceof JsonObject) return (JsonObject) node;
        throw expected("JsonObject", node);
    }

    private static JsonArray array(JsonElement node) {
        if (node instanceof JsonArray) return (JsonArray) node;
        throw expected("JsonArray", node);
    }

    private static NodeException expected(String expected, Object node) {
        return new NodeException("expected " + expected + ", but was " + Types.name(node));
    }
}
