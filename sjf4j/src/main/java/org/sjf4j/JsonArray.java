package org.sjf4j;

import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeStream;
import org.sjf4j.node.Nodes;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * JSON array container in SJF4J's OBNT model.
 *
 * <p>{@link JsonArray} can be used directly as a mutable JSON array node, or
 * subclassed to define a JAJO. A JAJO is the array-side counterpart to JOJO:
 * it keeps JSON-array behavior while giving the subtype a dedicated Java type.
 *
 * <p>Element access and conversion are delegated to {@link Nodes} to keep
 * behavior consistent across JSON container types.
 */
public class JsonArray extends JsonContainer {

    /**
     * Stores JSON elements as a list.
     */
    protected transient List<Object> dynamicList;

    /**
     * Creates an empty JsonArray instance.
     */
    public JsonArray() {
        super();
    }

    /**
     * Creates JsonArray from literal element values.
     */
    public static JsonArray of(Object... values) {
        JsonArray ja = new JsonArray();
        if (values != null) {
            List<Object> list = new ArrayList<>(values.length);
            Collections.addAll(list, values);
            ja._dynamicList(list);
        }
        return ja;
    }

    /**
     * Creates a JsonArray by wrapping a backing list.
     * <p>
     * The provided list becomes this array's storage directly, so element reads
     * and writes are shared with the same list instance.
     */
    @SuppressWarnings("unchecked")
    public JsonArray(List<?> list) {
        this();
        _dynamicList((List<Object>) list);
    }


    /// Object

    /**
     * Returns the expected element type for this array.
     */
    public Class<?> elementType() {
        return Object.class;
    }

    /**
     * Replaces internal list storage with optional runtime element type check.
     */
    protected void _dynamicList(List<Object> list) {
        if (list != null) {
            Class<?> elemClazz = elementType();
            if (elemClazz != Object.class) {
                for (int i = 0; i < list.size(); i++) {
                    Object v = list.get(i);
                    if (v != null && !elemClazz.isInstance(v))
                        throw new JsonException("element type mismatch at [" + i + "]: expected " + elemClazz.getName() +
                                ", but was " + v.getClass().getName());
                }
            }
        }
        this.dynamicList = list;
    }

    /**
     * Returns the hash code value for this JsonArray.
     */
    @Override
    public int hashCode() {
        return dynamicList == null || dynamicList.isEmpty() ? 0 : dynamicList.hashCode();
    }

    /**
     * Compares arrays by runtime type and element sequence.
     */
    @Override
    public boolean equals(Object target) {
//        return Nodes.equals(this, target);
        if (target == this) return true;
        if (target == null || target.getClass() != this.getClass()) return false;
        JsonArray targetJa = (JsonArray) target;
        int size = this.size();
        if (size != targetJa.size()) return false;
        if (size == 0) {
            return true;
        } else {
            return Objects.equals(this.dynamicList, targetJa.dynamicList);
        }
    }

    /**
     * Returns the number of elements in this JsonArray.
     */
    public int size() {
        return dynamicList == null ? 0 : dynamicList.size();
    }

    /**
     * Returns true if this array has no elements.
     */
    public boolean isEmpty() {
        return dynamicList == null || dynamicList.isEmpty();
    }

    /**
     * Returns the elements as a List.
     */
    public List<Object> toList() {
        return dynamicList == null ? Collections.emptyList() : new ArrayList<>(dynamicList);
    }

    /**
     * Returns a typed List of elements.
     */
    public <T> List<T> toList(Class<T> clazz) {
        return Nodes.toList(this, clazz);
    }

    /**
     * Returns the elements as an Object array.
     */
    public Object[] toArray() {
        return dynamicList == null ? new Object[0] : dynamicList.toArray();
    }

    /**
     * Returns the elements as a typed array.
     */
    public <T> T[] toArray(Class<T> clazz) {
        return Nodes.toArray(this, clazz);
    }

    /**
     * Returns the elements as a Set.
     */
    public Set<Object> toSet() {
        return dynamicList == null ? Collections.emptySet() : new LinkedHashSet<>(dynamicList);
    }

