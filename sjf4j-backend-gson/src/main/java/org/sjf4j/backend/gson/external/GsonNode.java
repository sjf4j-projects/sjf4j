package org.sjf4j.backend.gson.external;

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

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/** Gson JsonElement implementation of the external node contract. */
public final class GsonNode implements ExternalNode<JsonElement> {
    @Override
    public Class<JsonElement> nodeType() {
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
        if (node.isJsonNull()) return null;
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isString()) {
            return node.getAsString();
        }
        throw _expected("JsonPrimitive(String)", node);
    }

    @Override
    public String asString(JsonElement node) {
        return node.getAsString();
    }

    @Override
    public Number toNumber(JsonElement node) {
        if (node.isJsonNull()) return null;
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isNumber()) {
            return node.getAsNumber();
        }
        throw _expected("JsonPrimitive(Number)", node);
    }

    @Override
    public Number asNumber(JsonElement node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isNumber()) {
            return node.getAsNumber();
        }
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isString()) {
            return Nodes.asNumber(node.getAsString());
        }
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isBoolean()) {
            return Nodes.asNumber(node.getAsBoolean());
        }
        if (node.isJsonNull()) return null;
        throw new NodeException("cannot convert node type '" + Types.name(node) + "' to Number");
    }

    @Override
    public Boolean toBoolean(JsonElement node) {
        if (node.isJsonNull()) return null;
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isBoolean()) {
            return node.getAsBoolean();
        }
        throw _expected("JsonPrimitive(Boolean)", node);
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
        if (node.isJsonNull()) return null;
        throw new NodeException("cannot convert node type '" + Types.name(node) + "' to Boolean");
    }

    @Override
    public void forEachObject(JsonElement node, BiConsumer<String, Object> consumer) {
        for (Map.Entry<String, JsonElement> entry : _object(node).entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean anyMatchObject(JsonElement node, BiPredicate<String, Object> predicate) {
        for (Map.Entry<String, JsonElement> entry : _object(node).entrySet()) {
            if (predicate.test(entry.getKey(), entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean replaceAllInObject(JsonElement node, BiFunction<String, Object, Object> mapper) {
        boolean changed = false;
        for (Map.Entry<String, JsonElement> entry : _object(node).entrySet()) {
            JsonElement oldValue = entry.getValue();
            JsonElement newValue = _element(mapper.apply(entry.getKey(), oldValue));
            if (newValue != oldValue) {
                entry.setValue(newValue);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean removeIfInObject(JsonElement node, BiPredicate<String, Object> predicate) {
        return _object(node).entrySet().removeIf(entry -> predicate.test(entry.getKey(), entry.getValue()));
    }

    @Override
    public void forEachArray(JsonElement node, BiConsumer<Integer, Object> consumer) {
        JsonArray array = _array(node);
        for (int i = 0, size = array.size(); i < size; i++) {
            consumer.accept(i, array.get(i));
        }
    }

    @Override
    public boolean anyMatchInArray(JsonElement node, BiPredicate<Integer, Object> predicate) {
        JsonArray array = _array(node);
        for (int i = 0, size = array.size(); i < size; i++) {
            if (predicate.test(i, array.get(i))) return true;
        }
        return false;
    }

    @Override
    public int sizeInObject(JsonElement node) {
        return _object(node).size();
    }

    @Override
    public int sizeInArray(JsonElement node) {
        return _array(node).size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> iteratorInArray(JsonElement node) {
        return (Iterator<Object>) (Iterator<?>) _array(node).iterator();
    }

    @Override
    public boolean containsInObject(JsonElement node, String key) {
        return _object(node).get(key) != null;
    }

    @Override
    public Set<String> keySetInObject(JsonElement node) {
        return _object(node).keySet();
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySetInObject(JsonElement node) {
        Set<Map.Entry<String, JsonElement>> entries = _object(node).entrySet();
        return new AbstractSet<Map.Entry<String, Object>>() {
            @Override
            public Iterator<Map.Entry<String, Object>> iterator() {
                Iterator<Map.Entry<String, JsonElement>> iterator = entries.iterator();
                return new Iterator<Map.Entry<String, Object>>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public Map.Entry<String, Object> next() {
                        Map.Entry<String, JsonElement> entry = iterator.next();
                        return new Map.Entry<String, Object>() {
                            @Override
                            public String getKey() {
                                return entry.getKey();
                            }

                            @Override
                            public Object getValue() {
                                return entry.getValue();
                            }

                            @Override
                            public Object setValue(Object value) {
                                return entry.setValue(_element(value));
                            }

                            @Override
                            public boolean equals(Object other) {
                                return entry.equals(other);
                            }

                            @Override
                            public int hashCode() {
                                return entry.hashCode();
                            }
                        };
                    }

                    @Override
                    public void remove() {
                        iterator.remove();
                    }
                };
            }

            @Override
            public int size() {
                return entries.size();
            }
        };
    }

    @Override
    public Object getInObject(JsonElement node, String key) {
        return _object(node).get(key);
    }

    @Override
    public Object getInArray(JsonElement node, int idx) {
        JsonArray array = _array(node);
        idx = idx < 0 ? array.size() + idx : idx;
        return idx >= 0 && idx < array.size() ? array.get(idx) : null;
    }

    @Override
    public void getAccessInObject(JsonElement node, String key, Nodes.Access out) {
        JsonObject object = _object(node);
        out.node = object.get(key);
        out.present = object.has(key);
    }

    @Override
    public void putAccessInObject(JsonElement node, String key, Nodes.Access out) {
        JsonObject object = _object(node);
        out.node = object.get(key);
        out.type = JsonElement.class;
        out.puttable = true;
    }

    @Override
    public void getAccessInArray(JsonElement node, int idx, Nodes.Access out) {
        JsonArray array = _array(node);
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
        JsonArray array = _array(node);
        out.type = JsonElement.class;
        out.node = null;
        out.puttable = true;
        if (idx == null) return;
        idx = idx < 0 ? array.size() + idx : idx;
        if (idx >= 0 && idx < array.size()) {
            out.node = array.get(idx);
        } else if (idx != array.size()) {
            out.puttable = false;
        }
    }

    @Override
    public Object putInObject(JsonElement node, String key, Object value) {
        JsonObject object = _object(node);
        JsonElement old = object.get(key);
        object.add(key, _element(value));
        return old;
    }

    @Override
    public Object setInArray(JsonElement node, int idx, Object value) {
        JsonArray array = _array(node);
        int size = array.size();
        idx = idx < 0 ? size + idx : idx;
        if (idx < 0 || idx >= size) {
            throw new NodeException("cannot set at index " + idx + " in Gson JsonArray of size " + size);
        }
        return array.set(idx, _element(value));
    }

    @Override
    public void addInArray(JsonElement node, Object value) {
        _array(node).add(_element(value));
    }

    @Override
    public void addInArray(JsonElement node, int idx, Object value) {
        JsonArray array = _array(node);
        int size = array.size();
        idx = idx < 0 ? size + idx : idx;
        if (idx < 0 || idx > size) {
            throw new NodeException("cannot add at index " + idx + " in Gson JsonArray of size " + size);
        }
        array.asList().add(idx, _element(value));
    }

    @Override
    public Object removeInObject(JsonElement node, String key) {
        return _object(node).remove(key);
    }

    @Override
    public Object removeInArray(JsonElement node, int idx) {
        JsonArray array = _array(node);
        int size = array.size();
        idx = idx < 0 ? size + idx : idx;
        if (idx < 0 || idx >= size) {
            throw new NodeException("cannot remove at index " + idx + " in Gson JsonArray of size " + size);
        }
        return array.remove(idx);
    }

    @Override
    public JsonElement copy(JsonElement node) {
        if (node instanceof JsonObject) {
            JsonObject copy = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : ((JsonObject) node).entrySet()) {
                copy.add(entry.getKey(), entry.getValue());
            }
            return copy;
        }
        if (node instanceof JsonArray) {
            JsonArray array = (JsonArray) node;
            JsonArray copy = new JsonArray(array.size());
            copy.addAll(array);
            return copy;
        }
        return node;
    }

    @Override
    public JsonElement deepCopy(JsonElement node) {
        return node.deepCopy();
    }

    @Override
    public Object createObjectNode(Class<?> clazz) {
        return new JsonObject();
    }

    @Override
    public Object createArrayNode(Class<?> clazz) {
        return new JsonArray();
    }


    private static JsonObject _object(JsonElement node) {
        if (node instanceof JsonObject) return (JsonObject) node;
        throw _expected("JsonObject", node);
    }

    private static JsonArray _array(JsonElement node) {
        if (node instanceof JsonArray) return (JsonArray) node;
        throw _expected("JsonArray", node);
    }

    private static JsonElement _element(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof JsonElement) return (JsonElement) value;
        throw _expected("JsonElement or null", value);
    }

    private static NodeException _expected(String expected, Object node) {
        return new NodeException("expected " + expected + ", but was " + Types.name(node));
    }
}
