package org.sjf4j;

import org.sjf4j.util.ContainerUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Abstract base class for JSON container types (JsonObject and JsonArray).
 * <p>
 * This class provides common functionality for working with JSON data structures,
 * including path-based access, type conversion methods, and traversal capabilities.
 * It serves as the foundation for both JSON object and array implementations.
 */
public abstract class JsonContainer {

    /**
     * Returns the size of the container.
     * For JsonObject, this returns the number of key-value pairs.
     * For JsonArray, this returns the number of elements.
     *
     * @return the size of the container
     */
    public abstract int size();

    /// By path

    /**
     * Checks if a value exists at the specified JSON path.
     *
     * @param path the JSON path to check
     * @return true if a non-null value exists at the path, false otherwise
     */
    public boolean containsByPath(String path) {
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
        return JsonPath.compile(path).findNode(this);
    }
    
    /**
     * Gets the value at the specified JSON path as an Object, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path, or the default value if it doesn't exist
     */
    public Object getNodeByPath(String path, Object defaultValue) {
        return JsonPath.compile(path).findNode(this, defaultValue);
    }

    // String

    /**
     * Gets the value at the specified JSON path as a String.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a String, or null if it doesn't exist or can't be converted
     */
    public String getStringByPath(String path) {
        return JsonPath.compile(path).findString(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a String, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a String, or the default value if it doesn't exist or can't be converted
     */
    public String getStringByPath(String path, String defaultValue) {
        return JsonPath.compile(path).findString(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a String.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a String, or null if it doesn't exist
     */
    public String asStringByPath(String path) {
        return JsonPath.compile(path).findAsString(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a String, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a String, or the default value if it doesn't exist
     */
    public String asStringByPath(String path, String defaultValue) {
        return JsonPath.compile(path).findAsString(this, defaultValue);
    }

    // Long

    /**
     * Gets the value at the specified JSON path as a Long.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Long, or null if it doesn't exist or can't be converted
     */
    public Long getLongByPath(String path) {
        return JsonPath.compile(path).findLong(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a long primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a long, or the default value if it doesn't exist or can't be converted
     */
    public long getLongByPath(String path, long defaultValue) {
        return JsonPath.compile(path).findLong(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a Long.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Long, or null if it doesn't exist
     */
    public Long asLongByPath(String path) {
        return JsonPath.compile(path).findAsLong(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a long primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a long, or the default value if it doesn't exist
     */
    public long asLongByPath(String path, long defaultValue) {
        return JsonPath.compile(path).findAsLong(this, defaultValue);
    }

    // Integer

    /**
     * Gets the value at the specified JSON path as an Integer.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as an Integer, or null if it doesn't exist or can't be converted
     */
    public Integer getIntegerByPath(String path) {
        return JsonPath.compile(path).findInteger(this);
    }
    
    /**
     * Gets the value at the specified JSON path as an int primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as an int, or the default value if it doesn't exist or can't be converted
     */
    public int getIntegerByPath(String path, int defaultValue) {
        return JsonPath.compile(path).findInteger(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to an Integer.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to an Integer, or null if it doesn't exist
     */
    public Integer asIntegerByPath(String path) {
        return JsonPath.compile(path).findAsInteger(this);
    }
    
    /**
     * Converts the value at the specified JSON path to an int primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to an int, or the default value if it doesn't exist
     */
    public int asIntegerByPath(String path, int defaultValue) {
        return JsonPath.compile(path).findAsInteger(this, defaultValue);
    }

    // Short

    /**
     * Gets the value at the specified JSON path as a Short.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Short, or null if it doesn't exist or can't be converted
     */
    public Short getShortByPath(String path) {
        return JsonPath.compile(path).findShort(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a short primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a short, or the default value if it doesn't exist or can't be converted
     */
    public short getShortByPath(String path, short defaultValue) {
        return JsonPath.compile(path).findShort(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a Short.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Short, or null if it doesn't exist
     */
    public Short asShortByPath(String path) {
        return JsonPath.compile(path).findAsShort(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a short primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a short, or the default value if it doesn't exist
     */
    public short asShortByPath(String path, short defaultValue) {
        return JsonPath.compile(path).findAsShort(this, defaultValue);
    }

    // Byte

    /**
     * Gets the value at the specified JSON path as a Byte.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Byte, or null if it doesn't exist or can't be converted
     */
    public Byte getByteByPath(String path) {
        return JsonPath.compile(path).findByte(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a byte primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a byte, or the default value if it doesn't exist or can't be converted
     */
    public byte getByteByPath(String path, byte defaultValue) {
        return JsonPath.compile(path).findByte(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a Byte.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Byte, or null if it doesn't exist
     */
    public Byte asByteByPath(String path) {
        return JsonPath.compile(path).findAsByte(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a byte primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a byte, or the default value if it doesn't exist
     */
    public byte asByteByPath(String path, byte defaultValue) {
        return JsonPath.compile(path).findAsByte(this, defaultValue);
    }

    // Double

    /**
     * Gets the value at the specified JSON path as a Double.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Double, or null if it doesn't exist or can't be converted
     */
    public Double getDoubleByPath(String path) {
        return JsonPath.compile(path).findDouble(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a double primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a double, or the default value if it doesn't exist or can't be converted
     */
    public double getDoubleByPath(String path, double defaultValue) {
        return JsonPath.compile(path).findDouble(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a Double.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Double, or null if it doesn't exist
     */
    public Double asDoubleByPath(String path) {
        return JsonPath.compile(path).findAsDouble(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a double primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a double, or the default value if it doesn't exist
     */
    public double asDoubleByPath(String path, double defaultValue) {
        return JsonPath.compile(path).findAsDouble(this, defaultValue);
    }

    // Float

    /**
     * Gets the value at the specified JSON path as a Float.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Float, or null if it doesn't exist or can't be converted
     */
    public Float getFloatByPath(String path) {
        return JsonPath.compile(path).findFloat(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a float primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a float, or the default value if it doesn't exist or can't be converted
     */
    public float getFloatByPath(String path, float defaultValue) {
        return JsonPath.compile(path).findFloat(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a Float.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Float, or null if it doesn't exist
     */
    public Float asFloatByPath(String path) {
        return JsonPath.compile(path).findAsFloat(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a float primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a float, or the default value if it doesn't exist
     */
    public float asFloatByPath(String path, float defaultValue) {
        return JsonPath.compile(path).findAsFloat(this, defaultValue);
    }

    // BigInteger

    /**
     * Gets the value at the specified JSON path as a BigInteger.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a BigInteger, or null if it doesn't exist or can't be converted
     */
    public BigInteger getBigIntegerByPath(String path) {
        return JsonPath.compile(path).findBigInteger(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a BigInteger, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a BigInteger, or the default value if it doesn't exist or can't be converted
     */
    public BigInteger getBigIntegerByPath(String path, BigInteger defaultValue) {
        return JsonPath.compile(path).findBigInteger(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a BigInteger.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a BigInteger, or null if it doesn't exist
     */
    public BigInteger asBigIntegerByPath(String path) {
        return JsonPath.compile(path).findAsBigInteger(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a BigInteger, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a BigInteger, or the default value if it doesn't exist
     */
    public BigInteger asBigIntegerByPath(String path, BigInteger defaultValue) {
        return JsonPath.compile(path).findAsBigInteger(this, defaultValue);
    }

    // BigDecimal

    /**
     * Gets the value at the specified JSON path as a BigDecimal.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a BigDecimal, or null if it doesn't exist or can't be converted
     */
    public BigDecimal getBigDecimalByPath(String path) {
        return JsonPath.compile(path).findBigDecimal(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a BigDecimal, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a BigDecimal, or the default value if it doesn't exist or can't be converted
     */
    public BigDecimal getBigDecimalByPath(String path, BigDecimal defaultValue) {
        return JsonPath.compile(path).findBigDecimal(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a BigDecimal.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a BigDecimal, or null if it doesn't exist
     */
    public BigDecimal asBigDecimalByPath(String path) {
        return JsonPath.compile(path).findAsBigDecimal(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a BigDecimal, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a BigDecimal, or the default value if it doesn't exist
     */
    public BigDecimal asBigDecimalByPath(String path, BigDecimal defaultValue) {
        return JsonPath.compile(path).findAsBigDecimal(this, defaultValue);
    }

    // Boolean

    /**
     * Gets the value at the specified JSON path as a Boolean.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a Boolean, or null if it doesn't exist or can't be converted
     */
    public Boolean getBooleanByPath(String path) {
        return JsonPath.compile(path).findBoolean(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a boolean primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a boolean, or the default value if it doesn't exist or can't be converted
     */
    public boolean getBooleanByPath(String path, boolean defaultValue) {
        return JsonPath.compile(path).findBoolean(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a Boolean.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Boolean, or null if it doesn't exist
     */
    public Boolean asBooleanByPath(String path) {
        return JsonPath.compile(path).findAsBoolean(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a boolean primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a boolean, or the default value if it doesn't exist
     */
    public boolean asBooleanByPath(String path, boolean defaultValue) {
        return JsonPath.compile(path).findAsBoolean(this, defaultValue);
    }

    // JsonObject

    /**
     * Gets the value at the specified JSON path as a JsonObject.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a JsonObject, or null if it doesn't exist or can't be converted
     */
    public JsonObject getJsonObjectByPath(String path) {
        return JsonPath.compile(path).findJsonObject(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a JsonObject, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a JsonObject, or the default value if it doesn't exist or can't be converted
     */
    public JsonObject getJsonObjectByPath(String path, JsonObject defaultValue) {
        return JsonPath.compile(path).findJsonObject(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a JsonObject.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a JsonObject, or null if it doesn't exist
     */
    public JsonObject asJsonObjectByPath(String path) {
        return JsonPath.compile(path).findAsJsonObject(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a JsonObject, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a JsonObject, or the default value if it doesn't exist
     */
    public JsonObject asJsonObjectByPath(String path, JsonObject defaultValue) {
        return JsonPath.compile(path).findAsJsonObject(this, defaultValue);
    }

    // JsonArray

    /**
     * Gets the value at the specified JSON path as a JsonArray.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as a JsonArray, or null if it doesn't exist or can't be converted
     */
    public JsonArray getJsonArrayByPath(String path) {
        return JsonPath.compile(path).findJsonArray(this);
    }
    
    /**
     * Gets the value at the specified JSON path as a JsonArray, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as a JsonArray, or the default value if it doesn't exist or can't be converted
     */
    public JsonArray getJsonArrayByPath(String path, JsonArray defaultValue) {
        return JsonPath.compile(path).findJsonArray(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to a JsonArray.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a JsonArray, or null if it doesn't exist
     */
    public JsonArray asJsonArrayByPath(String path) {
        return JsonPath.compile(path).findAsJsonArray(this);
    }
    
    /**
     * Converts the value at the specified JSON path to a JsonArray, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist
     * @return the value at the path converted to a JsonArray, or the default value if it doesn't exist
     */
    public JsonArray asJsonArrayByPath(String path, JsonArray defaultValue) {
        return JsonPath.compile(path).findAsJsonArray(this, defaultValue);
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
        return JsonPath.compile(path).find(this, clazz);
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
        return JsonPath.compile(path).find(this, reified);
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
        return JsonPath.compile(path).findAs(this, clazz);
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
        return JsonPath.compile(path).findAs(this, reified);
    }
    
    // Put by path

    /**
     * Puts a value at the specified JSON path, creating intermediate objects/arrays if needed.
     *
     * @param path the JSON path to put the value at
     * @param value the value to put
     */
    public void putByPath(String path, Object value) {
        JsonPath.compile(path).put(this, value);
    }

    /**
     * Puts a value at the specified JSON path only if the value is non-null,
     * creating intermediate objects/arrays if needed.
     *
     * @param path the JSON path to put the value at
     * @param value the value to put (must be non-null)
     */
    public void putNonNullByPath(String path, Object value) {
        JsonPath.compile(path).putNonNull(this, value);
    }

    /**
     * Puts a value at the specified JSON path only if no value exists at that path,
     * creating intermediate objects/arrays if needed.
     *
     * @param path the JSON path to put the value at
     * @param value the value to put
     */
    public void putIfAbsentByPath(String path, Object value) {
        JsonPath.compile(path).putIfAbsent(this, value);
    }

    /**
     * Removes the value at the specified JSON path.
     *
     * @param path the JSON path to remove the value from
     */
    public void removeByPath(String path) {
        JsonPath.compile(path).remove(this);
    }

    /// Find all

    public List<Object> findAllNodes(String path) {
        return JsonPath.compile(path).findAllNodes(this);
    }

    public <T> List<T> findAll(String path, Class<T> clazz) {
        return JsonPath.compile(path).findAll(this, clazz);
    }

    public <T> List<T> findAllAs(String path, Class<T> clazz) {
        return JsonPath.compile(path).findAllAs(this, clazz);
    }

    /// Walk

    /**
     * Traverses the JSON container using the specified visitor function.
     *
     * @param visitor the function to apply to each node during traversal
     */
    public void walk(BiFunction<JsonPath, Object, JsonWalker.Control> visitor) {
        JsonWalker.walk(this, visitor);
    }

    /**
     * Traverses the JSON container using the specified visitor function and target.
     *
     * @param target the target nodes to visit (objects, arrays, values, or all)
     * @param visitor the function to apply to each node during traversal
     */
    public void walk(JsonWalker.Target target,
                     BiFunction<JsonPath, Object, JsonWalker.Control> visitor) {
        JsonWalker.walk(this, target, visitor);
    }

    /**
     * Traverses the JSON container using the specified visitor function, target, and order.
     *
     * @param target the target nodes to visit (objects, arrays, values, or all)
     * @param order the traversal order (depth-first or breadth-first)
     * @param visitor the function to apply to each node during traversal
     */
    public void walk(JsonWalker.Target target, JsonWalker.Order order,
                     BiFunction<JsonPath, Object, JsonWalker.Control> visitor) {
        JsonWalker.walk(this, target, order, visitor);
    }

    /**
     * Traverses the JSON container using the specified visitor function, target, order, and maximum depth.
     *
     * @param target the target nodes to visit (objects, arrays, values, or all)
     * @param order the traversal order (depth-first or breadth-first)
     * @param maxDepth the maximum depth to traverse
     * @param visitor the function to apply to each node during traversal
     */
    public void walk(JsonWalker.Target target, JsonWalker.Order order, int maxDepth,
                     BiFunction<JsonPath, Object, JsonWalker.Control> visitor) {
        JsonWalker.walk(this, target, order, maxDepth, visitor);
    }

    /// Base

    /**
     * Compares this container to another object for equality.
     * Uses {@link ContainerUtil#equals(Object, Object)} to perform deep equality comparison.
     *
     * @param target the object to compare to
     * @return true if the objects are deeply equal, false otherwise
     */
    @SuppressWarnings("EqualsDoesntCheckParameterClass")
    @Override
    public boolean equals(Object target) {
        return ContainerUtil.equals(this, target);
    }

    /**
     * Returns a string representation of this container for debugging purposes.
     * Uses {@link ContainerUtil#inspect(Object)} to generate the string.
     *
     * @return a debug string representation of the container
     */
    public String inspect() {
        return ContainerUtil.inspect(this);
    }

}