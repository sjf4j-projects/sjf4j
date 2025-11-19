package org.sjf4j;

import lombok.Getter;
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

    @Getter
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

    @Override
    public String toString() {
        return raw;
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

    /// Find

    // Cannot have wildcard
    public Object findOne(@NonNull Object container) {
        Object node = container;
        for (int i = 1; i < tokens.size(); i++) {
            if (node == null) return null;
            PathToken pt = tokens.get(i);
            if (pt instanceof PathToken.Wildcard) {
                throw new JsonException("Cannot use wildcard '*' in findOne()");
            } else if (pt instanceof PathToken.Name) {
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
                    if (idx >= 0 && idx < list.size()) {
                        node = list.get(idx);
                    } else {
                        return null;
                    }
                } else if (node.getClass().isArray()) {
                    int idx = ((PathToken.Index) pt).index;
                    if (idx >= 0 && idx < Array.getLength(node)) {
                        node = Array.get(node, idx);
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        return node;
    }

    public JsonArray findAll(@NonNull Object container) {
        JsonArray result = new JsonArray();
        _findAllRecursively(container, 1, result);
        return result;
    }

    
    /// Get

    public boolean hasNonNull(@NonNull Object container) {
        return findOne(container) != null;
    }

    // Object
    public Object getObject(@NonNull Object container) {
        try {
            return findOne(container);
        } catch (Exception e) {
            throw new JsonException("Failed to get Object by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public Object getObject(@NonNull Object container, Object defaultValue) {
        Object value = getObject(container);
        return null == value ? defaultValue : value;
    }

    // String
    public String getString(@NonNull Object container) {
        try {
            Object value = findOne(container);
            return NodeUtil.valueToString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get String by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public String getString(@NonNull Object container, String defaultValue) {
        String value = getString(container);
        return value == null ? defaultValue : value;
    }

    // Long
    public Long getLong(@NonNull Object container) {
        try {
            Object value = findOne(container);
            return NodeUtil.valueAsLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Long by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public long getLong(@NonNull Object container, long defaultValue) {
        Long value = getLong(container);
        return value == null ? defaultValue : value;
    }

    // Integer
    public Integer getInteger(@NonNull Object container) {
        try {
            Object value = findOne(container);
            return NodeUtil.valueAsInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Integer by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public int getInteger(@NonNull Object container, int defaultValue) {
        Integer value = getInteger(container);
        return value == null ? defaultValue : value;
    }

    // Short
    public Short getShort(@NonNull Object container) {
        try {
            Object value = findOne(container);
            return NodeUtil.valueAsShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Short by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public short getShort(@NonNull Object container, short defaultValue) {
        Short value = getShort(container);
        return value == null ? defaultValue : value;
    }

    // Byte
    public Byte getByte(@NonNull Object container) {
        try {
            Object value = findOne(container);
            return NodeUtil.valueAsByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Byte by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public byte getByte(@NonNull Object container, byte defaultValue) {
        Byte value = getByte(container);
        return value == null ? defaultValue : value;
    }

    // Double
    public Double getDouble(@NonNull Object container) {
        try {
            Object value = findOne(container);
            return NodeUtil.valueAsDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Double by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public double getDouble(@NonNull Object container, double defaultValue) {
        Double value = getDouble(container);
        return value == null ? defaultValue : value;
    }

    // Float
    public Float getFloat(@NonNull Object container) {
        try {
            Object value = findOne(container);
            return NodeUtil.valueAsFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Float by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public float getFloat(@NonNull Object container, float defaultValue) {
        Float value = getFloat(container);
        return value == null ? defaultValue : value;
    }

    // BigInteger
    public BigInteger getBigInteger(@NonNull Object container) {
        try {
            Object value = findOne(container);
            return NodeUtil.valueAsBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigInteger by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public BigInteger getBigInteger(@NonNull Object container, BigInteger defaultValue) {
        BigInteger value = getBigInteger(container);
        return value == null ? defaultValue : value;
    }

    // BigDecimal
    public BigDecimal getBigDecimal(@NonNull Object container) {
        try {
            Object value = findOne(container);
            return NodeUtil.valueAsBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigDecimal by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public BigDecimal getBigDecimal(@NonNull Object container, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(container);
        return value == null ? defaultValue : value;
    }

    // Boolean
    public Boolean getBoolean(@NonNull Object container) {
        try {
            Object value = findOne(container);
            return NodeUtil.valueToBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Boolean by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public boolean getBoolean(@NonNull Object container, boolean defaultValue) {
        Boolean value = getBoolean(container);
        return value == null ? defaultValue : value;
    }

    // JsonObject
    public JsonObject getJsonObject(@NonNull Object container) {
        try {
            Object value = findOne(container);
            return NodeUtil.valueToJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonObject by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public JsonObject getJsonObject(@NonNull Object container, JsonObject defaultValue) {
        JsonObject value = getJsonObject(container);
        return value == null ? defaultValue : value;
    }

    // JsonArray
    public JsonArray getJsonArray(@NonNull Object container) {
        try {
            Object value = findOne(container);
            return NodeUtil.valueToJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonArray by path '" + getRaw() + "': " + e.getMessage(), e);
        }
    }

    public JsonArray getJsonArray(@NonNull Object container, JsonArray defaultValue) {
        JsonArray value = getJsonArray(container);
        return value == null ? defaultValue : value;
    }

    /// Put

    @SuppressWarnings("unchecked")
    public void put(@NonNull Object container, Object value) {
//        if (!(container instanceof JsonObject) && !(container instanceof JsonArray)) {
//            throw new JsonException("Invalid container type: expected JsonObject or JsonArray, but was '" +
//                    container.getClass() + "'");
//        }
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
                    throw new JsonException("Cannot set index " + idx + " in JsonArray of size " +
                            ja.size() + " (only when index = size allowed for addition)");
                }
            } else if (lastContainer instanceof List) {
                List<Object> list = (List<Object>) lastContainer;
                int idx = ((PathToken.Index) lastToken).index;
                if (idx == list.size()) {
                    list.add(value);
                } else if (idx >= 0 && idx < list.size()) {
                    list.set(idx, value);
                } else {
                    throw new JsonException("Cannot set index " + idx + " in List of size " +
                            list.size() + " (only when index = size allowed for addition)");
                }
            } else {
                throw new JsonException("Mismatched path token " + lastToken + " with container type '" +
                        lastContainer.getClass() + "'");
            }
        } else {
            throw new JsonException("Unexpected path token " + lastToken);
        }
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

    private void _findAllRecursively(Object container, int depth, JsonArray result) {
        Object node = container;
        for (int i = depth; i < tokens.size(); i++) {
            if (node ==  null) return;
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
                        return;
                    }
                } else {
                    return;
                }
            } else if (pt instanceof PathToken.Index) {
                if (node instanceof JsonArray) {
                    node = ((JsonArray) node).getObject(((PathToken.Index) pt).index);
                } else if (node instanceof List) {
                    List<?> list = (List<?>) node;
                    int idx = ((PathToken.Index) pt).index;
                    if (idx >= 0 && idx < list.size()) {
                        node = list.get(idx);
                    } else {
                        return;
                    }
                } else if (node.getClass().isArray()) {
                    int idx = ((PathToken.Index) pt).index;
                    if (idx >= 0 && idx < Array.getLength(node)) {
                        node = Array.get(node, idx);
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            } else if (pt instanceof PathToken.Wildcard) {
                if (node instanceof JsonObject) {
                    final int finalI = i;
                    ((JsonObject) node).forEach((k, v) -> {
                        _findAllRecursively(v, finalI + 1, result);
                    });
                } else if (node instanceof JsonArray) {
                    for (Object val : ((JsonArray) node)) {
                        _findAllRecursively(val, i + 1, result);
                    }
                } else if (node instanceof Map) {
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                        _findAllRecursively(entry.getValue(), i + 1, result);
                    }
                } else if (node instanceof List) {
                    for (Object val : ((List<?>) node)) {
                        _findAllRecursively(val, i + 1, result);
                    }
                } else if (node.getClass().isArray()) {
                    for (int k = 0; k < Array.getLength(node); k++) {
                        _findAllRecursively(Array.get(node, k), i + 1, result);
                    }
                } else {
                    return;
                }
            } else {
                return;
            }
        }
        result.add(node);
    }


    // 1. Automatically create or extends JsonObject nodes when they do not exist.
    // 2. Automatically create or extends JsonArray nodes when they do not exist and the index is 0.
    // TODO: Consider traversing the path first to verify if it can be filled with containers.
    @SuppressWarnings("unchecked")
    Object _autoCreateContainers(@NonNull Object container) {
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
                            " (idx < size: modify; idx == size: append).");
                    }
                } else if (node.getClass().isArray()) {
                    Object arr = node;
                    int size = Array.getLength(arr);
                    Class<?> rawClazz = arr.getClass().getComponentType();
                    type = rawClazz;
                    if (idx >= 0 && idx < size) {
                        node = Array.get(arr, idx);
                        if (node == null) {
                            PathToken nextPt = tokens.get(i + 1);
                            node = createContainer(nextPt, rawClazz);
                            Array.set(arr, idx, node);
                        }
                    } else {
                        throw new JsonException("Invalid Array index " + idx + " for size " + size +
                                " (idx < size: modify; idx == size: no append).");
                    }
                } else {
                    throw new JsonException("Unexpected container type '" + node.getClass() + "' with list token " +
                            pt + ". The type must be one of JsonArray/List/Array.");
                }
            } else if (pt instanceof PathToken.Wildcard) {
                throw new JsonException("Cannot use wildcard '*' in _autoCreateContainers()");
            } else {
                throw new JsonException("Unexpected path token " + pt);
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
            throw new JsonException("Unexpected path token " + pt);
        }

    }


}