    /**
     * Returns the elements as a typed Set.
     */
    public <T> Set<T> toSet(Class<T> clazz) {
        return Nodes.toSet(this, clazz);
    }

    /**
     * Performs the action for each element.
     */
    public void forEach(Consumer<Object> action) {
        if (dynamicList == null) return;
        for (Object object : dynamicList) {
            action.accept(object);
        }
    }

    /**
     * Performs the action for each index/value pair.
     */
    public void forEach(BiConsumer<Integer, Object> action) {
        if (dynamicList == null) return;
        for (int i = 0; i < dynamicList.size(); i++) {
            action.accept(i, dynamicList.get(i));
        }
    }

    public boolean anyMatch(BiPredicate<Integer, Object> predicate) {
        if (dynamicList == null) return false;
        for (int i = 0; i < dynamicList.size(); i++) {
            if (predicate.test(i, dynamicList.get(i))) return true;
        }
        return false;
    }

    /**
     * Returns an iterator over elements.
     */
    public Iterator<Object> iterator() {
        if (dynamicList == null) return Collections.emptyIterator();
        return dynamicList.iterator();
    }

    /**
     * Normalizes negative indices against array size.
     */
    private int _pos(int idx) {
        return idx < 0 ? size() + idx : idx;
    }

    /**
     * Returns true if the index is within bounds.
     * <p>
     * Negative indexes are normalized from array tail.
     */
    public boolean containsIndex(int idx) {
        idx = _pos(idx);
        return idx >= 0 && idx < size();
    }

    /**
     * Returns true if the index exists and the value is non-null.
     */
    public boolean hasNonNull(int idx) {
        return getNode(idx) != null;
    }

    /**
     * Returns true if the array contains the given value.
     */
    public boolean containsValue(Object value) {
        if (dynamicList == null) return false;
        for (Object o : dynamicList) {
            if (Objects.equals(o, value)) {
                return true;
            }
        }
        return false;
    }


    /// JSON Facade

    /**
     * Parses a JSON string into a JsonArray.
     */
    public static JsonArray fromJson(String input) {
        return Sjf4j.global().fromJson(input, JsonArray.class);
    }

    /// YAML Facade

    /**
     * Parses a YAML string into a JsonArray.
     */
    public static JsonArray fromYaml(String input) {
        return Sjf4j.global().fromYaml(input, JsonArray.class);
    }

    /// Node Facade

    /**
     * Converts a node into a detached JsonArray.
     * <p>
     * This routes through {@link Sjf4j#fromNode(Object, Class)} and does not
     * preserve source-container aliasing. Use {@link Nodes#toJsonArray(Object)}
     * when you want target-representation conversion that may reuse an existing
     * {@link JsonArray} or wrap a backing {@link List}.
     */
    public static JsonArray fromNode(Object node) {
        return Sjf4j.global().fromNode(node, JsonArray.class);
    }


    /// Getter
    /**
     * Strict getter helper. When {@code containerType} is non-null the message
     * includes the container name; for plain scalar types pass {@code null}.
     */
    private <T> T _getStrict(int idx, Function<Object, T> fn, Class<?> elementType, Class<?> containerType) {
        try {
            return fn.apply(getNode(idx));
        } catch (Exception e) {
            String msg = containerType == null
                ? "cannot get " + elementType.getSimpleName() + " at [" + idx + "]"
                : "cannot get " + containerType.getSimpleName() + " with element type " + elementType.getSimpleName() +
                  " at [" + idx + "]";
            throw new JsonException(msg, e);
        }
    }

    /**
     * Lenient getter helper for scalar types only.
     */
    private <T> T _getLenient(int idx, Function<Object, T> fn, Class<?> type) {
        try {
            return fn.apply(getNode(idx));
        } catch (Exception e) {
            throw new JsonException("cannot coerce to " + type.getSimpleName() + " at [" + idx + "]", e);
        }
    }


