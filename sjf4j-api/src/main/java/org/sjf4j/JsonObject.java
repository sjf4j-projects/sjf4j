package org.sjf4j;

import lombok.NonNull;
import org.sjf4j.util.NumberUtil;
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

public class JsonObject {

    protected Map<String, Object> objectMap;


    public JsonObject() {
        objectMap = new LinkedHashMap<>();
    }

    public JsonObject(@NonNull JsonObject target) {
        this.objectMap = target.objectMap;
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
        return fromJson(new StringReader(input), JsonFacadeFactory.getDefaultJsonFacade());
    }

    public static JsonObject fromJson(@NonNull Reader input) {
        return fromJson(input, JsonFacadeFactory.getDefaultJsonFacade());
    }

    public static JsonObject fromJson(@NonNull String input, @NonNull JsonFacade jsonFacade) {
        return fromJson(new StringReader(input), jsonFacade);
    }

    public static JsonObject fromJson(@NonNull Reader input, @NonNull JsonFacade jsonFacade) {
        return jsonFacade.readObject(input);
    }

    public String toJson() {
        return toJson(JsonFacadeFactory.getDefaultJsonFacade());
    }

    public String toJson(@NonNull JsonFacade jsonFacade) {
        StringWriter output = new StringWriter();
        toJson(output, jsonFacade);
        return output.toString();
    }

