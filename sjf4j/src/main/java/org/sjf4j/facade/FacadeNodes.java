package org.sjf4j.facade;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.gson.GsonNodes;
import org.sjf4j.facade.jackson2.Jackson2Nodes;
import org.sjf4j.facade.jackson3.Jackson3Nodes;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.Types;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Bridge for external JSON node trees (Jackson/Gson).
 */
public class FacadeNodes {

    private static final boolean JACKSON3_NODES_PRESENT;
    private static final boolean JACKSON2_NODES_PRESENT;
    private static final boolean GSON_NODES_PRESENT;

    static {
        boolean jackson3NodesPresent = false;
        try {
            Class.forName("tools.jackson.databind.JsonNode");
            jackson3NodesPresent = true;
        } catch (Throwable ignored) {}
        JACKSON3_NODES_PRESENT = jackson3NodesPresent;

        boolean jackson2NodesPresent = false;
        try {
            Class.forName("com.fasterxml.jackson.databind.JsonNode");
            jackson2NodesPresent = true;
        } catch (Throwable ignored) {}
        JACKSON2_NODES_PRESENT = jackson2NodesPresent;

        boolean gsonNodesPresent = false;
        try {
            Class.forName("com.google.gson.JsonElement");
            gsonNodesPresent = true;
        } catch (Throwable ignored) {}
        GSON_NODES_PRESENT = gsonNodesPresent;
    }

    private FacadeNodes() {}

    /**
     * Returns true when Jackson 3.x node classes are available.
     */
    public static boolean isJackson3NodesPresent() {
        return JACKSON3_NODES_PRESENT;
    }

    /**
     * Returns true when Jackson 2.x node classes are available.
     */
    public static boolean isJackson2NodesPresent() {
        return JACKSON2_NODES_PRESENT;
    }

    /**
     * Returns true when Gson node classes are available.
     */
    public static boolean isGsonNodesPresent() {
        return GSON_NODES_PRESENT;
    }

    /**
     * Returns true when object is a supported external node.
     */
    public static boolean isNode(Object node) {
        return (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) ||
                (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) ||
                (GSON_NODES_PRESENT && GsonNodes.isNode(node));
    }

    /**
     * Returns true when class is a supported external node type.
     */
    public static boolean isNode(Class<?> clazz) {
        return (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(clazz)) ||
                (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(clazz)) ||
                (GSON_NODES_PRESENT && GsonNodes.isNode(clazz));
    }

    /**
     * Resolves node kind for a supported external node.
     */
    public static NodeKind kindOf(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.kindOf(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.kindOf(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.kindOf(node);
        throw _unknownNode(node);
    }

    /**
     * Resolves node kind for a supported external node class.
     */
    public static NodeKind kindOf(Class<?> clazz) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(clazz)) return Jackson3Nodes.kindOf(clazz);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(clazz)) return Jackson2Nodes.kindOf(clazz);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(clazz)) return GsonNodes.kindOf(clazz);
        throw _unknownNode(clazz);
    }