    /**
     * Returns the node at the given index or {@code null} when out of range.
     * <p>
     * Supports negative indexes ({@code -1} means last element).
     */
    public Object getNode(int idx) {
        int pidx = _pos(idx);
        if (pidx >= 0 && pidx < size()) {
            return dynamicList.get(pidx);
        } else {
            return null;
        }
    }
    /**
     * Returns the node at the given index or the default value.
     */
    public Object getNode(int idx, Object defaultValue) {
        Object value = getNode(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a String value using strict conversion.
     */
    public String getString(int idx) { return _getStrict(idx, Nodes::toString, String.class, null); }
    /**
     * Returns a String value or the default value when missing.
     */
    public String getString(int idx, String defaultValue) {
        String value = getString(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a String value using lenient conversion.
     */
    public String getAsString(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asString(value);
        } catch (Exception e) {
            throw new JsonException("cannot coerce to String at [" + idx + "]", e);
        }
    }

    /**
     * Returns a Number value using strict conversion.
     */
    public Number getNumber(int idx) { return _getStrict(idx, Nodes::toNumber, Number.class, null); }

    /**
     * Returns a Number value or the default value when missing.
     */
    public Number getNumber(int idx, Number defaultValue) {
        Number value = getNumber(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Number value using lenient conversion.
     */
    public Number getAsNumber(int idx) { return _getLenient(idx, Nodes::asNumber, Number.class); }

    /**
     * Returns a Long value using strict conversion.
     */
    public Long getLong(int idx) { return _getStrict(idx, Nodes::toLong, Long.class, null); }

    /**
     * Returns a Long value or the default value when missing.
     */
    public long getLong(int idx, long defaultValue) {
        Long value = getLong(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Long value using lenient conversion.
     */
    public Long getAsLong(int idx) { return _getLenient(idx, Nodes::asLong, Long.class); }

    /**
     * Returns an Integer value using strict conversion.
     */
    public Integer getInt(int idx) { return _getStrict(idx, Nodes::toInt, Integer.class, null); }

    /**
     * Returns an Integer value or the default value when missing.
     */
    public int getInt(int idx, int defaultValue) {
        Integer value = getInt(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns an Integer value using lenient conversion.
     */
    public Integer getAsInt(int idx) { return _getLenient(idx, Nodes::asInt, Integer.class); }

    /**
     * Returns a Short value using strict conversion.
     */
    public Short getShort(int idx) { return _getStrict(idx, Nodes::toShort, Short.class, null); }

    /**
     * Returns a Short value or the default value when missing.
     */
    public short getShort(int idx, short defaultValue) {
        Short value = getShort(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Short value using lenient conversion.
     */
    public Short getAsShort(int idx) { return _getLenient(idx, Nodes::asShort, Short.class); }

    /**
     * Returns a Byte value using strict conversion.
     */
    public Byte getByte(int idx) { return _getStrict(idx, Nodes::toByte, Byte.class, null); }

    /**
     * Returns a Byte value or the default value when missing.
     */
    public byte getByte(int idx, byte defaultValue) {
        Byte value = getByte(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Byte value using lenient conversion.
     */
    public Byte getAsByte(int idx) { return _getLenient(idx, Nodes::asByte, Byte.class); }

    /**
     * Returns a Double value using strict conversion.
     */
    public Double getDouble(int idx) { return _getStrict(idx, Nodes::toDouble, Double.class, null); }

    /**
     * Returns a Double value or the default value when missing.
     */
    public double getDouble(int idx, double defaultValue) {
        Double value = getDouble(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Double value using lenient conversion.
     */
    public Double getAsDouble(int idx) { return _getLenient(idx, Nodes::asDouble, Double.class); }

    /**
     * Returns a Float value using strict conversion.
     */
    public Float getFloat(int idx) { return _getStrict(idx, Nodes::toFloat, Float.class, null); }

    /**
     * Returns a Float value or the default value when missing.
     */
    public float getFloat(int idx, float defaultValue) {
        Float value = getFloat(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Float value using lenient conversion.
     */
    public Float getAsFloat(int idx) { return _getLenient(idx, Nodes::asFloat, Float.class); }

    /**
     * Returns a BigInteger value using strict conversion.
     */
    public BigInteger getBigInteger(int idx) { return _getStrict(idx, Nodes::toBigInteger, BigInteger.class, null); }

    /**
     * Returns a BigInteger value or the default value when missing.
     */
    public BigInteger getBigInteger(int idx, BigInteger defaultValue) {
        BigInteger value = getBigInteger(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a BigInteger value using lenient conversion.
     */
    public BigInteger getAsBigInteger(int idx) { return _getLenient(idx, Nodes::asBigInteger, BigInteger.class); }

    /**
     * Returns a BigDecimal value using strict conversion.
     */
    public BigDecimal getBigDecimal(int idx) { return _getStrict(idx, Nodes::toBigDecimal, BigDecimal.class, null); }

    /**
     * Returns a BigDecimal value or the default value when missing.
     */
    public BigDecimal getBigDecimal(int idx, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a BigDecimal value using lenient conversion.
     */
    public BigDecimal getAsBigDecimal(int idx) { return _getLenient(idx, Nodes::asBigDecimal, BigDecimal.class); }

    /**
     * Returns a Boolean value using strict conversion.
     */
    public Boolean getBoolean(int idx) { return _getStrict(idx, Nodes::toBoolean, Boolean.class, null); }

    /**
     * Returns a Boolean value or the default value when missing.
     */
    public boolean getBoolean(int idx, boolean defaultValue) {
        Boolean value = getBoolean(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Boolean value using lenient conversion.
     */
    public Boolean getAsBoolean(int idx) { return _getLenient(idx, Nodes::asBoolean, Boolean.class); }

    /**
     * Returns a JsonObject value using strict conversion.
     */
    public JsonObject getJsonObject(int idx) { return _getStrict(idx, Nodes::toJsonObject, JsonObject.class, null); }

    /**
     * Returns a Map value using strict conversion.
     */
    public Map<String, Object> getMap(int idx) { return _getStrict(idx, Nodes::toMap, Map.class, null); }

    /**
     * Returns a typed Map value using strict conversion.
     */
    public <T> Map<String, T> getMap(int idx, Class<T> clazz) { return _getStrict(idx, v -> Nodes.toMap(v, clazz), clazz, Map.class); }

    /**
     * Returns a JsonArray value using strict conversion.
     */
    public JsonArray getJsonArray(int idx) { return _getStrict(idx, Nodes::toJsonArray, JsonArray.class, null); }

    /**
     * Returns a List value using strict conversion.
     */
    public List<Object> getList(int idx) { return _getStrict(idx, Nodes::toList, List.class, null); }

    /**
     * Returns a typed List value using strict conversion.
     */
    public <T> List<T> getList(int idx, Class<T> clazz) { return _getStrict(idx, v -> Nodes.toList(v, clazz), clazz, List.class); }

    /**
     * Returns an Object array using strict conversion.
     */
    public Object[] getArray(int idx) { return _getStrict(idx, Nodes::toArray, Object[].class, null); }

    /**
     * Returns a typed array using strict conversion.
     */
    public <T> T[] getArray(int idx, Class<T> clazz) { return _getStrict(idx, v -> Nodes.toArray(v, clazz), clazz, Object[].class); }

    /**
     * Returns a value converted to the given type.
     */
    public <T> T get(int idx, Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        return _getStrict(idx, v -> Nodes.to(v, clazz), clazz, null);
    }

    /**
     * Returns a value converted to the inferred type.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(int idx, T... reified) {
        if (reified.length > 0) throw new JsonException("reified varargs must be empty");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(idx, clazz);
    }

    /**
     * Returns a value using lenient conversion.
     */
    public <T> T getAs(int idx, Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        return _getLenient(idx, v -> Nodes.as(v, clazz), clazz);
    }

    /**
     * Returns a value using lenient conversion with inferred type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAs(int idx, T... reified) {
        if (reified.length > 0) throw new JsonException("reified varargs must be empty");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return getAs(idx, clazz);
    }

    /// Adder

    /**
     * Appends an element to the array.
     */
    public void add(Object object) {
        if (object != null && !elementType().isInstance(object))
            throw new JsonException("cannot add element of type '" + object.getClass().getName() +
                    " to JsonArray with elementType '" + elementType().getName() + "'");

        if (dynamicList == null) dynamicList = new ArrayList<>();
        dynamicList.add(object);
    }

    /**
     * Appends an element only when non-null.
     */
    public void addNonNull(Object object) {
        if (object != null) add(object);
    }

    /**
     * Appends literal values in order.
     * <p>
     * Each argument becomes one array element. This method does not flatten
     * nested arrays or collections; use {@link #addAll(Object)} to copy from an
     * array-like source.
     */
    public void append(Object... values) {
        if (values == null) return;
        for (Object value : values) add(value);
    }

    /**
     * Inserts an element at the given index.
     * <p>
     * Supports negative indexes after normalization. Valid insertion range is
     * {@code [0, size]}.
     */
    public void add(int idx, Object object) {
        if (object != null && !elementType().isInstance(object))
            throw new JsonException("cannot add element of type '" + object.getClass().getName() +
                    " to JsonArray with elementType '" + elementType().getName() + "'");

        int pidx = _pos(idx);
        if (pidx < 0 || pidx > size()) {
            throw new JsonException("cannot add at index " + idx + " in JsonArray of size " + size());
        }

        if (dynamicList == null) dynamicList = new ArrayList<>();
        dynamicList.add(pidx, object);
    }

    /**
     * Replaces the element at the given index.
     * <p>
     * Supports negative indexes after normalization. Valid replacement range is
     * {@code [0, size-1]}.
     */
    public Object set(int idx, Object object) {
        if (object != null && !elementType().isInstance(object))
            throw new JsonException("cannot set element of type '" + object.getClass().getName() +
                    " in JsonArray with elementType '" + elementType().getName() + "'");

        int pidx = _pos(idx);
        if (pidx < 0 || pidx >= size()) {
            throw new JsonException("cannot set at index " + idx + " in JsonArray of size " + size());
        }
        if (dynamicList == null) dynamicList = new ArrayList<>();
        return dynamicList.set(pidx, object);
    }

    /**
     * Sets a value only when the current value is null.
     */
    public Object setIfAbsent(int idx, Object object) {
        Object old = getNode(idx);
        if (old == null) {
            return set(idx, object);
        }
        return old;
    }

    /**
     * Copies all elements from the given array-like node.
     * <p>
     * Supported inputs follow {@link Nodes#forEachArray(Object, BiConsumer)}:
     * {@link List}, {@link JsonArray}, Java arrays, {@link Set}, and facade
     * array nodes. Values are appended through {@link #add(Object)} without deep
     * recursion, so nested child nodes may still be shared with the source.
     */
    public void addAll(Object node) {
        if (node == null) return;
        Nodes.forEachArray(node, (i, value) -> add(value));
    }


    /**
     * Removes the element at the given index.
     * <p>
     * Supports negative indexes after normalization.
     */
    public Object remove(int idx) {
        if (dynamicList == null) return null;
        int pidx = _pos(idx);
        if (pidx < 0 || pidx >= size()) {
            throw new JsonException("cannot remove at index " + idx + " in JsonArray of size " + size());
        }
        return dynamicList.remove(pidx);
    }

    /**
     * Removes all elements from the array.
     */
    public void clear() {
        if (dynamicList == null) return;
        dynamicList.clear();
    }

    /// Stream

    /**
     * Returns a stream wrapper for this array.
     */
    public NodeStream<JsonArray> stream() {
        return NodeStream.of(this);
    }

    /// Copy

    /**
     * Creates a shallow copy of this JsonArray.
     * <p>
     * Plain {@link JsonArray} instances copy their backing list directly. JAJO
     * subtypes fall back to {@link Nodes#copy(Object)} so subtype element rules
     * and construction semantics remain intact.
     */
    public JsonArray copy() {
        if (getClass() == JsonArray.class) {
            return dynamicList == null ? new JsonArray() : new JsonArray(new ArrayList<>(dynamicList));
        }
        return Nodes.copy(this);
    }

    /**
     * Creates a deep copy of this JsonArray.
     */
    public JsonArray deepCopy() {
        return Sjf4j.global().deepNode(this);
    }

}
