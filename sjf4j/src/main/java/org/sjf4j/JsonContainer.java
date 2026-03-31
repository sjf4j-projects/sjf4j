package org.sjf4j;

import org.sjf4j.node.Nodes;
import org.sjf4j.patch.JsonPatch;
import org.sjf4j.path.JsonPath;
import org.sjf4j.patch.Patches;
import org.sjf4j.path.PathSegment;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Base class for JSON containers ({@link JsonObject} and {@link JsonArray}).
 *
 * <p>Provides shared path-based accessors, conversion helpers, traversal, and
 * patch/merge utilities via {@link JsonPath}, {@link Nodes}, and {@link Patches}.
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

    /**
     * Compares this container with node-semantic equality.
     */
    public boolean nodeEquals(Object target) {
        return Nodes.equals(this, target);
    }

    /**
     * Returns a hash based on node semantics.
     */
    public int nodeHash() {
        return Nodes.hash(this);
    }

    /**
     * Returns a debug string using node inspection output.
     */
    public String inspect() {
        return Nodes.inspect(this);
    }


    /**
     * Removes null-valued object entries recursively.
     */
    public void deepPruneNulls() {
        Nodes.walk(this, Nodes.WalkTarget.CONTAINER, Nodes.WalkOrder.BOTTOM_UP,
                (path, node) -> {
                    if (node instanceof JsonObject) {
                        ((JsonObject) node).removeIf(e -> e.getValue() == null);
                    } else if (node instanceof Map) {
                        ((Map<?, ?>) node).entrySet().removeIf(e -> e.getValue() == null);
                    }
                    return true;
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
     * Returns true when the path exists and points to a non-null value.
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
    public String getAsStringByPath(String path) {
        return JsonPath.compile(path).getAsString(this);
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
    public Number getAsNumberByPath(String path) {
        return JsonPath.compile(path).getAsNumber(this);
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
    public Long getAsLongByPath(String path) {
        return JsonPath.compile(path).getAsLong(this);
    }

    // Integer

    /**
     * Gets the value at the specified JSON path as an Integer.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path as an Integer, or null if it doesn't exist or can't be converted
     */
    public Integer getIntByPath(String path) {
        return JsonPath.compile(path).getInt(this);
    }
    
    /**
     * Gets the value at the specified JSON path as an int primitive, with a default value.
     *
     * @param path the JSON path to get the value from
     * @param defaultValue the value to return if the path doesn't exist or can't be converted
     * @return the value at the path as an int, or the default value if it doesn't exist or can't be converted
     */
    public int getIntByPath(String path, int defaultValue) {
        return JsonPath.compile(path).getInt(this, defaultValue);
    }
    
    /**
     * Converts the value at the specified JSON path to an Integer.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to an Integer, or null if it doesn't exist
     */
    public Integer getAsIntByPath(String path) {
        return JsonPath.compile(path).getAsInt(this);
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
    public Short getAsShortByPath(String path) {
        return JsonPath.compile(path).getAsShort(this);
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
    public Byte getAsByteByPath(String path) {
        return JsonPath.compile(path).getAsByte(this);
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
    public Double getAsDoubleByPath(String path) {
        return JsonPath.compile(path).getAsDouble(this);
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
    public Float getAsFloatByPath(String path) {
        return JsonPath.compile(path).getAsFloat(this);
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
    public BigInteger getAsBigIntegerByPath(String path) {
        return JsonPath.compile(path).getAsBigInteger(this);
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
    public BigDecimal getAsBigDecimalByPath(String path) {
        return JsonPath.compile(path).getAsBigDecimal(this);
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
    public Boolean getAsBooleanByPath(String path) {
        return JsonPath.compile(path).getAsBoolean(this);
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
     * Converts the value at the specified JSON path to a Map.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a Map, or null if it doesn't exist
     */
    public <T> Map<String, T> asMapByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).getMap(this, clazz);
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
     * Converts the value at the specified JSON path to a List.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to a List, or null if it doesn't exist
     */
    public <T> List<T> getListByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).getList(this, clazz);
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
     * Converts the value at the specified JSON path to a Array.
     *
     * @param path the JSON path to get the value from
     * @return the value at the path converted to an Array, or null if it doesn't exist
     */
    public <T> T[] getArrayByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).getArray(this, clazz);
    }

    // Set
    /**
     * Returns a Set value by path using strict conversion.
     */
    public Set<Object> getSetByPath(String path) {
        return JsonPath.compile(path).getSet(this);
    }

    /**
     * Returns a typed Set value by path using strict conversion.
     */
    public <T> Set<T> getSetByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).getSet(this, clazz);
    }

    // Clazz

    /**
     * Returns a path value converted to the target type.
     */
    public <T> T getByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).get(this, clazz);
    }


    /**
     * Returns a path value converted to the inferred type.
     */
    @SafeVarargs
    public final <T> T getByPath(String path, T... reified) {
        return JsonPath.compile(path).get(this, reified);
    }

    /**
     * Returns a path value converted to target type leniently.
     */
    public <T> T getAsByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).getAs(this, clazz);
    }

    /**
     * Returns a path value converted to inferred type leniently.
     */
    @SafeVarargs
    public final <T> T getAsByPath(String path, T... reified) {
        return JsonPath.compile(path).getAs(this, reified);
    }
    
    // Put by path

    public Object putByPath(String path, Object value) {
        return JsonPath.compile(path).put(this, value);
    }

    /**
     * Ensures path containers exist and puts value.
     */
    public Object ensurePutByPath(String path, Object value) {
        return JsonPath.compile(path).ensurePut(this, value);
    }

    /**
     * Ensures path and puts value when absent.
     */
    public Object ensurePutIfAbsentByPath(String path, Object value) {
        return JsonPath.compile(path).ensurePutIfAbsent(this, value);
    }

    /**
     * Adds value at path using JSON Patch add semantics.
     */
    public void addByPath(String path, Object value) {
        JsonPath.compile(path).add(this, value);
    }

    /**
     * Replaces value at path using JSON Patch replace semantics.
     */
    public void replaceByPath(String path, Object value) {
        JsonPath.compile(path).replace(this, value);
    }

    /**
     * Removes value at path using JSON Patch remove semantics.
     */
    public void removeByPath(String path) {
        JsonPath.compile(path).remove(this);
    }

    /// Find

    /**
     * Finds all path matches.
     */
    public List<Object> findByPath(String path) {
        return JsonPath.compile(path).find(this);
    }

    /**
     * Finds all path matches converted to target type.
     */
    public <T> List<T> findByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).find(this, clazz);
    }

    /**
     * Finds all path matches converted leniently.
     */
    public <T> List<T> findAsByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).findAs(this, clazz);
    }

    /// Eval
    /**
     * Evaluates path and returns scalar or list result.
     */
    public Object evalByPath(String path) {
        return JsonPath.compile(path).eval(this);
    }

    /**
     * Evaluates path and converts result to target type.
     */
    public <T> T evalByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).eval(this, clazz);
    }

    /**
     * Evaluates path and converts result leniently.
     */
    public <T> T evalAsByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).evalAs(this, clazz);
    }


    /// Walk

    /**
     * Walks all nodes with default traversal options.
     */
    public void walk(BiFunction<PathSegment, Object, Boolean> visitor) {
        Nodes.walk(this, visitor);
    }

    /**
     * Walks nodes filtered by target kind.
     */
    public void walk(Nodes.WalkTarget target,
                     BiFunction<PathSegment, Object, Boolean> visitor) {
        Nodes.walk(this, target, visitor);
    }

    /**
     * Walks nodes with specified target and order.
     */
    public void walk(Nodes.WalkTarget target, Nodes.WalkOrder order,
                     BiFunction<PathSegment, Object, Boolean> visitor) {
        Nodes.walk(this, target, order, visitor);
    }

    /**
     * Walks nodes with specified target, order, and max depth.
     */
    public void walk(Nodes.WalkTarget target, Nodes.WalkOrder order, int maxDepth,
                     BiFunction<PathSegment, Object, Boolean> visitor) {
        Nodes.walk(this, target, order, maxDepth, visitor);
    }

    /// Patch

    /**
     * Applies a JSON Patch document to this container.
     */
    public void apply(JsonPatch patch) {
        patch.apply(this);
    }

    /**
     * Merges patch into this container using custom deep-merge semantics (not RFC 7386).
     * overwrite controls replacement of existing non-null values; deepCopy controls copying of
     * composite values taken from patch.
     */
    public void merge(Object mergePatch, boolean overwrite, boolean deepCopy) {
        Patches.merge(this, mergePatch, overwrite, deepCopy);
    }

    /**
     * Merges patch with overwrite=true and deepCopy=false.
     */
    public void merge(Object mergePatch) {
        merge(mergePatch, true, false);
    }

    /**
     * Merges patch with overwrite=true and deepCopy=true.
     */
    public void mergeWithCopy(Object mergePatch) {
        merge(mergePatch, true, true);
    }

    /**
     * Applies RFC 7386 JSON Merge Patch semantics.
     * Use this when null means delete and arrays should be replaced as a whole.
     */
    public void mergeRfc7386(Object mergePatch) {
        Patches.mergeRfc7386(this, mergePatch);
    }



}
