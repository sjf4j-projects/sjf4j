package org.sjf4j;

import org.sjf4j.node.NodeWalker;
import org.sjf4j.patch.JsonPatch;
import org.sjf4j.path.JsonPath;
import org.sjf4j.util.NodeUtil;
import org.sjf4j.util.PatchUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Abstract base class for JSON container types (JsonObject and JsonArray).
 * <p>
 * This class provides common functionality for working with JSON data structures,
 * including path-based access, type conversion methods, and traversal capabilities.
 * It serves as the foundation for both JSON object and array implementations.
 */
public abstract class JsonContainer {

    /// Base

    /**
     * Returns the size of the container.
     * For JsonObject, this returns the number of key-value pairs.
     * For JsonArray, this returns the number of elements.
     *
     * @return the size of the container
     */
    public abstract int size();


    public boolean nodeEquals(Object target) {
        return NodeUtil.equals(this, target);
    }

    /**
     * Returns a string representation of this container for debugging purposes.
     * Uses {@link NodeUtil#inspect(Object)} to generate the string.
     *
     * @return a debug string representation of the container
     */
    public String inspect() {
        return NodeUtil.inspect(this);
    }

    /**
     * Recursively removes all null values from this JsonObject and its nested structures.
     * <p>
     * This operation traverses all nested objects and arrays, removing any null values found.
     * Empty containers resulting from null removal are not automatically removed.
     */
    public void deepPruneNulls() {
        NodeWalker.walk(this, NodeWalker.Target.CONTAINER, NodeWalker.Order.BOTTOM_UP,
                (path, node) -> {
                    if (node instanceof JsonObject) {
                        ((JsonObject) node).removeIf(e -> e.getValue() == null);
                    } else if (node instanceof Map) {
                        ((Map<?, ?>) node).entrySet().removeIf(e -> e.getValue() == null);
                    }
                    return NodeWalker.Control.CONTINUE;
                });
    }

    /// By path

    /**
     * Checks if a value exists at the specified JSON path.
     *
     * @param path the JSON path to check
     * @return true if a value exists at the path, even it is null, false otherwise
     */
    public boolean containsByPath(String path) {
        return JsonPath.compile(path).contains(this);
    }

    /**
     * Checks if a value is null at the specified JSON path.
     *
     * @param path the JSON path to check
     * @return true if a non-null value exists at the path, false otherwise
     */
    public boolean hasNonNullByPath(String path) {
        return JsonPath.compile(path).hasNonNull(this);
    }

    // Object
    /**
     * Gets the value at the specified JSON path as an Object.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path, or null if it doesn't exist
     */
    public Object getNodeByPath(String path) {
        return JsonPath.compile(path).getNode(this);
    }
    
    /**
     * Gets the value at the specified JSON path as an Object, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path, or the default value if it doesn't exist
     */
    public Object getNodeByPath(String path, Object defaultValue) {
        return JsonPath.compile(path).getNode(this, defaultValue);
    }

    // String

