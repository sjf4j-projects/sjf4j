package org.sjf4j;

import lombok.NonNull;
import org.sjf4j.util.NodeUtil;
import org.sjf4j.util.TypeReference;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class JsonArray extends JsonContainer implements Iterable<Object> {

    protected transient List<Object> nodeList;

    public JsonArray() {
        super();
    }

    @SuppressWarnings("unchecked")
    public JsonArray(@NonNull Object node) {
        this();
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            this.nodeList = ja.nodeList;
        } else if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            if (!list.isEmpty()) this.nodeList = list;
        } else if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            if (len > 0) {
                this.nodeList = JsonConfig.global().listSupplier.create();
                for (int i = 0; i < len; i++) {
                    this.nodeList.add(Array.get(node, i));
                }
            }
        } else {
            throw new JsonException("Cannot wrap value of type '" + node.getClass().getName() +
                    "' into JsonArray. Supported types are: JsonArray, List, or Array.");
        }
    }

//    public JsonArray(Object[] objects) {
//        this();
//        addAll(objects);
//    }
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
//    public JsonArray(@NonNull List<Object> nodeList) {
//        this();
//        this.nodeList = nodeList;
//    }

    /// Object

    public String inspect() {
        return NodeUtil.inspect(this);
    }

    @Override
    public String toString() {
        return toJson();
    }

    @Override
    public int hashCode() {
        return nodeList == null ? 0 : nodeList.hashCode();
    }

    @Override
    public boolean equals(Object target) {
        return JsonWalker.equals(this, target);
//        if (target == this) return true;
//        if (target == null) return false;
//        if (target instanceof JsonArray) {
//            return Objects.equals(nodeList, ((JsonArray) target).nodeList);
//        }
//        return false;
    }

    @Override
    public int size() {
        return nodeList == null ? 0 : nodeList.size();
    }

    public boolean isEmpty() {
        return nodeList == null || nodeList.isEmpty();
    }

    private List<Object> toList() {
        return nodeList == null ? Collections.emptyList() : nodeList;
    }

    public void forEach(Consumer<Object> action) {
        if (nodeList == null) return;
        for (Object object : nodeList) {
            action.accept(object);
        }
    }

    public void forEach(BiConsumer<Integer, Object> action) {
        if (nodeList == null) return;
        for (int i = 0; i < nodeList.size(); i++) {
            action.accept(i, nodeList.get(i));
        }
    }

    @Override
    @NonNull
    public Iterator<Object> iterator() {
        return toList().iterator();
    }

    private int posIndex(int idx) {
        return idx < 0 ? size() + idx : idx;
    }

    public boolean containsIndex(int idx) {
        idx = posIndex(idx);
        return idx >= 0 && idx < size();
    }

    public boolean contains(Object value) {
        if (nodeList == null) return false;
        for (Object o : nodeList) {
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
        return Sjf4j.fromJson(input, JsonArray.class);
    }

    public String toJson() {
        return Sjf4j.toJson(this);
    }

    /// YAML Facade

    public static JsonArray fromYaml(@NonNull String input) {
        return fromYaml(new StringReader(input));
    }

    public static JsonArray fromYaml(@NonNull Reader input) {
        return Sjf4j.fromYaml(input, JsonArray.class);
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

    public static JsonArray fromPojo(@NonNull Object pojo) {
//        return (JsonObject) ObjectUtil.object2Value(pojo);
        return (JsonArray) JsonConfig.global().getObjectFacade().readNode(pojo, JsonArray.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T toPojo(@NonNull Class<T> clazz) {
        return (T) JsonConfig.global().getObjectFacade().readNode(this, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T toPojo(@NonNull TypeReference<T> type) {
        return (T) JsonConfig.global().getObjectFacade().readNode(this, type.getType());
    }

    /// Getter

    public Object getObject(int idx) {
        int pidx = posIndex(idx);
        if (pidx >= 0 && pidx < size()) {
            return nodeList.get(pidx);
        } else {
            return null;
        }
    }
    public Object getObject(int idx, Object defaultValue) {
        Object value = getObject(idx);
        return value == null ? defaultValue : value;
    }

    public String getString(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.toString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get String at index " + idx, e);
        }
    }
    public String getString(int idx, String defaultValue) {
        String value = getString(idx);
        return value == null ? defaultValue : value;
    }

    public String asString(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.asString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to String: " + e.getMessage(), e);
        }
    }
    public String asString(int idx, String defaultValue) {
        String value = asString(idx);
        return value == null ? defaultValue : value;
    }

    public Long getLong(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.toLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Long at index " + idx, e);
        }
    }
    public long getLong(int idx, long defaultValue) {
        Long value = getLong(idx);
        return value == null ? defaultValue : value;
    }

    public Long asLong(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.asLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to Long: " + e.getMessage(), e);
        }
    }
    public long asLong(int idx, long defaultValue) {
        Long value = asLong(idx);
        return value == null ? defaultValue : value;
    }

    public Integer getInteger(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.toInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Integer at index " + idx, e);
        }
    }
    public int getInteger(int idx, int defaultValue) {
        Integer value = getInteger(idx);
        return value == null ? defaultValue : value;
    }

    public Integer asInteger(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.asInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to Integer: " + e.getMessage(), e);
        }
    }
    public int asInteger(int idx, int defaultValue) {
        Integer value = asInteger(idx);
        return value == null ? defaultValue : value;
    }

    public Short getShort(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.toShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Short at index " + idx, e);
        }
    }
    public short getShort(int idx, short defaultValue) {
        Short value = getShort(idx);
        return value == null ? defaultValue : value;
    }

    public Short asShort(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.asShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to Short: " + e.getMessage(), e);
        }
    }
    public short asShort(int idx, short defaultValue) {
        Short value = asShort(idx);
        return value == null ? defaultValue : value;
    }

    public Byte getByte(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.toByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Byte at index " + idx, e);
        }
    }
    public byte getByte(int idx, byte defaultValue) {
        Byte value = getByte(idx);
        return value == null ? defaultValue : value;
    }

    public Byte asByte(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.asByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to Byte: " + e.getMessage(), e);
        }
    }
    public byte asByte(int idx, byte defaultValue) {
        Byte value = asByte(idx);
        return value == null ? defaultValue : value;
    }

    public Double getDouble(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.toDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Double at index " + idx, e);
        }
    }
    public double getDouble(int idx, double defaultValue) {
        Double value = getDouble(idx);
        return value == null ? defaultValue : value;
    }

    public Double asDouble(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.asDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to Double: " + e.getMessage(), e);
        }
    }
    public double asDouble(int idx, double defaultValue) {
        Double value = asDouble(idx);
        return value == null ? defaultValue : value;
    }

    public Float getFloat(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.toFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Float at index " + idx, e);
        }
    }
    public float getFloat(int idx, float defaultValue) {
        Float value = getFloat(idx);
        return value == null ? defaultValue : value;
    }

    public Float asFloat(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.asFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to Float: " + e.getMessage(), e);
        }
    }
    public float asFloat(int idx, float defaultValue) {
        Float value = asFloat(idx);
        return value == null ? defaultValue : value;
    }

    public BigInteger getBigInteger(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.toBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigInteger at index " + idx, e);
        }
    }
    public BigInteger getBigInteger(int idx, BigInteger defaultValue) {
        BigInteger value = getBigInteger(idx);
        return value == null ? defaultValue : value;
    }

    public BigInteger asBigInteger(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.asBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to BigInteger: " + e.getMessage(), e);
        }
    }
    public BigInteger asBigInteger(int idx, BigInteger defaultValue) {
        BigInteger value = asBigInteger(idx);
        return value == null ? defaultValue : value;
    }

    public BigDecimal getBigDecimal(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.toBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigDecimal at index " + idx, e);
        }
    }
    public BigDecimal getBigDecimal(int idx, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(idx);
        return value == null ? defaultValue : value;
    }

    public BigDecimal asBigDecimal(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.asBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to BigDecimal: " + e.getMessage(), e);
        }
    }
    public BigDecimal asBigDecimal(int idx, BigDecimal defaultValue) {
        BigDecimal value = asBigDecimal(idx);
        return value == null ? defaultValue : value;
    }

    public Boolean getBoolean(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.toBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Boolean at index " + idx, e);
        }
    }
    public boolean getBoolean(int idx, boolean defaultValue) {
        Boolean value = getBoolean(idx);
        return value == null ? defaultValue : value;
    }

    public Boolean asBoolean(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.asBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to Boolean: " + e.getMessage(), e);
        }
    }
    public boolean asBoolean(int idx, boolean defaultValue) {
        Boolean value = asBoolean(idx);
        return value == null ? defaultValue : value;
    }

    public JsonObject getJsonObject(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.toJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonObject at index " + idx, e);
        }
    }
    public JsonObject getJsonObject(int idx, JsonObject defaultValue) {
        JsonObject value = getJsonObject(idx);
        return value == null ? defaultValue : value;
    }

    public JsonObject asJsonObject(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.asJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to JsonObject: " + e.getMessage(), e);
        }
    }
    public JsonObject asJsonObject(int idx, JsonObject defaultValue) {
        JsonObject value = asJsonObject(idx);
        return value == null ? defaultValue : value;
    }

    public JsonArray getJsonArray(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.toJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonArray at index " + idx, e);
        }
    }
    public JsonArray getJsonArray(int idx, JsonArray defaultValue) {
        JsonArray value = getJsonArray(idx);
        return value == null ? defaultValue : value;
    }

    public JsonArray asJsonArray(int idx) {
        try {
            Object value = getObject(idx);
            return NodeUtil.asJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to JsonArray: " + e.getMessage(), e);
        }
    }
    public JsonArray asJsonArray(int idx, JsonArray defaultValue) {
        JsonArray value = asJsonArray(idx);
        return value == null ? defaultValue : value;
    }

    public <T> T get(int idx, @NonNull Class<T> clazz) {
        Object value = getObject(idx);
        try {
            return NodeUtil.to(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to get " + clazz.getName() + " at index " + idx, e);
        }
    }
    @SuppressWarnings("unchecked")
    public <T> T get(int idx, T... reified) {
        if (reified.length > 0) throw new JsonException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(idx, clazz);
    }

    public <T> T as(int idx, @NonNull Class<T> clazz) {
        Object value = getObject(idx);
        try {
            return NodeUtil.as(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert index '" + idx + "' to " + clazz.getName() +
                    ": " + e.getMessage(), e);
        }
    }
    @SuppressWarnings("unchecked")
    public <T> T as(int idx, T... reified) {
        if (reified.length > 0) throw new JsonException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return as(idx, clazz);
    }


    /// Adder

    public void add(Object object) {
//        object = ObjectUtil.wrapObject(object);
//        if (!ObjectUtil.isValidOrConvertible(object)) {
//            throw new JsonException("Not a valid JSON value or a JSON-convertible object");
//        }
        if (nodeList == null) nodeList = JsonConfig.global().listSupplier.create();
        nodeList.add(object);
    }

    public void add(int idx, Object object) {
        int pidx = posIndex(idx);
        if (pidx < 0 || pidx > size()) {
            throw new JsonException("Cannot add index " + idx + " in JsonArray of size " + size());
        }

//        object = ObjectUtil.wrapObject(object);
//        if (!ObjectUtil.isValidOrConvertible(object)) {
//            throw new JsonException("Not a valid JSON value or a JSON-convertible object at index " + idx);
//        }
        if (nodeList == null) nodeList = JsonConfig.global().listSupplier.create();
        nodeList.add(pidx, object);
    }

    public void set(int idx, Object object) {
        int pidx = posIndex(idx);
        if (pidx < 0 || pidx >= size()) {
            throw new JsonException("Cannot set index " + idx + " in JsonArray of size " + size());
        }

//        object = ObjectUtil.wrapObject(object);
//        if (!ObjectUtil.isValidOrConvertible(object)) {
//            throw new JsonException("Not a valid JSON value or a JSON-convertible object at index " + idx);
//        }
        if (nodeList == null) nodeList = JsonConfig.global().listSupplier.create();
        nodeList.set(pidx, object);
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

    public Object remove(int idx) {
        if (nodeList == null) return null;
        return nodeList.remove(posIndex(idx));
    }

    public void clear() {
        if (nodeList == null) return;
        nodeList.clear();
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
        return (JsonArray) JsonConfig.global().getObjectFacade().readNode(this, JsonArray.class);
    }

    public void merge(JsonArray target, boolean preferTarget, boolean needCopy) {
        JsonWalker.merge(this, target, preferTarget, needCopy);
//        if (target == null) return;
//        for (int i = 0; i < target.size() && i < size(); i++) {
//            Object tarValue = target.getObject(i);
//            if (tarValue instanceof JsonObject) {
//                Object srcValue = getObject(i);
//                if (srcValue instanceof JsonObject) {
//                    ((JsonObject) srcValue).merge((JsonObject) tarValue, targetWin, needCopy);
//                } else if (targetWin || srcValue == null) {
//                    if (needCopy) {
//                        set(i, ((JsonObject) tarValue).deepCopy());
//                    } else {
//                        set(i, tarValue);
//                    }
//                }
//            } else if (tarValue instanceof JsonArray) {
//                Object srcValue = getObject(i);
//                if (srcValue instanceof JsonArray) {
//                    ((JsonArray) srcValue).merge((JsonArray) tarValue, targetWin, needCopy);
//                } else if (targetWin || srcValue == null) {
//                    if (needCopy) {
//                        set(i, ((JsonArray) tarValue).deepCopy());
//                    } else {
//                        set(i, tarValue);
//                    }
//                }
//            } else if (targetWin && tarValue != null) {
//                set(i, tarValue);
//            }
//        }
//        if (target.size() > size()) {
//            for (int i = size(); i < target.size(); i++) {
//                Object tarValue = target.getObject(i);
//                if (needCopy) {
//                    if (tarValue instanceof JsonObject) add(((JsonObject) tarValue).deepCopy());
//                    if (tarValue instanceof JsonArray) add(((JsonArray) tarValue).deepCopy());
//                } else {
//                    add(tarValue);
//                }
//            }
//        }
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
