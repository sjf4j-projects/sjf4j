package org.sjf4j;

import org.sjf4j.annotation.node.NodeObject;
import org.sjf4j.exception.NodeException;
import org.sjf4j.node.NodeMapper;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.meta.ContainerInfo;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.util.IdxConsumer;
import org.sjf4j.util.IdxPredicate;
import org.sjf4j.util.Numbers;
import org.sjf4j.meta.PojoInfo;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.util.Types;
import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;


/**
 * Core utilities for SJF4J's OBNT (Object-Based Node Tree).
 * <p>
 * {@code Nodes} is the main helper API for working directly with OBNT values:
 * Java object graphs composed of object nodes, array nodes, and value nodes.
 *
 * <p>It provides type conversion, inspection, traversal, equality, hashing,
 * copying, and container access for {@link JsonObject}, {@link JsonArray}, and
 * plain {@link Map}/{@link List} values. Backend-native or external tree
 * representations are supported only by individual operations where documented.
 * <p>
 * Conversion methods prefixed with {@code to} use strict scalar semantics;
 * methods prefixed with {@code as} allow scalar coercion where supported.
 * Both return {@code null} for a null input, but incompatible non-null values
 * can still throw rather than silently becoming {@code null}.
 */
public final class Nodes {

    private Nodes() {}

    /*
     * --------------------------------------------------------------
     * Direct Conversion (to / as)
     * --------------------------------------------------------------
     */

