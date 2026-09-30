package org.sjf4j.backend.jsonp.external;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.exception.NodeException;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.node.Types;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/** JSON-P {@link JsonValue} implementation of the external node contract. */
public final class JsonpNode implements ExternalNode<JsonValue> {
    @Override
    public Class<JsonValue> nodeType() {
        return JsonValue.class;
    }

    @Override
    public JsonType jsonType(JsonValue node) {
        switch (_value(node).getValueType()) {
            case OBJECT: return JsonType.OBJECT;
            case ARRAY: return JsonType.ARRAY;
            case STRING: return JsonType.STRING;
            case NUMBER: return JsonType.NUMBER;
            case TRUE:
            case FALSE: return JsonType.BOOLEAN;
            case NULL: return JsonType.NULL;
            default: return JsonType.UNKNOWN;
        }
    }

    @Override
    public JsonType jsonTypeOfClass(Class<?> type) {
        if (JsonObject.class.isAssignableFrom(type)) return JsonType.OBJECT;
        if (JsonArray.class.isAssignableFrom(type)) return JsonType.ARRAY;
        if (JsonString.class.isAssignableFrom(type)) return JsonType.STRING;
        if (JsonNumber.class.isAssignableFrom(type)) return JsonType.NUMBER;
        return JsonType.UNKNOWN;
    }

    @Override
    public String toString(JsonValue node) {
        JsonValue.ValueType type = _value(node).getValueType();
        if (type == JsonValue.ValueType.NULL) return null;
        if (node instanceof JsonString) return ((JsonString) node).getString();
        throw _expected("JsonString", node);
    }

    @Override
    public String asString(JsonValue node) {
        JsonValue.ValueType type = _value(node).getValueType();
        if (type == JsonValue.ValueType.NULL) return null;
        if (node instanceof JsonString) return ((JsonString) node).getString();
        if (type == JsonValue.ValueType.OBJECT || type == JsonValue.ValueType.ARRAY) {
            throw _cannotConvert(node, "String");
        }
        return node.toString();
    }

    @Override
    public Number toNumber(JsonValue node) {
        if (_value(node).getValueType() == JsonValue.ValueType.NULL) return null;
        if (node instanceof JsonNumber) return ((JsonNumber) node).bigDecimalValue();
        throw _expected("JsonNumber", node);
    }

    @Override
    public Number asNumber(JsonValue node) {
        JsonValue.ValueType type = _value(node).getValueType();
        if (type == JsonValue.ValueType.NULL) return null;
        if (node instanceof JsonNumber) return ((JsonNumber) node).bigDecimalValue();
        if (node instanceof JsonString) return Nodes.asNumber(((JsonString) node).getString());
        if (type == JsonValue.ValueType.TRUE) return Nodes.asNumber(true);
        if (type == JsonValue.ValueType.FALSE) return Nodes.asNumber(false);
        throw _cannotConvert(node, "Number");
    }

    @Override
    public Boolean toBoolean(JsonValue node) {
        JsonValue.ValueType type = _value(node).getValueType();
        if (type == JsonValue.ValueType.NULL) return null;
        if (type == JsonValue.ValueType.TRUE) return true;
        if (type == JsonValue.ValueType.FALSE) return false;
        throw _expected("JsonValue(TRUE or FALSE)", node);
    }

    @Override
    public Boolean asBoolean(JsonValue node) {
        JsonValue.ValueType type = _value(node).getValueType();
        if (type == JsonValue.ValueType.NULL) return null;
        if (type == JsonValue.ValueType.TRUE) return true;
        if (type == JsonValue.ValueType.FALSE) return false;
        if (node instanceof JsonString) return Nodes.asBoolean(((JsonString) node).getString());
        if (node instanceof JsonNumber) return Nodes.asBoolean(((JsonNumber) node).bigDecimalValue());
        throw _cannotConvert(node, "Boolean");
    }

