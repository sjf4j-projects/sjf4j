package org.sjf4j.path;

import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.node.NodeType;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.util.PathUtil;
import org.sjf4j.util.PointerUtil;
import org.sjf4j.util.NodeUtil;
import org.sjf4j.util.NumberUtil;
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
     * Creates an empty JsonPath with just a root token.
     */
    public JsonPath() {
        this.tokens = new ArrayList<>();
        push(PathToken.Root.INSTANCE);
    }

    /**
     * Creates a copy of an existing JsonPath instance.
     *
     * @param target the JsonPath to copy
     */
    protected JsonPath(JsonPath target) {
        this.raw = target.raw;
        this.tokens = new ArrayList<>(target.tokens);
    }

    /**
     * Creates a JsonPath from a path expression string.
     *
     * @param expr the path expression to compile
     * @throws IllegalArgumentException if expr is null
     * @throws JsonException if the path expression is invalid
     */
    protected JsonPath(String expr) {
        if (expr == null) throw new IllegalArgumentException("Expr must not be null");
        if (expr.startsWith("$") || expr.startsWith("@")) {
            this.tokens = PathUtil.compile(expr);
        } else if (expr.startsWith("/")) {
            this.tokens = PointerUtil.compile(expr);
        } else {
            throw new JsonException("Invalid path expression '" + expr + "'. " +
                    "Must start with '$' or '@' for JSON Path, or '/' for JSON Pointer.");
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
        return PathUtil.genExpr(tokens);
    }

    /**
     * Converts the path tokens to a JSON Pointer expression string.
     *
     * @return the JSON Pointer expression string
     */
    public String toPointerExpr() {
        return PointerUtil.genExpr(tokens);
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
    public PathToken head() {
        return tokens.get(0);
    }

    /**
     * Returns the last token in the path without removing it.
     *
     * @return the last path token
     */
    public PathToken tail() {
        return tokens.get(tokens.size() - 1);
    }

    /**
     * Pushes a new path token to the end of the path.
     *
     * @param token the token to add
     * @throws IllegalArgumentException if token is null
     */
    public void push(PathToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Token must not be null");
        }
        tokens.add(token);
    }

    /**
     * Removes the last token in the path, and returns it.
     *
     * @return the removed path token
     */
    public PathToken pop() {
        return tokens.remove(tokens.size() - 1);
    }

    /// Find

    /**
     * Finds an object at this path in the given container.
     *
     * @param container the JSON container to search
     * @return the object found at the path, or null if not found
     * @throws IllegalArgumentException if container is null
     */
    public Object getNode(Object container) {
        if (container == null) throw new IllegalArgumentException("Container must not be null");
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
            return NodeUtil.toString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find String by path '" + this + "'", e);
        }
    }
    public String getString(Object container, String defaultValue) {
        String value = getString(container);
        return value == null ? defaultValue : value;
    }

    public String asString(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.asString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to String", e);
        }
    }
    public String asString(Object container, String defaultValue) {
        String value = asString(container);
        return value == null ? defaultValue : value;
    }


    // Long
    public Long getLong(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.toLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Long by path '" + this + "'", e);
        }
    }
    public long getLong(Object container, long defaultValue) {
        Long value = getLong(container);
        return value == null ? defaultValue : value;
    }

    public Long asLong(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.asLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Long", e);
        }
    }
    public long asLong(Object container, long defaultValue) {
        Long value = asLong(container);
        return value == null ? defaultValue : value;
    }

    // Integer
    public Integer getInteger(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.toInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Integer by path '" + this + "'", e);
        }
    }
    public int getInteger(Object container, int defaultValue) {
        Integer value = getInteger(container);
        return value == null ? defaultValue : value;
    }

    public Integer asInteger(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.asInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Integer", e);
        }
    }
    public int asInteger(Object container, int defaultValue) {
        Integer value = asInteger(container);
        return value == null ? defaultValue : value;
    }

    // Short
    public Short getShort(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.toShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Short by path '" + this + "'", e);
        }
    }
    public short getShort(Object container, short defaultValue) {
        Short value = getShort(container);
        return value == null ? defaultValue : value;
    }

    public Short asShort(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.asShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Short", e);
        }
    }
    public short asShort(Object container, short defaultValue) {
        Short value = asShort(container);
        return value == null ? defaultValue : value;
    }

    // Byte
    public Byte getByte(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.toByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Byte by path '" + this + "'", e);
        }
    }
    public byte getByte(Object container, byte defaultValue) {
        Byte value = getByte(container);
        return value == null ? defaultValue : value;
    }

    public Byte asByte(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.asByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Byte", e);
        }
    }
    public byte asByte(Object container, byte defaultValue) {
        Byte value = asByte(container);
        return value == null ? defaultValue : value;
    }

    // Double
    public Double getDouble(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.toDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Double by path '" + this + "'", e);
        }
    }
    public double getDouble(Object container, double defaultValue) {
        Double value = getDouble(container);
        return value == null ? defaultValue : value;
    }

    public Double asDouble(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.asDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Double", e);
        }
    }
    public double asDouble(Object container, double defaultValue) {
        Double value = asDouble(container);
        return value == null ? defaultValue : value;
    }

    // Float
    public Float getFloat(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.toFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Float by path '" + this + "'", e);
        }
    }
    public float getFloat(Object container, float defaultValue) {
        Float value = getFloat(container);
        return value == null ? defaultValue : value;
    }

    public Float asFloat(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.asFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Float", e);
        }
    }
    public float asFloat(Object container, float defaultValue) {
        Float value = asFloat(container);
        return value == null ? defaultValue : value;
    }

    // BigInteger
    public BigInteger getBigInteger(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.toBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find BigInteger by path '" + this + "'", e);
        }
    }
    public BigInteger getBigInteger(Object container, BigInteger defaultValue) {
        BigInteger value = getBigInteger(container);
        return value == null ? defaultValue : value;
    }

    public BigInteger asBigInteger(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.asBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to BigInteger", e);
        }
    }
    public BigInteger asBigInteger(Object container, BigInteger defaultValue) {
        BigInteger value = asBigInteger(container);
        return value == null ? defaultValue : value;
    }

    // BigDecimal
    public BigDecimal getBigDecimal(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.toBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find BigDecimal by path '" + this + "'", e);
        }
    }
    public BigDecimal getBigDecimal(Object container, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(container);
        return value == null ? defaultValue : value;
    }

    public BigDecimal asBigDecimal(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.asBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to BigDecimal", e);
        }
    }
    public BigDecimal asBigDecimal(Object container, BigDecimal defaultValue) {
        BigDecimal value = asBigDecimal(container);
        return value == null ? defaultValue : value;
    }

    // Boolean
    public Boolean getBoolean(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.toBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find Boolean by path '" + this + "'", e);
        }
    }
    public boolean getBoolean(Object container, boolean defaultValue) {
        Boolean value = getBoolean(container);
        return value == null ? defaultValue : value;
    }

    public Boolean asBoolean(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.asBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to Boolean", e);
        }
    }
    public boolean asBoolean(Object container, boolean defaultValue) {
        Boolean value = asBoolean(container);
        return value == null ? defaultValue : value;
    }

    // JsonObject
    public JsonObject getJsonObject(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.toJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find JsonObject by path '" + this + "'", e);
        }
    }
    public JsonObject getJsonObject(Object container, JsonObject defaultValue) {
        JsonObject value = getJsonObject(container);
        return value == null ? defaultValue : value;
    }

    public JsonObject asJsonObject(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.asJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to JsonObject", e);
        }
    }
    public JsonObject asJsonObject(Object container, JsonObject defaultValue) {
        JsonObject value = asJsonObject(container);
        return value == null ? defaultValue : value;
    }

    // JsonArray
    public JsonArray getJsonArray(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.toJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to find JsonArray by path '" + this + "'", e);
        }
    }
    public JsonArray getJsonArray(Object container, JsonArray defaultValue) {
        JsonArray value = getJsonArray(container);
        return value == null ? defaultValue : value;
    }

    public JsonArray asJsonArray(Object container) {
        try {
            Object value = getNode(container);
            return NodeUtil.asJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to JsonArray", e);
        }
    }

    public JsonArray asJsonArray(Object container, JsonArray defaultValue) {
        JsonArray value = asJsonArray(container);
        return value == null ? defaultValue : value;
    }

    public <T> T get(Object container, Class<T> clazz) {
        try {
            Object value = getNode(container);
            return NodeUtil.to(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to find '" + clazz + "' by path '" + this + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Object container, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(container, clazz);
    }

    public <T> T as(Object container, Class<T> clazz) {
        try {
            Object value = getNode(container);
            return NodeUtil.as(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at path '" + this + "' to '" + clazz + "'", e);
        }
    }
    @SuppressWarnings("unchecked")
    public <T> T as(Object container, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return as(container, clazz);
    }


    /// All
    public List<Object> findNodes(Object container) {
        if (container == null) throw new IllegalArgumentException("Container must not be null");
        List<Object> result = new ArrayList<>();
        _findAll(container, container, 1, result, (n) -> n);
        return result;
    }

    public <T> List<T> find(Object container, Class<T> clazz) {
        if (container == null) throw new IllegalArgumentException("Container must not be null");
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        List<T> result = new ArrayList<>();
        _findAll(container, container, 1, result, (n) -> NodeUtil.to(n, clazz));
        return result;
    }

    public <T> List<T> findAs(Object container, Class<T> clazz) {
        if (container == null) throw new IllegalArgumentException("Container must not be null");
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        List<T> result = new ArrayList<>();
        _findAll(container, container, 1, result, (n) -> NodeUtil.as(n, clazz));
        return result;
    }

    // eval() is more powerful than get() / find()
    public Object eval(Object container) {
        if (container == null) throw new IllegalArgumentException("Container must not be null");
        List<Object> result = new ArrayList<>();
        _findAll(container, container, 1, result, (n) -> n);
        if (result.isEmpty()) return null;

        PathToken tk = tokens.get(tokens.size() - 1);
        if (tk instanceof PathToken.Function) {
            PathToken.Function func = (PathToken.Function) tk;
            Object[] args = new Object[1 + func.args.size()];
            args[0] = (result.size() == 1 ? result.get(0) : result);
            for (int i = 0; i < func.args.size(); i++) {
                args[1 + i] = resolveFunctionArg(func.args.get(i));
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
            return NodeUtil.to(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to eval '" + clazz + "' by path '" + this + "'", e);
        }
    }

    public <T> T evalAs(Object container, Class<T> clazz) {
        try {
            Object value = eval(container);
            return NodeUtil.as(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert eval-value at path '" + this + "' to '" + clazz + "'", e);
        }
    }

    /// ensurePut

    public Object ensurePut(Object container, Object value) {
        if (container == null) throw new IllegalArgumentException("Container must not be null");
        Object lastContainer = _ensureContainersInPath(container);
        PathToken lastToken = tail();
        if (lastToken instanceof PathToken.Name) {
            String name = ((PathToken.Name) lastToken).name;
            return NodeWalker.putInObject(lastContainer, name, value);
        } else if (lastToken instanceof PathToken.Index) {
            int idx = ((PathToken.Index) lastToken).index;
            NodeWalker.setInArray(lastContainer, idx, value);
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
        if (computer == null) throw new IllegalArgumentException("Computer must not be null");
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
        if (container == null) throw new IllegalArgumentException("Container must not be null");
        Object penult = _findOne(container, -1);
        if (penult == null) return false;
        PathToken lastToken = tail();
        if (lastToken instanceof PathToken.Name) {
            String name = ((PathToken.Name) lastToken).name;
            return NodeWalker.containsInObject(penult, name);
        } else if (lastToken instanceof PathToken.Index) {
            int index = ((PathToken.Index) lastToken).index;
            return NodeWalker.containsInArray(penult, index);
        } else if (lastToken instanceof PathToken.Append) {
            return false;
        } else {
            throw new JsonException("contains() not supported for last path token '" + lastToken +
                    "'; expected Name, Index, or Append token");
        }
    }

    /// JSON Patch: add, replace, remove

    public void add(Object container, Object value) {
        if (container == null) throw new IllegalArgumentException("Container must not be null");
        Object penult = _findOne(container, -1);
        if  (penult == null)
            throw new JsonException("Parent container at the penultimate path token does not exist");

        PathToken lastToken = tail();
        if (lastToken instanceof PathToken.Name) {
            String name = ((PathToken.Name) lastToken).name;
            NodeWalker.putInObject(penult, name, value);
        } else if (lastToken instanceof PathToken.Index) {
            int idx = ((PathToken.Index) lastToken).index;
            NodeWalker.addInArray(penult, idx, value);
        } else if (lastToken instanceof PathToken.Append) {
            NodeWalker.addInArray(penult, value);
        } else {
            throw new JsonException("add() not supported for last path token '" + lastToken +
                    "'; expected Name, Index, or Append token");
        }
    }

    public Object replace(Object container, Object value) {
        if (container == null) throw new IllegalArgumentException("Container must not be null");
        Object penult = _findOne(container, -1);
        if  (penult == null)
            throw new JsonException("Parent container at the penultimate path token does not exist");

        PathToken lastToken = tail();
        if (lastToken instanceof PathToken.Name) {
            String name = ((PathToken.Name) lastToken).name;
            return NodeWalker.putInObject(penult, name, value);
        } else if (lastToken instanceof PathToken.Index) {
            int idx = ((PathToken.Index) lastToken).index;
            return NodeWalker.setInArray(penult, idx, value);
        } else {
            throw new JsonException("add() not supported for last path token '" + lastToken +
                    "'; expected Name or Index token");
        }
    }

    public Object remove(Object container) {
        if (container == null) throw new IllegalArgumentException("Container must not be null");
        Object penult = _findOne(container, -1);
        if  (penult == null) return null;

        PathToken lastToken = tail();
        if (lastToken instanceof PathToken.Name) {
            String name = ((PathToken.Name) lastToken).name;
            return NodeWalker.removeInObject(penult, name);
        } else if (lastToken instanceof PathToken.Index) {
            int index = ((PathToken.Index) lastToken).index;
            return NodeWalker.removeInArray(penult, index);
        } else if (lastToken instanceof PathToken.Append) {
            return null;
        } else {
            throw new JsonException("remove() not supported for last path token '" + lastToken +
                    "'; expected Name, Index, or Append token");
        }
    }


    /// private

    private Object _findOne(Object container, int tailIndex) {
        Object node = container;
        for (int i = 1; i < tokens.size() + tailIndex; i++) {
            if (node == null) return null;
            PathToken pt = tokens.get(i);
            NodeType nt = NodeType.of(node);
            if (pt instanceof PathToken.Name) {
                if (nt.isObject()) {
                    node = NodeWalker.getInObject(node, ((PathToken.Name) pt).name);
                } else {
                    return null;
                }
            } else if (pt instanceof PathToken.Index) {
                if (nt.isArray()) {
                    node = NodeWalker.getInArray(node, ((PathToken.Index) pt).index);
                } else {
                    return null;
                }
            } else if (pt instanceof PathToken.Descendant) {
                if (i + 1 >= tokens.size()) throw new JsonException("Descendant '..' cannot appear at the end.");
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
        for (int i = tokenIdx; i < tokens.size(); i++) {
            if (node ==  null) return;
            PathToken pt = tokens.get(i);
            if (i == tokens.size() - 1 && pt instanceof PathToken.Function) break;
            NodeType nt = NodeType.of(node);
            final int nextI = i + 1;
            if (pt instanceof PathToken.Name) {
                if (nt.isObject()) {
                    String name = ((PathToken.Name) pt).name;
                    if (NodeWalker.containsInObject(node, name)) {
                        node = NodeWalker.getInObject(node, name);
                        continue;
                    }
                }
            } else if (pt instanceof PathToken.Index) {
                if (nt.isArray()) {
                    node = NodeWalker.getInArray(node, ((PathToken.Index) pt).index);
                    continue;
                }
            } else if (pt instanceof PathToken.Wildcard) {
                if (nt.isObject()) {
                    NodeWalker.visitObject(node, (k, v) -> _findAll(root, v, nextI, result, converter));
                } else if (nt.isArray()) {
                    NodeWalker.visitArray(node, (j, v) -> _findAll(root, v, nextI, result, converter));
                }
            } else if (pt instanceof PathToken.Descendant) {
                if (i + 1 >= tokens.size()) throw new JsonException("Descendant '..' cannot appear at the end.");
                _findMatch(root, node, i + 1, result, converter);
            } else if (pt instanceof PathToken.Slice) {
                PathToken.Slice slicePt = (PathToken.Slice) pt;
                if (nt.isArray()) {
                    NodeWalker.visitArray(node, (j, v) -> {
                        if (slicePt.matchIndex(j)) _findAll(root, v, nextI, result, converter);
                    });
                }
            } else if (pt instanceof PathToken.Union) {
                PathToken.Union unionPt = (PathToken.Union) pt;
                if (nt.isObject()) {
                    NodeWalker.visitObject(node, (k, v) -> {
                        if (unionPt.matchKey(k)) _findAll(root, v, nextI, result, converter);
                    });
                } else if (nt.isArray()) {
                    NodeWalker.visitArray(node, (j, v) -> {
                        if (unionPt.matchIndex(j)) _findAll(root, v, nextI, result, converter);
                    });
                }
            } else if (pt instanceof PathToken.Filter) {
                PathToken.Filter filterPt = (PathToken.Filter) pt;
                if (nt.isArray()) {
                    NodeWalker.visitArray(node, (j, v) -> {
                        if (filterPt.filterExpr.evalTruth(root, v)) {
                            _findAll(root, v, nextI, result, converter);
                        }
                    });
                } else if (nt.isObject()) {
                    NodeWalker.visitObject(node, (k, v) -> {
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
        PathToken pt = tokens.get(tokenIdx);
        NodeType nt = NodeType.of(current);
        if (nt.isObject()) {
            NodeWalker.visitObject(current, (k, v) -> {
                if (pt.matchKey(k)) {
                    _findAll(root, v, tokenIdx + 1, result, converter);
                }
                _findMatch(root, v, tokenIdx, result, converter);
            });
        } else if (nt.isArray()) {
            NodeWalker.visitArray(current, (j, v) -> {
                if (pt.matchIndex(j)) {
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
                    TypedNode tnn = NodeWalker.getInObjectTyped(tnode, key);
                    if (tnn == null) {
                        throw new JsonException("Cannot access or put field '" + key + "' on an object container '" +
                                tnode.getType() + "'");
                    } else if (tnn.isNull()) {
                        PathToken nextPt = tokens.get(i + 1);
                        Class<?> rawClazz = TypeUtil.getRawClass(tnn.getType());
                        Object nn = _createContainer(nextPt, rawClazz);
                        NodeWalker.putInObject(tnode.getNode(), key, nn);
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
                    TypedNode tnn = NodeWalker.getInArrayTyped(tnode, idx);
                    if (tnn == null) {
                        throw new JsonException("Cannot get or set index " + idx + " on an array container '" +
                                tnode.getType() + "'");
                    } else if (tnn.isNull()) {
                        PathToken nextPt = tokens.get(i + 1);
                        Class<?> rawClazz = TypeUtil.getRawClass(tnn.getType());
                        Object nn = _createContainer(nextPt, rawClazz);
                        NodeWalker.setInArray(tnode.getNode(), idx, nn);
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
            if (clazz.isAssignableFrom(Map.class) || Map.class.isAssignableFrom(clazz)) {
                return Sjf4jConfig.global().mapSupplier.create();
            } else if (clazz.isAssignableFrom(JsonObject.class)) {
                return new JsonObject();
            } else if (NodeRegistry.isPojo(clazz)) {
                NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(clazz);
                return pi.newInstance();
            } else {
                throw new JsonException("Cannot create container with type '" + clazz + "' at name token '" +
                        pt + "'. The type must be one of JsonObject/Map/POJO.");
            }
        } else if (pt instanceof PathToken.Index) {
            if (clazz.isAssignableFrom(List.class) || List.class.isAssignableFrom(clazz)) {
                return Sjf4jConfig.global().listSupplier.create();
            } else if (clazz.isAssignableFrom(JsonArray.class)) {
                return new JsonArray();
            } else if (JsonArray.class.isAssignableFrom(clazz)) {
                NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(clazz);
                return pi.newInstance();
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

    private Object resolveFunctionArg(String raw) {
        if ((raw.startsWith("'") && raw.endsWith("'")) || (raw.startsWith("\"") && raw.endsWith("\""))) {
            return raw.substring(1, raw.length() - 1);
        } else if ("true".equals(raw)) {
            return true;
        } else if ("false".equals(raw)) {
            return false;
        } else if ("null".equals(raw)) {
            return null;
        } else if (NumberUtil.isNumeric(raw)) {
            return NumberUtil.toNumber(raw);
        } else {
            throw new JsonException("Invalid raw argument '" + raw + "'");
        }
    }

}
