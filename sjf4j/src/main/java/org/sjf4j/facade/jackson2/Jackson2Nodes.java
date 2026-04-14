package org.sjf4j.facade.jackson2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.Types;

import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Jackson2 JsonNode adapter used by {@link org.sjf4j.facade.FacadeNodes}.
 */
public final class Jackson2Nodes {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Jackson2Nodes() {}

    /**
     * Returns true when object is a Jackson JsonNode.
     */
    public static boolean isNode(Object node) {
        return node instanceof JsonNode;
    }

    /**
     * Returns true when class is a Jackson JsonNode type.
     */
    public static boolean isNode(Class<?> clazz) {
        return JsonNode.class.isAssignableFrom(clazz);
    }

    /**
     * Resolves node kind for a Jackson JsonNode.
     */
    public static NodeKind kindOf(Object node) {
        if (!isNode(node)) throw _notNode(node);
        JsonNode jsonNode = (JsonNode) node;
        if (jsonNode.isNull() || jsonNode.isMissingNode()) return NodeKind.VALUE_NULL;
        if (jsonNode.isTextual()) return NodeKind.VALUE_STRING_FACADE;
        if (jsonNode.isNumber()) return NodeKind.VALUE_NUMBER_FACADE;
        if (jsonNode.isBoolean()) return NodeKind.VALUE_BOOLEAN_FACADE;
        if (jsonNode.isObject()) return NodeKind.OBJECT_FACADE;
        if (jsonNode.isArray()) return NodeKind.ARRAY_FACADE;
        if (jsonNode.isPojo()) throw new JsonException("Not support POJONode of Jackson2");
        return NodeKind.UNKNOWN;
    }

    /**
     * Resolves node kind for a Jackson JsonNode class.
     */
    public static NodeKind kindOf(Class<?> clazz) {
        if (!isNode(clazz)) throw _notNode(clazz);
        if (ObjectNode.class.isAssignableFrom(clazz)) return NodeKind.OBJECT_FACADE;
        if (ArrayNode.class.isAssignableFrom(clazz)) return NodeKind.ARRAY_FACADE;
        if (TextNode.class.isAssignableFrom(clazz)) return NodeKind.VALUE_STRING_FACADE;
        if (NumericNode.class.isAssignableFrom(clazz)) return NodeKind.VALUE_NUMBER_FACADE;
        if (BooleanNode.class.isAssignableFrom(clazz)) return NodeKind.VALUE_BOOLEAN_FACADE;
        if (JsonNode.class == clazz || JsonNode.class.isAssignableFrom(clazz)) return NodeKind.UNKNOWN;
        return NodeKind.UNKNOWN;
    }

    /**
     * Converts a Jackson text node to String.
     */
    public static String toString(Object node) {
        if (node instanceof TextNode) {
            return ((TextNode) node).textValue();
        }
        throw _expected("TextNode", node);
    }

    /**
     * Converts a Jackson node to String leniently.
     */
    public static String asString(Object node) {
        return ((JsonNode) node).asText();
    }

    /**
     * Converts a Jackson numeric node to Number.
     */
    public static Number toNumber(Object node) {
        if (node instanceof NumericNode) {
            return ((NumericNode) node).numberValue();
        }
        throw _expected("NumericNode", node);
    }

    /**
     * Converts a Jackson node to Number leniently.
     */
    public static Number asNumber(Object node) {
        if (node instanceof NumericNode) {
            return ((NumericNode) node).numberValue();
        }
        if (node instanceof TextNode) {
            return Nodes.asNumber(((TextNode) node).textValue());
        }
        return null;
    }

    /**
     * Converts a Jackson boolean node to Boolean.
     */
    public static Boolean toBoolean(Object node) {
        if (node instanceof BooleanNode) {
            return ((BooleanNode) node).booleanValue();
        }
        throw _expected("BooleanNode", node);
    }

    /**
     * Converts a Jackson node to Boolean leniently.
     */
    public static Boolean asBoolean(Object node) {
        if (node instanceof BooleanNode) {
            return ((BooleanNode) node).booleanValue();
        }
        if (node instanceof TextNode) {
            return Nodes.asBoolean(((TextNode) node).textValue());
        }
        if (node instanceof NumericNode) {
            return Nodes.asBoolean(((NumericNode) node).numberValue());
        }
        return null;
    }

