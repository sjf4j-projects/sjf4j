package org.sjf4j.util;

import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4j;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.NodeType;
import org.sjf4j.node.NodeWalker;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Utility class for handling JSON node operations and type conversions.
 * 
 * <p>This class provides a set of static methods for safely converting between different
 * JSON node types, with strict type checking and appropriate exception handling. It
 * distinguishes between "toXxx" methods (which perform strict type checking) and "asXxx"
 * methods (which perform more flexible conversions).
 */
public class NodeUtil {

    /// Type-safe access and cross-type conversion

    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> E toEnum(Object node, Class<E> enumClazz) {
        if (node == null) {
            return null;
        } else if (enumClazz.isInstance(node)) {
            return (E) node;
        } else if (node instanceof String) {
            return Enum.valueOf(enumClazz, (String) node);
        } else if (node.getClass().isEnum()) {
            String s = ((Enum<?>) node).name();
            return Enum.valueOf(enumClazz, s);
        }
        throw new JsonException("Expected String or Enum for " + enumClazz.getName() + ", but got " +
                TypeUtil.nameOf(node));
    }

    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> E asEnum(Object node, Class<E> enumClazz) {
        if (node == null) {
            return null;
        } else if (enumClazz.isInstance(node)) {
            return (E) node;
        } else {
            String s = asString(node);
            return Enum.valueOf(enumClazz, s);
        }
    }

