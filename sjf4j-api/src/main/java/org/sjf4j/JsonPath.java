package org.sjf4j;

import lombok.Getter;
import lombok.NonNull;
import org.sjf4j.util.JsonPathParser;
import org.sjf4j.util.ObjectHandler;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class JsonPath {

    @Getter
    private String rawExpr;
    private final List<PathToken> tokens;

    private JsonPath(JsonPath target) {
        this.rawExpr = target.rawExpr;
        this.tokens = new ArrayList<>(target.tokens);
    }

    public JsonPath() {
        this.tokens = new ArrayList<>();
        push(new PathToken.Root());
    }

    public JsonPath(String expr) {
        this.tokens = JsonPathParser.compile(expr);
        this.rawExpr = expr;
    }
    
    public static JsonPath compile(@NonNull String expr) {
        // Consider add cache here
        return new JsonPath(expr);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (PathToken t : tokens) sb.append(t);
        return sb.toString();
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
    public Object findOne(@NonNull JsonContainer container) {
        Object value = container;
        for (int i = 1; i < tokens.size(); i++) {
            if (value == null) return null;
            PathToken pt = tokens.get(i);
            if (pt instanceof PathToken.Wildcard) {
                throw new JsonException("Cannot use wildcard '*' in findOne()");
            } else if (pt instanceof PathToken.Field) {
                if (value instanceof JsonObject) {
                    value = ((JsonObject) value).getObject(((PathToken.Field) pt).name);
                } else if (value instanceof Map) {
                    value = ((Map<?, ?>) value).get(((PathToken.Field) pt).name);
                } else {
                    return null;
                }
            } else if (pt instanceof PathToken.Index) {
                if (value instanceof JsonArray) {
                    value = ((JsonArray) value).getObject(((PathToken.Index) pt).index);
                } else if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    int idx = ((PathToken.Index) pt).index;
                    if (idx >= 0 && idx < list.size()) {
                        value = list.get(idx);
                    } else {
                        return null;
                    }
                } else if (value.getClass().isArray()) {
                    int idx = ((PathToken.Index) pt).index;
                    if (idx >= 0 && idx < Array.getLength(value)) {
                        value = Array.get(value, idx);
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
        return value;
    }

    public JsonArray findAll(@NonNull JsonContainer container) {
        JsonArray result = new JsonArray();
        _findAllRecursively(container, 1, result);
        return result;
    }

    
    /// Get

    public boolean contains(@NonNull JsonContainer container) {
        return findOne(container) != null;
    }

    // Object
    public Object getObject(@NonNull JsonContainer container) {
        try {
            return findOne(container);
        } catch (Exception e) {
            throw new JsonException("Failed to get Object by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public Object getObject(@NonNull JsonContainer container, Object defaultValue) {
        Object value = getObject(container);
        return null == value ? defaultValue : value;
    }

    // String
    public String getString(@NonNull JsonContainer container) {
        try {
            Object object = findOne(container);
            return ObjectHandler.objectToString(object);
        } catch (Exception e) {
            throw new JsonException("Failed to get String by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public String getString(@NonNull JsonContainer container, String defaultValue) {
        String object = getString(container);
        return object == null ? defaultValue : object;
    }

    // Long
    public Long getLong(@NonNull JsonContainer container) {
        try {
            Object object = findOne(container);
            return ObjectHandler.objectToLong(object);
        } catch (Exception e) {
            throw new JsonException("Failed to get Long by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public long getLong(@NonNull JsonContainer container, long defaultValue) {
        Long object = getLong(container);
        return object == null ? defaultValue : object;
    }

    // Integer
    public Integer getInteger(@NonNull JsonContainer container) {
        try {
            Object object = findOne(container);
            return ObjectHandler.objectToInteger(object);
        } catch (Exception e) {
            throw new JsonException("Failed to get Integer by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public int getInteger(@NonNull JsonContainer container, int defaultValue) {
        Integer object = getInteger(container);
        return object == null ? defaultValue : object;
    }

    // Short
    public Short getShort(@NonNull JsonContainer container) {
        try {
            Object object = findOne(container);
            return ObjectHandler.objectToShort(object);
        } catch (Exception e) {
            throw new JsonException("Failed to get Short by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public short getShort(@NonNull JsonContainer container, short defaultValue) {
        Short object = getShort(container);
        return object == null ? defaultValue : object;
    }

    // Byte
    public Byte getByte(@NonNull JsonContainer container) {
        try {
            Object object = findOne(container);
            return ObjectHandler.objectToByte(object);
        } catch (Exception e) {
            throw new JsonException("Failed to get Byte by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public byte getByte(@NonNull JsonContainer container, byte defaultValue) {
        Byte object = getByte(container);
        return object == null ? defaultValue : object;
    }

    // Double
    public Double getDouble(@NonNull JsonContainer container) {
        try {
            Object object = findOne(container);
            return ObjectHandler.objectToDouble(object);
        } catch (Exception e) {
            throw new JsonException("Failed to get Double by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public double getDouble(@NonNull JsonContainer container, double defaultValue) {
        Double object = getDouble(container);
        return object == null ? defaultValue : object;
    }

    // Float
    public Float getFloat(@NonNull JsonContainer container) {
        try {
            Object object = findOne(container);
            return ObjectHandler.objectToFloat(object);
        } catch (Exception e) {
            throw new JsonException("Failed to get Float by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public float getFloat(@NonNull JsonContainer container, float defaultValue) {
        Float object = getFloat(container);
        return object == null ? defaultValue : object;
    }

    // BigInteger
    public BigInteger getBigInteger(@NonNull JsonContainer container) {
        try {
            Object object = findOne(container);
            return ObjectHandler.objectToBigInteger(object);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigInteger by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public BigInteger getBigInteger(@NonNull JsonContainer container, BigInteger defaultValue) {
        BigInteger object = getBigInteger(container);
        return object == null ? defaultValue : object;
    }

    // BigDecimal
    public BigDecimal getBigDecimal(@NonNull JsonContainer container) {
        try {
            Object object = findOne(container);
            return ObjectHandler.objectToBigDecimal(object);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigDecimal by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public BigDecimal getBigDecimal(@NonNull JsonContainer container, BigDecimal defaultValue) {
        BigDecimal object = getBigDecimal(container);
        return object == null ? defaultValue : object;
    }

    // Boolean
    public Boolean getBoolean(@NonNull JsonContainer container) {
        try {
            Object object = findOne(container);
            return ObjectHandler.objectToBoolean(object);
        } catch (Exception e) {
            throw new JsonException("Failed to get Boolean by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public boolean getBoolean(@NonNull JsonContainer container, boolean defaultValue) {
        Boolean object = getBoolean(container);
        return object == null ? defaultValue : object;
    }

    // JsonObject
    public JsonObject getJsonObject(@NonNull JsonContainer container) {
        try {
            Object object = findOne(container);
            return ObjectHandler.objectToJsonObject(object);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonObject by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public JsonObject getJsonObject(@NonNull JsonContainer container, JsonObject defaultValue) {
        JsonObject object = getJsonObject(container);
        return object == null ? defaultValue : object;
    }

    // JsonArray
    public JsonArray getJsonArray(@NonNull JsonContainer container) {
        try {
            Object object = findOne(container);
            return ObjectHandler.objectToJsonArray(object);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonArray by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public JsonArray getJsonArray(@NonNull JsonContainer container, JsonArray defaultValue) {
        JsonArray object = getJsonArray(container);
        return object == null ? defaultValue : object;
    }

    /// Put

    public void put(@NonNull JsonContainer container, Object value) {
        if (!(container instanceof JsonObject) && !(container instanceof JsonArray)) {
            throw new JsonException("Invalid container type: expected JsonObject or JsonArray, but was " +
                    container.getClass().getName());
        }
        Object lastContainer = _autoCreateContainers(container);
        PathToken lastToken = peek();
        if (lastContainer instanceof JsonObject && lastToken instanceof PathToken.Field) {
            ((JsonObject) lastContainer).put(((PathToken.Field) lastToken).name, value);
        } else if (lastContainer instanceof JsonArray && lastToken instanceof PathToken.Index) {
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
        } else {
            throw new JsonException("Mismatched path token " + lastToken + "' with container " +
                    lastContainer.getClass().getSimpleName());
        }
    }

    public void putIfNonNull(@NonNull JsonContainer container, Object value) {
        if (null != value) { put(container, value); }
    }

    public void putIfAbsent(@NonNull JsonContainer container, Object value) {
        if (!contains(container)) { put(container, value); }
    }

    public void remove(@NonNull JsonContainer container) {
        if (contains(container)) {
            Object lastContainer = _autoCreateContainers(container);
            PathToken lastToken = peek();
            if (lastToken instanceof PathToken.Field) {
                ((JsonObject) lastContainer).remove(((PathToken.Field) lastToken).name);
            } else if (lastToken instanceof PathToken.Index) {
                ((JsonArray) lastContainer).remove(((PathToken.Index) lastToken).index);
            }
        }
    }


    /// private

    private void _findAllRecursively(Object value, int depth, JsonArray result) {
        for (int i = depth; i < tokens.size(); i++) {
            if (value ==  null) return;
            PathToken pt = tokens.get(i);
            if (pt instanceof PathToken.Field) {
                if (value instanceof JsonObject) {
                    value = ((JsonObject) value).getObject(((PathToken.Field) pt).name);
                } else if (value instanceof Map) {
                    value = ((Map<?, ?>) value).get(((PathToken.Field) pt).name);
                } else {
                    return;
                }
            } else if (pt instanceof PathToken.Index) {
                if (value instanceof JsonArray) {
                    value = ((JsonArray) value).getObject(((PathToken.Index) pt).index);
                } else if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    int idx = ((PathToken.Index) pt).index;
                    if (idx >= 0 && idx < list.size()) {
                        value = list.get(idx);
                    } else {
                        return;
                    }
                } else if (value.getClass().isArray()) {
                    int idx = ((PathToken.Index) pt).index;
                    if (idx >= 0 && idx < Array.getLength(value)) {
                        value = Array.get(value, idx);
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            } else if (pt instanceof PathToken.Wildcard) {
                if (value instanceof JsonObject) {
                    for (Map.Entry<String, Object> entry : ((JsonObject) value).entrySet()) {
                        _findAllRecursively(entry.getValue(), i + 1, result);
                    }
                } else if (value instanceof JsonArray) {
                    for (Object val : ((JsonArray) value)) {
                        _findAllRecursively(val, i + 1, result);
                    }
                } else if (value instanceof Map) {
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                        _findAllRecursively(entry.getValue(), i + 1, result);
                    }
                } else if (value instanceof List) {
                    for (Object val : ((List<?>) value)) {
                        _findAllRecursively(val, i + 1, result);
                    }
                } else if (value.getClass().isArray()) {
                    for (int k = 0; k < Array.getLength(value); k++) {
                        _findAllRecursively(Array.get(value, k), i + 1, result);
                    }
                } else {
                    return;
                }
            } else {
                return;
            }
        }
        result.add(value);
    }


    // 1. Automatically create or extends JsonObject nodes when they do not exist.
    // 2. Automatically create or extends JsonArray nodes when they do not exist and the index is 0.
    // TODO: Consider traversing the path first to verify if it can be filled with containers.
    Object _autoCreateContainers(@NonNull Object container) {
        for (int i = 1; i < tokens.size() - 1; i++) { // traverse up to the second-last token
            PathToken pt = tokens.get(i);
            if (pt instanceof PathToken.Field) {
                String key = ((PathToken.Field) pt).name;
                if (container instanceof JsonObject) {
                    JsonObject jo = (JsonObject) container;
                    container = jo.getObject(key);
                    if (container == null) {
                        PathToken nextPt = tokens.get(i + 1);
                        if (nextPt instanceof PathToken.Field) {
                            container = jo.createJsonObjectIfAbsent(key);
                        } else if (nextPt instanceof PathToken.Index) {
                            container = jo.createJsonArrayIfAbsent(key);
                        } else {
                            throw new JsonException("Unexpected path token " + pt);
                        }
                    }
                } else if (container instanceof Map) {
                    container = ((Map<?, ?>) container).get(key);
                    if (container == null) {
                        throw new JsonException("Cannot automatically create container for field " + pt +
                                " of Map. Automatic creation is only supported for JsonObject/JsonArray.");
                    }
                } else {
                    throw new JsonException("Mismatched path token " + pt + " with container type '" +
                            container.getClass().getName() + "'");
                }
            } else if (pt instanceof PathToken.Index) {
                int idx = ((PathToken.Index) pt).index;
                if (container instanceof JsonArray) {
                    JsonArray ja = (JsonArray) container;
                    PathToken nextPt = tokens.get(i + 1);
                    if (idx == ja.size()) {
                        if (nextPt instanceof PathToken.Field) {
                            container = new JsonObject();
                            ja.add(container);
                        } else if (nextPt instanceof PathToken.Index) {
                            container = new JsonArray();
                            ja.add(container);
                        } else {
                            throw new JsonException("Unexpected path token " + pt);
                        }
                    } else if (ja.containsIndex(idx)) {
                        container = ja.getObject(idx);
                        if (container == null) {
                            if (nextPt instanceof PathToken.Field) {
                                container = new JsonObject();
                                ja.set(idx, container);
                            } else if (nextPt instanceof PathToken.Index) {
                                container = new JsonArray();
                                ja.set(idx, container);
                            } else {
                                throw new JsonException("Unexpected path token " + pt);
                            }
                        }
                    } else {
                        throw new JsonException("Cannot create container at index " + idx + " in JsonArray of size " +
                                ja.size() + " (Only when index = size allowed for addition)");
                    }
                } else if (container instanceof List) {
                    List<?> list = (List<?>) container;
                    if (idx >= 0 && idx < list.size()) {
                        container = list.get(idx);
                        if (container == null) {
                            throw new JsonException("Cannot automatically create container for index " + pt +
                                    " of List. Automatic creation is only supported for JsonObject/JsonArray.");
                        }
                    } else {
                        throw new JsonException("Index " + pt + " out of size " + list.size() +
                                " of List. Automatic creation is only supported for JsonObject/JsonArray.");
                    }
                } else if (container.getClass().isArray()) {
                    int size = Array.getLength(container);
                    if (idx >= 0 && idx < size) {
                        container = Array.get(container, idx);
                        if (container == null) {
                            throw new JsonException("Cannot automatically create container for index " + pt +
                                    " of Array. Automatic creation is only supported for JsonObject/JsonArray.");
                        }
                    } else {
                        throw new JsonException("Index " + pt + " out of size " + size +
                                " of Array. Automatic creation is only supported for JsonObject/JsonArray.");
                    }
                } else {
                    throw new JsonException("Mismatched path token " + pt + " with container type '" +
                            container.getClass().getName() + "'");
                }
            } else if (pt instanceof PathToken.Wildcard) {
                throw new JsonException("Cannot use wildcard '*' in _autoCreateContainers()");
            } else {
                throw new JsonException("Unexpected path token " + pt);
            }
        }
        return container; // last container
    }


}