    /**
     * Converts a node to a String with strict type checking.
     */
    public static String toString(Object node) {
        return NodeMapper.toString(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to a String with flexible type conversion.
     */
    public static String asString(Object node) {
        return NodeMapper.asString(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to Character using strict conversion.
     */
    public static Character toChar(Object node) {
        return NodeMapper.toChar(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to Character using lenient conversion.
     */
    public static Character asChar(Object node) {
        return NodeMapper.asChar(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to enum using strict conversion.
     */
    public static <E extends Enum<E>> E toEnum(Object node, Class<E> enumClazz) {
        return NodeMapper.toEnum(node, enumClazz, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to enum using lenient conversion.
     */
    public static <E extends Enum<E>> E asEnum(Object node, Class<E> enumClazz) {
        return NodeMapper.asEnum(node, enumClazz, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to a Number with strict type checking.
     */
    public static Number toNumber(Object node) {
        return NodeMapper.toNumber(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to a Number with flexible type conversion.
     */
    public static Number asNumber(Object node) {
        return NodeMapper.asNumber(node, RuntimeContext.EMPTY);
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
        return NodeMapper.toBoolean(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to Boolean using lenient conversion.
     */
    public static Boolean asBoolean(Object node) {
        return NodeMapper.asBoolean(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts an OBNT value to an object node represented by {@link JsonObject}.
     * <p>
     * Existing {@link JsonObject} instances are returned as-is. {@link Map}
     * inputs are wrapped as the dynamic backing map. Other object node representations
     * are materialized into a new {@link JsonObject} by copying their readable
     * entries.
     */
    public static JsonObject toJsonObject(Object node) {
        return NodeMapper.toJsonObject(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts an OBNT value to a map with {@link Object} values.
     * <p>
     * Existing {@link Map} instances are returned as-is. {@link JsonObject}
     * and other object node representations are projected into a new outer map by
     * readable entries; entry values are not recursively converted.
     */
    public static Map<String, Object> toMap(Object node) {
        return NodeMapper.toMap(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts object entries to the requested value type. Unlike
     * {@link #toMap(Object)}, this may convert entry values.
     */
    public static <T> Map<String, T> toMap(Object node, Class<T> valueClazz) {
        return NodeMapper.toMap(node, valueClazz, RuntimeContext.EMPTY);
    }

    /**
     * Converts an OBNT value to an array node represented by {@link JsonArray}.
     * <p>
     * Existing {@link JsonArray} instances are returned as-is. {@link List}
     * inputs are wrapped as the backing list. Other array node representations are
     * materialized into a new {@link JsonArray} by copying their readable
     * elements.
     */
    public static JsonArray toJsonArray(Object node) {
        return NodeMapper.toJsonArray(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts an OBNT value to a list with {@link Object} values.
     * <p>
     * Existing {@link List} instances are returned as-is. {@link JsonArray}
     * follows {@link JsonArray#toList()} snapshot semantics; other array node
     * representations are materialized into a new outer list. Elements are not
     * recursively converted.
     */
    public static List<Object> toList(Object node) {
        return NodeMapper.toList(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts array elements to the requested element type. Unlike
     * {@link #toList(Object)}, this may convert elements.
     */
    public static <T> List<T> toList(Object node, Class<T> elementClazz) {
        return NodeMapper.toList(node, elementClazz, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to Object array.
     */
    public static Object[] toArray(Object node) {
        return NodeMapper.toArray(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to typed array.
     */
    public static <T> T[] toArray(Object node, Class<T> componentClazz) {
        return NodeMapper.toArray(node, componentClazz, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to Set with Object values.
     */
    public static Set<Object> toSet(Object node) {
        return NodeMapper.toSet(node, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to typed Set.
     */
    public static <T> Set<T> toSet(Object node, Class<T> elementClazz) {
        return NodeMapper.toSet(node, elementClazz, RuntimeContext.EMPTY);
    }

    /**
     * Adapts a node to the requested class using strict scalar conversion.
     * Compatible values may be returned without copying.
     */
    public static <T> T to(Object node, Class<T> clazz) {
        return NodeMapper.to(node, clazz, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to target type using lenient conversion.
     * <p>
     * Lenient mode allows cross-type scalar coercion when possible (for example
     * string to number/boolean). It does not make nested structural mapping
     * lenient; an invalid enum may return {@code null}, while other invalid
     * conversions can throw.
     */
    public static <T> T as(Object node, Class<T> clazz) {
        return NodeMapper.as(node, clazz, RuntimeContext.EMPTY);
    }


    /*
     * --------------------------------------------------------------
     * Basic
     * --------------------------------------------------------------
     */


    /**
     * Compares two values using OBNT semantics instead of Java type identity.
     * <p>
     * Object nodes are compared by readable key/value pairs, array nodes are
     * compared by order and element values, and number value nodes are
     * compared by numeric value (not boxed type).
     */
    public static boolean equals(Object source, Object target) {
        if (source == target) return true;

        // Fast paths for ordinary JSON scalar values.
        // Numeric equality must preserve cross-type semantics.
        if (source instanceof Number && target instanceof Number) {
            return Numbers.compare((Number) source, (Number) target) == 0;
        }
        if (source instanceof String && target instanceof String) {
            return source.equals(target);
        }
        if (source instanceof Boolean && target instanceof Boolean) {
            return source.equals(target);
        }

        // Generic OBNT dispatch, including external and mixed representations.
        JsonType jtSource = JsonType.of(source);
        JsonType jtTarget = JsonType.of(target);

        if (jtSource.isNull() || jtTarget.isNull()) {
            return jtSource.isNull() && jtTarget.isNull();
        }
        if (jtSource.isNumber() && jtTarget.isNumber()) {
            return Numbers.compare(toNumber(source), toNumber(target)) == 0;
        } else if (jtSource.isString() && jtTarget.isString()) {
            return toString(source).equals(toString(target));
        } else if (jtSource.isBoolean() && jtTarget.isBoolean()) {
            return toBoolean(source).equals(toBoolean(target));
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
     * Computes a hash code aligned with {@link #equals(Object, Object)}
     * OBNT semantics.
     * <p>
     * Object nodes are hashed from readable key/value pairs (order-insensitive
     * for object members), while array nodes are hashed in iteration order.
     */
    public static int hash(Object node) {
        if (node == null) return 0;
        if (node instanceof Number) return Numbers.hash((Number) node);
        if (node instanceof String || node instanceof Boolean) return node.hashCode();

        switch (JsonType.of(node)) {
            case NULL:
                return 0;
            case NUMBER:
            case INTEGER:
                return Numbers.hash(toNumber(node));
            case STRING:
                return toString(node).hashCode();
            case BOOLEAN:
                return toBoolean(node).hashCode();
            case OBJECT:
                final int[] objectHash = {1};
                forEachObject(node, (k, v) ->
                        objectHash[0] += 31 * k.hashCode() + hash(v));
                return objectHash[0];
            case ARRAY:
                final int[] arrayHash = {1};
                forEachArray(node, (i, v) ->
                        arrayHash[0] = 31 * arrayHash[0] + hash(v));
                return arrayHash[0];
            case UNKNOWN:
                return node.hashCode();
            default:
                return 0;
        }
    }

    /**
     * Returns a shallow copy of the given node.
     * <p>
     * Container nodes copy only the outer container; nested child values are shared.
     * For POJO/JOJO/JAJO representations, a new instance is created and direct field/item
     * values are transferred without deep recursion. Strings, numbers, booleans,
     * and unrecognized values are returned unchanged. {@code @NodeValue} types
     * use their registered value-copy hook. Map/List copies use the concrete
     * Java collection type; types with no supported construction path fail
     * instead of silently falling back to a different implementation.
     */
    @SuppressWarnings({"unchecked", "SuspiciousSystemArraycopy"})
    public static <T> T copy(T node) {
        if (node == null) return null;

        if (node instanceof String || node instanceof Number || node instanceof Boolean) {
            return node;
        }

        Class<?> rawClazz = node.getClass();
        if (node instanceof Map) {
            if (rawClazz == LinkedHashMap.class) {
                return (T) new LinkedHashMap<>((Map<String, Object>) node);
            }
            ContainerInfo ci = TypeRegistry.registerTypeInfo(rawClazz).containerInfo;
            if (ci.externalNode != null) {
                return (T) ci.externalNode.copy(node);
            }
            Map<String, Object> map = (Map<String, Object>) ci.newContainer();
            map.putAll((Map<String, Object>) node);
            return (T) map;
        }
        if (node instanceof List) {
            if (rawClazz == ArrayList.class) {
                return (T) new ArrayList<>((List<Object>) node);
            }
            ContainerInfo ci = TypeRegistry.registerTypeInfo(rawClazz).containerInfo;
            if (ci.externalNode != null) {
                return (T) ci.externalNode.copy(node);
            }
            List<Object> list = (List<Object>) ci.newContainer();
            list.addAll((List<Object>) node);
            return (T) list;
        }

        if (rawClazz == JsonObject.class) {
            return (T) new JsonObject(((JsonObject) node).toMap());
        }
        if (rawClazz == JsonArray.class) {
            return (T) new JsonArray(((JsonArray) node).toList());
        }
        if (node instanceof JsonArray) {
            PojoInfo pi = TypeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
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
            Set<Object> set = TypeRegistry.newSetContainer(rawClazz, 0, true);
            set.addAll((Set<Object>) node);
            return (T) set;
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(rawClazz);
        if (ti.valueSet != null) {
            return (T) ti.valueSet.defaultInfo.valueCopy(node);
        }
        if (ti.externalNode != null) {
            return (T) ti.externalNode.copy(node);
        }
        if (ti.pojoInfo != null) {
            PojoInfo pi = ti.pojoInfo;
            Map<String, Object> dynamic = pi.isJojo
                    ? ((JsonObject) node).dynamicProperties : null;
            int capacity = pi.readableProperties.length
                    + (dynamic == null ? 0 : dynamic.size());
            TypeRegistry.PojoCreationSession session =
                    new TypeRegistry.PojoCreationSession(pi.creatorInfo, capacity);

            // POJO and JOJO share the same static-property/creator path.
            for (PropertyInfo property : pi.readableProperties) {
                Object value = property.invokeGetter(node);
                int argIdx = pi.creatorInfo.getArgIndexOrAlias(property.name);
                if (argIdx >= 0) {
                    session.acceptCtorArg(argIdx, value);
                } else if (property.writable) {
                    session.acceptProperty(property, value);
                }
            }

            // JOJO adds dynamic entries without iterating over static properties
            // a second time or treating them as dynamic keys.
            if (dynamic != null) {
                for (Map.Entry<String, Object> entry : dynamic.entrySet()) {
                    session.acceptDynamic(entry.getKey(), entry.getValue());
                }
            }
            return (T) session.finish();
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
     *   <li>{@code @Type{..}}  - POJO / JOJO / External Object</li>
     *   <li>{@code [..]}       - List</li>
     *   <li>{@code J[..]}      - JsonArray</li>
     *   <li>{@code A[..]}      - Array</li>
     *   <li>{@code S[..]}      - Set</li>
     *   <li>{@code @Type[..]}  - JAJO / External Array</li>
     *   <li>{@code @Type#raw}  - NodeValue / External value</li>
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
            for (int i = 0, len = list.size(); i < len; i++) {
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
            PojoInfo pi = TypeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
            int[] idx = new int[1];
            jo.forEach((k, v) -> {
                if (idx[0]++ > 0) sb.append(", ");
                if (pi != null) {
                    PropertyInfo propertyInfo = pi.getPropertyNoAlias(k);
                    if (propertyInfo != null && propertyInfo.readable) {
                        sb.append("*");
                    }
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
            for (int i = 0, len = ja.size(); i < len; i++) {
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
            for (int i = 0, len = ja.size(); i < len; i++) {
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

        TypeInfo ti = TypeRegistry.registerTypeInfo(rawClazz);
        if (ti.valueSet != null) {
            Object raw = ti.valueSet.defaultInfo.valueToRaw(node);
            sb.append("@").append(rawClazz.getSimpleName()).append("#");
            _inspect(raw, sb, shapeOnly);
            return;
        }
        if (ti.externalNode != null) {
            switch (ti.externalNode.jsonType(node)) {
                case OBJECT:
                    sb.append("@").append(node.getClass().getSimpleName()).append("{");
                    int[] idx = new int[1];
                    ti.externalNode.forEachObject(node, (k, v) -> {
                        if (idx[0]++ > 0) sb.append(", ");
                        sb.append(k).append("=");
                        _inspect(v, sb, shapeOnly);
                    });
                    sb.append("}");
                    return;
                case ARRAY:
                    sb.append("@").append(node.getClass().getSimpleName()).append("[");
                    ti.externalNode.forEachArray(node, (i, v) -> {
                        if (i > 0) sb.append(", ");
                        _inspect(v, sb, shapeOnly);
                    });
                    sb.append("]");
                    return;
                default:
                    sb.append("@").append(rawClazz.getSimpleName()).append("#");
                    sb.append(node);
                    return;
            }
        }
        if (ti.pojoInfo != null) {
            PojoInfo pi = ti.pojoInfo;
            sb.append("@").append(rawClazz.getSimpleName()).append("{");
            int idx = 0;
            for (PropertyInfo propertyInfo : pi.readableProperties) {
                if (idx++ > 0) sb.append(", ");
                sb.append("*").append(propertyInfo.name).append("=");
                Object v = propertyInfo.invokeGetter(node);
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



    /*
     * --------------------------------------------------------------
     * Visit
     * --------------------------------------------------------------
     */

    /**
     * Visits each readable entry in an object node.
     */
    public static void forEachObject(Object node, BiConsumer<String, Object> consumer) {
        NodeWalker.forEachObject(node, consumer);
    }


    /**
     * Returns true if any readable object entry matches the predicate.
     */
    public static boolean anyMatchInObject(Object node, BiPredicate<String, Object> predicate) {
        return NodeWalker.anyMatchInObject(node, predicate);
    }


    /**
     * Replaces object-entry values in place using the given mapper.
     * <p>
     * The mapper receives each readable-and-writable entry key and current value,
     * and returns the new value to store. Returns true when at least one entry
     * changes by reference.
     */
    public static boolean replaceAllInObject(Object node, BiFunction<String, Object, Object> replacer) {
        return NodeWalker.replaceAllInObject(node, replacer);
    }

    /**
     * Removes object properties that match the predicate.
     * <p>
     * This operation applies to removable object properties only. Declared POJO
     * properties are not considered removable properties and therefore are left
     * unchanged. For supported backend-native object tree representations,
     * matching keys are collected first and removed afterward so live key views
     * remain safe to traverse.
     */
    public static boolean removeIfInObject(Object node, BiPredicate<String, Object> predicate) {
        return NodeWalker.removeIfInObject(node, predicate);
    }


    /**
     * Visits each element in an array node.
     */
    public static void forEachArray(Object node, IdxConsumer<Object> consumer) {
        NodeWalker.forEachArray(node, consumer);
    }

    /**
     * Returns true if any element matches the predicate.
     */
    public static boolean anyMatchInArray(Object node, IdxPredicate<Object> predicate) {
        return NodeWalker.anyMatchInArray(node, predicate);
    }

    /**
     * Returns the number of readable members in an object node.
     */
    public static int sizeInObject(Object node) {
        return NodeWalker.sizeInObject(node);
    }

    /**
     * Returns the number of elements in an array node.
     */
    public static int sizeInArray(Object node) {
        return NodeWalker.sizeInArray(node);
    }

    /**
     * Returns the readable key set for an object node.
     * <p>
     * Map and {@link JsonObject} inputs return their live key views. POJO inputs
     * return the structural readable-property key view; callers must treat it as
     * read-only metadata.
     */
    public static Set<String> keySetInObject(Object node) {
        return NodeWalker.keySetInObject(node);
    }

    /**
     * Returns the readable entry set for an object node.
     * <p>
     * Map and {@link JsonObject} inputs return live entry views. POJO inputs
     * return a read-only projection of readable properties; modifying returned
     * entries does not write back to the POJO.
     */
    public static Set<Map.Entry<String, Object>> entrySetInObject(Object node) {
        return NodeWalker.entrySetInObject(node);
    }

    /**
     * Returns an iterator over an array node.
     */
    public static Iterator<Object> iteratorInArray(Object node) {
        return NodeWalker.iteratorInArray(node);
    }

    /**
     * Returns true when an object node contains a readable key.
     */
    @SuppressWarnings("unchecked")
    public static boolean containsInObject(Object node, String key) {
        return NodeWalker.containsInObject(node, key);
    }

    /**
     * Returns whether an index is valid for an array node.
     * <p>
     * Negative indexes are normalized against current size.
     */
    public static boolean containsInArray(Object node, int idx) {
        return NodeWalker.containsInArray(node, idx);
    }

    /**
     * Gets a value by key from an object node.
     * <p>
     * Only readable members participate in this view. For POJO representations, properties
     * without a getter behave as absent and return {@code null}.
     */
    public static Object getInObject(Object node, String key) {
        return NodeWalker.getInObject(node, key);
    }

    /**
     * Gets a value by key from an object node and converts it to the
     * requested target type.
     */
        public static <T> T getInObject(Object node, String key, Class<T> clazz) {
            return NodeWalker.getInObject(node, key, clazz);
    }

    /**
     * Gets a value by index from an array node.
     * <p>
     * Negative indexes are supported ({@code -1} means last element). For List,
     * Array, and Set, out-of-range access returns {@code null}. JsonArray behavior
     * follows {@link JsonArray#getNode(int)}.
     */
    public static Object getInArray(Object node, int idx) {
        return NodeWalker.getInArray(node, idx);
    }

    /**
     * Gets a value by index from an array node and converts it to the
     * requested target type.
     */
    public static <T> T getInArray(Object node, int idx, Class<T> clazz) {
        return NodeWalker.getInArray(node, idx, clazz);
    }

    /**
     * Creates a missing object node representation for path ensure operations.
     */
    public static Object createObjectNode(Class<?> clazz) {
        return NodeWalker.createObjectNode(clazz);
    }

    /**
     * Creates a missing array node representation for path ensure operations.
     */
    public static Object createArrayNode(Class<?> clazz) {
        return NodeWalker.createArrayNode(clazz);
    }


    /**
     * Puts a value into an object node and returns the previous value when
     * the target shape exposes one.
     * <p>
     * For POJO representations, only discovered properties are writable; unknown keys fail.
     * POJO property writes do not read back the old value and therefore return
     * {@code null}.
     */
    public static Object putInObject(Object node, String key, Object value) {
        return NodeWalker.putInObject(node, key, value);
    }

    /**
     * Removes a key from an object node and returns the previous value.
     * <p>
     * Removal is supported for Map/JsonObject. POJO fields are structural and
     * cannot be removed.
     */
    public static Object removeInObject(Object node, String key) {
        return NodeWalker.removeInObject(node, key);
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
        return NodeWalker.computeIfAbsentInObject(node, key, computer);
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
        return NodeWalker.putInArray(node, idx, value);
    }

    /**
     * Sets a value in an array node by index.
     * <p>
     * For List/JsonArray: only existing normalized indexes may be replaced.
     * For Java arrays: only in-range replacement is allowed. Negative indexes are
     * supported.
     */
    public static Object setInArray(Object node, int idx, Object value) {
        return NodeWalker.setInArray(node, idx, value);
    }

    /**
     * Appends a value to an array node.
     * <p>
     * Java arrays are fixed-size and therefore not appendable.
     */
    public static void addInArray(Object node, Object value) {
        NodeWalker.addInArray(node, value);
    }

    /**
     * Inserts a value at the given index of an array node.
     * <p>
     * Indexed insert is supported by List/JsonArray only. Set and Java array
     * inputs are rejected because they are unordered or fixed-size.
     */
    public static void addInArray(Object node, int idx, Object value) {
        NodeWalker.addInArray(node, idx, value);
    }

    /**
     * Removes an element by index from an array node.
     * <p>
     * Negative indexes are supported for List. Java arrays and Set do not support
     * index-based removal.
     */
    public static Object removeInArray(Object node, int idx) {
        return NodeWalker.removeInArray(node, idx);
    }


}
