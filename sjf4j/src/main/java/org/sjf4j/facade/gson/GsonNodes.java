package org.sjf4j.facade.gson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
 * Gson JsonElement adapter used by {@link org.sjf4j.facade.FacadeNodes}.
 */
public final class GsonNodes {

    private GsonNodes() {}

    /**
     * Returns true when object is a Gson JsonElement.
     */
    public static boolean isNode(Object node) {
        return node instanceof JsonElement;
    }

    /**
     * Returns true when class is a Gson JsonElement type.
     */
    public static boolean isNode(Class<?> clazz) {
        return JsonElement.class.isAssignableFrom(clazz);
    }

    /**
     * Resolves node kind for a Gson JsonElement.
     */
    public static NodeKind kindOf(Object node) {
        if (!isNode(node)) throw notNode(node);
        JsonElement jsonNode = (JsonElement) node;
        if (jsonNode.isJsonNull()) return NodeKind.VALUE_NULL;
        if (jsonNode.isJsonPrimitive()) {
            JsonPrimitive jp = jsonNode.getAsJsonPrimitive();
            if (jp.isString()) return NodeKind.VALUE_STRING_FACADE;
            if (jp.isNumber()) return NodeKind.VALUE_NUMBER_FACADE;
            if (jp.isBoolean()) return NodeKind.VALUE_BOOLEAN_FACADE;
        }
        if (jsonNode.isJsonObject()) return NodeKind.OBJECT_FACADE;
        if (jsonNode.isJsonArray()) return NodeKind.ARRAY_FACADE;
        return NodeKind.UNKNOWN;
    }

    /**
     * Resolves node kind for a Gson node class.
     */
    public static NodeKind kindOf(Class<?> clazz) {
        if (JsonObject.class.isAssignableFrom(clazz)) {
            return NodeKind.OBJECT_FACADE;
        }
        if (JsonArray.class.isAssignableFrom(clazz)) {
            return NodeKind.ARRAY_FACADE;
        }
        if (JsonNull.class.isAssignableFrom(clazz)) {
            return NodeKind.VALUE_NULL;
        }
        return NodeKind.UNKNOWN;
    }

