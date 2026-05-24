package org.sjf4j.node;


import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.facade.FacadeNodes;
import org.sjf4j.path.PathSegment;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;


/**
 * Core structural utilities for SJF4J's OBNT (Object-Based Node Tree).
 * <p>
 * {@code Nodes} is the main helper API for working directly with OBNT values:
 * native Java object graphs composed of object nodes, array nodes, and scalar
 * values without a dedicated JSON AST.
 *
 * <p>It provides type conversion, inspection, traversal, equality, hashing,
 * copying, and container access with semantics shared across {@link JsonObject},
 * {@link JsonArray}, plain {@link Map}/{@link List}, and supported facade-native
 * node types.
 */
public final class Nodes {

    /// Type-safe access and cross-type conversion

    /**
     * Converts a node to enum using strict conversion.
     */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> E toEnum(Object node, Class<E> enumClazz) {
        if (node == null) return null;
        if (enumClazz.isInstance(node)) return (E) node;
        String s = toString(node);
        return Enum.valueOf(enumClazz, s);
    }

    /**
     * Converts a node to enum using lenient conversion.
     */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> E asEnum(Object node, Class<E> enumClazz) {
        if (node == null) return null;
        if (enumClazz.isInstance(node)) return (E) node;
        String s = asString(node);
        try {
            return Enum.valueOf(enumClazz, s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Converts a node to a String with strict type checking.
     */
    public static String toString(Object node) {
        if (node == null) return null;
        if (node instanceof String || node instanceof Character) return node.toString();
        if (node.getClass().isEnum()) return ((Enum<?>) node).name();
        if (FacadeNodes.isNode(node)) return FacadeNodes.toString(node);
        throw new JsonException("expected String, but was " + Types.name(node));
    }

    /**
     * Converts a node to a String with flexible type conversion.
     */
    public static String asString(Object node) {
        if (node == null) return null;
        if (node.getClass().isEnum()) return ((Enum<?>) node).name();
        if (FacadeNodes.isNode(node)) return FacadeNodes.asString(node);
        return node.toString();
    }

    /**
     * Converts a node to Character using strict conversion.
     */
    public static Character toChar(Object node) {
        if (node == null) return null;
        if (node instanceof Character) return (Character) node;
        String s = toString(node);
        return !s.isEmpty() ? s.charAt(0) : null;
    }

    /**
     * Converts a node to Character using lenient conversion.
     */
    public static Character asChar(Object node) {
        if (node == null) return null;
        if (node instanceof Character) return (Character) node;
        String s = asString(node);
        return !s.isEmpty() ? s.charAt(0) : null;
    }

    /**
     * Converts a node to a Number with strict type checking.
     */
    public static Number toNumber(Object node) {
        if (node == null) return null;
        if (node instanceof Number) return (Number) node;
        if (FacadeNodes.isNode(node)) return FacadeNodes.toNumber(node);
        throw new JsonException("expected Number, but was " + Types.name(node));
    }

    /**
     * Converts a node to a Number with flexible type conversion.
     */
    public static Number asNumber(Object node) {
        if (node == null) return null;
        if (node instanceof Number) return (Number) node;
        if (node instanceof Boolean) return (Boolean) node ? 1 : 0;
        if (node.getClass().isEnum()) return ((Enum<?>) node).ordinal();
        if (FacadeNodes.isNode(node)) return FacadeNodes.asNumber(node);
        try {
            String s = toString(node);
            return Numbers.parseNumber(s);
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Converts a node to a Long with strict type checking.
     */
    public static Long toLong(Object node) {
        Number n = toNumber(node);
        if (n == null) return null;
        return Numbers.toLong(n);
    }

    /**
     * Converts a node to a Long with flexible type conversion.
     */
    public static Long asLong(Object node) {
        Number n = asNumber(node);
        if (n == null) return null;
        return Numbers.toLong(n);
    }

    /**
     * Converts a node to an Integer with strict type checking.
     */
    public static Integer toInt(Object node) {
        Number n = toNumber(node);
        if (n == null) return null;
        return Numbers.toInt(n);
    }

    /**
     * Converts a node to an Integer with flexible type conversion.
     */
    public static Integer asInt(Object node) {
        Number n = asNumber(node);
        if (n == null) return null;
        return Numbers.toInt(n);
    }

    /**
     * Converts a node to a Short with strict type checking.
     */
    public static Short toShort(Object node) {
        Number n = toNumber(node);
        if (n == null) return null;
        return Numbers.toShort(n);
    }

    /**
     * Converts a node to a Short with flexible type conversion.
     */
    public static Short asShort(Object node) {
        Number n = asNumber(node);
        if (n == null) return null;
        return Numbers.toShort(n);
    }

    /**
     * Converts a node to Byte using strict conversion.
     */
    public static Byte toByte(Object node) {
        Number n = toNumber(node);
        if (n == null) return null;
        return Numbers.toByte(n);
    }

    /**
     * Converts a node to Byte using lenient conversion.
     */
    public static Byte asByte(Object node) {
        Number n = asNumber(node);
        if (n == null) return null;
        return Numbers.toByte(n);
    }

    /**
     * Converts a node to Double using strict conversion.
     */
    public static Double toDouble(Object node) {
        Number n = toNumber(node);
        if (n == null) return null;
        return Numbers.toDouble(n);
    }

    /**
     * Converts a node to Double using lenient conversion.
     */
    public static Double asDouble(Object node) {
        Number n = asNumber(node);
        if (n == null) return null;
        return Numbers.toDouble(n);
    }

    /**
     * Converts a node to Float using strict conversion.
     */
    public static Float toFloat(Object node) {
        Number n = toNumber(node);
        if (n == null) return null;
        return Numbers.toFloat(n);
    }

    /**
     * Converts a node to Float using lenient conversion.
     */
    public static Float asFloat(Object node) {
        Number n = asNumber(node);
        if (n == null) return null;
        return Numbers.toFloat(n);
    }

    /**
     * Converts a node to BigInteger using strict conversion.
     */
    public static BigInteger toBigInteger(Object node) {
        Number n = toNumber(node);
        if (n == null) return null;
        return Numbers.toBigInteger(n);
    }

    /**
     * Converts a node to BigInteger using lenient conversion.
     */
    public static BigInteger asBigInteger(Object node) {
        Number n = asNumber(node);
        if (n == null) return null;
        return Numbers.toBigInteger(n);
    }

    /**
     * Converts a node to BigDecimal using strict conversion.
     */
    public static BigDecimal toBigDecimal(Object node) {
        Number n = toNumber(node);
        if (n == null) return null;
        return Numbers.toBigDecimal(n);
    }

    /**
     * Converts a node to BigDecimal using lenient conversion.
     */
    public static BigDecimal asBigDecimal(Object node) {
        Number n = asNumber(node);
        if (n == null) return null;
        return Numbers.toBigDecimal(n);
    }

    /**
     * Converts a node to Boolean using strict conversion.
     */
    public static Boolean toBoolean(Object node) {
        if (node == null) return null;
        if (node instanceof Boolean) return (Boolean) node;
        if (FacadeNodes.isNode(node)) return FacadeNodes.toBoolean(node);
        throw new JsonException("expected Boolean, but was " + Types.name(node));
    }

    /**
     * Converts a node to Boolean using lenient conversion.
     */
    public static Boolean asBoolean(Object node) {
        if (node == null) return null;
        if (node instanceof Boolean) return (Boolean) node;
        if (node instanceof String) {
            String str = ((String) node).toLowerCase(Locale.ROOT);
            if ("true".equals(str) || "yes".equals(str) || "on".equals(str) || "1".equals(str)) return true;
            if ("false".equals(str) || "no".equals(str) || "off".equals(str) || "0".equals(str)) return false;
//            throw new JsonException("cannot convert String to Boolean: supported formats: true/false, yes/no, on/off, 1/0");
            return null;
        }
        if (node instanceof Number) {
            int i = ((Number) node).intValue();
            if (i == 1) return true;
            if (i == 0) return false;
//            throw new JsonException("cannot convert Number to Boolean: numeric values other than 0-false or 1-true");
            return null;
        }
        if (FacadeNodes.isNode(node)) return FacadeNodes.asBoolean(node);
        return null;
    }

    /**
     * Converts a node to JsonObject.
     * <p>
     * Existing {@link JsonObject} instances are returned as-is. {@link Map}
     * inputs are wrapped as the dynamic backing map. Other object-like sources
     * are materialized into a new {@link JsonObject} by copying their readable
     * entries.
     */
    @SuppressWarnings("unchecked")
    public static JsonObject toJsonObject(Object node) {
        if (node == null) return null;
        if (node instanceof JsonObject) return (JsonObject) node;
        if (node instanceof Map) return new JsonObject((Map<String, Object>) node);
        JsonObject jo = new JsonObject();
        jo.putAll(node);
        return jo;
    }

    /**
     * Converts a node to Map with Object values.
     * <p>
     * Existing {@link Map} instances are returned as-is. {@link JsonObject}
     * and other object-like sources are projected into a new map by readable
     * entries.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object node) {
        if (node == null) return null;
        if (node instanceof Map) return (Map<String, Object>) node;
        if (node instanceof JsonObject) return ((JsonObject) node).toMap();
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, NodeRegistry.PropertyInfo> entry : pi.readableProperties.entrySet()) {
                Object v = entry.getValue().invokeGetter(node);
                map.put(entry.getKey(), v);
            }
            return map;
        }
        if (FacadeNodes.isNode(node)) return FacadeNodes.toMap(node);
        throw new JsonException("expected Map, but was " + Types.name(node));
    }

    /**
     * Converts a node to typed Map.
     */
    public static <T> Map<String, T> toMap(Object node, Class<T> valueClazz) {
        return _toMap(node, Map.class, valueClazz);
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> _toMap(Object node, Class<?> mapType, Class<T> valueClazz) {
        if (node == null) return null;
        if ((mapType == null || mapType == Map.class) && node instanceof Map
                && (valueClazz == null || valueClazz == Object.class)) {
            return (Map<String, T>) node;
        }
        Map<String, T> map = NodeRegistry.newMapContainer(mapType, false);
        forEachObject(node, (k, v) -> {
            T value = to(v, valueClazz);
            map.put(k, value);
        });
        return map;
    }

    /**
     * Converts a node to JsonArray.
     * <p>
     * Existing {@link JsonArray} instances are returned as-is. {@link List}
     * inputs are wrapped as the backing list. Other array-like sources are
     * materialized into a new {@link JsonArray} by copying their readable
     * elements.
     */
    @SuppressWarnings("unchecked")
    public static JsonArray toJsonArray(Object node) {
        if (node == null) return null;
        if (node instanceof JsonArray) return (JsonArray) node;
        if (node instanceof List) return new JsonArray((List<Object>) node);
        JsonArray ja = new JsonArray();
        ja.addAll(node);
        return ja;
    }

    /**
     * Converts a node to List with Object values.
     * <p>
     * Existing {@link List} instances are returned as-is. {@link JsonArray}
     * and other array-like sources are projected into a new list by readable
     * element order.
     */
    @SuppressWarnings("unchecked")
    public static List<Object> toList(Object node) {
        if (node == null) return null;
        if (node instanceof List) return (List<Object>) node;
        if (node instanceof JsonArray) return ((JsonArray) node).toList();
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            List<Object> list = new ArrayList<>(len);
            for (int i = 0; i < len; i++) list.add(Array.get(node, i));
            return list;
        }
        if (node instanceof Set) {
            return new ArrayList<>((Set<Object>) node);
        }
        if (FacadeNodes.isNode(node)) return FacadeNodes.toList(node);
        throw new JsonException("expected List, but was " + Types.name(node));
    }

    /**
     * Converts a node to typed List.
     */
    public static <T> List<T> toList(Object node, Class<T> valueClazz) {
        return _toList(node, List.class, valueClazz);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> _toList(Object node, Class<?> listType, Class<T> valueClazz) {
        if (node == null) return null;
        if ((listType == null || listType == List.class) && node instanceof List
                && (valueClazz == null || valueClazz == Object.class)) return (List<T>) node;
        List<T> list = NodeRegistry.newListContainer(listType, false);
        forEachArray(node, (i, v) -> list.add(to(v, valueClazz)));
        return list;
    }

    /**
     * Converts a node to Object array.
     */
    @SuppressWarnings("unchecked")
    public static Object[] toArray(Object node) {
        if (node == null) return null;
        if (node instanceof List) return ((List<Object>) node).toArray();
        if (node instanceof JsonArray) return ((JsonArray) node).toArray();
        if (node.getClass().isArray()) {
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
        if (node instanceof Set) return ((Set<Object>) node).toArray();
        if (FacadeNodes.isNode(node)) return FacadeNodes.toArray(node);
        throw new JsonException("expected Array, but was " + Types.name(node));
    }

    /**
     * Converts a node to typed array.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(Object node, Class<T> clazz) {
        if (node == null) return null;
        if (node.getClass().isArray() && (clazz == null || clazz == Object.class)) {
            if (node.getClass().getComponentType().isPrimitive()) {
                int length = Array.getLength(node);
                Object[] arr = new Object[length];
                for (int i = 0; i < length; i++) {
                    arr[i] = Array.get(node, i); // Auto boxing
                }
                return (T[]) arr;
            } else {
                return (T[]) node;
            }
        }
        Class<?> componentType = Types.box(clazz);
        Object[] arr = (Object[]) Array.newInstance(componentType, sizeInArray(node));
        forEachArray(node, (i, v) -> Array.set(arr, i, to(v, componentType)));
        return (T[]) arr;
    }

    /**
     * Converts a node to Set with Object values.
     */
    @SuppressWarnings("unchecked")
    public static Set<Object> toSet(Object node) {
        if (node == null) return null;
        if (node instanceof List) return new LinkedHashSet<>((List<Object>) node);
        if (node instanceof JsonArray) return ((JsonArray) node).toSet();
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            Set<Object> set = new LinkedHashSet<>(len);
            for (int i = 0; i < len; i++) set.add(Array.get(node, i));
            return set;
        }
        if (node instanceof Set) return (Set<Object>) node;
        if (FacadeNodes.isNode(node)) return FacadeNodes.toSet(node);
        throw new JsonException("expected Set, but was " + Types.name(node));
    }

    /**
     * Converts a node to typed Set.
     */
    public static <T> Set<T> toSet(Object node, Class<T> valueClazz) {
        return _toSet(node, Set.class, valueClazz);
    }

    @SuppressWarnings("unchecked")
    private static <T> Set<T> _toSet(Object node, Class<?> setType, Class<T> valueClazz) {
        if (node == null) return null;
        if ((setType == null || setType == Set.class) && node instanceof Set
                && (valueClazz == null || valueClazz == Object.class)) return (Set<T>) node;
        Set<T> set = NodeRegistry.newSetContainer(setType, false);
        forEachArray(node, (i, v) -> set.add(to(v, valueClazz)));
        return set;
    }

    /**
     * Converts a node to a JOJO subtype.
     * <p>
     * A JOJO is any concrete {@link JsonObject} subclass other than
     * {@link JsonObject} itself. During conversion, declared properties are bound by
     * normal POJO rules. Unknown object members are retained as dynamic
     * properties unless {@link org.sjf4j.annotation.node.NodeBinding#readDynamic()}
     * disables that behavior.
     * <p>
     * This is a binding conversion, not a forced deep copy. Nested containers may
     * still alias source values when the target binding allows reuse.
     */
    @SuppressWarnings("unchecked")
    public static <T> T toJojo(Object node, Class<T> clazz) {
        if (!JsonObject.class.isAssignableFrom(clazz) || clazz == JsonObject.class)
            throw new JsonException("expected JOJO subtype, but was " + clazz.getName());
        if (node == null) return null;
        return (T) Sjf4j.global().nodeFacade().readNode(node, clazz, false);
    }

    /**
     * Converts a node to a JAJO subtype.
     * <p>
     * A JAJO is any concrete {@link JsonArray} subclass other than
     * {@link JsonArray} itself. The target instance is created first and then
     * populated with converted array elements in order.
     * <p>
     * This is a binding conversion, not a forced deep copy. Nested containers may
     * still alias source values when the target binding allows reuse.
     */
    @SuppressWarnings("unchecked")
    public static <T> T toJajo(Object node, Class<T> clazz) {
        if (!JsonArray.class.isAssignableFrom(clazz) || clazz == JsonArray.class)
            throw new JsonException("expected JAJO subtype, but was " + clazz.getName());
        if (node == null) return null;
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(clazz);
        JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
        forEachArray(node, jajo::add);
        return (T) jajo;
    }


    /**
     * Converts a node to a registered POJO type, including JOJO and JAJO subclasses.
     * <p>
     * In SJF4J terminology:
     * <ul>
     *     <li>POJO means a regular Java object bound by declared members</li>
     *     <li>JOJO means a {@link JsonObject} subtype with both declared properties and dynamic object properties</li>
     *     <li>JAJO means a {@link JsonArray} subtype with array semantics and a dedicated Java type</li>
     * </ul>
     *
     * <p>For regular POJO targets, properties are mapped by discovered property names, with
     * alias support from {@code @NodeProperty}. Constructor-arg mapping is used
     * when required by {@code @NodeCreator}; unmatched values are applied later
     * through setters when available.
     *
     * <p>For JOJO targets, object members that are not declared properties are
     * preserved in the dynamic map unless type binding disables dynamic reads.
     * For JAJO targets, array items are appended to the target subtype in order.
     * <p>
     * This is a binding conversion, not a forced deep copy. Nested containers may
     * still alias source values when the target binding allows reuse.
     */
    @SuppressWarnings("unchecked")
    public static <T> T toPojo(Object node, Class<T> clazz) {
        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
        if (ti.pojoInfo == null && ti.oneOfInfo == null) {
            throw new JsonException("class '" + clazz.getName() + "' is not a registered POJO");
        }
        return (T) Sjf4j.global().nodeFacade().readNode(node, clazz, false);
    }

    /**
     * Internal conversion with strict/lenient flag.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _to(Object node, Type type, boolean cross) {
        if (type == null || type == Object.class) return node;
        if (node == null) return null;

        Class<?> clazz = Types.rawBox(type);
        if (clazz.isInstance(node)) return node;

        if (clazz == String.class) {
            if (cross) return asString(node);
            else return toString(node);
        }
        if (Number.class.isAssignableFrom(clazz)) {
            if (cross) return Numbers.to(asNumber(node), clazz);
            else return Numbers.to(toNumber(node), clazz);
        }
        if (clazz == Boolean.class) {
            if (cross) return asBoolean(node);
            else return toBoolean(node);
        }
        if (Map.class.isAssignableFrom(clazz)) {
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> vc = Types.rawBox(vt);
            return _toMap(node, clazz, vc);
        }
        if (List.class.isAssignableFrom(clazz)) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> vc = Types.rawBox(vt);
            return _toList(node, clazz, vc);
        }

        if (clazz == JsonObject.class) return toJsonObject(node);
        if (JsonObject.class.isAssignableFrom(clazz)) return toJojo(node, clazz);

        if (clazz == JsonArray.class) return toJsonArray(node);
        if (JsonArray.class.isAssignableFrom(clazz)) return toJajo(node, clazz);
        if (clazz.isArray()) return toArray(node, clazz.getComponentType());
        if (Set.class.isAssignableFrom(clazz)) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> vc = Types.rawBox(vt);
            return _toSet(node, clazz, vc);
        }

        if (clazz == Character.class) {
            if (cross) return asChar(node);
            else return toChar(node);
        }
        if (clazz.isEnum()) {
            if (cross) return asEnum(node, (Class<Enum>) clazz);
            return toEnum(node, (Class<Enum>) clazz);
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
        if (!ti.isNone()) {
            return Sjf4j.global().nodeFacade().readNode(node, clazz, false);
        }

        throw new JsonException("expected " + clazz.getName() + ", but was " + Types.name(node));
    }

    /**
     * Converts a node to target type using strict conversion.
     * <p>
     * Strict mode does not coerce incompatible values across logical domains
     * (for example arbitrary string to number/boolean). Type mismatch throws
     * {@link JsonException}.
     */
    @SuppressWarnings("unchecked")
    public static <T> T to(Object node, Class<T> clazz) {
        return (T) _to(node, clazz, false);
    }

    @SuppressWarnings("unchecked")
    public static <T> T to(Object node, TypeReference<T> type) {
        return (T) _to(node, type.getType(), false);
    }

    /**
     * Converts a node to target type using lenient conversion.
     * <p>
     * Lenient mode allows cross-type coercion when possible (for example string
     * to number/boolean). Failed coercions generally yield {@code null} in the
     * lower-level converters.
     */
    @SuppressWarnings({"unchecked"})
    public static <T> T as(Object node, Class<T> clazz) {
        return (T) _to(node, clazz, true);
    }


    /// Basic

    /**
     * Compares two values using node semantics instead of Java type identity.
     * <p>
     * Object-like nodes are compared by readable key/value pairs, array-like
     * nodes are compared by order and element values, and number values are
     * compared by numeric value (not boxed type).
     */
    public static boolean equals(Object source, Object target) {
        if (target == source) return true;
        if (source == null || target == null) return false;

        JsonType jtSource = JsonType.of(source);
        JsonType jtTarget = JsonType.of(target);
        if (jtSource.isNumber() && jtTarget.isNumber()) {
            return Numbers.compare((Number) source, (Number) target) == 0;
        } else if (jtSource.isString() && jtTarget.isString()) {
            return toString(source).equals(toString(target));
        } else if (jtSource.isValue() && jtTarget.isValue()) {
            return source.equals(target);
        } else if (jtSource.isObject() && jtTarget.isObject()) {
            if (sizeInObject(source) != sizeInObject(target)) return false;
            return !anyMatchInObject(source, (k, subSource) -> {
                Object subTarget = getInObject(target, k);
                if (subTarget == null && !containsInObject(target, k)) return true;
                return !equals(subSource, subTarget);
            });
        } else if (jtSource.isArray() && jtTarget.isArray()) {
            if (sizeInArray(source) != sizeInArray(target)) return false;
            Iterator<Object> itSource = iteratorInArray(source);
            Iterator<Object> itTarget = iteratorInArray(target);
            while (itSource.hasNext() && itTarget.hasNext()) {
                Object subSource = itSource.next();
                Object subTarget = itTarget.next();
                if (!equals(subSource, subTarget)) return false;
            }
            return true;
        } else if (jtSource.isUnknown() && jtTarget.isUnknown()) {
            return Objects.equals(source, target);
        }
        return false;
    }

    /**
     * Computes a hash code aligned with {@link #equals(Object, Object)} node semantics.
     * <p>
     * Object-like nodes are hashed from readable key/value pairs (order-insensitive
     * for object members), while array-like nodes are hashed in iteration order.
     */
    public static int hash(Object node) {
        if (node == null) return 0;
        JsonType jt = JsonType.of(node);
        if (jt.isNumber()) {
            return Numbers.hash((Number) node);
        } else if (jt.isValue()) {
            return node.hashCode();
        } else if (jt.isObject()) {
            final int[] hash = {1};
            forEachObject(node, (k, v) -> {
                // disorder
                hash[0] += 31 * k.hashCode() + hash(v);
            });
            return hash[0];
        } else if (jt.isArray()) {
            int hash = 1;
            Iterator<Object> it = iteratorInArray(node);
            while (it.hasNext()) {
                Object item = it.next();
                hash = 31 * hash + hash(item);
            }
            return hash;
        } else if (jt.isUnknown()) {
            return Objects.hashCode(node);
        }
        return 0;
    }

    /**
     * Returns a shallow copy of the given node.
     * <p>
     * Container nodes copy only the outer container; nested child nodes are shared.
     * For POJO/JOJO/JAJO targets, a new instance is created and direct field/item
     * values are transferred without deep recursion.
     */
    @SuppressWarnings({"unchecked", "SuspiciousSystemArraycopy"})
    public static <T> T copy(T node) {
        if (node == null) return null;

        if (node instanceof String || node instanceof Number || node instanceof Boolean) {
            return node;
        }

        Class<?> rawClazz = node.getClass();
        if (node instanceof Map) {
            Map<String, Object> map = NodeRegistry.newMapContainer(rawClazz, true);
            map.putAll((Map<String, Object>) node);
            return (T) map;
        }
        if (node instanceof List) {
            List<Object> list = NodeRegistry.newListContainer(rawClazz, true);
            list.addAll((List<Object>) node);
            return (T) list;
        }

        if (rawClazz == JsonObject.class) {
            return (T) new JsonObject(((JsonObject) node).toMap());
        }
        if (node instanceof JsonObject) {
            JsonObject srcJo = (JsonObject) node;
            NodeRegistry.PojoInfo pojoInfo = NodeRegistry.registerPojoOrElseThrow(node.getClass());
            NodeRegistry.PojoCreationSession session = new NodeRegistry.PojoCreationSession(pojoInfo.creatorInfo, srcJo.size());

            for (Map.Entry<String, Object> entry : srcJo.entrySet()) {
                String key = entry.getKey();
                int argIdx = pojoInfo.creatorInfo.getArgIndexOrAlias(key);
                if (argIdx >= 0) {
                    session.acceptCtorArg(argIdx, entry.getValue());
                } else {
                    session.acceptDynamic(key, entry.getValue());
                }
            }
            JsonObject newJo = (JsonObject) session.finish();
            return (T) newJo;
        }
        if (rawClazz == JsonArray.class) {
            return (T) new JsonArray(((JsonArray) node).toList());
        }
        if (node instanceof JsonArray) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            jajo.addAll(node);
            return (T) jajo;
        }
        if (rawClazz.isArray()) {
            int len = Array.getLength(node);
            Object arr = Array.newInstance(node.getClass().getComponentType(), len);
            System.arraycopy(node, 0, arr, 0, len);
            return (T) arr;
        }
        if (node instanceof Set) {
            Set<Object> set = NodeRegistry.newSetContainer(rawClazz, true);
            set.addAll((Set<Object>) node);
            return (T) set;
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
        if (ti.valueCodecInfo != null) {
            return (T) ti.valueCodecInfo.valueCopy(node);
        } else if (ti.pojoInfo != null) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(node.getClass());
            NodeRegistry.PojoCreationSession session = new NodeRegistry.PojoCreationSession(pi.creatorInfo, pi.propertyCount);

            for (Map.Entry<String, NodeRegistry.PropertyInfo> entry : pi.readableProperties.entrySet()) {
                String key = entry.getKey();
                NodeRegistry.PropertyInfo fi = entry.getValue();
                Object v = fi.invokeGetter(node);
                int argIdx = pi.creatorInfo.getArgIndexOrAlias(key);
                if (argIdx >= 0) {
                    session.acceptCtorArg(argIdx, v);
                } else {
                    session.acceptProperty(fi, v);
                }
            }
            Object pojo = session.finish();
            return (T) pojo;
        }

        if (FacadeNodes.isNode(node)) {
            throw new JsonException("cannot copy facade node '" + Types.name(node) + "'");
        }

        return node;
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
     *   <li>{@code {..}}       - Map</li>
     *   <li>{@code J{..}}      - JsonObject</li>
     *   <li>{@code @Type{..}}  - POJO / JOJO</li>
     *   <li>{@code [..]}       - List</li>
     *   <li>{@code J[..]}      - JsonArray</li>
     *   <li>{@code @Type[..]}  - JAJO</li>
     *   <li>{@code A[..]}      - Array</li>
     *   <li>{@code S[..]}      - Set</li>
     *   <li>{@code @Type#raw}  - NodeValue</li>
     *   <li>{@code !node}      - Unknown</li>
     * </ul>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * J{email=.com, user=@User{*id=1, name={a=b, c=d}}, arr=A[haha, xi, 1], date=@LocalDate#2025-12-29}
     * }</pre>
     *
     * @param node the object to inspect
     * @return a compact structural string
     */
    public static String inspect(Object node) {
        StringBuilder sb = new StringBuilder();
        _inspect(node, sb, false);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void _inspect(Object node, StringBuilder sb, boolean shapeOnly) {
        if (node == null) {
            sb.append((Object) null);
            return;
        }

        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            sb.append("{");
            int idx = 0;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (idx++ > 0) sb.append(", ");
                sb.append(entry.getKey()).append("=");
                _inspect(entry.getValue(), sb, shapeOnly);
            }
            sb.append("}");
            return;
        }
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            sb.append("[");
            for (int i = 0; i < list.size(); i++) {
                Object v = list.get(i);
                if (i > 0) sb.append(", ");
                _inspect(v, sb, shapeOnly);
            }
            sb.append("]");
            return;
        }

        Class<?> rawClazz = node.getClass();
        if (rawClazz == JsonObject.class) {
            JsonObject jo = (JsonObject) node;
            sb.append("J{");
            int idx = 0;
            for (Map.Entry<String, Object> entry : jo.entrySet()) {
                if (idx++ > 0) sb.append(", ");
                sb.append(entry.getKey()).append("=");
                _inspect(entry.getValue(), sb, shapeOnly);
            }
            sb.append("}");
            return;
        }
        if (node instanceof JsonObject) {
            JsonObject jo = (JsonObject) node;
            sb.append("@").append(node.getClass().getSimpleName()).append("{");
            NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
            int[] idx = new int[1];
            jo.forEach((k, v) -> {
                if (idx[0]++ > 0) sb.append(", ");
                if (pi != null && pi.properties.containsKey(k)) {
                    sb.append("*");
                }
                sb.append(k).append("=");
                _inspect(v, sb, shapeOnly);
            });
            sb.append("}");
            return;
        }
        if (rawClazz == JsonArray.class) {
            JsonArray ja = (JsonArray) node;
            sb.append("J[");
            for (int i = 0; i < ja.size(); i++) {
                Object v = ja.getNode(i);
                if (i > 0) sb.append(", ");
                _inspect(v, sb, shapeOnly);
            }
            sb.append("]");
            return;
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            sb.append("@").append(node.getClass().getSimpleName()).append("[");
            for (int i = 0; i < ja.size(); i++) {
                Object v = ja.getNode(i);
                if (i > 0) sb.append(", ");
                _inspect(v, sb, shapeOnly);
            }
            sb.append("]");
            return;
        }
        if (rawClazz.isArray()) {
            int len = Array.getLength(node);
            sb.append("A[");
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(", ");
                _inspect(Array.get(node, i), sb, shapeOnly);
            }
            sb.append("]");
            return;
        }
        if (node instanceof Set) {
            Set<Object> set = (Set<Object>) node;
            sb.append("S[");
            int i = 0;
            for (Object v : set) {
                if (i++ > 0) sb.append(", ");
                _inspect(v, sb, shapeOnly);
            }
            sb.append("]");
            return;
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
        if (ti.valueCodecInfo != null) {
            Object raw = ti.valueCodecInfo.valueToRaw(node);
            sb.append("@").append(rawClazz.getSimpleName()).append("#");
            _inspect(raw, sb, shapeOnly);
            return;
        }
        if (ti.pojoInfo != null) {
            NodeRegistry.PojoInfo pi = ti.pojoInfo;
            sb.append("@").append(rawClazz.getSimpleName()).append("{");
            int idx = 0;
            for (Map.Entry<String, NodeRegistry.PropertyInfo> entry : pi.readableProperties.entrySet()) {
                if (idx++ > 0) sb.append(", ");
                sb.append("*").append(entry.getKey()).append("=");
                Object v = entry.getValue().invokeGetter(node);
                _inspect(v, sb, shapeOnly);
            }
            sb.append("}");
            return;
        }

        if (NodeKind.of(node).isUnknown()) {
            sb.append("!");
        }

        if (shapeOnly) {
            sb.append(rawClazz.getSimpleName());
        } else {
            sb.append(node);
        }
    }

    /**
     * Returns a compact structural shape string of the given object.
     * <p>
     * The output is similar to {@link #inspect(Object)}, but does not print the
     * actual value content.
     *
     * <h3>Examples</h3>
     * <pre>{@code
     * Nodes.shape(JsonObject.of("name", "han", "age", 18))
     * // => J{name=String, age=Integer}
     *
     * Nodes.shape(JsonArray.of("a", "b"))
     * // => J[String, String]
     * }</pre>
     *
     * @param node the object to inspect structurally
     * @return a compact structural shape string
     */
    public static String shape(Object node) {
        StringBuilder sb = new StringBuilder();
        _inspect(node, sb, true);
        return sb.toString();
    }



    /// Visit

    /**
     * Visits each readable entry in an object-like node.
     */
    @SuppressWarnings("unchecked")
    public static void forEachObject(Object node, BiConsumer<String, Object> consumer) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(consumer, "consumer");
        if (node instanceof Map) {
            ((Map<String, Object>) node).forEach(consumer);
            return;
        }
        if (node instanceof JsonObject) {
            ((JsonObject) node).forEach(consumer);
            return;
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            for (Map.Entry<String, NodeRegistry.PropertyInfo> entry : pi.readableProperties.entrySet()) {
                Object value = entry.getValue().invokeGetter(node);
                consumer.accept(entry.getKey(), value);
            }
            return;
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.forEachObject(node, consumer);
            return;
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }


    /**
     * Returns true if any readable object entry matches the predicate.
     */
    @SuppressWarnings("unchecked")
    public static boolean anyMatchInObject(Object node, BiPredicate<String, Object> predicate) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(predicate, "predicate");
        if (node instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) node).entrySet()) {
                if (predicate.test(entry.getKey(), entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).anyMatch(predicate);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            for (Map.Entry<String, NodeRegistry.PropertyInfo> entry : pi.readableProperties.entrySet()) {
                Object value = entry.getValue().invokeGetter(node);
                if (predicate.test(entry.getKey(), value)) {
                    return true;
                }
            }
            return false;
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.anyMatchObject(node, predicate);
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }


    /**
     * Replaces object-entry values in place using the given mapper.
     * <p>
     * The mapper receives each readable-and-writable entry key and current value,
     * and returns the new value to store. Returns true when at least one entry
     * changes by reference.
     */
    @SuppressWarnings("unchecked")
    public static boolean replaceAllInObject(Object node, BiFunction<String, Object, Object> replacer) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(replacer, "replacer");
        if (node instanceof Map) {
            boolean changed = false;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) node).entrySet()) {
                Object oldValue = entry.getValue();
                Object newValue = replacer.apply(entry.getKey(), oldValue);
                if (oldValue != newValue) {
                    entry.setValue(newValue);
                    changed = true;
                }
            }
            return changed;
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).replaceAll(replacer);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            boolean changed = false;
            for (Map.Entry<String, NodeRegistry.PropertyInfo> entry : pi.readableProperties.entrySet()) {
                NodeRegistry.PropertyInfo fi = entry.getValue();
                if (!fi.hasSetter()) {
                    continue;
                }
                Object oldValue = fi.invokeGetter(node);
                Object newValue = replacer.apply(entry.getKey(), oldValue);
                if (oldValue != newValue) {
                    fi.invokeSetter(node, newValue);
                    changed = true;
                }
            }
            return changed;
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.replaceAllInObject(node, replacer);
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }

