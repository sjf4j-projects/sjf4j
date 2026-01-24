package org.sjf4j;

import org.sjf4j.node.NodeStream;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Represents a JSON array that extends {@link JsonContainer} and provides a list-like interface
 * for working with JSON data. JsonArray supports dynamic element storage and provides convenient
 * methods for type conversion and navigation.
 *
 * <p>JsonArray can be initialized from various sources:
 * <ul>
 *   <li>Empty constructor for creating a new JsonArray</li>
 *   <li>Existing JsonArray instances</li>
 *   <li>List objects</li>
 *   <li>Java arrays</li>
 * </ul>
 *
 * <p>This class implements list-like operations while maintaining JSON type safety and
 * providing convenient methods for type conversion and iteration.
 */
public class JsonArray extends JsonContainer implements Iterable<Object> {

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
     * Creates a JsonArray from an existing object, supporting multiple input types.
     *
     * @param node the object to wrap or copy
     * @throws JsonException if the input object type is not supported
     */
    @SuppressWarnings("unchecked")
    public JsonArray(Object node) {
        this();
        if (node == null) return;

        Class<?> elemClazz = elementType();
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            if (elemClazz != Object.class) {
                list.forEach(v -> {
                    if (v != null && !elemClazz.isInstance(v))
                        throw new JsonException("Element type mismatch: expected " + elemClazz.getName() +
                                ", but got " + v.getClass().getName());
                });
            }
            this.nodeList = list;
        } else if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            if (!elemClazz.isAssignableFrom(ja.elementType())) {
                ja.forEach(v -> {
                    if (v != null && !elemClazz.isInstance(v))
                        throw new JsonException("Element type mismatch: expected " + elemClazz.getName() +
                                ", but got " + v.getClass().getName());
                });
            }
            this.nodeList = ja.nodeList;
        } else if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            if (len > 0) {
                if (!elemClazz.isAssignableFrom(node.getClass().getComponentType())) {
                    for (int i = 0; i < len; i++) {
                        Object v = Array.get(node, i);
                        if (v != null && !elemClazz.isInstance(v))
                            throw new JsonException("Element type mismatch: expected " + elemClazz.getName() +
                                    ", but got " + v.getClass().getName());
                    }
                }
                this.nodeList = Sjf4jConfig.global().listSupplier.create();
                for (int i = 0; i < len; i++) {
                    this.nodeList.add(Array.get(node, i));
                }
            }
        } else {
            throw new JsonException("Cannot wrap value of type '" + node.getClass().getName() +
                    "' into JsonArray. Supported types are: JsonArray, List, or Array.");
        }
    }

    /// Object

    /**
     * Returns the expected element type of this JSON array.
     *
     * <p>The default implementation returns {@link Object}, indicating that
     * this array accepts arbitrary element values.</p>
     *
     * <p>Subclasses may override this method to declare a more specific
     * element type. This information can be used by JSON backends to
     * correctly deserialize array elements and to perform runtime
     * validation.</p>
     *
     * <p>This method serves as a semantic hint only and does not introduce
     * compile-time type constraints.</p>
     *
     * @return      the expected runtime type of elements contained in this array
     */
    public Class<?> elementType() {
        return Object.class;
    }

    /**
     * Returns the JSON-like string representation of this JsonArray.
     *
     * @return the JSON-like string representation
     */
    @Override
    public String toString() {
        return inspect();
    }

    /**
     * Returns the hash code value for this JsonArray.
     *
     * @return the hash code value
     */
    @Override
    public int hashCode() {
        return nodeList == null ? 0 : nodeList.hashCode();
    }

    @Override
    public boolean equals(Object target) {
        if (target == this) return true;
        if (target == null || target.getClass() != this.getClass()) return false;
        JsonArray targetJa = (JsonArray) target;
        if (targetJa.size() != this.size()) return false;
        return Objects.equals(this.nodeList, targetJa.nodeList);
    }

    /**
     * Returns the number of elements in this JsonArray.
     *
     * @return the number of elements
     */
    @Override
    public int size() {
        return nodeList == null ? 0 : nodeList.size();
    }

    /**
     * Returns true if this JsonArray contains no elements.
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return nodeList == null || nodeList.isEmpty();
    }

    /**
     * Returns the elements of this JsonArray as a List.
     *
     * @return a List containing the elements, or empty list if no elements
     */
    public List<Object> toList() {
        return nodeList == null ? Collections.emptyList() : Sjf4jConfig.global().listSupplier.create(nodeList);
    }

    /**
     * Converts the elements of this JsonArray to a List of the specified type.
     *
     * @param <T> the target type
     * @param clazz the class of the target type
     * @return a List containing the converted elements
     * @throws IllegalArgumentException if clazz is null
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> toList(Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        if (nodeList == null) {
            return Collections.emptyList();
        } else if (elementType() != Object.class && clazz.isAssignableFrom(elementType())) {
            return Sjf4jConfig.global().listSupplier.create((List<T>) nodeList);
        } else {
            List<T> list = Sjf4jConfig.global().listSupplier.create();
            for (Object node : nodeList) {
                list.add(Nodes.as(node, clazz));
            }
            return list;
        }
    }

    public Object[] toArray() {
        return nodeList == null ? new Object[0] : nodeList.toArray();
    }

    @SuppressWarnings("unchecked")
    public <T> T[] toArray(Class<T> clazz) {
        if (nodeList == null) {
            return (T[]) Array.newInstance(clazz, 0);
        } else if (elementType() != Object.class && clazz.isAssignableFrom(elementType())) {
            T[] arr = (T[]) Array.newInstance(clazz, nodeList.size());
            return nodeList.toArray(arr);
        } else {
            T[] arr = (T[]) Array.newInstance(clazz, size());
            for (int i = 0; i < size(); i++) {
                arr[i] = as(i, clazz);
            }
            return arr;
        }
    }

    public void forEach(Consumer<Object> action) {
        if (nodeList == null) return;
        for (Object object : nodeList) {
            action.accept(object);
        }
    }

    public void forEach(BiConsumer<Integer, Object> action) {
        if (nodeList == null) return;
        for (int i = 0; i < nodeList.size(); i++) {
            action.accept(i, nodeList.get(i));
        }
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public Iterator<Object> iterator() {
        return toList().iterator();
    }

    private int posIndex(int idx) {
        return idx < 0 ? size() + idx : idx;
    }

    public boolean containsIndex(int idx) {
        idx = posIndex(idx);
        return idx >= 0 && idx < size();
    }

    public boolean hasNonNull(int idx) {
        return getNode(idx) != null;
    }

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

    public static JsonArray fromJson(String input) {
        return Sjf4j.fromJson(input, JsonArray.class);
    }

    public static <T extends JsonArray> T fromJson(String input, Class<T> clazz) {
        return Sjf4j.fromJson(input, clazz);
    }

    public static <T extends JsonArray> T fromJson(String input, TypeReference<T> type) {
        return Sjf4j.fromJson(input, type);
    }

    public String toJson() {
        return Sjf4j.toJson(this);
    }

    /// YAML Facade

    public static JsonArray fromYaml(String input) {
        return Sjf4j.fromYaml(input, JsonArray.class);
    }

    public static <T extends JsonArray> T fromYaml(String input, Class<T> clazz) {
        return Sjf4j.fromYaml(input, clazz);
    }
    public String toYaml() {
        return Sjf4j.toYaml(this);
    }

    /// Node Facade

    public static JsonArray fromNode(Object node) {
        return Sjf4j.fromNode(node, JsonArray.class);
    }

    public static <T extends JsonArray> T fromNode(Object node, Class<T> clazz) {
        return Sjf4j.fromNode(node, clazz);
    }

    public static <T extends JsonArray> T fromNode(Object node, TypeReference<T> type) {
        return Sjf4j.fromNode(node, type);
    }

    public <T> T toNode(Class<T> clazz) {
        return Sjf4j.fromNode(this, clazz);
    }

    public <T> T toNode(TypeReference<T> type) {
        return Sjf4j.fromNode(this, type);
    }

    public static JsonArray deepNode(Object node) {
        return Sjf4j.deepNode(node, JsonArray.class);
    }

    public static <T extends JsonArray> T deepNode(Object node, Class<T> clazz) {
        return Sjf4j.deepNode(node, clazz);
    }

    public static <T extends JsonArray> T deepNode(Object node, TypeReference<T> type) {
        return Sjf4j.deepNode(node, type);
    }

    public <T> T deepNode(Class<T> clazz) {
        return Sjf4j.deepNode(this, clazz);
    }

    public <T> T deepNode(TypeReference<T> type) {
        return Sjf4j.deepNode(this, type);
    }


    /// Getter

    public Object getNode(int idx) {
        int pidx = posIndex(idx);
        if (pidx >= 0 && pidx < size()) {
            return nodeList.get(pidx);
        } else {
            return null;
        }
    }
    public Object getNode(int idx, Object defaultValue) {
        Object value = getNode(idx);
        return value == null ? defaultValue : value;
    }

    public String getString(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get String at index " + idx, e);
        }
    }
    public String getString(int idx, String defaultValue) {
        String value = getString(idx);
        return value == null ? defaultValue : value;
    }

    public String asString(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to String", e);
        }
    }
    public String asString(int idx, String defaultValue) {
        String value = asString(idx);
        return value == null ? defaultValue : value;
    }

    public Number getNumber(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toNumber(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Number at index " + idx, e);
        }
    }
    public Number getNumber(int idx, Number defaultValue) {
        Number value = getNumber(idx);
        return value == null ? defaultValue : value;
    }

    public Number asNumber(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asNumber(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to Number", e);
        }
    }
    public Number asNumber(int idx, Number defaultValue) {
        Number value = asNumber(idx);
        return value == null ? defaultValue : value;
    }

    public Long getLong(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Long at index " + idx, e);
        }
    }
    public long getLong(int idx, long defaultValue) {
        Long value = getLong(idx);
        return value == null ? defaultValue : value;
    }

    public Long asLong(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to Long", e);
        }
    }
    public long asLong(int idx, long defaultValue) {
        Long value = asLong(idx);
        return value == null ? defaultValue : value;
    }

    public Integer getInteger(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Integer at index " + idx, e);
        }
    }
    public int getInteger(int idx, int defaultValue) {
        Integer value = getInteger(idx);
        return value == null ? defaultValue : value;
    }

    public Integer asInteger(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to Integer", e);
        }
    }
    public int asInteger(int idx, int defaultValue) {
        Integer value = asInteger(idx);
        return value == null ? defaultValue : value;
    }

    public Short getShort(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Short at index " + idx, e);
        }
    }
    public short getShort(int idx, short defaultValue) {
        Short value = getShort(idx);
        return value == null ? defaultValue : value;
    }

    public Short asShort(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to Short", e);
        }
    }
    public short asShort(int idx, short defaultValue) {
        Short value = asShort(idx);
        return value == null ? defaultValue : value;
    }

    public Byte getByte(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Byte at index " + idx, e);
        }
    }
    public byte getByte(int idx, byte defaultValue) {
        Byte value = getByte(idx);
        return value == null ? defaultValue : value;
    }

    public Byte asByte(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to Byte", e);
        }
    }
    public byte asByte(int idx, byte defaultValue) {
        Byte value = asByte(idx);
        return value == null ? defaultValue : value;
    }

    public Double getDouble(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Double at index " + idx, e);
        }
    }
    public double getDouble(int idx, double defaultValue) {
        Double value = getDouble(idx);
        return value == null ? defaultValue : value;
    }

    public Double asDouble(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to Double", e);
        }
    }
    public double asDouble(int idx, double defaultValue) {
        Double value = asDouble(idx);
        return value == null ? defaultValue : value;
    }

    public Float getFloat(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Float at index " + idx, e);
        }
    }
    public float getFloat(int idx, float defaultValue) {
        Float value = getFloat(idx);
        return value == null ? defaultValue : value;
    }

    public Float asFloat(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to Float", e);
        }
    }
    public float asFloat(int idx, float defaultValue) {
        Float value = asFloat(idx);
        return value == null ? defaultValue : value;
    }

    public BigInteger getBigInteger(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigInteger at index " + idx, e);
        }
    }
    public BigInteger getBigInteger(int idx, BigInteger defaultValue) {
        BigInteger value = getBigInteger(idx);
        return value == null ? defaultValue : value;
    }

    public BigInteger asBigInteger(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to BigInteger", e);
        }
    }
    public BigInteger asBigInteger(int idx, BigInteger defaultValue) {
        BigInteger value = asBigInteger(idx);
        return value == null ? defaultValue : value;
    }

    public BigDecimal getBigDecimal(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigDecimal at index " + idx, e);
        }
    }
    public BigDecimal getBigDecimal(int idx, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(idx);
        return value == null ? defaultValue : value;
    }

    public BigDecimal asBigDecimal(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to BigDecimal", e);
        }
    }
    public BigDecimal asBigDecimal(int idx, BigDecimal defaultValue) {
        BigDecimal value = asBigDecimal(idx);
        return value == null ? defaultValue : value;
    }

    public Boolean getBoolean(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.toBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Boolean at index " + idx, e);
        }
    }
    public boolean getBoolean(int idx, boolean defaultValue) {
        Boolean value = getBoolean(idx);
        return value == null ? defaultValue : value;
    }

    public Boolean asBoolean(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to Boolean", e);
        }
    }
    public boolean asBoolean(int idx, boolean defaultValue) {
        Boolean value = asBoolean(idx);
        return value == null ? defaultValue : value;
    }

    public JsonObject getJsonObject(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonObject at index " + idx, e);
        }
    }
    public JsonObject getJsonObject(int idx, JsonObject defaultValue) {
        JsonObject value = getJsonObject(idx);
        return value == null ? defaultValue : value;
    }

    public Map<String, Object> getMap(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asMap(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Map<String, Object> at index " + idx, e);
        }
    }
    public Map<String, Object> getMap(int idx, Map<String, Object> defaultValue) {
        Map<String, Object> value = getMap(idx);
        return value == null ? defaultValue : value;
    }

    public <T> Map<String, T> asMap(int idx, Class<T> clazz) {
        try {
            Object value = getNode(idx);
            return Nodes.asMap(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to Map<String, " +
                    clazz.getName() + ">", e);
        }
    }
    public <T> Map<String, T> asMap(int idx, Class<T> clazz, Map<String, T> defaultValue) {
        Map<String, T> value = asMap(idx, clazz);
        return value == null ? defaultValue : value;
    }

    public JsonArray getJsonArray(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonArray at index " + idx, e);
        }
    }
    public JsonArray getJsonArray(int idx, JsonArray defaultValue) {
        JsonArray value = getJsonArray(idx);
        return value == null ? defaultValue : value;
    }

    public List<Object> getList(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asList(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get List<Object> at index " + idx, e);
        }
    }
    public List<Object> getList(int idx, List<Object> defaultValue) {
        List<Object> value = getList(idx);
        return value == null ? defaultValue : value;
    }

    public <T> List<T> asList(int idx, Class<T> clazz) {
        try {
            Object value = getNode(idx);
            return Nodes.asList(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to List<" + clazz.getName() + ">", e);
        }
    }
    public <T> List<T> asList(int idx, Class<T> clazz, List<T> defaultValue) {
        List<T> value = asList(idx, clazz);
        return value == null ? defaultValue : value;
    }

    public Object[] getArray(int idx) {
        try {
            Object value = getNode(idx);
            return Nodes.asArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Object[] at index " + idx, e);
        }
    }
    public Object[] getArray(int idx, Object[] defaultValue) {
        Object[] value = getArray(idx);
        return value == null ? defaultValue : value;
    }

    public <T> T[] asArray(int idx, Class<T> clazz) {
        try {
            Object value = getNode(idx);
            return Nodes.asArray(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to " + clazz.getName() + "[]", e);
        }
    }
    public <T> T[] asArray(int idx, Class<T> clazz, T[] defaultValue) {
        T[] value = asArray(idx, clazz);
        return value == null ? defaultValue : value;
    }

    public <T> T get(int idx, Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        Object value = getNode(idx);
        try {
            return Nodes.to(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to get " + clazz.getName() + " at index " + idx, e);
        }
    }
    @SuppressWarnings("unchecked")
    public <T> T get(int idx, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(idx, clazz);
    }

    public <T> T as(int idx, Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        Object value = getNode(idx);
        try {
            return Nodes.as(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to " + clazz.getName(), e);
        }
    }
    @SuppressWarnings("unchecked")
    public <T> T as(int idx, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return as(idx, clazz);
    }


    /// Adder

    public void add(Object object) {
        if (object != null && !elementType().isInstance(object))
            throw new IllegalArgumentException("Cannot add element of type " + object.getClass().getName() +
                    " to JsonArray with elementType '" + elementType().getName() + "'");

        if (nodeList == null) nodeList = Sjf4jConfig.global().listSupplier.create();
        nodeList.add(object);
    }

    public void addNonNull(Object object) {
        if (object != null) add(object);
    }

    public void add(int idx, Object object) {
        if (object != null && !elementType().isInstance(object))
            throw new IllegalArgumentException("Cannot add element of type " + object.getClass().getName() +
                    " to JsonArray with elementType '" + elementType().getName() + "'");

        int pidx = posIndex(idx);
        if (pidx < 0 || pidx > size()) {
            throw new JsonException("Cannot add index " + idx + " in JsonArray of size " + size());
        }

        if (nodeList == null) nodeList = Sjf4jConfig.global().listSupplier.create();
        nodeList.add(pidx, object);
    }

    public Object set(int idx, Object object) {
        if (object != null && !elementType().isInstance(object))
            throw new IllegalArgumentException("Cannot set element of type " + object.getClass().getName() +
                    " to JsonArray with elementType '" + elementType().getName() + "'");

        int pidx = posIndex(idx);
        if (pidx < 0 || pidx >= size()) {
            throw new JsonException("Cannot set index " + idx + " in JsonArray of size " + size());
        }
        if (nodeList == null) nodeList = Sjf4jConfig.global().listSupplier.create();
        return nodeList.set(pidx, object);
    }

    public Object setIfAbsent(int idx, Object object) {
        Object old = getNode(idx);
        if (old == null) {
            return set(idx, object);
        }
        return old;
    }

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

    public void addAll(short... values) {
        for (short v : values) { add(v); }
    }

    public void addAll(int... values) {
        for (int v : values) { add(v); }
    }

    public void addAll(long... values) {
        for (long v : values) { add(v); }
    }

    public void addAll(float... values) {
        for (float v : values) { add(v); }
    }

    public void addAll(double... values) {
        for (double v : values) { add(v); }
    }

    public void addAll(char... values) {
        for (char v : values) { add(v); }
    }

    public void addAll(boolean... values) {
        for (boolean v : values) { add(v); }
    }

    public void addAll(Collection<?> values) {
        for (Object v : values) { add(v); }
    }

    public void addAll(JsonArray jsonArray) {
        for (int i=0; i < jsonArray.size(); i++) {
            add(jsonArray.getNode(i));
        }
    }

    public Object remove(int idx) {
        if (nodeList == null) return null;
        return nodeList.remove(posIndex(idx));
    }

    public void clear() {
        if (nodeList == null) return;
        nodeList.clear();
    }

    /// Stream

    public NodeStream<JsonArray> stream() {
        return NodeStream.of(this);
    }

    /// Copy

    @SuppressWarnings("unchecked")
    public <T extends JsonArray> T copy() {
        return (T) Nodes.copy(this);
    }
    @SuppressWarnings("unchecked")
    public <T extends JsonArray> T deepCopy() {
        return (T) Nodes.deepCopy(this);
    }

}