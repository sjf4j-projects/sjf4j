package org.sjf4j;

import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.NodeException;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.FieldInfo;
import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;

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
import java.util.function.Function;
import java.util.function.BiPredicate;
import java.util.function.Predicate;


/**
 * Mutable object node in SJF4J's OBNT model.
 *
 * <p>{@link JsonObject} can be subclassed to define a JOJO, a Java
 * representation category that combines declared properties and dynamic entries
 * in one object node.
 *
 * <p>Dynamic entries are stored in {@code dynamicProperties}; declared properties
 * are mapped via {@link FieldInfo}. Accessors use {@link Nodes} strict and
 * lenient conversion semantics.
 */
public class JsonObject extends JsonContainer {

    /**
     * Stores dynamic object node entries.
     */
    protected transient Map<String, Object> dynamicProperties;
    
    /**
     * Stores property metadata for POJO mapping.
     */
    protected final transient PojoInfo pi;

    /**
     * Creates an empty object node.
     */
    public JsonObject() {
        super();
        this.pi = this.getClass() == JsonObject.class
                ? null
                : TypeRegistry.registerPojoOrElseThrow(this.getClass());
    }

    /**
     * Creates a JOJO with precomputed property metadata.
     */
    protected JsonObject(PojoInfo pi) {
        super();
        this.pi = pi;
    }

    /**
     * Creates an object node backed directly by {@code map}.
     * <p>
     * The provided map becomes this object's dynamic storage directly; dynamic
     * reads and writes are therefore shared with the same map instance. Declared
     * JOJO properties, when present, still live on the object instance itself.
     */
    public JsonObject(Map<String, Object> map) {
        this();
        this.dynamicProperties = map;
    }

    /**
     * Creates a JsonObject from alternating key-value pairs.
     */
    public static JsonObject of(Object... keyValues) {
        JsonObject jo = new JsonObject();
        if (keyValues == null || keyValues.length == 0) return jo;
        if ((keyValues.length & 1) != 0) {
            throw new NodeException("JsonObject.of requires an even number of arguments");
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (!(key instanceof String)) {
                throw new NodeException("JsonObject.of key at index " + i + " must be a String");
            }
            jo.put((String) key, keyValues[i + 1]);
        }
        return jo;
    }

    /**
     * Returns the current dynamic properties as an unmodifiable map.
     */
    public Map<String, Object> dynamicProperties() {
        return dynamicProperties == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(dynamicProperties);
    }

    /*
     * --------------------------------------------------------------
     * Map
     * --------------------------------------------------------------
     */

