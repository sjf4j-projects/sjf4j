package org.sjf4j.node;

import org.sjf4j.InternalAccess;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.NodeKind;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.NodeException;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.meta.CreatorInfo;
import org.sjf4j.meta.CreatorState;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.util.Numbers;
import org.sjf4j.meta.OneOfInfo;
import org.sjf4j.meta.LazyTypeInfo;
import org.sjf4j.meta.PojoInfo;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.util.Types;
import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;
import org.sjf4j.util.Strings;
import org.sjf4j.value.ValueInfo;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;


/**
 * Runtime mapper for converting between OBNT node representations.
 *
 * <p>Supports structural conversion between POJOs, JOJOs, maps, lists,
 * arrays, sets, {@link JsonObject}, and {@link JsonArray}, including
 * {@link org.sjf4j.value.ValueCodec ValueCodec} and {@link OneOf} semantics.
 */
public final class NodeMapper {

    private NodeMapper() {
    }

    /*
     * --------------------------------------------------------------
     * Direct Adaptation (to / as)
     * --------------------------------------------------------------
     */


    /**
     * Returns the string value of a string-shaped node without cross-type coercion.
     * Character and enum values use their textual representation. A node value
     * must use a String raw representation under the selected value format.
     */
    public static String toString(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof String) return (String) node;
        if (node instanceof Character) return node.toString();
        if (node instanceof Enum) return ((Enum<?>) node).name();

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.valueSet != null) {
            ValueInfo vi = ti.requireValueInfo(context.valueFormat(node.getClass()));
            if (vi.rawClazz != String.class) {
                throw new NodeException("expected String raw type for '" + Types.name(node)
                        + "', but was '" + vi.rawClazz.getName() + "'");
            }
            return (String) vi.valueToRaw(node);
        }
        if (ti.externalNode != null) return ti.externalNode.toString(node);
        throw new NodeException("expected String, but was " + Types.name(node));
    }
    /**
     * Returns the textual representation of a scalar node using lenient coercion.
     * <p>
     * String, character, number, boolean, and enum values are supported.
     * Node values are interpreted through their selected value codec.
     * Object and array nodes are not stringified; use JSON binding for
     * serialization instead.
     *
     * @throws NodeException if the node is not a supported scalar
     */
    public static String asString(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof String) return (String) node;
        if (node instanceof Number || node instanceof Boolean || node instanceof Character) {
            return node.toString();
        }
        if (node instanceof Enum) {
            return ((Enum<?>) node).name();
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.valueSet != null) {
            ValueInfo vi = ti.requireValueInfo(context.valueFormat(node.getClass()));
            return asString(vi.valueToRaw(node), context);
        }
        if (ti.externalNode != null) {
            switch (ti.externalNode.jsonType(node)) {
                case NULL: return null;
                case STRING: return ti.externalNode.toString(node);
                case NUMBER: return ti.externalNode.toNumber(node).toString();
                case BOOLEAN: return ti.externalNode.toBoolean(node).toString();
                default: break;
            }
        }
        throw new NodeException("cannot convert node type '" + Types.name(node) + "' to String");
    }

    /**
     * Returns a Character from a single-character string representation.
     * Empty or multi-character strings are rejected.
     */
    public static Character toChar(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof Character) return (Character) node;

        String s = toString(node, context);
        if (s == null) return null;
        if (s.length() != 1) {
            throw new NodeException("cannot convert to char: expected single-character string, but length was " + s.length());
        }
        return s.charAt(0);
    }

    /**
     * Returns the first character of the lenient string representation.
     * Returns {@code null} for an absent or empty string.
     */
    public static Character asChar(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof Character) return (Character) node;

        String s = asString(node, context);
        return s != null && !s.isEmpty() ? s.charAt(0) : null;
    }

    /**
     * Resolves an enum constant by its exact name, using strict string semantics.
     * Numeric ordinals are not accepted.
     */
    public static <E extends Enum<E>> E toEnum(Object node, Class<E> enumClazz, RuntimeContext context) {
        Asserts.notNull(enumClazz, "enumClazz");
        if (node == null) return null;
        if (enumClazz.isInstance(node)) return enumClazz.cast(node);

        String s = toString(node, context);
        if (s == null) return null;
        try {
            return Enum.valueOf(enumClazz, s);
        } catch (IllegalArgumentException e) {
            throw new NodeException("cannot bind '" + s + "' to enum " + enumClazz.getName(), e);
        }
    }

    /**
     * Resolves an enum constant by name or by a numeric ordinal.
     * Numeric input (including numeric external nodes and value-codec raw values)
     * must be an exact, in-range integer. Numeric strings are treated as names,
     * not ordinals. Returns {@code null} for an unknown name or invalid ordinal.
     */
    public static <E extends Enum<E>> E asEnum(Object node, Class<E> enumClazz, RuntimeContext context) {
        Asserts.notNull(enumClazz, "enumClazz");
        if (node == null) return null;
        if (enumClazz.isInstance(node)) return enumClazz.cast(node);

        Object value = node;
        if (!(value instanceof Number) && !(value instanceof String)
                && !(value instanceof Character) && !(value instanceof Enum)) {
            TypeInfo ti = TypeRegistry.registerTypeInfo(value.getClass());
            if (ti.valueSet != null) {
                ValueInfo vi = ti.requireValueInfo(context.valueFormat(value.getClass()));
                value = vi.valueToRaw(value);
            } else if (ti.externalNode != null && ti.externalNode.jsonType(value) == JsonType.NUMBER) {
                value = ti.externalNode.toNumber(value);
            }
        }
        if (value instanceof Number) {
            Number number = (Number) value;
            if (!Numbers.isSemanticInteger(number)) return null;
            int ordinal;
            try {
                ordinal = Numbers.toInt(number);
            } catch (ArithmeticException e) {
                return null;
            }
            E[] values = enumClazz.getEnumConstants();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
        }

        String s = asString(value, context);
        if (s == null) return null;
        try {
            return Enum.valueOf(enumClazz, s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns the numeric value of a number-shaped node without coercion from
     * strings or booleans. The selected value codec must have a numeric raw type.
     */
    public static Number toNumber(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof Number) return (Number) node;

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.valueSet != null) {
            ValueInfo vi = ti.requireValueInfo(context.valueFormat(node.getClass()));
            if (!Number.class.isAssignableFrom(Types.box(vi.rawClazz))) {
                throw new NodeException("expected Number raw type for '" + Types.name(node)
                        + "', but was '" + vi.rawClazz.getName() + "'");
            }
            return (Number) vi.valueToRaw(node);
        }
        if (ti.externalNode != null) return ti.externalNode.toNumber(node);
        throw new NodeException("expected Number, but was " + Types.name(node));
    }

    /**
     * Returns a numeric value using lenient coercion: strings are parsed,
     * booleans map to 1/0, and enums map to their ordinal.
     * Invalid numeric text follows {@link Numbers#parseNumber(String)} semantics.
     */
    public static Number asNumber(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof Number) return (Number) node;
        if (node instanceof Boolean) return (Boolean) node ? 1 : 0;
        if (node instanceof String || node instanceof Character) return Numbers.parseNumber(node.toString());
        if (node instanceof Enum) return ((Enum<?>) node).ordinal();

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.valueSet != null) {
            ValueInfo vi = ti.requireValueInfo(context.valueFormat(node.getClass()));
            return asNumber(vi.valueToRaw(node), context);
        }
        if (ti.externalNode != null) {
            switch (ti.externalNode.jsonType(node)) {
                case NULL: return null;
                case NUMBER: return ti.externalNode.toNumber(node);
                case BOOLEAN: return ti.externalNode.toBoolean(node) ? 1 : 0;
                case STRING: return Numbers.parseNumber(ti.externalNode.toString(node));
                default: break;
            }
        }
        throw new NodeException("cannot convert node type '" + Types.name(node) + "' to Number");
    }


    /**
     * Returns a boolean-shaped node without coercion from numbers or strings.
     * The selected value codec must use Boolean as its raw type.
     */
    public static Boolean toBoolean(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof Boolean) return (Boolean) node;

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.valueSet != null) {
            ValueInfo vi = ti.requireValueInfo(context.valueFormat(node.getClass()));
            if (Types.box(vi.rawClazz) != Boolean.class) {
                throw new NodeException("expected Boolean raw type for '" + Types.name(node)
                        + "', but was '" + vi.rawClazz.getName() + "'");
            }
            return (Boolean) vi.valueToRaw(node);
        }
        if (ti.externalNode != null) return ti.externalNode.toBoolean(node);
        throw new NodeException("expected Boolean, but was " + Types.name(node));
    }

    /**
     * Returns a boolean using lenient coercion.
     * Accepted strings are true/false, yes/no, on/off, and 1/0 (ignoring case).
     * Numeric values must be exactly 0 or 1; fractions and overflow are rejected.
     */
    public static Boolean asBoolean(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof Boolean) return (Boolean) node;
        if (node instanceof String || node instanceof Character) {
            String str = node.toString().toLowerCase(Locale.ROOT);
            if ("true".equals(str) || "yes".equals(str) || "on".equals(str) || "1".equals(str)) return true;
            if ("false".equals(str) || "no".equals(str) || "off".equals(str) || "0".equals(str)) return false;
            throw new NodeException("cannot convert String to Boolean: supported formats: true/false, yes/no, on/off, 1/0");
        }
        if (node instanceof Number) {
            Number n = (Number) node;
            if (Numbers.isSemanticInteger(n)) {
                if (Numbers.compare(n, 1) == 0) return true;
                if (Numbers.compare(n, 0) == 0) return false;
            }
            throw new NodeException("cannot convert Number '" + n + "' to Boolean: expected exactly 0 or 1");
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.valueSet != null) {
            ValueInfo vi = ti.requireValueInfo(context.valueFormat(node.getClass()));
            return asBoolean(vi.valueToRaw(node), context);
        }
        if (ti.externalNode != null) {
            switch (ti.externalNode.jsonType(node)) {
                case NULL: return null;
                case BOOLEAN: return ti.externalNode.toBoolean(node);
                case NUMBER: return asBoolean(ti.externalNode.toNumber(node), context);
                case STRING: return asBoolean(ti.externalNode.toString(node), context);
                default: break;
            }
        }
        throw new NodeException("cannot convert node type '" + Types.name(node) + "' to Boolean");
    }

    /**
     * Returns a {@link JsonObject} shallow representation of an object-shaped node.
     * An existing JsonObject is reused; a Map is copied into a new outer
     * container. POJO and external object members are also copied into a new
     * outer container; nested entry values are not recursively mapped.
     */
    @SuppressWarnings("unchecked")
    public static JsonObject toJsonObject(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof JsonObject) return (JsonObject) node;
        if (node instanceof Map) return new JsonObject((Map<String, Object>) node);

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            switch (ti.externalNode.jsonType(node)) {
                case NULL: return null;
                case OBJECT: {
                    JsonObject jo = new JsonObject();
                    ti.externalNode.forEachObject(node, jo::put);
                    return jo;
                }
                default: throw new NodeException("expected object node, but was " + Types.name(node));

            }
        }
        if (ti.pojoInfo != null) {
            JsonObject jo = new JsonObject();
            for (PropertyInfo property : ti.pojoInfo.gettableProperties) {
                jo.put(property.name, property.invokeGetter(node));
            }
            return jo;
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Returns an object-shaped node as a Map with String keys.
     * <p>
     * Existing maps are reused. JsonObject values are projected into a
     * new outer map; other object representations are copied shallowly.
     * Child values are not recursively converted.
     *
     * <p>Map-backed object nodes must use String keys. This method does
     * not validate keys of existing maps at runtime.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof Map) return (Map<String, Object>) node;
        if (node instanceof JsonObject) return ((JsonObject) node).toMap();

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            switch (ti.externalNode.jsonType(node)) {
                case NULL: return null;
                case OBJECT: {
                    Map<String, Object> map = new LinkedHashMap<>();
                    ti.externalNode.forEachObject(node, map::put);
                    return map;
                }
                default: throw new NodeException("expected object node, but was " + Types.name(node));

            }
        }
        if (ti.pojoInfo != null) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (PropertyInfo property : ti.pojoInfo.gettableProperties) {
                map.put(property.name, property.invokeGetter(node));
            }
            return map;
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Returns a Map whose values are adapted to the specified class.
     * <p>
     * Each immediate member value is adapted using strict direct
     * adaptation. Compatible nested values may be reused.
     *
     * <p>A null or Object.class value type selects the untyped shallow
     * Map representation, which may reuse the original map.
     * Map-backed object nodes must have String keys.
     */
    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> toMap(Object node, Class<T> valueClazz, RuntimeContext context) {
        if (node == null) return null;
        if (valueClazz == null || valueClazz == Object.class) {
            return (Map<String, T>) toMap(node, context);
        }

        if (!(node instanceof Map) && !(node instanceof JsonObject)) {
            TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
            if (ti.externalNode != null && ti.externalNode.jsonType(node) == JsonType.NULL) {
                return null;
            }
        }

        Map<String, T> map = new LinkedHashMap<>();
        NodeWalker.forEachObject(node, (k, v) -> map.put(k, to(v, valueClazz, context)));
        return map;
    }


    /**
     * Returns a {@link JsonArray} shallow representation of an array-shaped node.
     * Existing JsonArray instances are reused; Lists are copied into a new
     * outer container. Other array representations also produce a new outer
     * container, without recursively mapping child elements.
     */
    @SuppressWarnings("unchecked")
    public static JsonArray toJsonArray(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof JsonArray) return (JsonArray) node;
        if (node instanceof List) return new JsonArray((List<Object>) node);
        if (node instanceof Set) {
            JsonArray ja = new JsonArray();
            ((Set<Object>) node).forEach(ja::add);
            return ja;
        }
        if (node.getClass().isArray()) {
            JsonArray ja = new JsonArray();
            for (int i = 0, len = Array.getLength(node); i < len; i++) ja.add(Array.get(node, i));
            return ja;
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            switch (ti.externalNode.jsonType(node)) {
                case NULL: return null;
                case ARRAY: {
                    JsonArray ja = new JsonArray();
                    ti.externalNode.forEachArray(node, ja::add);
                    return ja;
                }
                default: throw new NodeException("expected array node, but was " + Types.name(node));

            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Returns an array-shaped node as a {@code List<Object>}.
     * Existing Lists are reused. JsonArray follows {@link JsonArray#toList()}
     * snapshot semantics; other sources copy only their outer sequence.
     */
    @SuppressWarnings("unchecked")
    public static List<Object> toList(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof List) return (List<Object>) node;
        if (node instanceof JsonArray) return ((JsonArray) node).toList();
        if (node instanceof Set) {
            return new ArrayList<>(((Set<Object>) node));
        }
        if (node.getClass().isArray()) {
            List<Object> list = new ArrayList<>();
            for (int i = 0, len = Array.getLength(node); i < len; i++) list.add(Array.get(node, i));
            return list;
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            switch (ti.externalNode.jsonType(node)) {
                case NULL: return null;
                case ARRAY: {
                    List<Object> list = new ArrayList<>();
                    ti.externalNode.forEachArray(node, list::add);
                    return list;
                }
                default: throw new NodeException("expected array node, but was " + Types.name(node));

            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Copies array elements into a List, applying strict direct adaptation to
     * each element. A null or Object.class component type uses the untyped
     * shallow List form, which may return the original List.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> toList(Object node, Class<T> elementClazz, RuntimeContext context) {
        if (node == null) return null;
        if (elementClazz == null || elementClazz == Object.class) {
            return (List<T>) toList(node, context);
        }

        if (!(node instanceof List) && !(node instanceof JsonArray)
                && !(node instanceof Set) && !node.getClass().isArray()) {
            TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
            if (ti.externalNode != null && ti.externalNode.jsonType(node) == JsonType.NULL) {
                return null;
            }
        }

        List<T> list = new ArrayList<>();
        NodeWalker.forEachArray(node, (i, e) -> list.add(to(e, elementClazz, context)));
        return list;
    }

    /**
     * Returns array elements as {@code Object[]} without recursively mapping them.
     * Existing reference arrays are reused; primitive arrays are boxed into a new
     * array. Other indexed sources are shallowly copied into an Object array.
     */
    @SuppressWarnings("unchecked")
    public static Object[] toArray(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node.getClass().isArray()) {
            if (node.getClass().getComponentType().isPrimitive()) {
                int len = Array.getLength(node);
                Object[] arr = new Object[len];
                for (int i = 0; i < len; i++) {
                    arr[i] = Array.get(node, i); // Auto boxing
                }
                return arr;
            } else {
                return ((Object[]) node);
            }
        }
        if (node instanceof List) return ((List<Object>) node).toArray();
        if (node instanceof JsonArray) return ((JsonArray) node).toArray();
        if (node instanceof Set) return ((Set<Object>) node).toArray();

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            switch (ti.externalNode.jsonType(node)) {
                case NULL: return null;
                case ARRAY: {
                    int len = ti.externalNode.sizeInArray(node);
                    Object[] arr = new Object[len];
                    ti.externalNode.forEachArray(node, (i, e) -> arr[i] = e);
                    return arr;
                }
                default: throw new NodeException("expected array node, but was " + Types.name(node));

            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Returns a typed reference array, adapting each element strictly.
     * Passing null or Object.class uses {@link #toArray(Object, RuntimeContext)}.
     * Primitive component types must use {@link #to(Object, Class, RuntimeContext)}
     * with an array class such as {@code int[].class} instead.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(Object node, Class<T> componentClazz, RuntimeContext context) {
        if (node == null) return null;
        if (componentClazz == null || componentClazz == Object.class) {
            return (T[]) toArray(node, context);
        }
        if (componentClazz.isPrimitive()) {
            throw new IllegalArgumentException("primitive array component is not supported by toArray: "
                    + componentClazz.getName());
        }

        if (!(node instanceof List) && !(node instanceof JsonArray)
                && !(node instanceof Set) && !node.getClass().isArray()) {
            TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
            if (ti.externalNode != null && ti.externalNode.jsonType(node) == JsonType.NULL) {
                return null;
            }
        }

        int len = NodeWalker.sizeInArray(node);
        T[] arr = (T[]) Array.newInstance(componentClazz, len);
        NodeWalker.forEachArray(node, (i, e) -> arr[i] = to(e, componentClazz, context));
        return arr;
    }

    /**
     * Returns an array-shaped node as a Set with shallowly retained elements.
     * Existing Sets are reused. Other sources are copied into a LinkedHashSet,
     * removing duplicates while preserving encounter order.
     */
    @SuppressWarnings("unchecked")
    public static Set<Object> toSet(Object node, RuntimeContext context) {
        if (node == null) return null;
        if (node instanceof Set) return (Set<Object>) node;
        if (node instanceof List) {
            return new LinkedHashSet<>(((List<Object>) node));
        }
        if (node instanceof JsonArray) return ((JsonArray) node).toSet();
        if (node.getClass().isArray()) {
            Set<Object> set = new LinkedHashSet<>();
            for (int i = 0, len = Array.getLength(node); i < len; i++) set.add(Array.get(node, i));
            return set;
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            switch (ti.externalNode.jsonType(node)) {
                case NULL: return null;
                case ARRAY: {
                    Set<Object> set = new LinkedHashSet<>();
                    ti.externalNode.forEachArray(node, (i, e) -> set.add(e));
                    return set;
                }
                default: throw new NodeException("expected array node, but was " + Types.name(node));

            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Copies array elements into a Set, applying strict direct adaptation to
     * each element. A null or Object.class component type uses the untyped
     * shallow Set form, which may return the original Set.
     */
    @SuppressWarnings("unchecked")
    public static <T> Set<T> toSet(Object node, Class<T> elementClazz, RuntimeContext context) {
        if (node == null) return null;
        if (elementClazz == null || elementClazz == Object.class) {
            return (Set<T>) toSet(node, context);
        }

        if (!(node instanceof List) && !(node instanceof JsonArray)
                && !(node instanceof Set) && !node.getClass().isArray()) {
            TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
            if (ti.externalNode != null && ti.externalNode.jsonType(node) == JsonType.NULL) {
                return null;
            }
        }

        Set<T> set = new LinkedHashSet<>();
        NodeWalker.forEachArray(node, (i, e) -> set.add(to(e, elementClazz, context)));
        return set;
    }


    /**
     * Adapts a node to the requested target class.
     * <p>
     * Compatible nodes are reused. Scalar adaptation is strict or lenient
     * according to the requested mode. Java arrays apply the same mode to
     * their elements.
     * <p>
     * Standard object and array representations use direct adaptation.
     * Concrete POJO, JOJO, JAJO, and specialized container targets fall
     * back to structural mapping without forcing a deep copy.
     *
     * <p>Node values are not implicitly expanded into object or array
     * structures through their value codecs.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _adapt(Object node, Class<?> toClazz, boolean lenient, RuntimeContext context) {
        if (toClazz == Object.class) return node;
        if (node == null) return NodeMapper.convert(null, toClazz, false, context);
        if (toClazz.isInstance(node)) return node;

        Class<?> toBoxed = Types.box(toClazz);
        if (toBoxed == String.class) {
            return lenient ? asString(node, context) : toString(node, context);
        }
        if (toBoxed == Boolean.class) {
            return lenient ? asBoolean(node, context) : toBoolean(node, context);
        }
        if (Number.class.isAssignableFrom(toBoxed)) {
            Number n = lenient ? asNumber(node, context) : toNumber(node, context);
            return n == null ? null : Numbers.to(n, toBoxed);
        }
        if (toBoxed == Character.class) {
            return lenient ? asChar(node, context) : toChar(node, context);
        }
        if (toClazz.isEnum()) {
            return lenient ? asEnum(node, (Class<? extends Enum>) toClazz, context)
                    : toEnum(node, (Class<? extends Enum>) toClazz, context);
        }

        if (toClazz == Map.class) return toMap(node, context);
        if (toClazz == JsonObject.class) return toJsonObject(node, context);
        if (toClazz == List.class) return toList(node, context);
        if (toClazz == JsonArray.class) return toJsonArray(node, context);
        if (toClazz == Set.class) return toSet(node, context);

        if (toClazz.isArray()) {
            if (!(node instanceof List) && !(node instanceof JsonArray)
                    && !(node instanceof Set) && !node.getClass().isArray()) {
                TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
                if (ti.externalNode != null && ti.externalNode.jsonType(node) == JsonType.NULL) {
                    return null;
                }
            }

            Class<?> componentClazz = toClazz.getComponentType();
            int len = NodeWalker.sizeInArray(node);
            Object arr = Array.newInstance(componentClazz, len);
            NodeWalker.forEachArray(node, (i, e) ->
                    Array.set(arr, i, _adapt(e, componentClazz, lenient, context)));
            return arr;
        }

        return NodeMapper.convert(node, toClazz, false, context);
    }

    /**
     * Adapts a node to the requested class using strict scalar semantics.
     * <p>
     * Compatible values may be reused. Standard node containers use direct
     * adaptation; other supported concrete targets may use structural mapping
     * without forcing a deep copy.
     *
     * @throws NullPointerException if clazz or context is null
     */
    @SuppressWarnings("unchecked")
    public static <T> T to(Object node, Class<T> clazz, RuntimeContext context) {
        Asserts.notNull(clazz, "clazz");
        return (T) _adapt(node, clazz, false, context);
    }

    /**
     * Adapts a node to the requested class using lenient scalar semantics.
     * <p>
     * Leniency applies to the requested scalar value, not recursively to POJO
     * properties or other nested members. Structural targets use the same
     * mapping semantics as {@link #to(Object, Class, RuntimeContext)}.
     * An invalid enum name or ordinal returns null; other invalid coercions
     * may throw an exception.
     *
     * @throws NullPointerException if clazz or context is null
     */
    @SuppressWarnings("unchecked")
    public static <T> T as(Object node, Class<T> clazz, RuntimeContext context) {
        Asserts.notNull(clazz, "clazz");
        return (T) _adapt(node, clazz, true, context);
    }

















    /*
     * --------------------------------------------------------------
     * Structural Conversion (convert / convertToRaw)
     * --------------------------------------------------------------
     */


    /**
     * Converts a node to a declared class or generic target type. When
     * {@code deepCopy} is true, supported nested structures are copied;
     * otherwise compatible values can be reused. Binding failures can carry
     * a node path to the value that could not be converted.
     */
    public static Object convert(Object node, Type type, boolean deepCopy, RuntimeContext context) {
        Asserts.notNull(type, "type");
        Asserts.notNull(context, "context");
        try {
            Class<?> rawBox = Types.rawBox(type);
            return _convert(node, type, rawBox, null, deepCopy, PathSegment.Root.INSTANCE, context);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to convert node from '" + Types.name(node) +
                    "' to '" + type.getTypeName() + "'", e);
        }
    }

    /**
     * Converts a node to its raw JSON-compatible representation using the
     * supplied runtime context.
     *
     * <p>Object nodes become maps, array nodes become lists, and node values
     * are converted through their configured value codecs.
     */
    public static Object convertToRaw(Object node, RuntimeContext context) {
        Asserts.notNull(context, "context");
        return _convertToRaw(node, PathSegment.Root.INSTANCE, context);
    }


    /**
     * Internal conversion with path support.
     */
    @SuppressWarnings("unchecked")
    private static Object _convert(Object node, Type toType, Class<?> toBoxed,
                                   TypeInfo ti, boolean deepCopy, PathSegment ps, RuntimeContext context) {
        try {
            if (node == null) {
                if (ti == null) ti = TypeRegistry.registerTypeInfo(toBoxed);
                if (ti.valueSet != null) {
                    String valueFormat = context.valueFormat(toBoxed);
                    return ti.valueSet.selectForRead(JsonType.NULL, valueFormat).rawToValue(null);
                }
                // External targets, like other reference targets, retain Java null.
                return null;
            }

            if (toBoxed == Object.class) {
                return deepCopy ? _deepCopy(node, toType, toBoxed, ps, context) : node;
            }

            // Resolve unknown metadata before identity so class-level OneOf also
            // applies when the source already implements the declared target type.
            if (ti == null) ti = TypeRegistry.registerTypeInfo(toBoxed);
            if (ti.oneOfInfo != null) {
                return _convertOneOf(node, toBoxed, ti.oneOfInfo, deepCopy, ps, context);
            }

            // Compatible values can only be returned as-is when the target has no
            // generic structure to honor. Parameterized containers/POJOs still need
            // traversal so their declared element/member types are converted.
            if (toBoxed.isInstance(node) && !Types.hasGenericStructure(toType)) {
                return deepCopy ? _deepCopy(node, toType, toBoxed, ps, context) : node;
            }

            if (ti.valueSet != null) {
                return _convertValue(node, toBoxed, ti.valueSet, deepCopy, ps, context);
            }
            // Map/List classification wins over an external adapter. Only
            // nonconstructible containers use its mapping-only native builder.
            if (ti.containerInfo != null && ti.containerInfo.externalNode != null
                    && (Map.class.isAssignableFrom(toBoxed) || List.class.isAssignableFrom(toBoxed))) {
                return _convertExternal(node, toBoxed, ti.containerInfo.externalNode, deepCopy, ps, context);
            }
            if (ti.externalNode != null) {
                return _convertExternal(node, toBoxed, ti.externalNode, deepCopy, ps, context);
            }

            // Dispatch on the source representation for plain and unregistered targets.
            if (node instanceof String) {
                return _convertString(node.toString(), toBoxed, ps);
            }

            if (node instanceof Number) {
                if (Number.class.isAssignableFrom(toBoxed)) {
                    return Numbers.to((Number) node, toBoxed);
                }
                throw new BindingException("cannot convert node from '" +
                        Types.name(node) + "' to '" + toType + "'", ps);
            }

            if (node instanceof Boolean) {
                if (toBoxed == Boolean.class) {
                    return node;
                }
                throw new BindingException("cannot convert node from '" +
                        Types.name(node) + "' to '" + toType + "'", ps);
            }

            if (node instanceof Character) {
                return _convertString(node.toString(), toBoxed, ps);
            }

            if (node instanceof Enum) {
                return _convertString(((Enum<?>) node).name(), toBoxed, ps);
            }

            if (node instanceof Map) {
                return _convertFromObjectEntries(
                        ((Map<String, Object>) node).entrySet(), ((Map<?, ?>) node).size(),
                        false, "Map", toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof List) {
                return _convertFromIndexedSource(node, null, "List",
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof JsonObject) {
                JsonObject jo = (JsonObject) node;
                return _convertFromObjectEntries(jo.entrySet(), jo.size(),
                        false, "JsonObject", toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof JsonArray) {
                return _convertFromIndexedSource(node, null, "JsonArray",
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node.getClass().isArray()) {
                return _convertFromIndexedSource(node, null, "Array",
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof Set) {
                return _convertFromSet((Set<Object>) node,
                        toBoxed, toType, deepCopy, ps, context);
            }

            TypeInfo sourceTi = TypeRegistry.registerTypeInfo(node.getClass());
            if (sourceTi.externalNode != null) {
                return _convertFromExternal(node, sourceTi.externalNode,
                        toBoxed, toType, deepCopy, ps, context);
            }

            PojoInfo sourceInfo = sourceTi.pojoInfo;
            if (sourceInfo != null) {
                return _convertFromPojo(node, sourceInfo,
                        toBoxed, toType, deepCopy, ps, context);
            }

            throw new BindingException("cannot convert node from '" +
                    Types.name(node) + "' to '" + toType + "'", ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("cannot convert node from '" +
                    Types.name(node) + "' to '" + toType + "'", ps, e);
        }
    }


    /**
     * Auto-selects a value codec by the raw JSON shape during mapping.
     * Explicit property codecs still call the ValueInfo overload below.
     */
    private static Object _convertValue(Object node, Class<?> toBoxed,
                                        org.sjf4j.value.ValueSet set,
                                        boolean deepCopy, PathSegment ps, RuntimeContext context) {
        String preferred = context.valueFormat(toBoxed);
        if (node == null) return set.selectForRead(JsonType.NULL, preferred).rawToValue(null);
        if (toBoxed.isInstance(node)) {
            return deepCopy ? set.forWrite(preferred).valueCopy(node) : node;
        }

        Object raw = node;
        if (!(node instanceof String || node instanceof Number || node instanceof Boolean
                || node instanceof Map || node instanceof List)) {
            TypeInfo sourceInfo = TypeRegistry.registerTypeInfo(node.getClass());
            if (sourceInfo.valueSet != null) {
                ValueInfo source = sourceInfo.requireValueInfo(
                        context.valueFormat(node.getClass()));
                raw = source.valueToRaw(node);
            } else if (sourceInfo.externalNode != null) {
                raw = _convertToRaw(node, ps, context);
            }
        }

        JsonType shape = raw == null ? JsonType.NULL
                : JsonType.of(NodeKind.plainOf(raw.getClass()));
        ValueInfo selected = set.selectForRead(shape, preferred);
        return selected.rawToValue(raw);
    }

    private static Object _convertValue(Object node, Class<?> toBoxed, ValueInfo target,
                                        boolean deepCopy, PathSegment ps, RuntimeContext context) {
        if (node == null) return target.rawToValue(null);
        if (toBoxed.isInstance(node)) {
            return deepCopy ? target.valueCopy(node) : node;
        }
        // Raw scalar inputs are the common ValueCodec path. Avoid metadata
        // lookups for them; ValueInfo enforces the declared raw type.
        if (node instanceof String || node instanceof Number || node instanceof Boolean) {
            return target.rawToValue(node);
        }

        TypeInfo sourceInfo = TypeRegistry.registerTypeInfo(node.getClass());
        Object raw = node;
        if (sourceInfo.valueSet != null) {
            ValueInfo source = sourceInfo.requireValueInfo(
                    context.valueFormat(node.getClass()));
            raw = source.valueToRaw(node);
        } else if (sourceInfo.externalNode != null) {
            raw = _convertToRaw(node, ps, context);
        }
        return target.rawToValue(raw);
    }


    private static Object _convertFromExternal(Object node, ExternalNode<Object> external,
                                               Class<?> toBoxed, Type type, boolean deepCopy,
                                               PathSegment ps, RuntimeContext context) {
        switch (external.jsonType(node)) {
            case NULL:
                return _convert(null, type, toBoxed, null, deepCopy, ps, context);
            case STRING:
                return _convert(external.toString(node), type, toBoxed, null, deepCopy, ps, context);
            case NUMBER:
                return _convert(external.toNumber(node), type, toBoxed, null, deepCopy, ps, context);
            case BOOLEAN:
                return _convert(external.toBoolean(node), type, toBoxed, null, deepCopy, ps, context);
            case OBJECT:
                return _convertFromObjectEntries(external.entrySetInObject(node),
                        external.sizeInObject(node), true,
                        "External object", toBoxed, type, deepCopy, ps, context);
            case ARRAY:
                return _convertFromIndexedSource(node, external, "External array",
                        toBoxed, type, deepCopy, ps, context);
            default:
                throw new BindingException("unsupported external node type '" + Types.name(node) + "'", ps);
        }
    }

    /**
     * Constructs the target's native representation without intermediate JSON trees.
     * Standard Java/OBNT containers are handled before external adapters.
     */
    private static Object _convertExternal(Object node, Class<?> toBoxed,
                                           ExternalNode<Object> target,
                                           boolean deepCopy, PathSegment ps, RuntimeContext context) {
        try {
            Object result;
            if (node == null) {
                result = target.createValueNode(null);
            } else if (target.nodeType().isInstance(node)) {
                result = deepCopy ? target.deepCopy(node) : node;
            } else if (node instanceof String || node instanceof Number || node instanceof Boolean) {
                result = target.createValueNode(node);
            } else if (node instanceof Character) {
                result = target.createValueNode(node.toString());
            } else if (node instanceof Enum) {
                result = target.createValueNode(((Enum<?>) node).name());
            } else if (node instanceof Map || node instanceof JsonObject
                    || node instanceof List || node instanceof JsonArray
                    || node.getClass().isArray() || node instanceof Set) {
                result = target.convertContainer(toBoxed, node, context);
                if (deepCopy) result = target.deepCopy(result);
            } else {
                TypeInfo sourceInfo = TypeRegistry.registerTypeInfo(node.getClass());
                if (sourceInfo.valueSet != null) {
                    ValueInfo info = sourceInfo.requireValueInfo(context.valueFormat(node.getClass()));
                    return _requireExternalTarget(_convertExternal(
                            info.valueToRaw(node), target.nodeType(), target, deepCopy, ps, context), toBoxed, ps);
                }

                ExternalNode<Object> sourceExternal = sourceInfo.externalNode;
                if (sourceExternal != null) {
                    switch (sourceExternal.jsonType(node)) {
                        case NULL:
                            result = target.createValueNode(null);
                            break;
                        case STRING:
                            result = target.createValueNode(sourceExternal.toString(node));
                            break;
                        case NUMBER:
                            result = target.createValueNode(sourceExternal.toNumber(node));
                            break;
                        case BOOLEAN:
                            result = target.createValueNode(sourceExternal.toBoolean(node));
                            break;
                        case OBJECT:
                        case ARRAY:
                            result = target.convertContainer(toBoxed, node, context);
                            if (deepCopy) result = target.deepCopy(result);
                            break;
                        default:
                            throw new BindingException("unsupported external node type '" + Types.name(node) + "'", ps);
                    }
                } else if (sourceInfo.pojoInfo != null) {
                    result = target.convertContainer(toBoxed, node, context);
                    if (deepCopy) result = target.deepCopy(result);
                } else {
                    throw new BindingException("unsupported node type '" + Types.name(node) + "'", ps);
                }
            }
            return _requireExternalTarget(result, toBoxed, ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("cannot convert node from '" + Types.name(node)
                    + "' to external type '" + toBoxed.getName() + "'", ps, e);
        }
    }

    private static Object _requireExternalTarget(Object result, Class<?> toBoxed, PathSegment ps) {
        if (!toBoxed.isInstance(result)) {
            throw new BindingException("native node does not match target type '"
                    + toBoxed.getName() + "'", ps);
        }
        return result;
    }

    private static Object _convertOneOf(Object node, Class<?> toBoxed,
                                        OneOfInfo oneOfInfo, boolean deepCopy, PathSegment ps, RuntimeContext context) {
        LazyTypeInfo target;
        JsonType jsonType = JsonType.of(node);

        if (oneOfInfo.hasDiscriminator) {
            if (oneOfInfo.scope != OneOf.Scope.CURRENT) {
                throw new BindingException("oneOf scope '" + oneOfInfo.scope + "' is not supported", ps);
            }

            if (!jsonType.isObject()) {
                if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                    return null;
                }
                throw new BindingException("node must be a JSON object, when OneOf has a CURRENT discriminator", ps);
            }

            Object discriminatorValue;
            if (!oneOfInfo.discriminatorKey.isEmpty()) {
                discriminatorValue = Nodes.getInObject(node, oneOfInfo.discriminatorKey);
            } else if (!oneOfInfo.discriminatorPath.isEmpty()) {
                discriminatorValue = oneOfInfo.compiledPath.getNode(node);
            } else {
                discriminatorValue = null;
            }

            // Native external discriminator values must be normalized before
            // matching Java @OneOf case values.
            if (discriminatorValue != null) {
                TypeInfo discriminatorTi = TypeRegistry.registerTypeInfo(discriminatorValue.getClass());
                if (discriminatorTi.externalNode != null) {
                    discriminatorValue = _convertToRaw(discriminatorValue, ps, context);
                }
            }

            if (discriminatorValue == null) {
                if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                    return null;
                }
                throw new BindingException("not found value for discriminator "
                        + (oneOfInfo.keyDiscriminator
                        ? "key '" + oneOfInfo.discriminatorKey
                        : "path '" + oneOfInfo.discriminatorPath) + "'", ps);
            }

            target = oneOfInfo.lazyTypeInfoByWhen(discriminatorValue);
            if (target == null) {
                if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                    return null;
                }
                throw new BindingException("oneOf discriminator has no matching mapping: value='" +
                        discriminatorValue + "'", ps);
            }
        } else {
            target = oneOfInfo.lazyTypeInfoByJsonType(jsonType);
            if (target == null) {
                if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                    return null;
                }
                throw new BindingException("oneOf mapping does not support jsonType=" + jsonType +
                        " for type '" + toBoxed.getName() + "'", ps);
            }
        }

        return _convert(node, target.clazz, Types.rawBox(target.clazz), target.get(), deepCopy, ps, context);
    }

    /**
     * Internal deep structural copy with path support.
     */
    @SuppressWarnings("unchecked")
    private static Object _deepCopy(Object node, Type toType, Class<?> toBoxed, PathSegment ps,
                                    RuntimeContext context) {
        try {
            if (node == null) return null;

            /*
             * Target representation differs from the runtime value.
             *
             * In that case this is no longer a pure copy: let conversion
             * establish the requested target representation first.
             */
            if (toBoxed != Object.class && !toBoxed.isInstance(node)) {
                return _convert(node, toType, toBoxed, null, true, ps, context);
            }

            Class<?> nodeClazz = node.getClass();
            /*
             * Immutable JSON scalar values.
             */
            if (nodeClazz == String.class || nodeClazz == Boolean.class ||
                    nodeClazz == Integer.class || nodeClazz == Long.class ||
                    nodeClazz == Double.class || nodeClazz == Float.class ||
                    nodeClazz == Short.class || nodeClazz == Byte.class) {
                return node;
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(nodeClazz);

            if (ti.valueSet != null) {
                String valueFormat = context.valueFormat(nodeClazz);
                ValueInfo valueInfo = ti.requireValueInfo(valueFormat);
                return valueInfo.valueCopy(node);
            }
            /*
             * Map.
             */
            if (node instanceof Map) {
                Map<String, Object> src = (Map<String, Object>) node;
                if (ti.containerInfo != null && ti.containerInfo.externalNode != null) {
                    return ti.containerInfo.externalNode.deepCopy(node);
                }
                Map<String, Object> target = TypeRegistry.newMapContainer(nodeClazz, src.size(), false);
                Type valueType = Types.resolveTypeArgument(toType, Map.class, 1);
                Class<?> valueBoxed = Types.rawBox(valueType);
                for (Map.Entry<String, Object> entry : src.entrySet()) {
                    PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                    target.put(entry.getKey(), _deepCopy(entry.getValue(), valueType, valueBoxed, cps, context));
                }
                return target;
            }

            /*
             * List.
             */
            if (node instanceof List) {
                List<Object> src = (List<Object>) node;
                if (ti.containerInfo != null && ti.containerInfo.externalNode != null) {
                    return ti.containerInfo.externalNode.deepCopy(node);
                }
                List<Object> target = TypeRegistry.newListContainer(nodeClazz, src.size(), false);
                Type elemType = Types.resolveTypeArgument(toType, List.class, 0);
                Class<?> elemBoxed = Types.rawBox(elemType);

                int i = 0;
                for (Object value : src) {
                    PathSegment cps = new PathSegment.Index(ps, i++);
                    target.add(_deepCopy(value, elemType, elemBoxed, cps, context));
                }
                return target;
            }

            /*
             * Plain JsonObject.
             *
             * JOJO is handled together with normal POJOs below.
             */
            if (nodeClazz == JsonObject.class) {
                JsonObject src = (JsonObject) node;
                JsonObject target = new JsonObject();
                src.forEach((key, value) -> {
                    PathSegment cps = new PathSegment.Name(ps, key);
                    target.put(key, _deepCopy(value, Object.class, Object.class, cps, context));
                });
                return target;
            }

            /*
             * JsonArray / JAJO.
             */
            if (node instanceof JsonArray) {
                JsonArray src = (JsonArray) node;
                JsonArray target = nodeClazz == JsonArray.class
                        ? new JsonArray()
                        : (JsonArray) TypeRegistry.requireRegisteredPojoInfo(nodeClazz).creatorInfo.forceNewPojo();
                Type elemType = Types.resolveTypeArgument(toType, List.class, 0);
                Class<?> elemBoxed = Types.rawBox(elemType);
                for (int i = 0; i < src.size(); i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    target.add(_deepCopy(src.getNode(i), elemType, elemBoxed, cps, context));
                }
                return target;
            }

            /*
             * Java array.
             */
            if (nodeClazz.isArray()) {
                int len = Array.getLength(node);
                Class<?> componentClass = nodeClazz.getComponentType();
                Object target = Array.newInstance(componentClass, len);
                Class<?> componentBoxed = Types.rawBox(componentClass);
                for (int i = 0; i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    Array.set(target, i, _deepCopy(Array.get(node, i), componentClass, componentBoxed, cps, context));
                }
                return target;
            }

            /*
             * Set.
             */
            if (node instanceof Set) {
                Set<Object> src = (Set<Object>) node;
                Set<Object> target = TypeRegistry.newSetContainer(nodeClazz, src.size(), true);
                Type elemType = Types.resolveTypeArgument(toType, Set.class, 0);
                Class<?> elemBoxed = Types.rawBox(elemType);
                int i = 0;
                for (Object value : src) {
                    PathSegment cps = new PathSegment.Index(ps, i++);
                    target.add(_deepCopy(value, elemType, elemBoxed, cps, context));
                }
                return target;
            }

            if (ti.externalNode != null) {
                return ti.externalNode.deepCopy(node);
            }

            /*
             * POJO / JOJO.
             */
            PojoInfo pojoInfo = ti.pojoInfo;
            if (pojoInfo != null) {
                /*
                 * If the declared target is a supertype (Object/interface/base
                 * class), the runtime POJO is what is actually being recreated.
                 *
                 * Its member types therefore need to be resolved against the
                 * runtime class rather than against the declared supertype.
                 */
                Type pojoType = nodeClazz == toBoxed ? toType : nodeClazz;
                return _deepCopyPojo(node, pojoType, nodeClazz, pojoInfo, ps, context);
            }

            /*
             * Unknown immutable / unsupported value.
             */
            return node;
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to deep copy node '" + Types.name(node) + "'", ps, e);
        }
    }

    private static Object _deepCopyPojo(Object source, Type type, Class<?> boxed, PojoInfo pojoInfo, PathSegment ps,
                                        RuntimeContext context) {
        CreatorInfo ci = pojoInfo.creatorInfo;
        int expected = pojoInfo.gettableProperties.length;
        if (source instanceof JsonObject) {
            expected += ((JsonObject) source).size();
        }
        TypeRegistry.PojoCreationSession session = new TypeRegistry.PojoCreationSession(ci, expected);

        /*
         * Static POJO properties.
         */
        for (PropertyInfo property : pojoInfo.gettableProperties) {
            String name = property.name;
            Object value = property.invokeGetter(source);
            PathSegment cps = new PathSegment.Name(ps, name);
            int argIdx = ci.getArgIndexOrAlias(name);
            if (argIdx >= 0) {
                Type argType = Types.resolveMemberType(type, boxed, ci.argTypes[argIdx]);
                Class<?> argBoxed = Types.rawBox(argType);
                ValueInfo valueInfo = ci.argValueCodecs[argIdx];
                Object copied = value != null && valueInfo != null
                        ? valueInfo.valueCopy(value)
                        : _deepCopy(value, argType, argBoxed, cps, context);
                session.acceptCtorArg(argIdx, copied);
                continue;
            }

            if (!property.settable) {
                continue;
            }

            Type propertyType = property.genericDependent
                    ? Types.resolveMemberType(type, boxed, property.type)
                    : property.type;
            Class<?> propertyBoxed = Types.rawBox(propertyType);
            Object copied = value != null && property.oneOfInfo == null && property.valueInfo != null
                    ? property.valueInfo.valueCopy(value)
                    : _deepCopy(value, propertyType, propertyBoxed, cps, context);
            session.acceptProperty(property, copied);
        }

        /*
         * JOJO dynamic members.
         *
         * JsonObject itself was handled before reaching this method, so a
         * JsonObject here necessarily represents a JOJO.
         */
        if (source instanceof JsonObject) {
            JsonObject jo = (JsonObject) source;
            for (Map.Entry<String, Object> entry : jo.entrySet()) {
                String key = entry.getKey();
                PathSegment cps = new PathSegment.Name(ps, key);
                session.acceptDynamic(key, _deepCopy(entry.getValue(), Object.class, Object.class, cps, context));
            }
        }

        return session.finish();
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _convertString(String value, Class<?> toBoxed, PathSegment ps) {
        if (toBoxed == String.class) {
            return value;
        }
        if (toBoxed == Character.class) {
            return Nodes.toChar(value);
        }
        if (toBoxed.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) toBoxed, value);
        }
        throw new BindingException(
                "cannot convert String '" + Strings.truncate(value) +
                        "' to '" + toBoxed.getName() + "'", ps);
    }


    // Map / JsonObject / external object -> target Object representation.
    // Accept native entry views directly; do not allocate source adapters.
    private static Object _convertFromObjectEntries(Iterable<Map.Entry<String, Object>> entries,
                                                   int sourceSize, boolean externalSource,
                                                   String sourceName,
                                                   Class<?> toBoxed,
                                                   Type type,
                                                   boolean deepCopy,
                                                   PathSegment ps,
                                                   RuntimeContext context) {
        if (Map.class.isAssignableFrom(toBoxed)) {
            Map<String, Object> map =
                    TypeRegistry.newMapContainer(toBoxed, sourceSize, false);

            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueRaw = Types.rawBox(valueType);
            TypeInfo valueTi = TypeRegistry.registerTypeInfo(valueRaw);

            for (Map.Entry<String, Object> entry : entries) {
                PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                Object value = _convertSourceChild(entry.getValue(),
                        valueType, valueRaw, valueTi, deepCopy, cps, context, externalSource);
                map.put(entry.getKey(), value);
            }
            return map;
        }

        if (toBoxed == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, Object> entry : entries) {
                PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                Object value = _convertSourceChild(entry.getValue(),
                        Object.class, Object.class, null, deepCopy, cps, context, externalSource);
                jo.put(entry.getKey(), value);
            }
            return jo;
        }

        PojoInfo pi = TypeRegistry.registerTypeInfo(toBoxed).pojoInfo;
        if (pi != null && !pi.isJajo) {
            return _convertPojoFromEntries(
                    entries, type, toBoxed, pi, deepCopy, ps, context, externalSource);
        }

        throw new BindingException(
                "cannot convert " + sourceName + " to '" + toBoxed.getName() + "'", ps);
    }

    /**
     * Maps entry-backed objects to POJO/JOJO targets. Source lookup differs from
     * the cached POJO source path, but Creator, property and dynamic writes share
     * exactly the same target conversion routines.
     */
    private static Object _convertPojoFromEntries(Iterable<Map.Entry<String, Object>> entries, Type type, Class<?> toBoxed,
                                                  PojoInfo pi, boolean deepCopy, PathSegment ps,
                                                  RuntimeContext context, boolean externalSource) {
        CreatorInfo ci = pi.creatorInfo;
        Object direct = ci.hasNoArgsCreator() ? ci.newPojoNoArgs() : null;
        CreatorState state = direct == null ? new CreatorState(ci) : null;

        for (Map.Entry<String, Object> entry : entries) {
            String key = entry.getKey();
            Object rawValue = entry.getValue();
            int argIdx = ci.getArgIndexOrAlias(key);
            if (argIdx >= 0) {
                _acceptCreatorArgument(state, ci, argIdx, rawValue, type, toBoxed,
                        deepCopy, new PathSegment.Name(ps, key), context, externalSource);
                continue;
            }

            PropertyInfo propertyInfo = pi.getPropertyNoAlias(key);
            if (propertyInfo != null) {
                if (propertyInfo.settable) {
                    _acceptPojoProperty(direct, state, propertyInfo, rawValue, type, toBoxed,
                            deepCopy, new PathSegment.Name(ps, key), context, externalSource);
                }
                continue;
            }

            if (pi.isJojo && pi.readDynamic) {
                Object value = _convertSourceChild(rawValue, Object.class, Object.class, null,
                        deepCopy, new PathSegment.Name(ps, key), context, externalSource);
                if (direct != null) {
                    ((JsonObject) direct).put(key, value);
                } else {
                    state.acceptDynamic(key, value);
                }
            }
        }

        return direct != null ? direct : state.finish();
    }

    /**
     * Shared Creator argument conversion for entry-backed and cached POJO
     * sources. The original source field name supplies the error path.
     */
    private static void _acceptCreatorArgument(CreatorState state, CreatorInfo creator,
                                               int argIdx, Object rawValue,
                                               Type targetType, Class<?> targetClass,
                                               boolean deepCopy, PathSegment ps,
                                               RuntimeContext context, boolean externalSource) {
        Type argType = Types.resolveMemberType(targetType, targetClass, creator.argTypes[argIdx]);
        Class<?> argRaw = Types.rawBox(argType);
        TypeInfo ti = TypeRegistry.registerTypeInfo(argRaw);
        ValueInfo codec = creator.argValueCodecs[argIdx];
        // Only a codec explicitly selected on the creator parameter is strict.
        // Unannotated value parameters must select by the incoming JSON shape.
        OneOfInfo explicitOneOf = creator.argOneOfInfos[argIdx];
        Object value = explicitOneOf != null
                ? (rawValue == null ? null : _convertOneOf(rawValue, argRaw, explicitOneOf, deepCopy, ps, context))
                : ti.oneOfInfo == null && codec != null
                ? _convertValue(rawValue, argRaw, codec, deepCopy, ps, context)
                : _convertSourceChild(rawValue, argType, argRaw, ti,
                        deepCopy, ps, context, externalSource);
        state.acceptCtorArg(argIdx, value);
    }

    /** Shared field conversion for direct construction and delayed Creators. */
    private static void _acceptPojoProperty(Object direct, CreatorState state, PropertyInfo property,
                                            Object rawValue, Type targetType, Class<?> targetClass,
                                            boolean deepCopy, PathSegment ps,
                                            RuntimeContext context, boolean externalSource) {
        Type fieldType = property.genericDependent
                ? Types.resolveMemberType(targetType, targetClass, property.type)
                : property.type;
        Class<?> fieldRaw = property.genericDependent
                ? Types.rawBox(fieldType)
                : property.boxed;

        Object value;
        if (property.oneOfInfo != null) {
            value = rawValue == null ? null
                    : _convertOneOf(rawValue, fieldRaw, property.oneOfInfo, deepCopy, ps, context);
        } else if (property.valueInfo != null) {
            value = _convertValue(rawValue, fieldRaw, property.valueInfo, deepCopy, ps, context);
        } else {
            value = _convertSourceChild(rawValue, fieldType, fieldRaw, null,
                    deepCopy, ps, context, externalSource);
        }

        if (direct != null) {
            property.invokeSetter(direct, value);
        } else if (state.isCreated()) {
            property.invokeSetter(state.pojo(), value);
        } else {
            state.bufferProperty(property, value);
        }
    }

    // Set -> List/JsonArray/JAJO/Array/Set
    private static Object _convertFromSet(Set<Object> oldSet,
                                          Class<?> toBoxed,
                                          Type type,
                                          boolean deepCopy,
                                          PathSegment ps,
                                          RuntimeContext context) {
        int size = oldSet.size();

        if (List.class.isAssignableFrom(toBoxed)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            TypeInfo valueTi = TypeRegistry.registerTypeInfo(valueRaw);

            List<Object> list =
                    TypeRegistry.newListContainer(toBoxed, size, false);

            int i = 0;
            for (Object value : oldSet) {
                list.add(_convert(value, valueType, valueRaw, valueTi,
                        deepCopy, new PathSegment.Index(ps, i++), context));
            }
            return list;
        }

        if (toBoxed == JsonArray.class) {
            JsonArray ja = new JsonArray();
            int i = 0;
            for (Object value : oldSet) {
                ja.add(_convert(value,
                        Object.class, Object.class, null, deepCopy,
                        new PathSegment.Index(ps, i++), context));
            }
            return ja;
        }

        if (JsonArray.class.isAssignableFrom(toBoxed)) {
            PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(toBoxed);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            Class<?> valueRaw = jajo.elementClass();
            TypeInfo valueTi = TypeRegistry.registerTypeInfo(valueRaw);

            int i = 0;
            for (Object value : oldSet) {
                jajo.add(_convert(value,
                        valueRaw, valueRaw, valueTi, deepCopy,
                        new PathSegment.Index(ps, i++), context));
            }
            return jajo;
        }

        if (toBoxed.isArray()) {
            Class<?> valueType = toBoxed.getComponentType();
            Class<?> valueRaw = Types.rawBox(valueType);
            TypeInfo valueTi = TypeRegistry.registerTypeInfo(valueRaw);

            Object array = Array.newInstance(valueType, size);
            int i = 0;
            for (Object value : oldSet) {
                Array.set(array, i, _convert(value,
                        valueType, valueRaw, valueTi, deepCopy,
                        new PathSegment.Index(ps, i++), context));
            }
            return array;
        }

        if (Set.class.isAssignableFrom(toBoxed)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            TypeInfo valueTi = TypeRegistry.registerTypeInfo(valueRaw);

            Set<Object> set =
                    TypeRegistry.newSetContainer(toBoxed, size, false);

            int i = 0;
            for (Object value : oldSet) {
                set.add(_convert(value, valueType, valueRaw, valueTi,
                        deepCopy, new PathSegment.Index(ps, i++), context));
            }
            return set;
        }

        throw new BindingException(
                "cannot convert Set to '" + toBoxed.getName() + "'", ps);
    }

    private static Object _convertFromIndexedSource(Object source, ExternalNode<Object> external,
                                                    String sourceName,
                                                    Class<?> toBoxed,
                                                    Type type,
                                                    boolean deepCopy,
                                                    PathSegment ps,
                                                    RuntimeContext context) {
        // Snapshot only sequential Lists; indexed reads on them can be quadratic.
        // Keep native external, JsonArray (getNode), array and RandomAccess reads unchanged.
        if (source instanceof List && !(source instanceof RandomAccess)) {
            source = ((List<?>) source).toArray();
        }
        int size = _indexedSize(source, external);
        boolean externalSource = external != null;
        if (List.class.isAssignableFrom(toBoxed)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            TypeInfo valueTi = TypeRegistry.registerTypeInfo(valueRaw);

            List<Object> list =
                    TypeRegistry.newListContainer(toBoxed, size, false);

            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                list.add(_convertSourceChild(_indexedValue(source, external, i),
                        valueType, valueRaw, valueTi, deepCopy, cps, context, externalSource));
            }
            return list;
        }

        if (toBoxed == JsonArray.class) {
            JsonArray ja = new JsonArray();
            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                ja.add(_convertSourceChild(_indexedValue(source, external, i),
                        Object.class, Object.class, null, deepCopy, cps, context, externalSource));
            }
            return ja;
        }

        if (JsonArray.class.isAssignableFrom(toBoxed)) {
            PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(toBoxed);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            Class<?> valueRaw = jajo.elementClass();
            TypeInfo valueTi = TypeRegistry.registerTypeInfo(valueRaw);

            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                jajo.add(_convertSourceChild(_indexedValue(source, external, i),
                        valueRaw, valueRaw, valueTi, deepCopy, cps, context, externalSource));
            }
            return jajo;
        }

        if (toBoxed.isArray()) {
            Class<?> valueType = toBoxed.getComponentType();
            Class<?> valueRaw = Types.rawBox(valueType);
            TypeInfo valueTi = TypeRegistry.registerTypeInfo(valueRaw);

            Object array = Array.newInstance(valueType, size);
            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                Array.set(array, i, _convertSourceChild(_indexedValue(source, external, i),
                        valueType, valueRaw, valueTi, deepCopy, cps, context, externalSource));
            }
            return array;
        }

        if (Set.class.isAssignableFrom(toBoxed)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            TypeInfo valueTi = TypeRegistry.registerTypeInfo(valueRaw);

            Set<Object> set =
                    TypeRegistry.newSetContainer(toBoxed, size, false);

            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                set.add(_convertSourceChild(_indexedValue(source, external, i),
                        valueType, valueRaw, valueTi, deepCopy, cps, context, externalSource));
            }
            return set;
        }

        throw new BindingException(
                "cannot convert " + sourceName + " to '" +
                        toBoxed.getName() + "'", ps);
    }


    private static int _indexedSize(Object source, ExternalNode<Object> external) {
        if (external != null) return external.sizeInArray(source);
        if (source instanceof List) return ((List<?>) source).size();
        if (source instanceof JsonArray) return ((JsonArray) source).size();
        return Array.getLength(source);
    }

    private static Object _indexedValue(Object source, ExternalNode<Object> external, int index) {
        if (external != null) return external.getInArray(source, index);
        if (source instanceof List) return ((List<?>) source).get(index);
        if (source instanceof JsonArray) return ((JsonArray) source).getNode(index);
        if (source instanceof Object[]) return ((Object[]) source)[index];
        if (source instanceof int[]) return ((int[]) source)[index];
        if (source instanceof long[]) return ((long[]) source)[index];
        if (source instanceof byte[]) return ((byte[]) source)[index];
        if (source instanceof short[]) return ((short[]) source)[index];
        if (source instanceof float[]) return ((float[]) source)[index];
        if (source instanceof double[]) return ((double[]) source)[index];
        if (source instanceof boolean[]) return ((boolean[]) source)[index];
        if (source instanceof char[]) return ((char[]) source)[index];
        throw new AssertionError("Unsupported indexed source: " + source.getClass());
    }

    // POJO -> Map/JsonObject/JOJO/POJO
    private static Object _convertFromPojo(Object node,
                                           PojoInfo sourceInfo,
                                           Class<?> toBoxed,
                                           Type type,
                                           boolean deepCopy,
                                           PathSegment ps,
                                           RuntimeContext context) {
        if (Map.class.isAssignableFrom(toBoxed)) {
            Map<String, Object> map = TypeRegistry.newMapContainer(
                    toBoxed, sourceInfo.gettableProperties.length, false);

            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueRaw = Types.rawBox(valueType);
            TypeInfo valueTi = TypeRegistry.registerTypeInfo(valueRaw);
            for (PropertyInfo propertyInfo : sourceInfo.gettableProperties) {
                Object value = propertyInfo.invokeGetter(node);
                PathSegment cps = new PathSegment.Name(ps, propertyInfo.name);
                map.put(propertyInfo.name,
                        _convert(value, valueType, valueRaw, valueTi, deepCopy, cps, context));
            }
            return map;
        }

        if (toBoxed == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (PropertyInfo propertyInfo : sourceInfo.gettableProperties) {
                Object value = propertyInfo.invokeGetter(node);
                PathSegment cps = new PathSegment.Name(ps, propertyInfo.name);
                jo.put(propertyInfo.name,
                        _convert(value, Object.class, Object.class, null, deepCopy, cps, context));
            }
            return jo;
        }

        PojoInfo targetInfo = TypeRegistry.registerTypeInfo(toBoxed).pojoInfo;
        if (targetInfo != null && !targetInfo.isJajo) {
            return _convertPojoFromProperties(node, sourceInfo, type, toBoxed, targetInfo, deepCopy, ps, context);
        }

        throw new BindingException("cannot convert POJO to '" + toBoxed.getName() + "'", ps);
    }

    private static Object _convertPojoFromProperties(Object source,
                                                     PojoInfo sourceInfo,
                                                     Type type,
                                                     Class<?> toBoxed,
                                                     PojoInfo targetInfo,
                                                     boolean deepCopy,
                                                     PathSegment ps,
                                                     RuntimeContext context) {
        CreatorInfo ci = targetInfo.creatorInfo;
        Object direct = ci.hasNoArgsCreator() ? ci.newPojoNoArgs() : null;
        CreatorState state = direct == null ? new CreatorState(ci) : null;
        boolean includeDynamic = targetInfo.isJojo && targetInfo.readDynamic;

        for (PropertyInfo sourceProperty : sourceInfo.gettableProperties) {
            String name = sourceProperty.name;
            int argIdx = ci.getArgIndexOrAlias(name);
            PropertyInfo targetProperty = argIdx < 0 ? targetInfo.getPropertyNoAlias(name) : null;
            if (argIdx < 0 && !(targetProperty != null ? targetProperty.settable : includeDynamic)) continue;

            Object rawValue = sourceProperty.invokeGetter(source);
            PathSegment cps = new PathSegment.Name(ps, name);
            if (argIdx >= 0) {
                _acceptCreatorArgument(state, ci, argIdx, rawValue, type, toBoxed,
                        deepCopy, cps, context, false);
                continue;
            }

            if (targetProperty != null) {
                _acceptPojoProperty(direct, state, targetProperty, rawValue, type, toBoxed,
                        deepCopy, cps, context, false);
                continue;
            }

            Object value = _convert(rawValue, Object.class, Object.class, null, deepCopy, cps, context);
            if (direct != null) {
                ((JsonObject) direct).put(name, value);
            } else {
                state.acceptDynamic(name, value);
            }
        }

        return direct != null ? direct : state.finish();
    }


    private static Object _convertSourceChild(Object value, Type type, Class<?> raw,
                                               TypeInfo ti, boolean deepCopy,
                                               PathSegment ps, RuntimeContext context,
                                               boolean externalSource) {
        if (externalSource && raw == Object.class) {
            return _convertToRaw(value, ps, context);
        }
        return _convert(value, type, raw, ti, deepCopy, ps, context);
    }


    /*
     * --------------------------------------------------------------
     * Raw conversion
     * --------------------------------------------------------------
     */

    @SuppressWarnings("unchecked")
    private static Object _convertToRaw(Object node, PathSegment ps, RuntimeContext context) {
        try {
            if (node == null) return null;

            if (node instanceof String) {
                return node.toString();
            }

            if (node instanceof Number) {
                return node;
            }

            if (node instanceof Boolean) {
                return node;
            }

            if (node instanceof Map) {
                Map<String, Object> oldMap = (Map<String, Object>) node;
                Map<String, Object> newMap = TypeRegistry.newMapContainer(LinkedHashMap.class, oldMap.size(), false);

                for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                    PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                    newMap.put(entry.getKey(), _convertToRaw(entry.getValue(), cps, context));
                }
                return newMap;
            }

            if (node instanceof List) {
                List<Object> oldList = (List<Object>) node;
                List<Object> newList = new ArrayList<>(oldList.size());

                int i = 0;
                for (Object value : oldList) {
                    PathSegment cps = new PathSegment.Index(ps, i++);
                    newList.add(_convertToRaw(value, cps, context));
                }
                return newList;
            }

            Class<?> rawClazz = node.getClass();

            if (node instanceof JsonObject) {
                JsonObject jo = (JsonObject) node;

                if (rawClazz == JsonObject.class) {
                    Map<String, Object> newMap = TypeRegistry.newMapContainer(LinkedHashMap.class, jo.size(), false);

                    for (Map.Entry<String, Object> entry : jo.entrySet()) {
                        PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                        newMap.put(entry.getKey(), _convertToRaw(entry.getValue(), cps, context));
                    }
                    return newMap;
                }

                PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(rawClazz);
                Map<String, Object> dynamic = pi.writeDynamic ? InternalAccess.dynamicMembers(jo) : null;

                int size = pi.gettableProperties.length + (dynamic == null ? 0 : dynamic.size());

                Map<String, Object> newMap = TypeRegistry.newMapContainer(LinkedHashMap.class, size, false);

                for (PropertyInfo propertyInfo : pi.gettableProperties) {
                    Object value = propertyInfo.invokeGetter(node);
                    PathSegment cps = new PathSegment.Name(ps, propertyInfo.name);

                    Object raw = value != null && propertyInfo.valueInfo != null
                            ? propertyInfo.valueInfo.valueToRaw(value)
                            : _convertToRaw(value, cps, context);

                    newMap.put(propertyInfo.name, raw);
                }

                if (dynamic != null) {
                    for (Map.Entry<String, Object> entry : dynamic.entrySet()) {
                        PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                        newMap.put(entry.getKey(), _convertToRaw(entry.getValue(), cps, context));
                    }
                }

                return newMap;
            }

            if (node instanceof JsonArray) {
                JsonArray ja = (JsonArray) node;
                List<Object> newList = new ArrayList<>(ja.size());

                for (int i = 0, len = ja.size(); i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newList.add(_convertToRaw(ja.getNode(i), cps, context));
                }
                return newList;
            }

            if (rawClazz.isArray()) {
                int len = Array.getLength(node);
                List<Object> newList = new ArrayList<>(len);

                for (int i = 0; i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newList.add(_convertToRaw(Array.get(node, i), cps, context));
                }
                return newList;
            }

            if (node instanceof Set) {
                Set<Object> set = (Set<Object>) node;
                List<Object> newList = new ArrayList<>(set.size());

                int i = 0;
                for (Object value : set) {
                    PathSegment cps = new PathSegment.Index(ps, i++);
                    newList.add(_convertToRaw(value, cps, context));
                }
                return newList;
            }

            if (node instanceof Character) {
                return node.toString();
            }

            if (node instanceof Enum) {
                return ((Enum<?>) node).name();
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(rawClazz);
            if (ti.externalNode != null) {
                ExternalNode<Object> external = ti.externalNode;
                switch (external.jsonType(node)) {
                    case NULL:
                        return null;
                    case STRING:
                        return external.toString(node);
                    case NUMBER:
                        return external.toNumber(node);
                    case BOOLEAN:
                        return external.toBoolean(node);
                    case OBJECT: {
                        Map<String, Object> map = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> entry : external.entrySetInObject(node)) {
                            map.put(entry.getKey(), _convertToRaw(entry.getValue(),
                                    new PathSegment.Name(ps, entry.getKey()), context));
                        }
                        return map;
                    }
                    case ARRAY: {
                        int size = external.sizeInArray(node);
                        List<Object> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            list.add(_convertToRaw(external.getInArray(node, i),
                                    new PathSegment.Index(ps, i), context));
                        }
                        return list;
                    }
                    default:
                        throw new BindingException("unsupported external node type '" + Types.name(node) + "'", ps);
                }
            }
            if (ti.valueSet != null) {
                String valueFormat = context.valueFormat(rawClazz);
                ValueInfo valueInfo = ti.requireValueInfo(valueFormat);
                return valueInfo.valueToRaw(node);
            }

            PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                Map<String, Object> newMap = TypeRegistry.newMapContainer(LinkedHashMap.class,
                        pi.gettableProperties.length, false);

                for (PropertyInfo propertyInfo : pi.gettableProperties) {
                    Object value = propertyInfo.invokeGetter(node);
                    PathSegment cps = new PathSegment.Name(ps, propertyInfo.name);

                    Object raw = value != null && propertyInfo.valueInfo != null
                            ? propertyInfo.valueInfo.valueToRaw(value)
                            : _convertToRaw(value, cps, context);

                    newMap.put(propertyInfo.name, raw);
                }

                return newMap;
            }

            throw new BindingException("unsupported node type '" + Types.name(node) + "'", ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("cannot convert node from '" + Types.name(node) +
                    "' to raw (Map/List/String/Number/Boolean/null)", ps, e);
        }
    }

}
