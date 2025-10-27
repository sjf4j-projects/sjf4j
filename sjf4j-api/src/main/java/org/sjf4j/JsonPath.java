package org.sjf4j;

import lombok.Getter;
import lombok.NonNull;
import org.sjf4j.util.JsonPathUtil;
import org.sjf4j.util.ValueUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
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
        this.tokens = JsonPathUtil.compile(expr);
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
            PathToken pt = tokens.get(i);
            if (value instanceof JsonObject) {
                if (pt instanceof PathToken.Field) {
                    value = ((JsonObject) value).getObject(((PathToken.Field) pt).name);
                } else if (pt instanceof PathToken.Wildcard) {
                    throw new JsonException("Cannot use wildcard '*' in findOne()");
                } else {
                    return null;
                }
            } else if (value instanceof JsonArray) {
                if (pt instanceof PathToken.Index) {
                    value = ((JsonArray) value).getObject(((PathToken.Index) pt).index);
                } else if (pt instanceof PathToken.Wildcard) {
                    throw new JsonException("Cannot use wildcard '*' in findOne()");
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
            throw new JsonException("Failed to get Object value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public Object getObject(@NonNull JsonContainer container, Object defaultValue) {
        Object value = getObject(container);
        return null == value ? defaultValue : value;
    }

    // String
    public String getString(@NonNull JsonContainer container) {
        try {
            Object value = findOne(container);
            return ValueUtil.valueToString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get String value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public String getString(@NonNull JsonContainer container, String defaultValue) {
        String value = getString(container);
        return null == value ? defaultValue : value;
    }

    // Long
    public Long getLong(@NonNull JsonContainer container) {
        try {
            Object value = findOne(container);
            return ValueUtil.valueToLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Long value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public long getLong(@NonNull JsonContainer container, long defaultValue) {
        Long value = getLong(container);
        return null == value ? defaultValue : value;
    }

    // Integer
    public Integer getInteger(@NonNull JsonContainer container) {
        try {
            Object value = findOne(container);
            return ValueUtil.valueToInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Integer value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public int getInteger(@NonNull JsonContainer container, int defaultValue) {
        Integer value = getInteger(container);
        return null == value ? defaultValue : value;
    }

    // Short
    public Short getShort(@NonNull JsonContainer container) {
        try {
            Object value = findOne(container);
            return ValueUtil.valueToShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Short value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public short getShort(@NonNull JsonContainer container, short defaultValue) {
        Short value = getShort(container);
        return null == value ? defaultValue : value;
    }

    // Byte
    public Byte getByte(@NonNull JsonContainer container) {
        try {
            Object value = findOne(container);
            return ValueUtil.valueToByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Byte value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public byte getByte(@NonNull JsonContainer container, byte defaultValue) {
        Byte value = getByte(container);
        return null == value ? defaultValue : value;
    }

    // Double
    public Double getDouble(@NonNull JsonContainer container) {
        try {
            Object value = findOne(container);
            return ValueUtil.valueToDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Double value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public double getDouble(@NonNull JsonContainer container, double defaultValue) {
        Double value = getDouble(container);
        return null == value ? defaultValue : value;
    }

    // Float
    public Float getFloat(@NonNull JsonContainer container) {
        try {
            Object value = findOne(container);
            return ValueUtil.valueToFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Float value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public float getFloat(@NonNull JsonContainer container, float defaultValue) {
        Float value = getFloat(container);
        return null == value ? defaultValue : value;
    }

    // BigInteger
    public BigInteger getBigInteger(@NonNull JsonContainer container) {
        try {
            Object value = findOne(container);
            return ValueUtil.valueToBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigInteger value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public BigInteger getBigInteger(@NonNull JsonContainer container, BigInteger defaultValue) {
        BigInteger value = getBigInteger(container);
        return null == value ? defaultValue : value;
    }

    // BigDecimal
    public BigDecimal getBigDecimal(@NonNull JsonContainer container) {
        try {
            Object value = findOne(container);
            return ValueUtil.valueToBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigDecimal value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public BigDecimal getBigDecimal(@NonNull JsonContainer container, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(container);
        return null == value ? defaultValue : value;
    }

    // Boolean
    public Boolean getBoolean(@NonNull JsonContainer container) {
        try {
            Object value = findOne(container);
            return ValueUtil.valueToBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Boolean value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public boolean getBoolean(@NonNull JsonContainer container, boolean defaultValue) {
        Boolean value = getBoolean(container);
        return null == value ? defaultValue : value;
    }

    // JsonObject
    public JsonObject getJsonObject(@NonNull JsonContainer container) {
        try {
            Object value = findOne(container);
            return ValueUtil.valueToJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonObject value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public JsonObject getJsonObject(@NonNull JsonContainer container, JsonObject defaultValue) {
        JsonObject value = getJsonObject(container);
        return null == value ? defaultValue : value;
    }

    // JsonArray
    public JsonArray getJsonArray(@NonNull JsonContainer container) {
        try {
            Object value = findOne(container);
            return ValueUtil.valueToJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonArray value by path '" + getRawExpr() + "': " + e.getMessage(), e);
        }
    }

    public JsonArray getJsonArray(@NonNull JsonContainer container, JsonArray defaultValue) {
        JsonArray value = getJsonArray(container);
        return null == value ? defaultValue : value;
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
            PathToken pt = tokens.get(i);
            if (value instanceof JsonObject) {
                if (pt instanceof PathToken.Field) {
                    value = ((JsonObject) value).getObject(((PathToken.Field) pt).name);
                } else if (pt instanceof PathToken.Wildcard) {
                    for (Map.Entry<String, Object> entry : ((JsonObject) value).entrySet()) {
                        _findAllRecursively(entry.getValue(), i + 1, result);
                    }
                } else {
                    return;
                }
            } else if (value instanceof JsonArray) {
                if (pt instanceof PathToken.Index) {
                    value = ((JsonArray) value).getObject(((PathToken.Index) pt).index);
                } else if (pt instanceof PathToken.Wildcard) {
                    for (Object val : ((JsonArray) value)) {
                        _findAllRecursively(val, i + 1, result);
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
            if (container instanceof JsonObject) {
                JsonObject jo = (JsonObject) container;
                if (pt instanceof PathToken.Field) {
                    String key = ((PathToken.Field) pt).name;
                    PathToken nextPt = tokens.get(i + 1);
                    container = jo.getObject(key);
                    if (container == null) {
                        if (nextPt instanceof PathToken.Field) {
                            container = jo.createJsonObjectIfAbsent(key);
                        } else if (nextPt instanceof PathToken.Index) {
                            container = jo.createJsonArrayIfAbsent(key);
                        } else {
                            throw new JsonException("Unexpected path token " + pt);
                        }
                    }
                } else if (pt instanceof PathToken.Wildcard) {
                    throw new JsonException("Cannot use wildcard '*' in _autoCreateContainers()");
                } else {
                    throw new JsonException("Mismatched path token " + pt + " with JsonObject container");
                }
            } else if (container instanceof JsonArray) {
                JsonArray ja = (JsonArray) container;
                if (pt instanceof PathToken.Index) {
                    int idx = ((PathToken.Index) pt).index;
                    PathToken nextPt = tokens.get(i + 1);
                    // Special rule: autofill index 0 if array is empty
                    if (ja.isEmpty() && idx == 0) {
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
                                ja.size() + " (only index 0 allowed for auto-create)");
                    }
                } else if (pt instanceof PathToken.Wildcard) {
                    throw new JsonException("Cannot use wildcard '*' in _autoCreateContainers()");
                } else {
                    throw new JsonException("Mismatched path token " + pt + "' with JsonArray container");
                }
            } else {
                throw new JsonException("Invalid container type at step " + i + ": " +
                        (container == null ? "null" : container.getClass().getSimpleName()));
            }
        }
        return container; // last container
    }


}