    /**
     * Converts Jackson object node to JsonObject.
     */
    public static JsonObject toJsonObject(Object node) {
        if (node instanceof ObjectNode) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, JsonNode> entry : ((ObjectNode) node).properties()) {
                jo.put(entry.getKey(), entry.getValue());
            }
            return jo;
        }
        throw _expected("ObjectNode", node);
    }

    /**
     * Converts Jackson object node to Map.
     */
    public static Map<String, Object> toMap(Object node) {
        if (node instanceof ObjectNode) {
            ObjectNode on = (ObjectNode) node;
            Map<String, Object> map = new LinkedHashMap<>(on.size());
            for (Map.Entry<String, JsonNode> entry : ((ObjectNode) node).properties()) {
                map.put(entry.getKey(), entry.getValue());
            }
            return map;
        }
        throw _expected("ObjectNode", node);
    }

    /**
     * Converts Jackson array node to JsonArray.
     */
    public static JsonArray toJsonArray(Object node) {
        if (node instanceof ArrayNode) {
            JsonArray ja = new JsonArray();
            for (Iterator<JsonNode> it = ((ArrayNode) node).elements(); it.hasNext(); ) {
                ja.add(it.next());
            }
            return ja;
        }
        throw _expected("ArrayNode", node);
    }

    /**
     * Converts Jackson array node to List.
     */
    public static List<Object> toList(Object node) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            List<Object> list = new ArrayList<>(an.size());
            for (Iterator<JsonNode> it = ((ArrayNode) node).elements(); it.hasNext(); ) {
                list.add(it.next());
            }
            return list;
        }
        throw _expected("ArrayNode", node);
    }

    /**
     * Converts Jackson array node to Object array.
     */
    public static Object[] toArray(Object node) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            Object[] arr = new Object[an.size()];
            for (int i = 0, size = an.size(); i < size; i++) {
                arr[i] = an.get(i);
            }
            return arr;
        }
        throw _expected("ArrayNode", node);
    }

    /**
     * Converts Jackson array node to Set.
     */
    public static Set<Object> toSet(Object node) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            Set<Object> set = new LinkedHashSet<>(an.size());
            for (Iterator<JsonNode> it = ((ArrayNode) node).elements(); it.hasNext(); ) {
                set.add(it.next());
            }
            return set;
        }
        throw _expected("ArrayNode", node);
    }



    /**
     * Returns number of fields in Jackson object node.
     */
    public static int sizeInObject(Object node) {
        if (node instanceof ObjectNode) {
            return ((ObjectNode) node).size();
        }
        throw _expected("ObjectNode", node);
    }

    /**
     * Returns number of items in Jackson array node.
     */
    public static int sizeInArray(Object node) {
        if (node instanceof ArrayNode) {
            return ((ArrayNode) node).size();
        }
        throw _expected("ArrayNode", node);
    }

    /**
     * Returns keys from Jackson object node.
     */
    public static Set<String> keySetInObject(Object node) {
        if (node instanceof ObjectNode) {
            Iterator<String> names = ((ObjectNode) node).fieldNames();
            Set<String> set = new LinkedHashSet<>();
            while (names.hasNext()) {
                set.add(names.next());
            }
            return set;
        }
        throw _expected("ObjectNode", node);
    }

    /**
     * Returns entries from Jackson object node.
     */
    public static Set<Map.Entry<String, Object>> entrySetInObject(Object node) {
        if (node instanceof ObjectNode) {
            ObjectNode on = (ObjectNode) node;
            Set<Map.Entry<String, Object>> out = new LinkedHashSet<>(on.size());
            for (Map.Entry<String, JsonNode> e : on.properties()) {
                out.add(new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()));
            }
            return out;
        }
        throw _expected("ObjectNode", node);
    }

    /**
     * Returns iterator over Jackson array node.
     */
    public static Iterator<Object> iteratorInArray(Object node) {
        if (node instanceof ArrayNode) {
            final Iterator<JsonNode> it = ((ArrayNode) node).elements();
            return new Iterator<Object>() {
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Object next() { return it.next(); }
                @Override public void remove() { it.remove(); }
            };
        }
        throw _expected("ArrayNode", node);
    }

    /**
     * Returns true when key exists in Jackson object node.
     */
    public static boolean containsInObject(Object node, String key) {
        if (node instanceof ObjectNode) {
            return ((ObjectNode) node).get(key) != null;
        }
        throw _expected("ObjectNode", node);
    }

    /**
     * Returns object field value from Jackson object node.
     */
    public static Object getInObject(Object node, String key) {
        if (node instanceof ObjectNode) {
            return ((ObjectNode) node).get(key);
        }
        throw _expected("ObjectNode", node);
    }

    /**
     * Returns array item value from Jackson array node.
     */
    public static Object getInArray(Object node, int idx) {
        if (node instanceof ArrayNode) {
            return ((ArrayNode) node).get(idx);
        }
        throw _expected("ArrayNode", node);
    }
    
    /**
     * Resolves child access info for Jackson object node.
     */
    public static void accessInObject(Object node, Type type, String key, Nodes.Access out) {
        if (node instanceof ObjectNode) {
            out.node = ((ObjectNode) node).get(key);
            out.type = JsonNode.class;
            out.puttable = true;
            return;
        }
        throw _expected("ObjectNode", node);
    }

    /**
     * Resolves child access info for Jackson array node.
     */
    public static void accessInArray(Object node, Type type, Integer idx, Nodes.Access out) {
        if (node instanceof ArrayNode) {
            out.type = JsonNode.class;
            out.node = null;
            out.puttable = true;
            ArrayNode an = (ArrayNode) node;
            if (idx == null) return;
            idx = idx < 0 ? an.size() + idx : idx;
            if (idx >= 0 && idx < an.size()) {
                out.node = an.get(idx);
                return;
            }
            if (idx == an.size()) return;
            out.puttable = false;
            return;
        }
        throw _expected("ArrayNode", node);
    }
    
    /**
     * Visits fields of Jackson object node.
     */
    public static void forEachObject(Object node, BiConsumer<String, Object> consumer) {
        if (node instanceof ObjectNode) {
            for (Map.Entry<String, JsonNode> entry : ((ObjectNode) node).properties()) {
                consumer.accept(entry.getKey(), entry.getValue());
            }
            return;
        }
        throw _expected("ObjectNode", node);
    }

    public static boolean anyMatchObject(Object node, BiPredicate<String, Object> predicate) {
        if (node instanceof ObjectNode) {
            for (Map.Entry<String, JsonNode> entry : ((ObjectNode) node).properties()) {
                if (predicate.test(entry.getKey(), entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        throw _expected("ObjectNode", node);
    }

    public static boolean transformInObject(Object node, BiFunction<String, Object, Object> mapper) {
        if (node instanceof ObjectNode) {
            boolean changed = false;
            for (Map.Entry<String, JsonNode> entry : ((ObjectNode) node).properties()) {
                Object oldValue = entry.getValue();
                Object newValue = mapper.apply(entry.getKey(), oldValue);
                if (newValue != oldValue) {
                    entry.setValue((JsonNode) newValue);
                    changed = true;
                }
            }
            return changed;
        }
        throw _expected("ObjectNode", node);
    }


    /**
     * Visits items of Jackson array node.
     */
    public static void forEachArray(Object node, BiConsumer<Integer, Object> consumer) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                consumer.accept(i, an.get(i));
            }
            return;
        }
        throw _expected("ArrayNode", node);
    }

    /**
     * Returns true when any item matches predicate.
     */
    public static boolean anyMatchArray(Object node, BiPredicate<Integer, Object> predicate) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                if (predicate.test(i, an.get(i))) return true;
            }
            return false;
        }
        throw _expected("ArrayNode", node);
    }

    /**
     * Puts a value into a Jackson object node and returns the previous value.
     */
    public static Object putInObject(Object node, String key, Object value) {
        if (!(node instanceof ObjectNode)) throw _expected("ObjectNode", node);
        if (!(value instanceof JsonNode)) throw _notNode(value);
        ObjectNode on = (ObjectNode) node;
        JsonNode old = on.get(key);
        on.set(key, (JsonNode) value);
        return old;
    }

    /**
     * Sets or appends a value in a Jackson array node.
     */
    public static Object setInArray(Object node, int idx, Object value) {
        if (!(node instanceof ArrayNode)) throw _expected("ArrayNode", node);
        if (!(value instanceof JsonNode)) throw _notNode(value);
        ArrayNode an = (ArrayNode) node;
        idx = idx < 0 ? an.size() + idx : idx;
        JsonNode vv = (JsonNode) value;
        if (idx == an.size()) {
            an.add(vv);
            return null;
        }
        if (idx >= 0 && idx < an.size()) {
            JsonNode old = an.get(idx);
            an.set(idx, vv);
            return old;
        }
        throw new JsonException("Cannot set/add index " + idx + " in ArrayNode of size " +
                an.size() + " (index < size: modify; index == size: append)");
    }

    /**
     * Appends a value to a Jackson array node.
     */
    public static void addInArray(Object node, Object value) {
        if (!(node instanceof ArrayNode)) throw _expected("ArrayNode", node);
        if (!(value instanceof JsonNode)) throw _notNode(value);
        ((ArrayNode) node).add((JsonNode) value);
    }

    /**
     * Inserts a value into a Jackson array node.
     */
    public static void addInArray(Object node, int idx, Object value) {
        if (!(node instanceof ArrayNode)) throw _expected("ArrayNode", node);
        if (!(value instanceof JsonNode)) throw _notNode(value);
        ArrayNode an = (ArrayNode) node;
        idx = idx < 0 ? an.size() + idx : idx;
        if (idx < 0 || idx > an.size()) {
            throw new JsonException("Cannot insert index " + idx + " in ArrayNode of size " + an.size());
        }
        an.insert(idx, (JsonNode) value);
    }

    /**
     * Removes a key from a Jackson object node.
     */
    public static Object removeInObject(Object node, String key) {
        if (node instanceof ObjectNode) {
            return ((ObjectNode) node).remove(key);
        }
        throw _expected("ObjectNode", node);
    }

    /**
     * Removes an element from a Jackson array node.
     */
    public static Object removeInArray(Object node, int idx) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            idx = idx < 0 ? an.size() + idx : idx;
            if (idx < 0 || idx >= an.size()) {
                throw new JsonException("Cannot remove index " + idx + " in ArrayNode of size " + an.size());
            }
            return an.remove(idx);
        }
        throw _expected("ArrayNode", node);
    }


    private static JsonException _notNode(Object node) {
        return new JsonException("Not a Jackson 2.x JsonNode, but was '" + Types.name(node) + "'");
    }

    private static JsonException _expected(String expected, Object node) {
        return new JsonException("Expected " + expected + " but was " + Types.name(node));
    }


}
