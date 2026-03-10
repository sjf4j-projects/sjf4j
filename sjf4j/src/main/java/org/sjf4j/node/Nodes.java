package org.sjf4j.node;


import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.facade.FacadeNodes;
import org.sjf4j.path.PathSegment;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;


/**
 * Core node utilities: type conversion, inspection, and container access.
 */
public class Nodes {

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
        throw new JsonException("Expected String/Character/Enum, but got " + Types.name(node));
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
    public static Character toCharacter(Object node) {
        if (node == null) return null;
        if (node instanceof Character) return (Character) node;
        String s = toString(node);
        return s.length() > 0 ? s.charAt(0) : null;
    }

    /**
     * Converts a node to Character using lenient conversion.
     */
    public static Character asCharacter(Object node) {
        if (node == null) return null;
        if (node instanceof Character) return (Character) node;
        String s = asString(node);
        return s.length() > 0 ? s.charAt(0) : null;
    }

    /**
     * Converts a node to a Number with strict type checking.
     */
    public static Number toNumber(Object node) {
        if (node == null) return null;
        if (node instanceof Number) return (Number) node;
        if (FacadeNodes.isNode(node)) return FacadeNodes.toNumber(node);
        throw new JsonException("Expected Number, but got " + Types.name(node));
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
    public static Integer toInteger(Object node) {
        Number n = toNumber(node);
        if (n == null) return null;
        return Numbers.toInt(n);
    }

    /**
     * Converts a node to an Integer with flexible type conversion.
     */
    public static Integer asInteger(Object node) {
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
        throw new JsonException("Expected Boolean, but got " + Types.name(node));
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
//            throw new JsonException("Cannot convert String to Boolean: supported formats: true/false, yes/no, on/off, 1/0");
            return null;
        }
        if (node instanceof Number) {
            int i = ((Number) node).intValue();
            if (i == 1) return true;
            if (i == 0) return false;
//            throw new JsonException("Cannot convert Number to Boolean: numeric values other than 0-false or 1-true");
            return null;
        }
        if (FacadeNodes.isNode(node)) return FacadeNodes.asBoolean(node);
        return null;
    }

    /**
     * Converts a node to JsonObject.
     */
    public static JsonObject toJsonObject(Object node) {
        if (node == null) return null;
        if (node instanceof JsonObject) return (JsonObject) node;
        if (FacadeNodes.isNode(node)) return FacadeNodes.toJsonObject(node);
        return new JsonObject(node);
    }

    /**
     * Converts a node to Map with Object values.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object node) {
        if (node == null) return null;
        if (node instanceof Map) return (Map<String, Object>) node;
        if (node instanceof JsonObject) return ((JsonObject) node).toMap();
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                Object v = entry.getValue().invokeGetter(node);
                map.put(entry.getKey(), v);
            }
            return map;
        }
        if (FacadeNodes.isNode(node)) return FacadeNodes.toMap(node);
        throw new JsonException("Type mismatch: cannot convert " + Types.name(node) + " to Map");
    }

    /**
     * Converts a node to typed Map.
     */
    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> toMap(Object node, Class<T> clazz) {
        if (node == null) return null;
        if (node instanceof Map && (clazz == null || clazz == Object.class)) return (Map<String, T>) node;
        Map<String, T> map = Sjf4jConfig.global().mapSupplier.create();
        visitObject(node, (k, v) -> map.put(k, to(v, clazz)));
        return map;
    }

    /**
     * Converts a node to JsonArray.
     */
    public static JsonArray toJsonArray(Object node) {
        if (node == null) return null;
        if (node instanceof JsonArray) return (JsonArray) node;
        if (FacadeNodes.isNode(node)) return FacadeNodes.toJsonArray(node);
        return new JsonArray(node);
    }

