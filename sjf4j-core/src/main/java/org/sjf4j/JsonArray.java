package org.sjf4j;

import org.sjf4j.exception.JsonException;

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

/**
 * Mutable array node in SJF4J's OBNT model.
 *
 * <p>{@link JsonArray} can be subclassed to define a JAJO, a Java representation
 * category that participates as an array node.
 *
 * <p>Element access and conversion are delegated to {@link Nodes}.
 */
public class JsonArray extends JsonContainer {

    /**
     * Stores array node elements.
     */
    protected transient List<Object> dynamicList;

    /**
     * Creates an empty array node.
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
            for (Object value : values) {
                ja.add(value);
            }
        }
        return ja;
    }

    /**
     * Creates an array node backed directly by {@code list}.
     * <p>
     * The provided list becomes this array's storage directly, so element reads
     * and writes are shared with the same list instance.
     */
    @SuppressWarnings("unchecked")
    public JsonArray(List<?> list) {
        this();
        if (list != null) {
            Class<?> elemClazz = elementClass();
            if (elemClazz != Object.class) {
                for (int i = 0, len = list.size(); i < len; i++) {
                    Object v = list.get(i);
                    if (v != null && !elemClazz.isInstance(v))
                        throw new JsonException("element type mismatch at [" + i + "]: expected " +
                                elemClazz.getName() + ", but was " + v.getClass().getName());
                }
            }
        }
        this.dynamicList = (List<Object>) list;
    }


    /*
     * --------------------------------------------------------------
     * Object
     * --------------------------------------------------------------
     */

    /**
     * Returns the expected element class for this array.
     */
    public Class<?> elementClass() {
        return Object.class;
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
     * Returns a shallow element snapshot; element references are preserved.
     * When no backing list exists, the empty result may be unmodifiable.
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


    /*
     * --------------------------------------------------------------
     * JSON Facade
     * --------------------------------------------------------------
     */

    /**
     * Parses a JSON string into a JsonArray.
     */
    public static JsonArray fromJson(String input) {
        return Sjf4j.global().fromJson(input, JsonArray.class);
    }

    /*
     * --------------------------------------------------------------
     * YAML Facade
     * --------------------------------------------------------------
     */

    /**
     * Parses a YAML string into a JsonArray.
     */
    public static JsonArray fromYaml(String input) {
        return Sjf4j.global().fromYaml(input, JsonArray.class);
    }

    /*
     * --------------------------------------------------------------
     * Node Facade
     * --------------------------------------------------------------
     */

    /**
     * Converts an OBNT value to a JsonArray through {@link Sjf4j#fromNode(Object, Class)}.
     * The configured node facade defines conversion and copy boundaries; returned
     * values may retain references.
     */
    public static JsonArray fromNode(Object node) {
        return Sjf4j.global().fromNode(node, JsonArray.class);
    }


    /*
     * --------------------------------------------------------------
     * Getter
     * --------------------------------------------------------------
     */
    /**
     * Strict getter helper. When {@code containerType} is non-null the message
     * includes the container name; for non-container value types pass {@code null}.
     */
    private JsonException _strict(int idx, Class<?> elementType, Class<?> containerType, Exception cause) {
        String msg = containerType == null ? "cannot get " + elementType.getSimpleName() + " at [" + idx + "]" : "cannot get " + containerType.getSimpleName() + " with element type " + elementType.getSimpleName() + " at [" + idx + "]";
        return new JsonException(msg, cause);
    }

    /**
     * Lenient getter helper for value types only.
     */
    private JsonException _lenient(int idx, Class<?> type, Exception cause) {
        return new JsonException("cannot coerce to " + type.getSimpleName() + " at [" + idx + "]", cause);
    }


    /**
     * Returns the OBNT value at the given index or {@code null} when out of range.
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
     * Returns the OBNT value at the given index or the default value.
     */
    public Object getNode(int idx, Object defaultValue) {
        Object value = getNode(idx);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a String value using strict conversion.
     */
    public String getString(int idx) {
        try {
            return Nodes.toString(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, String.class, null, e);
        }
    }
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
            throw _lenient(idx, String.class, e);
        }
    }

