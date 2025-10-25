package org.sjf4j;

import lombok.NonNull;
import org.sjf4j.util.ValueUtil;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class JsonObject extends JsonContainer {

    protected Map<String, Object> valueMap;


    public JsonObject() {
        valueMap = new LinkedHashMap<>();
    }

    public JsonObject(@NonNull JsonObject target) {
        this.valueMap = target.valueMap;
    }

    public JsonObject(@NonNull Map<String, ?> map) {
        this();
        putAll(map);
    }

    public JsonObject(@NonNull String key1, Object value1) {
        this();
        put(key1, value1);
    }
    public JsonObject(@NonNull String key1, Object value1,
                      @NonNull String key2, Object value2) {
        this(key1, value1);
        put(key2, value2);
    }
    public JsonObject(@NonNull String key1, Object value1,
                      @NonNull String key2, Object value2,
                      @NonNull String key3, Object value3) {
        this(key1, value1, key2, value2);
        put(key3, value3);
    }
    public JsonObject(@NonNull String key1, Object value1,
                      @NonNull String key2, Object value2,
                      @NonNull String key3, Object value3,
                      @NonNull String key4, Object value4) {
        this(key1, value1, key2, value2, key3, value3);
        put(key4, value4);
    }
    public JsonObject(@NonNull String key1, Object value1,
                      @NonNull String key2, Object value2,
                      @NonNull String key3, Object value3,
                      @NonNull String key4, Object value4,
                      @NonNull String key5, Object value5) {
        this(key1, value1, key2, value2, key3, value3, key4, value4);
        put(key5, value5);
    }
    public JsonObject(@NonNull String key1, Object value1,
                      @NonNull String key2, Object value2,
                      @NonNull String key3, Object value3,
                      @NonNull String key4, Object value4,
                      @NonNull String key5, Object value5,
                      @NonNull String key6, Object value6) {
        this(key1, value1, key2, value2, key3, value3, key4, value4, key5, value5);
        put(key6, value6);
    }
    public JsonObject(@NonNull String key1, Object value1,
                      @NonNull String key2, Object value2,
                      @NonNull String key3, Object value3,
                      @NonNull String key4, Object value4,
                      @NonNull String key5, Object value5,
                      @NonNull String key6, Object value6,
                      @NonNull String key7, Object value7) {
        this(key1, value1, key2, value2, key3, value3, key4, value4, key5, value5, key6, value6);
        put(key7, value7);
    }
    public JsonObject(@NonNull String key1, Object value1,
                      @NonNull String key2, Object value2,
                      @NonNull String key3, Object value3,
                      @NonNull String key4, Object value4,
                      @NonNull String key5, Object value5,
                      @NonNull String key6, Object value6,
                      @NonNull String key7, Object value7,
                      @NonNull String key8, Object value8) {
        this(key1, value1, key2, value2, key3, value3, key4, value4, key5, value5, key6, value6, key7, value7);
        put(key8, value8);
    }

    /// Subclass

    public <T extends JsonObject> T cast(@NonNull Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getConstructor(JsonObject.class);
            T newObj = constructor.newInstance(this);
//            newObj.objectMap = objectMap;
            return newObj;
        } catch (Throwable e) {
            throw new JsonException("Failed to cast JsonObject to '" + clazz.getName() + "'", e);
        }
    }

    /// Json

    public static JsonObject fromJson(@NonNull String input) {
        return fromJson(new StringReader(input), FacadeFactory.getDefaultJsonFacade());
    }

    public static JsonObject fromJson(@NonNull Reader input) {
        return fromJson(input, FacadeFactory.getDefaultJsonFacade());
    }

    public static JsonObject fromJson(@NonNull String input, @NonNull JsonFacade jsonFacade) {
        return fromJson(new StringReader(input), jsonFacade);
    }

    public static JsonObject fromJson(@NonNull Reader input, @NonNull JsonFacade jsonFacade) {
        return jsonFacade.readObject(input);
    }

    public String toJson() {
        return toJson(FacadeFactory.getDefaultJsonFacade());
    }

    public String toJson(@NonNull JsonFacade jsonFacade) {
        StringWriter output = new StringWriter();
        toJson(output, jsonFacade);
        return output.toString();
    }

    public void toJson(@NonNull Writer output) {
        toJson(output, FacadeFactory.getDefaultJsonFacade());
    }

    public void toJson(@NonNull Writer output, @NonNull JsonFacade jsonFacade) {
        jsonFacade.write(output, this);
    }


    /// Object

    @Override
    public String toString() {
        return toJson();
    }

    @Override
    public int hashCode() {
        return valueMap.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) return true;
        if (object == null) return false;
        if (object instanceof JsonObject) {
            return valueMap.equals(((JsonObject) object).valueMap);
        }
        return false;
    }

    /// Map

    @Override
    public int size() {
        return valueMap.size();
    }

    public boolean isEmpty() {
        return valueMap.isEmpty();
    }

    public Set<String> keySet() {
        return valueMap.keySet();
    }

    public boolean containsKey(@NonNull String key) {
        return valueMap.containsKey(key);
    }

    public Set<Map.Entry<String, Object>> entrySet() {
        return toMap().entrySet();
    }

    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(valueMap);
    }

    /// getter

    public Object getObject(@NonNull String key) {
        return valueMap.get(key);
    }

    public Object getObject(@NonNull String key, Object defaultValue) {
        Object value = getObject(key);
        return null == value ? defaultValue : value;
    }

    public String getString(@NonNull String key) {
        try {
            Object value = getObject(key);
            return ValueUtil.valueToString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get String value of key '" + key + "': " + e.getMessage(), e);
        }
    }

    public String getString(@NonNull String key, String defaultValue) {
        String value = getString(key);
        return null == value ? defaultValue : value;
    }

    public String getAsString(@NonNull String key) {
        Object value = getObject(key);
        return ValueUtil.valueAsString(value);
    }

    public String getAsString(@NonNull String key, String defaultValue) {
        String value = getAsString(key);
        return null == value ? defaultValue : value;
    }

    public Long getLong(@NonNull String key) {
        Object value = getObject(key);
        try {
            return ValueUtil.valueToLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Long value of key '" + key + "': " + e.getMessage(), e);
        }
    }

    public long getLong(@NonNull String key, long defaultValue) {
        Long value = getLong(key);
        return null == value ? defaultValue : value;
    }

    public Integer getInteger(@NonNull String key) {
        Object value = getObject(key);
        try {
            return ValueUtil.valueToInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Integer value of key '" + key + "': " + e.getMessage(), e);
        }
    }

    public int getInteger(@NonNull String key, int defaultValue) {
        Integer value = getInteger(key);
        return null == value ? defaultValue : value;
    }

    public Short getShort(@NonNull String key) {
        Object value = getObject(key);
        try {
            return ValueUtil.valueToShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Short value of key '" + key + "': " + e.getMessage(), e);
        }
    }

    public short getShort(@NonNull String key, short defaultValue) {
        Short value = getShort(key);
        return null == value ? defaultValue : value;
    }

    public Byte getByte(@NonNull String key) {
        Object value = getObject(key);
        try {
            return ValueUtil.valueToByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Byte value of key '" + key + "': " + e.getMessage(), e);
        }
    }

    public byte getByte(@NonNull String key, byte defaultValue) {
        Byte value = getByte(key);
        return null == value ? defaultValue : value;
    }

    public Double getDouble(@NonNull String key) {
        Object value = getObject(key);
        try {
            return ValueUtil.valueToDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Double value of key '" + key + "': " + e.getMessage(), e);
        }
    }

    public double getDouble(@NonNull String key, double defaultValue) {
        Double value = getDouble(key);
        return null == value ? defaultValue : value;
    }

    public Float getFloat(@NonNull String key) {
        Object value = getObject(key);
        try {
            return ValueUtil.valueToFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Float value of key '" + key + "': " + e.getMessage(), e);
        }
    }

    public float getFloat(@NonNull String key, float defaultValue) {
        Float value = getFloat(key);
        return null == value ? defaultValue : value;
    }

    public BigInteger getBigInteger(@NonNull String key) {
        Object value = getObject(key);
        try {
            return ValueUtil.valueToBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigInteger value of key '" + key + "': " + e.getMessage(), e);
        }
    }

    public BigInteger getBigInteger(@NonNull String key, BigInteger defaultValue) {
        BigInteger value = getBigInteger(key);
        return null == value ? defaultValue : value;
    }

    public BigDecimal getBigDecimal(@NonNull String key) {
        Object value = getObject(key);
        try {
            return ValueUtil.valueToBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigDecimal value of key '" + key + "': " + e.getMessage(), e);
        }
    }

    public BigDecimal getBigDecimal(@NonNull String key, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(key);
        return null == value ? defaultValue : value;
    }

    public Boolean getBoolean(@NonNull String key) {
        Object value = getObject(key);
        try {
            return ValueUtil.valueToBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Boolean value of key '" + key + "': " + e.getMessage(), e);
        }
    }

    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        Boolean value = getBoolean(key);
        return null == value ? defaultValue : value;
    }

    public JsonObject getJsonObject(@NonNull String key) {
        Object value = getObject(key);
        try {
            return ValueUtil.valueToJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonObject value of key '" + key + "': " + e.getMessage(), e);
        }
    }

    public JsonObject getJsonObject(@NonNull String key, JsonObject defaultValue) {
        JsonObject value = getJsonObject(key);
        return null == value ? defaultValue : value;
    }

    public JsonArray getJsonArray(@NonNull String key) {
        Object value = getObject(key);
        try {
            return ValueUtil.valueToJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonArray value of key '" + key + "': " + e.getMessage(), e);
        }
    }

    public JsonArray getJsonArray(@NonNull String key, JsonArray defaultValue) {
        JsonArray value = getJsonArray(key);
        return null == value ? defaultValue : value;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(@NonNull String key, @NonNull Class<T> clazz) {
        if (Object.class.equals(clazz)) {
            return (T) getObject(key);
        } else if (JsonObject.class.equals(clazz)) {
            return (T) getJsonObject(key);
        } else if (JsonObject.class.isAssignableFrom(clazz)) {
            return (T) getJsonObject(key).cast((Class<? extends JsonObject>) clazz);
        } else if (JsonArray.class.equals(clazz)) {
            return (T) getJsonArray(key);
        } else if (String.class.equals(clazz)) {
            return (T) getString(key);
        } else if (Boolean.class.equals(clazz)) {
            return (T) getBoolean(key);
        } else if (Long.class.equals(clazz)) {
            return (T) getLong(key);
        } else if (Integer.class.equals(clazz)) {
            return (T) getInteger(key);
        } else if (Short.class.equals(clazz)) {
            return (T) getShort(key);
        } else if (Byte.class.equals(clazz)) {
            return (T) getByte(key);
        } else if (Double.class.equals(clazz)) {
            return (T) getDouble(key);
        } else if (Float.class.equals(clazz)) {
            return (T) getFloat(key);
        } else if (BigInteger.class.equals(clazz)) {
            return (T) getBigInteger(key);
        } else if (BigDecimal.class.equals(clazz)) {
            return (T) getBigDecimal(key);
        }
        throw new JsonException("Failed to get unsupported type " + clazz.getName() + " of key '" + key + "'");
    }

    @SuppressWarnings("unchecked")
    public <T> T get(@NonNull String key, T... reified) {
        if (reified.length > 0) throw new JsonException("`reified` should be empty.");

        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(key, clazz);
    }


    /// Putter

    public void put(@NonNull String key, Object value) {
        try {
            value = ValueUtil.objectToValue(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value of key '" + key + "' to JSON-compatible Value: " +
                    e.getMessage());
        }
        valueMap.put(key, value);
    }

    public void putIfNonNull(@NonNull String key, Object value) {
        if (null != value) {
            put(key, value);
        }
    }

    public void putIfAbsent(@NonNull String key, Object value) {
        if (!containsKey(key)) {
            put(key, value);
        }
    }

    /**
     * Returns the JsonObject at the given key.
     * If absent, creates an empty one and puts it back.
     */
    public JsonObject createJsonObjectIfAbsent(@NonNull String key) {
        JsonObject jo = getJsonObject(key);
        if (null == jo) {
            jo = new JsonObject();
            put(key, jo);
        }
        return jo;
    }

    public JsonArray createJsonArrayIfAbsent(@NonNull String key) {
        JsonArray ja = getJsonArray(key);
        if (null == ja) {
            ja = new JsonArray();
            put(key, ja);
        }
        return ja;
    }

    public void putAll(@NonNull JsonObject jsonObject) {
        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.getObject(key);
            put(key, value);
        }
    }

    public void putAll(@NonNull Map<String, ?> map) {
        for (String key : map.keySet()) {
            Object value = map.get(key);
            put(key, value);
        }
    }

    public void remove(@NonNull String key) {
        valueMap.remove(key);
    }

    public void clear() {
        valueMap.clear();
    }

    /// Copy, merge

    public JsonObject deepCopy() {
        JsonObject copy = new JsonObject();
        for (String key : keySet()) {
            Object value = getObject(key);
            if (value instanceof JsonObject) {
                copy.put(key, ((JsonObject) value).deepCopy());
            } else if (value instanceof JsonArray) {
                copy.put(key, ((JsonArray) value).deepCopy());
            } else {
                copy.put(key, value);
            }
        }
        return copy;
    }

    /**
     * Merges the given target JsonObject into the current JsonObject.
     *
     * <p>All key-value pairs from the target object will be merged into this object.
     * When a key exists in both objects, the conflict resolution depends on the
     * {@code preferTarget} parameter.</p>
     *
     * @param target        the JsonObject to merge into this one
     * @param targetWin     if {@code true}, values from the target will overwrite
     *                      existing values in this object; if {@code false}, existing
     *                      values in this object will be preserved
     *
     */
    public void merge(JsonObject target, boolean targetWin, boolean needCopy) {
        if (target == null) return;
        for (String key : target.keySet()) {
            Object tarValue = target.getObject(key);
            if (tarValue instanceof JsonObject) {
                Object srcValue = getObject(key);
                if (srcValue instanceof JsonObject) {
                    ((JsonObject) srcValue).merge((JsonObject) tarValue, targetWin, needCopy);
                } else if (targetWin || srcValue == null) {
                    if (needCopy) {
                        put(key, ((JsonObject) tarValue).deepCopy());
                    } else {
                        put(key, tarValue);
                    }
                }
            } else if (tarValue instanceof JsonArray) {
                Object srcValue = getObject(key);
                if (srcValue instanceof JsonArray) {
                    ((JsonArray) srcValue).merge((JsonArray) tarValue, targetWin, needCopy);
                } else if (targetWin || srcValue == null) {
                    if (needCopy) {
                        put(key, ((JsonArray) tarValue).deepCopy());
                    } else {
                        put(key, tarValue);
                    }
                }
            } else if (targetWin || !containsKey(key)) {
                put(key, tarValue);
            }
        }
    }

    /**
     * Merges the given target JsonObject into the current JsonObject.
     *
     * <p>All key-value pairs from the target object will be merged into this object.
     * When a key exists in both objects, the target wins.</p>
     *
     */
    public void merge(JsonObject target) {
        merge(target, true, false);
    }

    public void mergeWithCopy(JsonObject target) {
        merge(target, true, true);
    }


    /// builder

    public Builder toBuilder() {
        return new Builder(this);
    }

    public Builder getBuilder(String key) {
        JsonObject jo = getJsonObject(key);
        if (null == jo) {
            jo = new JsonObject();
            put(key, jo);
        }
        return new Builder(jo);
    }

    public static Builder newBuilder() {
        return new Builder(new JsonObject());
    }

    public static class Builder {
        private final JsonObject jo;
        public Builder(JsonObject jo) {
            this.jo = jo;
        }
        public Builder put(String key, Object value) {
            jo.put(key, value);
            return this;
        }
        public Builder putIfNonNull(String key, Object value) {
            jo.putIfNonNull(key, value);
            return this;
        }
        public Builder putIfAbsent(String key, Object value) {
            jo.putIfAbsent(key, value);
            return this;
        }
//        public Builder putByPath(String jsonPath, Object value) {
//            jo.putByPath(jsonPath, value);
//            return this;
//        }
//        public Builder putByPathIfAbsent(String jsonPath, Object value) {
//            jo.putByPathIfAbsent(jsonPath, value);
//            return this;
//        }
        public JsonObject build() {
            return jo;
        }
    }


}