    /**
     * Removes object properties that match the predicate.
     * <p>
     * This operation applies to removable object properties only. Structural
     * POJO properties are not considered removable properties and therefore are left
     * unchanged. For facade object nodes, matching keys are collected first and
     * removed afterward so live key views remain safe to traverse.
     */
    @SuppressWarnings("unchecked")
    public static boolean removeIfInObject(Object node, BiPredicate<String, Object> predicate) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(predicate, "predicate");

        if (node instanceof Map) {
            return ((Map<String, Object>) node).entrySet().removeIf(entry ->
                    predicate.test(entry.getKey(), entry.getValue()));
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).removeIf(entry -> predicate.test(entry.getKey(), entry.getValue()));
        }
        if (NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo != null) {
            return false;
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.removeIfInObject(node, predicate);
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }


    /**
     * Visits each element in an array-like node.
     */
    @SuppressWarnings("unchecked")
    public static void forEachArray(Object node, BiConsumer<Integer, Object> consumer) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(consumer, "consumer");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            for (int i = 0; i < list.size(); i++) consumer.accept(i, list.get(i));
            return;
        }
        if (node instanceof JsonArray) {
            ((JsonArray) node).forEach(consumer);
            return;
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            for (int i = 0; i < len; i++) consumer.accept(i, Array.get(node, i));
            return;
        }
        if (node instanceof Set) {
            Set<Object> set = (Set<Object>) node;
            int i = 0;
            for (Object v : set) consumer.accept(i++, v);
            return;
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.forEachArray(node, consumer);
            return;
        }
        throw new JsonException("expected Array node, but was " + Types.name(node));
    }

    /**
     * Returns true if any element matches the predicate.
     */
    @SuppressWarnings("unchecked")
    public static boolean anyMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(predicate, "predicate");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            for (int i = 0; i < list.size(); i++) {
                if (predicate.test(i, list.get(i))) return true;
            }
            return false;
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            for (int i = 0; i < ja.size(); i++) {
                if (predicate.test(i, ja.getNode(i))) return true;
            }
            return false;
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            for (int i = 0; i < len; i++) {
                if (predicate.test(i, Array.get(node, i))) return true;
            }
            return false;
        }
        if (node instanceof Set) {
            Set<Object> set = (Set<Object>) node;
            int i = 0;
            for (Object v : set) {
                if (predicate.test(i++, v)) return true;
            }
            return false;
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.anyMatchArray(node, predicate);
        }
        throw new JsonException("expected Array node, but was " + Types.name(node));
    }

    /**
     * Returns the number of readable members in an object-like node.
     */
    public static int sizeInObject(Object node) {
        Objects.requireNonNull(node, "node");
        if (node instanceof Map) {
            return ((Map<?, ?>) node).size();
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).size();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            return pi.readablePropertyCount;
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.sizeInObject(node);
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }

    /**
     * Returns the number of elements in an array-like node.
     */
    public static int sizeInArray(Object node) {
        Objects.requireNonNull(node, "node");
        if (node instanceof List) {
            return ((List<?>) node).size();
        }
        if (node instanceof JsonArray) {
            return ((JsonArray) node).size();
        }
        if (node.getClass().isArray()) {
            return Array.getLength(node);
        }
        if (node instanceof Set) {
            return ((Set<?>) node).size();
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.sizeInArray(node);
        }
        throw new JsonException("expected Array node, but was " + Types.name(node));
    }

    /**
     * Returns the readable key set for an object-like node.
     */
    @SuppressWarnings("unchecked")
    public static Set<String> keySetInObject(Object node) {
        Objects.requireNonNull(node, "node");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).keySet();
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).keySet();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            return pi.readableProperties.keySet();
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.keySetInObject(node);
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }

    /**
     * Returns the readable entry set for an object-like node.
     */
    @SuppressWarnings("unchecked")
    public static Set<Map.Entry<String, Object>> entrySetInObject(Object node) {
        Objects.requireNonNull(node, "node");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).entrySet();
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).entrySet();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            return new AbstractSet<Map.Entry<String, Object>>() {
                @Override
                public Iterator<Map.Entry<String, Object>> iterator() {
                    return new Iterator<Map.Entry<String, Object>>() {
                        private final Iterator<Map.Entry<String, NodeRegistry.PropertyInfo>> fieldIterator =
                                pi.readableProperties.entrySet().iterator();
                        @Override
                        public boolean hasNext() {
                            return fieldIterator.hasNext();
                        }

                        @Override
                        public Map.Entry<String, Object> next() {
                            Map.Entry<String, NodeRegistry.PropertyInfo> entry = fieldIterator.next();
                            Object value = entry.getValue().invokeGetter(node);
                            return new AbstractMap.SimpleEntry<>(entry.getKey(), value);
                        }
                    };
                }

                @Override
                public int size() {
                    return pi.readablePropertyCount;
                }
            };
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.entrySetInObject(node);
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }

    /**
     * Returns an iterator over an array-like node.
     */
    @SuppressWarnings("unchecked")
    public static Iterator<Object> iteratorInArray(Object node) {
        Objects.requireNonNull(node, "node");
        if (node instanceof List) {
            return ((List<Object>) node).iterator();
        }
        if (node instanceof JsonArray) {
            return ((JsonArray) node).iterator();
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            return new Iterator<Object>() {
                int i = 0;
                public boolean hasNext() {
                    return i < len;
                }
                public Object next() {
                    return Array.get(node, i++);
                }
            };
        }
        if (node instanceof Set) {
            return ((Set<Object>) node).iterator();
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.iteratorInArray(node);
        }
        throw new JsonException("expected Array node, but was " + Types.name(node));
    }

    /**
     * Returns true when an object-like node contains a readable key.
     */
    @SuppressWarnings("unchecked")
    public static boolean containsInObject(Object node, String key) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(key, "key");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).containsKey(key);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).containsKey(key);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            return pi.readableProperties.containsKey(key);
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.containsInObject(node, key);
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }

    /**
     * Returns whether an index is valid for an array-like node.
     * <p>
     * Negative indexes are normalized against current size.
     */
    public static boolean containsInArray(Object node, int idx) {
        int len = sizeInArray(node);
        idx = idx < 0 ? len + idx : idx;
        return idx >= 0 && idx < len;
    }

    /**
     * Gets a value by key from an object-like node.
     * <p>
     * Only readable members participate in this view. For POJO nodes, properties
     * without a getter behave as absent and return {@code null}.
     */
    public static Object getInObject(Object node, String key) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(key, "key");
        if (node instanceof Map) {
            return ((Map<?, ?>) node).get(key);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).getNode(key);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            NodeRegistry.PropertyInfo fi = pi.readableProperties.get(key);
            return fi != null ? fi.invokeGetter(node) : null;
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.getInObject(node, key);
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }

    /**
     * Gets a value by key from an object-like node and converts it to the
     * requested target type.
     */
    public static <T> T getInObject(Object node, String key, Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        Object sub = getInObject(node, key);
        return to(sub, clazz);
    }

    /**
     * Gets a value by index from an array-like node.
     * <p>
     * Negative indexes are supported ({@code -1} means last element). For List,
     * Array, and Set, out-of-range access returns {@code null}. JsonArray behavior
     * follows {@link JsonArray#getNode(int)}.
     */
    @SuppressWarnings("unchecked")
    public static Object getInArray(Object node, int idx) {
        Objects.requireNonNull(node, "node");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx < 0 || idx >= list.size()) {
                return null;
            } else {
                return list.get(idx);
            }
        }
        if (node instanceof JsonArray) {
            return ((JsonArray) node).getNode(idx);
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            idx = idx < 0 ? len + idx : idx;
            if (idx < 0 || idx >= len) {
                return null;
            } else {
                return Array.get(node, idx);
            }
        }
        if (node instanceof Set) {
            throw new JsonException("cannot call getInArray() on an unordered Java Set");
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.getInArray(node, idx);
        }
        throw new JsonException("expected Array node, but was " + Types.name(node));
    }

    /**
     * Gets a value by index from an array-like node and converts it to the
     * requested target type.
     */
    public static <T> T getInArray(Object node, int idx, Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        Object sub = getInArray(node, idx);
        return to(sub, clazz);
    }

    /**
     * Mutable holder used by access helpers to report child node metadata.
     * <p>
     * Callers typically reuse one instance across repeated lookups to avoid
     * allocating short-lived result wrappers.
     * <p>
     * {@code getAccess*} methods interpret {@link #present} as readable-location
     * existence and do not update {@link #puttable}. {@code putAccess*} methods
     * fill {@link #type}/{@link #puttable} for write and auto-create paths and
     * do not update {@link #present}.
     */
    public static final class Access {
        /** child value (can be null) */
        public Object node;
        /** static Type of child resolved by the access helper */
        public Type type;
        /** whether the child location currently exists, even when its value is null */
        public boolean present;
        /**
         * Indicates whether public Nodes/JsonPath write operations can put or
         * auto-create at this location.
         * false means the container is locked (e.g. POJO without such field).
         */
        public boolean puttable;
    }

    /**
     * Resolves readable object-child access and fills {@link Access}.
     * <p>
     * The output distinguishes present-null from missing values under read
     * semantics. Write-only POJO properties are not readable and therefore are
     * reported as absent.
     */
    @SuppressWarnings("unchecked")
    public static void getAccessInObject(Object node, String key, Access out) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(out, "out");

        out.type = Object.class;
        out.node = null;
        out.present = false;
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            out.node = map.get(key);
            out.present = out.node != null || map.containsKey(key);
            return;
        }
        if (node.getClass() == JsonObject.class) {
            JsonObject jo = (JsonObject) node;
            out.node = jo.getNode(key);
            out.present = out.node != null || jo.containsKey(key);
            return;
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            NodeRegistry.PropertyInfo fi = pi.readableProperties.get(key);
            if (fi != null) {
                out.node = fi.invokeGetter(node);
                out.type = fi.type;
                out.present = true;
                return;
            }
            if (pi.isJojo) {
                JsonObject jo = (JsonObject) node;
                out.node = jo.getNode(key);
                out.present = out.node != null || jo.containsKey(key);
            }
            return;
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.getAccessInObject(node, key, out);
            return;
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }

    /**
     * Resolves writable object-child access and fills {@link Access} with node/type metadata.
     * <p>
     * The output describes the current child value, inferred static type, and
     * whether public write APIs can create or replace content at this location.
     */
    @SuppressWarnings("unchecked")
    public static void putAccessInObject(Object node, Type type, String key, Access out) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(out, "out");

        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            out.node = map.get(key);
            out.type = Types.resolveTypeArgument(type, Map.class, 1);
            out.puttable = true;
            return;
        }
        if (node.getClass() == JsonObject.class) {
            JsonObject jo = (JsonObject) node;
            out.node = jo.getNode(key);
            out.type = Object.class;
            out.puttable = true;
            return;
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            NodeRegistry.PropertyInfo fi = pi.properties.get(key);
            if (fi != null) {
                out.node = fi.hasGetter() ? fi.invokeGetter(node) : null;
                out.type = fi.type;
                out.puttable = fi.hasSetter();
                return;
            }
            if (pi.isJojo) {
                JsonObject jo = (JsonObject) node;
                out.node = jo.getNode(key);
                out.type = Object.class;
                out.puttable = true;
                return;
            }
            out.node = null;
            out.type = Object.class;
            out.puttable = false;
            return;
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.putAccessInObject(node, type, key, out);
            return;
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));

    }

    /**
     * Resolves readable array-child access and fills {@link Access}.
     * <p>
     * The output distinguishes present-null from missing values under read
     * semantics. Negative indexes are normalized.
     */
    @SuppressWarnings("unchecked")
    public static void getAccessInArray(Object node, int idx, Access out) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(out, "out");

        out.type = Object.class;
        out.node = null;
        out.present = false;
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx >= 0 && idx < list.size()) {
                out.node = list.get(idx);
                out.present = true;
            }
            return;
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            idx = idx < 0 ? ja.size() + idx : idx;
            if (idx >= 0 && idx < ja.size()) {
                out.node = ja.getNode(idx);
                out.present = true;
            }
            return;
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            idx = idx < 0 ? len + idx : idx;
            if (idx >= 0 && idx < len) {
                out.node = Array.get(node, idx);
                out.type = node.getClass().getComponentType();
                out.present = true;
            }
            return;
        }
        if (node instanceof Set) {
            throw new JsonException("cannot call getAccessInArray() on an unordered Java Set");
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.getAccessInArray(node, idx, out);
            return;
        }
        throw new JsonException("expected Array node, but was " + Types.name(node));
    }

    /**
     * Resolves writable array-child access and fills {@link Access} with node/type metadata.
     * <p>
     * Negative indexes are normalized. Indexed access is reported as puttable only
     * when the normalized index already exists.
     * <p>
     * If {@code idx == null}, it is treated as append-to-tail and remains puttable
     * with {@code node == null}.
     */
    @SuppressWarnings("unchecked")
    public static void putAccessInArray(Object node, Type type, Integer idx, Access out) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(out, "out");

        out.type = Object.class;
        out.node = null;
        out.puttable = true;
        if (node instanceof List) {
            out.type = Types.resolveTypeArgument(type, List.class, 0);
            if (idx == null) return;
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx >= 0 && idx < list.size()) {
                out.node = list.get(idx);
                return;
            }
            out.puttable = false;
            return;
        }
        if (node instanceof JsonArray) {
            if (idx == null) return;
            JsonArray ja = (JsonArray) node;
            idx = idx < 0 ? ja.size() + idx : idx;
            if (idx >= 0 && idx < ja.size()) {
                out.node = ja.getNode(idx);
                return;
            }
            out.puttable = false;
            return;
        }
        if (node.getClass().isArray()) {
            out.type = node.getClass().getComponentType();
            if (idx != null) {
                int len = Array.getLength(node);
                idx = idx < 0 ? len + idx : idx;
                if (idx >= 0 && idx < len) {
                    out.node = Array.get(node, idx);
                    return;
                }
            }
            out.puttable = false;
            return;
        }
        if (node instanceof Set) {
            if (idx == null) return;
            throw new JsonException("cannot call putAccessInArray() on an unordered Java Set");
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.putAccessInArray(node, type, idx, out);
            return;
        }
        throw new JsonException("expected Array node, but was " + Types.name(node));
    }


    /**
     * Puts a value into an object-like node and returns the previous value when
     * the target shape exposes one.
     * <p>
     * For POJO nodes, only discovered properties are writable; unknown keys fail.
     * POJO property writes do not read back the old value and therefore return
     * {@code null}.
     */
    @SuppressWarnings("unchecked")
    public static Object putInObject(Object node, String key, Object value) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(key, "key");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).put(key, value);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).put(key, value);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            NodeRegistry.PropertyInfo fi = pi.properties.get(key);
            if (fi != null) {
                fi.invokeSetter(node, value);
                return null;
            } else {
                throw new JsonException("unknown property '" + key + "' in POJO '" +
                        node.getClass().getName() + "'");
            }
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.putInObject(node, key, value);
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }

    /**
     * Removes a key from an object-like node and returns the previous value.
     * <p>
     * Removal is supported for Map/JsonObject. POJO fields are structural and
     * cannot be removed.
     */
    @SuppressWarnings("unchecked")
    public static Object removeInObject(Object node, String key) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(key, "key");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).remove(key);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).remove(key);
        }
        if (NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo != null) {
            throw new JsonException("cannot remove field '" + key + "' from POJO '" +
                    node.getClass().getName() + "'");
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.removeInObject(node, key);
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }

    /**
     * Computes and stores an object value when the current value is null.
     * <p>
     * Semantics follow {@link Map#computeIfAbsent(Object, Function)}: a non-null
     * current value is returned as-is; otherwise the mapping function is invoked,
     * and a non-null result is stored before being returned.
     */
    @SuppressWarnings("unchecked")
    public static <T> T computeIfAbsentInObject(Object node, String key, Function<String, T> computer) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(computer, "computer");
        if (node instanceof Map) {
            return ((Map<String, T>) node).computeIfAbsent(key, computer);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).computeIfAbsent(key, computer);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null) {
            NodeRegistry.PropertyInfo fi = pi.properties.get(key);
            if (fi != null) {
                T old = fi.hasGetter() ? (T) fi.invokeGetter(node) : null;
                if (old != null) {
                    return old;
                }
                T newNode = computer.apply(key);
                if (newNode != null) {
                    fi.invokeSetter(node, newNode);
                }
                return newNode;
            } else {
                throw new JsonException("unknown property '" + key + "' in POJO '" +
                        node.getClass().getName() + "'");
            }
        }
        if (FacadeNodes.isNode(node)) {
            T old = (T) FacadeNodes.getInObject(node, key);
            if (old != null) {
                return old;
            }
            T newNode = computer.apply(key);
            if (newNode != null) {
                FacadeNodes.putInObject(node, key, newNode);
            }
            return newNode;
        }
        throw new JsonException("expected Object node, but was " + Types.name(node));
    }

    /**
     * Sets at an existing index or appends when the index equals the array size.
     * <p>
     * For {@code List} and {@code JsonArray}: when {@code idx == size} the value is appended;
     * otherwise delegates to {@link #setInArray(Object, int, Object)} (which rejects out-of-range
     * indices). Negative indices are resolved by {@code setInArray}, not treated as append.
     *
     * @return the previous value at the index when replacing, or {@code null} when appending
     */
    public static Object putInArray(Object node, int idx, Object value) {
        return _putInArray(node, idx, value, true);
    }

    /**
     * Sets a value in an array-like node by index.
     * <p>
     * For List/JsonArray: only existing normalized indexes may be replaced.
     * For Java arrays: only in-range replacement is allowed. Negative indexes are
     * supported.
     */
    public static Object setInArray(Object node, int idx, Object value) {
        Objects.requireNonNull(node, "node");
        return _putInArray(node, idx, value, false);
    }

    @SuppressWarnings("unchecked")
    private static Object _putInArray(Object node, int idx, Object value, boolean allowAppend) {
        Objects.requireNonNull(node, "node");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            int size = list.size();
            idx = idx < 0 ? size + idx : idx;
            if (idx >= 0 && idx < size) {
                return list.set(idx, value);
            }
            if (allowAppend && idx == size) {
                list.add(value);
                return null;
            }
            throw new JsonException("cannot set at index " + idx + " in List of size " + size);
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            if (ja.containsIndex(idx)) {
                return ja.set(idx, value);
            }
            if (allowAppend && idx == ja.size()) {
                ja.add(value);
                return null;
            }
            throw new JsonException("cannot set at index " + idx + " in JsonArray of size " + ja.size());
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            idx = idx < 0 ? len + idx : idx;
            if (idx >= 0 && idx < len) {
                Object old = Array.get(node, idx);
                Array.set(node, idx, value);
                return old;
            }
            if (allowAppend && idx == len) {
                throw new JsonException("cannot append to a Java array");
            }
            throw new JsonException("cannot set at index " + idx + " in Java array of size " + len);
        }
        if (node instanceof Set) {
            throw new JsonException("cannot set by index on an unordered Java Set");
        }
        if (FacadeNodes.isNode(node)) {
            if (allowAppend && FacadeNodes.sizeInArray(node) == idx) {
                FacadeNodes.addInArray(node, value);
                return null;
            }
            return FacadeNodes.setInArray(node, idx, value);
        }
        throw new JsonException("expected Array node, but was " + Types.name(node));
    }

    /**
     * Appends a value to an array-like node.
     * <p>
     * Java arrays are fixed-size and therefore not appendable.
     */
    @SuppressWarnings("unchecked")
    public static void addInArray(Object node, Object value) {
        Objects.requireNonNull(node, "node");
        if (node instanceof List) {
            ((List<Object>) node).add(value);
            return;
        }
        if (node instanceof JsonArray) {
            ((JsonArray) node).add(value);
            return;
        }
        if (node.getClass().isArray()) {
            throw new JsonException("cannot append to a Java array");
        }
        if (node instanceof Set) {
            ((Set<Object>) node).add(value);
            return;
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.addInArray(node, value);
            return;
        }
        throw new JsonException("expected Array node, but was " + Types.name(node));
    }

    /**
     * Inserts a value at the given index of an array-like node.
     * <p>
     * Indexed insert is supported by List/JsonArray only. Set and Java array
     * inputs are rejected because they are unordered or fixed-size.
     */
    @SuppressWarnings("unchecked")
    public static void addInArray(Object node, int idx, Object value) {
        Objects.requireNonNull(node, "node");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            list.add(idx, value);
            return;
        }
        if (node instanceof JsonArray) {
            ((JsonArray) node).add(idx, value);
            return;
        }
        if (node.getClass().isArray()) {
            throw new JsonException("cannot insert into a Java array");
        }
        if (node instanceof Set) {
            throw new JsonException("cannot call addInArray() with an index on an unordered Java Set");
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.addInArray(node, idx, value);
            return;
        }
        throw new JsonException("expected Array node, but was " + Types.name(node));
    }

    /**
     * Removes an element by index from an array-like node.
     * <p>
     * Negative indexes are supported for List. Java arrays and Set do not support
     * index-based removal.
     */
    @SuppressWarnings("unchecked")
    public static Object removeInArray(Object node, int idx) {
        Objects.requireNonNull(node, "node");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            return list.remove(idx);
        }
        if (node instanceof JsonArray) {
            return ((JsonArray) node).remove(idx);
        }
        if (node.getClass().isArray()) {
            throw new JsonException("cannot remove at index " + idx +
                    " from Java array of component type '" + node.getClass().getComponentType().getName() + "'");
        }
        if (node instanceof Set) {
            throw new JsonException("cannot call removeInArray() on an unordered Java Set");
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.removeInArray(node, idx);
        }
        throw new JsonException("expected Array node, but was " + Types.name(node));
    }


    /// Walk

    /** Traversal order relative to child nodes. */
    public enum WalkOrder { TOP_DOWN, BOTTOM_UP }
    /** Node selection mode for visitor callbacks. */
    public enum WalkTarget { ANY, CONTAINER, OBJECT, ARRAY, VALUE, STRING, NUMBER, BOOLEAN, NULL, UNKNOWN }

    /**
     * Walks the node tree in top-down order and visits both containers and values.
     */
    public static void walk(Object container,
                            BiFunction<PathSegment, Object, Boolean> visitor) {
        walk(container, WalkTarget.ANY, WalkOrder.TOP_DOWN, -1, visitor);
    }

    /**
     * Walks a node tree with full traversal controls.
     * <p>
     * {@code maxDepth < 0} means unlimited depth. Traversal starts at root path.
     * Returning {@link Boolean} stops traversal of the current branch.
     */
    public static void walk(Object container, WalkTarget target,
                            WalkOrder order, int maxDepth,
                            BiFunction<PathSegment, Object, Boolean> visitor) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(visitor, "visitor");
        _walk(container, PathSegment.Root.INSTANCE, visitor, target, order, maxDepth);
    }

    private static void _walk(Object node, PathSegment path,
                              BiFunction<PathSegment, Object, Boolean> visitor,
                              WalkTarget target, WalkOrder order, int remainingDepth) {
        if (remainingDepth == 0) return;

        JsonType jt = JsonType.of(node);
        if (jt.isObject()) {
            if (order == WalkOrder.TOP_DOWN &&
                    (target == WalkTarget.ANY || target == WalkTarget.CONTAINER || target == WalkTarget.OBJECT)) {
                if (!visitor.apply(path, node)) return;
            }

            Nodes.forEachObject(node, (key, subNode) -> {
                PathSegment childPath = new PathSegment.Name(path, key);
                _walk(subNode, childPath, visitor, target, order, remainingDepth - 1);
            });
            if (order == WalkOrder.BOTTOM_UP &&
                    (target == WalkTarget.ANY || target == WalkTarget.CONTAINER || target == WalkTarget.OBJECT)) {
                visitor.apply(path, node);
            }
        } else if (jt.isArray()) {
            if (order == WalkOrder.TOP_DOWN &&
                    (target == WalkTarget.ANY || target == WalkTarget.CONTAINER || target == WalkTarget.ARRAY)) {
                if (!visitor.apply(path, node)) return;
            }

            Nodes.forEachArray(node, (idx, subNode) -> {
                PathSegment childPath = new PathSegment.Index(path, idx);
                _walk(subNode, childPath, visitor, target, order, remainingDepth - 1);
            });
            if (order == WalkOrder.BOTTOM_UP &&
                    (target == WalkTarget.ANY || target == WalkTarget.CONTAINER || target == WalkTarget.ARRAY)) {
                visitor.apply(path, node);
            }
        } else if (jt.isString()) {
            if (target == WalkTarget.ANY || target == WalkTarget.VALUE || target == WalkTarget.STRING ) {
                visitor.apply(path, node);
            }
        } else if (jt.isNumber()) {
            if (target == WalkTarget.ANY || target == WalkTarget.VALUE || target == WalkTarget.NUMBER ) {
                visitor.apply(path, node);
            }
        } else if (jt.isBoolean()) {
            if (target == WalkTarget.ANY || target == WalkTarget.VALUE || target == WalkTarget.BOOLEAN ) {
                visitor.apply(path, node);
            }
        } else if (jt.isNull()) {
            if (target == WalkTarget.ANY || target == WalkTarget.VALUE || target == WalkTarget.NULL ) {
                visitor.apply(path, node);
            }
        } else if (jt.isUnknown()) {
            if (target == WalkTarget.ANY || target == WalkTarget.VALUE || target == WalkTarget.UNKNOWN ) {
                visitor.apply(path, node);
            }
        }
    }

}
