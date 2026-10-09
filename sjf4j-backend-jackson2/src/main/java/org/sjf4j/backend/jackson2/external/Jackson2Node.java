package org.sjf4j.backend.jackson2.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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

/** Jackson 2 {@link JsonNode} implementation of the external node contract. */
public final class Jackson2Node implements ExternalNode<JsonNode> {
    @Override
    public Class<JsonNode> nodeType() {
        return JsonNode.class;
    }

    @Override
    public JsonType jsonType(JsonNode node) {
        _node(node);
        if (node.isNull()) return JsonType.NULL;
        if (node.isObject()) return JsonType.OBJECT;
        if (node.isArray()) return JsonType.ARRAY;
        if (node.isTextual()) return JsonType.STRING;
        if (node.isNumber()) return JsonType.NUMBER;
        if (node.isBoolean()) return JsonType.BOOLEAN;
        return JsonType.UNKNOWN;
    }

    @Override
    public JsonType jsonTypeOfClass(Class<?> type) {
        if (ObjectNode.class.isAssignableFrom(type)) return JsonType.OBJECT;
        if (ArrayNode.class.isAssignableFrom(type)) return JsonType.ARRAY;
        if (NullNode.class.isAssignableFrom(type)) return JsonType.NULL;
        if (TextNode.class.isAssignableFrom(type)) return JsonType.STRING;
        if (NumericNode.class.isAssignableFrom(type)) return JsonType.NUMBER;
        if (BooleanNode.class.isAssignableFrom(type)) return JsonType.BOOLEAN;
        return JsonType.UNKNOWN;
    }

    @Override
    public String toString(JsonNode node) {
        _node(node);
        if (node.isNull()) return null;
        if (node instanceof TextNode) return node.textValue();
        throw _expected("TextNode", node);
    }

    @Override
    public String asString(JsonNode node) {
        _node(node);
        if (node.isNull()) return null;
        return node.asText();
    }

    @Override
    public Number toNumber(JsonNode node) {
        _node(node);
        if (node.isNull()) return null;
        if (node instanceof NumericNode) return node.numberValue();
        throw _expected("NumericNode", node);
    }

    @Override
    public Number asNumber(JsonNode node) {
        _node(node);
        if (node.isNull()) return null;
        if (node instanceof NumericNode) return node.numberValue();
        if (node instanceof TextNode) return Nodes.asNumber(node.textValue());
        if (node instanceof BooleanNode) return Nodes.asNumber(node.booleanValue());
        throw _cannotConvert(node, "Number");
    }

    @Override
    public Boolean toBoolean(JsonNode node) {
        _node(node);
        if (node.isNull()) return null;
        if (node instanceof BooleanNode) return node.booleanValue();
        throw _expected("BooleanNode", node);
    }

    @Override
    public Boolean asBoolean(JsonNode node) {
        _node(node);
        if (node.isNull()) return null;
        if (node instanceof BooleanNode) return node.booleanValue();
        if (node instanceof TextNode) return Nodes.asBoolean(node.textValue());
        if (node instanceof NumericNode) return Nodes.asBoolean(node.numberValue());
        throw _cannotConvert(node, "Boolean");
    }

