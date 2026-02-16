package org.sjf4j.facade.gson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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

public final class GsonNodes {

    private GsonNodes() {}

    public static boolean isNode(Object node) {
        return node instanceof JsonElement;
    }

    public static boolean isNode(Class<?> clazz) {
        return JsonElement.class.isAssignableFrom(clazz);
    }

    public static NodeKind kindOf(Object node) {
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

    public static NodeKind kindOf(Class<?> clazz) {
        if (JsonObject.class.isAssignableFrom(clazz)) {
            return NodeKind.OBJECT_FACADE;
        }
        if (JsonArray.class.isAssignableFrom(clazz)) {
            return NodeKind.ARRAY_FACADE;
        }
        return NodeKind.UNKNOWN;
    }

    public static String toString(Object node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isString()) {
            return ((JsonPrimitive) node).getAsString();
        }
        throw new JsonException("Expected JsonPrimitive(String) but was " + Types.name(node));
    }

    public static String asString(Object node) {
        return ((JsonElement) node).getAsString();
    }

    public static Number toNumber(Object node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isNumber()) {
            return ((JsonPrimitive) node).getAsNumber();
        }
        throw new JsonException("Expected JsonPrimitive(Number) but was " + Types.name(node));
    }

    public static Number asNumber(Object node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isNumber()) {
            return ((JsonPrimitive) node).getAsNumber();
        }
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isString()) {
            return Nodes.asNumber(((JsonPrimitive) node).getAsString());
        }
        return null;
    }

    public static Boolean toBoolean(Object node) {
        if (node instanceof JsonPrimitive && ((JsonPrimitive) node).isBoolean()) {
            return ((JsonPrimitive) node).getAsBoolean();
        }
        throw new JsonException("Expected JsonPrimitive(Boolean) but was " + Types.name(node));
    }

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

    public static org.sjf4j.JsonObject toJsonObject(Object node) {
        if (node instanceof JsonObject) {
            org.sjf4j.JsonObject jo = new org.sjf4j.JsonObject();
            for (Map.Entry<String, JsonElement> entry : ((JsonObject) node).entrySet()) {
                jo.put(entry.getKey(), entry.getValue());
            }
            return jo;
        }
        throw new JsonException("Expected JsonObject but was " + Types.name(node));
    }

    public static Map<String, Object> toMap(Object node) {
        if (node instanceof JsonObject) {
            JsonObject on = (JsonObject) node;
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(on.size());
            for (Map.Entry<String, JsonElement> entry : on.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            return map;
        }
        throw new JsonException("Expected JsonObject but was " + Types.name(node));
    }

    public static org.sjf4j.JsonArray toJsonArray(Object node) {
        if (node instanceof JsonArray) {
            org.sjf4j.JsonArray ja = new org.sjf4j.JsonArray();
            for (int i = 0, size = ((JsonArray) node).size(); i < size; i++) {
                ja.add(((JsonArray) node).get(i));
            }
            return ja;
        }
        throw new JsonException("Expected JsonArray but was " + Types.name(node));
    }

    public static List<Object> toList(Object node) {
        if (node instanceof JsonArray) {
            JsonArray an = (JsonArray) node;
            List<Object> list = Sjf4jConfig.global().listSupplier.create(an.size());
            for (int i = 0, size = an.size(); i < size; i++) {
                list.add(an.get(i));
            }
            return list;
        }
        throw new JsonException("Expected JsonArray but was " + Types.name(node));
    }

    public static Object[] toArray(Object node) {
        if (node instanceof JsonArray) {
            JsonArray an = (JsonArray) node;
            Object[] arr = new Object[an.size()];
            for (int i = 0, size = an.size(); i < size; i++) {
                arr[i] = an.get(i);
            }
            return arr;
        }
        throw new JsonException("Expected JsonArray but was " + Types.name(node));
    }

    public static Set<Object> toSet(Object node) {
        if (node instanceof JsonArray) {
            JsonArray an = (JsonArray) node;
            Set<Object> set = Sjf4jConfig.global().setSupplier.create(an.size());
            for (int i = 0, size = an.size(); i < size; i++) {
                set.add(an.get(i));
            }
            return set;
        }
        throw new JsonException("Expected JsonArray but was " + Types.name(node));
    }

    public static int sizeInObject(Object node) {
        if (node instanceof JsonObject) {
            return ((JsonObject) node).size();
        }
        throw new JsonException("Expected JsonObject but was " + Types.name(node));
    }

    public static int sizeInArray(Object node) {
        if (node instanceof JsonArray) {
            return ((JsonArray) node).size();
        }
        throw new JsonException("Expected JsonArray but was " + Types.name(node));
    }

    public static Set<String> keySetInObject(Object node) {
        if (node instanceof JsonObject) {
            Set<String> set = new LinkedHashSet<>(((JsonObject) node).size());
            set.addAll(((JsonObject) node).keySet());
            return set;
        }
        throw new JsonException("Expected JsonObject but was " + Types.name(node));
    }

    public static Set<Map.Entry<String, Object>> entrySetInObject(Object node) {
        if (node instanceof JsonObject) {
            JsonObject on = (JsonObject) node;
            Set<Map.Entry<String, Object>> out = new LinkedHashSet<>(on.size());
            for (Map.Entry<String, JsonElement> e : on.entrySet()) {
                out.add(new AbstractMap.SimpleImmutableEntry<String, Object>(e.getKey(), e.getValue()));
            }
            return out;
        }
        throw new JsonException("Expected JsonObject but was " + Types.name(node));
    }

    public static Iterator<Object> iteratorInArray(Object node) {
        if (node instanceof JsonArray) {
            final Iterator<JsonElement> it = ((JsonArray) node).iterator();
            return new Iterator<Object>() {
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Object next() { return it.next(); }
                @Override public void remove() { it.remove(); }
            };
        }
        throw new JsonException("Expected JsonArray but was " + Types.name(node));
    }

    public static boolean containsInObject(Object node, String key) {
        if (node instanceof JsonObject) {
            return ((JsonObject) node).get(key) != null;
        }
        throw new JsonException("Expected JsonObject but was " + Types.name(node));
    }

    public static Object getInObject(Object node, String key) {
        if (node instanceof JsonObject) {
            return ((JsonObject) node).get(key);
        }
        throw new JsonException("Expected JsonObject but was " + Types.name(node));
    }

    public static Object getInArray(Object node, int idx) {
        if (node instanceof JsonArray) {
            return ((JsonArray) node).get(idx);
        }
        throw new JsonException("Expected JsonArray but was " + Types.name(node));
    }

    public static void accessInObject(Object node, Type type, String key, Nodes.Access out) {
        if (node instanceof JsonObject) {
            out.node = ((JsonObject) node).get(key);
            out.type = JsonElement.class;
            out.insertable = false;
            return;
        }
        throw new JsonException("Expected JsonObject but was " + Types.name(node));
    }

    public static void accessInArray(Object node, Type type, int idx, Nodes.Access out) {
        if (node instanceof JsonArray) {
            out.node = ((JsonArray) node).get(idx);
            out.type = JsonElement.class;
            out.insertable = false;
            return;
        }
        throw new JsonException("Expected JsonArray but was " + Types.name(node));
    }

    public static void visitObject(Object node, BiConsumer<String, Object> visitor) {
        if (node instanceof JsonObject) {
            for (Map.Entry<String, JsonElement> entry : ((JsonObject) node).entrySet()) {
                visitor.accept(entry.getKey(), entry.getValue());
            }
            return;
        }
        throw new JsonException("Expected JsonObject but was " + Types.name(node));
    }

    public static void visitArray(Object node, BiConsumer<Integer, Object> visitor) {
        if (node instanceof JsonArray) {
            JsonArray an = (JsonArray) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                visitor.accept(i, an.get(i));
            }
            return;
        }
        throw new JsonException("Expected JsonArray but was " + Types.name(node));
    }

    public static boolean anyMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        if (node instanceof JsonArray) {
            JsonArray an = (JsonArray) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                if (predicate.test(i, an.get(i))) return true;
            }
            return false;
        }
        throw new JsonException("Expected JsonArray but was " + Types.name(node));
    }

    public static boolean allMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        if (node instanceof JsonArray) {
            JsonArray an = (JsonArray) node;
            for (int i = 0, size = an.size(); i < size; i++) {
                if (!predicate.test(i, an.get(i))) return false;
            }
            return true;
        }
        throw new JsonException("Expected JsonArray but was " + Types.name(node));
    }

    public static Object putInObject(Object node, String key, Object value) {
        throw new JsonException("'putInObject' is not supported for `JsonElement` in Gson");
    }

    public static Object setInArray(Object node, int idx, Object value) {
        throw new JsonException("'setInArray' is not supported for `JsonElement` in Gson");
    }

    public static void addInArray(Object node, Object value) {
        throw new JsonException("'addInArray' is not supported for `JsonElement` in Gson");
    }

    public static void addInArray(Object node, int idx, Object value) {
        throw new JsonException("'addInArray' is not supported for `JsonElement` in Gson");
    }

    public static Object removeInObject(Object node, String key) {
        throw new JsonException("'removeInObject' is not supported for `JsonElement` in Gson");
    }

    public static Object removeInArray(Object node, int idx) {
        throw new JsonException("'removeInArray' is not supported for `JsonElement` in Gson");
    }
}
