package org.sjf4j;

import lombok.NonNull;
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

public class JsonPath {

    private String raw;
    private final List<PathToken> tokens;

    private JsonPath(JsonPath target) {
        this.raw = target.raw;
        this.tokens = new ArrayList<>(target.tokens);
    }

    public JsonPath() {
        this.tokens = new ArrayList<>();
        push(new PathToken.Root());
    }

    public JsonPath(@NonNull String expr) {
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
    
    public static JsonPath compile(@NonNull String expr) {
        // Consider add cache here
        return new JsonPath(expr);
    }

    public String toExpr() {
        return JsonPathUtil.genExpr(tokens);
    }

    public String toPointerExpr() {
        return JsonPointerUtil.genExpr(tokens);
    }

    public int getDepth() {
        return tokens.size();
    }

    @Override
    public String toString() {
        return raw == null ? toExpr() : raw;
    }

    public JsonPath push(@NonNull PathToken token) {
        tokens.add(token);
        return this;
    }

    public PathToken peek() {
        return tokens.get(tokens.size() - 1);
    }

    public JsonPath copy() {
        return new JsonPath(this);
    }


    /// Read

    // Object
    public Object readObject(@NonNull Object container) {
        return _findOne(container);
    }

    public Object readObject(@NonNull Object container, Object defaultValue) {
        Object value = readObject(container);
        return null == value ? defaultValue : value;
    }

    // String
    public String readString(@NonNull Object container) {
        try {
            Object value = readObject(container);
            return NodeUtil.toString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to read String by path '" + raw + "': " + e.getMessage(), e);
        }
    }

    public String readString(@NonNull Object container, String defaultValue) {
        String value = readString(container);
        return value == null ? defaultValue : value;
    }

    // Long
    public Long readLong(@NonNull Object container) {
        try {
            Object value = readObject(container);
            return NodeUtil.asLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to read Long by path '" + raw + "': " + e.getMessage(), e);
        }
    }

    public long readLong(@NonNull Object container, long defaultValue) {
        Long value = readLong(container);
        return value == null ? defaultValue : value;
    }

    // Integer
    public Integer readInteger(@NonNull Object container) {
        try {
            Object value = readObject(container);
            return NodeUtil.asInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to read Integer by path '" + raw + "': " + e.getMessage(), e);
        }
    }

    public int readInteger(@NonNull Object container, int defaultValue) {
        Integer value = readInteger(container);
        return value == null ? defaultValue : value;
    }

    // Short
    public Short readShort(@NonNull Object container) {
        try {
            Object value = readObject(container);
            return NodeUtil.asShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to read Short by path '" + raw + "': " + e.getMessage(), e);
        }
    }

    public short readShort(@NonNull Object container, short defaultValue) {
        Short value = readShort(container);
        return value == null ? defaultValue : value;
    }

    // Byte
    public Byte readByte(@NonNull Object container) {
        try {
            Object value = readObject(container);
            return NodeUtil.asByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to read Byte by path '" + raw + "': " + e.getMessage(), e);
        }
    }

    public byte readByte(@NonNull Object container, byte defaultValue) {
        Byte value = readByte(container);
        return value == null ? defaultValue : value;
    }

    // Double
    public Double readDouble(@NonNull Object container) {
        try {
            Object value = readObject(container);
            return NodeUtil.asDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to read Double by path '" + raw + "': " + e.getMessage(), e);
        }
    }

    public double readDouble(@NonNull Object container, double defaultValue) {
        Double value = readDouble(container);
        return value == null ? defaultValue : value;
    }

    // Float
    public Float readFloat(@NonNull Object container) {
        try {
            Object value = readObject(container);
            return NodeUtil.asFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to read Float by path '" + raw + "': " + e.getMessage(), e);
        }
    }

    public float readFloat(@NonNull Object container, float defaultValue) {
        Float value = readFloat(container);
        return value == null ? defaultValue : value;
    }

    // BigInteger
    public BigInteger readBigInteger(@NonNull Object container) {
        try {
            Object value = readObject(container);
            return NodeUtil.asBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to read BigInteger by path '" + raw + "': " + e.getMessage(), e);
        }
    }

    public BigInteger readBigInteger(@NonNull Object container, BigInteger defaultValue) {
        BigInteger value = readBigInteger(container);
        return value == null ? defaultValue : value;
    }

    // BigDecimal
    public BigDecimal readBigDecimal(@NonNull Object container) {
        try {
            Object value = readObject(container);
            return NodeUtil.asBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to read BigDecimal by path '" + raw + "': " + e.getMessage(), e);
        }
    }

    public BigDecimal readBigDecimal(@NonNull Object container, BigDecimal defaultValue) {
        BigDecimal value = readBigDecimal(container);
        return value == null ? defaultValue : value;
    }

    // Boolean
    public Boolean readBoolean(@NonNull Object container) {
        try {
            Object value = readObject(container);
            return NodeUtil.toBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to read Boolean by path '" + raw + "': " + e.getMessage(), e);
        }
    }

    public boolean readBoolean(@NonNull Object container, boolean defaultValue) {
        Boolean value = readBoolean(container);
        return value == null ? defaultValue : value;
    }

    // JsonObject
    public JsonObject readJsonObject(@NonNull Object container) {
        try {
            Object value = readObject(container);
            return NodeUtil.toJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to read JsonObject by path '" + raw + "': " + e.getMessage(), e);
        }
    }

    public JsonObject readJsonObject(@NonNull Object container, JsonObject defaultValue) {
        JsonObject value = readJsonObject(container);
        return value == null ? defaultValue : value;
    }

    // JsonArray
    public JsonArray readJsonArray(@NonNull Object container) {
        try {
            Object value = readObject(container);
            return NodeUtil.toJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to read JsonArray by path '" + raw + "': " + e.getMessage(), e);
        }
    }

    public JsonArray readJsonArray(@NonNull Object container, JsonArray defaultValue) {
        JsonArray value = readJsonArray(container);
        return value == null ? defaultValue : value;
    }

    public List<Object> readAll(@NonNull Object container) {
        List<Object> result = new ArrayList<>();
        _findAll(container, 1, result);
        return result;
    }

    /// Put

    public void put(@NonNull Object container, Object value) {
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

    public boolean hasNonNull(@NonNull Object container) {
        return readObject(container) != null;
    }

    public void putNonNull(@NonNull Object container, Object value) {
        if (null != value) { put(container, value); }
    }

    public void putIfAbsentOrNull(@NonNull Object container, Object value) {
        if (!hasNonNull(container)) { put(container, value); }
    }

    public void remove(@NonNull Object container) {
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

    public Object _findOne(@NonNull Object container) {
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
                _findMatch(node, i + 1, result);
                if (result.isEmpty()) {
                    return null;
                } else if (result.size() == 1) {
                    return result.get(0);
                } else {
                    throw new JsonException("Path matched " + result.size() +
                            " results, but read() returns only one value. Use readAll() to retrieve all matches.");
                }
            } else {
                throw new JsonException("Unsupported path token '" + pt + "' in read()");
            }
        }
        return node;
    }


    // In the future, the `result` could be replaced with a callback to allow more flexible handling of matches.
    private void _findAll(Object container, int tokenIdx, List<Object> result) {
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
                    JsonWalker.visitObject(node, (k, v) -> _findAll(v, nextI, result));
                } else if (nt.isArray()) {
                    JsonWalker.visitArray(node, (j, v) -> _findAll(v, nextI, result));
                }
            } else if (pt instanceof PathToken.Descendant) {
                if (i + 1 >= tokens.size()) throw new JsonException("Descendant '..' cannot appear at the end.");
                _findMatch(node, i + 1, result);
            } else if (pt instanceof PathToken.Slice) {
                PathToken.Slice slicePt = (PathToken.Slice) pt;
                if (nt.isArray()) {
                    JsonWalker.visitArray(node, (j, v) -> {
                        if (slicePt.match(j)) _findAll(v, nextI, result);
                    });
                }
            } else if (pt instanceof PathToken.Union) {
                PathToken.Union unionPt = (PathToken.Union) pt;
                if (nt.isObject()) {
                    JsonWalker.visitObject(node, (k, v) -> {
                        if (unionPt.match(k)) _findAll(v, nextI, result);
                    });
                } else if (nt.isArray()) {
                    JsonWalker.visitArray(node, (j, v) -> {
                        if (unionPt.match(j)) _findAll(v, nextI, result);
                    });
                }
            } else {
                throw new JsonException("Unexpected path token '" + pt + "'");
            }
            return;
        }

        result.add(node);
    }

    private void _findMatch(Object container, int tokenIdx, List<Object> result) {
        if (container == null) return;
        PathToken pt = tokens.get(tokenIdx);
        NodeType nt = NodeType.of(container);
        if (nt.isObject()) {
            JsonWalker.visitObject(container, (k, v) -> {
                if (pt.match(k)) {
                    _findAll(v, tokenIdx + 1, result);
                }
                _findMatch(v, tokenIdx, result);
            });
        } else if (nt.isArray()) {
            JsonWalker.visitArray(container, (j, v) -> {
                if (pt.match(j)) {
                    _findAll(v, tokenIdx + 1, result);
                }
                _findMatch(v, tokenIdx, result);
            });
        }
    }

    // 1. Automatically create or extends JsonObject nodes when they do not exist.
    // 2. Automatically create or extends JsonArray nodes when they do not exist and the index is 0.
    // FIXME: Need to support POJO with generic parameter type
    private Object _ensureContainersInPath(@NonNull Object container) {
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