    /**
     * Converts a node to a String with strict type checking.
     *
     * @param node the node to convert
     * @return the string representation
     * @throws JsonException if the node is not a String, Character, or Enum
     */
    public static String toString(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof String || node instanceof Character) {
            return node.toString();
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).name();
        }
        throw new JsonException("Expected String or Character or Enum, but got " + TypeUtil.nameOf(node));
    }

    /**
     * Converts a node to a String with flexible type conversion.
     *
     * @param node the node to convert
     * @return the string representation
     */
    public static String asString(Object node) {
        if (node == null) {
            return null;
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).name();
        }
        return node.toString();
    }

    public static Character toCharacter(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Character) {
            return (Character) node;
        } else if (node instanceof String) {
            String s = (String) node;
            if (s.length() == 1) return s.charAt(0);
        } else if (node.getClass().isEnum()) {
            String s = ((Enum<?>) node).name();
            if (s.length() == 1) return s.charAt(0);
        }
        throw new JsonException("Expected Character or single-character String, but got " + TypeUtil.nameOf(node));
    }

    public static Character asCharacter(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Character) {
            return (Character) node;
        } else {
            String s = asString(node);
            if (s.length() > 0) return s.charAt(0);
        }
        throw new JsonException("Cannot convert " + TypeUtil.nameOf(node) + " to Character"
        );
    }

    /**
     * Converts a node to a Number with strict type checking.
     *
     * @param node the node to convert
     * @return the number representation
     * @throws JsonException if the node is not a Number
     */
    public static Number toNumber(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return (Number) node;
        }
        throw new JsonException("Expected Number, but got " + TypeUtil.nameOf(node));
    }

    /**
     * Converts a node to a Number with flexible type conversion.
     *
     * @param node the node to convert
     * @return the number representation
     * @throws JsonException if the node cannot be converted to a Number
     */
    public static Number asNumber(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return (Number) node;
        } else if (node instanceof CharSequence || node instanceof Character) {
            String str = node.toString();
            if (NumberUtil.isNumeric(str)) {
                return NumberUtil.toNumber(node.toString());
            }
            throw new JsonException("Cannot convert String to Number: not a numeric value");
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).ordinal();
        }
        throw new JsonException("Cannot convert " + TypeUtil.nameOf(node) + " to Number");
    }


    /**
     * Converts a node to a Long with strict type checking.
     *
     * @param node the node to convert
     * @return the Long representation
     * @throws JsonException if the node is not a Number
     */
    public static Long toLong(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asLong((Number) node);
        }
        throw new JsonException("Expected Number, but got " + TypeUtil.nameOf(node));
    }

    /**
     * Converts a node to a Long with flexible type conversion.
     *
     * @param node the node to convert
     * @return the Long representation
     */
    public static Long asLong(Object node) {
        return NumberUtil.asLong(asNumber(node));
    }

    /**
     * Converts a node to an Integer with strict type checking.
     *
     * @param node the node to convert
     * @return the Integer representation
     * @throws JsonException if the node is not a Number
     */
    public static Integer toInteger(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asInteger((Number) node);
        }
        throw new JsonException("Expected Number, but got " + TypeUtil.nameOf(node));
    }

    /**
     * Converts a node to an Integer with flexible type conversion.
     *
     * @param node the node to convert
     * @return the Integer representation
     */
    public static Integer asInteger(Object node) {
        return NumberUtil.asInteger(asNumber(node));
    }

    /**
     * Converts a node to a Short with strict type checking.
     *
     * @param node the node to convert
     * @return the Short representation
     * @throws JsonException if the node is not a Number
     */
    public static Short toShort(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asShort((Number) node);
        }
        throw new JsonException("Expected Number, but got " + TypeUtil.nameOf(node));
    }

    /**
     * Converts a node to a Short with flexible type conversion.
     *
     * @param node the node to convert
     * @return the Short representation
     */
    public static Short asShort(Object node) {
        return NumberUtil.asShort(asNumber(node));
    }

    public static Byte toByte(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asByte((Number) node);
        }
        throw new JsonException("Expected Number, but got " + TypeUtil.nameOf(node));
    }

    public static Byte asByte(Object node) {
        return NumberUtil.asByte(asNumber(node));
    }

    public static Double toDouble(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asDouble((Number) node);
        }
        throw new JsonException("Expected Number, but got " + TypeUtil.nameOf(node));
    }

    public static Double asDouble(Object node) {
        return NumberUtil.asDouble(asNumber(node));
    }

    public static Float toFloat(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asFloat((Number) node);
        }
        throw new JsonException("Expected Number, but got " + TypeUtil.nameOf(node));
    }

    public static Float asFloat(Object node) {
        return NumberUtil.asFloat(asNumber(node));
    }

    public static BigInteger toBigInteger(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asBigInteger((Number) node);
        }
        throw new JsonException("Expected Number, but got " + TypeUtil.nameOf(node));
    }

    public static BigInteger asBigInteger(Object node) {
        return NumberUtil.asBigInteger(asNumber(node));
    }

    public static BigDecimal toBigDecimal(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asBigDecimal((Number) node);
        }
        throw new JsonException("Expected Number, but got " + TypeUtil.nameOf(node));
    }

    public static BigDecimal asBigDecimal(Object node) {
        return NumberUtil.asBigDecimal(asNumber(node));
    }

    public static Boolean toBoolean(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Boolean) {
            return (Boolean) node;
        }
        throw new JsonException("Expected Boolean, but got " + TypeUtil.nameOf(node));
    }

    public static Boolean asBoolean(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Boolean) {
            return (Boolean) node;
        } else if (node instanceof String) {
            String str = ((String) node).toLowerCase();
            if ("true".equals(str) || "yes".equals(str) || "on".equals(str) || "1".equals(str)) return true;
            if ("false".equals(str) || "no".equals(str) || "off".equals(str) || "0".equals(str)) return false;
            throw new JsonException("Cannot convert String to Boolean: supported formats: true/false, yes/no, on/off, 1/0");
        } else if (node instanceof Number) {
            int i = ((Number) node).intValue();
            if (i == 1) return true;
            if (i == 0) return false;
            throw new JsonException("Cannot convert Number to Boolean: numeric values other than 0-false or 1-true");
        }
        throw new JsonException("Cannot convert " + TypeUtil.nameOf(node) + " to Boolean");
    }

    public static JsonObject asJsonObject(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonObject) {
            return (JsonObject) node;
        }
        return new JsonObject(node);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Map) {
            return (Map<String, Object>) node;
        } else if (node instanceof JsonObject) {
            return ((JsonObject) node).toMap();
        } else if (NodeRegistry.isPojo(node.getClass())) {
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(node.getClass());
            for (Map.Entry<String, NodeRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                Object v = fi.getValue().invokeGetter(node);
                map.put(fi.getKey(), v);
            }
            return map;
        }
        throw new JsonException("Cannot convert " + TypeUtil.nameOf(node) + " to Map<String, Object>");
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> asMap(Object node, Class<T> clazz) {
        if (node == null) {
            return null;
        } else if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                T v = as(e.getValue(), clazz);
                e.setValue(v);
            }
            return (Map<String, T>) map;
        } else if (node instanceof JsonObject) {
            return ((JsonObject) node).toMap(clazz);
        } else if (NodeRegistry.isPojo(node.getClass())) {
            Map<String, T> map = Sjf4jConfig.global().mapSupplier.create();
            NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(node.getClass());
            for (Map.Entry<String, NodeRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                Object v = fi.getValue().invokeGetter(node);
                map.put(fi.getKey(), as(v, clazz));
            }
            return map;
        }
        throw new JsonException("Cannot convert " + TypeUtil.nameOf(node) + " to Map<String, " + clazz.getName() + ">");
    }

    public static JsonArray asJsonArray(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonArray) {
            return (JsonArray) node;
        }
        return new JsonArray(node);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof List) {
            return (List<Object>) node;
        } else if (node instanceof JsonArray) {
            return ((JsonArray) node).toList();
        } else if (node.getClass().isArray()) {
            List<Object> list = Sjf4jConfig.global().listSupplier.create();
            int len = Array.getLength(node);
            if (len > 0) {
                for (int i = 0; i < len; i++) {
                    list.add(Array.get(node, i));
                }
            }
            return list;
        }
        throw new JsonException("Cannot convert " + TypeUtil.nameOf(node) + " to List<Object>");
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> asList(Object node, Class<T> clazz) {
        if (node == null) {
            return null;
        } else if (node instanceof List) {
            List<?> list = (List<?>) node;
            if (list.isEmpty()) {
                return (List<T>) node;
            } else {
                Object first = list.get(0);
                if (clazz.isInstance(first)) return (List<T>) list;
                List<T> newlist = Sjf4jConfig.global().listSupplier.create();
                for (Object v : list) {
                    newlist.add(NodeUtil.as(v, clazz));
                }
                return newlist;
            }
        } else if (node instanceof JsonArray) {
            return ((JsonArray) node).toList(clazz);
        } else if (node.getClass().isArray()) {
            List<T> list = Sjf4jConfig.global().listSupplier.create();
            int len = Array.getLength(node);
            if (len > 0) {
                for (int i = 0; i < len; i++) {
                    list.add(NodeUtil.as(Array.get(node, i), clazz));
                }
            }
            return list;
        }
        throw new JsonException("Cannot convert " + TypeUtil.nameOf(node) + " to List<" + clazz.getName() + ">");
    }

    @SuppressWarnings("unchecked")
    public static Object[] asArray(Object node) {
        if (node == null) {
            return null;
        } else if (node.getClass().isArray()) {
            if (node.getClass().getComponentType().isPrimitive()) {
                int length = Array.getLength(node);
                Object[] arr = new Object[length];
                for (int i = 0; i < length; i++) {
                    arr[i] = Array.get(node, i); // Auto boxing
                }
                return arr;
            } else {
                return (Object[]) node;
            }
        } else if (node instanceof List) {
            return ((List<Object>) node).toArray();
        } else if (node instanceof JsonArray) {
            return ((JsonArray) node).toArray();
        }
        throw new JsonException("Cannot convert " + TypeUtil.nameOf(node) + " to Object[]");
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] asArray(Object node, Class<T> clazz) {
        if (node == null) {
            return null;
        } else if (node.getClass().isArray()) {
            if (!clazz.isAssignableFrom(node.getClass())) {
                int length = Array.getLength(node);
                T[] arr = (T[]) Array.newInstance(clazz, length);
                for (int i = 0; i < length; i++) {
                    arr[i] = NodeUtil.as(Array.get(node, i), clazz);
                }
                return arr;
            }
        } else if (node instanceof List) {
            List<?> list = (List<?>) node;
            if (list.isEmpty()) {
                return (T[]) Array.newInstance(clazz, 0);
            } else {
                T[] arr = (T[]) Array.newInstance(clazz, list.size());
                for (int i = 0; i < list.size(); i++) {
                    arr[i] = as(list.get(i), clazz);
                }
                return arr;
            }
        } else if (node instanceof JsonArray) {
            return ((JsonArray) node).toArray(clazz);
        }
        throw new JsonException("Cannot convert " + TypeUtil.nameOf(node) + " to " + clazz.getName() + "[]");
    }

    @SuppressWarnings({"unchecked"})
    public static <T> T to(Object node, Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        if (node == null) return null;

        Class<?> boxed = box(clazz);
        if (boxed.isInstance(node)) return (T) node;
        if (Number.class.isAssignableFrom(boxed)) return (T) NumberUtil.as(toNumber(node), boxed);
        if (boxed == String.class) return (T) toString(node);
        if (boxed == Character.class) return (T) toCharacter(node);
        throw new JsonException("Type mismatch: expected " + boxed.getName());
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T as(Object node, Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        if (node == null) return null;

        Class<?> boxed = box(clazz);
        if (boxed.isInstance(node)) return (T) node;
        if (boxed == String.class) return (T) asString(node);
        if (boxed == Character.class) return (T) asCharacter(node);
        if (Number.class.isAssignableFrom(boxed)) return (T) NumberUtil.as(asNumber(node), boxed);
        if (boxed == Boolean.class) return (T) asBoolean(node);
        if (boxed == Map.class) return (T) asMap(node);
        if (boxed == JsonObject.class) return (T) asJsonObject(node);
        if (boxed == JsonArray.class) return (T) asJsonArray(node);
        if (boxed == List.class) return (T) asList(node);
        if (boxed.isArray()) return (T) asArray(node, boxed.getComponentType());
        if (boxed.isEnum()) return (T) asEnum(node, (Class<Enum>) boxed);

        try {
            return Sjf4j.fromNode(node, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert node to " + clazz.getName(), e);
        }
    }

    private static Class<?> box(Class<?> clazz) {
        if (!clazz.isPrimitive()) return clazz;
        if (clazz == int.class) return Integer.class;
        if (clazz == long.class) return Long.class;
        if (clazz == double.class) return Double.class;
        if (clazz == float.class) return Float.class;
        if (clazz == boolean.class) return Boolean.class;
        if (clazz == char.class) return Character.class;
        if (clazz == byte.class) return Byte.class;
        if (clazz == short.class) return Short.class;
        throw new AssertionError(clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T createContainer(Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        if (Map.class.isAssignableFrom(clazz)) {
            return (T) Sjf4jConfig.global().mapSupplier.create();
        } else if (List.class.isAssignableFrom(clazz)) {
            return (T) Sjf4jConfig.global().listSupplier.create();
        } else if (clazz == JsonObject.class) {
            return (T) new JsonObject();
        } else if (clazz == JsonArray.class) {
            return (T) new JsonArray();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(clazz);
        if (pi != null) {
            return (T) pi.newInstance();
        }
        throw new JsonException("Cannot create container of " + clazz.getName());
    }


    /// Basic



    /**
     * Compares two objects using <b>Object-Based Node Tree</b> semantics.
     * <p>
     * This method determines whether {@code source} and {@code target} represent
     * the same logical JSON node, independent of their concrete Java types.
     * <p>
     * <b>JSON container semantics:</b>
     * <ul>
     *   <li><b>JSON Object</b> are compared by key sets and corresponding values,
     *       regardless of whether they are backed by {@code Map}, {@code JsonObject},
     *       {@code JOJO}, or {@code POJO}.</li>
     *   <li><b>JSON Array</b> are compared by element order and values,
     *       regardless of whether they are backed by {@code List},
     *       {@code JsonArray}, {@code JAJO}, or Java arrays.</li>
     * </ul>
     * <p>
     * The comparison is recursive and applies to nested objects, arrays, and primitive
     * values. It focuses on node-level equivalence rather than Java
     * {@link Object#equals(Object)} semantics.
     *
     * @param source the source object
     * @param target the target object
     * @return {@code true} if both objects are equivalent under node semantics;
     *         {@code false} otherwise
     */
    public static boolean equals(Object source, Object target) {
        if (target == source) return true;
        if (source == null || target == null) return false;

        NodeType ntSource = NodeType.of(source);
        NodeType ntTarget = NodeType.of(target);
        if (ntSource.isNumber() && ntTarget.isNumber()) {
            return NumberUtil.compare((Number) source, (Number) target) == 0;
        } else if (ntSource.isValue() && ntTarget.isValue()) {
            return source.equals(target);
        } else if (ntSource.isObject() && ntTarget.isObject()) {
            if (NodeWalker.sizeInObject(source) != NodeWalker.sizeInObject(target)) return false;
            for (Map.Entry<String, Object> entry : NodeWalker.entrySetInObject(source)) {
                Object subSource = entry.getValue();
                Object subTarget = NodeWalker.getInObject(target, entry.getKey());
                if (!equals(subSource, subTarget)) return false;
            }
            return true;
        } else if (ntSource.isArray() && ntTarget.isArray()) {
            if (NodeWalker.sizeInArray(source) != NodeWalker.sizeInArray(target)) return false;
            int size = NodeWalker.sizeInArray(source);
            for (int i = 0; i < size; i++) {
                Object subSource = NodeWalker.getInArray(source, i);
                Object subTarget = NodeWalker.getInArray(target, i);
                if (!equals(subSource, subTarget)) return false;
            }
            return true;
        } else if (ntSource.isUnknown() && ntTarget.isUnknown()) {
            return Objects.equals(source, target);
        }
        return false;
    }

    public static int hashCode(Object node) {
        if (node == null) return 0;
        NodeType nt = NodeType.of(node);
        if (nt.isNumber()) {
            return NumberUtil.hashCode((Number) node);
        } else if (nt.isValue()) {
            return node.hashCode();
        } else if (nt.isObject()) {
            int hash = 1;
            for (Map.Entry<String, Object> entry : NodeWalker.entrySetInObject(node)) {
                String key = entry.getKey();
                Object value = entry.getValue();
                int entryHash = 31 * key.hashCode() + hashCode(value);
                // disorder
                hash += entryHash;
            }
            return hash;
        } else if (nt.isArray()) {
            int hash = 1;
            int size = NodeWalker.sizeInArray(node);
            for (int i = 0; i < size; i++) {
                Object item = NodeWalker.getInArray(node, i);
                hash = 31 * hash + hashCode(item);
            }
            return hash;
        } else if (nt.isUnknown()) {
            return Objects.hashCode(node);
        }
        return 0;
    }

    /**
     * Returns a shallow copy of the given node.
     */
    @SuppressWarnings({"unchecked", "SuspiciousSystemArraycopy"})
    public static <T> T copy(T node) {
        NodeType nt = NodeType.of(node);
        switch (nt) {
            case OBJECT_MAP: {
                Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
                map.putAll((Map<String, ?>) node);
                return (T) map;
            }
            case OBJECT_JSON_OBJECT: {
                return (T) new JsonObject(node);
            }
            case OBJECT_JOJO: {
                NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(node.getClass());
                JsonObject jojo = (JsonObject) pi.newInstance();
                jojo.putAll(node);
                return (T) jojo;
            }
            case OBJECT_POJO: {
                NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(node.getClass());
                Object pojo = pi.newInstance();
                NodeWalker.visitObject(node, (k, v) -> NodeWalker.putInObject(pojo, k, v));
                return (T) pojo;
            }
            case ARRAY_LIST: {
                List<Object> list = Sjf4jConfig.global().listSupplier.create();
                list.addAll((List<?>) node);
                return (T) list;
            }
            case ARRAY_JSON_ARRAY: {
                return (T) new JsonArray(node);
            }
            case ARRAY_JAJO: {
                NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(node.getClass());
                JsonArray jajo = (JsonArray) pi.newInstance();
                jajo.addAll((JsonArray) node);
                return (T) jajo;
            }
            case ARRAY_ARRAY: {
                int len = Array.getLength(node);
                Object arr = Array.newInstance(node.getClass().getComponentType(), len);
                System.arraycopy(node, 0, arr, 0, len);
                return (T) arr;
            }
            case VALUE_NODE_VALUE: {
                NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(node.getClass());
                return (T) ci.copy(node);
            }
            default:
                return node;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T node) {
        return (T) Sjf4jConfig.global().getNodeFacade().readNode(node, node.getClass(), true);
    }

    /**
     * Returns a compact, human-readable representation of the given object.
     * <p>
     * This method is mainly used for debugging and logging. It prints objects
     * in a deterministic, structure-oriented format instead of relying on
     * {@link Object#toString()}.
     *
     * <h3>Type Notation</h3>
     * <ul>
     *   <li>{@code {..}}  – Map</li>
     *   <li>{@code J{..}}  – JsonObject</li>
     *   <li>{@code [..]}  – List</li>
     *   <li>{@code J[..]}  – JsonArray</li>
     *   <li>{@code A[..]}  – Array</li>
     *   <li>{@code @Type{..}} – POJO / JOJO</li>
     *   <li>{@code @Type[..]} – JAJO</li>
     *   <li>{@code @Type#raw} – NodeValue</li>
     *   <li>{@code !Object@hash} – Unknown</li>
     * </ul>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * J{email=.com, user=@User{*id=1, name=M{a=b, c=d}}, arr=A[haha, xi, 1], date=!LocalDate#2025-12-29}
     * }</pre>
     *
     * @param node the object to inspect
     * @return a compact structural string
     */
    public static String inspect(Object node) {
        StringBuilder sb = new StringBuilder();
        _inspect(node, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void _inspect(Object node, StringBuilder sb) {
        NodeType nt = NodeType.of(node);
        switch (nt) {
            case OBJECT_MAP: {
                Map<String, Object> map = (Map<String, Object>) node;
                sb.append("{");
                int idx = 0;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (idx++ > 0) sb.append(", ");
                    sb.append(entry.getKey()).append("=");
                    _inspect(entry.getValue(), sb);
                }
                sb.append("}");
                return;
            }
            case OBJECT_JSON_OBJECT: {
                JsonObject jo = (JsonObject) node;
                sb.append("J{");
                int idx = 0;
                for (Map.Entry<String, Object> entry : jo.entrySet()) {
                    if (idx++ > 0) sb.append(", ");
                    sb.append(entry.getKey()).append("=");
                    _inspect(entry.getValue(), sb);
                }
                sb.append("}");
                return;
            }
            case OBJECT_JOJO: {
                JsonObject jo = (JsonObject) node;
                sb.append("@").append(node.getClass().getSimpleName()).append("{");
                NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(node.getClass());
                AtomicInteger idx = new AtomicInteger(0);
                jo.forEach((k, v) -> {
                    if (idx.getAndIncrement() > 0) sb.append(", ");
                    if (pi != null && pi.getFields().containsKey(k)) {
                        sb.append("*");
                    }
                    sb.append(k).append("=");
                    _inspect(v, sb);
                });
                sb.append("}");
                return;
            }
            case OBJECT_POJO: {
                NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(node.getClass());
                sb.append("@").append(node.getClass().getSimpleName()).append("{");
                int idx = 0;
                for (Map.Entry<String, NodeRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                    if (idx++ > 0) sb.append(", ");
                    sb.append("*").append(fi.getKey()).append("=");
                    Object v = fi.getValue().invokeGetter(node);
                    _inspect(v, sb);
                }
                sb.append("}");
                return;
            }
            case ARRAY_LIST: {
                List<Object> list = (List<Object>) node;
                sb.append("[");
                int idx = 0;
                for (Object v : list) {
                    if (idx++ > 0) sb.append(", ");
                    _inspect(v, sb);
                }
                sb.append("]");
                return;
            }
            case ARRAY_JSON_ARRAY: {
                JsonArray ja = (JsonArray) node;
                sb.append("J[");
                int idx = 0;
                for (Object v : ja) {
                    if (idx++ > 0) sb.append(", ");
                    _inspect(v, sb);
                }
                sb.append("]");
                return;
            }
            case ARRAY_JAJO: {
                JsonArray ja = (JsonArray) node;
                sb.append("@").append(node.getClass().getSimpleName()).append("[");
                int idx = 0;
                for (Object v : ja) {
                    if (idx++ > 0) sb.append(", ");
                    _inspect(v, sb);
                }
                sb.append("]");
                return;
            }
            case ARRAY_ARRAY: {
                int len = Array.getLength(node);
                sb.append("A[");
                for (int i = 0; i < len; i++) {
                    if (i > 0) sb.append(", ");
                    _inspect(Array.get(node, i), sb);
                }
                sb.append("]");
                return;
            }
            case VALUE_NODE_VALUE: {
                NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(node.getClass());
                Object raw = ci.encode(node);
                sb.append("@").append(node.getClass().getSimpleName()).append("#");
                _inspect(raw, sb);
                return;
            }
            case UNKNOWN: {
                sb.append("!").append(node);
                return;
            }
            default: {
                sb.append(node);
                return;
            }
        }
    }


}
