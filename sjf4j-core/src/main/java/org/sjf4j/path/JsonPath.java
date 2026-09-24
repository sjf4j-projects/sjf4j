package org.sjf4j.path;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.exception.NodeException;
import org.sjf4j.JsonObject;
import org.sjf4j.Nodes;
import org.sjf4j.node.Types;
import org.sjf4j.util.Asserts;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * JSONPath/JSON Pointer execution engine for OBNT values.
 *
 * <p>JsonPath parses a textual path expression into a chain of {@link PathSegment}
 * tokens and then evaluates the path against an OBNT object node, array node, or
 * value node. It supports both:
 * <ul>
 *   <li>a JSONPath subset: root/current, name/index, wildcard, descendant,
 *       union, slice, filter, append, and function tokens</li>
 *   <li>a JSON Pointer subset: empty root, slash-delimited escaped tokens, and
 *       nonnegative 32-bit array indexes. {@code -} is reserved for append writes.</li>
 * </ul>
 *
 * <p>Read operations do not actively write the input graph. Invoked getters,
 * registered functions, and extensions can have side effects. Single-location
 * reads return {@code null} for both a missing location and an explicit null value; use
 * {@link #contains(Object)} to distinguish them. {@link #find(Object)} omits
 * missing locations while retaining explicit null matches. {@link #eval(Object)}
 * returns a single result for a single path, a list for non-single paths, or a
 * terminal function result.
 *
 * <p>Writes mutate only addressed containers in the supplied graph; they do not
 * replace a root reference. Object writes upsert {@link Map}/{@link JsonObject}
 * members, including dynamic JOJO members; ordinary POJO writes require a writable
 * declared property. Array writes are limited by
 * the underlying representation: lists and {@link JsonArray} can append, Java
 * arrays cannot, and sets do not support indexed writes. Multi-match paths are
 * writable only by {@link #compute(Object, BiFunction)}.
 */
public class JsonPath {

    static final Object MISSING = new Object();

    /**
     * The raw path expression string.
     */
    protected final String raw;

    protected final PathSegment[] segments;

    protected final boolean singleGet;
    protected final boolean singlePut;
    protected final boolean singleEval;
    protected final int appendCount;
    protected final int paramCount;

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
        this.appendCount = target.appendCount;
        this.paramCount = target.paramCount;
    }

    protected JsonPath(String raw, PathSegment[] segments) {
        if (segments.length == 0) throw new NodeException("segments must not be empty");
        this.raw = raw;
        this.segments = segments;

        int len = segments.length;
        boolean isSingleGet = true;
        boolean isSinglePut = true;
        boolean isSingleEval = len > 1 && segments[len - 1] instanceof PathSegment.Function;
        int appendCount = 0;
        int paramCount = 0;
        for (int i = 0; i < len; i++) {
            PathSegment ps = segments[i];
            if (ps instanceof PathSegment.Append) {
                appendCount++;
            }
            if (ps instanceof PathSegment.Param) {
                paramCount++;
            }
            if (!(ps instanceof PathSegment.Root || ps instanceof PathSegment.Current || ps instanceof PathSegment.Name || ps instanceof PathSegment.Index
                    || ps instanceof PathSegment.Param)) {
                isSingleGet = false;
                if (i < len - 1) {
                    isSingleEval = false;
                }
                if (!(ps instanceof PathSegment.Append)) {
                    isSinglePut = false;
                }
            }
            if (ps instanceof PathSegment.Current) {
                isSinglePut = false;
            }
        }
        this.singleGet = isSingleGet;
        this.singlePut = isSinglePut;
        this.singleEval = isSingleEval;
        this.appendCount = appendCount;
        this.paramCount = paramCount;
    }

    /**
     * Parses a JSONPath or JSON Pointer expression into executable segments.
     * <p>
     * Empty input resolves to root. Expressions starting with {@code /} are
     * parsed as JSON Pointer; others are parsed as the supported JSONPath subset.
     */
    public static JsonPath parse(String expr) {
        Asserts.notNull(expr, "expr");
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

    public boolean isSingleGet() {
        return singleGet;
    }

    /**
     * Returns whether this path is statically classified for a single put.
     * <p>
     * Root, Name, Index, Append, and Param segments qualify. Current makes this
     * false even though direct writes such as {@code @.a} can execute. Public
     * JsonPath execution does not resolve Param segments, so a path containing one
     * is not directly executable.
     */
    public boolean isSinglePut() {
        return singlePut;
    }

    /**
     * Returns the number of append segments in this path.
     * <p>
     * Append segments come from JSONPath {@code [+]} or JSON Pointer {@code /-}.
     */
    public int appendCount() {
        return appendCount;
    }

    public int paramCount() {
        return paramCount;
    }

    /*
     * --------------------------------------------------------------
     * Find
     * --------------------------------------------------------------
     */

    /**
     * Returns the value at a single-location path.
     * <p>
     * A missing location and an explicit null value both return {@code null}.
     * Broad path tokens are not collected by this method; use {@link #find(Object)}
     * or {@link #eval(Object)} for multi-match evaluation.
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
     * Returns a single-location value or the default value for missing or null.
     *
     * @param container the JSON container to search
     * @param defaultValue the value to return if the path doesn't exist
     * @return the object found at the path, or the default value if not found
     */
    public Object getNode(Object container, Object defaultValue) {
        Object value = getNode(container);
        return null == value ? defaultValue : value;
    }

    private NodeException _strict(Object container, Object value, String target, Exception cause) {
        return new NodeException("cannot get " + target + " from path '" + this + "': container=" +
                Types.name(container) + ", value=" + Types.name(value), cause);
    }

    private NodeException _lenient(Object container, Object value, String target, Exception cause) {
        return new NodeException("cannot coerce value at path '" + this + "' to " + target + ": container=" +
                Types.name(container) + ", value=" + Types.name(value), cause);
    }

    /**
     * Returns a String at this path using strict conversion.
     */
    public String getString(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toString(value);
        } catch (Exception e) {
            throw _strict(container, value, "String", e);
        }
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
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.asString(value);
        } catch (Exception e) {
            throw _lenient(container, value, "String", e);
        }
    }

    /**
     * Returns a Number at this path using strict conversion.
     */
    public Number getNumber(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toNumber(value);
        } catch (Exception e) {
            throw _strict(container, value, "Number", e);
        }
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
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.asNumber(value);
        } catch (Exception e) {
            throw _lenient(container, value, "Number", e);
        }
    }

    /**
     * Returns a Long at this path using strict conversion.
     */
    public Long getLong(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toLong(value);
        } catch (Exception e) {
            throw _strict(container, value, "Long", e);
        }
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
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.asLong(value);
        } catch (Exception e) {
            throw _lenient(container, value, "Long", e);
        }
    }

    /**
     * Returns an Integer at this path using strict conversion.
     */
    public Integer getInt(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toInt(value);
        } catch (Exception e) {
            throw _strict(container, value, "Integer", e);
        }
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
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.asInt(value);
        } catch (Exception e) {
            throw _lenient(container, value, "Integer", e);
        }
    }

    /**
     * Returns a Short at this path using strict conversion.
     */
    public Short getShort(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toShort(value);
        } catch (Exception e) {
            throw _strict(container, value, "Short", e);
        }
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
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.asShort(value);
        } catch (Exception e) {
            throw _lenient(container, value, "Short", e);
        }
    }

    /**
     * Returns a Byte at this path using strict conversion.
     */
    public Byte getByte(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toByte(value);
        } catch (Exception e) {
            throw _strict(container, value, "Byte", e);
        }
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
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.asByte(value);
        } catch (Exception e) {
            throw _lenient(container, value, "Byte", e);
        }
    }

    /**
     * Returns a Double at this path using strict conversion.
     */
    public Double getDouble(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toDouble(value);
        } catch (Exception e) {
            throw _strict(container, value, "Double", e);
        }
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
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.asDouble(value);
        } catch (Exception e) {
            throw _lenient(container, value, "Double", e);
        }
    }

    /**
     * Returns a Float at this path using strict conversion.
     */
    public Float getFloat(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toFloat(value);
        } catch (Exception e) {
            throw _strict(container, value, "Float", e);
        }
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
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.asFloat(value);
        } catch (Exception e) {
            throw _lenient(container, value, "Float", e);
        }
    }

    /**
     * Returns a BigInteger at this path using strict conversion.
     */
    public BigInteger getBigInteger(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toBigInteger(value);
        } catch (Exception e) {
            throw _strict(container, value, "BigInteger", e);
        }
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
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.asBigInteger(value);
        } catch (Exception e) {
            throw _lenient(container, value, "BigInteger", e);
        }
    }

    /**
     * Returns a BigDecimal at this path using strict conversion.
     */
    public BigDecimal getBigDecimal(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toBigDecimal(value);
        } catch (Exception e) {
            throw _strict(container, value, "BigDecimal", e);
        }
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
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.asBigDecimal(value);
        } catch (Exception e) {
            throw _lenient(container, value, "BigDecimal", e);
        }
    }

    /**
     * Returns a Boolean at this path using strict conversion.
     */
    public Boolean getBoolean(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toBoolean(value);
        } catch (Exception e) {
            throw _strict(container, value, "Boolean", e);
        }
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
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.asBoolean(value);
        } catch (Exception e) {
            throw _lenient(container, value, "Boolean", e);
        }
    }

    /**
     * Returns a JsonObject at this path using strict conversion.
     */
    public JsonObject getJsonObject(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toJsonObject(value);
        } catch (Exception e) {
            throw _strict(container, value, "JsonObject", e);
        }
    }

    /**
     * Returns a Map at this path using strict conversion.
     */
    public Map<String, Object> getMap(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toMap(value);
        } catch (Exception e) {
            throw _strict(container, value, "Map<String,Object>", e);
        }
    }

    /**
     * Returns a typed Map at this path using strict conversion.
     */
    public <T> Map<String, T> getMap(Object container, Class<T> clazz) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toMap(value, clazz);
        } catch (Exception e) {
            throw _strict(container, value, "Map<String," + clazz.getName() + ">", e);
        }
    }

    /**
     * Returns a JsonArray at this path using strict conversion.
     */
    public JsonArray getJsonArray(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toJsonArray(value);
        } catch (Exception e) {
            throw _strict(container, value, "JsonArray", e);
        }
    }

    // List
    /**
     * Returns a List at this path using strict conversion.
     */
    public List<Object> getList(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toList(value);
        } catch (Exception e) {
            throw _strict(container, value, "List<Object>", e);
        }
    }

    /**
     * Returns a typed List at this path using strict conversion.
     */
    public <T> List<T> getList(Object container, Class<T> clazz) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toList(value, clazz);
        } catch (Exception e) {
            throw _strict(container, value, "List<" + clazz.getName() + ">", e);
        }
    }

    /**
     * Returns an Object array at this path using strict conversion.
     */
    public Object[] getArray(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toArray(value);
        } catch (Exception e) {
            throw _strict(container, value, "Object[]", e);
        }
    }

    /**
     * Returns a typed array at this path using strict conversion.
     */
    public <T> T[] getArray(Object container, Class<T> clazz) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toArray(value, clazz);
        } catch (Exception e) {
            throw _strict(container, value, clazz.getName() + "[]", e);
        }
    }

    /**
     * Returns a Set at this path using strict conversion.
     */
    public Set<Object> getSet(Object container) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toSet(value);
        } catch (Exception e) {
            throw _strict(container, value, "Set<Object>", e);
        }
    }

    /**
     * Returns a typed Set at this path using strict conversion.
     */
    public <T> Set<T> getSet(Object container, Class<T> clazz) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.toSet(value, clazz);
        } catch (Exception e) {
            throw _strict(container, value, "Set<" + clazz.getName() + ">", e);
        }
    }

    /**
     * Returns a value at this path converted to the given type.
     */
    public <T> T get(Object container, Class<T> clazz) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.to(value, clazz);
        } catch (Exception e) {
            throw _strict(container, value, clazz.getName(), e);
        }
    }

    /**
     * Returns a value at this path converted to the inferred type parameter.
     *
     * @throws IllegalArgumentException if {@code reified} is nonempty
     * @throws NullPointerException if {@code reified} is null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Object container, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("reified varargs must be empty");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(container, clazz);
    }

    /**
     * Returns a value at this path using lenient conversion.
     */
    public <T> T getAs(Object container, Class<T> clazz) {
        Object value = null;
        try {
            value = getNode(container);
            return Nodes.as(value, clazz);
        } catch (Exception e) {
            throw _lenient(container, value, clazz.getName(), e);
        }
    }

    /**
     * Returns a value at this path using lenient conversion with inferred type.
     *
     * @throws IllegalArgumentException if {@code reified} is nonempty
     * @throws NullPointerException if {@code reified} is null
     */
    @SuppressWarnings("unchecked")
    public <T> T getAs(Object container, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("reified varargs must be empty");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return getAs(container, clazz);
    }

    /*
     * --------------------------------------------------------------
     * Find
     * --------------------------------------------------------------
     */

    /**
     * Finds all matching values for this path.
     * <p>
     * The returned list is empty when there is no match. Explicit null matches
     * are retained as null elements; missing locations are omitted.
     */
    public List<Object> find(Object container) {
        Asserts.notNull(container, "container");
        if (singleGet) {
            List<Object> result = new ArrayList<>(1);
            Object value = _findOne(container, 1, segments.length);
            if (value != MISSING) result.add(value);
            return result;
        }
        List<Object> result = new ArrayList<>();
        _findAll(container, container, 1, segments.length, result, Function.identity(), new Nodes.Access());
        return result;
    }

    /**
     * Finds and converts all matches using strict conversion.
     */
    public <T> List<T> find(Object container, Class<T> clazz) {
        Asserts.notNull(container, "container");
        Asserts.notNull(clazz, "clazz");
        if (singleGet) {
            List<T> result = new ArrayList<>(1);
            Object value = _findOne(container, 1, segments.length);
            if (value != MISSING) result.add(Nodes.to(value, clazz));
            return result;
        }
        List<T> result = new ArrayList<>();
        _findAll(container, container, 1, segments.length, result, (n) -> Nodes.to(n, clazz), new Nodes.Access());
        return result;
    }

    /**
     * Finds and converts all matches using lenient conversion.
     */
    public <T> List<T> findAs(Object container, Class<T> clazz) {
        Asserts.notNull(container, "container");
        Asserts.notNull(clazz, "clazz");
        if (singleGet) {
            List<T> result = new ArrayList<>(1);
            Object value = _findOne(container, 1, segments.length);
            if (value != MISSING) result.add(Nodes.as(value, clazz));
            return result;
        }
        List<T> result = new ArrayList<>();
        _findAll(container, container, 1, segments.length, result, (n) -> Nodes.as(n, clazz), new Nodes.Access());
        return result;
    }

    /*
     * --------------------------------------------------------------
     * Eval
     * --------------------------------------------------------------
     */

    /**
     * Evaluates the path and returns either a single value, a list of values,
     * or a function result. A multi-match path returns a list regardless of
     * its match count; an unresolved single-value path returns {@code null}.
     * <p>
     * When the last segment is a function token, the function is invoked with
     * the matched value(s) as target plus parsed literal arguments.
     */
    public Object eval(Object container) {
        Asserts.notNull(container, "container");
        PathSegment tk = segments[segments.length - 1];
        if (singleGet) {
            Object value = _findOne(container, 1, segments.length);
            return value == MISSING ? null : value;
        }
        if (singleEval) {
            Object value = _findOne(container, 1, segments.length - 1);
            if (value == MISSING) return null;
            PathSegment.Function func = (PathSegment.Function) tk;
            return FunctionRegistry.invoke(func.name, value, func.resolvedArgs);
        }
        List<Object> result = new ArrayList<>();
        _findAll(container, container, 1, segments.length, result, Function.identity(), new Nodes.Access());
        if (result.isEmpty()) return tk instanceof PathSegment.Function ? null : result;

        if (tk instanceof PathSegment.Function) {
            PathSegment.Function func = (PathSegment.Function) tk;
            return FunctionRegistry.invoke(func.name, result.size() == 1 ? result.get(0) : result, func.resolvedArgs);
        }
        return result;
    }

    /**
     * Evaluates the path and converts the result using strict conversion.
     */
    public <T> T eval(Object container, Class<T> clazz) {
        Object value = null;
        try {
            value = eval(container);
            if (!singleGet && !(tail() instanceof PathSegment.Function) && value instanceof List && ((List<?>) value).size() == 1
                    && !Collection.class.isAssignableFrom(clazz) && clazz != JsonArray.class && !clazz.isArray()) {
                value = ((List<?>) value).get(0);
            }
            return Nodes.to(value, clazz);
        } catch (Exception e) {
            throw new NodeException("cannot evaluate " + clazz.getName() + " from path '" + this + "': container=" +
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
            if (!singleGet && !(tail() instanceof PathSegment.Function) && value instanceof List && ((List<?>) value).size() == 1
                    && !Collection.class.isAssignableFrom(clazz) && clazz != JsonArray.class && !clazz.isArray()) {
                value = ((List<?>) value).get(0);
            }
            return Nodes.as(value, clazz);
        } catch (Exception e) {
            throw new NodeException("cannot coerce value at path '" + this + "' to " + clazz.getName() + ": container=" +
                    Types.name(container) + ", value=" + Types.name(value), e);
        }
    }

    /*
     * --------------------------------------------------------------
     * Put
     * --------------------------------------------------------------
     */

    /**
     * Writes the value at the final path location and returns the previous value
     * when the target shape exposes one.
     * <p>
     * The parent container of the final segment must already exist; this method
     * mutates that parent and never replaces the root reference. Object-name
     * targets upsert {@link Map}/{@link JsonObject} members, including dynamic
     * JOJO members. Ordinary POJO targets require a writable declared property.
     * Array index targets write through
     * {@link Nodes#putInArray(Object, int, Object)}, which replaces existing
     * elements and appends when {@code idx == size}. Append targets write
     * through {@link Nodes#addInArray(Object, Object)}. POJO property writes
     * return {@code null} because they avoid reading the old value.
     *
     * @throws NodeException when the parent container does not exist or the last
     *                       segment cannot be written
     */
    public Object put(Object container, Object value) {
        Asserts.notNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if (lastContainer == MISSING || lastContainer == null) {
            throw new NodeException("cannot put value at path '" + this + "': parent container does not exist");
        }
        return _putLast(lastContainer, segments[segments.length - 1], value, "put()");
    }

    /**
     * Writes the value only when the parent container of the final path segment
     * already exists.
     * <p>
     * Missing parent containers return {@code null} without writing. Once the
     * parent exists, the final write follows the same last-segment rules as
     * {@link #put(Object, Object)}, including its POJO property restrictions.
     *
     * @return the previous value when a write occurred and the target shape
     * exposes one, otherwise {@code null}
     */
    public Object putIfParentPresent(Object container, Object value) {
        Asserts.notNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if (lastContainer == MISSING || lastContainer == null) return null;
        return _putLast(lastContainer, segments[segments.length - 1], value, "putIfParentPresent()");
    }

    /**
     * Ensures intermediate containers exist and writes the value at the final
     * path location.
     * <p>
     * This mutates existing addressed containers and any containers it creates.
     * Auto-creation is only supported for single paths made of root/name/index/
     * append segments. Missing containers are created based on inferred static
     * type. Intermediate array indexes use {@link Nodes#putInArray(Object, int, Object)}
     * semantics when a missing container is written back: existing indexes are
     * replaced and {@code index == size} appends for appendable arrays.
     * Once the parent container exists, the final write follows the same last-
     * segment rules as {@link #put(Object, Object)}, including its POJO property
     * restrictions.
     */
    public Object ensurePut(Object container, Object value) {
        Asserts.notNull(container, "container");
        Object lastContainer = _ensureContainersInPath(container);
        return _putLast(lastContainer, segments[segments.length - 1], value, "ensurePut()");
    }

    /**
     * Ensures the final path location exists, and writes only when the current
     * value is absent or {@code null}.
     * <p>
     * Missing parent containers are created using {@link #ensurePut(Object, Object)}.
     * For object-name and pointer-object-key targets, absent means the key is
     * missing or currently maps to {@code null}; an unknown ordinary POJO property
     * fails rather than becoming a new member. For array-index targets, indexes
     * are normalized first; indexes greater than the current size fail, indexes
     * equal to the current size append, and existing indexes are replaced only
     * when their current value is {@code null}. Append targets always append.
     *
     * @return {@code null} when a write happened at an absent/null location, or
     * the existing non-null value when no write was performed
     */
    public Object ensurePutIfAbsent(Object container, Object value) {
        Asserts.notNull(container, "container");
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
                throw new NodeException("cannot ensure-put-if-absent value at indexed path '" + this +
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
            throw new NodeException("unsupported last path token '" + lastToken +
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
        Asserts.notNull(container, "container");
        Asserts.notNull(computer, "computer");
        PathSegment lastToken = segments[segments.length - 1];
        if (!(lastToken instanceof PathSegment.Name || lastToken instanceof PathSegment.Index
                || lastToken instanceof PathSegment.Append)) {
            throw new NodeException("unsupported last path token '" + lastToken +
                    "'; compute() expected Name, Index, or Append token");
        }
        if (singlePut) {
            Object parent = _findOne(container, 1, segments.length - 1);
            if (parent == MISSING || parent == null) return 0;
            _computeLast(parent, lastToken, computer);
            return 1;
        }
        List<Object> parents = new ArrayList<>();
        _findAll(container, container, 1, segments.length - 1, parents, Function.identity(), new Nodes.Access());
        for (Object parent : parents) {
            _computeLast(parent, lastToken, computer);
        }
        return parents.size();
    }

    /*
     * --------------------------------------------------------------
     * Has
     * --------------------------------------------------------------
     */

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
        Asserts.notNull(container, "container");
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
            throw new NodeException("unsupported last path token '" + lastToken +
                    "'; contains() expected Name or Index token");
        }
    }

    /*
     * --------------------------------------------------------------
     * JSON Patch: Add, Replace, Remove
     * --------------------------------------------------------------
     */

    /**
     * Applies JSON Patch {@code add} semantics at this pointer path.
     * <p>
     * This mutates the addressed parent; it cannot replace a root reference.
     * Name targets upsert {@link Map}/{@link JsonObject} members, including
     * dynamic JOJO members; ordinary POJOs require a writable declared property.
     * Index targets insert into lists and
     * {@link JsonArray}; append targets ({@code -}) append to appendable arrays.
     * Java arrays and sets reject indexed insertion.
     */
    public void add(Object container, Object value) {
        Asserts.notNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if  (lastContainer == MISSING || lastContainer == null)
            throw new NodeException("cannot add value at path '" + this + "': parent container does not exist");

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
            throw new NodeException("unsupported last path token '" + lastToken +
                    "'; add() expected Name, Index, or Append token");
        }
    }

    /**
     * Applies JSON Patch {@code replace} semantics (target must already exist).
     * <p>
     * This mutates the addressed parent. POJO replacement requires an existing
     * writable declared property; Java arrays allow only in-range replacement.
     *
     * @return previous value at the replaced location
     */
    public Object replace(Object container, Object value) {
        Asserts.notNull(container, "container");
        Object lastContainer = _findOne(container, 1, segments.length - 1);
        if  (lastContainer == MISSING || lastContainer == null) {
            throw new NodeException("cannot replace value at path '" + this + "': parent container does not exist");
        }
        PathSegment lastToken = segments[segments.length - 1];
        if (lastToken instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) lastToken).name;
            if (!Nodes.containsInObject(lastContainer, name)) {
                throw new NodeException("cannot replace value at non-existent path '" + this + "'");
            }
            return Nodes.putInObject(lastContainer, name, value);
        } else if (lastToken instanceof PathSegment.Index) {
            PathSegment.Index index = (PathSegment.Index) lastToken;
            if (_isPointerObjectKey(index, lastContainer)) {
                if (!Nodes.containsInObject(lastContainer, index.pointerToken)) {
                    throw new NodeException("cannot replace value at non-existent path '" + this + "'");
                }
                return Nodes.putInObject(lastContainer, index.pointerToken, value);
            }
            if (!Nodes.containsInArray(lastContainer, index.index)) {
                throw new NodeException("cannot replace value at non-existent path '" + this + "'");
            }
            return Nodes.setInArray(lastContainer, index.index, value);
        } else {
            throw new NodeException("unsupported last path token '" + lastToken +
                    "'; replace() expected Name or Index token");
        }
    }

    /**
     * Removes the value at this path when the target exists.
     * <p>
     * Missing parent paths return {@code null}. Missing object keys return
     * {@code null} only for removable object members such as {@link Map},
     * {@link JsonObject}, and dynamic JOJO members. Ordinary POJO properties,
     * including unknown keys, cannot be removed and may fail. Array removals
     * still follow array index rules and may fail for out-of-range indexes.
     * Java-array elements cannot be removed.
     *
     * @return removed value, or {@code null} when no value was removed
     */
    public Object removeIfPresent(Object container) {
        Asserts.notNull(container, "container");
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
            throw new NodeException("unsupported last path token '" + lastToken +
                    "'; remove() expected Name or Index token");
        }
    }


    /*
     * --------------------------------------------------------------
     * Private Helpers
     * --------------------------------------------------------------
     */

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
    Object _findOne(Object container, int startIdx, int endExclusive) {
        Object node = container;
        Nodes.Access acc = new Nodes.Access();
        for (int i = startIdx; i < endExclusive; i++) {
            if (node == null) return MISSING;
            PathSegment pt = segments[i];
            JsonType jt = JsonType.of(node);
            if (pt instanceof PathSegment.Name) {
                if (jt.isObject()) {
                    String name = ((PathSegment.Name) pt).name;
                    Nodes.getAccessInObject(node, name, acc);
                    if (!acc.present) return MISSING;
                    node = acc.node;
                } else {
                    return MISSING;
                }
            } else if (pt instanceof PathSegment.Index) {
                PathSegment.Index index = (PathSegment.Index) pt;
                if (jt.isArray()) {
                    Nodes.getAccessInArray(node, index.index, acc);
                    if (!acc.present) return MISSING;
                    node = acc.node;
                } else if (_isPointerObjectKey(index, jt)) {
                    Nodes.getAccessInObject(node, index.pointerToken, acc);
                    if (!acc.present) return MISSING;
                    node = acc.node;
                } else {
                    return MISSING;
                }
            } else if (pt instanceof PathSegment.Descendant) {
                if (i + 1 >= segments.length) throw new NodeException("descendant '..' cannot appear at the end");
                List<Object> result = new ArrayList<>();
                _findMatch(container, node, i + 1, endExclusive, result, Function.identity(), acc);
                if (result.isEmpty()) {
                    return MISSING;
                } else if (result.size() == 1) {
                    return result.get(0);
                } else {
                    throw new NodeException("path '" + this + "' matched " + result.size() +
                            " results, but this method requires a single value");
                }
            } else if (pt instanceof PathSegment.Param) {
                throw new NodeException("path parameter " + pt +
                        " can only be used in @GetByPath-style annotations");
            } else {
                throw new NodeException("unsupported path token '" + pt + "'");
            }
        }
        return node;
    }

    /**
     * Walks the path and collects matches up to {@code endExclusive}.
     */
    <T> void _findAll(Object root, Object current, int startIdx, int endExclusive,
                              List<T> result, Function<Object, T> converter, Nodes.Access acc) {
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
                    Nodes.getAccessInObject(node, name, acc);
                    if (acc.present) {
                        node = acc.node;
                        continue;
                    }
                }
            } else if (pt instanceof PathSegment.Index) {
                PathSegment.Index index = (PathSegment.Index) pt;
                if (jt.isArray()) {
                    Nodes.getAccessInArray(node, index.index, acc);
                    if (!acc.present) return;
                    node = acc.node;
                    continue;
                } else if (_isPointerObjectKey(index, jt)) {
                    Nodes.getAccessInObject(node, index.pointerToken, acc);
                    if (acc.present) {
                        node = acc.node;
                        continue;
                    }
                }
            } else if (pt instanceof PathSegment.Wildcard) {
                if (jt.isObject()) {
                    Nodes.forEachObject(node, (k, v) -> _findAll(root, v, nextI, endExclusive, result, converter, acc));
                } else if (jt.isArray()) {
                    Nodes.forEachArray(node, (j, v) -> _findAll(root, v, nextI, endExclusive, result, converter, acc));
                }
            } else if (pt instanceof PathSegment.Descendant) {
                if (i + 1 >= segments.length) throw new NodeException("descendant '..' cannot appear at the end");
                _findMatch(root, node, i + 1, endExclusive, result, converter, acc);
            } else if (pt instanceof PathSegment.Slice) {
                PathSegment.Slice slicePt = (PathSegment.Slice) pt;
                if (jt.isArray()) {
                    int size = Nodes.sizeInArray(node);
                    _findSlice(root, node, slicePt, size, nextI, endExclusive, result, converter, acc);
                }
            } else if (pt instanceof PathSegment.Union) {
                PathSegment.Union unionPt = (PathSegment.Union) pt;
                if (jt.isObject()) {
                    for (PathSegment member : unionPt.union) {
                        if (member instanceof PathSegment.Name) {
                            Nodes.getAccessInObject(node, ((PathSegment.Name) member).name, acc);
                            if (acc.present) _findAll(root, acc.node, nextI, endExclusive, result, converter, acc);
                        }
                    }
                } else if (jt.isArray()) {
                    int size = Nodes.sizeInArray(node);
                    for (PathSegment member : unionPt.union) {
                        if (member instanceof PathSegment.Index) {
                            int index = ((PathSegment.Index) member).index;
                            if (index < 0) index += size;
                            if (index >= 0 && index < size) _findAll(root, Nodes.getInArray(node, index), nextI, endExclusive, result, converter, acc);
                        } else if (member instanceof PathSegment.Slice) {
                            _findSlice(root, node, (PathSegment.Slice) member, size, nextI, endExclusive, result, converter, acc);
                        }
                    }
                }
            } else if (pt instanceof PathSegment.Filter) {
                PathSegment.Filter filterPt = (PathSegment.Filter) pt;
                if (jt.isArray()) {
                    Nodes.forEachArray(node, (j, v) -> {
                        if (filterPt.filterExpr.evalTruth(root, v)) {
                            _findAll(root, v, nextI, endExclusive, result, converter, acc);
                        }
                    });
                } else if (jt.isObject()) {
                    Nodes.forEachObject(node, (k, v) -> {
                        if (filterPt.filterExpr.evalTruth(root, v)) {
                            _findAll(root, v, nextI, endExclusive, result, converter, acc);
                        }
                    });
                } else {
                    if (filterPt.filterExpr.evalTruth(root, node)) {
                        continue;
                    }
                }
            } else {
                throw new NodeException("unexpected path token '" + pt + "'");
            }
            return;
        }
        result.add(converter.apply(node));
    }

    /**
     * Recursively scans descendants and matches the next token up to {@code endExclusive}.
     */
    <T> void _findMatch(Object root, Object current, int startIdx, int endExclusive,
                                 List<T> result, Function<Object, T> converter, Nodes.Access acc) {
        if (current == null) return;
        PathSegment pt = segments[startIdx];
        JsonType jt = JsonType.of(current);
        if (pt instanceof PathSegment.Slice && jt.isArray()) {
            int size = Nodes.sizeInArray(current);
            _findSlice(root, current, (PathSegment.Slice) pt, size, startIdx + 1, endExclusive, result, converter, acc);
            Nodes.forEachArray(current, (j, v) -> _findMatch(root, v, startIdx, endExclusive, result, converter, acc));
            return;
        }
        if (pt instanceof PathSegment.Union) {
            PathSegment.Union union = (PathSegment.Union) pt;
            if (jt.isObject()) {
                for (PathSegment member : union.union) {
                    if (member instanceof PathSegment.Name) {
                        Nodes.getAccessInObject(current, ((PathSegment.Name) member).name, acc);
                        if (acc.present) _findAll(root, acc.node, startIdx + 1, endExclusive, result, converter, acc);
                    }
                }
                Nodes.forEachObject(current, (k, v) -> _findMatch(root, v, startIdx, endExclusive, result, converter, acc));
                return;
            }
            if (jt.isArray()) {
                int size = Nodes.sizeInArray(current);
                for (PathSegment member : union.union) {
                    if (member instanceof PathSegment.Index) {
                        int index = ((PathSegment.Index) member).index;
                        if (index < 0) index += size;
                        if (index >= 0 && index < size) {
                            _findAll(root, Nodes.getInArray(current, index), startIdx + 1, endExclusive, result, converter, acc);
                        }
                    } else if (member instanceof PathSegment.Slice) {
                        _findSlice(root, current, (PathSegment.Slice) member, size, startIdx + 1, endExclusive, result, converter, acc);
                    }
                }
                Nodes.forEachArray(current, (j, v) -> _findMatch(root, v, startIdx, endExclusive, result, converter, acc));
                return;
            }
        }
        if (pt instanceof PathSegment.Name && jt.isObject()) {
            Nodes.getAccessInObject(current, ((PathSegment.Name) pt).name, acc);
            if (acc.present) {
                if (startIdx >= endExclusive) result.add(converter.apply(current));
                else _findAll(root, acc.node, startIdx + 1, endExclusive, result, converter, acc);
            }
            Nodes.forEachObject(current, (k, v) -> _findMatch(root, v, startIdx, endExclusive, result, converter, acc));
            return;
        }
        if (pt instanceof PathSegment.Index && jt.isArray() && !(current instanceof Set)) {
            Nodes.getAccessInArray(current, ((PathSegment.Index) pt).index, acc);
            if (acc.present) {
                if (startIdx >= endExclusive) result.add(converter.apply(current));
                else _findAll(root, acc.node, startIdx + 1, endExclusive, result, converter, acc);
            }
            Nodes.forEachArray(current, (j, v) -> _findMatch(root, v, startIdx, endExclusive, result, converter, acc));
            return;
        }
        if (pt instanceof PathSegment.Filter) {
            PathSegment.Filter filter = (PathSegment.Filter) pt;
            if (jt.isObject()) {
                Nodes.forEachObject(current, (k, v) -> {
                    if (filter.filterExpr.evalTruth(root, v)) {
                        _findAll(root, v, startIdx + 1, endExclusive, result, converter, acc);
                    }
                });
                Nodes.forEachObject(current, (k, v) -> _findMatch(root, v, startIdx, endExclusive, result, converter, acc));
                return;
            }
            if (jt.isArray()) {
                Nodes.forEachArray(current, (j, v) -> {
                    if (filter.filterExpr.evalTruth(root, v)) {
                        _findAll(root, v, startIdx + 1, endExclusive, result, converter, acc);
                    }
                });
                Nodes.forEachArray(current, (j, v) -> _findMatch(root, v, startIdx, endExclusive, result, converter, acc));
                return;
            }
        }
        if (jt.isObject()) {
            Nodes.forEachObject(current, (k, v) -> {
                if (pt.matchKey(k)) {
                    if (startIdx >= endExclusive) {
                        result.add(converter.apply(current));
                    } else {
                        _findAll(root, v, startIdx + 1, endExclusive, result, converter, acc);
                    }
                }
            });
            Nodes.forEachObject(current, (k, v) -> {
                _findMatch(root, v, startIdx, endExclusive, result, converter, acc);
            });
        } else if (jt.isArray()) {
            int size = Nodes.sizeInArray(current);
            Nodes.forEachArray(current, (j, v) -> {
                if (pt.matchIndex(j, size)) {
                    if (startIdx >= endExclusive) {
                        result.add(converter.apply(current));
                    } else {
                        _findAll(root, v, startIdx + 1, endExclusive, result, converter, acc);
                    }
                }
            });
            Nodes.forEachArray(current, (j, v) -> {
                _findMatch(root, v, startIdx, endExclusive, result, converter, acc);
            });
        }
    }

    private <T> void _findSlice(Object root, Object array, PathSegment.Slice slice, int size, int nextIdx, int endExclusive,
                                 List<T> result, Function<Object, T> converter, Nodes.Access acc) {
        long step = slice.step == null ? 1 : slice.step;
        long first = slice.start == null ? (step < 0 ? size - 1L : 0L) : slice.start;
        long last = slice.end == null ? (step < 0 ? -1L : size) : slice.end;
        if (slice.start != null && first < 0) first += size;
        if (slice.end != null && last < 0) last += size;
        if (step < 0) {
            first = Math.min(Math.max(first, -1L), size - 1L);
            last = Math.min(Math.max(last, -1L), size - 1L);
            for (long j = first; j > last; j += step) {
                _findAll(root, Nodes.getInArray(array, (int) j), nextIdx, endExclusive, result, converter, acc);
            }
        } else {
            first = Math.min(Math.max(first, 0L), size);
            last = Math.min(Math.max(last, 0L), size);
            for (long j = first; j < last; j += step) {
                _findAll(root, Nodes.getInArray(array, (int) j), nextIdx, endExclusive, result, converter, acc);
            }
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
            return Nodes.putInArray(lastContainer, index.index, value);
        } else if (lastToken instanceof PathSegment.Append) {
            Nodes.addInArray(lastContainer, value);
            return null;
        } else {
            throw new NodeException("unsupported last path token '" + lastToken +
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
            throw new NodeException("unsupported last path token '" + lastToken +
                    "'; compute() expected Name, Index, or Append token");
        }
    }

    /**
     * Ensures intermediate containers exist for single-path traversal and
     * returns the parent container of the final segment.
     */
    private Object _ensureContainersInPath(Object container) {
        if (!isSinglePut()) {
            throw new NodeException("JsonPath '" + this + "' must represent a single-node path; " +
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
                    Nodes.putAccessInObject(curNode, curType, key, acc);
                    if (acc.node != null) {
                        curNode = acc.node;
                        curType = acc.type;
                    } else if (acc.puttable) {
                        PathSegment nextPt = segments[i + 1];
                        Class<?> subClazz = Types.rawClazz(acc.type);
                        Object subNode = nextPt instanceof PathSegment.Name
                                ? Nodes.createObjectContainer(subClazz)
                                : Nodes.createArrayContainer(subClazz);
                        Nodes.putInObject(curNode, key, subNode);
                        curNode = subNode;
                        curType = acc.type;
                    } else {
                        throw new NodeException("cannot put field '" + key + "' on object node type '" + curType + "'");
                    }
                } else {
                    throw new NodeException("expected object node at '" + ps.rootedPathExpr() + "', but was '" +
                            curType + "'");
                }
            } else if (ps instanceof PathSegment.Index) {
                PathSegment.Index index = (PathSegment.Index) ps;
                if (jt.isArray()) {
                    Nodes.putAccessInArray(curNode, curType, index.index, acc);
                    if (acc.node != null) {
                        curNode = acc.node;
                        curType = acc.type;
                    } else if (acc.puttable) {
                        PathSegment nextPt = segments[i + 1];
                        Class<?> subClazz = Types.rawClazz(acc.type);
                        Object subNode = nextPt instanceof PathSegment.Name
                                ? Nodes.createObjectContainer(subClazz)
                                : Nodes.createArrayContainer(subClazz);
                        Nodes.putInArray(curNode, index.index, subNode);
                        curNode = subNode;
                        curType = acc.type;
                    } else {
                        throw new NodeException("cannot ensure path segment '" + ps.rootedPathExpr() +
                                "': indexed array access requires an existing element; use append path syntax instead");
                    }
                } else if (_isPointerObjectKey(index, jt)) {
                    Nodes.putAccessInObject(curNode, curType, index.pointerToken, acc);
                    if (acc.node != null) {
                        curNode = acc.node;
                        curType = acc.type;
                    } else if (acc.puttable) {
                        PathSegment nextPt = segments[i + 1];
                        Class<?> subClazz = Types.rawClazz(acc.type);
                        Object subNode = nextPt instanceof PathSegment.Name
                                ? Nodes.createObjectContainer(subClazz)
                                : Nodes.createArrayContainer(subClazz);
                        Nodes.putInObject(curNode, index.pointerToken, subNode);
                        curNode = subNode;
                        curType = acc.type;
                    } else {
                        throw new NodeException("cannot put field '" + index.pointerToken + "' on object node type '" + curType + "'");
                    }
                } else {
                    throw new NodeException("expected array node at '" + ps.rootedPathExpr() + "', but was '" +
                            curType + "'");
                }
            } else if (ps instanceof PathSegment.Append) {
                if (jt.isArray()) {
                    Nodes.putAccessInArray(curNode, curType, null, acc);
                    if (acc.puttable) {
                        PathSegment nextPt = segments[i + 1];
                        Class<?> subClazz = Types.rawClazz(acc.type);
                        Object subNode = nextPt instanceof PathSegment.Name
                                ? Nodes.createObjectContainer(subClazz)
                                : Nodes.createArrayContainer(subClazz);
                        Nodes.addInArray(curNode, subNode);
                        curNode = subNode;
                        curType = acc.type;
                    } else {
                        throw new NodeException("cannot append to array node type '" + curType + "'");
                    }
                } else {
                    throw new NodeException("expected array node at '" + ps.rootedPathExpr() + "', but was '" +
                            curType + "'");
                }
            } else {
                throw new NodeException("unexpected path token '" + ps + "'");
            }
        }
        return curNode; // last container
    }

}
