package org.sjf4j;

import lombok.NonNull;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.YamlFacade;
import org.sjf4j.util.ObjectUtil;
import org.sjf4j.util.ValueUtil;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
//    public JsonArray(short... objects) {
//        this();
//        addAll(objects);
//    }
//    public JsonArray(int... objects) {
//        this();
//        addAll(objects);
//    }
//    public JsonArray(long... objects) {
//        this();
//        addAll(objects);
//    }
//    public JsonArray(float... objects) {
//        this();
//        addAll(objects);
//    }
//    public JsonArray(double... objects) {
//        this();
//        addAll(objects);
//    }
//    public JsonArray(char... objects) {
//        this();
//        addAll(objects);
//    }
//    public JsonArray(boolean... objects) {
//        this();
//        addAll(objects);
//    }

    public JsonArray(Collection<?> objects) {
        this();
        addAll(objects);
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
    public boolean equals(Object target) {
        if (target instanceof JsonArray) {
            return valueList.equals(((JsonArray) target).valueList);
        }
        return false;
    }

    @Override
    public int size() {
        return valueList.size();
    }

    public boolean isEmpty() {
        return valueList.isEmpty();
    }

    private List<Object> toList() {
        return Collections.unmodifiableList(valueList);
    }

    public void forEach(Consumer<Object> action) {
        for (int i = 0; i < valueList.size(); i++) {
            action.accept(valueList.get(i));
        }
    }

    public void forEach(BiConsumer<Integer, Object> action) {
        for (int i = 0; i < valueList.size(); i++) {
            action.accept(i, valueList.get(i));
        }
    }

    @Override
    @NonNull
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

    public boolean contains(Object value) {
        for (Object o : valueList) {
            if (Objects.equals(o, value)) {
                return true;
            }
        }
        return false;
    }

    /// JSON Facade

    public static JsonArray fromJson(@NonNull String input) {
        return fromJson(new StringReader(input));
    }

    public static JsonArray fromJson(@NonNull Reader input) {
        return Sjf4j.readObjectFromJson(input, JsonArray.class);
    }

    public String toJson() {
        StringWriter output = new StringWriter();
        toJson(output);
        return output.toString();
    }

    public void toJson(@NonNull Writer output) {
        Sjf4j.writeNodeToJson(output, this);
    }

    /// YAML Facade

    public static JsonArray fromYaml(@NonNull String input) {
        return fromYaml(new StringReader(input));
    }

    public static JsonArray fromYaml(@NonNull Reader input) {
        return Sjf4j.readObjectFromYaml(input, JsonArray.class);
    }

    public String toYaml() {
        StringWriter output = new StringWriter();
        toYaml(output);
        return output.toString();
    }

    public void toYaml(@NonNull Writer output) {
        Sjf4j.writeNodeToYaml(output, this);
    }

    /// POJO

    public static JsonArray fromPojo(Object pojo) {
        return (JsonArray) ObjectUtil.object2Value(pojo);
    }

    @SuppressWarnings("unchecked")
    public <T> T toPojo(Type type) {
        return (T) ObjectUtil.value2Object(this, type);
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
        Object value = getObject(idx);
        return value == null ? defaultValue : value;
    }

    public String getString(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get String at index " + idx + " : " + e.getMessage(), e);
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
            throw new JsonException("Failed to get as String at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public Long getLong(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueAsLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Long at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public long getLong(int idx, long defaultValue) {
        Long value = getLong(idx);
        return value == null ? defaultValue : value;
    }

    public Integer getInteger(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueAsInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Integer at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public int getInteger(int idx, int defaultValue) {
        Integer value = getInteger(idx);
        return value == null ? defaultValue : value;
    }

    public Short getShort(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueAsShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Short at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public short getShort(int idx, short defaultValue) {
        Short value = getShort(idx);
        return value == null ? defaultValue : value;
    }

    public Byte getByte(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueAsByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Byte at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public byte getByte(int idx, byte defaultValue) {
        Byte value = getByte(idx);
        return value == null ? defaultValue : value;
    }

    public Double getDouble(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueAsDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Double at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public double getDouble(int idx, double defaultValue) {
        Double value = getDouble(idx);
        return value == null ? defaultValue : value;
    }

    public Float getFloat(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueAsFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Float at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public float getFloat(int idx, float defaultValue) {
        Float value = getFloat(idx);
        return value == null ? defaultValue : value;
    }

    public BigInteger getBigInteger(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueAsBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigInteger at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public BigInteger getBigInteger(int idx, BigInteger defaultValue) {
        BigInteger value = getBigInteger(idx);
        return value == null ? defaultValue : value;
    }

    public BigDecimal getBigDecimal(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueAsBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigDecimal at index " + idx + " : " + e.getMessage(), e);
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
            throw new JsonException("Failed to get Boolean at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public boolean getBoolean(int idx, boolean defaultValue) {
        Boolean value = getBoolean(idx);
        return value == null ? defaultValue : value;
    }

    public JsonObject getJsonObject(int idx) {
        try {
            Object value = getObject(idx);
            return ValueUtil.valueToJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonObject at index " + idx + " : " + e.getMessage(), e);
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
            throw new JsonException("Failed to get JsonArray at index " + idx + " : " + e.getMessage(), e);
        }
    }

    public JsonArray getJsonArray(int idx, JsonArray defaultValue) {
        JsonArray value = getJsonArray(idx);
        return value == null ? defaultValue : value;
    }

    public <T> T get(int idx, @NonNull Class<T> clazz) {
        Object value = getObject(idx);
        try {
            return ValueUtil.valueTo(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to get " + clazz.getName() + " at index " + idx + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(int idx, T... reified) {
        if (reified.length > 0) throw new JsonException("`reified` should be empty.");

        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(idx, clazz);
    }


    /// Adder

    public void add(Object object) {
        object = ObjectUtil.wrapObject(object);
        if (ObjectUtil.isValidOrConvertible(object)) {
            valueList.add(object);
        } else {
            throw new JsonException("Not a valid JSON value or a JSON-convertible object");
        }
    }

    public void add(int idx, Object object) {
        int pidx = posIndex(idx);
        if (pidx < 0 || pidx > valueList.size()) {
            throw new JsonException("Cannot add index " + idx + " in JsonArray of size " + valueList.size());
        }

        object = ObjectUtil.wrapObject(object);
        if (ObjectUtil.isValidOrConvertible(object)) {
            valueList.add(pidx, object);
        } else {
            throw new JsonException("Not a valid JSON value or a JSON-convertible object at index " + idx);
        }
    }

    public void set(int idx, Object object) {
        int pidx = posIndex(idx);
        if (pidx < 0 || pidx >= valueList.size()) {
            throw new JsonException("Cannot set index " + idx + " in JsonArray of size " + valueList.size());
        }

        object = ObjectUtil.wrapObject(object);
        if (ObjectUtil.isValidOrConvertible(object)) {
            valueList.set(pidx, object);
        } else {
            throw new JsonException("Not a valid JSON value or a JSON-convertible object at index " + idx);
        }
    }

    public void addAll(Object... values) {
        for (Object v : values) {
            if (v != null && v.getClass().isArray()) {
                int len = Array.getLength(v);
                for (int i = 0; i < len; i++) {
                    add(Array.get(v, i));
                }
            } else {
                add(v);
            }
        }
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
//        JsonArray copy = new JsonArray();
//        for (int i = 0; i < size(); i++) {
//            Object value = getObject(i);
//            if (value instanceof JsonObject) {
//                value = ((JsonObject) value).deepCopy();
//            } else if (value instanceof JsonArray) {
//                value = ((JsonArray) value).deepCopy();
//            }
//            copy.add(value);
//        }
//        return copy;
        return (JsonArray) ObjectUtil.object2Value(this);
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
     * <p>All key-value pairs from the target JsonObject will be merged into this JsonObject.
     * When a key exists in both JsonObjects, the target wins.</p>
     *
     */
    public void merge(JsonArray target) {
        merge(target, true, false);
    }

    public void mergeWithCopy(JsonArray target) {
        merge(target, true, true);
    }



}
