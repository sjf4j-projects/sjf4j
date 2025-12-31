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

    /**
     * Converts a node to a String with strict type checking.
     *
     * @param node the node to convert
     * @return the string representation
     * @throws JsonException if the node is not a CharSequence, Character, or Enum
     */
    public static String toString(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof CharSequence || node instanceof Character) {
            return node.toString();
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).name();
        }
        throw new JsonException("Expected node type CharSequence or Character, but got " + node.getClass());
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
        } else if (node instanceof CharSequence || node instanceof Character) {
            return node.toString();
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).name();
        }
        return node.toString();
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
        throw new JsonException("Expected node type Number, but got " + node.getClass());
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
            throw new JsonException("Cannot convert String '" + node + "' to Number: not a number");
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).ordinal();
        }
        throw new JsonException("Cannot convert " + node.getClass().getName() + " '" + node +
                "' to Number: unsupported type");
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
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
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
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
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
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
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
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
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
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
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
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
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
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
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
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
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
        throw new JsonException("Expected type Boolean, but got " + node.getClass().getName());
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
            throw new JsonException("Cannot convert String '" + node +
                    "' to Boolean: supported formats: true/false, yes/no, on/off, 1/0");
        } else if (node instanceof Number) {
            int i = ((Number) node).intValue();
            if (i == 1) return true;
            if (i == 0) return false;
            throw new JsonException("Cannot convert Number '" + node +
                    "' to Boolean: numeric values other than 0 or 1");
        }
        throw new JsonException("Cannot convert " + node.getClass().getName() + " '" + node +
                "' to Boolean: unsupported type");
    }

    public static JsonObject toJsonObject(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonObject) {
            return (JsonObject) node;
        }
        throw new JsonException("Expected node type JsonObject, but got " + node.getClass().getName());
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
    public static Map<String, Object> toMap(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Map) {
            return (Map<String, Object>) node;
        }
        throw new JsonException("Expected node type Map, but got " + node.getClass().getName());
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
        throw new JsonException("Cannot convert " + node.getClass().getName() + " '" + node +
                "' to Map: unsupported type");
    }

    public static JsonArray toJsonArray(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonArray) {
            return (JsonArray) node;
        }
        throw new JsonException("Expected node type JsonArray, but got " + node.getClass().getName());
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
    public static List<Object> toList(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof List) {
            return (List<Object>) node;
        }
        throw new JsonException("Expected node type List, but got " + node.getClass().getName());
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
        throw new JsonException("Cannot convert " + node.getClass().getName() + " '" + node +
                "' to List: unsupported type");
    }

    public static Object[] toArray(Object node) {
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
        }
        throw new JsonException("Expected node type Array, but got " + node.getClass().getName());
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
        throw new JsonException("Cannot convert " + node.getClass().getName() + " '" + node +
                "' to Array: unsupported type");
    }

    @SuppressWarnings({"unchecked"})
    public static <T> T to(Object node, Class<T> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        if (node == null) {
            return null;
        } else if (clazz.isInstance(node)) {
            return (T) node;
        } else if (clazz == JsonObject.class) {
            return (T) toJsonObject(node);
        } else if (clazz == Map.class) {
            return (T) toMap(node);
        } else if (clazz == JsonArray.class) {
            return (T) toJsonArray(node);
        } else if (clazz == List.class) {
            return (T) toList(node);
        } else if (clazz.isArray()) {
            return (T) toArray(node);
        } else if (clazz == String.class) {
            return (T) toString(node);
        } else if (clazz == Boolean.class) {
            return (T) toBoolean(node);
        } else if (Number.class.isAssignableFrom(clazz)) {
            Number n = toNumber(node);
            return NumberUtil.as(n, clazz);
        } else if (clazz.isPrimitive()) {
            if (clazz == boolean.class) {
                return (T) toBoolean(node);
            } else if (clazz == char.class && node instanceof Character) {
                return (T) node;
            } else {
                Number n = toNumber(node);
                return NumberUtil.as(n, clazz);
            }
        } else if (clazz.isEnum()) {
            if (node.getClass().isEnum()) return (T) node;
        }
        throw new JsonException("Type mismatch, expected " + clazz.getName() + ", but got " +
                node.getClass().getName());
    }


    /// Only support JsonObject/JsonArray, but not POJO/Map/List
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T as(Object node, Class<T> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        if (node == null) {
            return null;
        } else if (clazz.isInstance(node)) {
            return (T) node;
        } else if (clazz == JsonObject.class) {
            return (T) asJsonObject(node);
        } else if (clazz == JsonArray.class) {
            return (T) asJsonArray(node);
        } else if (clazz == String.class) {
            return (T) asString(node);
        } else if (clazz == Boolean.class) {
            return (T) asBoolean(node);
        } else if (Number.class.isAssignableFrom(clazz)) {
            Number n = asNumber(node);
            return NumberUtil.as(n, clazz);
        } else if (clazz.isPrimitive()) {
            if (clazz == boolean.class) {
                return (T) asBoolean(node);
            } else if (clazz == char.class && node instanceof Character) {
                return (T) (Character) asString(node).charAt(0);
            } else {
                Number n = asNumber(node);
                return NumberUtil.as(n, clazz);
            }
        } else if (clazz.isEnum()) {
            if (node.getClass().isEnum()) return (T) node;
            else return (T) Enum.valueOf((Class<? extends Enum>) clazz, asString(node));
        }

        try {
            return Sjf4j.fromNode(node, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert " + node.getClass().getName()  +
                    " to " + clazz.getName(), e);
        }

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
    public static boolean nodeEquals(Object source, Object target) {
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
                Object subSrouce = entry.getValue();
                Object subTarget = NodeWalker.getInObject(target, entry.getKey());
                if (!nodeEquals(subSrouce, subTarget)) return false;
            }
            return true;
        } else if (ntSource.isArray() && ntTarget.isArray()) {
            if (NodeWalker.sizeInArray(source) != NodeWalker.sizeInArray(target)) return false;
            int size = NodeWalker.sizeInArray(source);
            for (int i = 0; i < size; i++) {
                Object subSrouce = NodeWalker.getInArray(source, i);
                Object subTarget = NodeWalker.getInArray(target, i);
                if (!nodeEquals(subSrouce, subTarget)) return false;
            }
            return true;
        } else if (ntSource.isUnknown() && ntTarget.isUnknown()) {
            return Objects.equals(source, target);
        }
        return false;
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
            case VALUE_CONVERTIBLE: {
                NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(node.getClass());
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
     *   <li>{@code M{..}}  – Map</li>
     *   <li>{@code J{..}}  – JsonObject</li>
     *   <li>{@code L[..]}  – List</li>
     *   <li>{@code J[..]}  – JsonArray</li>
     *   <li>{@code A[..]}  – Java Array</li>
     *   <li>{@code @Type{..}} – POJO / JOJO</li>
     *   <li>{@code @Type[..]} – JAJO</li>
     *   <li>{@code !Type#raw} – Convertible</li>
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
                sb.append("M{");
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
                sb.append("L[");
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
            case VALUE_CONVERTIBLE: {
                NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(node.getClass());
                Object raw = ci.convert(node);
                sb.append("!").append(node.getClass().getSimpleName()).append("#");
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