    @Override
    public void forEachObject(JsonValue node, BiConsumer<String, Object> consumer) {
        for (Map.Entry<String, JsonValue> entry : _object(node).entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean anyMatchObject(JsonValue node, BiPredicate<String, Object> predicate) {
        for (Map.Entry<String, JsonValue> entry : _object(node).entrySet()) {
            if (predicate.test(entry.getKey(), entry.getValue())) return true;
        }
        return false;
    }

    @Override
    public void forEachArray(JsonValue node, BiConsumer<Integer, Object> consumer) {
        JsonArray array = _array(node);
        for (int i = 0, size = array.size(); i < size; i++) {
            consumer.accept(i, array.get(i));
        }
    }

    @Override
    public boolean anyMatchInArray(JsonValue node, BiPredicate<Integer, Object> predicate) {
        JsonArray array = _array(node);
        for (int i = 0, size = array.size(); i < size; i++) {
            if (predicate.test(i, array.get(i))) return true;
        }
        return false;
    }

    @Override
    public int sizeInObject(JsonValue node) {
        return _object(node).size();
    }

    @Override
    public int sizeInArray(JsonValue node) {
        return _array(node).size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> iteratorInArray(JsonValue node) {
        return (Iterator<Object>) (Iterator<?>) _array(node).iterator();
    }

    @Override
    public boolean containsInObject(JsonValue node, String key) {
        return _object(node).containsKey(key);
    }

    @Override
    public Set<String> keySetInObject(JsonValue node) {
        return _object(node).keySet();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<Map.Entry<String, Object>> entrySetInObject(JsonValue node) {
        return (Set<Map.Entry<String, Object>>) (Set<?>) _object(node).entrySet();
    }

    @Override
    public Object getInObject(JsonValue node, String key) {
        return _object(node).get(key);
    }

    @Override
    public Object getInArray(JsonValue node, int idx) {
        JsonArray array = _array(node);
        idx = _index(idx, array.size());
        return idx >= 0 && idx < array.size() ? array.get(idx) : null;
    }

    @Override
    public void getAccessInObject(JsonValue node, String key, Nodes.Access out) {
        JsonObject object = _object(node);
        out.node = object.get(key);
        out.present = object.containsKey(key);
    }

    @Override
    public void getAccessInArray(JsonValue node, int idx, Nodes.Access out) {
        JsonArray array = _array(node);
        out.node = null;
        out.present = false;
        idx = _index(idx, array.size());
        if (idx >= 0 && idx < array.size()) {
            out.node = array.get(idx);
            out.present = true;
        }
    }

    @Override
    public JsonValue copy(JsonValue node) {
        _value(node);
        if (node instanceof JsonObject) {
            JsonObjectBuilder copy = Json.createObjectBuilder();
            for (Map.Entry<String, JsonValue> entry : ((JsonObject) node).entrySet()) {
                copy.add(entry.getKey(), entry.getValue());
            }
            return copy.build();
        }
        if (node instanceof JsonArray) {
            JsonArrayBuilder copy = Json.createArrayBuilder();
            for (JsonValue value : (JsonArray) node) {
                copy.add(value);
            }
            return copy.build();
        }
        return node;
    }

    @Override
    public JsonValue deepCopy(JsonValue node) {
        _value(node);
        return node;
    }

    private static JsonObject _object(JsonValue node) {
        _value(node);
        if (node instanceof JsonObject) return (JsonObject) node;
        throw _expected("JsonObject", node);
    }

    private static JsonArray _array(JsonValue node) {
        _value(node);
        if (node instanceof JsonArray) return (JsonArray) node;
        throw _expected("JsonArray", node);
    }

    private static JsonValue _value(JsonValue node) {
        if (node != null) return node;
        throw _expected("JsonValue", null);
    }

    private static int _index(int idx, int size) {
        return idx < 0 ? size + idx : idx;
    }

    private static NodeException _cannotConvert(JsonValue node, String target) {
        return new NodeException("cannot convert node type '" + Types.name(node) + "' to " + target);
    }

    private static NodeException _expected(String expected, Object node) {
        return new NodeException("expected " + expected + ", but was " + Types.name(node));
    }
}
