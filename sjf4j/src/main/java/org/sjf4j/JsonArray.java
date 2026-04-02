package org.sjf4j;

import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.FacadeNodes;
import org.sjf4j.node.NodeStream;
import org.sjf4j.node.Nodes;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

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
    protected transient List<Object> nodeList;

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
        ja.setNodeList(values == null ? null : Sjf4jConfig.global().listSupplier.create(values));
        return ja;
    }

    /**
     * Creates a JsonArray by wrapping or converting an array-like node.
     * <p>
     * When the source already uses a compatible list backing, this constructor
     * shares that backing storage. Otherwise, it converts the source into a new
     * list. In practice:
     * <ul>
     *     <li>{@link List} and {@link JsonArray} share when possible</li>
     *     <li>Java arrays and {@link Set} inputs are copied</li>
     *     <li>facade array nodes are copied by exposed entries</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public JsonArray(Object node) {
        this();
        if (node == null) return;
        if (node instanceof List) {
            setNodeList((List<Object>) node);
            return;
        }
        if (node instanceof JsonArray) {
            setNodeList(((JsonArray) node).nodeList);
            return;
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            List<Object> list = Sjf4jConfig.global().listSupplier.create(len);
            for (int i = 0; i < len; i++) list.add(Array.get(node, i));
            setNodeList(list);
            return;
        }
        if (node instanceof Set) {
            setNodeList(Sjf4jConfig.global().listSupplier.create((Set<Object>) node));
            return;
        }
        if (FacadeNodes.isNode(node)) {
            setNodeList(FacadeNodes.toList(node));
            return;
        }
        throw new JsonException("Cannot wrap value of type '" + node.getClass().getName() +
                "' into JsonArray. Supported types are: List, JsonArray, Array, Set, or facade array node.");
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
    protected void setNodeList(List<Object> nodeList) {
        if (nodeList != null) {
            Class<?> elemClazz = elementType();
            if (elemClazz != Object.class) {
                for (int i = 0; i < nodeList.size(); i++) {
                    Object v = nodeList.get(i);
                    if (v != null && !elemClazz.isInstance(v))
                        throw new JsonException("Element type mismatch at " + i + ": expected " + elemClazz.getName() +
                                ", but was " + v.getClass().getName());
                }
            }
        }
        this.nodeList = nodeList;
    }

    /**
     * Returns the JSON-like string representation of this JsonArray.
     */
    @Override
    public String toString() {
        return inspect();
    }

    /**
     * Returns the hash code value for this JsonArray.
     */
    @Override
    public int hashCode() {
        return nodeList == null || nodeList.isEmpty() ? 0 : nodeList.hashCode();
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
            return Objects.equals(this.nodeList, targetJa.nodeList);
        }
    }

    /**
     * Returns the number of elements in this JsonArray.
     */
    public int size() {
        return nodeList == null ? 0 : nodeList.size();
    }

    /**
     * Returns true if this array has no elements.
     */
    public boolean isEmpty() {
        return nodeList == null || nodeList.isEmpty();
    }

    /**
     * Returns the elements as a List.
     */
    public List<Object> toList() {
        return nodeList == null ? Collections.emptyList() : Sjf4jConfig.global().listSupplier.create(nodeList);
    }

    /**
     * Returns a typed List of elements.
     */
    public <T> List<T> toList(Class<T> clazz) {
        return Nodes.toList(toList(), clazz);
    }

    /**
     * Returns the elements as an Object array.
     */
    public Object[] toArray() {
        return nodeList == null ? new Object[0] : nodeList.toArray();
    }

    /**
     * Returns the elements as a typed array.
     */
    public <T> T[] toArray(Class<T> clazz) {
        return Nodes.toArray(toArray(), clazz);
    }

    /**
     * Returns the elements as a Set.
     */
    public Set<Object> toSet() {
        return nodeList == null ? Collections.emptySet() : Sjf4jConfig.global().setSupplier.create(nodeList);
    }

    /**
     * Returns the elements as a typed Set.
     */
    public <T> Set<T> toSet(Class<T> clazz) {
        return Nodes.toSet(toList(), clazz);
    }

    /**
     * Performs the action for each element.
     */
    public void forEach(Consumer<Object> action) {
        if (nodeList == null) return;
        for (Object object : nodeList) {
            action.accept(object);
        }
    }

    /**
     * Performs the action for each index/value pair.
     */
    public void forEach(BiConsumer<Integer, Object> action) {
        if (nodeList == null) return;
        for (int i = 0; i < nodeList.size(); i++) {
            action.accept(i, nodeList.get(i));
        }
    }

    public boolean anyMatch(BiPredicate<Integer, Object> predicate) {
        if (nodeList == null) return false;
        for (int i = 0; i < nodeList.size(); i++) {
            if (predicate.test(i, nodeList.get(i))) return true;
        }
        return false;
    }

    /**
     * Returns an iterator over elements.
     */
    public Iterator<Object> iterator() {
        if (nodeList == null) return Collections.emptyIterator();
        return nodeList.iterator();
    }

    /**
     * Normalizes negative indices against array size.
     */
    private int pos(int idx) {
        return idx < 0 ? size() + idx : idx;
    }

    /**
     * Returns true if the index is within bounds.
     * <p>
     * Negative indexes are normalized from array tail.
     */
    public boolean containsIndex(int idx) {
        idx = pos(idx);
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
        if (nodeList == null) return false;
        for (Object o : nodeList) {
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
        return Sjf4j.fromJson(input, JsonArray.class);
    }

    /**
     * Serializes this JsonArray to JSON.
     */
    public String toJson() {
        return Sjf4j.toJsonString(this);
    }

    /// YAML Facade

    /**
     * Parses a YAML string into a JsonArray.
     */
    public static JsonArray fromYaml(String input) {
        return Sjf4j.fromYaml(input, JsonArray.class);
    }

    /**
     * Serializes this JsonArray to YAML.
     */
    public String toYaml() {
        return Sjf4j.toYamlString(this);
    }

    /// Node Facade

    /**
     * Converts a node into a JsonArray.
     */
    public static JsonArray fromNode(Object node) {
        return Sjf4j.fromNode(node, JsonArray.class);
    }

    /**
     * Converts this JsonArray into the target node type.
     */
    public <T> T toNode(Class<T> clazz) {
        return Sjf4j.fromNode(this, clazz);
    }

    /// Getter

    /**
     * Returns the node at the given index or {@code null} when out of range.
     * <p>
     * Supports negative indexes ({@code -1} means last element).
     */
    public Object getNode(int idx) {
        int pidx = pos(idx);
        if (pidx >= 0 && pidx < size()) {
            return nodeList.get(pidx);
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
    public String getString(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toString(value);
        } catch (Exception e) {
            throw new JsonException("get failed: String at index " + idx, e);
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
            throw new JsonException("as failed: value at index " + idx + " to String", e);
        }
    }

    /**
     * Returns a Number value using strict conversion.
     */
    public Number getNumber(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toNumber(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Number at index " + idx, e);
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
            Object value = getNode(idx);
            return Nodes.asNumber(value);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to Number", e);
        }
    }

    /**
     * Returns a Long value using strict conversion.
     */
    public Long getLong(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toLong(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Long at index " + idx, e);
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
            Object value = getNode(idx);
            return Nodes.asLong(value);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to Long", e);
        }
    }

    /**
     * Returns an Integer value using strict conversion.
     */
    public Integer getInt(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toInt(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Integer at index " + idx, e);
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
            Object value = getNode(idx);
            return Nodes.asInt(value);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to Integer", e);
        }
    }

    /**
     * Returns a Short value using strict conversion.
     */
    public Short getShort(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toShort(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Short at index " + idx, e);
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
            Object value = getNode(idx);
            return Nodes.asShort(value);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to Short", e);
        }
    }

    /**
     * Returns a Byte value using strict conversion.
     */
    public Byte getByte(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toByte(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Byte at index " + idx, e);
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
            Object value = getNode(idx);
            return Nodes.asByte(value);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to Byte", e);
        }
    }

    /**
     * Returns a Double value using strict conversion.
     */
    public Double getDouble(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toDouble(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Double at index " + idx, e);
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
            Object value = getNode(idx);
            return Nodes.asDouble(value);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to Double", e);
        }
    }

    /**
     * Returns a Float value using strict conversion.
     */
    public Float getFloat(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toFloat(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Float at index " + idx, e);
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
            Object value = getNode(idx);
            return Nodes.asFloat(value);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to Float", e);
        }
    }

    /**
     * Returns a BigInteger value using strict conversion.
     */
    public BigInteger getBigInteger(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("get failed: BigInteger at index " + idx, e);
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
            Object value = getNode(idx);
            return Nodes.asBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to BigInteger", e);
        }
    }

    /**
     * Returns a BigDecimal value using strict conversion.
     */
    public BigDecimal getBigDecimal(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("get failed: BigDecimal at index " + idx, e);
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
            Object value = getNode(idx);
            return Nodes.asBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to BigDecimal", e);
        }
    }

    /**
     * Returns a Boolean value using strict conversion.
     */
    public Boolean getBoolean(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toBoolean(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Boolean at index " + idx, e);
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
            Object value = getNode(idx);
            return Nodes.asBoolean(value);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to Boolean", e);
        }
    }

    /**
     * Returns a JsonObject value using strict conversion.
     */
    public JsonObject getJsonObject(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("get failed: JsonObject at index " + idx, e);
        }
    }

    /**
     * Returns a Map value using strict conversion.
     */
    public Map<String, Object> getMap(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toMap(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Map<String,Object> at index " + idx, e);
        }
    }

    /**
     * Returns a typed Map value using strict conversion.
     */
    public <T> Map<String, T> getMap(int idx, Class<T> clazz) {
        try {
            Object value = getNode(idx);
            return Nodes.toMap(value, clazz);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to Map<String," +
                    clazz.getName() + ">", e);
        }
    }

    /**
     * Returns a JsonArray value using strict conversion.
     */
    public JsonArray getJsonArray(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("get failed: JsonArray at index " + idx, e);
        }
    }

    /**
     * Returns a List value using strict conversion.
     */
    public List<Object> getList(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toList(value);
        } catch (Exception e) {
            throw new JsonException("get failed: List<Object> at index " + idx, e);
        }
    }

    /**
     * Returns a typed List value using strict conversion.
     */
    public <T> List<T> getList(int idx, Class<T> clazz) {
        try {
            Object value = getNode(idx);
            return Nodes.toList(value, clazz);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to List<" + clazz.getName() + ">", e);
        }
    }

    /**
     * Returns an Object array using strict conversion.
     */
    public Object[] getArray(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toArray(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Object[] at index " + idx, e);
        }
    }

    /**
     * Returns a typed array using strict conversion.
     */
    public <T> T[] getArray(int idx, Class<T> clazz) {
        try {
            Object value = getNode(idx);
            return Nodes.toArray(value, clazz);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to " + clazz.getName() + "[]", e);
        }
    }

    /**
     * Returns a value converted to the given type.
     */
    public <T> T get(int idx, Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        Object value = getNode(idx);
        try {
            return Nodes.to(value, clazz);
        } catch (Exception e) {
            throw new JsonException("get failed: " + clazz.getName() + " at index " + idx, e);
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
        Object value = getNode(idx);
        try {
            return Nodes.as(value, clazz);
        } catch (Exception e) {
            throw new JsonException("as failed: value at index " + idx + " to " + clazz.getName(), e);
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

    /// Adder

    /**
     * Appends an element to the array.
     */
    public void add(Object object) {
        if (object != null && !elementType().isInstance(object))
            throw new JsonException("Type mismatch: cannot add element of type '" + object.getClass().getName() +
                    " to JsonArray with elementType '" + elementType().getName() + "'");

        if (nodeList == null) nodeList = Sjf4jConfig.global().listSupplier.create();
        nodeList.add(object);
    }

    /**
     * Appends an element only when non-null.
     */
    public void addNonNull(Object object) {
        if (object != null) add(object);
    }

    /**
     * Inserts an element at the given index.
     * <p>
     * Supports negative indexes after normalization. Valid insertion range is
     * {@code [0, size]}.
     */
    public void add(int idx, Object object) {
        if (object != null && !elementType().isInstance(object))
            throw new JsonException("Type mismatch: cannot add element of type '" + object.getClass().getName() +
                    " to JsonArray with elementType '" + elementType().getName() + "'");

        int pidx = pos(idx);
        if (pidx < 0 || pidx > size()) {
            throw new JsonException("Cannot add index " + idx + " in JsonArray of size " + size());
        }

        if (nodeList == null) nodeList = Sjf4jConfig.global().listSupplier.create();
        nodeList.add(pidx, object);
    }

    /**
     * Replaces the element at the given index.
     * <p>
     * Supports negative indexes after normalization. Valid replacement range is
     * {@code [0, size-1]}.
     */
    public Object set(int idx, Object object) {
        if (object != null && !elementType().isInstance(object))
            throw new JsonException("Type mismatch: cannot set element of type '" + object.getClass().getName() +
                    " in JsonArray with elementType '" + elementType().getName() + "'");

        int pidx = pos(idx);
        if (pidx < 0 || pidx >= size()) {
            throw new JsonException("Cannot set index " + idx + " in JsonArray of size " + size());
        }
        if (nodeList == null) nodeList = Sjf4jConfig.global().listSupplier.create();
        return nodeList.set(pidx, object);
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
     * Appends all values, flattening direct Java array arguments one level.
     */
    public void addAll(Object... values) {
        for (Object v : values) {
            if (v != null && v.getClass().isArray()) {
                int len = Array.getLength(v);
                for (int i = 0; i < len; i++) {
                    add(Array.get(v, i));
                }
            } else {
                add(v);
            }
        }
    }

    /**
     * Appends all short values.
     */
    public void addAll(short... values) {
        for (short v : values) { add(v); }
    }

    /**
     * Appends all int values.
     */
    public void addAll(int... values) {
        for (int v : values) { add(v); }
    }

    /**
     * Appends all long values.
     */
    public void addAll(long... values) {
        for (long v : values) { add(v); }
    }

    /**
     * Appends all float values.
     */
    public void addAll(float... values) {
        for (float v : values) { add(v); }
    }

    /**
     * Appends all double values.
     */
    public void addAll(double... values) {
        for (double v : values) { add(v); }
    }

    /**
     * Appends all char values.
     */
    public void addAll(char... values) {
        for (char v : values) { add(v); }
    }

    /**
     * Appends all boolean values.
     */
    public void addAll(boolean... values) {
        for (boolean v : values) { add(v); }
    }

    /**
     * Appends all values from a collection.
     */
    public void addAll(Collection<?> values) {
        for (Object v : values) { add(v); }
    }

    /**
     * Appends all values from another JsonArray.
     */
    public void addAll(JsonArray jsonArray) {
        for (int i=0; i < jsonArray.size(); i++) {
            add(jsonArray.getNode(i));
        }
    }

    /**
     * Removes the element at the given index.
     * <p>
     * Supports negative indexes after normalization.
     */
    public Object remove(int idx) {
        if (nodeList == null) return null;
        int pidx = pos(idx);
        if (pidx < 0 || pidx >= size()) {
            throw new JsonException("Cannot remove index " + idx + " in JsonArray of size " + size());
        }
        return nodeList.remove(pidx);
    }

    /**
     * Removes all elements from the array.
     */
    public void clear() {
        if (nodeList == null) return;
        nodeList.clear();
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
     */
    @SuppressWarnings("unchecked")
    public <T extends JsonArray> T copy() {
        return (T) Nodes.copy(this);
    }

    /**
     * Creates a deep copy of this JsonArray.
     */
    @SuppressWarnings("unchecked")
    public <T extends JsonArray> T deepCopy() {
        return (T) Sjf4j.deepNode(this);
    }

}