    /**
     * Returns a Number value using strict conversion.
     */
    public Number getNumber(int idx) {
        try {
            return Nodes.toNumber(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, Number.class, null, e);
        }
    }

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
    public Number getAsNumber(int idx) {
        try {
            return Nodes.asNumber(getNode(idx));
        } catch (Exception e) {
            throw _lenient(idx, Number.class, e);
        }
    }

    /**
     * Returns a Long value using strict conversion.
     */
    public Long getLong(int idx) {
        try {
            return Nodes.toLong(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, Long.class, null, e);
        }
    }

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
    public Long getAsLong(int idx) {
        try {
            return Nodes.asLong(getNode(idx));
        } catch (Exception e) {
            throw _lenient(idx, Long.class, e);
        }
    }

    /**
     * Returns an Integer value using strict conversion.
     */
    public Integer getInt(int idx) {
        try {
            return Nodes.toInt(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, Integer.class, null, e);
        }
    }

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
    public Integer getAsInt(int idx) {
        try {
            return Nodes.asInt(getNode(idx));
        } catch (Exception e) {
            throw _lenient(idx, Integer.class, e);
        }
    }

    /**
     * Returns a Short value using strict conversion.
     */
    public Short getShort(int idx) {
        try {
            return Nodes.toShort(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, Short.class, null, e);
        }
    }

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
    public Short getAsShort(int idx) {
        try {
            return Nodes.asShort(getNode(idx));
        } catch (Exception e) {
            throw _lenient(idx, Short.class, e);
        }
    }

    /**
     * Returns a Byte value using strict conversion.
     */
    public Byte getByte(int idx) {
        try {
            return Nodes.toByte(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, Byte.class, null, e);
        }
    }

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
    public Byte getAsByte(int idx) {
        try {
            return Nodes.asByte(getNode(idx));
        } catch (Exception e) {
            throw _lenient(idx, Byte.class, e);
        }
    }

    /**
     * Returns a Double value using strict conversion.
     */
    public Double getDouble(int idx) {
        try {
            return Nodes.toDouble(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, Double.class, null, e);
        }
    }

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
    public Double getAsDouble(int idx) {
        try {
            return Nodes.asDouble(getNode(idx));
        } catch (Exception e) {
            throw _lenient(idx, Double.class, e);
        }
    }

    /**
     * Returns a Float value using strict conversion.
     */
    public Float getFloat(int idx) {
        try {
            return Nodes.toFloat(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, Float.class, null, e);
        }
    }

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
    public Float getAsFloat(int idx) {
        try {
            return Nodes.asFloat(getNode(idx));
        } catch (Exception e) {
            throw _lenient(idx, Float.class, e);
        }
    }

    /**
     * Returns a BigInteger value using strict conversion.
     */
    public BigInteger getBigInteger(int idx) {
        try {
            return Nodes.toBigInteger(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, BigInteger.class, null, e);
        }
    }

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
    public BigInteger getAsBigInteger(int idx) {
        try {
            return Nodes.asBigInteger(getNode(idx));
        } catch (Exception e) {
            throw _lenient(idx, BigInteger.class, e);
        }
    }

    /**
     * Returns a BigDecimal value using strict conversion.
     */
    public BigDecimal getBigDecimal(int idx) {
        try {
            return Nodes.toBigDecimal(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, BigDecimal.class, null, e);
        }
    }

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
    public BigDecimal getAsBigDecimal(int idx) {
        try {
            return Nodes.asBigDecimal(getNode(idx));
        } catch (Exception e) {
            throw _lenient(idx, BigDecimal.class, e);
        }
    }

    /**
     * Returns a Boolean value using strict conversion.
     */
    public Boolean getBoolean(int idx) {
        try {
            return Nodes.toBoolean(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, Boolean.class, null, e);
        }
    }

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
    public Boolean getAsBoolean(int idx) {
        try {
            return Nodes.asBoolean(getNode(idx));
        } catch (Exception e) {
            throw _lenient(idx, Boolean.class, e);
        }
    }

    /**
     * Returns a JsonObject value using strict conversion.
     */
    public JsonObject getJsonObject(int idx) {
        try {
            return Nodes.toJsonObject(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, JsonObject.class, null, e);
        }
    }

    /**
     * Returns a Map value using strict conversion.
     */
    public Map<String, Object> getMap(int idx) {
        try {
            return Nodes.toMap(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, Map.class, null, e);
        }
    }