    /**
     * Converts a node to List with Object values.
     */
    @SuppressWarnings("unchecked")
    public static List<Object> toList(Object node) {
        if (node == null) return null;
        if (node instanceof List) return (List<Object>) node;
        if (node instanceof JsonArray) return ((JsonArray) node).toList();
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            List<Object> list = Sjf4jConfig.global().listSupplier.create(len);
            for (int i = 0; i < len; i++) list.add(Array.get(node, i));
            return list;
        }
        if (node instanceof Set) {
            return Sjf4jConfig.global().listSupplier.create((Set<Object>) node);
        }
        if (FacadeNodes.isNode(node)) return FacadeNodes.toList(node);
        throw new JsonException("Type mismatch: cannot convert " + Types.name(node) + " to List");
    }

    /**
     * Converts a node to typed List.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> toList(Object node, Class<T> clazz) {
        if (node == null) return null;
        if (node instanceof List && (clazz == null || clazz == Object.class)) return (List<T>) node;
        List<T> list = Sjf4jConfig.global().listSupplier.create();
        visitArray(node, (i, v) -> list.add(to(v, clazz)));
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
        throw new JsonException("Type mismatch: cannot convert " + Types.name(node) + " to Array");
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
        visitArray(node, (i, v) -> Array.set(arr, i, to(v, componentType)));
        return (T[]) arr;
    }

    /**
     * Converts a node to Set with Object values.
     */
    @SuppressWarnings("unchecked")
    public static Set<Object> toSet(Object node) {
        if (node == null) return null;
        if (node instanceof List) return Sjf4jConfig.global().setSupplier.create((List<Object>) node);
        if (node instanceof JsonArray) return ((JsonArray) node).toSet();
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            Set<Object> set = Sjf4jConfig.global().setSupplier.create(len);
            for (int i = 0; i < len; i++) set.add(Array.get(node, i));
            return set;
        }
        if (node instanceof Set) return (Set<Object>) node;
        if (FacadeNodes.isNode(node)) return FacadeNodes.toSet(node);
        throw new JsonException("Type mismatch: cannot convert " + Types.name(node) + " to Set");
    }

    /**
     * Converts a node to typed Set.
     */
    @SuppressWarnings("unchecked")
    public static <T> Set<T> toSet(Object node, Class<T> clazz) {
        if (node == null) return null;
        if (node instanceof Set && (clazz == null || clazz == Object.class)) return (Set<T>) node;
        Set<T> set = Sjf4jConfig.global().setSupplier.create();
        visitArray(node, (i, v) -> set.add(to(v, clazz)));
        return set;
    }

    /**
     * Converts a node to JOJO subtype.
     */
    public static <T> T toJojo(Object node, Class<T> clazz) {
        if (!JsonObject.class.isAssignableFrom(clazz) || clazz == JsonObject.class)
            throw new JsonException("Type mismatch: expected <JOJO>, but was " + clazz.getName());
        if (node == null) return null;
        return toPojo(node, clazz);
    }

    /**
     * Converts a node to JAJO subtype.
     */
    @SuppressWarnings("unchecked")
    public static <T> T toJajo(Object node, Class<T> clazz) {
        if (!JsonArray.class.isAssignableFrom(clazz) || clazz == JsonArray.class)
            throw new JsonException("Type mismatch: expected <JAJO>, but was " + clazz.getName());
        if (node == null) return null;
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(clazz);
        JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
        visitArray(node, jajo::add);
        return (T) jajo;
    }


    /**
     * Converts a node to a registered POJO type (including JOJO/JAJO subclasses).
     * <p>
     * For regular POJO targets, fields are mapped by declared field names, with
     * alias support from {@code @NodeProperty}. Constructor-arg mapping is used
     * when required by {@code @NodeCreator}; unmatched values are applied later
     * through setters when available.
     * <p>
     * For JOJO targets ({@link JsonObject} subclasses), dynamic keys that are not
     * declared fields are preserved in the dynamic map.
     */
    @SuppressWarnings("unchecked")
    public static <T> T toPojo(Object node, Class<T> clazz) {
        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
        if (ti.pojoInfo == null && ti.anyOfInfo == null) {
            throw new JsonException("Class '" + clazz.getName() + "' is not a POJO");
        }
        return (T) Sjf4jConfig.global().getNodeFacade().readNode(node, clazz, false);
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
        if (clazz == Character.class) {
            if (cross) return asCharacter(node);
            else return toCharacter(node);
        }
        if (clazz.isEnum()) {
            if (cross) return asEnum(node, (Class<Enum>) clazz);
            return toEnum(node, (Class<Enum>) clazz);
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
            return toMap(node, vc);
        }
        if (clazz == JsonObject.class) return toJsonObject(node);
        if (JsonObject.class.isAssignableFrom(clazz)) return toJojo(node, clazz);

        if (List.class.isAssignableFrom(clazz)) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> vc = Types.rawBox(vt);
            return toList(node, vc);
        }
        if (clazz == JsonArray.class) return toJsonArray(node);
        if (JsonArray.class.isAssignableFrom(clazz)) return toJajo(node, clazz);
        if (clazz.isArray()) return toArray(node, clazz.getComponentType());
        if (Set.class.isAssignableFrom(clazz)) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> vc = Types.rawBox(vt);
            return toSet(node, vc);
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
        if (ti.pojoInfo != null || ti.anyOfInfo != null) return toPojo(node, clazz);

        throw new JsonException("Type mismatch: cannot convert " + Types.name(node) + " to " + clazz.getName());
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
     * Object-like nodes are compared by key/value pairs, array-like nodes are
     * compared by order and element values, and number values are compared by
     * numeric value (not boxed type).
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
     * Object-like nodes are hashed in key/value form (order-insensitive for object
     * members), while array-like nodes are hashed in iteration order.
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
            visitObject(node, (k, v) -> {
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
        Class<?> rawClazz = node.getClass();

        if (node instanceof Map) {
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            map.putAll((Map<String, ?>) node);
            return (T) map;
        }
        if (rawClazz == JsonObject.class) {
            return (T) new JsonObject((JsonObject) node);
        }
        if (node instanceof JsonObject) {
            JsonObject srcJo = (JsonObject) node;
            NodeRegistry.CreatorInfo ci = NodeRegistry.registerPojoOrElseThrow(node.getClass()).creatorInfo;
            JsonObject newJo = (JsonObject) (ci.noArgsCtorHandle == null ? null : ci.newPojoNoArgs());
            Object[] args = ci.noArgsCtorHandle == null ? new Object[ci.argNames.length] : null;
            int remainingArgs = ci.noArgsCtorHandle == null ? args.length : 0;
            int pendingSize = 0;
            String[] pendingKeys = null;
            Object[] pendingValues = null;

            for (Map.Entry<String, Object> entry : srcJo.entrySet()) {
                String key = entry.getKey();
                int argIdx = -1;
                if (newJo == null) {
                    argIdx = ci.getArgIndex(key);
                    if (argIdx < 0 && ci.aliasMap != null) {
                        String origin = ci.aliasMap.get(key); // alias -> origin
                        if (origin != null) {
                            argIdx = ci.getArgIndex(origin);
                        }
                    }
                }
                if (argIdx >= 0) {
                    assert args != null;
                    args[argIdx] = entry.getValue();
                    remainingArgs--;
                    if (remainingArgs == 0) {
                        newJo = (JsonObject) ci.newPojoWithArgs(args);
                        for (int i = 0; i < pendingSize; i++) {
                            newJo.put(pendingKeys[i], pendingValues[i]);
                        }
                    }
                    continue;
                }

                if (newJo != null) {
                    newJo.put(key, entry.getValue());
                } else {
                    if (pendingKeys == null) {
                        int cap = srcJo.size();
                        pendingKeys = new String[cap];
                        pendingValues = new Object[cap];
                    }
                    pendingKeys[pendingSize] = key;
                    pendingValues[pendingSize] = entry.getValue();
                    pendingSize++;
                }
            }
            if (newJo == null) {
                newJo = (JsonObject) ci.newPojoWithArgs(args);
                for (int i = 0; i < pendingSize; i++) {
                    newJo.put(pendingKeys[i], pendingValues[i]);
                }
            }
            return (T) newJo;
        }
        if (node instanceof List) {
            return (T) Sjf4jConfig.global().listSupplier.create((List<Object>) node);
        }
        if (rawClazz == JsonArray.class) {
            return (T) new JsonArray((JsonArray) node);
        }
        if (node instanceof JsonArray) {
            NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(node.getClass());
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            jajo.addAll((JsonArray) node);
            return (T) jajo;
        }
        if (rawClazz.isArray()) {
            int len = Array.getLength(node);
            Object arr = Array.newInstance(node.getClass().getComponentType(), len);
            System.arraycopy(node, 0, arr, 0, len);
            return (T) arr;
        }
        if (node instanceof Set) {
            return (T) Sjf4jConfig.global().setSupplier.create((Set<Object>) node);
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
        if (ti.isNodeValue()) {
            return (T) ti.valueCodecInfo.valueCopy(node);
        } else if (ti.isPojo()) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(node.getClass());
            NodeRegistry.CreatorInfo ci = pi.creatorInfo;
            Object pojo = ci.noArgsCtorHandle == null ? null : ci.newPojoNoArgs();
            Object[] args = ci.noArgsCtorHandle == null ? new Object[ci.argNames.length] : null;
            int remainingArgs = ci.noArgsCtorHandle == null ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;

            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                String key = entry.getKey();
                NodeRegistry.FieldInfo fi = entry.getValue();

                int argIdx = -1;
                if (pojo == null) {
                    argIdx = ci.getArgIndex(key);
                    if (argIdx < 0 && ci.aliasMap != null) {
                        String origin = ci.aliasMap.get(key); // alias -> origin
                        if (origin != null) {
                            argIdx = ci.getArgIndex(origin);
                        }
                    }
                }
                if (argIdx >= 0) {
                    assert args != null;
                    args[argIdx] = fi.invokeGetter(node);
                    remainingArgs--;
                    if (remainingArgs == 0) {
                        pojo = ci.newPojoWithArgs(args);
                        for (int j = 0; j < pendingSize; j++) {
                            pendingFields[j].invokeSetterIfPresent(pojo, pendingValues[j]);
                        }
                        pendingSize = 0;
                    }
                    continue;
                }

                Object v = fi.invokeGetter(node);
                if (pojo != null) {
                    fi.invokeSetterIfPresent(pojo, v);
                } else {
                    if (pendingFields == null) {
                        int cap = pi.fieldCount;
                        pendingFields = new NodeRegistry.FieldInfo[cap];
                        pendingValues = new Object[cap];
                    }
                    pendingFields[pendingSize] = fi;
                    pendingValues[pendingSize] = v;
                    pendingSize++;
                }
            }
            if (pojo == null) {
                pojo = ci.newPojoWithArgs(args);
                for (int i = 0; i < pendingSize; i++) {
                    pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                }
            }
            return (T) pojo;
        }

        if (FacadeNodes.isNode(node)) {
            throw new JsonException("Operation not supported: cannot copy facade node '" + Types.name(node) + "'");
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
        _inspect(node, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void _inspect(Object node, StringBuilder sb) {
        if (node == null) {
            sb.append(node);
            return;
        }
        Class<?> rawClazz = node.getClass();
        if (node instanceof Map) {
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
        if (rawClazz == JsonObject.class) {
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
        if (node instanceof JsonObject) {
            JsonObject jo = (JsonObject) node;
            sb.append("@").append(node.getClass().getSimpleName()).append("{");
            NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(node.getClass());
            AtomicInteger idx = new AtomicInteger(0);
            jo.forEach((k, v) -> {
                if (idx.getAndIncrement() > 0) sb.append(", ");
                if (pi != null && pi.fields.containsKey(k)) {
                    sb.append("*");
                }
                sb.append(k).append("=");
                _inspect(v, sb);
            });
            sb.append("}");
            return;
        }
        if (node instanceof List) {
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
        if (rawClazz == JsonArray.class) {
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
        if (node instanceof JsonArray) {
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
        if (rawClazz.isArray()) {
            int len = Array.getLength(node);
            sb.append("A[");
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(", ");
                _inspect(Array.get(node, i), sb);
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
                _inspect(v, sb);
            }
            sb.append("]");
            return;
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
        if (ti.isNodeValue()) {
            Object raw = ti.valueCodecInfo.valueToRaw(node);
            sb.append("@").append(rawClazz.getSimpleName()).append("#");
            _inspect(raw, sb);
            return;
        }
        if (ti.isPojo()) {
            NodeRegistry.PojoInfo pi = ti.pojoInfo;
            sb.append("@").append(rawClazz.getSimpleName()).append("{");
            int idx = 0;
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                if (idx++ > 0) sb.append(", ");
                sb.append("*").append(entry.getKey()).append("=");
                Object v = entry.getValue().invokeGetter(node);
                _inspect(v, sb);
            }
            sb.append("}");
            return;
        }

        if (NodeKind.of(node).isUnknown()) {
            sb.append("!").append(node);
            return;
        }

        sb.append(node);
    }



    /// Visit

    /**
     * Visits each entry in an object-like node.
     */
    @SuppressWarnings("unchecked")
    public static void visitObject(Object node, BiConsumer<String, Object> visitor) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(visitor, "visitor is null");
        if (node instanceof Map) {
            ((Map<String, Object>) node).forEach(visitor);
            return;
        }
        if (node instanceof JsonObject) {
            ((JsonObject) node).forEach(visitor);
            return;
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                Object value = entry.getValue().invokeGetter(node);
                visitor.accept(entry.getKey(), value);
            }
            return;
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.visitObject(node, visitor);
            return;
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an object node");
    }


    @SuppressWarnings("unchecked")
    public static boolean anyMatchInObject(Object node, BiPredicate<String, Object> predicate) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(predicate, "predicate is null");
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
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                Object value = entry.getValue().invokeGetter(node);
                if (predicate.test(entry.getKey(), value)) {
                    return true;
                }
            }
            return false;
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.anyMatchInObject(node, predicate);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an object node");
    }


    @SuppressWarnings("unchecked")
    public static boolean transformInObject(Object node, BiFunction<String, Object, Object> mapper) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(mapper, "mapper is null");
        if (node instanceof Map) {
            boolean changed = false;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) node).entrySet()) {
                Object oldValue = entry.getValue();
                Object newValue = mapper.apply(entry.getKey(), oldValue);
                if (oldValue != newValue) {
                    entry.setValue(newValue);
                    changed = true;
                }
            }
            return changed;
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).transform(mapper);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            boolean changed = false;
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                NodeRegistry.FieldInfo fi = entry.getValue();
                Object oldValue = fi.invokeGetter(node);
                Object newValue = mapper.apply(entry.getKey(), oldValue);
                if (oldValue != newValue) {
                    fi.invokeSetter(node, newValue);
                    changed = true;
                }
            }
            return changed;
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.transformInObject(node, mapper);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an object node");
    }


    /**
     * Visits each element in an array-like node.
     */
    @SuppressWarnings("unchecked")
    public static void visitArray(Object node, BiConsumer<Integer, Object> visitor) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(visitor, "visitor is null");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            for (int i = 0; i < list.size(); i++) visitor.accept(i, list.get(i));
            return;
        }
        if (node instanceof JsonArray) {
            ((JsonArray) node).forEach(visitor);
            return;
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            for (int i = 0; i < len; i++) visitor.accept(i, Array.get(node, i));
            return;
        }
        if (node instanceof Set) {
            Set<Object> set = (Set<Object>) node;
            int i = 0;
            for (Object v : set) visitor.accept(i++, v);
            return;
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.visitArray(node, visitor);
            return;
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an array node");
    }

    /**
     * Returns true if any element matches the predicate.
     */
    @SuppressWarnings("unchecked")
    public static boolean anyMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(predicate, "predicate is null");
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
            return FacadeNodes.anyMatchInArray(node, predicate);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an array node");
    }

    /**
     * Returns true if all elements match the predicate.
     */
    @SuppressWarnings("unchecked")
    public static boolean allMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(predicate, "predicate is null");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            for (int i = 0; i < list.size(); i++) {
                if (!predicate.test(i, list.get(i))) return false;
            }
            return true;
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            for (int i = 0; i < ja.size(); i++) {
                if (!predicate.test(i, ja.getNode(i))) return false;
            }
            return true;
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            for (int i = 0; i < len; i++) {
                if (!predicate.test(i, Array.get(node, i))) return false;
            }
            return true;
        }
        if (node instanceof Set) {
            Set<Object> set = (Set<Object>) node;
            int i = 0;
            for (Object v : set) {
                if (!predicate.test(i++, v)) return false;
            }
            return true;
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.allMatchInArray(node, predicate);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an array node");
    }

    /**
     * Returns true if no elements match the predicate.
     */
    @SuppressWarnings("unchecked")
    public static boolean noneMatchInArray(Object node, BiPredicate<Integer, Object> predicate) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(predicate, "predicate is null");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            for (int i = 0; i < list.size(); i++) {
                if (predicate.test(i, list.get(i))) return false;
            }
            return true;
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            for (int i = 0; i < ja.size(); i++) {
                if (predicate.test(i, ja.getNode(i))) return false;
            }
            return true;
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            for (int i = 0; i < len; i++) {
                if (predicate.test(i, Array.get(node, i))) return false;
            }
            return true;
        }
        if (node instanceof Set) {
            Set<Object> set = (Set<Object>) node;
            int i = 0;
            for (Object v : set) {
                if (predicate.test(i++, v)) return false;
            }
            return true;
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an array node");
    }

    /**
     * Returns the number of entries in an object-like node.
     */
    public static int sizeInObject(Object node) {
        Objects.requireNonNull(node, "node is null");
        if (node instanceof Map) {
            return ((Map<?, ?>) node).size();
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).size();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            return pi.fieldCount;
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.sizeInObject(node);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an object node");
    }

    /**
     * Returns the number of elements in an array-like node.
     */
    public static int sizeInArray(Object node) {
        Objects.requireNonNull(node, "node is null");
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
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an array node");
    }

    /**
     * Returns the key set for an object-like node.
     */
    @SuppressWarnings("unchecked")
    public static Set<String> keySetInObject(Object node) {
        Objects.requireNonNull(node, "node is null");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).keySet();
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).keySet();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            return pi.fields.keySet();
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.keySetInObject(node);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an object node");
    }

    /**
     * Returns the entry set for an object-like node.
     */
    @SuppressWarnings("unchecked")
    public static Set<Map.Entry<String, Object>> entrySetInObject(Object node) {
        Objects.requireNonNull(node, "node is null");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).entrySet();
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).entrySet();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            Set<Map.Entry<String, Object>> entrySet = new LinkedHashSet<>(pi.fieldCount);
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                Object value = entry.getValue().invokeGetter(node);
                entrySet.add(new AbstractMap.SimpleEntry<>(entry.getKey(), value));
            }
            return entrySet;
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.entrySetInObject(node);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an object node");
    }

    /**
     * Returns an iterator over an array-like node.
     */
    @SuppressWarnings("unchecked")
    public static Iterator<Object> iteratorInArray(Object node) {
        Objects.requireNonNull(node, "node is null");
        if (node instanceof List) {
            return ((List<Object>) node).iterator();
        }
        if (node instanceof JsonArray) {
            return ((JsonArray) node).iterator();
        }
        if (node.getClass().isArray()) {
            return _arrayIterator(node);
        }
        if (node instanceof Set) {
            return ((Set<Object>) node).iterator();
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.iteratorInArray(node);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an array node");
    }

    /**
     * Creates an iterator for a raw Java array.
     */
    private static Iterator<Object> _arrayIterator(Object array) {
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


    /**
     * Returns true when object-like node contains the key.
     */
    @SuppressWarnings("unchecked")
    public static boolean containsInObject(Object node, String key) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(key, "key is null");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).containsKey(key);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).containsKey(key);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            return pi.fields.containsKey(key);
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.containsInObject(node, key);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an object node");
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
     */
    public static Object getInObject(Object node, String key) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(key, "key is null");
        if (node instanceof Map) {
            return ((Map<?, ?>) node).get(key);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).getNode(key);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            NodeRegistry.FieldInfo fi = pi.fields.get(key);
            return fi != null ? fi.invokeGetter(node) : null;
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.getInObject(node, key);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an object node");
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
        Objects.requireNonNull(node, "node is null");
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
            throw new JsonException("Cannot call getInArray() on an unordered Java Set");
