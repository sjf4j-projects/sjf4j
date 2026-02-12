package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.Types;

import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public final class JacksonNodes {

    private JacksonNodes() {}

    public static boolean isNode(Object node) {
        return node instanceof JsonNode;
    }

    public static boolean isNode(Class<?> clazz) {
        return JsonNode.class.isAssignableFrom(clazz);
    }

    public static NodeKind kindOf(Object node) {
        if (!isNode(node)) return null;
        JsonNode jsonNode = (JsonNode) node;
        if (jsonNode.isNull() || jsonNode.isMissingNode()) return NodeKind.VALUE_NULL;
        if (jsonNode.isTextual()) return NodeKind.VALUE_STRING_FACADE;
        if (jsonNode.isNumber()) return NodeKind.VALUE_NUMBER_FACADE;
        if (jsonNode.isBoolean()) return NodeKind.VALUE_BOOLEAN_FACADE;
        if (jsonNode.isObject()) return NodeKind.OBJECT_FACADE;
        if (jsonNode.isArray()) return NodeKind.ARRAY_FACADE;
        if (jsonNode.isPojo()) throw new JsonException("Not support POJONode of Jackson");
        return NodeKind.UNKNOWN;
    }

//    public static boolean isObject(Object node) {
//        return node instanceof ObjectNode;
//    }
//
//    public static boolean isArray(Object node) {
//        return node instanceof ArrayNode;
//    }
//
//    public static boolean isNull(Object node) {
//        return node == null || node instanceof NullNode;
//    }
//
//    public static boolean isMissingNode(Object node) {
//        return node instanceof MissingNode;
//    }
//
//    public static int size(Object node) {
//        return ((ContainerNode<?>) node).size();
//    }
//
//    public static Iterator<String> fieldNames(Object node) {
//        return ((JsonNode) node).fieldNames();
//    }
//
//    public static Iterator<JsonNode> elements(Object node) {
//        return ((JsonNode) node).elements();
//    }

//    public static Object get(Object node, String key) {
//        if (node instanceof ObjectNode) {
//            return ((ObjectNode) node).get(key);
//        }
//        throw new JsonException("Expected ObjectNode but was " + Types.name(node));
//    }
//
//    public static Object get(Object node, int idx) {
//        if (node instanceof ArrayNode) {
//            return ((ArrayNode) node).get(idx);
//        }
//        throw new JsonException("Expected ArrayNode but was " + Types.name(node));
//    }

//    public static boolean hasField(Object node, String key) {
//        JsonNode value = ((JsonNode) node).get(key);
//        return value != null && !value.isMissingNode();
//    }

//    public static String asText(Object node) {
//        return ((JsonNode) node).asText();
//    }
//
//    public static Number numberValue(Object node) {
//        return ((JsonNode) node).numberValue();
//    }

//    public static Boolean booleanValue(Object node) {
//        JsonNode jsonNode = (JsonNode) node;
//        return jsonNode.isNull() || jsonNode.isMissingNode() ? null : jsonNode.booleanValue();
//    }
//
//    public static Object putField(Object objectNode, String key, Object value) {
//        ObjectNode on = (ObjectNode) objectNode;
//        JsonNode old = on.get(key);
//        on.set(key, (JsonNode) toJsonNodeValue(value));
//        return normalize(old);
//    }
//
//    public static Object removeField(Object objectNode, String key) {
//        ObjectNode on = (ObjectNode) objectNode;
//        JsonNode old = on.remove(key);
//        return normalize(old);
//    }

//    public static Object setElement(Object arrayNode, int idx, Object value) {
//        ArrayNode an = (ArrayNode) arrayNode;
//        JsonNode old = an.get(idx);
//        an.set(idx, (JsonNode) toJsonNodeValue(value));
//        return normalize(old);
//    }
//
//    public static void addElement(Object arrayNode, Object value) {
//        ((ArrayNode) arrayNode).add((JsonNode) toJsonNodeValue(value));
//    }
//
//    public static void insertElement(Object arrayNode, int idx, Object value) {
//        ((ArrayNode) arrayNode).insert(idx, (JsonNode) toJsonNodeValue(value));
//    }
//
//    public static Object removeElement(Object arrayNode, int idx) {
//        JsonNode old = ((ArrayNode) arrayNode).remove(idx);
//        return normalize(old);
//    }
//
//    public static Object deepCopy(Object node) {
//        return ((JsonNode) node).deepCopy();
//    }

//    public static Object toJsonNode(Object value) {
//        return toJsonNodeValue(value);
//    }

    public static String toString(Object node) {
        if (node instanceof TextNode) {
            return ((TextNode) node).textValue();
        }
        throw new JsonException("Expected TextNode but was " + Types.name(node));
    }

    public static String asString(Object node) {
        return ((JsonNode) node).asText();
    }

    public static Number toNumber(Object node) {
        if (node instanceof NumericNode) {
            return ((NumericNode) node).numberValue();
        }
        throw new JsonException("Expected NumericNode but was " + Types.name(node));
    }

    public static Number asNumber(Object node) {
        if (node instanceof NumericNode) {
            return ((NumericNode) node).numberValue();
        }
        if (node instanceof TextNode) {
            Nodes.asNumber(((TextNode) node).textValue());
        }
        return null;
    }

    public static Boolean toBoolean(Object node) {
        if (node instanceof BooleanNode) {
            return ((BooleanNode) node).booleanValue();
        }
        throw new JsonException("Expected BooleanNode but was " + Types.name(node));
    }

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

    public static JsonObject toJsonObject(Object node) {
        if (node instanceof ObjectNode) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, JsonNode> entry : ((ObjectNode) node).properties()) {
                jo.put(entry.getKey(), entry.getValue());
            }
            return jo;
        }
        throw new JsonException("Expected ObjectNode but was " + Types.name(node));
    }

    public static Map<String, Object> toMap(Object node) {
        if (node instanceof ObjectNode) {
            ObjectNode on = (ObjectNode) node;
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(on.size());
            for (Map.Entry<String, JsonNode> entry : ((ObjectNode) node).properties()) {
                map.put(entry.getKey(), entry.getValue());
            }
            return map;
        }
        throw new JsonException("Expected ObjectNode but was " + Types.name(node));
    }

    public static JsonArray toJsonArray(Object node) {
        if (node instanceof ArrayNode) {
            JsonArray ja = new JsonArray();
            for (Iterator<JsonNode> it = ((ArrayNode) node).elements(); it.hasNext(); ) {
                ja.add(it.next());
            }
            return ja;
        }
        throw new JsonException("Expected ArrayNode but was " + Types.name(node));
    }

    public static List<Object> toList(Object node) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            List<Object> list = Sjf4jConfig.global().listSupplier.create(an.size());
            for (Iterator<JsonNode> it = ((ArrayNode) node).elements(); it.hasNext(); ) {
                list.add(it.next());
            }
            return list;
        }
        throw new JsonException("Expected ArrayNode but was " + Types.name(node));
    }

    public static Object[] toArray(Object node) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            Object[] arr = new Object[an.size()];
            for (int i = 0, size = an.size(); i < size; i++) {
                arr[i] = an.get(i);
            }
            return arr;
        }
        throw new JsonException("Expected ArrayNode but was " + Types.name(node));
    }

    public static Set<Object> toSet(Object node) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            Set<Object> set = Sjf4jConfig.global().setSupplier.create(an.size());
            for (Iterator<JsonNode> it = ((ArrayNode) node).elements(); it.hasNext(); ) {
                set.add(it.next());
            }
            return set;
        }
        throw new JsonException("Expected ArrayNode but was " + Types.name(node));
    }



    public static int sizeInObject(Object node) {
        if (node instanceof ObjectNode) {
            return ((ObjectNode) node).size();
        }
        throw new JsonException("Expected ObjectNode but was " + Types.name(node));
    }

    public static int sizeInArray(Object node) {
        if (node instanceof ArrayNode) {
            return ((ArrayNode) node).size();
        }
        throw new JsonException("Expected ArrayNode but was " + Types.name(node));
    }

    public static Set<String> keySetInObject(Object node) {
        if (node instanceof ObjectNode) {
            Iterator<String> names = ((ObjectNode) node).fieldNames();
            Set<String> set = new LinkedHashSet<>();
            while (names.hasNext()) {
                set.add(names.next());
            }
            return set;
        }
        throw new JsonException("Expected ObjectNode but was " + Types.name(node));
    }

    public static Set<Map.Entry<String, Object>> entrySetInObject(Object node) {
        if (node instanceof ObjectNode) {
            ObjectNode on = (ObjectNode) node;
            Set<Map.Entry<String, Object>> out = new LinkedHashSet<>(on.size());
            for (Map.Entry<String, JsonNode> e : on.properties()) {
                out.add(new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()));
            }
            return out;
        }
        throw new JsonException("Expected ObjectNode but was " + Types.name(node));
    }

    public static Iterator<Object> iteratorInArray(Object node) {
        if (node instanceof ArrayNode) {
            final Iterator<JsonNode> it = ((ArrayNode) node).elements();
            return new Iterator<Object>() {
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Object next() { return it.next(); }
                @Override public void remove() { it.remove(); }
            };
        }
        throw new JsonException("Expected ArrayNode but was " + Types.name(node));
    }

    public static boolean containsInObject(Object node, String key) {
        if (node instanceof ObjectNode) {
            return ((ObjectNode) node).get(key) != null;
        }
        throw new JsonException("Expected ObjectNode but was " + Types.name(node));
    }

    public static Object getInObject(Object node, String key) {
        if (node instanceof ObjectNode) {
            return ((ObjectNode) node).get(key);
        }
        throw new JsonException("Expected ObjectNode but was " + Types.name(node));
    }

    public static Object getInArray(Object node, int idx) {
        if (node instanceof ArrayNode) {
            return ((ArrayNode) node).get(idx);
        }
        throw new JsonException("Expected ArrayNode but was " + Types.name(node));
    }
    
    public static void accessInObject(Object node, Type type, String key, Nodes.Access out) {
        if (node instanceof ObjectNode) {
            out.node = ((ObjectNode) node).get(key);
            out.type = JsonNode.class;
            out.insertable = false;
            return;
        }
        throw new JsonException("Expected ObjectNode but was " + Types.name(node));
    }

    public static void accessInArray(Object node, Type type, int idx, Nodes.Access out) {
        if (node instanceof ArrayNode) {
            out.node = ((ArrayNode) node).get(idx);
            out.type = JsonNode.class;
            out.insertable = false;
            return;
        }
        throw new JsonException("Expected ArrayNode but was " + Types.name(node));
    }
    
    public static void visitObject(Object node, BiConsumer<String, Object> visitor) {
        if (node instanceof ObjectNode) {
            for (Map.Entry<String, JsonNode> entry : ((ObjectNode) node).properties()) {
                visitor.accept(entry.getKey(), entry.getValue());
            }
            return;
        }
        throw new JsonException("Expected ObjectNode but was " + Types.name(node));
    }

    public static void visitArray(Object node, BiConsumer<Integer, Object> visitor) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                visitor.accept(i, an.get(i));
            }
            return;
        }
        throw new JsonException("Expected ArrayNode but was " + Types.name(node));
    }

    public static boolean anyMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                if (predicate.test(i, an.get(i))) return true;
            }
            return false;
        }
        throw new JsonException("Expected ArrayNode but was " + Types.name(node));
    }

    public static boolean allMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                if (!predicate.test(i, an.get(i))) return false;
            }
            return true;
        }
        throw new JsonException("Expected ArrayNode but was " + Types.name(node));
    }

}