    /**
     * Converts a Gson string primitive to String.
     */
    public static String toString(Object node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isString()) {
            return ((JsonPrimitive) node).getAsString();
        }
        throw expected("JsonPrimitive(String)", node);
    }

    /**
     * Converts a Gson node to String leniently.
     */
    public static String asString(Object node) {
        return ((JsonElement) node).getAsString();
    }

    /**
     * Converts a Gson numeric primitive to Number.
     */
    public static Number toNumber(Object node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isNumber()) {
            return ((JsonPrimitive) node).getAsNumber();
        }
        throw expected("JsonPrimitive(Number)", node);
    }

    /**
     * Converts a Gson node to Number leniently.
     */
    public static Number asNumber(Object node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isNumber()) {
            return ((JsonPrimitive) node).getAsNumber();
        }
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isString()) {
            return Nodes.asNumber(((JsonPrimitive) node).getAsString());
        }
        return null;
    }

    /**
     * Converts a Gson boolean primitive to Boolean.
     */
    public static Boolean toBoolean(Object node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isBoolean()) {
            return ((JsonPrimitive) node).getAsBoolean();
        }
        throw expected("JsonPrimitive(Boolean)", node);
    }

    /**
     * Converts a Gson node to Boolean leniently.
     */
    public static Boolean asBoolean(Object node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isBoolean()) {
            return ((JsonPrimitive) node).getAsBoolean();
        }
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isString()) {
            return Nodes.asBoolean(((JsonPrimitive) node).getAsString());
        }
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isNumber()) {
            return Nodes.asBoolean(((JsonPrimitive) node).getAsNumber());
        }
        return null;
    }

    /**
     * Converts Gson object node to JsonObject.
     */
    public static org.sjf4j.JsonObject toJsonObject(Object node) {
        if (node instanceof JsonObject) {
            org.sjf4j.JsonObject jo = new org.sjf4j.JsonObject();
            for (Map.Entry<String, JsonElement> entry : ((JsonObject) node).entrySet()) {
                jo.put(entry.getKey(), entry.getValue());
            }
            return jo;
        }
        throw expected("JsonObject", node);
    }

    /**
     * Converts Gson object node to Map.
     */
    public static Map<String, Object> toMap(Object node) {
        if (node instanceof JsonObject) {
            JsonObject on = (JsonObject) node;
            Map<String, Object> map = new LinkedHashMap<>(on.size());
            for (Map.Entry<String, JsonElement> entry : on.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            return map;
        }
        throw expected("JsonObject", node);
    }

    /**
     * Converts Gson array node to JsonArray.
     */
    public static org.sjf4j.JsonArray toJsonArray(Object node) {
        if (node instanceof JsonArray) {
            org.sjf4j.JsonArray ja = new org.sjf4j.JsonArray();
            for (int i = 0, size = ((JsonArray) node).size(); i < size; i++) {
                ja.add(((JsonArray) node).get(i));
            }
            return ja;
        }
        throw expected("JsonArray", node);
    }

    /**
     * Converts Gson array node to List.
     */
    public static List<Object> toList(Object node) {
        if (node instanceof JsonArray) {
            JsonArray an = (JsonArray) node;
            List<Object> list = new ArrayList<>(an.size());
            for (int i = 0, size = an.size(); i < size; i++) {
                list.add(an.get(i));
            }
            return list;
        }
        throw expected("JsonArray", node);
    }

    /**
     * Converts Gson array node to Object array.
     */
    public static Object[] toArray(Object node) {
        if (node instanceof JsonArray) {
            JsonArray an = (JsonArray) node;
            Object[] arr = new Object[an.size()];
            for (int i = 0, size = an.size(); i < size; i++) {
                arr[i] = an.get(i);
            }
            return arr;
        }
        throw expected("JsonArray", node);
    }

    /**
     * Converts Gson array node to Set.
     */
    public static Set<Object> toSet(Object node) {
        if (node instanceof JsonArray) {
            JsonArray an = (JsonArray) node;
            Set<Object> set = new LinkedHashSet<>(Math.max((int) (an.size() / 0.75f) + 1, 16));
            for (int i = 0, size = an.size(); i < size; i++) {
                set.add(an.get(i));
            }
            return set;
        }
        throw expected("JsonArray", node);
    }

    /**
     * Returns number of fields in Gson object node.
     */
    public static int sizeInObject(Object node) {
        if (node instanceof JsonObject) {
            return ((JsonObject) node).size();
        }
        throw expected("JsonObject", node);
    }

    /**
     * Returns number of items in Gson array node.
     */
    public static int sizeInArray(Object node) {
        if (node instanceof JsonArray) {
            return ((JsonArray) node).size();
        }
        throw expected("JsonArray", node);
    }

    /**
     * Returns keys from Gson object node.
     */
    public static Set<String> keySetInObject(Object node) {
        if (node instanceof JsonObject) {
            Set<String> set = new LinkedHashSet<>(((JsonObject) node).size());
            set.addAll(((JsonObject) node).keySet());
            return set;
        }
        throw expected("JsonObject", node);
    }

    /**
     * Returns entries from Gson object node.
     */
    public static Set<Map.Entry<String, Object>> entrySetInObject(Object node) {
        if (node instanceof JsonObject) {
            JsonObject on = (JsonObject) node;
            Set<Map.Entry<String, Object>> out = new LinkedHashSet<>(on.size());
            for (Map.Entry<String, JsonElement> e : on.entrySet()) {
                out.add(new AbstractMap.SimpleImmutableEntry<String, Object>(e.getKey(), e.getValue()));
            }
            return out;
        }
        throw expected("JsonObject", node);
    }

    /**
     * Returns iterator over Gson array node.
     */
    public static Iterator<Object> iteratorInArray(Object node) {
        if (node instanceof JsonArray) {
            final Iterator<JsonElement> it = ((JsonArray) node).iterator();
            return new Iterator<Object>() {
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Object next() { return it.next(); }
                @Override public void remove() { it.remove(); }
            };
        }
        throw expected("JsonArray", node);
    }

    /**
     * Returns true when key exists in Gson object node.
     */
    public static boolean containsInObject(Object node, String key) {
        if (node instanceof JsonObject) {
            return ((JsonObject) node).get(key) != null;
        }
        throw expected("JsonObject", node);
    }

    /**
     * Returns object field value from Gson object node.
     */
    public static Object getInObject(Object node, String key) {
        if (node instanceof JsonObject) {
            return ((JsonObject) node).get(key);
        }
        throw expected("JsonObject", node);
    }

    /**
     * Returns array item value from Gson array node.
     */
    public static Object getInArray(Object node, int idx) {
        if (node instanceof JsonArray) {
            return ((JsonArray) node).get(idx);
        }
        throw expected("JsonArray", node);
    }

    /**
     * Resolves child access info for Gson object node.
     */
    public static void accessInObject(Object node, Type type, String key, Nodes.Access out) {
        if (node instanceof JsonObject) {
            out.node = ((JsonObject) node).get(key);
            out.type = JsonElement.class;
            out.insertable = false;
            return;
        }
        throw expected("JsonObject", node);
    }

    /**
     * Resolves child access info for Gson array node.
     */
    public static void accessInArray(Object node, Type type, int idx, Nodes.Access out) {
        if (node instanceof JsonArray) {
            out.node = ((JsonArray) node).get(idx);
            out.type = JsonElement.class;
            out.insertable = false;
            return;
        }
        throw expected("JsonArray", node);
    }

    /**
     * Visits fields of Gson object node.
     */
    public static void visitObject(Object node, BiConsumer<String, Object> visitor) {
        if (node instanceof JsonObject) {
            for (Map.Entry<String, JsonElement> entry : ((JsonObject) node).entrySet()) {
                visitor.accept(entry.getKey(), entry.getValue());
            }
            return;
        }
        throw expected("JsonObject", node);
    }

    public static boolean anyMatchInObject(Object node, BiPredicate<String, Object> predicate) {
        if (node instanceof JsonObject) {
            for (Map.Entry<String, JsonElement> entry : ((JsonObject) node).entrySet()) {
                if (predicate.test(entry.getKey(), entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        throw expected("JsonObject", node);
    }

    public static boolean transformInObject(Object node, BiFunction<String, Object, Object> mapper) {
        if (node instanceof JsonObject) {
            boolean changed = false;
            for (Map.Entry<String, JsonElement> entry : ((JsonObject) node).entrySet()) {
                JsonElement oldValue = entry.getValue();
                Object newValue = mapper.apply(entry.getKey(), oldValue);
                if (newValue != oldValue) {
                    entry.setValue((JsonElement) newValue);
                    changed = true;
                }
            }
            return changed;
        }
        throw expected("JsonObject", node);
    }

    /**
     * Visits items of Gson array node.
     */
    public static void visitArray(Object node, BiConsumer<Integer, Object> visitor) {
        if (node instanceof JsonArray) {
            JsonArray an = (JsonArray) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                visitor.accept(i, an.get(i));
            }
            return;
        }
        throw expected("JsonArray", node);
    }

    /**
     * Returns true when any item matches predicate.
     */
    public static boolean anyMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        if (node instanceof JsonArray) {
            JsonArray an = (JsonArray) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                if (predicate.test(i, an.get(i))) return true;
            }
            return false;
        }
        throw expected("JsonArray", node);
    }

    /**
     * Returns true when all items match predicate.
     */
    public static boolean allMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        if (node instanceof JsonArray) {
            JsonArray an = (JsonArray) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                if (!predicate.test(i, an.get(i))) return false;
            }
            return true;
        }
        throw expected("JsonArray", node);
    }

    /**
     * Mutation is unsupported for Gson JsonElement facade.
     */
    public static Object putInObject(Object node, String key, Object value) {
        throw unsupported("putInObject");
    }

    /**
     * Mutation is unsupported for Gson JsonElement facade.
     */
    public static Object setInArray(Object node, int idx, Object value) {
        throw unsupported("setInArray");
    }

    /**
     * Mutation is unsupported for Gson JsonElement facade.
     */
    public static void addInArray(Object node, Object value) {
        throw unsupported("addInArray");
    }

    /**
     * Mutation is unsupported for Gson JsonElement facade.
     */
    public static void addInArray(Object node, int idx, Object value) {
        throw unsupported("addInArray");
    }

    /**
     * Mutation is unsupported for Gson JsonElement facade.
     */
    public static Object removeInObject(Object node, String key) {
        throw unsupported("removeInObject");
    }

    /**
     * Mutation is unsupported for Gson JsonElement facade.
     */
    public static Object removeInArray(Object node, int idx) {
        throw unsupported("removeInArray");
    }

    private static JsonException notNode(Object node) {
        return new JsonException("Not a Gson's JsonElement, but was '" + Types.name(node) + "'");
    }

    private static JsonException expected(String expected, Object node) {
        return new JsonException("Expected " + expected + " but was " + Types.name(node));
    }

    private static JsonException unsupported(String method) {
        return new JsonException("'" + method + "' is not supported for `JsonElement` in Gson");
    }
}
