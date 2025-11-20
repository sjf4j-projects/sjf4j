package org.sjf4j;

import lombok.NonNull;
import org.sjf4j.util.JsonPathUtil;
import org.sjf4j.util.JsonPointerUtil;
import org.sjf4j.util.NodeUtil;
import org.sjf4j.util.TypeUtil;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
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

    @SuppressWarnings("unchecked")
    public void put(@NonNull Object container, Object value) {
        Object lastContainer = _autoCreateContainers(container);
        PathToken lastToken = peek();
        if (lastToken instanceof PathToken.Name) {
            if (lastContainer instanceof JsonObject) {
                ((JsonObject) lastContainer).put(((PathToken.Name) lastToken).name, value);
            } else if (lastContainer instanceof Map) {
                ((Map<String, Object>) lastContainer).put(((PathToken.Name) lastToken).name, value);
            } else if (PojoRegistry.isPojo(lastContainer.getClass())) {
                String name = ((PathToken.Name) lastToken).name;
                PojoRegistry.FieldInfo fi = PojoRegistry.getFieldInfo(lastContainer.getClass(), name);
                if (fi != null) {
                    fi.invokeSetter(lastContainer, value);
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
                JsonArray ja = (JsonArray) lastContainer;
                int idx = ((PathToken.Index) lastToken).index;
                if (idx == ja.size()) {
                    ja.add(value);
                } else if (ja.containsIndex(idx)) {
                    ja.set(idx, value);
                } else {
                    throw new JsonException("Cannot set/add index " + idx + " in JsonArray of size " +
                            ja.size() + " (index < size: modify; index == size: append)");
                }
            } else if (lastContainer instanceof List) {
                List<Object> list = (List<Object>) lastContainer;
                int idx = ((PathToken.Index) lastToken).index;
                idx = idx < 0 ? list.size() + idx : idx;
                if (idx == list.size()) {
                    list.add(value);
                } else if (idx >= 0 && idx < list.size()) {
                    list.set(idx, value);
                } else {
                    throw new JsonException("Cannot set/add index " + idx + " in List of size " +
                            list.size() + " (index < size: modify; index == size: append)");
                }
            } else if (lastContainer.getClass().isArray()) {
                int idx = ((PathToken.Index) lastToken).index;
                int len = Array.getLength(lastContainer);
                idx = idx < 0 ? len + idx : idx;
                if (idx >= 0 && idx < len) {
                    Array.set(lastContainer, idx, value);
                } else {
                    throw new JsonException("Cannot set index " + idx + " in Array of size " +
                            len + " (index < size: modify)");
                }
            } else {
                throw new JsonException("Mismatched path token " + lastToken + " with container type '" +
                        lastContainer.getClass() + "'");
            }
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
            Object lastContainer = _autoCreateContainers(container);
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
            if (pt instanceof PathToken.Name) {
                if (node instanceof JsonObject) {
                    node = ((JsonObject) node).getObject(((PathToken.Name) pt).name);
                } else if (node instanceof Map) {
                    node = ((Map<?, ?>) node).get(((PathToken.Name) pt).name);
                } else if (PojoRegistry.isPojo(node.getClass())) {
                    PojoRegistry.FieldInfo fi = PojoRegistry.getFieldInfo(node.getClass(),((PathToken.Name) pt).name);
                    if (fi != null) {
                        node = fi.invokeGetter(node);
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else if (pt instanceof PathToken.Index) {
                if (node instanceof JsonArray) {
                    node = ((JsonArray) node).getObject(((PathToken.Index) pt).index);
                } else if (node instanceof List) {
                    List<?> list = (List<?>) node;
                    int idx = ((PathToken.Index) pt).index;
                    idx = idx < 0 ? list.size() + idx : idx;
                    if (idx >= 0 && idx < list.size()) {
                        node = list.get(idx);
                    } else {
                        return null;
                    }
                } else if (node.getClass().isArray()) {
                    int idx = ((PathToken.Index) pt).index;
                    int len = Array.getLength(node);
                    idx = idx < 0 ? len + idx : idx;
                    if (idx >= 0 && idx < len) {
                        node = Array.get(node, idx);
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else if (pt instanceof PathToken.Recursive) {
                if (i + 1 >= tokens.size()) throw new JsonException("Recursive cannot appear at the end.");
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


    private void _findAll(Object container, int tokenIdx, List<Object> result) {
        Object node = container;

        for (int i = tokenIdx; i < tokens.size(); i++) {
            if (node ==  null) return;
            PathToken pt = tokens.get(i);
            if (pt instanceof PathToken.Name) {
                if (node instanceof JsonObject) {
                    node = ((JsonObject) node).getObject(((PathToken.Name) pt).name);
                    continue;
                } else if (node instanceof Map) {
                    node = ((Map<?, ?>) node).get(((PathToken.Name) pt).name);
                    continue;
                } else if (PojoRegistry.isPojo(node.getClass())) {
                    PojoRegistry.FieldInfo fi = PojoRegistry.getFieldInfo(node.getClass(),((PathToken.Name) pt).name);
                    if (fi != null) {
                        node = fi.invokeGetter(node);
                        continue;
                    }
                }
            } else if (pt instanceof PathToken.Index) {
                if (node instanceof JsonArray) {
                    node = ((JsonArray) node).getObject(((PathToken.Index) pt).index);
                    continue;
                } else if (node instanceof List) {
                    List<?> list = (List<?>) node;
                    int idx = ((PathToken.Index) pt).index;
                    idx = idx < 0 ? list.size() + idx : idx;
                    if (idx >= 0 && idx < list.size()) {
                        node = list.get(idx);
                        continue;
                    }
                } else if (node.getClass().isArray()) {
                    int idx = ((PathToken.Index) pt).index;
                    int len = Array.getLength(node);
                    idx = idx < 0 ? len + idx : idx;
                    if (idx >= 0 && idx < len) {
                        node = Array.get(node, idx);
                        continue;
                    }
                }
            } else if (pt instanceof PathToken.Wildcard) {
                if (node instanceof JsonObject) {
                    final int finalI = i;
                    ((JsonObject) node).forEach((k, v) -> {
                        _findAll(v, finalI + 1, result);
                    });
                } else if (node instanceof JsonArray) {
                    for (Object val : ((JsonArray) node)) {
                        _findAll(val, i + 1, result);
                    }
                } else if (node instanceof Map) {
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                        _findAll(entry.getValue(), i + 1, result);
                    }
                } else if (node instanceof List) {
                    for (Object val : ((List<?>) node)) {
                        _findAll(val, i + 1, result);
                    }
                } else if (node.getClass().isArray()) {
                    for (int j = 0; j < Array.getLength(node); j++) {
                        _findAll(Array.get(node, j), i + 1, result);
                    }
                } else if (PojoRegistry.isPojo(node.getClass())) {
                    PojoRegistry.PojoInfo pi = PojoRegistry.getPojoInfo(node.getClass());
                    for (Map.Entry<String, PojoRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                        _findAll(fi.getValue().invokeGetter(node), i + 1, result);
                    }
                }
            } else if (pt instanceof PathToken.Recursive) {
                if (i + 1 >= tokens.size()) throw new JsonException("Recursive cannot appear at the end.");
                _findMatch(node, i + 1, result);
            } else if (pt instanceof PathToken.Slice) {
                PathToken.Slice slicePt = (PathToken.Slice) pt;
                if (node instanceof JsonArray) {
                    final int finalI = i;
                    ((JsonArray) node).forEach((j, v) -> {
                        if (slicePt.match(j)) _findAll(v, finalI + 1, result);
                    });
                } else if (node instanceof List) {
                    List<?> list = (List<?>) node;
                    for (int j = 0; j < list.size(); j++) {
                        if (slicePt.match(j)) _findAll(list.get(j), i + 1, result);
                    }
                } else if (node.getClass().isArray()) {
                    for (int j = 0; j < Array.getLength(node); j++) {
                        if (slicePt.match(j)) _findAll(Array.get(node, j), i + 1, result);
                    }
                }
            } else if (pt instanceof PathToken.Union) {
                PathToken.Union unionPt = (PathToken.Union) pt;
                if (node instanceof JsonObject) {
                    final int finalI = i;
                    ((JsonObject) node).forEach((k, v) -> {
                        if (unionPt.match(k)) _findAll(v, finalI + 1, result);
                    });
                } else if (node instanceof Map) {
                    final int finalI = i;
                    ((Map<?, ?>) node).forEach((k, v) -> {
                        if (unionPt.match(k)) _findAll(v, finalI + 1, result);
                    });
                } else if (node instanceof JsonArray) {
                    final int finalI = i;
                    ((JsonArray) node).forEach((j, v) -> {
                        if (unionPt.match(j)) _findAll(v, finalI + 1, result);
                    });
                } else if (node instanceof List) {
                    List<?> list = (List<?>) node;
                    for (int j = 0; j < list.size(); j++) {
                        if (unionPt.match(j)) _findAll(list.get(j), i + 1, result);
                    }
                } else if (node.getClass().isArray()) {
                    for (int j = 0; j < Array.getLength(node); j++) {
                        if (unionPt.match(j)) _findAll(Array.get(node, j), i + 1, result);
                    }
                } else if (PojoRegistry.isPojo(node.getClass())) {
                    PojoRegistry.PojoInfo pi = PojoRegistry.getPojoInfo(node.getClass());
                    for (Map.Entry<String, PojoRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                        if (unionPt.match(fi.getKey())) _findAll(fi.getValue().invokeGetter(node), i + 1, result);
                    }
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
        if (container instanceof JsonObject) {
            ((JsonObject) container).forEach((k, v) -> {
                if (pt.match(k)) {
                    _findAll(v, tokenIdx + 1, result);
                }
                _findMatch(v, tokenIdx, result);
            });
        } else if (container instanceof JsonArray) {
            ((JsonArray) container).forEach((i, v) -> {
                if (pt.match(i)) {
                    _findAll(v, tokenIdx + 1, result);
                }
                _findMatch(v, tokenIdx, result);
            });
        } else if (container instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) container).entrySet()) {
                if (pt.match(entry.getKey())) {
                    _findAll(entry.getValue(), tokenIdx + 1, result);
                }
                _findMatch(entry.getValue(), tokenIdx, result);
            }
        } else if (container instanceof List) {
            List<?> list = (List<?>) container;
            for (int i = 0; i < list.size(); i++) {
                if (pt.match(i)) {
                    _findAll(list.get(i), tokenIdx + 1, result);
                }
                _findMatch(list.get(i), tokenIdx, result);
            }
        } else if (container.getClass().isArray()) {
            for (int j = 0; j < Array.getLength(container); j++) {
                if (pt.match(j)) {
                    _findAll(Array.get(container, j), tokenIdx + 1, result);
                }
                _findMatch(Array.get(container, j), tokenIdx, result);
            }
        } else if (PojoRegistry.isPojo(container.getClass())) {
            PojoRegistry.PojoInfo pi = PojoRegistry.getPojoInfo(container.getClass());
            for (Map.Entry<String, PojoRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                if (pt.match(fi.getKey())) {
                    _findAll(fi.getValue().invokeGetter(container), tokenIdx + 1, result);
                }
                _findMatch(fi.getValue().invokeGetter(container), tokenIdx, result);
            }
        }
    }

    // 1. Automatically create or extends JsonObject nodes when they do not exist.
    // 2. Automatically create or extends JsonArray nodes when they do not exist and the index is 0.
    // TODO: Consider traversing the path first to verify if it can be filled with containers.
    @SuppressWarnings("unchecked")
    private Object _autoCreateContainers(@NonNull Object container) {
        Object node = container;
        Type type = Object.class;
        for (int i = 1; i < tokens.size() - 1; i++) { // traverse up to the second-last token
            PathToken pt = tokens.get(i);
            if (pt instanceof PathToken.Name) {
                String key = ((PathToken.Name) pt).name;
                if (PojoRegistry.isPojo(node.getClass())) { // POJO first
                    PojoRegistry.FieldInfo fi = PojoRegistry.getFieldInfo(node.getClass(), key);
                    if (fi != null) {
                        Object pojo = node;
                        node = fi.invokeGetter(node);
                        type = fi.getType();
                        if (node == null) {
                            PathToken nextPt = tokens.get(i + 1);
                            Class<?> rawClazz = TypeUtil.getRawClass(type);
                            node = createContainer(nextPt, rawClazz);
                            fi.invokeSetter(pojo, node);
                        }
                        continue;
                    } else if (node instanceof JsonObject) {
                        JsonObject jo = (JsonObject) node;
                        node = jo.getObject(key);
                        type = Object.class;
                        if (node == null) {
                            PathToken nextPt = tokens.get(i + 1);
                            node = createContainer(nextPt, Object.class);
                            jo.put(key, node);
                        }
                    } else {
                        throw new JsonException("Not found field '" + key + "' in POJO container '" +
                                node.getClass() + "'");
                    }
                }
                if (node instanceof JsonObject) {
                    JsonObject jo = (JsonObject) node;
                    node = jo.getObject(key);
                    type = Object.class;
                    if (node == null) {
                        PathToken nextPt = tokens.get(i + 1);
                        node = createContainer(nextPt, Object.class);
                        jo.put(key, node);
                    }
                } else if (node == Map.class) {
                    Map<String, Object> map = (Map<String, Object>) node;
                    node = map.get(key);
                    type = TypeUtil.resolveTypeArgument(type, Map.class, 1);
                    if (node == null) {
                        PathToken nextPt = tokens.get(i + 1);
                        Class<?> rawClazz = TypeUtil.getRawClass(type);
                        node = createContainer(nextPt, rawClazz);
                        map.put(key, node);
                    }
                } else {
                    throw new JsonException("Unexpected container type '" + node.getClass() + "' with name token '" +
                            pt + "'. The type must be one of JsonObject/Map/POJO.");
                }
            } else if (pt instanceof PathToken.Index) {
                int idx = ((PathToken.Index) pt).index;
                if (node instanceof JsonArray) {
                    JsonArray ja = (JsonArray) node;
                    type = Object.class;
                    if (idx == ja.size()) {
                        PathToken nextPt = tokens.get(i + 1);
                        node = createContainer(nextPt, Object.class);
                        ja.add(node);
                    } else if (ja.containsIndex(idx)) {
                        node = ja.getObject(idx);
                        if (node == null) {
                            PathToken nextPt = tokens.get(i + 1);
                            node = createContainer(nextPt, Object.class);
                            ja.set(idx, node);
                        }
                    } else {
                        throw new JsonException("Invalid JsonArray index " + idx + " for size " + ja.size() +
                                " (idx < size: modify; idx == size: append).");
                    }
                } else if (node instanceof List) {
                    List<Object> list = (List<Object>) node;
                    type = TypeUtil.resolveTypeArgument(type, List.class, 0);
                    Class<?> rawClazz = TypeUtil.getRawClass(type);
                    idx = idx < 0 ? list.size() + idx : idx;
                    if (idx == list.size()) {
                        PathToken nextPt = tokens.get(i + 1);
                        node = createContainer(nextPt, rawClazz);
                        list.add(node);
                    } else if (idx >= 0 && idx < list.size()) {
                        node = list.get(idx);
                        if (node == null) {
                            PathToken nextPt = tokens.get(i + 1);
                            node = createContainer(nextPt, rawClazz);
                            list.set(idx, node);
                        }
                    } else {
                        throw new JsonException("Invalid List index " + idx + " for size " + list.size() +
                            " (index < size: modify; index == size: append).");
                    }
                } else if (node.getClass().isArray()) {
                    Object arr = node;
                    int len = Array.getLength(arr);
                    idx = idx < 0 ? len + idx : idx;
                    Class<?> rawClazz = arr.getClass().getComponentType();
                    type = rawClazz;
                    if (idx >= 0 && idx < len) {
                        node = Array.get(arr, idx);
                        if (node == null) {
                            PathToken nextPt = tokens.get(i + 1);
                            node = createContainer(nextPt, rawClazz);
                            Array.set(arr, idx, node);
                        }
                    } else {
                        throw new JsonException("Invalid Array index " + idx + " for size " + len +
                                " (index < size: modify;).");
                    }
                } else {
                    throw new JsonException("Unexpected container type '" + node.getClass() + "' with list token " +
                            pt + ". The type must be one of JsonArray/List/Array.");
                }
            } else if (pt instanceof PathToken.Wildcard) {
                throw new JsonException("Cannot use wildcard '*' in _autoCreateContainers()");
            } else {
                throw new JsonException("Unexpected path token '" + pt + "'");
            }
        }
        return node; // last container
    }


    private Object createContainer(PathToken pt, Class<?> clazz) {
        if (pt instanceof PathToken.Name) {
            if (clazz.isAssignableFrom(JsonObject.class)) {
                return new JsonObject();
            } else if (clazz.isAssignableFrom(Map.class)) {
                return JsonConfig.global().mapSupplier.create();
            } else if (PojoRegistry.isPojo(clazz)) {
                PojoRegistry.PojoInfo pi = PojoRegistry.getPojoInfo(clazz);
                return pi.newInstance();
            } else {
                throw new JsonException("Cannot create container with type '" + clazz + "' at name token " +
                        pt + ". The type must be one of JsonObject/Map/POJO.");
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
                        "' at index token " + pt + ". The type must be one of JsonArray/List/Array.");
            }
        } else {
            throw new JsonException("Unexpected path token '" + pt + "'");
        }

    }


}
