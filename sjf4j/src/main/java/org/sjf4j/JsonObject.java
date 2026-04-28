package org.sjf4j;

import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.FacadeNodes;
import org.sjf4j.node.NodeStream;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Nodes;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;


/**
 * JSON object container in SJF4J's OBNT model.
 *
 * <p>{@link JsonObject} can be used directly as a mutable JSON object node, or
 * subclassed to define a JOJO (JSON Object Java Object). A JOJO combines
 * declared Java fields with dynamic JSON properties in the same object, so a
 * model can keep typed domain fields without losing extra object members from
 * input JSON.
 *
 * <p>Dynamic fields are stored in {@code dynamicMap}; declared fields are mapped
 * via {@link NodeRegistry.FieldInfo}. Accessors use {@link Nodes} conversion
 * semantics for strict/lenient reads.
 */
public class JsonObject extends JsonContainer {

    /**
     * Stores dynamic JSON nodes as key-value pairs.
     */
    protected transient Map<String, Object> dynamicMap;
    
    /**
     * Stores field information for POJO mapping.
     */
    protected final transient NodeRegistry.PojoInfo pi =
            this.getClass() == JsonObject.class
            ? null
            : NodeRegistry.registerPojoOrElseThrow(this.getClass());

    /**
     * Creates an empty JsonObject instance.
     */
    public JsonObject() {
        super();
    }