    /**
     * Computes hash code from readable declared properties and dynamic entries.
     */
    @Override
    public int hashCode() {
        int hash = dynamicProperties == null ? 0 : dynamicProperties.hashCode();
        if (pi != null) {
            for (Map.Entry<String, FieldInfo> entry : pi.readableProperties.entrySet()){
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
     * Returns the number of readable declared properties and dynamic entries.
     */
    public int size() {
        return (pi == null ? 0 : pi.readablePropertyCount) + (dynamicProperties == null ? 0 : dynamicProperties.size());
    }

    /**
     * Returns true if this object has no entries.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns true if the key exists in readable declared properties or dynamic entries.
     */
    public boolean containsKey(String key) {
        if (key == null) return false;
        return (pi != null && pi.readableProperties.containsKey(key))
                || (dynamicProperties != null && dynamicProperties.containsKey(key));
    }

    /**
     * Returns true if the key exists and the value is non-null.
     */
    public boolean hasNonNull(String key) {
        if (key == null) return false;
        return getNode(key) != null;
    }

    /**
     * Returns a merged key set of readable declared properties and dynamic entries.
     */
    public Set<String> keySet() {
        if (pi == null) {
            return dynamicProperties == null ? Collections.emptySet() : dynamicProperties.keySet();
        } else if (dynamicProperties == null) {
            return pi.readableProperties.keySet();
        } else {
            return new AbstractSet<String>() {
                @SuppressWarnings("NullableProblems")
                @Override
                public Iterator<String> iterator() {
                    return new Iterator<String>() {
                        private final Iterator<String> propertyIterator = pi.readableProperties.keySet().iterator();
                        private final Iterator<String> dynamicIterator = dynamicProperties.keySet().iterator();

                        @Override
                        public boolean hasNext() {
                            if (propertyIterator.hasNext()) return true;
                            return dynamicIterator.hasNext();
                        }

                        @Override
                        public String next() {
                            if (propertyIterator.hasNext()) return propertyIterator.next();
                            return dynamicIterator.next();
                        }
                    };
                }

                @Override
                public int size() {
                    return pi.readablePropertyCount + dynamicProperties.size();
                }
            };
        }
    }

    /**
     * Returns a merged entry set of readable declared properties and dynamic entries.
     */
    public Set<Map.Entry<String, Object>> entrySet() {
        if (pi == null) {
            return dynamicProperties == null ? Collections.emptySet() : dynamicProperties.entrySet();
        } else if (dynamicProperties == null) {
            return new AbstractSet<Map.Entry<String, Object>>() {
                @SuppressWarnings("NullableProblems")
                @Override
                public Iterator<Map.Entry<String, Object>> iterator() {
                    final Iterator<Map.Entry<String, FieldInfo>> propertyIterator =
                            pi.readableProperties.entrySet().iterator();
                    return new Iterator<Map.Entry<String, Object>>() {
                        @Override
                        public boolean hasNext() {
                            return propertyIterator.hasNext();
                        }

                        @Override
                        public Map.Entry<String, Object> next() {
                            Map.Entry<String, FieldInfo> entry = propertyIterator.next();
                            Object value = entry.getValue().invokeGetter(JsonObject.this);
                            return new AbstractMap.SimpleEntry<>(entry.getKey(), value);
                        }
                    };
                }

                @Override
                public int size() {
                    return pi.readablePropertyCount;
                }
            };
        } else {
            return new AbstractSet<Map.Entry<String, Object>>() {
                @SuppressWarnings("NullableProblems")
                @Override
                public Iterator<Map.Entry<String, Object>> iterator() {
                    return new Iterator<Map.Entry<String, Object>>() {
                        private final Iterator<Map.Entry<String, FieldInfo>> propertyIterator =
                                pi.readableProperties.entrySet().iterator();
                        private final Iterator<Map.Entry<String, Object>> dynamicIterator =
                                dynamicProperties.entrySet().iterator();

                        @Override
                        public boolean hasNext() {
                            if (propertyIterator.hasNext()) return true;
                            return dynamicIterator.hasNext();
                        }

                        @Override
                        public Map.Entry<String, Object> next() {
                            if (propertyIterator.hasNext()) {
                                Map.Entry<String, FieldInfo> entry = propertyIterator.next();
                                Object value = entry.getValue().invokeGetter(JsonObject.this);
                                return new AbstractMap.SimpleEntry<>(entry.getKey(), value);
                            }
                            return dynamicIterator.next();
                        }
                    };
                }

                @Override
                public int size() {
                    return pi.readablePropertyCount + dynamicProperties.size();
                }
            };
        }
    }

    /**
     * Performs the given visitor for each readable entry.
     */
    public void forEach(BiConsumer<String, Object> visitor) {
        Asserts.notNull(visitor, "visitor");
        if (pi != null) {
            for (Map.Entry<String, FieldInfo> entry : pi.readableProperties.entrySet()){
                visitor.accept(entry.getKey(), entry.getValue().invokeGetter(this));
            }
        }
        if (dynamicProperties != null) {
            for (Map.Entry<String, Object> entry : dynamicProperties.entrySet()){
                visitor.accept(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Returns true if any readable entry matches the predicate.
     */
    public boolean anyMatch(BiPredicate<String, Object> predicate) {
        Asserts.notNull(predicate, "predicate");
        if (pi != null) {
            for (Map.Entry<String, FieldInfo> entry : pi.readableProperties.entrySet()){
                if (predicate.test(entry.getKey(), entry.getValue().invokeGetter(this))) {
                    return true;
                }
            }
        }
        if (dynamicProperties != null) {
            for (Map.Entry<String, Object> entry : dynamicProperties.entrySet()){
                if (predicate.test(entry.getKey(), entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Replaces values for readable-and-writable declared properties and dynamic
     * entries in place.
     */
    public boolean replaceAll(BiFunction<String, Object, Object> mapper) {
        Asserts.notNull(mapper, "mapper");
        boolean changed = false;
        if (pi != null) {
            for (Map.Entry<String, FieldInfo> entry : pi.readableProperties.entrySet()){
                FieldInfo fi = entry.getValue();
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
        if (dynamicProperties != null) {
            for (Map.Entry<String, Object> entry : dynamicProperties.entrySet()){
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
     * Returns a new map containing readable declared properties and dynamic entries.
     * Values are not copied.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (pi != null) {
            for (Map.Entry<String, FieldInfo> entry : pi.readableProperties.entrySet()){
                merged.put(entry.getKey(), entry.getValue().invokeGetter(this));
            }
        }
        if (dynamicProperties != null) {
            merged.putAll(dynamicProperties);
        }
        return merged;
    }

    /**
     * Converts this object to a typed Map.
     */
    public <T> Map<String, T> toMap(Class<T> clazz) {
        return Nodes.toMap(this, clazz);
    }

    /*
     * --------------------------------------------------------------
     * JSON Facade
     * --------------------------------------------------------------
     */

    /**
     * Parses a JSON string into a JsonObject.
     */
    public static JsonObject fromJson(String input) {
        return Sjf4j.global().fromJson(input, JsonObject.class);
    }


    /*
     * --------------------------------------------------------------
     * YAML Facade
     * --------------------------------------------------------------
     */

    /**
     * Parses a YAML string into a JsonObject.
     */
    public static JsonObject fromYaml(String input) {
        return Sjf4j.global().fromYaml(input, JsonObject.class);
    }


    /*
     * --------------------------------------------------------------
     * Properties Facade
     * --------------------------------------------------------------
     */

    /**
     * Converts Java Properties into a JsonObject.
     */
    public static JsonObject fromProperties(Properties props) {
        return Sjf4j.global().fromProperties(props, JsonObject.class);
    }

    /*
     * --------------------------------------------------------------
     * Node Facade
     * --------------------------------------------------------------
     */

    /**
     * Converts an OBNT value to a JsonObject through {@link Sjf4j#fromNode(Object, Class)}.
     * The configured node facade defines conversion and copy boundaries; returned
     * values may retain references.
     */
    public static JsonObject fromNode(Object node) {
        return Sjf4j.global().fromNode(node, JsonObject.class);
    }


    /*
     * --------------------------------------------------------------
     * Getter
     * --------------------------------------------------------------
     */

    /**
     * Strict getter helper. When {@code containerType} is non-null the message
     * includes the container name; for non-container value types pass {@code null}.
     */
    private NodeException _strict(String key, Class<?> type, Class<?> containerType, Exception cause) {
        String message;
        if (containerType == null) {
            message = "cannot get " + type.getName() + " at '" + key + "'";
        } else {
            message = "cannot get " + containerType.getName() + " with element type " +
                    type.getName() + " at '" + key + "'";
        }
        if (cause instanceof BindingException) {
            BindingException binding = (BindingException) cause;
            if (binding.hasPathSegment()) return binding;
            return new BindingException(message + ": " + binding.getMessage(),
                    new PathSegment.Name(PathSegment.Root.INSTANCE, key), binding);
        }
        return new NodeException(message, cause);
    }

    /**
     * Lenient getter helper with location context.
     */
    private NodeException _lenient(String key, Class<?> type, Exception cause) {
        String message = "cannot coerce to " + type.getName() + " at '" + key + "'";
        if (cause instanceof BindingException) {
            BindingException binding = (BindingException) cause;
            if (binding.hasPathSegment()) return binding;
            return new BindingException(message + ": " + binding.getMessage(),
                    new PathSegment.Name(PathSegment.Root.INSTANCE, key), binding);
        }
        return new NodeException(message, cause);
    }

    /**
     * Returns the OBNT value for the given key or {@code null}.
     * <p>
     * Only readable declared properties participate in this value view. When a
     * declared property is not readable, lookup falls through to dynamic entries
     * with the same key.
     */
    public Object getNode(String key) {
        if (key == null) return null;
        if (pi != null) {
            FieldInfo fi = pi.readableProperties.get(key);
            if (fi != null) {
                return fi.invokeGetter(this);
            }
        }
        if (dynamicProperties != null) {
            return dynamicProperties.get(key);
        }
        return null;
    }

    /**
     * Returns the OBNT value for the given key or the default value.
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
            return Nodes.toString(getNode(key));
        } catch (Exception e) {
            throw _strict(key, String.class, null, e);
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
        try {
            return Nodes.asString(getNode(key));
        } catch (BindingException e) {
            throw _lenient(key, String.class, e);
        }
    }

    /**
     * Returns a Number value using strict conversion.
     */
    public Number getNumber(String key) {
        try {
            return Nodes.toNumber(getNode(key));
        } catch (Exception e) {
            throw _strict(key, Number.class, null, e);
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
        try {
            return Nodes.asNumber(getNode(key));
        } catch (Exception e) {
            throw _lenient(key, Number.class, e);
        }
    }

    /**
     * Returns a Long value using strict conversion.
     */
    public Long getLong(String key) {
        try {
            return Nodes.toLong(getNode(key));
        } catch (Exception e) {
            throw _strict(key, Long.class, null, e);
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
        try {
            return Nodes.asLong(getNode(key));
        } catch (Exception e) {
            throw _lenient(key, Long.class, e);
        }
    }

    /**
     * Returns an Integer value using strict conversion.
     */
    public Integer getInt(String key) {
        try {
            return Nodes.toInt(getNode(key));
        } catch (Exception e) {
            throw _strict(key, Integer.class, null, e);
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
        try {
            return Nodes.asInt(getNode(key));
        } catch (Exception e) {
            throw _lenient(key, Integer.class, e);
        }
    }

    /**
     * Returns a Short value using strict conversion.
     */
    public Short getShort(String key) {
        try {
            return Nodes.toShort(getNode(key));
        } catch (Exception e) {
            throw _strict(key, Short.class, null, e);
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
        try {
            return Nodes.asShort(getNode(key));
        } catch (Exception e) {
            throw _lenient(key, Short.class, e);
        }
    }

    /**
     * Returns a Byte value using strict conversion.
     */
    public Byte getByte(String key) {
        try {
            return Nodes.toByte(getNode(key));
        } catch (Exception e) {
            throw _strict(key, Byte.class, null, e);
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
        try {
            return Nodes.asByte(getNode(key));
        } catch (Exception e) {
            throw _lenient(key, Byte.class, e);
        }
    }

    /**
     * Returns a Double value using strict conversion.
     */
    public Double getDouble(String key) {
        try {
            return Nodes.toDouble(getNode(key));
        } catch (Exception e) {
            throw _strict(key, Double.class, null, e);
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
        try {
            return Nodes.asDouble(getNode(key));
        } catch (Exception e) {
            throw _lenient(key, Double.class, e);
        }
    }

    /**
     * Returns a Float value using strict conversion.
     */
    public Float getFloat(String key) {
        try {
            return Nodes.toFloat(getNode(key));
        } catch (Exception e) {
            throw _strict(key, Float.class, null, e);
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
        try {
            return Nodes.asFloat(getNode(key));
        } catch (Exception e) {
            throw _lenient(key, Float.class, e);
        }
    }

    /**
     * Returns a BigInteger value using strict conversion.
     */
    public BigInteger getBigInteger(String key) {
        try {
            return Nodes.toBigInteger(getNode(key));
        } catch (Exception e) {
            throw _strict(key, BigInteger.class, null, e);
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
        try {
            return Nodes.asBigInteger(getNode(key));
        } catch (Exception e) {
            throw _lenient(key, BigInteger.class, e);
        }
    }

    /**
     * Returns a BigDecimal value using strict conversion.
     */
    public BigDecimal getBigDecimal(String key) {
        try {
            return Nodes.toBigDecimal(getNode(key));
        } catch (Exception e) {
            throw _strict(key, BigDecimal.class, null, e);
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
        try {
            return Nodes.asBigDecimal(getNode(key));
        } catch (Exception e) {
            throw _lenient(key, BigDecimal.class, e);
        }
    }

    /**
     * Returns a Boolean value using strict conversion.
     */
    public Boolean getBoolean(String key) {
        try {
            return Nodes.toBoolean(getNode(key));
        } catch (Exception e) {
            throw _strict(key, Boolean.class, null, e);
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
        try {
            return Nodes.asBoolean(getNode(key));
        } catch (Exception e) {
            throw _lenient(key, Boolean.class, e);
        }
    }

    /**
     * Returns a JsonObject value using strict conversion.
     */
    public JsonObject getJsonObject(String key) {
        try {
            return Nodes.toJsonObject(getNode(key));
        } catch (Exception e) {
            throw _strict(key, JsonObject.class, null, e);
        }
    }

    /**
     * Returns a Map value using strict conversion.
     */
    public Map<String, Object> getMap(String key) {
        try {
            return Nodes.toMap(getNode(key));
        } catch (Exception e) {
            throw _strict(key, Map.class, null, e);
        }
    }

    /**
     * Returns a typed Map value using strict conversion.
     */
    public <T> Map<String, T> getMap(String key, Class<T> clazz) {
        try {
            return Nodes.toMap(getNode(key), clazz);
        } catch (Exception e) {
            throw _strict(key, clazz, Map.class, e);
        }
    }

    /**
     * Returns a JsonArray value using strict conversion.
     */
    public JsonArray getJsonArray(String key) {
        try {
            return Nodes.toJsonArray(getNode(key));
        } catch (Exception e) {
            throw _strict(key, JsonArray.class, null, e);
        }
    }

    /**
     * Returns a List value using strict conversion.
     */
    public List<Object> getList(String key) {
        try {
            return Nodes.toList(getNode(key));
        } catch (Exception e) {
            throw _strict(key, List.class, null, e);
        }
    }

    /**
     * Returns a typed List value using strict conversion.
     */
    public <T> List<T> getList(String key, Class<T> clazz) {
        try {
            return Nodes.toList(getNode(key), clazz);
        } catch (Exception e) {
            throw _strict(key, clazz, List.class, e);
        }
    }

    /**
     * Returns an Object array using strict conversion.
     */
    public Object[] getArray(String key) {
        try {
            return Nodes.toArray(getNode(key));
        } catch (Exception e) {
            throw _strict(key, Object[].class, null, e);
        }
    }

    /**
     * Returns a typed array using strict conversion.
     */
    public <T> T[] getArray(String key, Class<T> clazz) {
        try {
            return Nodes.toArray(getNode(key), clazz);
        } catch (Exception e) {
            throw _strict(key, clazz, Object[].class, e);
        }
    }

    /**
     * Returns a value converted to the given type.
     */
    public <T> T get(String key, Class<T> clazz) {
        try {
            return Nodes.to(getNode(key), clazz);
        } catch (Exception e) {
            throw _strict(key, clazz, null, e);
        }
    }
    
    /**
     * Returns a value converted to the inferred type.
     *
     * @throws IllegalArgumentException if {@code reified} is nonempty
     * @throws NullPointerException if {@code reified} is null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("reified varargs must be empty");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(key, clazz);
    }

    /**
     * Returns a value using lenient conversion.
     */
    public <T> T getAs(String key, Class<T> clazz) {
        try {
            return Nodes.as(getNode(key), clazz);
        } catch (Exception e) {
            throw _lenient(key, clazz, e);
        }
    }
    
    /**
     * Returns a value using lenient conversion with inferred type.
     *
     * @throws IllegalArgumentException if {@code reified} is nonempty
     * @throws NullPointerException if {@code reified} is null
     */
    @SuppressWarnings("unchecked")
    public <T> T getAs(String key, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("reified varargs must be empty");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return getAs(key, clazz);
    }


    /*
     * --------------------------------------------------------------
     * Putter
     * --------------------------------------------------------------
     */

    /**
     * Puts a key/value pair and returns the previous value when the target
     * storage exposes one.
     * <p>
     * When key matches a declared field, the field setter is used; otherwise the
     * value is stored in dynamic map. Declared-field writes do not read back the
     * old value and therefore return {@code null}.
     */
    public Object put(String key, Object object) {
        Asserts.notNull(key, "key");
        if (pi != null) {
            FieldInfo fi = pi.properties.get(key);
            if (fi != null) {
                fi.invokeSetter(this, object);
                return null;
            }
        }
        if (dynamicProperties == null) dynamicProperties = new LinkedHashMap<>();
        return dynamicProperties.put(key, object);
    }

    /**
     * Computes and stores a value when the current value is null.
     */
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(String key, Function<String, T> computer) {
        Asserts.notNull(key, "key");
        Asserts.notNull(computer, "computer");

        if (pi != null && pi.properties.containsKey(key)) {
            T old = (T) getNode(key);
            if (old != null) {
                return old;
            }
            T newNode = computer.apply(key);
            if (newNode != null) {
                put(key, newNode);
            }
            return newNode;
        }

        if (dynamicProperties == null) dynamicProperties = new LinkedHashMap<>();
        return (T) dynamicProperties.computeIfAbsent(key, computer);
    }

    /**
     * Copies all readable entries from the given object node representation.
     * <p>
     * Supported inputs follow {@link Nodes#forEachObject(Object, BiConsumer)}:
     * {@link Map}, {@link JsonObject}, JOJO/POJO, and facade-native Java
     * representations that participate as object nodes.
     * Values are transferred through {@link #put(String, Object)} without deep
     * recursion, so nested child values may still be shared with the source.
     */
    public void putAll(Object node) {
        if (node == null) return;
        Nodes.forEachObject(node, this::put);
    }

    /**
     * Removes a dynamic key and returns its previous value.
     * <p>
     * Declared JOJO/POJO properties are not removable.
     */
    public Object remove(String key) {
        Asserts.notNull(key, "key");
        if (pi != null && pi.properties.containsKey(key)) {
            throw new NodeException("cannot remove key '" + key + "' from JOJO '" + getClass().getName() +
                    "'. Only dynamic properties in JsonObject are removable.");
        }
        if (dynamicProperties != null) {
            return dynamicProperties.remove(key);
        }
        return null;
    }

    /**
     * Removes dynamic entries that match the predicate.
     */
    public boolean removeIf(Predicate<Map.Entry<String, Object>> filter) {
        Asserts.notNull(filter, "filter");
        if (dynamicProperties != null) {
            return dynamicProperties.entrySet().removeIf(filter);
        }
        return false;
    }

    /**
     * Clears dynamic entries only, keeping declared property values intact.
     */
    public void clear() {
        if (dynamicProperties != null) {
            dynamicProperties.clear();
        }
    }

    /**
     * Drops the dynamic map reference and keeps only declared property values.
     */
    public void prune() {
        dynamicProperties = null;
    }


    /*
     * --------------------------------------------------------------
     * Copy and Merge
     * --------------------------------------------------------------
     */

    /**
     * Creates a shallow copy of this JsonObject.
     * <p>
     * Plain {@link JsonObject} instances copy their dynamic entries; nested values
     * are shared.
     * JOJO subtypes fall back to {@link Nodes#copy(Object)} so subtype properties
     * remain part of the copied object view.
     */
    public JsonObject copy() {
        if (getClass() == JsonObject.class) {
            return dynamicProperties == null ? new JsonObject() : new JsonObject(new LinkedHashMap<>(dynamicProperties));
        }
        return Nodes.copy(this);
    }

    /**
     * Creates a deep copy of this JsonObject.
     * <p>
     * Delegates to {@link Sjf4j#deepNode(Object)}. Declared and dynamic entries
     * are traversed according to the configured node facade.
     */
    public JsonObject deepCopy() {
        return Sjf4j.global().deepNode(this);
    }

    /*
     * --------------------------------------------------------------
     * Stream
     * --------------------------------------------------------------
     */

    /**
     * Returns a NodeStream starting from this object.
     */
    public NodeStream<JsonObject> stream() {
        return NodeStream.of(this);
    }


    /*
     * --------------------------------------------------------------
     * Builder
     * --------------------------------------------------------------
     */

    /**
     * @deprecated Use {@link #edit()} instead.
     */
    @Deprecated
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Returns a builder initialized from this object.
     * <p>
     * The builder mutates this object in place; it does not create a detached
     * copy.
     */
    public Builder edit() {
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
         * Writes a value at the given path.
         */
        public Builder putByPath(String path, Object value) {
            jo.putByPath(path, value);
            return this;
        }

        /**
         * Writes a value only when the parent container of the final path
         * segment already exists.
         */
        public Builder putIfParentPresentByPath(String path, Object value) {
            jo.putIfParentPresentByPath(path, value);
            return this;
        }

        /**
         * Ensures the final path location exists and writes only when absent.
         */
        public Builder ensurePutIfAbsentByPath(String path, Object value) {
            jo.ensurePutIfAbsentByPath(path, value);
            return this;
        }

        /**
         * Ensures intermediate path containers exist, then writes the value.
         */
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
