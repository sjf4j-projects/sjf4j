package org.sjf4j.path;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.Types;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Represents a JSON path that provides functionality for parsing and evaluating
 * JSON path expressions. JsonPath supports both JSON Path syntax (starting with '$')
 * and JSON Pointer syntax (starting with '/'), allowing navigation and manipulation
 * of JSON data structures.
 *
 * <p>This class provides methods for:
 * <ul>
 *   <li>Compiling path expressions into executable path objects</li>
 *   <li>Finding values in JSON containers by path</li>
 *   <li>Putting values into JSON containers at specific paths</li>
 *   <li>Removing values from JSON containers at specific paths</li>
 *   <li>Converting between different path expression formats</li>
 * </ul>
 */
public class JsonPath {

    /**
     * The raw path expression string.
     */
    private final String raw;

    private final PathSegment[] segments;

    /**
     * Creates a copy of an existing JsonPath instance.
     *
     * @param target the JsonPath to copy
     */
    protected JsonPath(JsonPath target) {
        this.raw = target.raw;
        this.segments = new PathSegment[target.segments.length];
        System.arraycopy(target.segments, 0, segments, 0, segments.length);
    }

    protected JsonPath(String raw, PathSegment[] segments) {
        this.raw = raw;
        this.segments = segments;
    }

    public static JsonPath compile(String expr) {
        Objects.requireNonNull(expr, "expr is null");
        expr = expr.trim();

        PathSegment[] segments;
        if (expr.isEmpty()) {
            segments = new PathSegment[]{PathSegment.Root.INSTANCE};
        } else if (expr.startsWith("/")) {
            segments = Paths.parsePointer(expr);
        } else if (expr.startsWith("$") || expr.startsWith("@")) {
            segments = Paths.parsePath(expr);
        } else {
            segments = Paths.parsePath(expr);
//            throw new JsonException("Invalid path expression '" + expr + "'. " +
//                    "Must start with '$' or '@' for JSON Path, or '/' for JSON Pointer.");
        }
        return new JsonPath(expr, segments);
    }


    public static JsonPath fromLast(PathSegment lastSegment) {
        Objects.requireNonNull(lastSegment, "lastSegment is null");
        PathSegment[] segments = Paths.linearize(lastSegment);
        return new JsonPath(null, segments);
    }

    /**
     * Converts the path tokens back to a JSON Path expression string.
     *
     * @return the JSON Path expression string
     */
    public String toExpr() {
        return Paths.toPathExpr(segments);
    }

    /**
     * Converts the path tokens to a JSON Pointer expression string.
     *
     * @return the JSON Pointer expression string
     */
    public String toPointerExpr() {
        return Paths.toPointerExpr(segments);
    }

    /**
     * Returns the depth of the path (number of tokens).
     *
     * @return the depth of the path
     */
    public int depth() {
        return segments.length;
    }

    /**
     * Returns the string representation of the path.
     *
     * @return the raw path expression if available, otherwise the generated expression
     */
    @Override
    public String toString() {
        return raw == null ? toExpr() : raw;
    }

    /**
     * Creates a copy of this JsonPath instance.
     *
     * @return a new JsonPath instance with the same tokens
     */
    public JsonPath copy() {
        return new JsonPath(this);
    }

    /**
     * Returns the first token in the path without removing it.
     *
     * @return the first path token
     */
    public PathSegment head() {
        return segments.length > 0 ? segments[0] : null;
    }

//    /**
//     * Returns the last token in the path without removing it.
//     *
//     * @return the last path token
//     */
//    public PathSegment tail() {
//        return tokens.get(tokens.size() - 1);
//    }
//
//    /**
//     * Pushes a new path token to the end of the path.
//     *
//     * @param token the token to add
//     * @throws IllegalArgumentException if token is null
//     */
//    public void push(PathSegment token) {
//        Objects.requireNonNull(token, "token is null");
//        tokens.add(token);
//    }
//
//    /**
//     * Removes the last token in the path, and returns it.
//     *
//     * @return the removed path token
//     */
//    public PathSegment pop() {
//        return tokens.remove(tokens.size() - 1);
//    }

    /// Find

    /**
     * Finds an object at this path in the given container.
     *
     * @param container the JSON container to search
     * @return the object found at the path, or null if not found
     * @throws IllegalArgumentException if container is null
     */
    public Object getNode(Object container) {
        Objects.requireNonNull(container, "container is null");
        return _findOne(container, 0);
    }

