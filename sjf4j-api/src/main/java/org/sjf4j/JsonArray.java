package org.sjf4j;

import lombok.NonNull;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.YamlFacade;
import org.sjf4j.util.ValueUtil;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class JsonArray extends JsonContainer implements Iterable<Object> {

    protected List<Object> valueList;

    public JsonArray() {
        valueList = new ArrayList<Object>();
    }

    public JsonArray(@NonNull JsonArray target) {
        this.valueList = target.valueList;
    }

    public JsonArray(Object... objects) {
        this();
        addAll(objects);
    }
    public JsonArray(short... objects) {
        this();
        addAll(objects);
    }
    public JsonArray(int... objects) {
        this();
        addAll(objects);
    }
    public JsonArray(long... objects) {
        this();
        addAll(objects);
    }
    public JsonArray(float... objects) {
        this();
        addAll(objects);
    }
    public JsonArray(double... objects) {
        this();
        addAll(objects);
    }
    public JsonArray(char... objects) {
        this();
        addAll(objects);
    }
    public JsonArray(boolean... objects) {
        this();
        addAll(objects);
    }

    public JsonArray(Collection<?> objects) {
        this();
        addAll(objects);
    }


    /// JSON Facade

    public static JsonArray fromJson(@NonNull String input) {
        return fromJson(new StringReader(input), FacadeFactory.getDefaultJsonFacade());
    }

    public static JsonArray fromJson(@NonNull Reader input) {
        return fromJson(input, FacadeFactory.getDefaultJsonFacade());
    }

    public static JsonArray fromJson(@NonNull String input, @NonNull JsonFacade jsonFacade) {
        return fromJson(new StringReader(input), jsonFacade);
    }

    public static JsonArray fromJson(@NonNull Reader input, @NonNull JsonFacade jsonFacade) {
        return jsonFacade.readArray(input);
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
        jsonFacade.writeArray(output, this);
    }

    /// YAML Facade

    public static JsonArray fromYaml(@NonNull String input) {
        return fromYaml(new StringReader(input), FacadeFactory.getDefaultYamlFacade());
    }

    public static JsonArray fromYaml(@NonNull Reader input) {
        return fromYaml(input, FacadeFactory.getDefaultYamlFacade());
    }

    public static JsonArray fromYaml(@NonNull String input, @NonNull YamlFacade yamlFacade) {
        return fromYaml(new StringReader(input), yamlFacade);
    }

    public static JsonArray fromYaml(@NonNull Reader input, @NonNull YamlFacade yamlFacade) {
        return yamlFacade.readArray(input);
    }

    public String toYaml() {
        return toYaml(FacadeFactory.getDefaultYamlFacade());
    }

    public String toYaml(@NonNull YamlFacade yamlFacade) {
        StringWriter output = new StringWriter();
        toYaml(output, yamlFacade);
        return output.toString();
    }

    public void toYaml(@NonNull Writer output) {
        toYaml(output, FacadeFactory.getDefaultYamlFacade());
    }

    public void toYaml(@NonNull Writer output, @NonNull YamlFacade yamlFacade) {
        yamlFacade.writeArray(output, this);
    }


    /// Object

    @Override
    public String toString() {
        return toJson();
    }

    @Override
    public int hashCode() {
        return valueList.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof JsonArray) {
            return valueList.equals(((JsonArray) object).valueList);
        }
        return false;
    }

    /// List

    @Override
    public int size() {
        return valueList.size();
    }

    public boolean isEmpty() {
        return valueList.isEmpty();
    }

    public List<Object> toList() {
        return Collections.unmodifiableList(valueList);
    }

    @Override
    public Iterator<Object> iterator() {
        return toList().iterator();
    }

    private int posIndex(int idx) {
        return idx < 0 ? valueList.size() + idx : idx;
    }

    public boolean containsIndex(int idx) {
        idx = posIndex(idx);
        return idx >= 0 && idx < valueList.size();
    }


    /// Getter

    public Object getObject(int idx) {
        int pidx = posIndex(idx);
        if (pidx < 0 || pidx >= valueList.size()) {
//            throw new JsonException("Index " + idx + " out of bounds " + size);
            return null;
        } else {
            return valueList.get(pidx);
        }
    }

    public Object getObject(int idx, Object defaultValue) {
        Object value = getFloat(idx);
        return value == null ? defaultValue : value;
    }

    public String getString(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get String value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public String getString(int idx, String defaultValue) {
        String value = getString(idx);
        return value == null ? defaultValue : value;
    }

    public String getAsString(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueAsString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get as String value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public Long getLong(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Long value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public long getLong(int idx, long defaultValue) {
        Long value = getLong(idx);
        return value == null ? defaultValue : value;
    }

    public Integer getInteger(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Integer value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public int getInteger(int idx, int defaultValue) {
        Integer value = getInteger(idx);
        return value == null ? defaultValue : value;
    }

    public Short getShort(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Short value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public short getShort(int idx, short defaultValue) {
        Short value = getShort(idx);
        return value == null ? defaultValue : value;
    }

    public Byte getByte(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Byte value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public byte getByte(int idx, byte defaultValue) {
        Byte value = getByte(idx);
        return value == null ? defaultValue : value;
    }

    public Double getDouble(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Double value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public double getDouble(int idx, double defaultValue) {
        Double value = getDouble(idx);
        return value == null ? defaultValue : value;
    }

    public Float getFloat(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Float value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public float getFloat(int idx, float defaultValue) {
        Float value = getFloat(idx);
        return value == null ? defaultValue : value;
    }

    public BigInteger getBigInteger(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigInteger value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public BigInteger getBigInteger(int idx, BigInteger defaultValue) {
        BigInteger value = getBigInteger(idx);
        return value == null ? defaultValue : value;
    }

    public BigDecimal getBigDecimal(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigDecimal value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public BigDecimal getBigDecimal(int idx, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(idx);
        return value == null ? defaultValue : value;
    }

    public Boolean getBoolean(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Boolean value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public boolean getString(int idx, boolean defaultValue) {
        Boolean value = getBoolean(idx);
        return value == null ? defaultValue : value;
    }

    public JsonObject getJsonObject(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonObject value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public JsonObject getJsonObject(int idx, JsonObject defaultValue) {
        JsonObject value = getJsonObject(idx);
        return value == null ? defaultValue : value;
    }

    public JsonArray getJsonArray(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonArray value at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public JsonArray getJsonArray(int idx, JsonArray defaultValue) {
        JsonArray value = getJsonArray(idx);
        return value == null ? defaultValue : value;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(int idx, @NonNull Class<T> clazz) {
        if (JsonObject.class.equals(clazz)) {
            return (T) getJsonObject(idx);
        } else if (JsonObject.class.isAssignableFrom(clazz)) {
            return (T) getJsonObject(idx).cast((Class<? extends JsonObject>) clazz);
        } else if (JsonArray.class.equals(clazz)) {
            return (T) getJsonArray(idx);
        } else if (String.class.equals(clazz)) {
            return (T) getString(idx);
        } else if (Boolean.class.equals(clazz)) {
            return (T) getBoolean(idx);
        } else if (Long.class.equals(clazz)) {
            return (T) getLong(idx);
        } else if (Integer.class.equals(clazz)) {
            return (T) getInteger(idx);
        } else if (Short.class.equals(clazz)) {
            return (T) getShort(idx);
        } else if (Byte.class.equals(clazz)) {
            return (T) getByte(idx);
        } else if (Double.class.equals(clazz)) {
            return (T) getDouble(idx);
        } else if (Float.class.equals(clazz)) {
            return (T) getFloat(idx);
        } else if (BigInteger.class.equals(clazz)) {
            return (T) getBigInteger(idx);
        } else if (BigDecimal.class.equals(clazz)) {
            return (T) getBigDecimal(idx);
        }
        throw new JsonException("Failed to get unsupported type " + clazz.getName() + " at index " + idx + "");
    }
    @SuppressWarnings("unchecked")
    public <T> T get(int idx, T... reified) {
        if (reified.length > 0) throw new JsonException("`reified` should be empty.");

        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(idx, clazz);
    }


    /// Adder

    public void add(Object value) {
        try {
            value = ValueUtil.objectToValue(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value to JSON-compatible Value: " + e.getMessage());
        }
        valueList.add(value);
    }

    public void add(int idx, Object value) {
        int pidx = posIndex(idx);
        if (pidx < 0 || pidx > valueList.size()) {
            throw new JsonException("Cannot add index " + idx + " in JsonArray of size " + valueList.size());
        }
        try {
            value = ValueUtil.objectToValue(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to JSON-compatible Value: " +
                    e.getMessage());
        }
        valueList.add(pidx, value);
    }

    public void set(int idx, Object value) {
        int pidx = posIndex(idx);
        if (pidx < 0 || pidx >= valueList.size()) {
            throw new JsonException("Cannot set index " + idx + " in JsonArray of size " + valueList.size());
        }
        try {
            value = ValueUtil.objectToValue(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx + " to JSON-compatible Value: " +
                    e.getMessage());
        }
        valueList.set(pidx, value);
    }

    public void addAll(Object... values) {
        for (Object v : values) { add(v); }
    }

    public void addAll(short... values) {
        for (short v : values) { add(v); }
    }

    public void addAll(int... values) {
        for (int v : values) { add(v); }
    }

    public void addAll(long... values) {
        for (long v : values) { add(v); }
    }

    public void addAll(float... values) {
        for (float v : values) { add(v); }
    }

    public void addAll(double... values) {
        for (double v : values) { add(v); }
    }

    public void addAll(char... values) {
        for (char v : values) { add(v); }
    }

    public void addAll(boolean... values) {
        for (boolean v : values) { add(v); }
    }

    public void addAll(Collection<?> values) {
        for (Object v : values) { add(v); }
    }

    public void addAll(JsonArray jsonArray) {
        for (int i=0; i < jsonArray.size(); i++) {
            add(jsonArray.getObject(i));
        }
    }

    public void remove(int idx) {
        valueList.remove(posIndex(idx));
    }

    public void clear() {
        valueList.clear();
    }

    /// Copy, merge

    public JsonArray deepCopy() {
        JsonArray copy = new JsonArray();
        for (int i = 0; i < size(); i++) {
            Object value = getObject(i);
            if (value instanceof JsonObject) {
                value = ((JsonObject) value).deepCopy();
            } else if (value instanceof JsonArray) {
                value = ((JsonArray) value).deepCopy();
            }
            copy.add(value);
        }
        return copy;
    }

    public void merge(JsonArray target, boolean targetWin, boolean needCopy) {
        if (target == null) return;
        for (int i = 0; i < target.size() && i < size(); i++) {
            Object tarValue = target.getObject(i);
            if (tarValue instanceof JsonObject) {
                Object srcValue = getObject(i);
                if (srcValue instanceof JsonObject) {
                    ((JsonObject) srcValue).merge((JsonObject) tarValue, targetWin, needCopy);
                } else if (targetWin || srcValue == null) {
                    if (needCopy) {
                        set(i, ((JsonObject) tarValue).deepCopy());
                    } else {
                        set(i, tarValue);
                    }
                }
            } else if (tarValue instanceof JsonArray) {
                Object srcValue = getObject(i);
                if (srcValue instanceof JsonArray) {
                    ((JsonArray) srcValue).merge((JsonArray) tarValue, targetWin, needCopy);
                } else if (targetWin || srcValue == null) {
                    if (needCopy) {
                        set(i, ((JsonArray) tarValue).deepCopy());
                    } else {
                        set(i, tarValue);
                    }
                }
            } else if (targetWin && tarValue != null) {
                set(i, tarValue);
            }
        }
        if (target.size() > size()) {
            for (int i = size(); i < target.size(); i++) {
                Object tarValue = target.getObject(i);
                if (needCopy) {
                    if (tarValue instanceof JsonObject) add(((JsonObject) tarValue).deepCopy());
                    if (tarValue instanceof JsonArray) add(((JsonArray) tarValue).deepCopy());
                } else {
                    add(tarValue);
                }
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
    public void merge(JsonArray target) {
        merge(target, true, false);
    }

    public void mergeWithCopy(JsonArray target) {
        merge(target, true, true);
    }



}
