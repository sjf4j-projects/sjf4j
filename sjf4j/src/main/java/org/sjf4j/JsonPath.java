package org.sjf4j;

import org.sjf4j.util.JsonPathUtil;
import org.sjf4j.util.JsonPointerUtil;
import org.sjf4j.util.NodeUtil;
import org.sjf4j.util.TypeUtil;
import org.sjf4j.util.TypedNode;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private String raw;
    
    /**
     * List of parsed path tokens that represent the path expression.
     */
    private final List<PathToken> tokens;

    /**
     * Creates a copy of an existing JsonPath instance.
     *
     * @param target the JsonPath to copy
     */
    private JsonPath(JsonPath target) {
        this.raw = target.raw;
        this.tokens = new ArrayList<>(target.tokens);
    }

    /**
     * Creates an empty JsonPath with just a root token.
     */
    public JsonPath() {
        this.tokens = new ArrayList<>();
        push(new PathToken.Root());
    }

    /**
     * Creates a JsonPath from a path expression string.
     *
     * @param expr the path expression to compile
     * @throws IllegalArgumentException if expr is null
     * @throws JsonException if the path expression is invalid
     */
    public JsonPath(String expr) {
        if (expr == null) {
            throw new IllegalArgumentException("Expr must not be null");
        }
        if (expr.startsWith("$")) {
            this.tokens = JsonPathUtil.compile(expr);
        } else if (expr.startsWith("/")) {
            this.tokens = JsonPointerUtil.compile(expr);
        } else {
            throw new JsonException("Invalid path expression '" + expr + "'. " +
                    "Must start with '$' for JSON Path or '/' for JSON Pointer.");
        }
        this.raw = expr;
    }
    
    /**
     * Compiles a path expression string into a JsonPath instance.
     *
     * @param expr the path expression to compile
     * @return a new JsonPath instance
     * @throws IllegalArgumentException if expr is null
     * @throws JsonException if the path expression is invalid
     */
    public static JsonPath compile(String expr) {
        return new JsonPath(expr);
    }

    /**
     * Converts the path tokens back to a JSON Path expression string.
     *
     * @return the JSON Path expression string
     */
    public String toExpr() {
        return JsonPathUtil.genExpr(tokens);
    }

    /**
     * Converts the path tokens to a JSON Pointer expression string.
     *
     * @return the JSON Pointer expression string
     */
    public String toPointerExpr() {
        return JsonPointerUtil.genExpr(tokens);
    }

    /**
     * Returns the depth of the path (number of tokens).
     *
     * @return the depth of the path
     */
    public int depth() {
        return tokens.size();
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
     * Pushes a new path token to the end of the path.
     *
     * @param token the token to add
     * @return this JsonPath instance for method chaining
     * @throws IllegalArgumentException if token is null
     */
    public JsonPath push(PathToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Token must not be null");
        }
        tokens.add(token);
        return this;
    }

    /**
     * Returns the last token in the path without removing it.
     *
     * @return the last path token
     */
    public PathToken peek() {
        return tokens.get(tokens.size() - 1);
    }

    /**
     * Creates a copy of this JsonPath instance.
     *
     * @return a new JsonPath instance with the same tokens
     */
    public JsonPath copy() {
        return new JsonPath(this);
    }


    /// Find

    /**
     * Finds an object at this path in the given container.
     *
     * @param container the JSON container to search
     * @return the object found at the path, or null if not found
     * @throws IllegalArgumentException if container is null
     */
    public Object findObject(Object container) {
        if (container == null) {
            throw new IllegalArgumentException("Container must not be null");
        }
        return _findOne(container);
    }

    /**
     * Finds an object at this path in the given container, or returns a default value if not found.
     *
     * @param container the JSON container to search
     * @param defaultValue the value to return if the path doesn't exist
     * @return the object found at the path, or the default value if not found
     */
    public Object findObject(Object container, Object defaultValue) {
        Object value = findObject(container);
        return null == value ? defaultValue : value;
    }

    // String
    public String findString(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.toString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find String by path '" + this + "'", e);
        }
    }
    public String findString(Object container, String defaultValue) {
        String value = findString(container);
        return value == null ? defaultValue : value;
    }

    public String findAsString(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.asString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to String", e);
        }
    }
    public String findAsString(Object container, String defaultValue) {
        String value = findAsString(container);
        return value == null ? defaultValue : value;
    }


    // Long
    public Long findLong(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.toLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Long by path '" + this + "'", e);
        }
    }
    public long findLong(Object container, long defaultValue) {
        Long value = findLong(container);
        return value == null ? defaultValue : value;
    }

    public Long findAsLong(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.asLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Long", e);
        }
    }
    public long findAsLong(Object container, long defaultValue) {
        Long value = findAsLong(container);
        return value == null ? defaultValue : value;
    }

    // Integer
    public Integer findInteger(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.toInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Integer by path '" + this + "'", e);
        }
    }
    public int findInteger(Object container, int defaultValue) {
        Integer value = findInteger(container);
        return value == null ? defaultValue : value;
    }

    public Integer findAsInteger(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.asInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Integer", e);
        }
    }
    public int findAsInteger(Object container, int defaultValue) {
        Integer value = findAsInteger(container);
        return value == null ? defaultValue : value;
    }

    // Short
    public Short findShort(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.toShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Short by path '" + this + "'", e);
        }
    }
    public short findShort(Object container, short defaultValue) {
        Short value = findShort(container);
        return value == null ? defaultValue : value;
    }

    public Short findAsShort(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.asShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Short", e);
        }
    }
    public short findAsShort(Object container, short defaultValue) {
        Short value = findAsShort(container);
        return value == null ? defaultValue : value;
    }

    // Byte
    public Byte findByte(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.toByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Byte by path '" + this + "'", e);
        }
    }
    public byte findByte(Object container, byte defaultValue) {
        Byte value = findByte(container);
        return value == null ? defaultValue : value;
    }

    public Byte findAsByte(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.asByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Byte", e);
        }
    }
    public byte findAsByte(Object container, byte defaultValue) {
        Byte value = findAsByte(container);
        return value == null ? defaultValue : value;
    }

    // Double
    public Double findDouble(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.toDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Double by path '" + this + "'", e);
        }
    }
    public double findDouble(Object container, double defaultValue) {
        Double value = findDouble(container);
        return value == null ? defaultValue : value;
    }

    public Double findAsDouble(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.asDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Double", e);
        }
    }
    public double findAsDouble(Object container, double defaultValue) {
        Double value = findAsDouble(container);
        return value == null ? defaultValue : value;
    }

    // Float
    public Float findFloat(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.toFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Float by path '" + this + "'", e);
        }
    }
    public float findFloat(Object container, float defaultValue) {
        Float value = findFloat(container);
        return value == null ? defaultValue : value;
    }

    public Float findAsFloat(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.asFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Float", e);
        }
    }
    public float findAsFloat(Object container, float defaultValue) {
        Float value = findAsFloat(container);
        return value == null ? defaultValue : value;
    }

    // BigInteger
    public BigInteger findBigInteger(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.toBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find BigInteger by path '" + this + "'", e);
        }
    }
    public BigInteger findBigInteger(Object container, BigInteger defaultValue) {
        BigInteger value = findBigInteger(container);
        return value == null ? defaultValue : value;
    }

    public BigInteger findAsBigInteger(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.asBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to BigInteger", e);
        }
    }
    public BigInteger findAsBigInteger(Object container, BigInteger defaultValue) {
        BigInteger value = findAsBigInteger(container);
        return value == null ? defaultValue : value;
    }

    // BigDecimal
    public BigDecimal findBigDecimal(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.toBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find BigDecimal by path '" + this + "'", e);
        }
    }
    public BigDecimal findBigDecimal(Object container, BigDecimal defaultValue) {
        BigDecimal value = findBigDecimal(container);
        return value == null ? defaultValue : value;
    }

    public BigDecimal findAsBigDecimal(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.asBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to BigDecimal", e);
        }
    }
    public BigDecimal findAsBigDecimal(Object container, BigDecimal defaultValue) {
        BigDecimal value = findAsBigDecimal(container);
        return value == null ? defaultValue : value;
    }

    // Boolean
    public Boolean findBoolean(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.toBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Boolean by path '" + this + "'", e);
        }
    }
    public boolean findBoolean(Object container, boolean defaultValue) {
        Boolean value = findBoolean(container);
        return value == null ? defaultValue : value;
    }

    public Boolean findAsBoolean(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.asBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Boolean", e);
        }
    }
    public boolean findAsBoolean(Object container, boolean defaultValue) {
        Boolean value = findAsBoolean(container);
        return value == null ? defaultValue : value;
    }

    // JsonObject
    public JsonObject findJsonObject(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.toJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find JsonObject by path '" + this + "'", e);
        }
    }
    public JsonObject findJsonObject(Object container, JsonObject defaultValue) {
        JsonObject value = findJsonObject(container);
        return value == null ? defaultValue : value;
    }

    public JsonObject findAsJsonObject(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.asJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to JsonObject", e);
        }
    }
    public JsonObject findAsJsonObject(Object container, JsonObject defaultValue) {
        JsonObject value = findAsJsonObject(container);
        return value == null ? defaultValue : value;
    }

    // JsonArray
    public JsonArray findJsonArray(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.toJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find JsonArray by path '" + this + "'", e);
        }
    }
    public JsonArray findJsonArray(Object container, JsonArray defaultValue) {
        JsonArray value = findJsonArray(container);
        return value == null ? defaultValue : value;
    }

    public JsonArray findAsJsonArray(Object container) {
        try {
            Object value = findObject(container);
            return NodeUtil.asJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to JsonArray", e);
        }
    }
    public JsonArray findAsJsonArray(Object container, JsonArray defaultValue) {
        JsonArray value = findAsJsonArray(container);
        return value == null ? defaultValue : value;
    }

    // Clazz
    public <T> T find(Object container, Class<T> clazz) {
        try {
            Object value = findObject(container);
            return NodeUtil.to(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to find " + clazz.getName() + " by path '" + this + "': " +
                    e.getMessage(), e);
        }
    }
    @SuppressWarnings("unchecked")
    public <T> T find(Object container, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return find(container, clazz);
    }

    public <T> T findAs(Object container, Class<T> clazz) {
        try {
            Object value = findObject(container);
            return NodeUtil.as(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to '" + clazz + "'", e);
        }
    }
    @SuppressWarnings("unchecked")
    public <T> T findAs(Object container, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return findAs(container, clazz);
    }


    /// All
    public List<Object> findAll(Object container) {
        if (container == null) {
            throw new IllegalArgumentException("Container must not be null");
        }
        List<Object> result = new ArrayList<>();
        _findAll(container, 1, result, (n) -> n);
        return result;
    }

    public <T> List<T> findAll(Object container, Class<T> clazz) {
        if (container == null) {
            throw new IllegalArgumentException("Container must not be null");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Clazz must not be null");
        }
        List<T> result = new ArrayList<>();
        _findAll(container, 1, result, (n) -> NodeUtil.to(n, clazz));
        return result;
    }

    public <T> List<T> findAllAs(Object container, Class<T> clazz) {
        if (container == null) {
            throw new IllegalArgumentException("Container must not be null");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Clazz must not be null");
        }
        List<T> result = new ArrayList<>();
        _findAll(container, 1, result, (n) -> NodeUtil.as(n, clazz));
        return result;
    }

    /// Put

    public void put(Object container, Object value) {
        if (container == null) {
            throw new IllegalArgumentException("Container must not be null");
        }
        Object lastContainer = _ensureContainersInPath(container);
        PathToken lastToken = peek();
        if (lastToken instanceof PathToken.Name) {
            String name = ((PathToken.Name) lastToken).name;
            JsonWalker.putInObject(lastContainer, name, value);
        } else if (lastToken instanceof PathToken.Index) {
            int idx = ((PathToken.Index) lastToken).index;
            JsonWalker.setInArray(lastContainer, idx, value);
        } else {
            throw new JsonException("Unexpected path token '" + lastToken + "'");
        }
    }

    public boolean hasNonNull(Object container) {
        return findObject(container) != null;
    }

    public void putNonNull(Object container, Object value) {
        if (null != value) { put(container, value); }
    }

    public void putIfAbsent(Object container, Object value) {
        if (!hasNonNull(container)) { put(container, value); }
    }

    public void remove(Object container) {
        if (container == null) {
            throw new IllegalArgumentException("Container must not be null");
        }
        if (hasNonNull(container)) {
            Object lastContainer = _ensureContainersInPath(container);
            PathToken lastToken = peek();
            if (lastToken instanceof PathToken.Name) {
                if (lastContainer instanceof JsonObject) {
                    ((JsonObject) lastContainer).remove(((PathToken.Name) lastToken).name);
                } else if (lastContainer instanceof Map) {
                    ((Map<?, ?>) lastContainer).remove(((PathToken.Name) lastToken).name);
                } else if (PojoRegistry.isPojo(lastContainer.getClass())) {
                    String name = ((PathToken.Name) lastToken).name;
                    PojoRegistry.FieldInfo fi = PojoRegistry.getFieldInfo(lastContainer.getClass(), name);
                    if (fi != null) {
                        fi.invokeSetter(lastContainer, null);
                    } else {
                        throw new JsonException("Not found field '" + name + "' of POJO container type '" +
                                lastContainer.getClass() + "'");
                    }
                } else {
                    throw new JsonException("Mismatched path token " + lastToken + " with container type '" +
                            lastContainer.getClass() + "'");
                }
            } else if (lastToken instanceof PathToken.Index) {
                if (lastContainer instanceof JsonArray) {
                    ((JsonArray) lastContainer).remove(((PathToken.Index) lastToken).index);
                } else if (lastContainer instanceof List) {
                    ((List<?>) lastContainer).remove(((PathToken.Index) lastToken).index);
                } else if (lastContainer.getClass().isArray()) {
                    throw new JsonException("Cannot remove item for Array");
                } else {
                    throw new JsonException("Mismatched path token " + lastToken + " with container type '" +
                            lastContainer.getClass() + "'");
                }
            }
        }
    }


    /// private

    private Object _findOne(Object container) {
        Object node = container;
        for (int i = 1; i < tokens.size(); i++) {
            if (node == null) return null;
            PathToken pt = tokens.get(i);
            NodeType nt = NodeType.of(node);
            if (pt instanceof PathToken.Name) {
                if (nt.isObject()) {
                    node = JsonWalker.getInObject(node, ((PathToken.Name) pt).name);
                } else {
                    return null;
                }
            } else if (pt instanceof PathToken.Index) {
                if (nt.isArray()) {
                    node = JsonWalker.getInArray(node, ((PathToken.Index) pt).index);
                } else {
                    return null;
                }
            } else if (pt instanceof PathToken.Descendant) {
                if (i + 1 >= tokens.size()) throw new JsonException("Descendant '..' cannot appear at the end.");
                List<Object> result = new ArrayList<>();
                _findMatch(node, i + 1, result, Function.identity());
                if (result.isEmpty()) {
                    return null;
                } else if (result.size() == 1) {
                    return result.get(0);
                } else {
                    throw new JsonException("Path matched " + result.size() +
                            " results, but find() returns only one value. Use findAll() to retrieve all matches.");
                }
            } else {
                throw new JsonException("Unsupported path token '" + pt + "' in find()");
            }
        }
        return node;
    }


    // In the future, the `result` could be replaced with a callback to allow more flexible handling of matches.
    private <T> void _findAll(Object container, int tokenIdx, List<T> result, Function<Object, T> converter) {
        Object node = container;
        for (int i = tokenIdx; i < tokens.size(); i++) {
            if (node ==  null) return;
            PathToken pt = tokens.get(i);
            NodeType nt = NodeType.of(node);
            final int nextI = i + 1;
            if (pt instanceof PathToken.Name) {
                if (nt.isObject()) {
                    node = JsonWalker.getInObject(node, ((PathToken.Name) pt).name);
                    continue;
                }
            } else if (pt instanceof PathToken.Index) {
                if (nt.isArray()) {
                    node = JsonWalker.getInArray(node, ((PathToken.Index) pt).index);
                    continue;
                }
            } else if (pt instanceof PathToken.Wildcard) {
                if (nt.isObject()) {
                    JsonWalker.visitObject(node, (k, v) -> _findAll(v, nextI, result, converter));
                } else if (nt.isArray()) {
                    JsonWalker.visitArray(node, (j, v) -> _findAll(v, nextI, result, converter));
                }
            } else if (pt instanceof PathToken.Descendant) {
                if (i + 1 >= tokens.size()) throw new JsonException("Descendant '..' cannot appear at the end.");
                _findMatch(node, i + 1, result, converter);
            } else if (pt instanceof PathToken.Slice) {
                PathToken.Slice slicePt = (PathToken.Slice) pt;
                if (nt.isArray()) {
                    JsonWalker.visitArray(node, (j, v) -> {
                        if (slicePt.match(j)) _findAll(v, nextI, result, converter);
                    });
                }
            } else if (pt instanceof PathToken.Union) {
                PathToken.Union unionPt = (PathToken.Union) pt;
                if (nt.isObject()) {
                    JsonWalker.visitObject(node, (k, v) -> {
                        if (unionPt.match(k)) _findAll(v, nextI, result, converter);
                    });
                } else if (nt.isArray()) {
                    JsonWalker.visitArray(node, (j, v) -> {
                        if (unionPt.match(j)) _findAll(v, nextI, result, converter);
                    });
                }
            } else {
                throw new JsonException("Unexpected path token '" + pt + "'");
            }
            return;
        }
        result.add(converter.apply(node));
    }

    private <T> void _findMatch(Object container, int tokenIdx, List<T> result, Function<Object, T> converter) {
        if (container == null) return;
        PathToken pt = tokens.get(tokenIdx);
        NodeType nt = NodeType.of(container);
        if (nt.isObject()) {
            JsonWalker.visitObject(container, (k, v) -> {
                if (pt.match(k)) {
                    _findAll(v, tokenIdx + 1, result, converter);
                }
                _findMatch(v, tokenIdx, result, converter);
            });
        } else if (nt.isArray()) {
            JsonWalker.visitArray(container, (j, v) -> {
                if (pt.match(j)) {
                    _findAll(v, tokenIdx + 1, result, converter);
                }
                _findMatch(v, tokenIdx, result, converter);
            });
        }
    }

    // 1. Automatically create or extends JsonObject nodes when they do not exist.
    // 2. Automatically create or extends JsonArray nodes when they do not exist and the index is 0.
    // FIXME: Need to support POJO with generic parameter type
    private Object _ensureContainersInPath(Object container) {
        if (!_isSingle()) {
            throw new JsonException("JsonPath '" + this +
                    "' must represent a single node (only Name/Index tokens are allowed to automatically " +
                    "create containers in path.)");
        }

        TypedNode tnode = TypedNode.infer(container);
        for (int i = 1; i < tokens.size() - 1; i++) { // traverse up to the second-last token
            PathToken pt = tokens.get(i);
            NodeType nt = NodeType.of(tnode.getNode());
            if (pt instanceof PathToken.Name) {
                String key = ((PathToken.Name) pt).name;
                if (nt.isObject()) {
                    TypedNode tnn = JsonWalker.getInObjectTyped(tnode, key);
                    if (tnn == null) {
                        throw new JsonException("Cannot access or put field '" + key + "' on an object container '" +
                                tnode.getType() + "'");
                    } else if (tnn.isNull()) {
                        PathToken nextPt = tokens.get(i + 1);
                        Class<?> rawClazz = TypeUtil.getRawClass(tnn.getType());
                        Object nn = _createContainer(nextPt, rawClazz);
                        JsonWalker.putInObject(tnode.getNode(), key, nn);
                        tnode = TypedNode.of(nn, tnn.getType());
                    } else {
                        tnode = tnn;
                    }
                } else {
                    throw new JsonException("Unexpected container type '" + tnode.getType() + "' with name token '" +
                            pt + "'. The type must be one of JsonObject/Map/POJO.");
                }
            } else if (pt instanceof PathToken.Index) {
                int idx = ((PathToken.Index) pt).index;
                if (nt.isArray()) {
                    TypedNode tnn = JsonWalker.getInArrayTyped(tnode, idx);
                    if (tnn == null) {
                        throw new JsonException("Cannot get or set index " + idx + " on an array container '" +
                                tnode.getType() + "'");
                    } else if (tnn.isNull()) {
                        PathToken nextPt = tokens.get(i + 1);
                        Class<?> rawClazz = TypeUtil.getRawClass(tnn.getType());
                        Object nn = _createContainer(nextPt, rawClazz);
                        JsonWalker.setInArray(tnode.getNode(), idx, nn);
                        tnode = TypedNode.of(nn, tnn.getType());
                    } else {
                        tnode = tnn;
                    }
                } else {
                    throw new JsonException("Unexpected container type '" + tnode.getType() + "' with list token " +
                            pt + ". The type must be one of JsonArray/List/Array.");
                }
            } else {
                throw new JsonException("Unexpected path token '" + pt + "'");
            }
        }
        return tnode.getNode(); // last container
    }


    private Object _createContainer(PathToken pt, Class<?> clazz) {
        if (pt instanceof PathToken.Name) {
            if (clazz.isAssignableFrom(JsonObject.class)) {
                return new JsonObject();
            } else if (clazz.isAssignableFrom(Map.class)) {
                return JsonConfig.global().mapSupplier.create();
            } else if (PojoRegistry.isPojo(clazz)) {
                PojoRegistry.PojoInfo pi = PojoRegistry.getPojoInfo(clazz);
                return pi.newInstance();
            } else {
                throw new JsonException("Cannot create container with type '" + clazz + "' at name token '" +
                        pt + "'. The type must be one of JsonObject/Map/POJO.");
            }
        } else if (pt instanceof PathToken.Index) {
            if (clazz.isAssignableFrom(JsonArray.class)) {
                return new JsonArray();
            } else if (clazz.isAssignableFrom(List.class)) {
                return JsonConfig.global().listSupplier.create();
            } else if (clazz.isArray()) {
                int idx = ((PathToken.Index) pt).index;
                return Array.newInstance(clazz.getComponentType(), idx + 1); // size = idx + 1
            } else {
                throw new JsonException("Cannot create container with type '" + clazz +
                        "' at index token '" + pt + "'. The type must be one of JsonArray/List/Array.");
            }
        } else {
            throw new JsonException("Unexpected path token '" + pt + "'");
        }
    }

    private boolean _isSingle() {
        for (PathToken pt : tokens) {
            if (!(pt instanceof PathToken.Root || pt instanceof PathToken.Name || pt instanceof PathToken.Index)) {
                return false;
            }
        }
        return true;
    }

}