    @Override
    public void forEachObject(JsonNode node, BiConsumer<String, Object> consumer) {
        Set<Map.Entry<String, JsonNode>> entries = _object(node).properties();
        for (Map.Entry<String, JsonNode> entry : entries) {
            consumer.accept(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean anyMatchObject(JsonNode node, BiPredicate<String, Object> predicate) {
        Set<Map.Entry<String, JsonNode>> entries = _object(node).properties();
        for (Map.Entry<String, JsonNode> entry : entries) {
            if (predicate.test(entry.getKey(), entry.getValue())) return true;
        }
        return false;
    }

    @Override
    public boolean replaceAllInObject(JsonNode node, BiFunction<String, Object, Object> mapper) {
        boolean changed = false;
        Set<Map.Entry<String, JsonNode>> entries = _object(node).properties();
        for (Map.Entry<String, JsonNode> entry : entries) {
            JsonNode oldValue = entry.getValue();
            JsonNode newValue = _nodeValue(mapper.apply(entry.getKey(), oldValue));
            if (newValue != oldValue) {
                entry.setValue(newValue);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean removeIfInObject(JsonNode node, BiPredicate<String, Object> predicate) {
        boolean changed = false;
        Iterator<Map.Entry<String, JsonNode>> its = _object(node).properties().iterator();
        while (its.hasNext()) {
            Map.Entry<String, JsonNode> entry = its.next();
            if (predicate.test(entry.getKey(), entry.getValue())) {
                its.remove();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void forEachArray(JsonNode node, BiConsumer<Integer, Object> consumer) {
        ArrayNode array = _array(node);
        for (int i = 0, size = array.size(); i < size; i++) consumer.accept(i, array.get(i));
    }

    @Override
    public boolean anyMatchInArray(JsonNode node, BiPredicate<Integer, Object> predicate) {
        ArrayNode array = _array(node);
        for (int i = 0, size = array.size(); i < size; i++) {
            if (predicate.test(i, array.get(i))) return true;
        }
        return false;
    }

    @Override
    public int sizeInObject(JsonNode node) {
        return _object(node).size();
    }

    @Override
    public int sizeInArray(JsonNode node) {
        return _array(node).size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> iteratorInArray(JsonNode node) {
        return (Iterator<Object>) (Iterator<?>) _array(node).elements();
    }

    @Override
    public boolean containsInObject(JsonNode node, String key) {
        return _object(node).has(key);
    }

    @Override
    public Set<String> keySetInObject(JsonNode node) {
        ObjectNode object = _object(node);
        return new AbstractSet<String>() {
            @Override
            public Iterator<String> iterator() {
                Iterator<Map.Entry<String, JsonNode>> iterator = object.properties().iterator();
                return new Iterator<String>() {
                    @Override public boolean hasNext() { return iterator.hasNext(); }
                    @Override public String next() { return iterator.next().getKey(); }
                    @Override public void remove() { iterator.remove(); }
                };
            }

            @Override public int size() { return object.size(); }
            @Override public boolean contains(Object key) {
                return key instanceof String && object.has((String) key);
            }
        };
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySetInObject(JsonNode node) {
        ObjectNode object = _object(node);
        return new AbstractSet<Map.Entry<String, Object>>() {
            @Override
            public Iterator<Map.Entry<String, Object>> iterator() {
                Iterator<Map.Entry<String, JsonNode>> iterator = object.properties().iterator();
                return new Iterator<Map.Entry<String, Object>>() {
                    @Override public boolean hasNext() { return iterator.hasNext(); }

                    @Override
                    public Map.Entry<String, Object> next() {
                        Map.Entry<String, JsonNode> entry = iterator.next();
                        return new Map.Entry<String, Object>() {
                            @Override public String getKey() { return entry.getKey(); }
                            @Override public Object getValue() { return entry.getValue(); }
                            @Override public Object setValue(Object value) {
                                return entry.setValue(_nodeValue(value));
                            }
                            @Override public boolean equals(Object other) { return entry.equals(other); }
                            @Override public int hashCode() { return entry.hashCode(); }
                        };
                    }

                    @Override public void remove() { iterator.remove(); }
                };
            }

            @Override public int size() { return object.size(); }
        };
    }

    @Override
    public Object getInObject(JsonNode node, String key) {
        return _object(node).get(key);
    }

    @Override
    public Object getInArray(JsonNode node, int idx) {
        ArrayNode array = _array(node);
        idx = _index(idx, array.size());
        return idx >= 0 && idx < array.size() ? array.get(idx) : null;
    }

    @Override
    public void getAccessInObject(JsonNode node, String key, Nodes.Access out) {
        ObjectNode object = _object(node);
        out.node = object.get(key);
        out.present = object.has(key);
    }

    @Override
    public void putAccessInObject(JsonNode node, String key, Nodes.Access out) {
        out.node = _object(node).get(key);
        out.type = JsonNode.class;
        out.puttable = true;
    }

    @Override
    public void getAccessInArray(JsonNode node, int idx, Nodes.Access out) {
        ArrayNode array = _array(node);
        out.node = null;
        out.present = false;
        idx = _index(idx, array.size());
        if (idx >= 0 && idx < array.size()) {
            out.node = array.get(idx);
            out.present = true;
        }
    }

    @Override
    public void putAccessInArray(JsonNode node, Integer idx, Nodes.Access out) {
        ArrayNode array = _array(node);
        out.type = JsonNode.class;
        out.node = null;
        out.puttable = true;
        if (idx == null) return;
        idx = _index(idx, array.size());
        if (idx >= 0 && idx < array.size()) {
            out.node = array.get(idx);
        } else if (idx != array.size()) {
            out.puttable = false;
        }
    }

    @Override
    public Object putInObject(JsonNode node, String key, Object value) {
        ObjectNode object = _object(node);
        JsonNode old = object.get(key);
        object.set(key, _nodeValue(value));
        return old;
    }

    @Override
    public Object setInArray(JsonNode node, int idx, Object value) {
        ArrayNode array = _array(node);
        int size = array.size();
        idx = _index(idx, size);
        if (idx < 0 || idx >= size) throw _indexError("set", idx, size);
        JsonNode old = array.get(idx);
        array.set(idx, _nodeValue(value));
        return old;
    }

    @Override
    public void addInArray(JsonNode node, Object value) {
        _array(node).add(_nodeValue(value));
    }

    @Override
    public void addInArray(JsonNode node, int idx, Object value) {
        ArrayNode array = _array(node);
        int size = array.size();
        idx = _index(idx, size);
        if (idx < 0 || idx > size) throw _indexError("add", idx, size);
        array.insert(idx, _nodeValue(value));
    }

    @Override
    public Object removeInObject(JsonNode node, String key) {
        return _object(node).remove(key);
    }

    @Override
    public Object removeInArray(JsonNode node, int idx) {
        ArrayNode array = _array(node);
        int size = array.size();
        idx = _index(idx, size);
        if (idx < 0 || idx >= size) throw _indexError("remove", idx, size);
        return array.remove(idx);
    }

    @Override
    public JsonNode copy(JsonNode node) {
        _node(node);
        if (node instanceof ObjectNode) {
            ObjectNode object = (ObjectNode) node;
            ObjectNode copy = object.objectNode();
            copy.setAll(object);
            return copy;
        }
        if (node instanceof ArrayNode) {
            ArrayNode array = (ArrayNode) node;
            ArrayNode copy = array.arrayNode();
            copy.addAll(array);
            return copy;
        }
        return node;
    }

    @Override
    public JsonNode deepCopy(JsonNode node) {
        _node(node);
        return node.deepCopy();
    }

    @Override
    public Object createValueNode(Object value) {
        if (value == null) return NullNode.instance;
        if (value instanceof String) return JsonNodeFactory.instance.textNode((String) value);
        if (value instanceof Boolean) return BooleanNode.valueOf((Boolean) value);
        if (value instanceof java.math.BigDecimal) return JsonNodeFactory.instance.numberNode((java.math.BigDecimal) value);
        if (value instanceof java.math.BigInteger) return JsonNodeFactory.instance.numberNode((java.math.BigInteger) value);
        if (value instanceof Integer) return JsonNodeFactory.instance.numberNode((Integer) value);
        if (value instanceof Long) return JsonNodeFactory.instance.numberNode((Long) value);
        if (value instanceof Double) return JsonNodeFactory.instance.numberNode((Double) value);
        if (value instanceof Float) return JsonNodeFactory.instance.numberNode((Float) value);
        if (value instanceof Short) return JsonNodeFactory.instance.numberNode((Short) value);
        if (value instanceof Byte) return JsonNodeFactory.instance.numberNode((Byte) value);
        throw new NodeException("unsupported Java value for Jackson 2 node: '" + Types.name(value) + "'");
    }

    @Override
    public Object createObjectNode(Class<?> clazz) {
        return JsonNodeFactory.instance.objectNode();
    }

    @Override
    public Object createArrayNode(Class<?> clazz) {
        return JsonNodeFactory.instance.arrayNode();
    }

    private static ObjectNode _object(JsonNode node) {
        if (node instanceof ObjectNode) return (ObjectNode) node;
        throw _expected("ObjectNode", node);
    }

    private static ArrayNode _array(JsonNode node) {
        if (node instanceof ArrayNode) return (ArrayNode) node;
        throw _expected("ArrayNode", node);
    }

    private static JsonNode _node(JsonNode node) {
        if (node != null) return node;
        throw _expected("Jackson 2 JsonNode", null);
    }

    private static JsonNode _nodeValue(Object value) {
        if (value == null) return NullNode.instance;
        if (value instanceof JsonNode) return (JsonNode) value;
        throw _expected("Jackson 2 JsonNode or null", value);
    }

    private static int _index(int idx, int size) {
        return idx < 0 ? size + idx : idx;
    }

    private static NodeException _indexError(String operation, int idx, int size) {
        return new NodeException("cannot " + operation + " at index " + idx + " in Jackson 2 ArrayNode of size " + size);
    }

    private static NodeException _cannotConvert(JsonNode node, String target) {
        return new NodeException("cannot convert node type '" + Types.name(node) + "' to " + target);
    }

    private static NodeException _expected(String expected, Object node) {
        return new NodeException("expected " + expected + ", but was " + Types.name(node));
    }
}