    /**
     * Gets the value at the specified JSON path as a String.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a String, or null if it doesn't exist or can't be converted
     */
    public String getStringByPath(String path) {
        return JsonPath.compile(path).getString(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a String, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a String, or the default value if it doesn't exist or can't be converted
     */
    public String getStringByPath(String path, String defaultValue) {
        return JsonPath.compile(path).getString(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a String.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a String, or null if it doesn't exist
     */
    public String asStringByPath(String path) {
        return JsonPath.compile(path).asString(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a String, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a String, or the default value if it doesn't exist
     */
    public String asStringByPath(String path, String defaultValue) {
        return JsonPath.compile(path).asString(this, defaultValue);
    }

    // Number

    /**
     * Gets the value at the specified JSON path as a Number.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Number, or null if it doesn't exist or can't be converted
     */
    public Number getNumberByPath(String path) {
        return JsonPath.compile(path).getNumber(this);
    }

    /**
     * Gets the value at the specified JSON path as a Number, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a Number, or the default value if it doesn't exist or can't be converted
     */
    public Number getNumberByPath(String path, Number defaultValue) {
        return JsonPath.compile(path).getNumber(this, defaultValue);
    }

    /**
     * Converts the value at the specified JSON path to a Number.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Number, or null if it doesn't exist
     */
    public Number asNumberByPath(String path) {
        return JsonPath.compile(path).asNumber(this);
    }

    /**
     * Converts the value at the specified JSON path to a Number, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a Number, or the default value if it doesn't exist
     */
    public Number asNumberByPath(String path, Number defaultValue) {
        return JsonPath.compile(path).asNumber(this, defaultValue);
    }

    // Long

    /**
     * Gets the value at the specified JSON path as a Long.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Long, or null if it doesn't exist or can't be converted
     */
    public Long getLongByPath(String path) {
        return JsonPath.compile(path).getLong(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a long primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a long, or the default value if it doesn't exist or can't be converted
     */
    public long getLongByPath(String path, long defaultValue) {
        return JsonPath.compile(path).getLong(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a Long.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Long, or null if it doesn't exist
     */
    public Long asLongByPath(String path) {
        return JsonPath.compile(path).asLong(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a long primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a long, or the default value if it doesn't exist
     */
    public long asLongByPath(String path, long defaultValue) {
        return JsonPath.compile(path).asLong(this, defaultValue);
    }

    // Integer

    /**
     * Gets the value at the specified JSON path as an Integer.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as an Integer, or null if it doesn't exist or can't be converted
     */
    public Integer getIntegerByPath(String path) {
        return JsonPath.compile(path).getInteger(this);
    }
    
    /**
     * Gets the value at the specified JSON path as an int primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as an int, or the default value if it doesn't exist or can't be converted
     */
    public int getIntegerByPath(String path, int defaultValue) {
        return JsonPath.compile(path).getInteger(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to an Integer.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to an Integer, or null if it doesn't exist
     */
    public Integer asIntegerByPath(String path) {
        return JsonPath.compile(path).asInteger(this);
    }
    
    /**
     * Converts the value at the specified JSON path to an int primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to an int, or the default value if it doesn't exist
     */
    public int asIntegerByPath(String path, int defaultValue) {
        return JsonPath.compile(path).asInteger(this, defaultValue);
    }

    // Short

    /**
     * Gets the value at the specified JSON path as a Short.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Short, or null if it doesn't exist or can't be converted
     */
    public Short getShortByPath(String path) {
        return JsonPath.compile(path).getShort(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a short primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a short, or the default value if it doesn't exist or can't be converted
     */
    public short getShortByPath(String path, short defaultValue) {
        return JsonPath.compile(path).getShort(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a Short.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Short, or null if it doesn't exist
     */
    public Short asShortByPath(String path) {
        return JsonPath.compile(path).asShort(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a short primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a short, or the default value if it doesn't exist
     */
    public short asShortByPath(String path, short defaultValue) {
        return JsonPath.compile(path).asShort(this, defaultValue);
    }

    // Byte

    /**
     * Gets the value at the specified JSON path as a Byte.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Byte, or null if it doesn't exist or can't be converted
     */
    public Byte getByteByPath(String path) {
        return JsonPath.compile(path).getByte(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a byte primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a byte, or the default value if it doesn't exist or can't be converted
     */
    public byte getByteByPath(String path, byte defaultValue) {
        return JsonPath.compile(path).getByte(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a Byte.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Byte, or null if it doesn't exist
     */
    public Byte asByteByPath(String path) {
        return JsonPath.compile(path).asByte(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a byte primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a byte, or the default value if it doesn't exist
     */
    public byte asByteByPath(String path, byte defaultValue) {
        return JsonPath.compile(path).asByte(this, defaultValue);
    }

    // Double

    /**
     * Gets the value at the specified JSON path as a Double.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Double, or null if it doesn't exist or can't be converted
     */
    public Double getDoubleByPath(String path) {
        return JsonPath.compile(path).getDouble(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a double primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a double, or the default value if it doesn't exist or can't be converted
     */
    public double getDoubleByPath(String path, double defaultValue) {
        return JsonPath.compile(path).getDouble(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a Double.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Double, or null if it doesn't exist
     */
    public Double asDoubleByPath(String path) {
        return JsonPath.compile(path).asDouble(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a double primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a double, or the default value if it doesn't exist
     */
    public double asDoubleByPath(String path, double defaultValue) {
        return JsonPath.compile(path).asDouble(this, defaultValue);
    }

    // Float

    /**
     * Gets the value at the specified JSON path as a Float.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Float, or null if it doesn't exist or can't be converted
     */
    public Float getFloatByPath(String path) {
        return JsonPath.compile(path).getFloat(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a float primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a float, or the default value if it doesn't exist or can't be converted
     */
    public float getFloatByPath(String path, float defaultValue) {
        return JsonPath.compile(path).getFloat(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a Float.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Float, or null if it doesn't exist
     */
    public Float asFloatByPath(String path) {
        return JsonPath.compile(path).asFloat(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a float primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a float, or the default value if it doesn't exist
     */
    public float asFloatByPath(String path, float defaultValue) {
        return JsonPath.compile(path).asFloat(this, defaultValue);
    }

    // BigInteger

    /**
     * Gets the value at the specified JSON path as a BigInteger.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a BigInteger, or null if it doesn't exist or can't be converted
     */
    public BigInteger getBigIntegerByPath(String path) {
        return JsonPath.compile(path).getBigInteger(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a BigInteger, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a BigInteger, or the default value if it doesn't exist or can't be converted
     */
    public BigInteger getBigIntegerByPath(String path, BigInteger defaultValue) {
        return JsonPath.compile(path).getBigInteger(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a BigInteger.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a BigInteger, or null if it doesn't exist
     */
    public BigInteger asBigIntegerByPath(String path) {
        return JsonPath.compile(path).asBigInteger(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a BigInteger, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a BigInteger, or the default value if it doesn't exist
     */
    public BigInteger asBigIntegerByPath(String path, BigInteger defaultValue) {
        return JsonPath.compile(path).asBigInteger(this, defaultValue);
    }

    // BigDecimal

    /**
     * Gets the value at the specified JSON path as a BigDecimal.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a BigDecimal, or null if it doesn't exist or can't be converted
     */
    public BigDecimal getBigDecimalByPath(String path) {
        return JsonPath.compile(path).getBigDecimal(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a BigDecimal, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a BigDecimal, or the default value if it doesn't exist or can't be converted
     */
    public BigDecimal getBigDecimalByPath(String path, BigDecimal defaultValue) {
        return JsonPath.compile(path).getBigDecimal(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a BigDecimal.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a BigDecimal, or null if it doesn't exist
     */
    public BigDecimal asBigDecimalByPath(String path) {
        return JsonPath.compile(path).asBigDecimal(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a BigDecimal, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a BigDecimal, or the default value if it doesn't exist
     */
    public BigDecimal asBigDecimalByPath(String path, BigDecimal defaultValue) {
        return JsonPath.compile(path).asBigDecimal(this, defaultValue);
    }

    // Boolean

    /**
     * Gets the value at the specified JSON path as a Boolean.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Boolean, or null if it doesn't exist or can't be converted
     */
    public Boolean getBooleanByPath(String path) {
        return JsonPath.compile(path).getBoolean(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a boolean primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a boolean, or the default value if it doesn't exist or can't be converted
     */
    public boolean getBooleanByPath(String path, boolean defaultValue) {
        return JsonPath.compile(path).getBoolean(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a Boolean.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Boolean, or null if it doesn't exist
     */
    public Boolean asBooleanByPath(String path) {
        return JsonPath.compile(path).asBoolean(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a boolean primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a boolean, or the default value if it doesn't exist
     */
    public boolean asBooleanByPath(String path, boolean defaultValue) {
        return JsonPath.compile(path).asBoolean(this, defaultValue);
    }

    // Map

    /**
     * Gets the value at the specified JSON path as a Map.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Map, or null if it doesn't exist or can't be converted
     */
    public Map<String, Object> getMapByPath(String path) {
        return JsonPath.compile(path).getMap(this);
    }

    /**
     * Gets the value at the specified JSON path as a Map, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a Map, or the default value if it doesn't exist or can't be converted
     */
    public Map<String, Object> getMapByPath(String path, Map<String, Object> defaultValue) {
        return JsonPath.compile(path).getMap(this, defaultValue);
    }

    /**
     * Converts the value at the specified JSON path to a Map.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Map, or null if it doesn't exist
     */
    public <T> Map<String, T> asMapByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).asMap(this, clazz);
    }

    /**
     * Converts the value at the specified JSON path to a Map, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a Map, or the default value if it doesn't exist
     */
    public <T> Map<String, T> asMapByPath(String path, Class<T> clazz, Map<String, T> defaultValue) {
        return JsonPath.compile(path).asMap(this, clazz, defaultValue);
    }

    // JsonObject
    /**
     * Gets the value at the specified JSON path as a JsonObject.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a JsonObject, or null if it doesn't exist or can't be converted
     */
    public JsonObject getJsonObjectByPath(String path) {
        return JsonPath.compile(path).getJsonObject(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a JsonObject, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a JsonObject, or the default value if it doesn't exist or can't be converted
     */
    public JsonObject getJsonObjectByPath(String path, JsonObject defaultValue) {
        return JsonPath.compile(path).getJsonObject(this, defaultValue);
    }

    // JsonArray
    /**
     * Gets the value at the specified JSON path as a JsonArray.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a JsonArray, or null if it doesn't exist or can't be converted
     */
    public JsonArray getJsonArrayByPath(String path) {
        return JsonPath.compile(path).getJsonArray(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a JsonArray, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a JsonArray, or the default value if it doesn't exist or can't be converted
     */
    public JsonArray getJsonArrayByPath(String path, JsonArray defaultValue) {
        return JsonPath.compile(path).getJsonArray(this, defaultValue);
    }

    // List
    /**
     * Gets the value at the specified JSON path as a List.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a List, or null if it doesn't exist or can't be converted
     */
    public List<Object> getListByPath(String path) {
        return JsonPath.compile(path).getList(this);
    }

    /**
     * Gets the value at the specified JSON path as a List, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a List, or the default value if it doesn't exist or can't be converted
     */
    public List<Object> getListByPath(String path, List<Object> defaultValue) {
        return JsonPath.compile(path).getList(this, defaultValue);
    }

    /**
     * Converts the value at the specified JSON path to a List.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a List, or null if it doesn't exist
     */
    public <T> List<T> asListByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).asList(this, clazz);
    }

    /**
     * Converts the value at the specified JSON path to a List, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a List, or the default value if it doesn't exist
     */
    public <T> List<T> asListByPath(String path, Class<T> clazz, List<T> defaultValue) {
        return JsonPath.compile(path).asList(this, clazz, defaultValue);
    }

    // Array
    /**
     * Gets the value at the specified JSON path as a Array.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as an Array, or null if it doesn't exist or can't be converted
     */
    public Object[] getArrayByPath(String path) {
        return JsonPath.compile(path).getArray(this);
    }

    /**
     * Gets the value at the specified JSON path as a Array, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as an Array, or the default value if it doesn't exist or can't be converted
     */
    public Object[] getArrayByPath(String path, Object[] defaultValue) {
        return JsonPath.compile(path).getArray(this, defaultValue);
    }

    /**
     * Converts the value at the specified JSON path to a Array.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to an Array, or null if it doesn't exist
     */
    public <T> T[] asArrayByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).asArray(this, clazz);
    }

    /**
     * Converts the value at the specified JSON path to an Array, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to an Array, or the default value if it doesn't exist
     */
    public <T> T[] asArrayByPath(String path, Class<T> clazz, T[] defaultValue) {
        return JsonPath.compile(path).asArray(this, clazz, defaultValue);
    }

    
    // Clazz

    /**
     * Gets the value at the specified JSON path and converts it to the specified class type.
     *
     * @param <T> the type to convert to
     * @param path the JSON path to get the value from
     * @param clazz the class to convert the value to
     * @return the value at the path converted to the specified class, or null if it doesn't exist or can't be converted
     */
    public <T> T getByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).get(this, clazz);
    }


    /**
     * Gets the value at the specified JSON path and converts it to the inferred class type.
     * This is a convenience method that uses reified type parameters for type inference.
     *
     * @param <T> the type to convert to
     * @param path the JSON path to get the value from
     * @param reified an empty array of the target type (used for type inference only)
     * @return the value at the path converted to the specified class, or null if it doesn't exist or can't be converted
     */
    @SafeVarargs
    public final <T> T getByPath(String path, T... reified) {
        return JsonPath.compile(path).get(this, reified);
    }

    /**
     * Converts the value at the specified JSON path to the specified class type.
     *
     * @param <T> the type to convert to
     * @param path the JSON path to get the value from
     * @param clazz the class to convert the value to
     * @return the value at the path converted to the specified class, or null if it doesn't exist
     */
    public <T> T asByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).as(this, clazz);
    }

    /**
     * Converts the value at the specified JSON path to the inferred class type.
     * This is a convenience method that uses reified type parameters for type inference.
     *
     * @param <T> the type to convert to
     * @param path the JSON path to get the value from
     * @param reified an empty array of the target type (used for type inference only)
     * @return the value at the path converted to the specified class, or null if it doesn't exist
     * @throws IllegalArgumentException if reified is not empty
     */
    @SafeVarargs
    public final <T> T asByPath(String path, T... reified) {
        return JsonPath.compile(path).as(this, reified);
    }
    
    // Put by path

    /**
     * Puts a value at the specified JSON path, creating intermediate objects/arrays if needed.
     *
     * @param path the JSON path to put the value at
     * @param value the value to put
     */
    public void ensurePutByPath(String path, Object value) {
        JsonPath.compile(path).ensurePut(this, value);
    }

    /**
     * Puts a value at the specified JSON path only if the value is non-null,
     * creating intermediate objects/arrays if needed.
     *
     * @param path the JSON path to put the value at
     * @param value the value to put (must be non-null)
     */
    public void ensurePutNonNullByPath(String path, Object value) {
        JsonPath.compile(path).ensurePutNonNull(this, value);
    }

    /**
     * Puts a value at the specified JSON path only if no value exists at that path,
     * creating intermediate objects/arrays if needed.
     *
     * @param path the JSON path to put the value at
     * @param value the value to put
     */
    public void ensurePutIfAbsentByPath(String path, Object value) {
        JsonPath.compile(path).ensurePutIfAbsent(this, value);
    }

    public void addByPath(String path, Object value) {
        JsonPath.compile(path).add(this, value);
    }

    public void replaceByPath(String path, Object value) {
        JsonPath.compile(path).replace(this, value);
    }

    public void removeByPath(String path) {
        JsonPath.compile(path).remove(this);
    }

    /// Find

    public List<Object> findNodesByPath(String path) {
        return JsonPath.compile(path).findNodes(this);
    }

    public <T> List<T> findByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).find(this, clazz);
    }

    public <T> List<T> findAsByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).findAs(this, clazz);
    }

    /// Eval

    public Object evalByPath(String path) {
        return JsonPath.compile(path).eval(this);
    }

    public <T> T evalByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).eval(this, clazz);
    }


    /// Walk

    /**
     * Traverses the JSON container using the specified visitor function.
     *
     * @param visitor the function to apply to each node during traversal
     */
    public void walk(BiFunction<JsonPath, Object, NodeWalker.Control> visitor) {
        NodeWalker.walk(this, visitor);
    }

    /**
     * Traverses the JSON container using the specified visitor function and target.
     *
     * @param target the target nodes to visit (objects, arrays, values, or all)
     * @param visitor the function to apply to each node during traversal
     */
    public void walk(NodeWalker.Target target,
                     BiFunction<JsonPath, Object, NodeWalker.Control> visitor) {
        NodeWalker.walk(this, target, visitor);
    }

    /**
     * Traverses the JSON container using the specified visitor function, target, and order.
     *
     * @param target the target nodes to visit (objects, arrays, values, or all)
     * @param order the traversal order (depth-first or breadth-first)
     * @param visitor the function to apply to each node during traversal
     */
    public void walk(NodeWalker.Target target, NodeWalker.Order order,
                     BiFunction<JsonPath, Object, NodeWalker.Control> visitor) {
        NodeWalker.walk(this, target, order, visitor);
    }

    /**
     * Traverses the JSON container using the specified visitor function, target, order, and maximum depth.
     *
     * @param target the target nodes to visit (objects, arrays, values, or all)
     * @param order the traversal order (depth-first or breadth-first)
     * @param maxDepth the maximum depth to traverse
     * @param visitor the function to apply to each node during traversal
     */
    public void walk(NodeWalker.Target target, NodeWalker.Order order, int maxDepth,
                     BiFunction<JsonPath, Object, NodeWalker.Control> visitor) {
        NodeWalker.walk(this, target, order, maxDepth, visitor);
    }

    /// Patch

    public void apply(JsonPatch patch) {
        patch.apply(this);
    }

    /**
     * Merges the specified {@code mergePatch} into this {@code JsonObject}.
     *
     * <p>The merge is applied in place. Objects and arrays are merged recursively
     * according to {@link PatchUtil#merge(Object, Object, boolean, boolean)}.
     *
     * @param mergePatch    the patch object to merge
     * @param overwrite     whether existing non-null values should be replaced
     * @param deepCopy      whether composite values from the patch should be deep-copied
     */
    public void merge(Object mergePatch, boolean overwrite, boolean deepCopy) {
        PatchUtil.merge(this, mergePatch, overwrite, deepCopy);
    }

    /**
     * Merges the specified {@code mergePatch} into this {@code JsonObject}.
     *
     * <p>This is equivalent to {@link #merge(Object, boolean, boolean)} with
     * {@code overwrite=true} and {@code deepCopy=false}.
     *
     * @param mergePatch    the patch object to merge
     */
    public void merge(Object mergePatch) {
        merge(mergePatch, true, false);
    }

    /**
     * Merges the specified {@code mergePatch} into this {@code JsonObject},
     * making deep copies of any composite values from the patch.
     *
     * <p>This is equivalent to {@link #merge(Object, boolean, boolean)} with
     * {@code overwrite=true} and {@code deepCopy=true}.
     *
     * @param mergePatch    the patch object to merge
     */
    public void mergeWithCopy(Object mergePatch) {
        merge(mergePatch, true, true);
    }

    /**
     * Applies a <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386 (JSON Merge Patch)</a>
     * to this {@code JsonObject}.
     *
     * <p>Strictly follows the semantics defined in RFC 7386:
     * <ul>
     *     <li>Objects are merged by key recursively</li>
     *     <li>Arrays are replaced as a whole</li>
     *     <li>A {@code null} value deletes the corresponding target member</li>
     *     <li>No deep copy is performed</li>
     * </ul>
     *
     * @param mergePatch the JSON Merge Patch to apply
     */
    public void mergeRfc7386(Object mergePatch) {
        PatchUtil.mergeRfc7386(this, mergePatch);
    }



}