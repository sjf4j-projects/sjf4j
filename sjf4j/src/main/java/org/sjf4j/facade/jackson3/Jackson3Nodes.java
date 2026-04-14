package org.sjf4j.facade.jackson3;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.Types;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.NumericNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

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
 * Jackson3 JsonNode adapter used by {@link org.sjf4j.facade.FacadeNodes}.
 */
public final class Jackson3Nodes {

    private Jackson3Nodes() {}

    public static boolean isNode(Object node) {
        return node instanceof JsonNode;
    }

    public static boolean isNode(Class<?> clazz) {
        return JsonNode.class.isAssignableFrom(clazz);
    }

    public static NodeKind kindOf(Object node) {
        if (!isNode(node)) throw notNode(node);
        JsonNode jsonNode = (JsonNode) node;
        if (jsonNode.isNull() || jsonNode.isMissingNode()) return NodeKind.VALUE_NULL;
        if (jsonNode.isString()) return NodeKind.VALUE_STRING_FACADE;
        if (jsonNode.isNumber()) return NodeKind.VALUE_NUMBER_FACADE;
        if (jsonNode.isBoolean()) return NodeKind.VALUE_BOOLEAN_FACADE;
        if (jsonNode.isObject()) return NodeKind.OBJECT_FACADE;
        if (jsonNode.isArray()) return NodeKind.ARRAY_FACADE;
        if (jsonNode.isPojo()) throw new JsonException("Not support POJONode of Jackson3");
        return NodeKind.UNKNOWN;
    }

    public static NodeKind kindOf(Class<?> clazz) {
        if (!isNode(clazz)) throw notNode(clazz);
        if (ObjectNode.class.isAssignableFrom(clazz)) return NodeKind.OBJECT_FACADE;
        if (ArrayNode.class.isAssignableFrom(clazz)) return NodeKind.ARRAY_FACADE;
        if (StringNode.class.isAssignableFrom(clazz)) return NodeKind.VALUE_STRING_FACADE;
        if (NumericNode.class.isAssignableFrom(clazz)) return NodeKind.VALUE_NUMBER_FACADE;
        if (BooleanNode.class.isAssignableFrom(clazz)) return NodeKind.VALUE_BOOLEAN_FACADE;
        if (JsonNode.class == clazz || JsonNode.class.isAssignableFrom(clazz)) return NodeKind.UNKNOWN;
        return NodeKind.UNKNOWN;
    }

    public static String toString(Object node) {
        if (node instanceof StringNode) {
            return ((StringNode) node).stringValue();
        }
        throw expected("StringNode", node);
    }

    public static String asString(Object node) {
        return ((JsonNode) node).asString();
    }

    public static Number toNumber(Object node) {
        if (node instanceof NumericNode) {
            return ((NumericNode) node).numberValue();
        }
        throw expected("NumericNode", node);
    }

    public static Number asNumber(Object node) {
        if (node instanceof NumericNode) {
            return ((NumericNode) node).numberValue();
        }
        if (node instanceof StringNode) {
            return Nodes.asNumber(((StringNode) node).stringValue());
        }
        return null;
    }

    public static Boolean toBoolean(Object node) {
        if (node instanceof BooleanNode) {
            return ((BooleanNode) node).booleanValue();
        }
        throw expected("BooleanNode", node);
    }

