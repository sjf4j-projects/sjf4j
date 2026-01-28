package org.sjf4j.node;


import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.Sjf4jConfig;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public class Nodes {


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
                Types.name(node));
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
        throw new JsonException("Expected String or Character or Enum, but got " + Types.name(node));
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
        throw new JsonException("Expected Character or single-character String, but got " + Types.name(node));
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
        throw new JsonException("Cannot convert " + Types.name(node) + " to Character"
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
        throw new JsonException("Expected Number, but got " + Types.name(node));
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
            if (Numbers.isNumeric(str)) {
                return Numbers.toNumber(node.toString());
            }
            throw new JsonException("Cannot convert String to Number: not a numeric value");
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).ordinal();
        }
        throw new JsonException("Cannot convert " + Types.name(node) + " to Number");
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
            return Numbers.asLong((Number) node);
        }
        throw new JsonException("Expected Number, but got " + Types.name(node));
    }

    /**
     * Converts a node to a Long with flexible type conversion.
     *
     * @param node the node to convert
     * @return the Long representation
     */
    public static Long asLong(Object node) {
        return Numbers.asLong(asNumber(node));
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
            return Numbers.asInteger((Number) node);
        }
        throw new JsonException("Expected Number, but got " + Types.name(node));
    }

    /**
     * Converts a node to an Integer with flexible type conversion.
     *
     * @param node the node to convert
     * @return the Integer representation
     */
    public static Integer asInteger(Object node) {
        return Numbers.asInteger(asNumber(node));
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
            return Numbers.asShort((Number) node);
        }
        throw new JsonException("Expected Number, but got " + Types.name(node));
    }

    /**
     * Converts a node to a Short with flexible type conversion.
     *
     * @param node the node to convert
     * @return the Short representation
     */
    public static Short asShort(Object node) {
        return Numbers.asShort(asNumber(node));
    }

    public static Byte toByte(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return Numbers.asByte((Number) node);
        }
        throw new JsonException("Expected Number, but got " + Types.name(node));
    }

    public static Byte asByte(Object node) {
        return Numbers.asByte(asNumber(node));
    }

    public static Double toDouble(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return Numbers.asDouble((Number) node);
        }
        throw new JsonException("Expected Number, but got " + Types.name(node));
    }

    public static Double asDouble(Object node) {
        return Numbers.asDouble(asNumber(node));
    }

    public static Float toFloat(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return Numbers.asFloat((Number) node);
        }
        throw new JsonException("Expected Number, but got " + Types.name(node));
    }

    public static Float asFloat(Object node) {
        return Numbers.asFloat(asNumber(node));
    }

    public static BigInteger toBigInteger(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return Numbers.asBigInteger((Number) node);
        }
        throw new JsonException("Expected Number, but got " + Types.name(node));
    }

    public static BigInteger asBigInteger(Object node) {
        return Numbers.asBigInteger(asNumber(node));
    }

    public static BigDecimal toBigDecimal(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return Numbers.asBigDecimal((Number) node);
        }
        throw new JsonException("Expected Number, but got " + Types.name(node));
    }

    public static BigDecimal asBigDecimal(Object node) {
        return Numbers.asBigDecimal(asNumber(node));
    }

    public static Boolean toBoolean(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Boolean) {
            return (Boolean) node;
        }
        throw new JsonException("Expected Boolean, but got " + Types.name(node));
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
        throw new JsonException("Cannot convert " + Types.name(node) + " to Boolean");
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
        throw new JsonException("Cannot convert " + Types.name(node) + " to Map<String, Object>");
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
        throw new JsonException("Cannot convert " + Types.name(node) + " to Map<String, " + clazz.getName() + ">");
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
        throw new JsonException("Cannot convert " + Types.name(node) + " to List<Object>");
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
                    newlist.add(as(v, clazz));
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
                    list.add(as(Array.get(node, i), clazz));
                }
            }
            return list;
        }
        throw new JsonException("Cannot convert " + Types.name(node) + " to List<" + clazz.getName() + ">");
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
        throw new JsonException("Cannot convert " + Types.name(node) + " to Object[]");
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
                    arr[i] = as(Array.get(node, i), clazz);
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
        throw new JsonException("Cannot convert " + Types.name(node) + " to " + clazz.getName() + "[]");
    }

    @SuppressWarnings({"unchecked"})
    public static <T> T to(Object node, Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        if (node == null) return null;

        Class<?> boxed = box(clazz);
        if (boxed.isInstance(node)) return (T) node;
        if (Number.class.isAssignableFrom(boxed)) return (T) Numbers.as(toNumber(node), boxed);
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
        if (Number.class.isAssignableFrom(boxed)) return (T) Numbers.as(asNumber(node), boxed);
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
            return Numbers.compare((Number) source, (Number) target) == 0;
        } else if (ntSource.isString() && ntTarget.isString()) {
            return toString(source).equals(toString(target));
        } else if (ntSource.isValue() && ntTarget.isValue()) {
            return source.equals(target);
        } else if (ntSource.isObject() && ntTarget.isObject()) {
            if (sizeInObject(source) != sizeInObject(target)) return false;
            for (Map.Entry<String, Object> entry : entrySetInObject(source)) {
                Object subSource = entry.getValue();
                Object subTarget = getInObject(target, entry.getKey());
                if (!equals(subSource, subTarget)) return false;
            }
            return true;
        } else if (ntSource.isArray() && ntTarget.isArray()) {
            if (sizeInArray(source) != sizeInArray(target)) return false;
            int size = sizeInArray(source);
            for (int i = 0; i < size; i++) {
                Object subSource = getInArray(source, i);
                Object subTarget = getInArray(target, i);
                if (!equals(subSource, subTarget)) return false;
            }
            return true;
        } else if (ntSource.isUnknown() && ntTarget.isUnknown()) {
            return Objects.equals(source, target);
        }
        return false;
    }

    public static int hash(Object node) {
        if (node == null) return 0;
        NodeType nt = NodeType.of(node);
        if (nt.isNumber()) {
            return Numbers.hash((Number) node);
        } else if (nt.isValue()) {
            return node.hashCode();
        } else if (nt.isObject()) {
            int hash = 1;
            for (Map.Entry<String, Object> entry : entrySetInObject(node)) {
                String key = entry.getKey();
                Object value = entry.getValue();
                int entryHash = 31 * key.hashCode() + hash(value);
                // disorder
                hash += entryHash;
            }
            return hash;
        } else if (nt.isArray()) {
            int hash = 1;
            int size = sizeInArray(node);
            for (int i = 0; i < size; i++) {
                Object item = getInArray(node, i);
                hash = 31 * hash + hash(item);
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
            case VALUE_SET: {
                return node;
            }
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
                visitObject(node, (k, v) -> putInObject(pojo, k, v));
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
            case VALUE_REGISTERED: {
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
            case VALUE_SET: {
                sb.append("@Set#");
                sb.append(node);
                return;
            }
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
                for (int i = 0; i < list.size(); i++) {
                    Object v = list.get(i);
                    if (i > 0) sb.append(", ");
                    _inspect(v, sb);
                }
                sb.append("]");
                return;
            }
            case ARRAY_JSON_ARRAY: {
                JsonArray ja = (JsonArray) node;
                sb.append("J[");
                for (int i = 0; i < ja.size(); i++) {
                    Object v = ja.getNode(i);
                    if (i > 0) sb.append(", ");
                    _inspect(v, sb);
                }
                sb.append("]");
                return;
            }
            case ARRAY_JAJO: {
                JsonArray ja = (JsonArray) node;
                sb.append("@").append(node.getClass().getSimpleName()).append("[");
                for (int i = 0; i < ja.size(); i++) {
                    Object v = ja.getNode(i);
                    if (i > 0) sb.append(", ");
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
            case VALUE_REGISTERED: {
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



    /// Visit

    @SuppressWarnings("unchecked")
    public static void visitObject(Object container, BiConsumer<String, Object> visitor) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(visitor, "visitor is null");
        if (container instanceof Map) {
            ((Map<String, Object>) container).forEach(visitor);
            return;
        }
        if (container instanceof JsonObject) {
            ((JsonObject) container).forEach(visitor);
            return;
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
                Object node = entry.getValue().invokeGetter(container);
                visitor.accept(entry.getKey(), node);
            }
            return;
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }


    @SuppressWarnings("unchecked")
    public static void visitArray(Object container, BiConsumer<Integer, Object> visitor) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(visitor, "visitor is null");
        if (container instanceof java.util.Set) {
            throw new JsonException("Invalid array container: Set");
        }
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            for (int i = 0; i < list.size(); i++) {
                visitor.accept(i, list.get(i));
            }
            return;
        }
        if (container instanceof JsonArray) {
            ((JsonArray) container).forEach(visitor);
            return;
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            for (int i = 0; i < len; i++) {
                visitor.accept(i, Array.get(container, i));
            }
            return;
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static boolean anyMatchInArray(Object container, BiPredicate<Integer, Object> predicate) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(predicate, "predicate is null");
        if (container instanceof java.util.Set) {
            throw new JsonException("Invalid array container: Set");
        }
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            for (int i = 0; i < list.size(); i++) {
                if (predicate.test(i, list.get(i))) return true;
            }
            return false;
        }
        if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            for (int i = 0; i < ja.size(); i++) {
                if (predicate.test(i, ja.get(i))) return true;
            }
            return false;
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            for (int i = 0; i < len; i++) {
                if (predicate.test(i, Array.get(container, i))) return true;
            }
            return false;
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());

    }

    @SuppressWarnings("unchecked")
    public static boolean allMatchInArray(Object container, BiPredicate<Integer, Object> predicate) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(predicate, "predicate is null");
        if (container instanceof java.util.Set) {
            throw new JsonException("Invalid array container: Set");
        }
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            for (int i = 0; i < list.size(); i++) {
                if (!predicate.test(i, list.get(i))) return false;
            }
            return true;
        }
        if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            for (int i = 0; i < ja.size(); i++) {
                if (!predicate.test(i, ja.get(i))) return false;
            }
            return true;
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            for (int i = 0; i < len; i++) {
                if (!predicate.test(i, Array.get(container, i))) return false;
            }
            return true;
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static boolean noneMatchInArray(Object container, BiPredicate<Integer, Object> predicate) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(predicate, "predicate is null");
        if (container instanceof java.util.Set) {
            throw new JsonException("Invalid array container: Set");
        }
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            for (int i = 0; i < list.size(); i++) {
                if (predicate.test(i, list.get(i))) return false;
            }
            return true;
        }
        if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            for (int i = 0; i < ja.size(); i++) {
                if (predicate.test(i, ja.get(i))) return false;
            }
            return true;
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            for (int i = 0; i < len; i++) {
                if (predicate.test(i, Array.get(container, i))) return false;
            }
            return true;
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    public static int sizeInObject(Object container) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof Map) {
            return ((Map<?, ?>) container).size();
        }
        if (container instanceof JsonObject) {
            return ((JsonObject) container).size();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            return pi.getFields().size();
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }

    public static int sizeInArray(Object container) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof java.util.Set) {
            throw new JsonException("Invalid array container: Set");
        }
        if (container instanceof List) {
            return ((List<?>) container).size();
        }
        if (container instanceof JsonArray) {
            return ((JsonArray) container).size();
        }
        if (container.getClass().isArray()) {
            return Array.getLength(container);
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static Set<String> keySetInObject(Object container) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof Map) {
            return ((Map<String, Object>) container).keySet();
        }
        if (container instanceof JsonObject) {
            return ((JsonObject) container).keySet();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            return pi.getFields().keySet();
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static Set<Map.Entry<String, Object>> entrySetInObject(Object container) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof Map) {
            return ((Map<String, Object>) container).entrySet();
        }
        if (container instanceof JsonObject) {
            return ((JsonObject) container).entrySet();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            Set<Map.Entry<String, Object>> entrySet = new LinkedHashSet<>();
            for (Map.Entry<String, NodeRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                Object node = fi.getValue().invokeGetter(container);
                entrySet.add(new AbstractMap.SimpleEntry<>(fi.getKey(), node));
            }
            return entrySet;
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static Iterator<Object> iteratorInArray(Object container) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof List) {
            return ((List<Object>) container).iterator();
        }
        if (container instanceof JsonArray) {
            return ((JsonArray) container).iterator();
        }
        if (container.getClass().isArray()) {
            return arrayIterator(container);
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    private static Iterator<Object> arrayIterator(Object array) {
        int len = Array.getLength(array);
        return new Iterator<Object>() {
            int i = 0;
            public boolean hasNext() {
                return i < len;
            }
            public Object next() {
                if (!hasNext()) throw new NoSuchElementException();
                return Array.get(array, i++);
            }
        };
    }


    @SuppressWarnings("unchecked")
    public static boolean containsInObject(Object container, String key) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(key, "key is null");
        if (container instanceof Map) {
            return ((Map<String, Object>) container).containsKey(key);
        }
        if (container instanceof JsonObject) {
            return ((JsonObject) container).containsKey(key);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            return pi.getFields().containsKey(key);
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static boolean containsInArray(Object container, int idx) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof java.util.Set) {
            throw new JsonException("Invalid array container: Set");
        }
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            idx = idx < 0 ? list.size() + idx : idx;
            return idx >= 0 && idx < list.size();
        }
        if (container instanceof JsonArray) {
            return ((JsonArray) container).containsIndex(idx);
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            idx = idx < 0 ? len + idx : idx;
            return idx >= 0 && idx < len;
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    public static Object getInObject(Object container, String key) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(key, "key is null");
        if (container instanceof Map) {
            return ((Map<?, ?>) container).get(key);
        }
        if (container instanceof JsonObject) {
            return ((JsonObject) container).get(key);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            NodeRegistry.FieldInfo fi = pi.getFields().get(key);
            return fi != null ? fi.invokeGetter(container) : null;
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }


    @SuppressWarnings("unchecked")
    public static Object getInArray(Object container, int idx) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof java.util.Set) {
            throw new JsonException("Invalid array container: Set");
        }
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx < 0 || idx >= list.size()) {
                return null;
            } else {
                return list.get(idx);
            }
        }
        if (container instanceof JsonArray) {
            return ((JsonArray) container).get(idx);
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            idx = idx < 0 ? len + idx : idx;
            if (idx < 0 || idx >= len) {
                return null;
            } else {
                return Array.get(container, idx);
            }
        }
        throw new JsonException("Invalid array container: " + container.getClass());
    }

    // return null, indicates that the value is a POJO without the key, and no additional keys can be inserted.
    // return TypedNode.of(null) means the value of the key is null, and you can insert it.
    @SuppressWarnings("unchecked")
    public static TypedNode getTypedInObject(TypedNode typedNode, String key) {
        Objects.requireNonNull(typedNode, "typedNode is null");
        Objects.requireNonNull(key, "key is null");
        Object node = typedNode.getNode();
        if (node instanceof Map) {
            Type subtype = Types.resolveTypeArgument(typedNode.getClazzType(), Map.class, 1);
            return TypedNode.of(((Map<String, Object>) node).get(key), subtype);
        }
        if (node.getClass() == JsonObject.class) {
            return TypedNode.infer(((JsonObject) node).getNode(key));
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            NodeRegistry.FieldInfo fi = pi.getFields().get(key);
            if (fi != null) {
                return TypedNode.of(fi.invokeGetter(node), fi.getType());
            } else if (node instanceof JsonObject) {
                return TypedNode.infer(((JsonObject) node).getNode(key));
            } else {
                return null;
            }
        }
        throw new JsonException("Invalid object typedNode: " + typedNode.getClass().getName());
    }

    // return null, indicates that the index of JsonArray/List/Array is invalid, and you can not set it.
    // return TypedNode.of(null), means the value of the index is null, and you can insert it.
    @SuppressWarnings("unchecked")
    public static TypedNode getTypedInArray(TypedNode typedNode, int idx) {
        Objects.requireNonNull(typedNode, "typedNode is null");
        Object node = typedNode.getNode();
        if (node instanceof List) {
            Type subtype = Types.resolveTypeArgument(typedNode.getClazzType(), List.class, 0);
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx >= 0 && idx < list.size()) {
                return TypedNode.of(list.get(idx), subtype);
            } else if (idx == list.size()){
                return TypedNode.nullOf(subtype);
            } else {
                return null;
            }
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            idx = idx < 0 ? ja.size() + idx : idx;
            if (idx >= 0 && idx <= ja.size()) {
                return TypedNode.infer(ja.getNode(idx));
            } else {
                return null;
            }
        }
        if (typedNode.getClass().isArray()) {
            Type subtype = typedNode.getClass().getComponentType();
            int len = Array.getLength(typedNode);
            idx = idx < 0 ? len + idx : idx;
            if (idx >= 0 || idx < len) {
                return TypedNode.of(Array.get(typedNode, idx), subtype);
            } else {
                return null;
            }
        }
        throw new JsonException("Invalid array typedNode: " + typedNode.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static Object putInObject(Object container, String key, Object node) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(key, "key is null");
        if (container instanceof Map) {
            return ((Map<String, Object>) container).put(key, node);
        }
        if (container instanceof JsonObject) {
            return ((JsonObject) container).put(key, node);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            NodeRegistry.FieldInfo fi = pi.getFields().get(key);
            if (fi != null) {
                Object old = fi.invokeGetter(container);
                fi.invokeSetter(container, node);
                return old;
            } else {
                throw new JsonException("Not found field '" + key + "' in POJO container " +
                        container.getClass().getName());
            }
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static Object setInArray(Object container, int idx, Object node) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx == list.size()) {
                list.add(node);
                return null;
            } else if (idx >= 0 && idx < list.size()) {
                return list.set(idx, node);
            } else {
                throw new JsonException("Cannot set/add index " + idx + " in List of size " +
                        list.size() + " (index < size: modify; index == size: append)");
            }
        }
        if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            if (idx == ja.size()) {
                ja.add(node);
                return null;
            } else if (ja.containsIndex(idx)) {
                return ja.set(idx, node);
            } else {
                throw new JsonException("Cannot set/add index " + idx + " in JsonArray of size " +
                        ja.size() + " (index < size: modify; index == size: append)");
            }
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            idx = idx < 0 ? len + idx : idx;
            if (idx >= 0 && idx < len) {
                Object old = Array.get(container, idx);
                Array.set(container, idx, node);
                return old;
            } else {
                throw new JsonException("Cannot set index " + idx + " in Array of size " +
                        len + " (index < size: modify)");
            }
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static void addInArray(Object container, Object node) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof List) {
            ((List<Object>) container).add(node);
            return;
        }
        if (container instanceof JsonArray) {
            ((JsonArray) container).add(node);
            return;
        }
        if (container.getClass().isArray()) {
            throw new JsonException("Array do not support append operation");
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static void addInArray(Object container, int idx, Object node) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof List) {
            ((List<Object>) container).add(idx, node);
            return;
        }
        if (container instanceof JsonArray) {
            ((JsonArray) container).add(idx, node);
            return;
        }
        if (container.getClass().isArray()) {
            throw new JsonException("Java Array do not support add operation");
        }
        throw new JsonException("Invalid array container: " + container.getClass());
    }

    @SuppressWarnings("unchecked")
    public static Object removeInObject(Object container, String key) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(key, "key is null");
        if (container instanceof JsonObject) {
            return ((JsonObject) container).remove(key);
        }
        if (container instanceof Map) {
            return ((Map<String, Object>) container).remove(key);
        }
        if (NodeRegistry.isPojo(container.getClass())) {
            throw new JsonException("Cannot remove field '" + key + "' in POJO container '" +
                    container.getClass() + "'");
        }
        throw new JsonException("Invalid object container: " + container.getClass());
    }

    @SuppressWarnings("unchecked")
    public static Object removeInArray(Object container, int idx) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx < 0 || idx >= list.size()) {
                return null;
            } else {
                return list.remove(idx);
            }
        }
        if (container instanceof JsonArray) {
            return ((JsonArray) container).remove(idx);
        }
        if (container.getClass().isArray()) {
            throw new JsonException("Cannot remove index " + idx + " in Array container '" +
                    container.getClass().getComponentType() + "'");
        }
        throw new JsonException("Invalid array container: " + container.getClass());
    }




}