    /**
     * Returns a typed Map value using strict conversion.
     */
    public <T> Map<String, T> getMap(int idx, Class<T> clazz) {
        try {
            return Nodes.toMap(getNode(idx), clazz);
        } catch (Exception e) {
            throw _strict(idx, clazz, Map.class, e);
        }
    }

    /**
     * Returns a JsonArray value using strict conversion.
     */
    public JsonArray getJsonArray(int idx) {
        try {
            return Nodes.toJsonArray(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, JsonArray.class, null, e);
        }
    }

    /**
     * Returns a List value using strict conversion.
     */
    public List<Object> getList(int idx) {
        try {
            return Nodes.toList(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, List.class, null, e);
        }
    }

    /**
     * Returns a typed List value using strict conversion.
     */
    public <T> List<T> getList(int idx, Class<T> clazz) {
        try {
            return Nodes.toList(getNode(idx), clazz);
        } catch (Exception e) {
            throw _strict(idx, clazz, List.class, e);
        }
    }

    /**
     * Returns an Object array using strict conversion.
     */
    public Object[] getArray(int idx) {
        try {
            return Nodes.toArray(getNode(idx));
        } catch (Exception e) {
            throw _strict(idx, Object[].class, null, e);
        }
    }

    /**
     * Returns a typed array using strict conversion.
     */
    public <T> T[] getArray(int idx, Class<T> clazz) {
        try {
            return Nodes.toArray(getNode(idx), clazz);
        } catch (Exception e) {
            throw _strict(idx, clazz, Object[].class, e);
        }
    }

    /**
     * Returns a value converted to the given type.
     */
    public <T> T get(int idx, Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        try {
            return Nodes.to(getNode(idx), clazz);
        } catch (Exception e) {
            throw _strict(idx, clazz, null, e);
        }
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
        try {
            return Nodes.as(getNode(idx), clazz);
        } catch (Exception e) {
            throw _lenient(idx, clazz, e);
        }
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

    /*
     * --------------------------------------------------------------
     * Adder
     * --------------------------------------------------------------
     */

    /**
     * Appends an element to the array.
     */
    public void add(Object object) {
        if (object != null && !elementClass().isInstance(object))
            throw new JsonException("cannot add element of type '" + object.getClass().getName() +
                    " to JsonArray with elementType '" + elementClass().getName() + "'");

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
     * array node representation.
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
        if (object != null && !elementClass().isInstance(object))
            throw new JsonException("cannot add element of type '" + object.getClass().getName() +
                    " to JsonArray with elementType '" + elementClass().getName() + "'");

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
        if (object != null && !elementClass().isInstance(object))
            throw new JsonException("cannot set element of type '" + object.getClass().getName() +
                    " in JsonArray with elementType '" + elementClass().getName() + "'");

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
     * Copies all elements from the given array node representation.
     * <p>
     * Supported inputs follow {@link Nodes#forEachArray(Object, BiConsumer)}:
     * {@link List}, {@link JsonArray}, Java arrays, {@link Set}, and facade-native
     * Java representations that participate as array nodes.
     * Values are appended through {@link #add(Object)} without deep recursion, so
     * nested child values may still be shared with the source.
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

    /*
     * --------------------------------------------------------------
     * Stream
     * --------------------------------------------------------------
     */

    /**
     * Returns a stream wrapper for this array.
     */
    public NodeStream<JsonArray> stream() {
        return NodeStream.of(this);
    }

    /*
     * --------------------------------------------------------------
     * Copy
     * --------------------------------------------------------------
     */

    /**
     * Creates a shallow copy of this JsonArray.
     * <p>
     * Plain {@link JsonArray} instances copy their list entries; nested values are
     * shared. JAJO subtypes fall back to {@link Nodes#copy(Object)} so subtype
     * element rules and construction semantics remain intact.
     */
    public JsonArray copy() {
        if (getClass() == JsonArray.class) {
            return dynamicList == null ? new JsonArray() : new JsonArray(new ArrayList<>(dynamicList));
        }
        return Nodes.copy(this);
    }

    /**
     * Creates a deep copy of this JsonArray.
     * <p>
     * Delegates to {@link Sjf4j#deepNode(Object)}; array elements are traversed
     * according to the configured node facade.
     */
    public JsonArray deepCopy() {
        return Sjf4j.global().deepNode(this);
    }

}