    /**
     * Finds an object at this path in the given container, or returns a default value if not found.
     *
     * @param container the JSON container to search
     * @param defaultValue the value to return if the path doesn't exist
     * @return the object found at the path, or the default value if not found
     */
    public Object getNode(Object container, Object defaultValue) {
        Object value = getNode(container);
        return null == value ? defaultValue : value;
    }

    // String
    public String getString(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get String by path '" + this + "'", e);
        }
    }
    public String getString(Object container, String defaultValue) {
        String value = getString(container);
        return value == null ? defaultValue : value;
    }

    public String getAsString(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.asString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to String", e);
        }
    }
    public String getAsString(Object container, String defaultValue) {
        String value = getAsString(container);
        return value == null ? defaultValue : value;
    }

    // Number
    public Number getNumber(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toNumber(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Number by path '" + this + "'", e);
        }
    }
    public Number getNumber(Object container, Number defaultValue) {
        Number value = getNumber(container);
        return value == null ? defaultValue : value;
    }

    public Number getAsNumber(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.asNumber(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Number", e);
        }
    }
    public Number getAsNumber(Object container, Number defaultValue) {
        Number value = getAsNumber(container);
        return value == null ? defaultValue : value;
    }

    // Long
    public Long getLong(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Long by path '" + this + "'", e);
        }
    }
    public long getLong(Object container, long defaultValue) {
        Long value = getLong(container);
        return value == null ? defaultValue : value;
    }

    public Long getAsLong(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.asLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Long", e);
        }
    }
    public long getAsLong(Object container, long defaultValue) {
        Long value = getAsLong(container);
        return value == null ? defaultValue : value;
    }

    // Integer
    public Integer getInteger(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Integer by path '" + this + "'", e);
        }
    }
    public int getInteger(Object container, int defaultValue) {
        Integer value = getInteger(container);
        return value == null ? defaultValue : value;
    }

    public Integer getAsInteger(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.asInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Integer", e);
        }
    }
    public int getAsInteger(Object container, int defaultValue) {
        Integer value = getAsInteger(container);
        return value == null ? defaultValue : value;
    }

    // Short
    public Short getShort(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Short by path '" + this + "'", e);
        }
    }
    public short getShort(Object container, short defaultValue) {
        Short value = getShort(container);
        return value == null ? defaultValue : value;
    }

    public Short getAsShort(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.asShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Short", e);
        }
    }
    public short getAsShort(Object container, short defaultValue) {
        Short value = getAsShort(container);
        return value == null ? defaultValue : value;
    }

    // Byte
    public Byte getByte(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Byte by path '" + this + "'", e);
        }
    }
    public byte getByte(Object container, byte defaultValue) {
        Byte value = getByte(container);
        return value == null ? defaultValue : value;
    }

    public Byte getAsByte(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.asByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Byte", e);
        }
    }
    public byte getAsByte(Object container, byte defaultValue) {
        Byte value = getAsByte(container);
        return value == null ? defaultValue : value;
    }

    // Double
    public Double getDouble(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Double by path '" + this + "'", e);
        }
    }
    public double getDouble(Object container, double defaultValue) {
        Double value = getDouble(container);
        return value == null ? defaultValue : value;
    }

    public Double getAsDouble(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.asDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Double", e);
        }
    }
    public double getAsDouble(Object container, double defaultValue) {
        Double value = getAsDouble(container);
        return value == null ? defaultValue : value;
    }

    // Float
    public Float getFloat(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Float by path '" + this + "'", e);
        }
    }
    public float getFloat(Object container, float defaultValue) {
        Float value = getFloat(container);
        return value == null ? defaultValue : value;
    }

    public Float getAsFloat(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.asFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Float", e);
        }
    }
    public float getAsFloat(Object container, float defaultValue) {
        Float value = getAsFloat(container);
        return value == null ? defaultValue : value;
    }

    // BigInteger
    public BigInteger getBigInteger(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigInteger by path '" + this + "'", e);
        }
    }
    public BigInteger getBigInteger(Object container, BigInteger defaultValue) {
        BigInteger value = getBigInteger(container);
        return value == null ? defaultValue : value;
    }

    public BigInteger getAsBigInteger(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.asBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to BigInteger", e);
        }
    }
    public BigInteger getAsBigInteger(Object container, BigInteger defaultValue) {
        BigInteger value = getAsBigInteger(container);
        return value == null ? defaultValue : value;
    }

    // BigDecimal
    public BigDecimal getBigDecimal(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigDecimal by path '" + this + "'", e);
        }
    }
    public BigDecimal getBigDecimal(Object container, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(container);
        return value == null ? defaultValue : value;
    }

    public BigDecimal getAsBigDecimal(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.asBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to BigDecimal", e);
        }
    }
    public BigDecimal getAsBigDecimal(Object container, BigDecimal defaultValue) {
        BigDecimal value = getAsBigDecimal(container);
        return value == null ? defaultValue : value;
    }

    // Boolean
    public Boolean getBoolean(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Boolean by path '" + this + "'", e);
        }
    }
    public boolean getBoolean(Object container, boolean defaultValue) {
        Boolean value = getBoolean(container);
        return value == null ? defaultValue : value;
    }

    public Boolean getAsBoolean(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.asBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Boolean", e);
        }
    }
    public boolean getAsBoolean(Object container, boolean defaultValue) {
        Boolean value = getAsBoolean(container);
        return value == null ? defaultValue : value;
    }

    // JsonObject
    public JsonObject getJsonObject(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonObject by path '" + this + "'", e);
        }
    }
    public JsonObject getJsonObject(Object container, JsonObject defaultValue) {
        JsonObject value = getJsonObject(container);
        return value == null ? defaultValue : value;
    }

    // Map
    public Map<String, Object> getMap(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toMap(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Map<String, Object> by path '" + this + "'", e);
        }
    }
    public Map<String, Object> getMap(Object container, Map<String, Object> defaultValue) {
        Map<String, Object> value = getMap(container);
        return value == null ? defaultValue : value;
    }

    public <T> Map<String, T> getMap(Object container, Class<T> clazz) {
        try {
            Object value = getNode(container);
            return Nodes.toMap(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Map<String, " +
                    clazz.getName() + ">", e);
        }
    }
    public <T> Map<String, T> getMap(Object container, Class<T> clazz, Map<String, T> defaultValue) {
        Map<String, T> value = getMap(container, clazz);
        return value == null ? defaultValue : value;
    }

    // JsonArray
    public JsonArray getJsonArray(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonArray by path '" + this + "'", e);
        }
    }
    public JsonArray getJsonArray(Object container, JsonArray defaultValue) {
        JsonArray value = getJsonArray(container);
        return value == null ? defaultValue : value;
    }

    // List
    public List<Object> getList(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toList(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get List<Object> by path '" + this + "'", e);
        }
    }
    public List<Object> getList(Object container, List<Object> defaultValue) {
        List<Object> value = getList(container);
        return value == null ? defaultValue : value;
    }

    public <T> List<T> getList(Object container, Class<T> clazz) {
        try {
            Object value = getNode(container);
            return Nodes.toList(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to List<" + clazz.getName() + ">", e);
        }
    }
    public <T> List<T> getList(Object container, Class<T> clazz, List<T> defaultValue) {
        List<T> value = getList(container, clazz);
        return value == null ? defaultValue : value;
    }

    // Array
    public Object[] getArray(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Object[] by path '" + this + "'", e);
        }
    }
    public Object[] getArray(Object container, Object[] defaultValue) {
        Object[] value = getArray(container);
        return value == null ? defaultValue : value;
    }

    public <T> T[] getArray(Object container, Class<T> clazz) {
        try {
            Object value = getNode(container);
            return Nodes.toArray(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to " + clazz.getName() + "[]", e);
        }
    }
    public <T> T[] getArray(Object container, Class<T> clazz, T[] defaultValue) {
        T[] value = getArray(container, clazz);
        return value == null ? defaultValue : value;
    }

    // Set
    public Set<Object> getSet(Object container) {
        try {
            Object value = getNode(container);
            return Nodes.toSet(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Set<Object> by path '" + this + "'", e);
        }
    }
    public Set<Object> getSet(Object container, Set<Object> defaultValue) {
        Set<Object> value = getSet(container);
        return value == null ? defaultValue : value;
    }

    public <T> Set<T> getSet(Object container, Class<T> clazz) {
        try {
            Object value = getNode(container);
            return Nodes.toSet(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Set<" + clazz.getName() + ">", e);
        }
    }
    public <T> Set<T> getSet(Object container, Class<T> clazz, Set<T> defaultValue) {
        Set<T> value = getSet(container, clazz);
        return value == null ? defaultValue : value;
    }

    
    public <T> T get(Object container, Class<T> clazz) {
        try {
            Object value = getNode(container);
            return Nodes.to(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to get " + clazz.getName() + " by path '" + this + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Object container, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(container, clazz);
    }

    public <T> T getAs(Object container, Class<T> clazz) {
        try {
            Object value = getNode(container);
            return Nodes.as(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to " + clazz.getName(), e);
        }
    }
    @SuppressWarnings("unchecked")
    public <T> T getAs(Object container, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return getAs(container, clazz);
    }


    /// All
    public List<Object> find(Object container) {
        Objects.requireNonNull(container, "container is null");
        List<Object> result = new ArrayList<>();
        _findAll(container, container, 1, result, (n) -> n);
        return result;
    }

    public <T> List<T> find(Object container, Class<T> clazz) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(clazz, "clazz is null");
        List<T> result = new ArrayList<>();
        _findAll(container, container, 1, result, (n) -> Nodes.to(n, clazz));
        return result;
    }

    public <T> List<T> findAs(Object container, Class<T> clazz) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(clazz, "clazz is null");
        List<T> result = new ArrayList<>();
        _findAll(container, container, 1, result, (n) -> Nodes.as(n, clazz));
        return result;
    }

    // eval() is more powerful than get() / find()
    public Object eval(Object container) {
        Objects.requireNonNull(container, "container is null");
        List<Object> result = new ArrayList<>();
        _findAll(container, container, 1, result, (n) -> n);
        if (result.isEmpty()) return null;

        PathSegment tk = segments[segments.length - 1];
        if (tk instanceof PathSegment.Function) {
            PathSegment.Function func = (PathSegment.Function) tk;
            Object[] args = new Object[1 + func.args.size()];
            args[0] = (result.size() == 1 ? result.get(0) : result);
            for (int i = 0; i < func.args.size(); i++) {
                args[1 + i] = _resolveFunctionArg(func.args.get(i));
            }
            return PathFunctionRegistry.invoke(func.name, args);
        } else {
            if (result.size() == 1) return result.get(0);
            else return result;
        }
    }

    public <T> T eval(Object container, Class<T> clazz) {
        try {
            Object value = eval(container);
            return Nodes.to(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to eval " + clazz.getName() + " by path '" + this + "'", e);
        }
    }

    public <T> T evalAs(Object container, Class<T> clazz) {
        try {
            Object value = eval(container);
            return Nodes.as(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert eval-value at path '" + this + "' to " + clazz.getName(), e);
        }
    }

    /// ensurePut

    public Object ensurePut(Object container, Object value) {
        Objects.requireNonNull(container, "container is null");
        Object lastContainer = _ensureContainersInPath(container);
        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            return Nodes.putInObject(lastContainer, name, value);
        } else if (lastToken instanceof PathSegment.Index) {
            int idx = ((PathSegment.Index) lastToken).index;
            Nodes.setInArray(lastContainer, idx, value);
            return null; // No need return old value in List/JsonArray
        } else {
            throw new JsonException("ensurePut() not supported for last path token '" + lastToken +
                    "'; expected Name or Index token");
        }
    }

    public Object ensurePutNonNull(Object container, Object value) {
        if (null != value) {
            return ensurePut(container, value);
        }
        return getNode(container);
    }

    public Object ensurePutIfAbsent(Object container, Object value) {
        Object old = getNode(container);
        if (old == null) {
            return ensurePut(container, value);
        }
        return old;
    }

    @SuppressWarnings("unchecked")
    public <T> T ensureComputeIfAbsent(Object container, Function<JsonPath, T> computer) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(computer, "computer is null");
        T old = get(container);
        if (old == null) {
            T newNode = computer.apply(this);
            if (newNode != null) {
                ensurePut(container, newNode);
                return newNode;
            }
        }
        return old;
    }

    /// has

    public boolean hasNonNull(Object container) {
        return getNode(container) != null;
    }

    public boolean contains(Object container) {
        Objects.requireNonNull(container, "container is null");
        Object penult = _findOne(container, -1);
        if (penult == null) return false;
        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            return Nodes.containsInObject(penult, name);
        } else if (lastToken instanceof PathSegment.Index) {
            int index = ((PathSegment.Index) lastToken).index;
            return Nodes.containsInArray(penult, index);
        } else if (lastToken instanceof PathSegment.Append) {
            return false;
        } else {
            throw new JsonException("contains() not supported for last path token '" + lastToken +
                    "'; expected Name, Index, or Append token");
        }
    }

    /// JSON Patch: add, replace, remove

    public void add(Object container, Object value) {
        Objects.requireNonNull(container, "container is null");
        Object penult = _findOne(container, -1);
        if  (penult == null)
            throw new JsonException("Parent container at the penultimate path token does not exist");

        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            Nodes.putInObject(penult, name, value);
        } else if (lastToken instanceof PathSegment.Index) {
            int idx = ((PathSegment.Index) lastToken).index;
            Nodes.addInArray(penult, idx, value);
        } else if (lastToken instanceof PathSegment.Append) {
            Nodes.addInArray(penult, value);
        } else {
            throw new JsonException("add() not supported for last path token '" + lastToken +
                    "'; expected Name, Index, or Append token");
        }
    }

    public Object replace(Object container, Object value) {
        Objects.requireNonNull(container, "container is null");
        Object penult = _findOne(container, -1);
        if  (penult == null)
            throw new JsonException("Parent container at the penultimate path token does not exist");

        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            return Nodes.putInObject(penult, name, value);
        } else if (lastToken instanceof PathSegment.Index) {
            int idx = ((PathSegment.Index) lastToken).index;
            return Nodes.setInArray(penult, idx, value);
        } else {
            throw new JsonException("add() not supported for last path token '" + lastToken +
                    "'; expected Name or Index token");
        }
    }

    public Object remove(Object container) {
        Objects.requireNonNull(container, "container is null");
        Object penult = _findOne(container, -1);
        if  (penult == null) return null;

        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            return Nodes.removeInObject(penult, name);
        } else if (lastToken instanceof PathSegment.Index) {
            int index = ((PathSegment.Index) lastToken).index;
            return Nodes.removeInArray(penult, index);
        } else if (lastToken instanceof PathSegment.Append) {
            return null;
        } else {
            throw new JsonException("remove() not supported for last path token '" + lastToken +
                    "'; expected Name, Index, or Append token");
        }
    }


    /// private

    private Object _findOne(Object container, int tailIndex) {
        Object node = container;
        for (int i = 1, len = segments.length + tailIndex; i < len; i++) {
            if (node == null) return null;
            PathSegment pt = segments[i];
            JsonType jt = JsonType.of(node);
            if (pt instanceof PathSegment.Name) {
                if (jt.isObject()) {
                    node = Nodes.getInObject(node, ((PathSegment.Name) pt).name);
                } else {
                    return null;
                }
            } else if (pt instanceof PathSegment.Index) {
                if (jt.isArray()) {
                    node = Nodes.getInArray(node, ((PathSegment.Index) pt).index);
                } else {
                    return null;
                }
            } else if (pt instanceof PathSegment.Descendant) {
                if (i + 1 >= segments.length) throw new JsonException("Descendant '..' cannot appear at the end.");
                List<Object> result = new ArrayList<>();
                _findMatch(container, node, i + 1, result, Function.identity());
                if (result.isEmpty()) {
                    return null;
                } else if (result.size() == 1) {
                    return result.get(0);
                } else {
                    throw new JsonException("Path matched " + result.size() +
                            " results, but this method can only returns one value.");
                }
            } else {
                throw new JsonException("Unsupported path token '" + pt + "'");
            }
        }
        return node;
    }


    // In the future, the `result` could be replaced with a callback to allow more flexible handling of matches.
    private <T> void _findAll(Object root, Object current, int tokenIdx,
                              List<T> result, Function<Object, T> converter) {
        Object node = current;
        for (int i = tokenIdx; i < segments.length; i++) {
            if (node ==  null) return;
            PathSegment pt = segments[i];
            if (i == segments.length - 1 && pt instanceof PathSegment.Function) break;
            JsonType jt = JsonType.of(node);
            final int nextI = i + 1;
            if (pt instanceof PathSegment.Name) {
                if (jt.isObject()) {
                    String name = ((PathSegment.Name) pt).name;
                    if (Nodes.containsInObject(node, name)) {
                        node = Nodes.getInObject(node, name);
                        continue;
                    }
                }
            } else if (pt instanceof PathSegment.Index) {
                if (jt.isArray()) {
                    node = Nodes.getInArray(node, ((PathSegment.Index) pt).index);
                    continue;
                }
            } else if (pt instanceof PathSegment.Wildcard) {
                if (jt.isObject()) {
                    Nodes.visitObject(node, (k, v) -> _findAll(root, v, nextI, result, converter));
                } else if (jt.isArray()) {
                    Nodes.visitArray(node, (j, v) -> _findAll(root, v, nextI, result, converter));
                }
            } else if (pt instanceof PathSegment.Descendant) {
                if (i + 1 >= segments.length) throw new JsonException("Descendant '..' cannot appear at the end.");
                _findMatch(root, node, i + 1, result, converter);
            } else if (pt instanceof PathSegment.Slice) {
                PathSegment.Slice slicePt = (PathSegment.Slice) pt;
                if (jt.isArray()) {
                    int size = Nodes.sizeInArray(node);
                    Nodes.visitArray(node, (j, v) -> {
                        if (slicePt.matchIndex(j, size)) _findAll(root, v, nextI, result, converter);
                    });
                }
            } else if (pt instanceof PathSegment.Union) {
                PathSegment.Union unionPt = (PathSegment.Union) pt;
                if (jt.isObject()) {
                    Nodes.visitObject(node, (k, v) -> {
                        if (unionPt.matchKey(k)) _findAll(root, v, nextI, result, converter);
                    });
                } else if (jt.isArray()) {
                    int size = Nodes.sizeInArray(node);
                    Nodes.visitArray(node, (j, v) -> {
                        if (unionPt.matchIndex(j, size)) _findAll(root, v, nextI, result, converter);
                    });
                }
            } else if (pt instanceof PathSegment.Filter) {
                PathSegment.Filter filterPt = (PathSegment.Filter) pt;
                if (jt.isArray()) {
                    Nodes.visitArray(node, (j, v) -> {
                        if (filterPt.filterExpr.evalTruth(root, v)) {
                            _findAll(root, v, nextI, result, converter);
                        }
                    });
                } else if (jt.isObject()) {
                    Nodes.visitObject(node, (k, v) -> {
                        if (filterPt.filterExpr.evalTruth(root, v)) {
                            _findAll(root, v, nextI, result, converter);
                        }
                    });
                } else {
                    if (filterPt.filterExpr.evalTruth(root, node)) {
                        continue;
                    }
                }
            } else {
                throw new JsonException("Unexpected path token '" + pt + "'");
            }
            return;
        }
        result.add(converter.apply(node));
    }

    private <T> void _findMatch(Object root, Object current, int tokenIdx, List<T> result, Function<Object, T> converter) {
        if (current == null) return;
        PathSegment pt = segments[tokenIdx];
        JsonType jt = JsonType.of(current);
        if (jt.isObject()) {
            Nodes.visitObject(current, (k, v) -> {
                if (pt.matchKey(k)) {
                    _findAll(root, v, tokenIdx + 1, result, converter);
                }
                _findMatch(root, v, tokenIdx, result, converter);
            });
        } else if (jt.isArray()) {
            int size = Nodes.sizeInArray(current);
            Nodes.visitArray(current, (j, v) -> {
                if (pt.matchIndex(j, size)) {
                    _findAll(root, v, tokenIdx + 1, result, converter);
                }
                _findMatch(root, v, tokenIdx, result, converter);
            });
        }
    }

    // 1. Automatically create or extends JsonObject nodes when they do not exist.
    // 2. Automatically create or extends JsonArray nodes when they do not exist and the index is 0.
    private Object _ensureContainersInPath(Object container) {
        if (!_isSingle()) {
            throw new JsonException("JsonPath '" + this + "' must represent a single node " +
                    "(only Name/Index tokens are allowed to automatically create containers in path.)");
        }

        Nodes.Access acc = new Nodes.Access();
        Object curNode = container;
        Type curType = container.getClass();
        for (int i = 1, len = segments.length - 1; i < len; i++) { // traverse up to the second-last token
            PathSegment ps = segments[i];
            JsonType jt = JsonType.of(curNode);
            if (ps instanceof PathSegment.Name) {
                String key = ((PathSegment.Name) ps).name;
                if (jt.isObject()) {
                    Nodes.accessInObject(curNode, curType, key, acc);
                    if (acc.node != null) {
                        curNode = acc.node;
                        curType = acc.type;
                    } else if (acc.insertable) {
                        PathSegment nextPt = segments[i + 1];
                        Object subNode = _createContainer(nextPt, Types.rawClazz(acc.type));
                        Nodes.putInObject(curNode, key, subNode);
                        curNode = subNode;
                        curType = acc.type;
                    } else {
                        throw new JsonException("Cannot put field '" + key + "' on an object node '" + curType + "'");
                    }
                } else {
                    throw new JsonException("Not an object node, but was '" + curType +
                            "' at '" + ps.rootedInspect() + "'");
                }
            } else if (ps instanceof PathSegment.Index) {
                int idx = ((PathSegment.Index) ps).index;
                if (jt.isArray()) {
                    Nodes.accessInArray(curNode, curType, idx, acc);
                    if (acc.node != null) {
                        curNode = acc.node;
                        curType = acc.type;
                    } else if (acc.insertable) {
                        PathSegment nextPt = segments[i + 1];
                        Object subNode = _createContainer(nextPt, Types.rawClazz(acc.type));
                        Nodes.setInArray(curNode, idx, subNode);
                        curNode = subNode;
                        curType = acc.type;
                    } else {
                        throw new JsonException("Cannot add or set index " + idx + " on an array node '" + curType + "'");
                    }
                } else {
                    throw new JsonException("Not an array node, but was '" + curType +
                            "' at '" + ps.rootedInspect() + "'");
                }
            } else {
                throw new JsonException("Unexpected path token '" + ps + "'");
            }
        }
        return curNode; // last container
    }


    private Object _createContainer(PathSegment ps, Class<?> clazz) {
        if (ps instanceof PathSegment.Name) {
            if (clazz == Object.class || clazz == Map.class) {
                return Sjf4jConfig.global().mapSupplier.create();
            }
            if (clazz == JsonObject.class) {
                return new JsonObject();
            }
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(clazz);
            if (pi != null) {
                return pi.creatorInfo.forceNewPojo();
            }
            throw new JsonException("Cannot create object node with type '" + clazz + "' at '" +
                    ps.rootedInspect() + "'. Only support Map/JsonObject/JOJO/POJO.");
        } else if (ps instanceof PathSegment.Index) {
            if (clazz == Object.class || clazz == List.class) {
                return Sjf4jConfig.global().listSupplier.create();
            }
            if (clazz == JsonArray.class) {
                return new JsonArray();
            }
            if (JsonArray.class.isAssignableFrom(clazz)) {
                return NodeRegistry.registerPojoOrElseThrow(clazz).creatorInfo.forceNewPojo();
            }
            if (clazz.isArray()) {
                int idx = ((PathSegment.Index) ps).index;
                return Array.newInstance(clazz.getComponentType(), idx + 1); // size = idx + 1
            }
            if (clazz == Set.class) {
                return Sjf4jConfig.global().setSupplier.create();
            }
            throw new JsonException("Cannot create array node with type '" + clazz +
                    "' at '" + ps.rootedInspect() + "'. Only support List/JsonArray/JAJO/Array/Set.");
        } else {
            throw new JsonException("Unexpected path token '" + ps + "'");
        }
    }

    private boolean _isSingle() {
        for (PathSegment pt : segments) {
            if (!(pt instanceof PathSegment.Root || pt instanceof PathSegment.Name || pt instanceof PathSegment.Index)) {
                return false;
            }
        }
        return true;
    }

    private Object _resolveFunctionArg(String raw) {
        if ((raw.startsWith("'") && raw.endsWith("'")) || (raw.startsWith("\"") && raw.endsWith("\""))) {
            return raw.substring(1, raw.length() - 1);
        } else if ("true".equals(raw)) {
            return true;
        } else if ("false".equals(raw)) {
            return false;
        } else if ("null".equals(raw)) {
            return null;
        } else if (Numbers.isNumeric(raw)) {
            return Numbers.parseNumber(raw);
        } else {
            throw new JsonException("Invalid raw argument '" + raw + "'");
        }
    }

}