//    public static Object copy(Object node) {
//        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.deepCopy(node);
//        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.deepCopy(node);
//        return null;
//    }

    /**
     * Converts external node to string using strict conversion.
     */
    public static String toString(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.toString(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.toString(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toString(node);
        throw _unknownNode(node);
    }

    /**
     * Converts external node to string using lenient conversion.
     */
    public static String asString(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.asString(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.asString(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.asString(node);
        throw _unknownNode(node);
    }

    /**
     * Converts external node to number using strict conversion.
     */
    public static Number toNumber(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.toNumber(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.toNumber(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toNumber(node);
        throw _unknownNode(node);
    }

    /**
     * Converts external node to number using lenient conversion.
     */
    public static Number asNumber(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.asNumber(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.asNumber(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.asNumber(node);
        throw _unknownNode(node);
    }

    /**
     * Converts external node to boolean using strict conversion.
     */
    public static Boolean toBoolean(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.toBoolean(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.toBoolean(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toBoolean(node);
        throw _unknownNode(node);
    }

    /**
     * Converts external node to boolean using lenient conversion.
     */
    public static Boolean asBoolean(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.asBoolean(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.asBoolean(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.asBoolean(node);
        throw _unknownNode(node);
    }

    /**
     * Converts external node to JsonObject.
     */
    public static JsonObject toJsonObject(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.toJsonObject(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.toJsonObject(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toJsonObject(node);
        throw _unknownNode(node);
    }

    /**
     * Converts external node to Map.
     */
    public static Map<String, Object> toMap(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.toMap(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.toMap(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toMap(node);
        throw _unknownNode(node);
    }

    /**
     * Converts external node to JsonArray.
     */
    public static JsonArray toJsonArray(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.toJsonArray(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.toJsonArray(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toJsonArray(node);
        throw _unknownNode(node);
    }

    /**
     * Converts external node to List.
     */
    public static List<Object> toList(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.toList(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.toList(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toList(node);
        throw _unknownNode(node);
    }

    /**
     * Converts external node to Object array.
     */
    public static Object[] toArray(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.toArray(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.toArray(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toArray(node);
        throw _unknownNode(node);
    }

    /**
     * Converts external node to Set.
     */
    public static Set<Object> toSet(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.toSet(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.toSet(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.toSet(node);
        throw _unknownNode(node);
    }

    /**
     * Visits object members of an external node.
     */
    public static void forEachObject(Object node, BiConsumer<String, Object> consumer) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            Jackson3Nodes.forEachObject(node, consumer);
            return;
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            Jackson2Nodes.forEachObject(node, consumer);
            return;
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            GsonNodes.forEachObject(node, consumer);
            return;
        }
        throw _unknownNode(node);
    }

    public static boolean anyMatchObject(Object node, BiPredicate<String, Object> predicate) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            return Jackson3Nodes.anyMatchObject(node, predicate);
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            return Jackson2Nodes.anyMatchObject(node, predicate);
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            return GsonNodes.anyMatchObject(node, predicate);
        }
        throw _unknownNode(node);
    }

    public static boolean replaceInObject(Object node, BiFunction<String, Object, Object> replacer) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            return Jackson3Nodes.replaceInObject(node, replacer);
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            return Jackson2Nodes.replaceInObject(node, replacer);
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            return GsonNodes.replaceInObject(node, replacer);
        }
        throw _unknownNode(node);
    }

    /**
     * Visits array elements of an external node.
     */
    public static void forEachArray(Object node, BiConsumer<Integer, Object> consumer) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            Jackson3Nodes.forEachArray(node, consumer);
            return;
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            Jackson2Nodes.forEachArray(node, consumer);
            return;
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            GsonNodes.forEachArray(node, consumer);
            return;
        }
        throw _unknownNode(node);
    }

    /**
     * Returns true when any array element matches predicate.
     */
    public static boolean anyMatchArray(Object node, BiPredicate<Integer, Object> predicate) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            return Jackson3Nodes.anyMatchArray(node, predicate);
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            return Jackson2Nodes.anyMatchArray(node, predicate);
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            return GsonNodes.anyMatchArray(node, predicate);
        }
        throw _unknownNode(node);
    }

    /**
     * Returns number of object entries in external node.
     */
    public static int sizeInObject(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.sizeInObject(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.sizeInObject(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.sizeInObject(node);
        throw _unknownNode(node);
    }

    /**
     * Returns number of array entries in external node.
     */
    public static int sizeInArray(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.sizeInArray(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.sizeInArray(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.sizeInArray(node);
        throw _unknownNode(node);
    }

    /**
     * Returns object keys from external node.
     */
    public static Set<String> keySetInObject(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.keySetInObject(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.keySetInObject(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.keySetInObject(node);
        throw _unknownNode(node);
    }

    /**
     * Returns object entries from external node.
     */
    public static Set<Map.Entry<String, Object>> entrySetInObject(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.entrySetInObject(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.entrySetInObject(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.entrySetInObject(node);
        throw _unknownNode(node);
    }

    /**
     * Returns array iterator from external node.
     */
    public static Iterator<Object> iteratorInArray(Object node) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.iteratorInArray(node);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.iteratorInArray(node);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.iteratorInArray(node);
        throw _unknownNode(node);
    }

    /**
     * Returns true when object key exists in external node.
     */
    public static boolean containsInObject(Object node, String key) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.containsInObject(node, key);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.containsInObject(node, key);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.containsInObject(node, key);
        throw _unknownNode(node);
    }

    /**
     * Returns object value by key from external node.
     */
    public static Object getInObject(Object node, String key) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.getInObject(node, key);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.getInObject(node, key);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.getInObject(node, key);
        throw _unknownNode(node);
    }

    /**
     * Returns array value by index from external node.
     */
    public static Object getInArray(Object node, int idx) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) return Jackson3Nodes.getInArray(node, idx);
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.getInArray(node, idx);
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.getInArray(node, idx);
        throw _unknownNode(node);
    }

//    public static Object putInObject(Object node, String key, Object value) {
//        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.putInObject(node, key, value);
//        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.putInObject(node, key, value);
//        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
//    }
//
//    public static Object setInArray(Object node, int idx, Object value) {
//        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.setInArray(node, idx, value);
//        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.setInArray(node, idx, value);
//        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
//    }
//
//    public static void addInArray(Object node, Object value) {
//        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
//            Jackson2Nodes.addInArray(node, value);
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
//        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
//            Jackson2Nodes.addInArray(node, idx, value);
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
//        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.removeInObject(node, key);
//        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.removeInObject(node, key);
//        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
//    }
//
//    public static Object removeInArray(Object node, int idx) {
//        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) return Jackson2Nodes.removeInArray(node, idx);
//        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) return GsonNodes.removeInArray(node, idx);
//        throw new JsonException("Unknown node type '" + Types.name(node) + "'");
//    }

    /**
     * Resolves child access info for object node.
     */
    public static void accessInObject(Object node, Type type, String key, Nodes.Access out) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            Jackson3Nodes.accessInObject(node, type, key, out);
            return;
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            Jackson2Nodes.accessInObject(node, type, key, out);
            return;
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            GsonNodes.accessInObject(node, type, key, out);
            return;
        }
        throw _unknownNode(node);
    }

    /**
     * Resolves child access info for array node.
     */
    public static void accessInArray(Object node, Type type, Integer idx, Nodes.Access out) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            Jackson3Nodes.accessInArray(node, type, idx, out);
            return;
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            Jackson2Nodes.accessInArray(node, type, idx, out);
            return;
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            GsonNodes.accessInArray(node, type, idx, out);
            return;
        }
        throw _unknownNode(node);
    }

    /**
     * Puts object value in external node.
     */
    public static Object putInObject(Object node, String key, Object value) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            return Jackson3Nodes.putInObject(node, key, value);
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            return Jackson2Nodes.putInObject(node, key, value);
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            return GsonNodes.putInObject(node, key, value);
        }
        throw _unknownNode(node);
    }

    /**
     * Sets array value in external node.
     */
    public static Object setInArray(Object node, int idx, Object value) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            return Jackson3Nodes.setInArray(node, idx, value);
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            return Jackson2Nodes.setInArray(node, idx, value);
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            return GsonNodes.setInArray(node, idx, value);
        }
        throw _unknownNode(node);
    }

    /**
     * Appends value to array external node.
     */
    public static void addInArray(Object node, Object value) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            Jackson3Nodes.addInArray(node, value);
            return;
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            Jackson2Nodes.addInArray(node, value);
            return;
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            GsonNodes.addInArray(node, value);
            return;
        }
        throw _unknownNode(node);
    }

    /**
     * Inserts value at index in array external node.
     */
    public static void addInArray(Object node, int idx, Object value) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            Jackson3Nodes.addInArray(node, idx, value);
            return;
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            Jackson2Nodes.addInArray(node, idx, value);
            return;
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            GsonNodes.addInArray(node, idx, value);
            return;
        }
        throw _unknownNode(node);
    }

    /**
     * Removes object value by key in external node.
     */
    public static Object removeInObject(Object node, String key) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            return Jackson3Nodes.removeInObject(node, key);
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            return Jackson2Nodes.removeInObject(node, key);
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            return GsonNodes.removeInObject(node, key);
        }
        throw _unknownNode(node);
    }

    /**
     * Removes array value by index in external node.
     */
    public static Object removeInArray(Object node, int idx) {
        if (JACKSON3_NODES_PRESENT && Jackson3Nodes.isNode(node)) {
            return Jackson3Nodes.removeInArray(node, idx);
        }
        if (JACKSON2_NODES_PRESENT && Jackson2Nodes.isNode(node)) {
            return Jackson2Nodes.removeInArray(node, idx);
        }
        if (GSON_NODES_PRESENT && GsonNodes.isNode(node)) {
            return GsonNodes.removeInArray(node, idx);
        }
        throw _unknownNode(node);
    }

    private static JsonException _unknownNode(Object node) {
        return new JsonException("Unknown node type '" + Types.name(node) + "'");
    }

    private static JsonException _unknownNode(Class<?> clazz) {
        return new JsonException("Unknown node type '" + clazz.getName() + "'");
    }

}
