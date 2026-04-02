package org.sjf4j.path;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.Nodes;
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
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * JSONPath/JSON Pointer execution engine.
 *
 * <p>JsonPath compiles a textual path expression into a chain of {@link PathSegment}
 * tokens and then evaluates the path against a JSON container. It supports both:
 * <ul>
 *   <li>JSON Path syntax (starts with '$' or '@')</li>
 *   <li>JSON Pointer syntax (starts with '/')</li>
 * </ul>
 *
 * <p>Read operations return nodes or converted values using {@link Nodes} conversion
 * semantics. Write operations delegate to {@link Nodes} for object/array mutation,
 * and follow JSON Patch rules for add/replace/remove when used with pointer paths.
 */
public class JsonPath {

    /**
     * The raw path expression string.
     */
    protected final String raw;

    protected final PathSegment[] segments;

    protected final boolean single;

    /**
     * Creates a copy of an existing JsonPath instance.
     *
     * @param target the JsonPath to copy
     */
    protected JsonPath(JsonPath target) {
        this.raw = target.raw;
        this.segments = new PathSegment[target.segments.length];
        System.arraycopy(target.segments, 0, segments, 0, segments.length);
        this.single = target.single;
    }

    protected JsonPath(String raw, PathSegment[] segments) {
        this.raw = raw;
        this.segments = segments;

        boolean isSingle = true;
        for (PathSegment pt : segments) {
            if (!(pt instanceof PathSegment.Root || pt instanceof PathSegment.Name || pt instanceof PathSegment.Index)) {
                isSingle = false;
                break;
            }
        }
        this.single = isSingle;
    }

    /**
     * Compiles with global cache from {@link Sjf4jConfig#global()}.
     * <p>
     * Cache implementation is configurable via {@link Sjf4jConfig.Builder#pathCache(PathCache)}.
     */
    public static JsonPath compileCached(String expr) {
        return Sjf4jConfig.global().pathCache.getOrCompile(expr, JsonPath::compile);
    }

