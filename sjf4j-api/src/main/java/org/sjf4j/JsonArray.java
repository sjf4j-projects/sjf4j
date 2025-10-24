package org.sjf4j;

import lombok.NonNull;
import org.sjf4j.util.NumberUtil;
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
import java.util.List;

public class JsonArray {

    protected List<Object> objectList;

    public JsonArray() {
        objectList = new ArrayList<Object>();
    }

    public JsonArray(@NonNull JsonArray target) {
        this.objectList = target.objectList;
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


    /// Json

    public static JsonArray fromJson(@NonNull String input) {
        return fromJson(new StringReader(input), JsonFacadeFactory.getDefaultJsonFacade());
    }

    public static JsonArray fromJson(@NonNull Reader input) {
        return fromJson(input, JsonFacadeFactory.getDefaultJsonFacade());
    }

    public static JsonArray fromJson(@NonNull String input, @NonNull JsonFacade jsonFacade) {
        return fromJson(new StringReader(input), jsonFacade);
    }

    public static JsonArray fromJson(@NonNull Reader input, @NonNull JsonFacade jsonFacade) {
        return jsonFacade.readArray(input);
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
        return objectList.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof JsonArray) {
            return objectList.equals(((JsonArray) object).objectList);
        }
        return false;
    }

    /// List

    public int size() {
        return objectList.size();
    }

    public boolean isEmpty() {
        return objectList.isEmpty();
    }

    public List<Object> toList() {
        return Collections.unmodifiableList(objectList);
    }


    /// getter

    public Object getObject(int idx) {
        return objectList.get(idx);
    }

    public String getString(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof String) {
            return (String) value;
        }
        throw new JsonException("Expected String at index " + idx + ", but got '" + value.getClass() + "'");
    }

    public String getAsString(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }

    public Long getLong(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.numberAsLong((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to convert numeric value at index " + idx +
                        " to Long: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value at index " + idx + ", but got '" +
                value.getClass() + "'");
    }

    public Integer getInteger(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.numberAsInteger((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to convert numeric value at index " + idx +
                        " to Integer: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value at index " + idx + ", but got '" +
                value.getClass() + "'");
    }

    public Short getShort(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.numberAsShort((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to convert numeric value at index " + idx +
                        " to Short: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value at index " + idx + ", but got '" +
                value.getClass() + "'");
    }

    public Byte getByte(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.numberAsByte((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to convert numeric value at index " + idx +
                        " to Byte: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value at index " + idx + ", but got '" +
                value.getClass() + "'");
    }

    public Double getDouble(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.numberAsDouble((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to convert numeric value at index " + idx +
                        " to Double: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value at index " + idx + ", but got '" +
                value.getClass() + "'");
    }

    public Float getFloat(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.numberAsFloat((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to convert numeric value at index " + idx +
                        " to Float: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value at index " + idx + ", but got '" +
                value.getClass() + "'");
    }

    public BigInteger getBigInteger(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.numberAsBigInteger((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to convert numeric value at index " + idx +
                        " to BigInteger: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value at index " + idx + ", but got '" +
                value.getClass() + "'");
    }

    public BigDecimal getBigDecimal(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            try {
                return NumberUtil.numberAsBigDecimal((Number) value);
            } catch (Exception e) {
                throw new JsonException("Failed to convert numeric value at index " + idx +
                        " to BigDecimal: " + e.getMessage());
            }
        }
        throw new JsonException("Expected a numeric value at index " + idx + ", but got '" +
                value.getClass() + "'");
    }

    public Boolean getBoolean(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new JsonException("Expected Boolean at index " + idx + ", but got '" +
                value.getClass() + "'");
    }

    public JsonObject getJsonObject(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof JsonObject) {
            return (JsonObject) value;
        }
        throw new JsonException("Expected JsonObject at index " + idx + ", but got '" +
                value.getClass() + "'");
    }

    public JsonArray getJsonArray(int idx) {
        Object value = getObject(idx);
        if (value == null) {
            return null;
        } else if (value instanceof JsonArray) {
            return (JsonArray) value;
        }
        throw new JsonException("Expected JsonArray at index " + idx + ", but got '" +
                value.getClass() + "'");
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
        throw new JsonException("Cannot convert value at index " + idx + " to unsupported type: " + clazz.getName());
    }
    @SuppressWarnings("unchecked")
    public <T> T get(int idx, T... reified) {
        if (reified.length > 0) throw new JsonException("`reified` should be empty.");

        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(idx, clazz);
    }


    /// adder

    public void add(Object value) {
        try {
            value = ValueUtil.objectToValue(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value to JSON-compatible Value: " + e.getMessage());
        }
        objectList.add(value);
    }

    public void add(int idx, Object value) {
        try {
            value = ValueUtil.objectToValue(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx +
                    " to JSON-compatible Value: " + e.getMessage());
        }
        objectList.add(idx, value);
    }

    public void set(int idx, Object value) {
        try {
            value = ValueUtil.objectToValue(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value at index " + idx +
                    " to JSON-compatible Value: " + e.getMessage());
        }
        objectList.set(idx, value);
    }

    public void addAll(Object... values) {
        for (Object v : values) {
            add(v);
        }
    }

    public void addAll(short... values) {
        for (short v : values) {
            add(v);
        }
    }

    public void addAll(int... values) {
        for (int v : values) {
            add(v);
        }
    }

    public void addAll(long... values) {
        for (long v : values) {
            add(v);
        }
    }

    public void addAll(float... values) {
        for (float v : values) {
            add(v);
        }
    }

    public void addAll(double... values) {
        for (double v : values) {
            add(v);
        }
    }

    public void addAll(char... values) {
        for (char v : values) {
            add(v);
        }
    }

    public void addAll(boolean... values) {
        for (boolean v : values) {
            add(v);
        }
    }

    public void addAll(Collection<?> values) {
        for (Object v : values) {
            add(v);
        }
    }

    public void addAll(JsonArray jsonArray) {
        int size = jsonArray.size();
        for (int i=0; i < size; i++) {
            add(jsonArray.getObject(i));
        }
    }

    public void remove(int idx) {
        objectList.remove(idx);
    }

    public void clear() {
        objectList.clear();
    }

    /// need override

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


    /// private


}