//            Set<Object> set = (Set<Object>) node;
//            idx = idx < 0 ? set.size() + idx : idx;
//            if (idx < 0 || idx >= set.size()) {
//                return null;
//            } else {
//                int i = 0;
//                for (Object v : set) {
//                    if (i++ == idx) return v;
//                }
//                return null;
//            }
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.getInArray(node, idx);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an array node");
    }

    public static final class Access {
        /** child value (can be null) */
        public Object node;
        /** static Type of child (never null; default Object.class) */
        public Type type;
        /**
         * Indicates whether this location allows insertion or auto-creation.
         * false means the container is locked (e.g. POJO without such field).
         */
        public boolean insertable;

        /**
         * Resets access payload to defaults.
         */
        public void reset() {
            node = null;
            type = Object.class;
            insertable = false;
        }
        /**
         * Sets access payload values.
         */
        public void set(Object node, Type type, boolean insertable) {
            this.node = node;
            this.type = type;
            this.insertable = insertable;
        }
    }

    /**
     * Resolves object-child access and fills {@link Access} with node/type metadata.
     * <p>
     * The output describes the current child value, inferred static type, and
     * whether writing/inserting at this location is allowed.
     */
    @SuppressWarnings("unchecked")
    public static void accessInObject(Object node, Type type, String key, Access out) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(key, "key is null");
        Objects.requireNonNull(out, "out is null");

        if (node instanceof Map) {
            out.node = ((Map<String, Object>) node).get(key);
            out.type = Types.resolveTypeArgument(type, Map.class, 1);
            out.insertable = true;
            return;
        }
        if (node.getClass() == JsonObject.class) {
            out.node = ((JsonObject) node).getNode(key);
            out.type = Object.class;
            out.insertable = true;
            return;
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            NodeRegistry.FieldInfo fi = pi.fields.get(key);
            if (fi != null) {
                out.node = fi.invokeGetter(node);
                out.type = fi.type;
                out.insertable = true;
                return;
            }
            if (node instanceof JsonObject) {
                out.node = ((JsonObject) node).getNode(key);
                out.type = Object.class;
                out.insertable = true;
                return;
            }
            out.node = null;
            out.type = Object.class;
            out.insertable = false;
            return;
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.accessInObject(node, type, key, out);
            return;
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an object node");

    }

    /**
     * Resolves array-child access and fills {@link Access} with node/type metadata.
     * <p>
     * Negative indexes are normalized. {@code idx == size} is treated as appendable
     * for List/JsonArray/Set, and reported as insertable with {@code node == null}.
     */
    @SuppressWarnings("unchecked")
    public static void accessInArray(Object node, Type type, int idx, Access out) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(out, "out is null");

        if (node instanceof List) {
            out.type = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx >= 0 && idx < list.size()) {
                out.node = list.get(idx);
                out.insertable = true;
                return;
            }
            if (idx == list.size()){
                out.node = null;
                out.insertable = true;
                return;
            }
            out.node = null;
            out.insertable = false;
            return;
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            idx = idx < 0 ? ja.size() + idx : idx;
            if (idx >= 0 && idx <= ja.size()) {
                out.node = ja.getNode(idx);
                out.type = Object.class;
                out.insertable = true;
                return;
            }
            out.set(null, Object.class, false);
            return;
        }
        if (node.getClass().isArray()) {
            out.type = node.getClass().getComponentType();
            int len = Array.getLength(node);
            idx = idx < 0 ? len + idx : idx;
            if (idx >= 0 && idx < len) {
                out.node = Array.get(node, idx);
                out.insertable = true;
                return;
            }
            out.node = null;
            out.insertable = false;
            return;
        }
        if (node instanceof Set) {
            throw new JsonException("Cannot call accessInArray() on an unordered Java Set");
//            out.type = Types.resolveTypeArgument(type, Set.class, 0);
//            Set<Object> set = (Set<Object>) node;
//            idx = idx < 0 ? set.size() + idx : idx;
//            if (idx >= 0 && idx < set.size()) {
//                int i = 0;
//                for (Object v : set) {
//                    if (i++ == idx) {
//                        out.node = v;
//                        out.insertable = true;
//                        return;
//                    }
//                }
//                throw new AssertionError("Unreachable");
//            }
//            if (idx == set.size()){
//                out.node = null;
//                out.insertable = true;
//                return;
//            }
//            out.node = null;
//            out.insertable = false;
//            return;
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.accessInArray(node, type, idx, out);
            return;
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an array node");
    }


    /**
     * Puts a value into an object-like node and returns the previous value.
     * <p>
     * For POJO nodes, only declared fields are writable; unknown keys fail.
     */
    @SuppressWarnings("unchecked")
    public static Object putInObject(Object node, String key, Object value) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(key, "key is null");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).put(key, value);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).put(key, value);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            NodeRegistry.FieldInfo fi = pi.fields.get(key);
            if (fi != null) {
                Object old = fi.invokeGetter(node);
                fi.invokeSetter(node, value);
                return old;
            } else {
                throw new JsonException("Unknown field '" + key + "' in POJO node '" +
                        node.getClass().getName() + "'");
            }
        }
        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.putInObject(node, key, value);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an object node");
    }

    /**
     * Sets or appends a value in an array-like node by index.
     * <p>
     * For List/JsonArray/Set: {@code idx < size} updates, {@code idx == size}
     * appends, other indexes fail. For Java arrays: only in-range replacement is
     * allowed (no append). Negative indexes are supported.
     */
    @SuppressWarnings("unchecked")
    public static Object setInArray(Object node, int idx, Object value) {
        Objects.requireNonNull(node, "node is null");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx == list.size()) {
                list.add(value);
                return null;
            } else if (idx >= 0 && idx < list.size()) {
                return list.set(idx, value);
            } else {
                throw new JsonException("Cannot set/add index " + idx + " in List of size " +
                        list.size() + " (index < size: modify; index == size: append)");
            }
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            if (idx == ja.size()) {
                ja.add(value);
                return null;
            } else if (ja.containsIndex(idx)) {
                return ja.set(idx, value);
            } else {
                throw new JsonException("Cannot set/add index " + idx + " in JsonArray of size " +
                        ja.size() + " (index < size: modify; index == size: append)");
            }
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            idx = idx < 0 ? len + idx : idx;
            if (idx >= 0 && idx < len) {
                Object old = Array.get(node, idx);
                Array.set(node, idx, value);
                return old;
            } else {
                throw new JsonException("Cannot set index " + idx + " in Array of size " +
                        len + " (index < size: modify)");
            }
        }
        if (node instanceof Set) {
            throw new JsonException("Cannot call setInArray() on an unordered Java Set");
//            Set<Object> set = (Set<Object>) node;
//            idx = idx < 0 ? set.size() + idx : idx;
//            if (idx == set.size()) {
//                set.add(value);
//                return null;
//            } else if (idx >= 0 && idx < set.size()) {
//                throw new JsonException("Cannot set an element at a given index in an unordered Java Set");
//            } else {
//                throw new JsonException("Cannot set/add index " + idx + " in Set of size " + set.size());
//            }
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.setInArray(node, idx, value);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an array node");
    }

    /**
     * Appends a value to an array-like node.
     * <p>
     * Java arrays are fixed-size and therefore not appendable.
     */
    @SuppressWarnings("unchecked")
    public static void addInArray(Object node, Object value) {
        Objects.requireNonNull(node, "node is null");
        if (node instanceof List) {
            ((List<Object>) node).add(value);
            return;
        }
        if (node instanceof JsonArray) {
            ((JsonArray) node).add(value);
            return;
        }
        if (node.getClass().isArray()) {
            throw new JsonException("Cannot call addInArray() on a Java array");
        }
        if (node instanceof Set) {
            ((Set<Object>) node).add(value);
            return;
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.addInArray(node, value);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an array node");
    }

    /**
     * Inserts a value at the given index of an array-like node.
     * <p>
     * Indexed insert is supported by List/JsonArray only. Set and Java array
     * inputs are rejected because they are unordered or fixed-size.
     */
    @SuppressWarnings("unchecked")
    public static void addInArray(Object node, int idx, Object value) {
        Objects.requireNonNull(node, "node is null");
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
            throw new JsonException("Cannot call addInArray() with index on a Java array");
        }
        if (node instanceof Set) {
            throw new JsonException("Cannot call addInArray() at a given index on an unordered Java Set");
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.addInArray(node, idx, value);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an array node");
    }

    /**
     * Removes a key from an object-like node and returns the previous value.
     * <p>
     * Removal is supported for Map/JsonObject. POJO fields are structural and
     * cannot be removed.
     */
    @SuppressWarnings("unchecked")
    public static Object removeInObject(Object node, String key) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(key, "key is null");
        if (node instanceof JsonObject) {
            return ((JsonObject) node).remove(key);
        }
        if (node instanceof Map) {
            return ((Map<String, Object>) node).remove(key);
        }
        if (NodeRegistry.registerPojo(node.getClass()) != null) {
            throw new JsonException("Cannot remove field '" + key + "' in POJO node '" +
                    node.getClass().getName() + "'");
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.removeInObject(node, key);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an object node");
    }

    /**
     * Removes an element by index from an array-like node.
     * <p>
     * Negative indexes are supported for List. Java arrays and Set do not support
     * index-based removal.
     */
    @SuppressWarnings("unchecked")
    public static Object removeInArray(Object node, int idx) {
        Objects.requireNonNull(node, "node is null");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            return list.remove(idx);
        }
        if (node instanceof JsonArray) {
            return ((JsonArray) node).remove(idx);
        }
        if (node.getClass().isArray()) {
            throw new JsonException("Operation not supported: cannot remove index " + idx +
                    " from Java array of component type '" + node.getClass().getComponentType().getName() + "'");
        }
        if (node instanceof Set) {
            throw new JsonException("Cannot call removeInArray() on an unordered Java Set");
        }
        if (FacadeNodes.isNode(node)) {
            FacadeNodes.removeInArray(node, idx);
        }
        throw new JsonException("Type mismatch: " + Types.name(node) + " is not an array node");
    }


    /// Walk

    /** Traversal order relative to child nodes. */
    public enum WalkOrder { TOP_DOWN, BOTTOM_UP }
    /** Node selection mode for visitor callbacks. */
    public enum WalkTarget { ANY, CONTAINER, VALUE }

    /**
     * Walks the node tree in top-down order and visits both containers and values.
     */
    public static void walk(Object container,
                            BiFunction<PathSegment, Object, Boolean> visitor) {
        walk(container, WalkTarget.ANY, WalkOrder.TOP_DOWN, -1, visitor);
    }

    /**
     * Walks the node tree in top-down order with explicit target selection.
     */
    public static void walk(Object container,
                            WalkTarget target,
                            BiFunction<PathSegment, Object, Boolean> visitor) {
        walk(container, target, WalkOrder.TOP_DOWN, -1, visitor);
    }

    /**
     * Walks the node tree with explicit target and traversal order.
     */
    public static void walk(Object container,
                            WalkTarget target,
                            WalkOrder order,
                            BiFunction<PathSegment, Object, Boolean> visitor) {
        walk(container, target, order, -1, visitor);
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
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(target, "target is null");
        Objects.requireNonNull(order, "order is null");
        Objects.requireNonNull(visitor, "visitor is null");
        _walk(container, new PathSegment.Root(null, container.getClass()), visitor, target, order, maxDepth);
    }

    private static void _walk(Object container, PathSegment path,
                              BiFunction<PathSegment, Object, Boolean> visitor,
                              WalkTarget target, WalkOrder order, int remainingDepth) {
        if (remainingDepth == 0) return;

        JsonType jt = JsonType.of(container);
        if (jt.isObject()) {
            if (order == WalkOrder.TOP_DOWN && (target == WalkTarget.CONTAINER || target == WalkTarget.ANY)) {
                if (!visitor.apply(path, container)) return;
            }
            Nodes.visitObject(container, (key, node) -> {
                PathSegment childPath = new PathSegment.Name(path, container.getClass(), key);
                _walk(node, childPath, visitor, target, order, remainingDepth - 1);
            });
            if (order == WalkOrder.BOTTOM_UP && (target == WalkTarget.CONTAINER || target == WalkTarget.ANY)) {
                if (!visitor.apply(path, container)) return;
            }
        } else if (jt.isArray()) {
            if (order == WalkOrder.TOP_DOWN && (target == WalkTarget.CONTAINER || target == WalkTarget.ANY)) {
                if (!visitor.apply(path, container)) return;
            }
            Nodes.visitArray(container, (idx, node) -> {
                PathSegment childPath = new PathSegment.Index(path, container.getClass(), idx);
                _walk(node, childPath, visitor, target, order, remainingDepth - 1);
            });
            if (order == WalkOrder.BOTTOM_UP && (target == WalkTarget.CONTAINER || target == WalkTarget.ANY)) {
                if (!visitor.apply(path, container)) return;
            }
        } else {
            if (target == WalkTarget.VALUE || target == WalkTarget.ANY) {
                if (!visitor.apply(path, container)) return;
            }
        }

    }

}
