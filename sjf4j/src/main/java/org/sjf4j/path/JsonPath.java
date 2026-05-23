package org.sjf4j.path;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.Types;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * JSONPath/JSON Pointer execution engine.
 *
 * <p>JsonPath parses a textual path expression into a chain of {@link PathSegment}
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

    private static final Object MISSING = new Object();

    /**
     * The raw path expression string.
     */
    protected final String raw;

    protected final PathSegment[] segments;

    protected final boolean singleGet;
    protected final boolean singlePut;
    protected final boolean singleEval;
    protected final boolean append;

    /**
     * Creates a copy of an existing JsonPath instance.
     *
     * @param target the JsonPath to copy
     */
    protected JsonPath(JsonPath target) {
        this.raw = target.raw;
        this.segments = new PathSegment[target.segments.length];
        System.arraycopy(target.segments, 0, segments, 0, segments.length);
        this.singleGet = target.singleGet;
        this.singlePut = target.singlePut;
        this.singleEval = target.singleEval;
        this.append = target.append;
    }

    protected JsonPath(String raw, PathSegment[] segments) {
        if (segments.length == 0) throw new JsonException("segments must not be empty");
        this.raw = raw;
        this.segments = segments;

        int len = segments.length;
        boolean isSingleGet = true;
        boolean isSinglePut = true;
        boolean isSingleEval = len > 1 && segments[len - 1] instanceof PathSegment.Function;
        boolean hasAppend = false;
        for (int i = 0; i < len; i++) {
            PathSegment ps = segments[i];
            if (!(ps instanceof PathSegment.Root || ps instanceof PathSegment.Name || ps instanceof PathSegment.Index)) {
                isSingleGet = false;
                boolean appendToken = ps instanceof PathSegment.Append;
                if (appendToken) {
                    hasAppend = true;
                } else {
                    isSinglePut = false;
                }
                if (i < len - 1) {
                    isSingleEval = false;
                }
            }
        }
        this.singleGet = isSingleGet;
        this.singlePut = isSinglePut;
        this.singleEval = isSingleEval;
        this.append = hasAppend;
    }

    /**
     * Parses a JSONPath or JSON Pointer expression into executable segments.
     * <p>
     * Empty input resolves to root. Expressions starting with {@code /} are
     * parsed as JSON Pointer; others are parsed as JSONPath.
     */
    public static JsonPath parse(String expr) {
        Objects.requireNonNull(expr, "expr");
        expr = expr.trim();
        PathSegment[] segments;
        if (expr.isEmpty()) {
            segments = new PathSegment[]{PathSegment.Root.INSTANCE};
        } else if (expr.startsWith("/")) {
            segments = PathSyntax.parsePointer(expr);
        } else {
            segments = PathSyntax.parsePath(expr);
        }
        return new JsonPath(expr, segments);
    }

    /**
     * Converts the path tokens back to a JSON Path expression string.
     *
     * @return the JSON Path expression string
     */
    public String toExpr() {
        return PathSyntax.toPathExpr(segments);
    }

    /**
     * Converts the path tokens to a JSON Pointer expression string.
     *
     * @return the JSON Pointer expression string
     */
    public String toPointerExpr() {
        return PathSyntax.toPointerExpr(segments);
    }

    /**
     * Returns the length of the path (number of tokens).
     *
     * @return the depth of the path
     */
    public int length() {
        return segments.length;
    }

    /**
     * Returns the parsed path segments in stored order.
     * <p>
     * The returned array is the internal segment array and is not copied.
     */
    public PathSegment[] segments() {
        return segments;
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
     * Returns whether this path starts from root segment.
     * <p>
     * This is true for JSONPath expressions like {@code $.a} and JSON Pointer
     * expressions like {@code /a}.
     */
    public boolean rooted() {
        return segments[0] instanceof PathSegment.Root;
    }

    /**
     * Returns the first non-root token in the path.
     * <p>
     * For a root-only path, returns {@code null}.
     *
     * @return the first segment after {@link PathSegment.Root}, or {@code null}
     */
    public PathSegment head() {
        return segments.length > 1 ? segments[1] : null;
    }

    /**
     * Returns the last token in the path.
     * <p>
     * For a root-only path, this returns {@link PathSegment.Root}.
     *
     * @return the terminal path segment
     */
    public PathSegment tail() {
        return segments[segments.length - 1];
    }

    /**
     * Returns whether this path addresses at most one concrete location.
     * <p>
     * This is true only when all segments are limited to Root, Name, Index,
     * and Append, with no wildcard, slice, filter, union, or recursive tokens.
     */
    public boolean isSinglePut() {
        return singlePut;
    }

    /**
     * Returns whether this path contains an append segment.
     * <p>
     * Append segments come from JSONPath {@code [+]} or JSON Pointer {@code /-}.
     */
    public boolean hasAppend() {
        return append;
    }

    /// Find

    /**
     * Returns the node at this path, or {@code null} when any segment is missing.
     *
     * @param container the JSON container to search
     * @return the matched node, or {@code null} when unresolved
     */
    public Object getNode(Object container) {
        if (container == null) return null;
        Object value = _findOne(container, 1, segments.length);
        return value == MISSING ? null : value;
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

    private <T> T _getStrict(Object container, String target, Function<Object, T> action) {
        Object value = null;
        try {
            value = getNode(container);
            return action.apply(value);
        } catch (Exception e) {
            throw new JsonException("cannot get " + target + " from path '" + this + "': container=" +
                    Types.name(container) + ", value=" + Types.name(value), e);
        }
    }

    private <T> T _getLenient(Object container, String target, Function<Object, T> action) {
        Object value = null;
        try {
            value = getNode(container);
            return action.apply(value);
        } catch (Exception e) {
            throw new JsonException("cannot coerce value at path '" + this + "' to " + target + ": container=" +
                    Types.name(container) + ", value=" + Types.name(value), e);
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
        return _getStrict(container, "Map<String," + clazz.getName() + ">", (value) -> Nodes.toMap(value, clazz));
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
        return _getStrict(container, "List<" + clazz.getName() + ">", (value) -> Nodes.toList(value, clazz));
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
        return _getStrict(container, clazz.getName() + "[]", (value) -> Nodes.toArray(value, clazz));
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
        return _getStrict(container, "Set<" + clazz.getName() + ">", (value) -> Nodes.toSet(value, clazz));
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
        if (singleGet) {
            List<Object> result = new ArrayList<>(1);
            Object value = _findOne(container, 1, segments.length);
            if (value != MISSING) result.add(value);
            return result;
        }
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
        if (singleGet) {
            List<T> result = new ArrayList<>(1);
            Object value = _findOne(container, 1, segments.length);
            if (value != MISSING) result.add(Nodes.to(value, clazz));
            return result;
        }
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
        if (singleGet) {
            List<T> result = new ArrayList<>(1);
            Object value = _findOne(container, 1, segments.length);
            if (value != MISSING) result.add(Nodes.as(value, clazz));
            return result;
        }
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
        PathSegment tk = segments[segments.length - 1];
        if (singleGet) {
            Object value = _findOne(container, 1, segments.length);
            return value == MISSING ? null : value;
        }
        if (singleEval) {
            Object value = _findOne(container, 1, segments.length - 1);
            if (value == MISSING) return null;
            PathSegment.Function func = (PathSegment.Function) tk;
            Object[] args = new Object[1 + func.args.size()];
            args[0] = value;
            for (int i = 0; i < func.args.size(); i++) {
                args[1 + i] = _resolveFunctionArg(func.args.get(i));
            }
            return FunctionRegistry.invoke(func.name, args);
        }
        List<Object> result = new ArrayList<>();
        _findAll(container, container, 1, segments.length, result, Function.identity());
        if (result.isEmpty()) return null;

        if (tk instanceof PathSegment.Function) {
            PathSegment.Function func = (PathSegment.Function) tk;
            Object[] args = new Object[1 + func.args.size()];
            args[0] = (result.size() == 1 ? result.get(0) : result);
            for (int i = 0; i < func.args.size(); i++) {
                args[1 + i] = _resolveFunctionArg(func.args.get(i));
            }
            return FunctionRegistry.invoke(func.name, args);
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
            throw new JsonException("cannot evaluate " + clazz.getName() + " from path '" + this + "': container=" +
                    Types.name(container) + ", value=" + Types.name(value), e);
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
            throw new JsonException("cannot coerce value at path '" + this + "' to " + clazz.getName() + ": container=" +
                    Types.name(container) + ", value=" + Types.name(value), e);
        }
    }

    /// Put

    /**
     * Writes the value at the final path location and returns the previous value
     * when the target shape exposes one.
     * <p>
     * The parent container of the final segment must already exist. Object-name
     * targets upsert. Array index targets write through
     * {@link Nodes#putInArray(Object, int, Object)}, which replaces existing
     * elements and appends when {@code idx == size}. Append targets write
     * through {@link Nodes#addInArray(Object, Object)}. POJO property writes
     * return {@code null} because they avoid reading the old value.
     *
     * @throws JsonException when the parent container does not exist or the last
     *                       segment cannot be written
     */
    public Object put(Object container, Object value) {
        Objects.requireNonNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if (lastContainer == MISSING || lastContainer == null) {
            throw new JsonException("cannot put value at path '" + this + "': parent container does not exist");
        }
        return _putLast(lastContainer, segments[segments.length - 1], value, "put()");
    }

    /**
     * Writes the value only when the parent container of the final path segment
     * already exists.
     * <p>
     * Missing parent containers return {@code null} without writing. Once the
     * parent exists, the final write follows the same last-segment rules as
     * {@link #put(Object, Object)}.
     *
     * @return the previous value when a write occurred and the target shape
     * exposes one, otherwise {@code null}
     */
    public Object putIfParentPresent(Object container, Object value) {
        Objects.requireNonNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if (lastContainer == MISSING || lastContainer == null) return null;
        return _putLast(lastContainer, segments[segments.length - 1], value, "putIfParentPresent()");
    }

    /**
     * Ensures intermediate containers exist and writes the value at the final
     * path location.
     * <p>
     * Auto-creation is only supported for single paths made of root/name/index/
     * append segments. Missing containers are created based on inferred static
     * type.
     * Once the parent container exists, the final write follows the same last-
     * segment rules as {@link #put(Object, Object)}.
     */
    public Object ensurePut(Object container, Object value) {
        Objects.requireNonNull(container, "container");
        Object lastContainer = _ensureContainersInPath(container);
        return _putLast(lastContainer, segments[segments.length - 1], value, "ensurePut()");
    }

    /**
     * Ensures the final path location exists, and writes only when the current
     * value is absent or {@code null}.
     * <p>
     * Missing parent containers are created using {@link #ensurePut(Object, Object)}.
     * For object-name and pointer-object-key targets, absent means the key is
     * missing or currently maps to {@code null}. For array-index targets, indexes
     * are normalized first; indexes greater than the current size fail, indexes
     * equal to the current size append, and existing indexes are replaced only
     * when their current value is {@code null}. Append targets always append.
     *
     * @return {@code null} when a write happened at an absent/null location, or
     * the existing non-null value when no write was performed
     */
    public Object ensurePutIfAbsent(Object container, Object value) {
        Objects.requireNonNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if (lastContainer == MISSING || lastContainer == null) {
            return ensurePut(container, value);
        }
        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            Object current = Nodes.getInObject(lastContainer, name);
            if (current == null) {
                return Nodes.putInObject(lastContainer, name, value);
            }
            return current;
        } else if (lastToken instanceof PathSegment.Index) {
            PathSegment.Index index = (PathSegment.Index) lastToken;
            if (_isPointerObjectKey(index, lastContainer)) {
                Object current = Nodes.getInObject(lastContainer, index.pointerToken);
                if (current == null) {
                    return Nodes.putInObject(lastContainer, index.pointerToken, value);
                }
                return current;
            }
            int size = Nodes.sizeInArray(lastContainer);
            int idx = index.index < 0 ? size + index.index : index.index;
            if (idx < 0 || idx > size) {
                throw new JsonException("cannot ensure-put-if-absent value at indexed path '" + this +
                        "': index " + index.index + " is out of bounds for array size " + size);
            }
            if (idx == size) {
                return Nodes.putInArray(lastContainer, idx, value);
            }
            Object current = Nodes.getInArray(lastContainer, idx);
            if (current == null) {
                return Nodes.putInArray(lastContainer, idx, value);
            }
            return current;
        } else if (lastToken instanceof PathSegment.Append) {
            Nodes.addInArray(lastContainer, value);
            return null;
        } else {
            throw new JsonException("unsupported last path token '" + lastToken +
                    "'; ensurePutIfAbsent() expected Name, Index, or Append token");
        }
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
            throw new JsonException("unsupported last path token '" + lastToken +
                    "'; compute() expected Name, Index, or Append token");
        }
        if (singlePut) {
            Object parent = _findOne(container, 1, segments.length - 1);
            if (parent == MISSING || parent == null) return 0;
            _computeLast(parent, lastToken, computer);
            return 1;
        }
        List<Object> parents = new ArrayList<>();
        _findAll(container, container, 1, segments.length - 1, parents, Function.identity());
        for (Object parent : parents) {
            _computeLast(parent, lastToken, computer);
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
        if (segments.length == 1 && segments[0] instanceof PathSegment.Root) return true;
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if (lastContainer == MISSING || lastContainer == null) return false;
        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            return Nodes.containsInObject(lastContainer, name);
        } else if (lastToken instanceof PathSegment.Index) {
            PathSegment.Index index = (PathSegment.Index) lastToken;
            if (_isPointerObjectKey(index, lastContainer)) {
                return Nodes.containsInObject(lastContainer, index.pointerToken);
            }
            return Nodes.containsInArray(lastContainer, index.index);
        } else if (lastToken instanceof PathSegment.Append) {
            return false;
        } else {
            throw new JsonException("unsupported last path token '" + lastToken +
                    "'; contains() expected Name or Index token");
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
        if  (lastContainer == MISSING || lastContainer == null)
            throw new JsonException("cannot add value at path '" + this + "': parent container does not exist");

        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            Nodes.putInObject(lastContainer, name, value);
        } else if (lastToken instanceof PathSegment.Index) {
            PathSegment.Index index = (PathSegment.Index) lastToken;
            if (_isPointerObjectKey(index, lastContainer)) {
                Nodes.putInObject(lastContainer, index.pointerToken, value);
            } else {
                Nodes.addInArray(lastContainer, index.index, value);
            }
        } else if (lastToken instanceof PathSegment.Append) {
            Nodes.addInArray(lastContainer, value);
        } else {
            throw new JsonException("unsupported last path token '" + lastToken +
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
        if  (lastContainer == MISSING || lastContainer == null) {
            throw new JsonException("cannot replace value at path '" + this + "': parent container does not exist");
        }
        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            if (!Nodes.containsInObject(lastContainer, name)) {
                throw new JsonException("cannot replace value at non-existent path '" + this + "'");
            }
            return Nodes.putInObject(lastContainer, name, value);
        } else if (lastToken instanceof PathSegment.Index) {
            PathSegment.Index index = (PathSegment.Index) lastToken;
            if (_isPointerObjectKey(index, lastContainer)) {
                if (!Nodes.containsInObject(lastContainer, index.pointerToken)) {
                    throw new JsonException("cannot replace value at non-existent path '" + this + "'");
                }
                return Nodes.putInObject(lastContainer, index.pointerToken, value);
            }
            if (!Nodes.containsInArray(lastContainer, index.index)) {
                throw new JsonException("cannot replace value at non-existent path '" + this + "'");
            }
            return Nodes.setInArray(lastContainer, index.index, value);
        } else {
            throw new JsonException("unsupported last path token '" + lastToken +
                    "'; replace() expected Name or Index token");
        }
    }

    /**
     * Removes the value at this path when the target exists.
     * <p>
     * Missing parent paths or object keys return {@code null}. Array removals
     * still follow array index rules and may fail for out-of-range indexes.
     *
     * @return removed value, or {@code null} when no value was removed
     */
    public Object removeIfPresent(Object container) {
        Objects.requireNonNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if  (lastContainer == MISSING || lastContainer == null) return null;

        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            return Nodes.removeInObject(lastContainer, name);
        } else if (lastToken instanceof PathSegment.Index) {
            PathSegment.Index index = (PathSegment.Index) lastToken;
            if (_isPointerObjectKey(index, lastContainer)) {
                return Nodes.removeInObject(lastContainer, index.pointerToken);
            }
            return Nodes.removeInArray(lastContainer, index.index);
        } else {
            throw new JsonException("unsupported last path token '" + lastToken +
                    "'; remove() expected Name or Index token");
        }
    }


    /// private

    private static boolean _isPointerObjectKey(PathSegment.Index index, Object container) {
        if (index.pointerToken == null) {
            return false;
        }
        if (container instanceof JsonObject || container instanceof Map) {
            return true;
        }
        if (container instanceof JsonArray || container instanceof List || container instanceof Set) {
            return false;
        }
        return !container.getClass().isArray() && JsonType.of(container).isObject();
    }

    private static boolean _isPointerObjectKey(PathSegment.Index index, JsonType containerType) {
        return index.pointerToken != null && containerType.isObject();
    }

    /**
     * Finds a single match for the path (optionally stopping before the tail).
     * <p>
     * Missing locations return the private {@link #MISSING} sentinel, while
     * existing locations whose value is {@code null} return {@code null}. Public
     * APIs that collapse missing and null (for example {@link #getNode(Object)})
     * translate {@code MISSING} back to {@code null}; query APIs such as
     * {@link #find(Object)} use the sentinel to omit missing locations while
     * preserving present-null matches.
     */
    private Object _findOne(Object container, int startIdx, int endExclusive) {
        Object node = container;
        for (int i = startIdx; i < endExclusive; i++) {
            if (node == null) return MISSING;
            PathSegment pt = segments[i];
            JsonType jt = JsonType.of(node);
            if (pt instanceof PathSegment.Name) {
                if (jt.isObject()) {
                    String name = ((PathSegment.Name) pt).name;
                    Object parent = node;
                    node = Nodes.getInObject(node, name);
                    if (node == null && i == endExclusive - 1 && !Nodes.containsInObject(parent, name)) return MISSING;
                } else {
                    return MISSING;
                }
            } else if (pt instanceof PathSegment.Index) {
                PathSegment.Index index = (PathSegment.Index) pt;
                if (jt.isArray()) {
                    Object parent = node;
                    node = Nodes.getInArray(node, index.index);
                    if (node == null && i == endExclusive - 1 && !Nodes.containsInArray(parent, index.index)) return MISSING;
                } else if (_isPointerObjectKey(index, jt)) {
                    Object parent = node;
                    node = Nodes.getInObject(node, index.pointerToken);
                    if (node == null && i == endExclusive - 1 && !Nodes.containsInObject(parent, index.pointerToken)) return MISSING;
                } else {
                    return MISSING;
                }
            } else if (pt instanceof PathSegment.Descendant) {
                if (i + 1 >= segments.length) throw new JsonException("descendant '..' cannot appear at the end");
                List<Object> result = new ArrayList<>();
                _findMatch(container, node, i + 1, endExclusive, result, Function.identity());
                if (result.isEmpty()) {
                    return MISSING;
                } else if (result.size() == 1) {
                    return result.get(0);
                } else {
                    throw new JsonException("path '" + this + "' matched " + result.size() +
                            " results, but this method requires a single value");
                }
            } else {
                throw new JsonException("unsupported path token '" + pt + "'");
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
                PathSegment.Index index = (PathSegment.Index) pt;
                if (jt.isArray()) {
                    if (!Nodes.containsInArray(node, index.index)) return;
                    node = Nodes.getInArray(node, index.index);
                    continue;
                } else if (_isPointerObjectKey(index, jt) && Nodes.containsInObject(node, index.pointerToken)) {
                    node = Nodes.getInObject(node, index.pointerToken);
                    continue;
                }
            } else if (pt instanceof PathSegment.Wildcard) {
                if (jt.isObject()) {
                    Nodes.forEachObject(node, (k, v) -> _findAll(root, v, nextI, endExclusive, result, converter));
                } else if (jt.isArray()) {
                    Nodes.forEachArray(node, (j, v) -> _findAll(root, v, nextI, endExclusive, result, converter));
                }
            } else if (pt instanceof PathSegment.Descendant) {
                if (i + 1 >= segments.length) throw new JsonException("descendant '..' cannot appear at the end");
                _findMatch(root, node, i + 1, endExclusive, result, converter);
            } else if (pt instanceof PathSegment.Slice) {
                PathSegment.Slice slicePt = (PathSegment.Slice) pt;
                if (jt.isArray()) {
                    int size = Nodes.sizeInArray(node);
                    Nodes.forEachArray(node, (j, v) -> {
                        if (slicePt.matchIndex(j, size)) _findAll(root, v, nextI, endExclusive, result, converter);
                    });
                }
            } else if (pt instanceof PathSegment.Union) {
                PathSegment.Union unionPt = (PathSegment.Union) pt;
                if (jt.isObject()) {
                    Nodes.forEachObject(node, (k, v) -> {
                        if (unionPt.matchKey(k)) _findAll(root, v, nextI, endExclusive, result, converter);
                    });
                } else if (jt.isArray()) {
                    int size = Nodes.sizeInArray(node);
                    Nodes.forEachArray(node, (j, v) -> {
                        if (unionPt.matchIndex(j, size)) _findAll(root, v, nextI, endExclusive, result, converter);
                    });
                }
            } else if (pt instanceof PathSegment.Filter) {
                PathSegment.Filter filterPt = (PathSegment.Filter) pt;
                if (jt.isArray()) {
                    Nodes.forEachArray(node, (j, v) -> {
                        if (filterPt.filterExpr.evalTruth(root, v)) {
                            _findAll(root, v, nextI, endExclusive, result, converter);
                        }
                    });
                } else if (jt.isObject()) {
                    Nodes.forEachObject(node, (k, v) -> {
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
                throw new JsonException("unexpected path token '" + pt + "'");
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
            Nodes.forEachObject(current, (k, v) -> {
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
            Nodes.forEachArray(current, (j, v) -> {
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
            PathSegment.Index index = (PathSegment.Index) lastToken;
            if (_isPointerObjectKey(index, lastContainer)) {
                return Nodes.putInObject(lastContainer, index.pointerToken, value);
            }
            Nodes.putInArray(lastContainer, index.index, value);
            return null;
        } else if (lastToken instanceof PathSegment.Append) {
            Nodes.addInArray(lastContainer, value);
            return null;
        } else {
            throw new JsonException("unsupported last path token '" + lastToken +
                    "'; " + opName + " expected Name, Index, or Append token");
        }
    }


    private void _computeLast(Object lastContainer, PathSegment lastToken, BiFunction<Object, Object, Object> computer) {
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            Object current = Nodes.getInObject(lastContainer, name);
            Nodes.putInObject(lastContainer, name, computer.apply(lastContainer, current));
        } else if (lastToken instanceof PathSegment.Index) {
            PathSegment.Index index = (PathSegment.Index) lastToken;
            if (_isPointerObjectKey(index, lastContainer)) {
                Object current = Nodes.getInObject(lastContainer, index.pointerToken);
                Nodes.putInObject(lastContainer, index.pointerToken, computer.apply(lastContainer, current));
            } else {
                Object current = Nodes.getInArray(lastContainer, index.index);
                Nodes.putInArray(lastContainer, index.index, computer.apply(lastContainer, current));
            }
        } else if (lastToken instanceof PathSegment.Append) {
            Nodes.addInArray(lastContainer, computer.apply(lastContainer, null));
        } else {
            throw new JsonException("unsupported last path token '" + lastToken +
                    "'; compute() expected Name, Index, or Append token");
        }
    }

    /**
     * Ensures intermediate containers exist for single-path traversal and
     * returns the parent container of the final segment.
     */
    private Object _ensureContainersInPath(Object container) {
        if (!isSinglePut()) {
            throw new JsonException("JsonPath '" + this + "' must represent a single-node path; " +
                    "automatic container creation supports only Root, Name, Index, and Append segments");
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
                    } else if (acc.puttable) {
                        PathSegment nextPt = segments[i + 1];
                        Object subNode = _createContainer(nextPt, Types.rawClazz(acc.type));
                        Nodes.putInObject(curNode, key, subNode);
                        curNode = subNode;
                        curType = acc.type;
                    } else {
                        throw new JsonException("cannot put field '" + key + "' on object node type '" + curType + "'");
                    }
                } else {
                    throw new JsonException("expected object node at '" + ps.rootedPathExpr() + "', but was '" +
                            curType + "'");
                }
            } else if (ps instanceof PathSegment.Index) {
                PathSegment.Index index = (PathSegment.Index) ps;
                if (jt.isArray()) {
                    Nodes.accessInArray(curNode, curType, index.index, acc);
                    if (acc.node != null) {
                        curNode = acc.node;
                        curType = acc.type;
                    } else if (acc.puttable) {
                        PathSegment nextPt = segments[i + 1];
                        Object subNode = _createContainer(nextPt, Types.rawClazz(acc.type));
                        Nodes.setInArray(curNode, index.index, subNode);
                        curNode = subNode;
                        curType = acc.type;
                    } else {
                        throw new JsonException("cannot ensure path segment '" + ps.rootedPathExpr() +
                                "': indexed array access requires an existing element; use append path syntax instead");
                    }
                } else if (_isPointerObjectKey(index, jt)) {
                    Nodes.accessInObject(curNode, curType, index.pointerToken, acc);
                    if (acc.node != null) {
                        curNode = acc.node;
                        curType = acc.type;
                    } else if (acc.puttable) {
                        PathSegment nextPt = segments[i + 1];
                        Object subNode = _createContainer(nextPt, Types.rawClazz(acc.type));
                        Nodes.putInObject(curNode, index.pointerToken, subNode);
                        curNode = subNode;
                        curType = acc.type;
                    } else {
                        throw new JsonException("cannot put field '" + index.pointerToken + "' on object node type '" + curType + "'");
                    }
                } else {
                    throw new JsonException("expected array node at '" + ps.rootedPathExpr() + "', but was '" +
                            curType + "'");
                }
            } else if (ps instanceof PathSegment.Append) {
                if (jt.isArray()) {
                    Nodes.accessInArray(curNode, curType, null, acc);
                    if (acc.puttable) {
                        PathSegment nextPt = segments[i + 1];
                        Object subNode = _createContainer(nextPt, Types.rawClazz(acc.type));
                        Nodes.addInArray(curNode, subNode);
                        curNode = subNode;
                        curType = acc.type;
                    } else {
                        throw new JsonException("cannot append to array node type '" + curType + "'");
                    }
                } else {
                    throw new JsonException("expected array node at '" + ps.rootedPathExpr() + "', but was '" +
                            curType + "'");
                }
            } else {
                throw new JsonException("unexpected path token '" + ps + "'");
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
                return new LinkedHashMap<>();
            }
            if (clazz == JsonObject.class) {
                return new JsonObject();
            }
            NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(clazz).pojoInfo;
            if (pi != null) {
                return pi.creatorInfo.forceNewPojo();
            }
            throw new JsonException("cannot create object node of type '" + clazz + "' at '" +
                    ps.rootedPathExpr() + "'; only Map/JsonObject/JOJO/POJO are supported");
        } else if (ps instanceof PathSegment.Index || ps instanceof PathSegment.Append) {
            if (clazz == Object.class || clazz == List.class) {
                return new ArrayList<>();
            }
            if (clazz == JsonArray.class) {
                return new JsonArray();
            }
            if (JsonArray.class.isAssignableFrom(clazz)) {
                return NodeRegistry.registerPojoOrElseThrow(clazz).creatorInfo.forceNewPojo();
            }
            if (clazz == Set.class) {
                return new LinkedHashSet<>();
            }
            throw new JsonException("cannot create array node of type '" + clazz +
                    "' at '" + ps.rootedPathExpr() + "'; only List/JsonArray/JAJO/Set are supported");
        } else {
            throw new JsonException("unexpected path token '" + ps + "'");
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
            throw new JsonException("invalid function argument '" + raw + "'");
        }
    }

}