    public static Boolean asBoolean(Object node) {
        if (node instanceof BooleanNode) {
            return ((BooleanNode) node).booleanValue();
        }
        if (node instanceof StringNode) {
            return Nodes.asBoolean(((StringNode) node).stringValue());
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
        throw expected("ObjectNode", node);
    }

    public static Map<String, Object> toMap(Object node) {
        if (node instanceof ObjectNode) {
            ObjectNode on = (ObjectNode) node;
            Map<String, Object> map = new LinkedHashMap<>(on.size());
            for (Map.Entry<String, JsonNode> entry : ((ObjectNode) node).properties()) {
                map.put(entry.getKey(), entry.getValue());
            }
            return map;
        }
        throw expected("ObjectNode", node);
    }

    public static JsonArray toJsonArray(Object node) {
        if (node instanceof ArrayNode) {
            JsonArray ja = new JsonArray();
            for (JsonNode item : ((ArrayNode) node).elements()) {
                ja.add(item);
            }
            return ja;
        }
        throw expected("ArrayNode", node);
    }

    public static List<Object> toList(Object node) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            List<Object> list = new ArrayList<>(an.size());
            for (JsonNode item : an.elements()) {
                list.add(item);
            }
            return list;
        }
        throw expected("ArrayNode", node);
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
        throw expected("ArrayNode", node);
    }

    public static Set<Object> toSet(Object node) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            Set<Object> set = new LinkedHashSet<>(an.size());
            for (JsonNode item : an.elements()) {
                set.add(item);
            }
            return set;
        }
        throw expected("ArrayNode", node);
    }

    public static int sizeInObject(Object node) {
        if (node instanceof ObjectNode) {
            return ((ObjectNode) node).size();
        }
        throw expected("ObjectNode", node);
    }

    public static int sizeInArray(Object node) {
        if (node instanceof ArrayNode) {
            return ((ArrayNode) node).size();
        }
        throw expected("ArrayNode", node);
    }

    public static Set<String> keySetInObject(Object node) {
        if (node instanceof ObjectNode) {
            return new LinkedHashSet<>(((ObjectNode) node).propertyNames());
        }
        throw expected("ObjectNode", node);
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
        throw expected("ObjectNode", node);
    }

    public static Iterator<Object> iteratorInArray(Object node) {
        if (node instanceof ArrayNode) {
            final Iterator<JsonNode> it = ((ArrayNode) node).elements().iterator();
            return new Iterator<Object>() {
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Object next() { return it.next(); }
                @Override public void remove() { it.remove(); }
            };
        }
        throw expected("ArrayNode", node);
    }

    public static boolean containsInObject(Object node, String key) {
        if (node instanceof ObjectNode) {
            return ((ObjectNode) node).get(key) != null;
        }
        throw expected("ObjectNode", node);
    }

    public static Object getInObject(Object node, String key) {
        if (node instanceof ObjectNode) {
            return ((ObjectNode) node).get(key);
        }
        throw expected("ObjectNode", node);
    }

    public static Object getInArray(Object node, int idx) {
        if (node instanceof ArrayNode) {
            return ((ArrayNode) node).get(idx);
        }
        throw expected("ArrayNode", node);
    }

    public static void accessInObject(Object node, Type type, String key, Nodes.Access out) {
        if (node instanceof ObjectNode) {
            out.node = ((ObjectNode) node).get(key);
            out.type = JsonNode.class;
            out.insertable = true;
            return;
        }
        throw expected("ObjectNode", node);
    }

    public static void accessInArray(Object node, Type type, Integer idx, Nodes.Access out) {
        if (node instanceof ArrayNode) {
            out.type = JsonNode.class;
            out.node = null;
            out.insertable = true;
            ArrayNode an = (ArrayNode) node;
            if (idx == null) return;
            idx = idx < 0 ? an.size() + idx : idx;
            if (idx >= 0 && idx < an.size()) {
                out.node = an.get(idx);
                return;
            }
            if (idx == an.size()) return;
            out.insertable = false;
            return;
        }
        throw expected("ArrayNode", node);
    }

    public static void visitObject(Object node, BiConsumer<String, Object> visitor) {
        if (node instanceof ObjectNode) {
            for (Map.Entry<String, JsonNode> entry : ((ObjectNode) node).properties()) {
                visitor.accept(entry.getKey(), entry.getValue());
            }
            return;
        }
        throw expected("ObjectNode", node);
    }

    public static boolean anyMatchInObject(Object node, BiPredicate<String, Object> predicate) {
        if (node instanceof ObjectNode) {
            for (Map.Entry<String, JsonNode> entry : ((ObjectNode) node).properties()) {
                if (predicate.test(entry.getKey(), entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        throw expected("ObjectNode", node);
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
        throw expected("ObjectNode", node);
    }

    public static void visitArray(Object node, BiConsumer<Integer, Object> visitor) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                visitor.accept(i, an.get(i));
            }
            return;
        }
        throw expected("ArrayNode", node);
    }

    public static boolean anyMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                if (predicate.test(i, an.get(i))) return true;
            }
            return false;
        }
        throw expected("ArrayNode", node);
    }

    public static boolean allMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        if (node instanceof ArrayNode) {
            ArrayNode an = (ArrayNode) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                if (!predicate.test(i, an.get(i))) return false;
            }
            return true;
        }
        throw expected("ArrayNode", node);
    }

    public static Object putInObject(Object node, String key, Object value) {
        throw unsupported("putInObject");
    }

    public static Object setInArray(Object node, int idx, Object value) {
        throw unsupported("setInArray");
    }

    public static void addInArray(Object node, Object value) {
        throw unsupported("addInArray");
    }

    public static void addInArray(Object node, int idx, Object value) {
        throw unsupported("addInArray");
    }

    public static Object removeInObject(Object node, String key) {
        throw unsupported("removeInObject");
    }

    public static Object removeInArray(Object node, int idx) {
        throw unsupported("removeInArray");
    }

    private static JsonException notNode(Object node) {
        return new JsonException("Not a Jackson3 JsonNode, but was '" + Types.name(node) + "'");
    }

    private static JsonException notNode(Class<?> clazz) {
        return new JsonException("Not a Jackson3 JsonNode, but was '" + Types.name(clazz) + "'");
    }

    private static JsonException expected(String expected, Object node) {
        return new JsonException("Expected " + expected + " but was " + Types.name(node));
    }

    private static JsonException unsupported(String method) {
        return new JsonException("'" + method + "' is not supported for `JsonNode` in Jackson3");
    }
}
