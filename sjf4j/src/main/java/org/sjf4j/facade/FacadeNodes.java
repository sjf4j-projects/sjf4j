package org.sjf4j.facade;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.gson.GsonNodes;
import org.sjf4j.facade.jackson.JacksonNodes;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.Types;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public class FacadeNodes {

    private static final boolean JACKSON_NODES_PRESENT;
    private static final boolean GSON_NODES_PRESENT;

    static {
        boolean jacksonNodesPresent = false;
        try {
            Class.forName("com.fasterxml.jackson.databind.JsonNode");
            jacksonNodesPresent = true;
        } catch (Throwable ignored) {}

        boolean gsonNodesPresent = false;
        try {
            Class.forName("com.google.gson.JsonElement");
            gsonNodesPresent = true;
        } catch (Throwable ignored) {}

        JACKSON_NODES_PRESENT = jacksonNodesPresent;
        GSON_NODES_PRESENT = gsonNodesPresent;
    }

    private FacadeNodes() {}

    public static boolean isJacksonNodesPresent() {
        return JACKSON_NODES_PRESENT;
    }

    public static boolean isGsonNodesPresent() {
        return GSON_NODES_PRESENT;
    }

    public static boolean isNode(Object node) {
        return (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) ||
                (GSON_NODES_PRESENT && GsonNodes.isNode(node));
    }

    public static boolean isNode(Class<?> clazz) {
        return (JACKSON_NODES_PRESENT && JacksonNodes.isNode(clazz)) ||
                (GSON_NODES_PRESENT && GsonNodes.isNode(clazz));
    }

    public static NodeKind kindOf(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.kindOf(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.kindOf(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static NodeKind kindOf(Class<?> clazz) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(clazz)) return JacksonNodes.kindOf(clazz);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(clazz)) return GsonNodes.kindOf(clazz);
        throw new JsonException("Unknown node type '" + clazz.getName() + "'");
    }

//    public static Object copy(Object node) {
//        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.deepCopy(node);
//        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.deepCopy(node);
//        return null;
//    }

    public static String toString(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.toString(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toString(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static String asString(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.asString(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.asString(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static Number toNumber(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.toNumber(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toNumber(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static Number asNumber(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.asNumber(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.asNumber(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static Boolean toBoolean(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.toBoolean(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toBoolean(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static Boolean asBoolean(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.asBoolean(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.asBoolean(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static JsonObject toJsonObject(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.toJsonObject(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toJsonObject(node);
        return null;
    }

    public static Map<String, Object> toMap(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.toMap(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toMap(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static JsonArray toJsonArray(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.toJsonArray(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toJsonArray(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static List<Object> toList(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.toList(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toList(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static Object[] toArray(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.toArray(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toArray(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static Set<Object> toSet(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.toSet(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toSet(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static void visitObject(Object node, BiConsumer<String, Object> visitor) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) {
            JacksonNodes.visitObject(node, visitor);
            return;
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            GsonNodes.visitObject(node, visitor);
            return;
        }
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static void visitArray(Object node, BiConsumer<Integer, Object> visitor) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) {
            JacksonNodes.visitArray(node, visitor);
            return;
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            GsonNodes.visitArray(node, visitor);
            return;
        }
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static boolean anyMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) {
            return JacksonNodes.anyMatchInArray(node, predicate);
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            return GsonNodes.anyMatchInArray(node, predicate);
        }
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static boolean allMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) {
            return JacksonNodes.allMatchInArray(node, predicate);
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            return GsonNodes.allMatchInArray(node, predicate);
        }
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static int sizeInObject(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.sizeInObject(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.sizeInObject(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static int sizeInArray(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.sizeInArray(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.sizeInArray(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static Set<String> keySetInObject(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.keySetInObject(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.keySetInObject(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static Set<Map.Entry<String, Object>> entrySetInObject(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.entrySetInObject(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.entrySetInObject(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static Iterator<Object> iteratorInArray(Object node) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.iteratorInArray(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.iteratorInArray(node);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static boolean containsInObject(Object node, String key) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.containsInObject(node, key);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.containsInObject(node, key);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static Object getInObject(Object node, String key) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.getInObject(node, key);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.getInObject(node, key);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static Object getInArray(Object node, int idx) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.getInArray(node, idx);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.getInArray(node, idx);
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

//    public static Object putInObject(Object node, String key, Object value) {
//        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.putInObject(node, key, value);
//        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.putInObject(node, key, value);
//        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
//    }
//
//    public static Object setInArray(Object node, int idx, Object value) {
//        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.setInArray(node, idx, value);
//        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.setInArray(node, idx, value);
//        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
//    }
//
//    public static void addInArray(Object node, Object value) {
//        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) {
//            JacksonNodes.addInArray(node, value);
//            return;
//        }
//        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
//            GsonNodes.addInArray(node, value);
//            return;
//        }
//        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
//    }
//
//    public static void addInArray(Object node, int idx, Object value) {
//        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) {
//            JacksonNodes.addInArray(node, idx, value);
//            return;
//        }
//        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
//            GsonNodes.addInArray(node, idx, value);
//            return;
//        }
//        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
//    }
//
//    public static Object removeInObject(Object node, String key) {
//        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.removeInObject(node, key);
//        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.removeInObject(node, key);
//        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
//    }
//
//    public static Object removeInArray(Object node, int idx) {
//        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) return JacksonNodes.removeInArray(node, idx);
//        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.removeInArray(node, idx);
//        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
//    }

    public static void accessInObject(Object node, Type type, String key, Nodes.Access out) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) {
            JacksonNodes.accessInObject(node, type, key, out);
            return;
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            GsonNodes.accessInObject(node, type, key, out);
            return;
        }
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    public static void accessInArray(Object node, Type type, int idx, Nodes.Access out) {
        if (JACKSON_NODES_PRESENT && JacksonNodes.isNode(node)) {
            JacksonNodes.accessInArray(node, type, idx, out);
            return;
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            GsonNodes.accessInArray(node, type, idx, out);
            return;
        }
        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
    }


}