    public void toJson(@NonNull Writer output) {
        toJson(output, JsonFacadeFactory.getDefaultJsonFacade());
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
        return objectMap.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) return true;
        if (object == null) return false;
        if (object instanceof JsonObject) {
            return objectMap.equals(((JsonObject) object).objectMap);
        }
        return false;
    }

    /// Map

    public int size() {
        return objectMap.size();
    }

    public boolean isEmpty() {
        return objectMap.isEmpty();
    }

    public Set<String> keySet() {
        return objectMap.keySet();
    }

    public boolean containsKey(@NonNull String key) {
        return objectMap.containsKey(key);
    }

    public Set<Map.Entry<String, Object>> entrySet() {
        return toMap().entrySet();
    }

    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(objectMap);
    }

    /// getter

    public Object getObject(@NonNull String key) {
        return objectMap.get(key);
    }
    public Object getObject(@NonNull String key, Object defaultValue) {
        Object value = getObject(key);
        return null == value ? defaultValue : value;
    }

    public String getString(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof String) {
            return (String) value;
        }
        throw new JsonException("Expected String for key '" + key + "', but got '" + value.getClass().getName() + "'");
    }
    public String getString(@NonNull String key, String defaultValue) {
        String value = getString(key);
        return null == value ? defaultValue : value;
    }

    public String getAsString(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }
    public String getAsString(@NonNull String key, String defaultValue) {
        String value = getAsString(key);
        return null == value ? defaultValue : value;
    }

    public Long getLong(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.valueAsLong((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to parse key '" + key + "' as Long: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value for key '" + key + "', but got '" +
                value.getClass().getName() + "'");
    }
    public long getLong(@NonNull String key, long defaultValue) {
        Long value = getLong(key);
        return null == value ? defaultValue : value;
    }

    public Integer getInteger(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.valueAsInteger((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to parse key '" + key + "' as Integer: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value for key '" + key + "', but got '" +
                value.getClass().getName() + "'");
    }
    public int getInteger(@NonNull String key, int defaultValue) {
        Integer value = getInteger(key);
        return null == value ? defaultValue : value;
    }

    public Short getShort(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.valueAsShort((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to parse key '" + key + "' as Short: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value for key '" + key + "', but got '" +
                value.getClass().getName() + "'");
    }
    public short getShort(@NonNull String key, short defaultValue) {
        Short value = getShort(key);
        return null == value ? defaultValue : value;
    }

    public Byte getByte(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.valueAsByte((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to parse key '" + key + "' as Byte: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value for key '" + key + "', but got '" +
                value.getClass().getName() + "'");
    }
    public byte getByte(@NonNull String key, byte defaultValue) {
        Byte value = getByte(key);
        return null == value ? defaultValue : value;
    }

    public Double getDouble(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.valueAsDouble((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to parse key '" + key + "' as Double: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value for key '" + key + "', but got '" +
                value.getClass().getName() + "'");
    }
    public double getDouble(@NonNull String key, double defaultValue) {
        Double value = getDouble(key);
        return null == value ? defaultValue : value;
    }

    public Float getFloat(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.valueAsFloat((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to parse key '" + key + "' as Float: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value for key '" + key + "', but got '" +
                value.getClass().getName() + "'");
    }
    public float getFloat(@NonNull String key, float defaultValue) {
        Float value = getFloat(key);
        return null == value ? defaultValue : value;
    }

    public BigInteger getBigInteger(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.valueAsBigInteger((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to parse key '" + key + "' as BigInteger: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value for key '" + key + "', but got '" +
                value.getClass().getName() + "'");
    }
    public BigInteger getBigInteger(@NonNull String key, BigInteger defaultValue) {
        BigInteger value = getBigInteger(key);
        return null == value ? defaultValue : value;
    }

    public BigDecimal getBigDecimal(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.valueAsBigDecimal((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to parse key '" + key + "' as BigDecimal: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value for key '" + key + "', but got '" +
                value.getClass().getName() + "'");
    }
    public BigDecimal getBigDecimal(@NonNull String key, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(key);
        return null == value ? defaultValue : value;
    }

    public Boolean getBoolean(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new JsonException("Expected Boolean for key '" + key + "', but got '" +
                value.getClass().getName() + "'");
    }
    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        Boolean value = getBoolean(key);
        return null == value ? defaultValue : value;
    }

    public JsonObject getJsonObject(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof JsonObject) {
            return (JsonObject) value;
        }
        throw new JsonException("Expected JsonObject for key '" + key + "', but got '" +
                value.getClass().getName() + "'");
    }
    public JsonObject getJsonObject(@NonNull String key, JsonObject defaultValue) {
        JsonObject value = getJsonObject(key);
        return null == value ? defaultValue : value;
    }

    public JsonArray getJsonArray(@NonNull String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        } else if (value instanceof JsonArray) {
            return (JsonArray) value;
        }
        throw new JsonException("Expected JsonArray for key '" + key + "', but got '" +
                value.getClass().getName() + "'");
    }
    public JsonArray getJsonArray(@NonNull String key, JsonArray defaultValue) {
        JsonArray value = getJsonArray(key);
        return null == value ? defaultValue : value;
    }

    /**
     * Returns the JsonObject at the given key.
     * If absent, creates an empty one and puts it back.
     */
    public JsonObject getOrCreateJsonObject(@NonNull String key) {
        JsonObject jo = getJsonObject(key);
        if (null == jo) {
            jo = new JsonObject();
            put(key, jo);
        }
        return jo;
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
        throw new JsonException("Cannot convert value of key '" + key + "' to unsupported type: " + clazz.getName());
    }
    @SuppressWarnings("unchecked")
    public <T> T get(@NonNull String key, T... reified) {
        if (reified.length > 0) throw new JsonException("`reified` should be empty.");

        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(key, clazz);
    }


    /// putter

    public void put(@NonNull String key, Object value) {
        try {
            value = ValueUtil.convertToJsonValue(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value of key '' to JSON-compatible Value: " + e.getMessage());
        }
        objectMap.put(key, value);
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
        objectMap.remove(key);
    }

    public void clear() {
        objectMap.clear();
    }

    /// copy / merge

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


    /// convert

//    public static JsonObject parse(String json) {
//        return parse(new StringReader(json));
//    }
//
//    public static JsonObject parse(Reader reader) {
//        if (null == reader) throw new JsonException("reader is null");
//        JsonObjectProxy jop = JsonProxyFactory.parseJsonObjectProxy(reader);
//        return new JsonObject(jop);
//    }


//    /// by path
//
//    // jsonPath like '$.attr[0].name', support JsonObject and JsonArray
//    @SuppressWarnings("unchecked")
//    public <T> T getByPath(String jsonPath, T... reified) {
//        if (reified.length > 0) throw new JsonException("`reified` should be empty.");
//
//        Pair<JsonObject, String> joNodePair = findLastSecondNode(this, jsonPath);
//        if (null == joNodePair) return null;
//
//        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(joNodePair.getLeft(), joNodePair.getRight());
//        if (null == jaNodePair) {
//            return joNodePair.getLeft().get(joNodePair.getRight(), reified);
//        } else {
//            return jaNodePair.getLeft().get(jaNodePair.getRight(), reified);
//        }
//    }
//
//
////    @SuppressWarnings("unchecked")
////    public <T> T getByPath(String jsonPath, T... reified) {
////        if (reified.length > 0) throw new JsonException("This should not have any values.");
////
////        Pair<JsonObject, String> joNodePair = findLastSecondNode(this, jsonPath);
////        if (null == joNodePair) return null;
////
////        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(joNodePair.getLeft(), joNodePair.getRight());
////        if (null == jaNodePair) {
////            return joNodePair.getLeft().get(joNodePair.getRight());
////        } else {
////            return jaNodePair.getLeft().get(jaNodePair.getRight());
////        }
////
////        Class<?> rt = reified.getClass().getComponentType();
////        if (rt.equals(String.class)) {
////            return (T) getString(key);
////        } else if (rt.equals(Short.class)) {
////            return (T) getShort(key);
////        }
////        return (T) get(key);
////    }
//
//    public String getStringByPath(String jsonPath) {
//        Pair<JsonObject, String> joNodePair = findLastSecondNode(this, jsonPath);
//        if (null == joNodePair) return null;
//
//        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(joNodePair.getLeft(), joNodePair.getRight());
//        if (null == jaNodePair) {
//            return joNodePair.getLeft().getString(joNodePair.getRight());
//        } else {
//            return jaNodePair.getLeft().getString(jaNodePair.getRight());
//        }
//    }
//
//    public String getStringByPath(String jsonPath, String defaultValue) {
//        String value = getStringByPath(jsonPath);
//        return null == value ? defaultValue : value;
//    }
//
//    public Long getLongByPath(String jsonPath) {
//        Pair<JsonObject, String> joNodePair = findLastSecondNode(this, jsonPath);
//        if (null == joNodePair) return null;
//
//        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(joNodePair.getLeft(), joNodePair.getRight());
//        if (null == jaNodePair) {
//            return joNodePair.getLeft().getLong(joNodePair.getRight());
//        } else {
//            return jaNodePair.getLeft().getLong(jaNodePair.getRight());
//        }
//    }
//
//    public long getLongByPath(String jsonPath, long defaultValue) {
//        Long value = getLongByPath(jsonPath);
//        return null == value ? defaultValue : value;
//    }
//
//    public Integer getIntegerByPath(String jsonPath) {
//        Pair<JsonObject, String> joNodePair = findLastSecondNode(this, jsonPath);
//        if (null == joNodePair) return null;
//
//        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(joNodePair.getLeft(), joNodePair.getRight());
//        if (null == jaNodePair) {
//            return joNodePair.getLeft().getInteger(joNodePair.getRight());
//        } else {
//            return jaNodePair.getLeft().getInteger(jaNodePair.getRight());
//        }
//    }
//
//    public int getIntegerByPath(String jsonPath, int defaultValue) {
//        Integer value = getIntegerByPath(jsonPath);
//        return null == value ? defaultValue : value;
//    }
//
//    public Short getShortByPath(String jsonPath) {
//        Pair<JsonObject, String> joNodePair = findLastSecondNode(this, jsonPath);
//        if (null == joNodePair) return null;
//
//        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(joNodePair.getLeft(), joNodePair.getRight());
//        if (null == jaNodePair) {
//            return joNodePair.getLeft().getShort(joNodePair.getRight());
//        } else {
//            return jaNodePair.getLeft().getShort(jaNodePair.getRight());
//        }
//    }
//
//    public short getShortByPath(String jsonPath, short defaultValue) {
//        Short value = getShortByPath(jsonPath);
//        return null == value ? defaultValue : value;
//    }
//
//    public Double getDoubleByPath(String jsonPath) {
//        Pair<JsonObject, String> joNodePair = findLastSecondNode(this, jsonPath);
//        if (null == joNodePair) return null;
//
//        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(joNodePair.getLeft(), joNodePair.getRight());
//        if (null == jaNodePair) {
//            return joNodePair.getLeft().getDouble(joNodePair.getRight());
//        } else {
//            return jaNodePair.getLeft().getDouble(jaNodePair.getRight());
//        }
//    }
//
//    public double getDoubleByPath(String jsonPath, double defaultValue) {
//        Double value = getDoubleByPath(jsonPath);
//        return null == value ? defaultValue : value;
//    }
//
//    public Float getFloatByPath(String jsonPath) {
//        Pair<JsonObject, String> joNodePair = findLastSecondNode(this, jsonPath);
//        if (null == joNodePair) return null;
//
//        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(joNodePair.getLeft(), joNodePair.getRight());
//        if (null == jaNodePair) {
//            return joNodePair.getLeft().getFloat(joNodePair.getRight());
//        } else {
//            return jaNodePair.getLeft().getFloat(jaNodePair.getRight());
//        }
//    }
//
//    public float getFloatByPath(String jsonPath, float defaultValue) {
//        Float value = getFloatByPath(jsonPath);
//        return null == value ? defaultValue : value;
//    }
//
//    public Boolean getBooleanByPath(String jsonPath) {
//        Pair<JsonObject, String> joNodePair = findLastSecondNode(this, jsonPath);
//        if (null == joNodePair) return null;
//
//        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(joNodePair.getLeft(), joNodePair.getRight());
//        if (null == jaNodePair) {
//            return joNodePair.getLeft().getBoolean(joNodePair.getRight());
//        } else {
//            return jaNodePair.getLeft().getBoolean(jaNodePair.getRight());
//        }
//    }
//
//    public boolean getBooleanByPath(String jsonPath, boolean defaultValue) {
//        Boolean value = getBooleanByPath(jsonPath);
//        return null == value ? defaultValue : value;
//    }
//
//    public JsonObject getJsonObjectByPath(String jsonPath) {
//        Pair<JsonObject, String> joNodePair = findLastSecondNode(this, jsonPath);
//        if (null == joNodePair) return null;
//
//        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(joNodePair.getLeft(), joNodePair.getRight());
//        if (null == jaNodePair) {
//            return joNodePair.getLeft().getJsonObject(joNodePair.getRight());
//        } else {
//            return jaNodePair.getLeft().getJsonObject(jaNodePair.getRight());
//        }
//    }
//
//    public JsonObject getJsonObjectByPath(String jsonPath, JsonObject defaultValue) {
//        JsonObject value = getJsonObjectByPath(jsonPath);
//        return null == value ? defaultValue : value;
//    }
//
//    public JsonObject createByPathIfAbsent(String jsonPath) {
//        JsonObject jo = getJsonObjectByPath(jsonPath);
//        if (null == jo) {
//            jo = new JsonObject();
//            putByPath(jsonPath, jo);
//        }
//        return jo;
//    }
//
//    public JsonArray getJsonArrayByPath(String jsonPath) {
//        Pair<JsonObject, String> joNodePair = findLastSecondNode(this, jsonPath);
//        if (null == joNodePair) return null;
//
//        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(joNodePair.getLeft(), joNodePair.getRight());
//        if (null == jaNodePair) {
//            return joNodePair.getLeft().getJsonArray(joNodePair.getRight());
//        } else {
//            return jaNodePair.getLeft().getJsonArray(jaNodePair.getRight());
//        }
//    }
//
//    public JsonArray getJsonArrayByPath(String jsonPath, JsonArray defaultValue) {
//        JsonArray value = getJsonArrayByPath(jsonPath);
//        return null == value ? defaultValue : value;
//    }
//
//    public boolean containsByPath(String jsonPath) {
//        Pair<JsonObject, String> joNodePair = findLastSecondNode(this, jsonPath);
//        if (null == joNodePair) return false;
//
//        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(joNodePair.getLeft(), joNodePair.getRight());
//        if (null == jaNodePair) {
//            return joNodePair.getLeft().containsKey(joNodePair.getRight());
//        } else {
//            return jaNodePair.getRight() >= 0 && jaNodePair.getRight() < jaNodePair.getLeft().size();
//        }
//    }
//
//
//    /**
//     *
//     * @param jsonPath like '$.a.b.x', only support JsonObject and JsonArray
//     * @param value
//     * @return
//     */
//    public void putByPath(String jsonPath, Object value) {
//        if (null == jsonPath || !jsonPath.startsWith("$.") || jsonPath.length() < 3) {
//            throw new JsonException("Invalid jsonPath: " + jsonPath);
//        }
//
////        String[] nodes = jsonPath.split("\\.");
//        String[] nodes = splitJsonPath(jsonPath);
//        // $, a, b, ..., x
//
//        JsonObject tempJo = this;
//        for (int i=1; i<nodes.length-1; i++) {
//            Pair<JsonArray, Integer> jaNodePair = tryArrayNode(tempJo, nodes[i]);
//            if (null == jaNodePair) {
//                if (null == tempJo.getJsonObject(nodes[i])) {
//                    tempJo.put(nodes[i], new JsonObject());
//                }
//                tempJo = tempJo.getJsonObject(nodes[i]);
//            } else {
//                JsonArray tempJa = jaNodePair.getLeft();
//                int idx = jaNodePair.getRight();
//                if (idx >= tempJa.size() || idx < 0) {
//                    throw new JsonException("Array index '" + nodes[i] + "' is out of JsonArray size '" +
//                            tempJa.size() + "'.");
//                }
//                tempJo = jaNodePair.getLeft().getJsonObject(jaNodePair.getRight());
//                if (null == tempJo) {
//                    throw new JsonException("Invalid pathNode: " + nodes[i]);
//                }
//            }
//        }
//
//        Pair<JsonArray, Integer> jaNodePair = tryArrayNode(tempJo, nodes[nodes.length-1]);
//        if (null == jaNodePair) {
//            tempJo.put(nodes[nodes.length-1], value);
//        } else {
//            jaNodePair.getLeft().add(jaNodePair.getRight(), value);
//        }
//    }
//
//    public void putByPathIfNonNull(String jsonPath, Object value) {
//        if (null != value) {
//            putByPath(jsonPath, value);
//        }
//    }
//
//    public void putByPathIfAbsent(String jsonPath, Object value) {
//        if (!containsByPath(jsonPath)) {
//            putByPath(jsonPath, value);
//        }
//    }
//
//    public void removeByPath(String jsonPath) {
//        if (null == jsonPath || !jsonPath.startsWith("$.") || jsonPath.length() < 3) {
//            throw new JsonException("Invalid jsonPath: " + jsonPath);
//        }
//
//        String[] nodes = jsonPath.split("\\.");
//        // $, a, b, ..., x
//
//        JsonObject tempJo = this;
//        for (int i=1; i<nodes.length-1; i++) {
//            Pair<JsonArray, Integer> jaNodePair = tryArrayNode(tempJo, nodes[i]);
//            if (null == jaNodePair) {
//                if (null == tempJo.getJsonObject(nodes[i])) {
//                    tempJo.put(nodes[i], new JsonObject());
//                }
//                tempJo = tempJo.getJsonObject(nodes[i]);
//            } else {
//                tempJo = jaNodePair.getLeft().getJsonObject(jaNodePair.getRight());
//                if (null == tempJo) {
//                    throw new JsonException("Invalid pathNode: " + nodes[i]);
//                }
//            }
//        }
//        tempJo.remove(nodes[nodes.length-1]);
//    }
//
//
//    /// private
//
//    /**
//     * abc[12]  => jsonArray, 12
//     *
//     */
//    private static Pair<JsonArray, Integer> tryArrayNode(JsonObject jsonObject, String pathNode) {
//        if (pathNode.endsWith("]")) {
//            String[] ss = pathNode.split("\\[");
//            if (ss.length == 2) {
//                String key = ss[0];
//                try {
//                    int idx = Integer.parseInt(ss[1].substring(0, ss[1].length()-1));
//                    JsonArray tempJa = jsonObject.getJsonArray(key);
//                    if (null == tempJa) {
//                        throw new JsonException("The value for the pathNode '" + pathNode + "' is not a JsonArray.");
//                    } else {
//                        return new Pair<>(tempJa, idx);
//                    }
//                } catch (NumberFormatException e) {
//                    throw new JsonException("Invalid pathNode: " + pathNode);
//                }
//            } else {
//                throw new JsonException("Invalid pathNode: " + pathNode);
//            }
//        }
//        return null;
//    }
//
//    private static Pair<JsonObject, String> findLastSecondNode(JsonObject jsonObject, String jsonPath) {
//        if (null == jsonPath || !jsonPath.startsWith("$.") || jsonPath.length() < 3) {
//            throw new JsonException("Invalid jsonPath: " + jsonPath);
//        }
//
//        String[] nodes = splitJsonPath(jsonPath);
//        // $, a, b, ..., x
//
//        JsonObject tempJo = jsonObject;
//        for (int i=1; i<nodes.length-1; i++) {
//            Pair<JsonArray, Integer> jaNodePair = tryArrayNode(tempJo, nodes[i]);
//            if (null == jaNodePair) {
//                tempJo = tempJo.getJsonObject(nodes[i]);
//                if (null == tempJo) {
//                    return null;
//                }
//            } else {
//                JsonArray ja = jaNodePair.getLeft();
//                if (null == ja) {
//                    return null;
//                } else {
//                    tempJo = ja.getJsonObject(jaNodePair.getRight());
//                    if (null == tempJo) {
//                        return null;
//                    }
//                }
//            }
//        }
//        return new Pair<>(tempJo, nodes[nodes.length-1]);
//    }
//
//    // TODO: There are still incomplete escaping problems.
//    // If 'a\' is key, then '$.a\.b.c' could be mistakenly recognized as {"a.b":{"c": ...}}.
//    private static String[] splitJsonPath(String jsonPath) {
//        String[] nodes = jsonPath.split("(?<!\\\\)\\.");
//        for (int i=0; i<nodes.length; i++) {
//            nodes[i] = nodes[i].replace("\\.", ".");
//        }
//        return nodes;
//    }

}