    /**
     * Creates a JsonObject by wrapping or converting an object-like node.
     * <p>
     * When the source already uses a compatible object backing, this constructor
     * shares that backing storage. Otherwise, it projects the source into this
     * container by copying exposed entries. In practice:
     * <ul>
     *     <li>{@link Map} shares as the dynamic backing map</li>
     *     <li>plain {@link JsonObject} shares its dynamic backing map</li>
     *     <li>JOJO/POJO inputs are copied by exposed node fields</li>
     *     <li>facade object nodes are copied by exposed entries</li>
     *     <li>unsupported object kinds fail fast</li>
     * </ul>
     *
     * <p>This is useful when you want object-style APIs on top of an existing
     * object node, but only plain {@link JsonObject} preserves a shared dynamic
     * backing map. Facade object nodes plus JOJO and POJO inputs are reprojected
     * field by field.
     */
    @SuppressWarnings("unchecked")
    public JsonObject(Object node) {
        this();
        if (node == null) return;
        if (node instanceof Map) {
            this.dynamicMap = (Map<String, Object>) node;
            return;
        }
        if (node instanceof JsonObject) {
            JsonObject jo = (JsonObject) node;
            if (jo.getClass() == JsonObject.class) {
                this.dynamicMap = jo.dynamicMap;
                return;
            }
            putAll(jo);
            return;
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
        if (pi != null && !pi.isJajo) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.readableFields.entrySet()) {
                Object v = entry.getValue().invokeGetter(node);
                put(entry.getKey(), v);
            }
            return;
        }
        if (FacadeNodes.isNode(node)) {
            putAll(FacadeNodes.toMap(node));
            return;
        }
        throw new JsonException("Cannot wrap value of type '" + node.getClass().getName() +
                "' into JsonObject. Supported types are: Map, JsonObject, JOJO, POJO, or facade object node.");
    }

    /**
     * Creates a JsonObject from alternating key-value pairs.
     */
    public static JsonObject of(Object... keyValues) {
        JsonObject jo = new JsonObject();
        if (keyValues == null || keyValues.length == 0) return jo;
        if ((keyValues.length & 1) != 0) {
            throw new JsonException("JsonObject.of requires an even number of arguments");
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (!(key instanceof String)) {
                throw new JsonException("JsonObject.of key at index " + i + " must be a String");
            }
            jo.put((String) key, keyValues[i + 1]);
        }
        return jo;
    }

    /// Dynamic

    public Map<String, Object> getDynamicMap() {
        return this.dynamicMap;
    }

    /**
     * Replaces the dynamic map backing this object.
     */
    public void setDynamicMap(Map<String, Object> map) {
        this.dynamicMap = map;
    }


    /// Map

    /**
     * Computes hash code from readable declared fields and dynamic entries.
     */
    @Override
    public int hashCode() {
        int hash = dynamicMap == null ? 0 : dynamicMap.hashCode();
        if (pi != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.readableFields.entrySet()){
                hash += Objects.hashCode(entry.getKey()) ^
                        Objects.hashCode(entry.getValue().invokeGetter(this));
            }
        }
        return hash;
    }

    /**
     * Compares JsonObject values within the same concrete runtime type.
     * <p>
     * Equality uses the readable object view while preserving same-concrete-type
     * semantics.
     */
    @Override
    public boolean equals(Object target) {
        if (target == this) return true;
        if (target == null || target.getClass() != this.getClass()) return false;
        JsonObject targetJo = (JsonObject) target;
        if (targetJo.size() != this.size()) return false;
        for (Map.Entry<String, Object> entry : entrySet()) {
            Object value = entry.getValue();
            Object targetValue = targetJo.getNode(entry.getKey());
            if (value == null) {
                if (!targetJo.containsKey(entry.getKey()) || targetJo.getNode(entry.getKey()) != null) {
                    return false;
                }
            } else {
                if (!Objects.equals(targetValue, entry.getValue())) return false;
            }
        }
        return true;
    }

    /**
     * Returns the number of readable declared fields and dynamic entries.
     */
    public int size() {
        return (pi == null ? 0 : pi.readableFieldCount) + (dynamicMap == null ? 0 : dynamicMap.size());
    }

    /**
     * Returns true if this object has no entries.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns a merged key set of readable declared fields and dynamic entries.
     */
    public Set<String> keySet() {
        if (pi == null) {
            return dynamicMap == null ? Collections.emptySet() : dynamicMap.keySet();
        } else if (dynamicMap == null) {
            return pi.readableFields.keySet();
        } else {
            return new AbstractSet<String>() {
                @SuppressWarnings("NullableProblems")
                @Override
                public Iterator<String> iterator() {
                    return new Iterator<String>() {
                        private final Iterator<String> fieldIterator = pi.readableFields.keySet().iterator();
                        private final Iterator<String> dynamicIterator = dynamicMap.keySet().iterator();

                        @Override
                        public boolean hasNext() {
                            if (fieldIterator.hasNext()) return true;
                            return dynamicIterator.hasNext();
                        }

                        @Override
                        public String next() {
                            if (fieldIterator.hasNext()) return fieldIterator.next();
                            return dynamicIterator.next();
                        }
                    };
                }

                @Override
                public int size() {
                    return pi.readableFieldCount + dynamicMap.size();
                }
            };
        }
    }

    /**
     * Returns true if the key exists in readable declared fields or dynamic entries.
     */
    public boolean containsKey(String key) {
        if (key == null) return false;
        return (pi != null && pi.readableFields.containsKey(key))
                || (dynamicMap != null && dynamicMap.containsKey(key));
    }

    /**
     * Returns true if the key exists and the value is non-null.
     */
    public boolean hasNonNull(String key) {
        if (key == null) return false;
        return getNode(key) != null;
    }

    /**
     * Performs the given visitor for each readable entry.
     */
    public void forEach(BiConsumer<String, Object> visitor) {
        Objects.requireNonNull(visitor, "visitor");
        if (pi != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.readableFields.entrySet()){
                visitor.accept(entry.getKey(), entry.getValue().invokeGetter(this));
            }
        }
        if (dynamicMap != null) {
            for (Map.Entry<String, Object> entry : dynamicMap.entrySet()){
                visitor.accept(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Returns true if any readable entry matches the predicate.
     */
    public boolean anyMatch(BiPredicate<String, Object> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        if (pi != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.readableFields.entrySet()){
                if (predicate.test(entry.getKey(), entry.getValue().invokeGetter(this))) {
                    return true;
                }
            }
        }
        if (dynamicMap != null) {
            for (Map.Entry<String, Object> entry : dynamicMap.entrySet()){
                if (predicate.test(entry.getKey(), entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Replaces values for readable-and-writable declared fields and dynamic
     * entries in place.
     */
    public boolean replace(BiFunction<String, Object, Object> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        boolean changed = false;
        if (pi != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.readableFields.entrySet()){
                NodeRegistry.FieldInfo fi = entry.getValue();
                if (!fi.hasSetter()) {
                    continue;
                }
                Object oldValue = fi.invokeGetter(this);
                Object newValue = mapper.apply(entry.getKey(), oldValue);
                if (newValue != oldValue) {
                    fi.invokeSetter(this, newValue);
                    changed = true;
                }
            }
        }
        if (dynamicMap != null) {
            for (Map.Entry<String, Object> entry : dynamicMap.entrySet()){
                Object oldValue = entry.getValue();
                Object newValue = mapper.apply(entry.getKey(), oldValue);
                if (newValue != oldValue) {
                    entry.setValue(newValue);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Returns a merged Map view of readable declared fields and dynamic entries.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (pi != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.readableFields.entrySet()){
                merged.put(entry.getKey(), entry.getValue().invokeGetter(this));
            }
        }
        if (dynamicMap != null) {
            merged.putAll(dynamicMap);
        }
        return merged;
    }

    /**
     * Converts this object to a typed Map.
     */
    public <T> Map<String, T> toMap(Class<T> clazz) {
        return Nodes.toMap(this, clazz);
    }

    /**
     * Binds this object into the requested POJO, JOJO, or JAJO target type.
     * <p>
     * This follows {@link Nodes#toPojo(Object, Class)} semantics and does not
     * force a deep copy of nested containers.
     */
    public <T> T toPojo(Class<T> clazz) {
        return Nodes.toPojo(this, clazz);
    }

    /**
     * Returns a merged entry set of readable declared fields and dynamic entries.
     */
    public Set<Map.Entry<String, Object>> entrySet() {
        if (pi == null) {
            return dynamicMap == null ? Collections.emptySet() : dynamicMap.entrySet();
        } else if (dynamicMap == null) {
            return new AbstractSet<Map.Entry<String, Object>>() {
                @SuppressWarnings("NullableProblems")
                @Override
                public Iterator<Map.Entry<String, Object>> iterator() {
                    final Iterator<Map.Entry<String, NodeRegistry.FieldInfo>> fieldIterator =
                            pi.readableFields.entrySet().iterator();
                    return new Iterator<Map.Entry<String, Object>>() {
                        @Override
                        public boolean hasNext() {
                            return fieldIterator.hasNext();
                        }

                        @Override
                        public Map.Entry<String, Object> next() {
                            Map.Entry<String, NodeRegistry.FieldInfo> entry = fieldIterator.next();
                            Object value = entry.getValue().invokeGetter(JsonObject.this);
                            return new AbstractMap.SimpleEntry<>(entry.getKey(), value);
                        }
                    };
                }

                @Override
                public int size() {
                    return pi.readableFieldCount;
                }
            };
        } else {
            return new AbstractSet<Map.Entry<String, Object>>() {
                @SuppressWarnings("NullableProblems")
                @Override
                public Iterator<Map.Entry<String, Object>> iterator() {
                    return new Iterator<Map.Entry<String, Object>>() {
                        private final Iterator<Map.Entry<String, NodeRegistry.FieldInfo>> fieldIterator =
                                pi.readableFields.entrySet().iterator();
                        private final Iterator<Map.Entry<String, Object>> dynamicIterator =
                                dynamicMap.entrySet().iterator();

                        @Override
                        public boolean hasNext() {
                            if (fieldIterator.hasNext()) return true;
                            return dynamicIterator.hasNext();
                        }

                        @Override
                        public Map.Entry<String, Object> next() {
                            if (fieldIterator.hasNext()) {
                                Map.Entry<String, NodeRegistry.FieldInfo> entry = fieldIterator.next();
                                Object value = entry.getValue().invokeGetter(JsonObject.this);
                                return new AbstractMap.SimpleEntry<>(entry.getKey(), value);
                            }
                            return dynamicIterator.next();
                        }
                    };
                }

                @Override
                public int size() {
                    return pi.readableFieldCount + dynamicMap.size();
                }
            };
        }
    }

    /**
     * Removes dynamic entries that match the predicate.
     */
    public boolean removeIf(Predicate<Map.Entry<String, Object>> filter) {
        if (dynamicMap != null) {
            return dynamicMap.entrySet().removeIf(filter);
        }
        return false;
    }


    /// JSON Facade

    /**
     * Parses a JSON string into a JsonObject.
     */
    public static JsonObject fromJson(String input) {
        return Sjf4j.global().fromJson(input, JsonObject.class);
    }


    ///  YAML Facade

    /**
     * Parses a YAML string into a JsonObject.
     */
    public static JsonObject fromYaml(String input) {
        return Sjf4j.global().fromYaml(input, JsonObject.class);
    }


    /// Properties Facade

    /**
     * Converts Java Properties into a JsonObject.
     */
    public static JsonObject fromProperties(Properties props) {
        return Sjf4j.global().fromProperties(props, JsonObject.class);
    }

    /// Node Facade

    /**
     * Converts a node into a JsonObject.
     */
    public static JsonObject fromNode(Object node) {
        return Sjf4j.global().fromNode(node, JsonObject.class);
    }


    /// Getter

    /**
     * Returns the node for the given key or {@code null}.
     * <p>
     * Only readable declared fields participate in this value view. When a
     * declared field is not readable, lookup falls through to dynamic entries
     * with the same key.
     */
    public Object getNode(String key) {
        if (key == null) return null;
        if (pi != null) {
            NodeRegistry.FieldInfo fi = pi.readableFields.get(key);
            if (fi != null) {
                return fi.invokeGetter(this);
            }
        }
        if (dynamicMap != null) {
            return dynamicMap.get(key);
        }
        return null;
    }

    /**
     * Returns the node for the given key or the default value.
     */
    public Object getNode(String key, Object defaultValue) {
        Object value = getNode(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a String value using strict conversion.
     */
    public String getString(String key) {
        try {
            Object value = getNode(key);
            return Nodes.toString(value);
        } catch (Exception e) {
            throw new JsonException("get failed: String for key '" + key + "'", e);
        }
    }

    /**
     * Returns a String value or the default value when missing.
     */
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a String value using lenient conversion.
     */
    public String getAsString(String key) {
        Object value = getNode(key);
        return Nodes.asString(value);
    }

    /**
     * Returns a Number value using strict conversion.
     */
    public Number getNumber(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toNumber(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Number for key '" + key + "'", e);
        }
    }
    
    /**
     * Returns a Number value or the default value when missing.
     */
    public Number getNumber(String key, Number defaultValue) {
        Number value = getNumber(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Number value using lenient conversion.
     */
    public Number getAsNumber(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asNumber(value);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to Number", e);
        }
    }

    /**
     * Returns a Long value using strict conversion.
     */
    public Long getLong(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toLong(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Long for key '" + key + "'", e);
        }
    }
    
    /**
     * Returns a Long value or the default value when missing.
     */
    public long getLong(String key, long defaultValue) {
        Long value = getLong(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Long value using lenient conversion.
     */
    public Long getAsLong(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asLong(value);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to Long", e);
        }
    }

    /**
     * Returns an Integer value using strict conversion.
     */
    public Integer getInt(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toInt(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Integer for key '" + key + "'", e);
        }
    }
    
    /**
     * Returns an Integer value or the default value when missing.
     */
    public int getInt(String key, int defaultValue) {
        Integer value = getInt(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns an Integer value using lenient conversion.
     */
    public Integer getAsInt(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asInt(value);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to Integer", e);
        }
    }

    /**
     * Returns a Short value using strict conversion.
     */
    public Short getShort(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toShort(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Short for key '" + key + "'", e);
        }
    }
    
    /**
     * Returns a Short value or the default value when missing.
     */
    public short getShort(String key, short defaultValue) {
        Short value = getShort(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Short value using lenient conversion.
     */
    public Short getAsShort(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asShort(value);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to Short", e);
        }
    }

    /**
     * Returns a Byte value using strict conversion.
     */
    public Byte getByte(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toByte(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Byte for key '" + key + "'", e);
        }
    }
    
    /**
     * Returns a Byte value or the default value when missing.
     */
    public byte getByte(String key, byte defaultValue) {
        Byte value = getByte(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Byte value using lenient conversion.
     */
    public Byte getAsByte(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asByte(value);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to Byte", e);
        }
    }

    /**
     * Returns a Double value using strict conversion.
     */
    public Double getDouble(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toDouble(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Double for key '" + key + "'", e);
        }
    }
    
    /**
     * Returns a Double value or the default value when missing.
     */
    public double getDouble(String key, double defaultValue) {
        Double value = getDouble(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Double value using lenient conversion.
     */
    public Double getAsDouble(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asDouble(value);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to Double", e);
        }
    }

    /**
     * Returns a Float value using strict conversion.
     */
    public Float getFloat(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toFloat(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Float for key '" + key + "'", e);
        }
    }
    
    /**
     * Returns a Float value or the default value when missing.
     */
    public float getFloat(String key, float defaultValue) {
        Float value = getFloat(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Float value using lenient conversion.
     */
    public Float getAsFloat(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asFloat(value);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to Float", e);
        }
    }

    /**
     * Returns a BigInteger value using strict conversion.
     */
    public BigInteger getBigInteger(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("get failed: BigInteger for key '" + key + "'", e);
        }
    }
    
    /**
     * Returns a BigInteger value or the default value when missing.
     */
    public BigInteger getBigInteger(String key, BigInteger defaultValue) {
        BigInteger value = getBigInteger(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a BigInteger value using lenient conversion.
     */
    public BigInteger getAsBigInteger(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to BigInteger", e);
        }
    }

    /**
     * Returns a BigDecimal value using strict conversion.
     */
    public BigDecimal getBigDecimal(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("get failed: BigDecimal for key '" + key + "'", e);
        }
    }
    
    /**
     * Returns a BigDecimal value or the default value when missing.
     */
    public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a BigDecimal value using lenient conversion.
     */
    public BigDecimal getAsBigDecimal(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to BigDecimal", e);
        }
    }

    /**
     * Returns a Boolean value using strict conversion.
     */
    public Boolean getBoolean(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toBoolean(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Boolean for key '" + key + "'", e);
        }
    }
    
    /**
     * Returns a Boolean value or the default value when missing.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Boolean value = getBoolean(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns a Boolean value using lenient conversion.
     */
    public Boolean getAsBoolean(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asBoolean(value);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to Boolean", e);
        }
    }

    /**
     * Returns a JsonObject value using strict conversion.
     */
    public JsonObject getJsonObject(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("get failed: JsonObject for key '" + key + "'", e);
        }
    }

    /**
     * Returns a Map value using strict conversion.
     */
    public Map<String, Object> getMap(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toMap(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Map<String,Object> for key '" + key + "'", e);
        }
    }

    /**
     * Returns a typed Map value using strict conversion.
     */
    public <T> Map<String, T> getMap(String key, Class<T> clazz) {
        Object value = getNode(key);
        try {
            return Nodes.toMap(value, clazz);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to Map<String," + clazz.getName() + ">", e);
        }
    }

    /**
     * Returns a JsonArray value using strict conversion.
     */
    public JsonArray getJsonArray(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("get failed: JsonArray for key '" + key + "'", e);
        }
    }

    /**
     * Returns a List value using strict conversion.
     */
    public List<Object> getList(String key) {
        try {
            Object value = getNode(key);
            return Nodes.toList(value);
        } catch (Exception e) {
            throw new JsonException("get failed: List<Object> for key '" + key + "'", e);
        }
    }

    /**
     * Returns a typed List value using strict conversion.
     */
    public <T> List<T> getList(String key, Class<T> clazz) {
        try {
            Object value = getNode(key);
            return Nodes.toList(value, clazz);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to List<" + clazz.getName() + ">", e);
        }
    }

    /**
     * Returns an Object array using strict conversion.
     */
    public Object[] getArray(String key) {
        try {
            Object value = getNode(key);
            return Nodes.toArray(value);
        } catch (Exception e) {
            throw new JsonException("get failed: Object[] for key '" + key + "'", e);
        }
    }

    /**
     * Returns a typed array using strict conversion.
     */
    public <T> T[] getArray(String key, Class<T> clazz) {
        try {
            Object value = getNode(key);
            return Nodes.toArray(value, clazz);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to " + clazz.getName() + "[]", e);
        }
    }

    /**
     * Returns a value converted to the given type.
     */
    public <T> T get(String key, Class<T> clazz) {
        Object value = getNode(key);
        try {
            return Nodes.to(value, clazz);
        } catch (Exception e) {
            throw new JsonException("get failed: " + clazz.getName() + " for key '" + key + "'", e);
        }
    }
    
    /**
     * Returns a value converted to the inferred type.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T... reified) {
        if (reified.length > 0) throw new JsonException("reified varargs must be empty");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(key, clazz);
    }

    /**
     * Returns a value using lenient conversion.
     */
    public <T> T getAs(String key, Class<T> clazz) {
        Object value = getNode(key);
        try {
            return Nodes.as(value, clazz);
        } catch (Exception e) {
            throw new JsonException("as failed: key '" + key + "' to " + clazz.getName(), e);
        }
    }
    
    /**
     * Returns a value using lenient conversion with inferred type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAs(String key, T... reified) {
        if (reified.length > 0) throw new JsonException("reified varargs must be empty");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return getAs(key, clazz);
    }


    /// Putter

    /**
     * Puts a key/value pair and returns the previous value.
     * <p>
     * When key matches a declared field, the field setter is used; otherwise the
     * value is stored in dynamic map.
     */
    public Object put(String key, Object object) {
        Objects.requireNonNull(key, "key");
        if (pi != null) {
            NodeRegistry.FieldInfo fi = pi.fields.get(key);
            if (fi != null) {
                Object old = fi.hasGetter() ? fi.invokeGetter(this) : null;
                fi.invokeSetter(this, object);
                return old;
            }
        }
        if (dynamicMap == null) dynamicMap = new LinkedHashMap<>();
        return dynamicMap.put(key, object);
    }

    /**
     * Puts a value only when it is non-null.
     */
    public Object putNonNull(String key, Object node) {
        if (node != null) {
            return put(key, node);
        } else {
            return getNode(key);
        }
    }

    /**
     * Puts a value only when the current value is null.
     */
    public Object putIfAbsent(String key, Object node) {
        Object old = getNode(key);
        if (old == null) {
            old = put(key, node);
        }
        return old;
    }

    /**
     * Replaces the value only if the key already exists.
     */
    public Object replace(String key, Object value) {
        Object old = getNode(key);
        if (old != null) {
            return put(key, value);
        }
        return null;
    }

    // Try generic
    /**
     * Computes and stores a value when the current value is null.
     */
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(String key, Function<String, T> computer) {
        Objects.requireNonNull(computer, "computer");
        T old = get(key);
        if (old == null) {
            T newNode = computer.apply(key);
            if (newNode != null) {
                put(key, newNode);
                return newNode;
            }
        }
        return old;
    }

    /**
     * Puts all entries from the provided map.
     */
    public void putAll(Map<String, Object> map) {
        if (map == null) return;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Puts all entries from another JsonObject.
     */
    public void putAll(JsonObject jo) {
        if (jo == null) return;
        jo.forEach(this::put);
    }

    /**
     * Removes a dynamic key and returns its previous value.
     * <p>
     * Declared JOJO/POJO fields are not removable.
     */
    public Object remove(String key) {
        Objects.requireNonNull(key, "key");
        if (pi != null && pi.fields.containsKey(key)) {
            throw new JsonException("Cannot remove key '" + key + "' from JOJO '" + getClass().getName() +
                    "'. Only dynamic properties in JsonObject are removable.");
        }
        if (dynamicMap != null) {
            return dynamicMap.remove(key);
        }
        return null;
    }

    /**
     * Clears dynamic entries only, keeping declared field values intact.
     */
    public void clear() {
        if (dynamicMap != null) {
            dynamicMap.clear();
        }
    }

    /**
     * Drops the dynamic map reference and keeps only declared field values.
     */
    public void prune() {
        dynamicMap = null;
    }


    /// Copy, merge

    /**
     * Creates a shallow copy of this JsonObject.
     */
    @SuppressWarnings("unchecked")
    public <T extends JsonObject>  T copy() {
        return (T) Nodes.copy(this);
    }

    /**
     * Creates a deep copy of this JsonObject.
     */
    @SuppressWarnings("unchecked")
    public <T extends JsonObject>  T deepCopy() {
        return (T) Sjf4j.global().deepNode(this);
    }

    /// Stream

    /**
     * Returns a NodeStream starting from this object.
     */
    public NodeStream<JsonObject> stream() {
        return NodeStream.of(this);
    }


    /// builder

    /**
     * Returns a builder initialized from this object.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Returns a builder for a new JsonObject.
     */
    public static Builder builder() {
        return new Builder(new JsonObject());
    }

    /**
     * Fluent builder for JsonObject updates.
     */
    public static class Builder {
        private final JsonObject jo;
        /**
         * Creates a builder for the given target object.
         */
        public Builder(JsonObject jo) {
            this.jo = jo;
        }
        /**
         * Puts key/value into target object.
         */
        public Builder put(String key, Object value) {
            jo.put(key, value);
            return this;
        }
        /**
         * Puts key/value only when value is non-null.
         */
        public Builder putNonNull(String key, Object value) {
            jo.putNonNull(key, value);
            return this;
        }
        /**
         * Puts key/value only when current value is absent.
         */
        public Builder putIfAbsent(String key, Object value) {
            jo.putIfAbsent(key, value);
            return this;
        }
        /**
         * Ensures path containers and puts value by path.
         */
        public Builder putByPath(String path, Object value) {
            jo.putByPath(path, value);
            return this;
        }

        public Builder ensurePutIfAbsentByPath(String path, Object value) {
            jo.ensurePutIfAbsentByPath(path, value);
            return this;
        }

        public Builder ensurePutByPath(String path, Object value) {
            jo.ensurePutByPath(path, value);
            return this;
        }


        /**
         * Returns the built JsonObject.
         */
        public JsonObject build() {
            return jo;
        }
    }


}