    /**
     * Compiles a JSONPath or JSON Pointer expression into executable segments.
     * <p>
     * Empty input resolves to root. Expressions starting with {@code /} are
     * parsed as JSON Pointer; others are parsed as JSONPath.
     */
    public static JsonPath compile(String expr) {
        Objects.requireNonNull(expr, "expr");
        expr = expr.trim();
        PathSegment[] segments;
        if (expr.isEmpty()) {
            segments = new PathSegment[]{PathSegment.Root.INSTANCE};
        } else if (expr.startsWith("/")) {
            segments = Paths.parsePointer(expr);
        } else {
            segments = Paths.parsePath(expr);
        }
        return new JsonPath(expr, segments);
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
     * Returns internal parsed segments for subclass extensions.
     */
    protected PathSegment[] segments() {
        return segments;
    }

    /**
     * Returns the first token in the path without removing it.
     *
     * @return the first path token
     */
    public PathSegment head() {
        return segments.length > 0 ? segments[0] : null;
    }

    /**
     * Returns true if the path uses only root/name/index tokens.
     */
    public boolean isSingle() {
        return single;
    }

    /// Find

    /**
     * Returns the node at this path, or {@code null} when any segment is missing.
     *
     * @param container the JSON container to search
     * @return the matched node, or {@code null} when unresolved
     * @throws IllegalArgumentException if container is null
     */
    public Object getNode(Object container) {
        Objects.requireNonNull(container, "container");
        return _findOne(container, 1, segments.length);
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

    private interface _PathAction<T> {
        T apply(Object value) throws Exception;
    }


    private <T> T _getStrict(Object container, String target, _PathAction<T> action) {
        Object value = null;
        try {
            value = getNode(container);
            return action.apply(value);
        } catch (Exception e) {
            throw new JsonException("get '" + target + "' failed: path='" + this + "', containerType='" +
                    Types.name(container) + "', valueType='" +  Types.name(value) + "'", e);
        }
    }

    private <T> T _getLenient(Object container, String target, _PathAction<T> action) {
        Object value = null;
        try {
            value = getNode(container);
            return action.apply(value);
        } catch (Exception e) {
            throw new JsonException("getAs '" + target + "' failed: path='" + this + "', containerType='" +
                    Types.name(container) + "', valueType='" +  Types.name(value) + "'", e);
        }
    }

    /**
     * Returns a String at this path using strict conversion.
     */
    public String getString(Object container) {
        return _getStrict(container, "String", Nodes::toString);
    }

    /**
     * Returns a String at this path or the default value when missing.
     */
    public String getString(Object container, String defaultValue) {
        String value = getString(container);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a String at this path using lenient conversion.
     */
    public String getAsString(Object container) {
        return _getLenient(container, "String", Nodes::asString);
    }

    /**
     * Returns a Number at this path using strict conversion.
     */
    public Number getNumber(Object container) {
        return _getStrict(container, "Number", Nodes::toNumber);
    }

    /**
     * Returns a Number at this path or the default value when missing.
     */
    public Number getNumber(Object container, Number defaultValue) {
        Number value = getNumber(container);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Number at this path using lenient conversion.
     */
    public Number getAsNumber(Object container) {
        return _getLenient(container, "Number", Nodes::asNumber);
    }

    /**
     * Returns a Long at this path using strict conversion.
     */
    public Long getLong(Object container) {
        return _getStrict(container, "Long", Nodes::toLong);
    }

    /**
     * Returns a Long at this path or the default value when missing.
     */
    public long getLong(Object container, long defaultValue) {
        Long value = getLong(container);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Long at this path using lenient conversion.
     */
    public Long getAsLong(Object container) {
        return _getLenient(container, "Long", Nodes::asLong);
    }

    /**
     * Returns an Integer at this path using strict conversion.
     */
    public Integer getInt(Object container) {
        return _getStrict(container, "Integer", Nodes::toInt);
    }

    /**
     * Returns an Integer at this path or the default value when missing.
     */
    public int getInt(Object container, int defaultValue) {
        Integer value = getInt(container);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns an Integer at this path using lenient conversion.
     */
    public Integer getAsInt(Object container) {
        return _getLenient(container, "Integer", Nodes::asInt);
    }

    /**
     * Returns a Short at this path using strict conversion.
     */
    public Short getShort(Object container) {
        return _getStrict(container, "Short", Nodes::toShort);
    }

    /**
     * Returns a Short at this path or the default value when missing.
     */
    public short getShort(Object container, short defaultValue) {
        Short value = getShort(container);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Short at this path using lenient conversion.
     */
    public Short getAsShort(Object container) {
        return _getLenient(container, "Short", Nodes::asShort);
    }

    /**
     * Returns a Byte at this path using strict conversion.
     */
    public Byte getByte(Object container) {
        return _getStrict(container, "Byte", Nodes::toByte);
    }

    /**
     * Returns a Byte at this path or the default value when missing.
     */
    public byte getByte(Object container, byte defaultValue) {
        Byte value = getByte(container);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Byte at this path using lenient conversion.
     */
    public Byte getAsByte(Object container) {
        return _getLenient(container, "Byte", Nodes::asByte);
    }

    /**
     * Returns a Double at this path using strict conversion.
     */
    public Double getDouble(Object container) {
        return _getStrict(container, "Double", Nodes::toDouble);
    }

    /**
     * Returns a Double at this path or the default value when missing.
     */
    public double getDouble(Object container, double defaultValue) {
        Double value = getDouble(container);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Double at this path using lenient conversion.
     */
    public Double getAsDouble(Object container) {
        return _getLenient(container, "Double", Nodes::asDouble);
    }

    /**
     * Returns a Float at this path using strict conversion.
     */
    public Float getFloat(Object container) {
        return _getStrict(container, "Float", Nodes::toFloat);
    }

    /**
     * Returns a Float at this path or the default value when missing.
     */
    public float getFloat(Object container, float defaultValue) {
        Float value = getFloat(container);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Float at this path using lenient conversion.
     */
    public Float getAsFloat(Object container) {
        return _getLenient(container, "Float", Nodes::asFloat);
    }

    /**
     * Returns a BigInteger at this path using strict conversion.
     */
    public BigInteger getBigInteger(Object container) {
        return _getStrict(container, "BigInteger", Nodes::toBigInteger);
    }

    /**
     * Returns a BigInteger at this path or the default value when missing.
     */
    public BigInteger getBigInteger(Object container, BigInteger defaultValue) {
        BigInteger value = getBigInteger(container);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a BigInteger at this path using lenient conversion.
     */
    public BigInteger getAsBigInteger(Object container) {
        return _getLenient(container, "BigInteger", Nodes::asBigInteger);
    }

    /**
     * Returns a BigDecimal at this path using strict conversion.
     */
    public BigDecimal getBigDecimal(Object container) {
        return _getStrict(container, "BigDecimal", Nodes::toBigDecimal);
    }

    /**
     * Returns a BigDecimal at this path or the default value when missing.
     */
    public BigDecimal getBigDecimal(Object container, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(container);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a BigDecimal at this path using lenient conversion.
     */
    public BigDecimal getAsBigDecimal(Object container) {
        return _getLenient(container, "BigDecimal", Nodes::asBigDecimal);
    }

    /**
     * Returns a Boolean at this path using strict conversion.
     */
    public Boolean getBoolean(Object container) {
        return _getStrict(container, "Boolean", Nodes::toBoolean);
    }

    /**
     * Returns a Boolean at this path or the default value when missing.
     */
    public boolean getBoolean(Object container, boolean defaultValue) {
        Boolean value = getBoolean(container);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Boolean at this path using lenient conversion.
     */
    public Boolean getAsBoolean(Object container) {
        return _getLenient(container, "Boolean", Nodes::asBoolean);
    }

    /**
     * Returns a JsonObject at this path using strict conversion.
     */
    public JsonObject getJsonObject(Object container) {
        return _getStrict(container, "JsonObject", Nodes::toJsonObject);
    }

    /**
     * Returns a Map at this path using strict conversion.
     */
    public Map<String, Object> getMap(Object container) {
        return _getStrict(container, "Map<String,Object>", Nodes::toMap);
    }

    /**
     * Returns a typed Map at this path using strict conversion.
     */
    public <T> Map<String, T> getMap(Object container, Class<T> clazz) {
        return _getLenient(container, "Map<String," + clazz.getName() + ">", (value) -> Nodes.toMap(value, clazz));
    }

    /**
     * Returns a JsonArray at this path using strict conversion.
     */
    public JsonArray getJsonArray(Object container) {
        return _getStrict(container, "JsonArray", Nodes::toJsonArray);
    }

    // List
    /**
     * Returns a List at this path using strict conversion.
     */
    public List<Object> getList(Object container) {
        return _getStrict(container, "List<Object>", Nodes::toList);
    }

    /**
     * Returns a typed List at this path using strict conversion.
     */
    public <T> List<T> getList(Object container, Class<T> clazz) {
        return _getLenient(container, "List<" + clazz.getName() + ">", (value) -> Nodes.toList(value, clazz));
    }

    /**
     * Returns an Object array at this path using strict conversion.
     */
    public Object[] getArray(Object container) {
        return _getStrict(container, "Object[]", Nodes::toArray);
    }

    /**
     * Returns a typed array at this path using strict conversion.
     */
    public <T> T[] getArray(Object container, Class<T> clazz) {
        return _getLenient(container, clazz.getName() + "[]", (value) -> Nodes.toArray(value, clazz));
    }

    /**
     * Returns a Set at this path using strict conversion.
     */
    public Set<Object> getSet(Object container) {
        return _getStrict(container, "Set<Object>", Nodes::toSet);
    }

    /**
     * Returns a typed Set at this path using strict conversion.
     */
    public <T> Set<T> getSet(Object container, Class<T> clazz) {
        return _getLenient(container, "Set<" + clazz.getName() + ">", (value) -> Nodes.toSet(value, clazz));
    }

    /**
     * Returns a value at this path converted to the given type.
     */
    public <T> T get(Object container, Class<T> clazz) {
        return _getStrict(container, clazz.getName(), (value) -> Nodes.to(value, clazz));
    }

    /**
     * Returns a value at this path converted to the inferred type parameter.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Object container, T... reified) {
        if (reified.length > 0) throw new JsonException("reified varargs must be empty");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(container, clazz);
    }

    /**
     * Returns a value at this path using lenient conversion.
     */
    public <T> T getAs(Object container, Class<T> clazz) {
        return _getLenient(container, clazz.getName(), (value) -> Nodes.as(value, clazz));
    }

    /**
     * Returns a value at this path using lenient conversion with inferred type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAs(Object container, T... reified) {
        if (reified.length > 0) throw new JsonException("reified varargs must be empty");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return getAs(container, clazz);
    }

    /// Find

    /**
     * Finds all matching nodes for this path.
     */
    public List<Object> find(Object container) {
        Objects.requireNonNull(container, "container");
        List<Object> result = new ArrayList<>();
        _findAll(container, container, 1, segments.length, result, Function.identity());
        return result;
    }

    /**
     * Finds and converts all matches using strict conversion.
     */
    public <T> List<T> find(Object container, Class<T> clazz) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(clazz, "clazz");
        List<T> result = new ArrayList<>();
        _findAll(container, container, 1, segments.length, result, (n) -> Nodes.to(n, clazz));
        return result;
    }

    /**
     * Finds and converts all matches using lenient conversion.
     */
    public <T> List<T> findAs(Object container, Class<T> clazz) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(clazz, "clazz");
        List<T> result = new ArrayList<>();
        _findAll(container, container, 1, segments.length, result, (n) -> Nodes.as(n, clazz));
        return result;
    }

    /// Eval

    /**
     * Evaluates the path and returns either a single value, a list of values,
     * or a function result.
     * <p>
     * When the last segment is a function token, the function is invoked with
     * the matched value(s) as first argument plus parsed literal arguments.
     */
    public Object eval(Object container) {
        Objects.requireNonNull(container, "container");
        List<Object> result = new ArrayList<>();
        _findAll(container, container, 1, segments.length, result, Function.identity());
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

    /**
     * Evaluates the path and converts the result using strict conversion.
     */
    public <T> T eval(Object container, Class<T> clazz) {
        Object value = null;
        try {
            value = eval(container);
            return Nodes.to(value, clazz);
        } catch (Exception e) {
            throw new JsonException("eval failed: path='" + this + "', clazz='" + clazz.getName() +
                    "', containerType='" +  Types.name(container) + "', valueType='" +  Types.name(value) + "'", e);
        }
    }

    /**
     * Evaluates the path and converts the result using lenient conversion.
     */
    public <T> T evalAs(Object container, Class<T> clazz) {
        Object value = null;
        try {
            value = eval(container);
            return Nodes.as(value, clazz);
        } catch (Exception e) {
            throw new JsonException("evalAs failed: path='" + this + "', clazz='" + clazz.getName() +
                    "', containerType='" +  Types.name(container) + "', valueType='" +  Types.name(value) + "'", e);
        }
    }

    /// Put

    public Object put(Object container, Object value) {
        Objects.requireNonNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if (lastContainer == null) {
            throw new JsonException("Cannot put value at path '" + this + "': parent container does not exist");
        }
        return _putLast(lastContainer, segments[segments.length - 1], value, "put()");
    }

    /**
     * Ensures intermediate containers exist and writes the value at the last
     * segment.
     * <p>
     * Auto-creation is only supported for single paths made of root/name/index
     * segments. Missing containers are created based on inferred static type.
     */
    public Object ensurePut(Object container, Object value) {
        Objects.requireNonNull(container, "container");
        Object lastContainer = _ensureContainersInPath(container);
        return _putLast(lastContainer, segments[segments.length - 1], value, "ensurePut()");
    }

    /**
     * Ensures the value exists; writes only when current value is null.
     */
    public Object ensurePutIfAbsent(Object container, Object value) {
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if (lastContainer == null) {
            return ensurePut(container, value);
        }
        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            if (!Nodes.containsInObject(lastContainer, name)) {
                return Nodes.putInObject(lastContainer, name, value);
            }
            return null;
        } else if (lastToken instanceof PathSegment.Index) {
            int idx = ((PathSegment.Index) lastToken).index;
            if (!Nodes.containsInArray(lastContainer, idx)) {
                Nodes.setInArray(lastContainer, idx, value);
            }
            return null; // No need return old value in List/JsonArray
        } else if (lastToken instanceof PathSegment.Append) {
            Nodes.addInArray(lastContainer, value);
            return null;
        } else {
            throw new JsonException("Unsupported last path token '" + lastToken +
                    "'; ensurePut() expected Name, Index, or Append token");
        }
    }

    /**
     * Recomputes and writes every matched target location.
     * <p>
     * Only already-matched parent containers are updated.
     *
     * <p>The callback receives only the current value. For access to the matched
     * parent container, use {@link #compute(Object, BiFunction)}.
     *
     * @return number of matched locations written
     */
    public int compute(Object container, Function<Object, Object> computer) {
        Objects.requireNonNull(computer, "computer");
        return compute(container, (parent, current) -> computer.apply(current));
    }

    /**
     * Recomputes and writes every matched target location with access to the
     * matched parent container and current value.
     * <p>
     * Only already-matched parent containers are updated.
     *
     * <p>The first callback argument is the matched parent container of the last
     * path segment. The second is the current value at that location, or
     * {@code null} for append targets.
     *
     * @return number of matched locations written
     */
    public int compute(Object container, BiFunction<Object, Object, Object> computer) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(computer, "computer");
        PathSegment lastToken = segments[segments.length - 1];
        if (!(lastToken instanceof PathSegment.Name || lastToken instanceof PathSegment.Index
                || lastToken instanceof PathSegment.Append)) {
            throw new JsonException("Unsupported last path token '" + lastToken +
                    "'; computeMulti() expected Name, Index, or Append token");
        }
        List<Object> parents = new ArrayList<Object>();
        _findAll(container, container, 1, segments.length - 1, parents, Function.identity());
        for (Object parent : parents) {
            _putLast(parent, lastToken, computer.apply(parent, _currentAt(parent, lastToken)), "compute()");
        }
        return parents.size();
    }

    /// has

    /**
     * Returns true when the node exists and is non-null.
     */
    public boolean hasNonNull(Object container) {
        return getNode(container) != null;
    }

    /**
     * Returns true if the final path location exists, regardless of stored value.
     * <p>
     * For object segments this checks key presence; for array segments it checks
     * index validity after negative-index normalization.
     */
    public boolean contains(Object container) {
        Objects.requireNonNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if (lastContainer == null) return false;
        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            return Nodes.containsInObject(lastContainer, name);
        } else if (lastToken instanceof PathSegment.Index) {
            int index = ((PathSegment.Index) lastToken).index;
            return Nodes.containsInArray(lastContainer, index);
        } else if (lastToken instanceof PathSegment.Append) {
            return false;
        } else {
            throw new JsonException("Unsupported last path token '" + lastToken +
                    "'; contains() expected Name, Index");
        }
    }

    /// JSON Patch: add, replace, remove

    /**
     * Applies JSON Patch {@code add} semantics at this pointer path.
     * <p>
     * Name targets upsert object fields. Index targets insert into arrays. Append
     * targets ({@code -}) append to arrays.
     */
    public void add(Object container, Object value) {
        Objects.requireNonNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if  (lastContainer == null)
            throw new JsonException("Cannot add value at path '" + this + "': parent container does not exist");

        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            Nodes.putInObject(lastContainer, name, value);
        } else if (lastToken instanceof PathSegment.Index) {
            int idx = ((PathSegment.Index) lastToken).index;
            Nodes.addInArray(lastContainer, idx, value);
        } else if (lastToken instanceof PathSegment.Append) {
            Nodes.addInArray(lastContainer, value);
        } else {
            throw new JsonException("Unsupported last path token '" + lastToken +
                    "'; add() expected Name, Index, or Append token");
        }
    }

    /**
     * Applies JSON Patch {@code replace} semantics (target must already exist).
     *
     * @return previous value at the replaced location
     */
    public Object replace(Object container, Object value) {
        Objects.requireNonNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if  (lastContainer == null) {
            throw new JsonException("Cannot replace value at path '" + this + "': parent container does not exist");
        }
        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            if (!Nodes.containsInObject(lastContainer, name)) {
                throw new JsonException("Cannot replace value at non-existent path '" + this + "'");
            }
            return Nodes.putInObject(lastContainer, name, value);
        } else if (lastToken instanceof PathSegment.Index) {
            int idx = ((PathSegment.Index) lastToken).index;
            if (!Nodes.containsInArray(lastContainer, idx)) {
                throw new JsonException("Cannot replace value at non-existent path '" + this + "'");
            }
            return Nodes.setInArray(lastContainer, idx, value);
        } else {
            throw new JsonException("Unsupported last path token '" + lastToken +
                    "'; replace() expected Name or Index token");
        }
    }

    /**
     * Applies JSON Patch {@code remove} semantics.
     *
     * @return removed value, or {@code null} when parent path is absent
     */
    public Object remove(Object container) {
        Objects.requireNonNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if  (lastContainer == null) return null;

        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            return Nodes.removeInObject(lastContainer, name);
        } else if (lastToken instanceof PathSegment.Index) {
            int index = ((PathSegment.Index) lastToken).index;
            return Nodes.removeInArray(lastContainer, index);
        } else {
            throw new JsonException("Unsupported last path token '" + lastToken +
                    "'; remove() expected Name or Index token");
        }
    }


    /// private

    /**
     * Finds a single match for the path (optionally stopping before the tail).
     */
    private Object _findOne(Object container, int startIdx, int endExclusive) {
        Object node = container;
        for (int i = startIdx; i < endExclusive; i++) {
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
                _findMatch(container, node, i + 1, endExclusive, result, Function.identity());
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

    /**
     * Walks the path and collects matches up to {@code endExclusive}.
     */
    private <T> void _findAll(Object root, Object current, int startIdx, int endExclusive,
                              List<T> result, Function<Object, T> converter) {
        Object node = current;
        for (int i = startIdx; i < endExclusive; i++) {
            if (node ==  null) return;
            PathSegment pt = segments[i];
            if (i == endExclusive - 1 && endExclusive == segments.length && pt instanceof PathSegment.Function) break;
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
                    Nodes.visitObject(node, (k, v) -> _findAll(root, v, nextI, endExclusive, result, converter));
                } else if (jt.isArray()) {
                    Nodes.visitArray(node, (j, v) -> _findAll(root, v, nextI, endExclusive, result, converter));
                }
            } else if (pt instanceof PathSegment.Descendant) {
                if (i + 1 >= segments.length) throw new JsonException("Descendant '..' cannot appear at the end.");
                _findMatch(root, node, i + 1, endExclusive, result, converter);
            } else if (pt instanceof PathSegment.Slice) {
                PathSegment.Slice slicePt = (PathSegment.Slice) pt;
                if (jt.isArray()) {
                    int size = Nodes.sizeInArray(node);
                    Nodes.visitArray(node, (j, v) -> {
                        if (slicePt.matchIndex(j, size)) _findAll(root, v, nextI, endExclusive, result, converter);
                    });
                }
            } else if (pt instanceof PathSegment.Union) {
                PathSegment.Union unionPt = (PathSegment.Union) pt;
                if (jt.isObject()) {
                    Nodes.visitObject(node, (k, v) -> {
                        if (unionPt.matchKey(k)) _findAll(root, v, nextI, endExclusive, result, converter);
                    });
                } else if (jt.isArray()) {
                    int size = Nodes.sizeInArray(node);
                    Nodes.visitArray(node, (j, v) -> {
                        if (unionPt.matchIndex(j, size)) _findAll(root, v, nextI, endExclusive, result, converter);
                    });
                }
            } else if (pt instanceof PathSegment.Filter) {
                PathSegment.Filter filterPt = (PathSegment.Filter) pt;
                if (jt.isArray()) {
                    Nodes.visitArray(node, (j, v) -> {
                        if (filterPt.filterExpr.evalTruth(root, v)) {
                            _findAll(root, v, nextI, endExclusive, result, converter);
                        }
                    });
                } else if (jt.isObject()) {
                    Nodes.visitObject(node, (k, v) -> {
                        if (filterPt.filterExpr.evalTruth(root, v)) {
                            _findAll(root, v, nextI, endExclusive, result, converter);
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

    /**
     * Recursively scans descendants and matches the next token up to {@code endExclusive}.
     */
    private <T> void _findMatch(Object root, Object current, int startIdx, int endExclusive,
                                List<T> result, Function<Object, T> converter) {
        if (current == null) return;
        PathSegment pt = segments[startIdx];
        JsonType jt = JsonType.of(current);
        if (jt.isObject()) {
            Nodes.visitObject(current, (k, v) -> {
                if (pt.matchKey(k)) {
                    if (startIdx >= endExclusive) {
                        result.add(converter.apply(current));
                    } else {
                        _findAll(root, v, startIdx + 1, endExclusive, result, converter);
                    }
                }
                _findMatch(root, v, startIdx, endExclusive, result, converter);
            });
        } else if (jt.isArray()) {
            int size = Nodes.sizeInArray(current);
            Nodes.visitArray(current, (j, v) -> {
                if (pt.matchIndex(j, size)) {
                    if (startIdx >= endExclusive) {
                        result.add(converter.apply(current));
                    } else {
                        _findAll(root, v, startIdx + 1, endExclusive, result, converter);
                    }
                }
                _findMatch(root, v, startIdx, endExclusive, result, converter);
            });
        }
    }

    private Object _putLast(Object lastContainer, PathSegment lastToken, Object value, String opName) {
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            return Nodes.putInObject(lastContainer, name, value);
        } else if (lastToken instanceof PathSegment.Index) {
            int idx = ((PathSegment.Index) lastToken).index;
            Nodes.setInArray(lastContainer, idx, value);
            return null;
        } else if (lastToken instanceof PathSegment.Append) {
            Nodes.addInArray(lastContainer, value);
            return null;
        } else {
            throw new JsonException("Unsupported last path token '" + lastToken +
                    "'; " + opName + " expected Name, Index, or Append token");
        }
    }

    private Object _currentAt(Object lastContainer, PathSegment lastToken) {
        if (lastToken instanceof PathSegment.Name) {
            return Nodes.getInObject(lastContainer, ((PathSegment.Name) lastToken).name);
        } else if (lastToken instanceof PathSegment.Index) {
            return Nodes.getInArray(lastContainer, ((PathSegment.Index) lastToken).index);
        } else if (lastToken instanceof PathSegment.Append) {
            return null;
        } else {
            throw new JsonException("Unsupported last path token '" + lastToken +
                    "'; _currentAt() expected Name, Index, or Append token");
        }
    }

    /**
     * Ensures intermediate containers exist for single-path traversal and
     * returns the parent container of the final segment.
     */
    private Object _ensureContainersInPath(Object container) {
        if (!isSingle()) {
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


    /**
     * Creates a missing container based on the next segment kind and declared
     * static type.
     */
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

    /**
     * Parses a literal argument for a path function.
     */
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
