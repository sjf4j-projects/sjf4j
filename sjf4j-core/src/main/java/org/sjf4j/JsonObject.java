package org.sjf4j;

import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.NodeException;
import org.sjf4j.node.NodeMapper;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.meta.PojoInfo;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.node.NodeStream;
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
 * representation category whose members combine declared properties and dynamic members
 * in one object node.
 *
 * <p>The outer map passed to the constructor is copied. Dynamic members can
 * also be modified through the live {@link #keySet()} and {@link #entrySet()}
 * views; JOJO declared-property entries may be snapshots and are not removable.
 * Nested values remain shared. These Map-like views intentionally expose
 * mutations of the node's own storage.
 *
 * <p>Dynamic members are stored in {@code dynamicMembers}; declared properties
 * are mapped via {@link PropertyInfo}. Accessors use {@link Nodes} strict and
 * lenient conversion semantics. A missing key and a key whose value is
 * {@code null} both return {@code null} from value getters; overloads with a
 * default value use that default in either case. A strict getter rejects an
 * incompatible value rather than returning the default.
 */
public class JsonObject extends JsonContainer {

    /**
     * Stores dynamic object node members.
     */
    transient LinkedHashMap<String, Object> dynamicMembers;
    
    /**
     * Stores object-binding metadata for declared JOJO properties.
     */
    private final transient PojoInfo pi;

    /**
     * Creates an empty object node.
     */
    public JsonObject() {
        super();
        this.pi = this.getClass() == JsonObject.class
                ? null
                : TypeRegistry.requireRegisteredPojoInfo(this.getClass());
    }

    /**
     * Creates a JOJO with precomputed object-binding metadata.
     */
    protected JsonObject(PojoInfo pi) {
        super();
        this.pi = pi;
    }

    /**
     * Creates an object node by copying the outer entries of {@code map} into
     * a new insertion-ordered map. Later changes to either outer container do
     * not affect the other; nested values retain their original references.
     * A {@code null} map is not accepted. Entries are copied without additional
     * key validation for performance; callers must supply non-null String keys
     * to maintain JSON object semantics.
     */
    public JsonObject(Map<String, Object> map) {
        this();
        Asserts.notNull(map, "map");
        this.dynamicMembers = new LinkedHashMap<>(map);
    }

    /**
     * Creates a JsonObject from alternating string keys and values.
     * A {@code null} or empty argument array creates an empty object.
     *
     * @throws IllegalArgumentException if the number of arguments is odd or a key is not a String
     */
    public static JsonObject of(Object... keyValues) {
        JsonObject jo = new JsonObject();
        if (keyValues == null || keyValues.length == 0) return jo;
        if ((keyValues.length & 1) != 0) {
            throw new IllegalArgumentException("JsonObject.of requires an even number of arguments");
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("JsonObject.of key at index " + i + " must be a String");
            }
            jo.put((String) key, keyValues[i + 1]);
        }
        return jo;
    }

    /**
     * Returns an unmodifiable view of the dynamic members, excluding declared
     * JOJO properties. The view reflects subsequent writes to the same backing map;
     * if no dynamic map exists yet, an empty map is returned.
     */
    public Map<String, Object> dynamicMembers() {
        return dynamicMembers == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(dynamicMembers);
    }

    /*
     * --------------------------------------------------------------
     * Map
     * --------------------------------------------------------------
     */

    /**
     * Computes hash code from gettable declared properties and dynamic members.
     */
    @Override
    public int hashCode() {
        int hash = dynamicMembers == null ? 0 : dynamicMembers.hashCode();
        if (pi != null) {
            for (PropertyInfo propertyInfo : pi.gettableProperties){
                hash += Objects.hashCode(propertyInfo.name) ^
                        Objects.hashCode(propertyInfo.invokeGetter(this));
            }
        }
        return hash;
    }

    /**
     * Compares JsonObject values within the same concrete runtime type.
     * <p>
     * Equality uses the gettable object view while preserving same-concrete-type
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
     * Returns the number of gettable declared properties and dynamic members.
     */
    public int size() {
        return (pi == null ? 0 : pi.gettableProperties.length) +
                (dynamicMembers == null ? 0 : dynamicMembers.size());
    }

    /**
     * Returns true if this object has no entries.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns true if the key exists in gettable declared properties or dynamic members.
     */
    public boolean containsKey(String key) {
        if (key == null) return false;
        if (pi != null) {
            PropertyInfo propertyInfo = pi.getPropertyNoAlias(key);
            if (propertyInfo != null && propertyInfo.gettable) return true;
        }
        return dynamicMembers != null && dynamicMembers.containsKey(key);
    }

    /**
     * Returns true if the key exists and the value is non-null.
     */
    public boolean hasNonNull(String key) {
        if (key == null) return false;
        return getNode(key) != null;
    }

    /**
     * Returns a key view of gettable declared properties and dynamic members.
     * When dynamic storage exists, its keys are backed by the underlying map,
     * so removing them through the view also changes this object. Declared JOJO
     * keys cannot be removed. An object without dynamic storage returns an
     * empty or declared-only view.
     */
    public Set<String> keySet() {
        if (pi == null || pi.gettableProperties.length == 0) {
            return dynamicMembers == null ? Collections.emptySet() : dynamicMembers.keySet();
        }

        if (dynamicMembers == null) {
            return pi.gettablePropertyNameSet();
        }

        return new AbstractSet<String>() {

            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {
                    private int propertyIndex;
                    private final Iterator<String> dynamicIterator = dynamicMembers == null
                            ? Collections.<String>emptySet().iterator()
                            : dynamicMembers.keySet().iterator();

                    @Override
                    public boolean hasNext() {
                        return propertyIndex < pi.gettableProperties.length || dynamicIterator.hasNext();
                    }

                    @Override
                    public String next() {
                        if (propertyIndex < pi.gettableProperties.length) {
                            return pi.gettableProperties[propertyIndex++].name;
                        }
                        return dynamicIterator.next();
                    }
                };
            }

            @Override
            public int size() {
                return pi.gettableProperties.length + (dynamicMembers == null ? 0 : dynamicMembers.size());
            }

            @Override
            public boolean contains(Object key) {
                return key instanceof String && JsonObject.this.containsKey((String) key);
            }
        };
    }

    /**
     * Returns an entry view of gettable declared properties and dynamic members.
     * Declared-property entries are snapshots; dynamic members are backed by the
     * map. For dynamic entries, {@code setValue()} and iterator removal mutate
     * this object. An object without dynamic storage returns an empty or
     * declared-only view.
     */
    public Set<Map.Entry<String, Object>> entrySet() {
        if (pi == null || pi.gettableProperties.length == 0) {
            return dynamicMembers == null ? Collections.emptySet() : dynamicMembers.entrySet();
        }

        return new AbstractSet<Map.Entry<String, Object>>() {

            @Override
            public Iterator<Map.Entry<String, Object>> iterator() {
                return new Iterator<Map.Entry<String, Object>>() {
                    private int propertyIndex;
                    private final Iterator<Map.Entry<String, Object>> dynamicIterator = dynamicMembers == null
                            ? Collections.<Map.Entry<String, Object>>emptySet().iterator()
                            : dynamicMembers.entrySet().iterator();

                    @Override
                    public boolean hasNext() {
                        return propertyIndex < pi.gettableProperties.length || dynamicIterator.hasNext();
                    }

                    @Override
                    public Map.Entry<String, Object> next() {
                        if (propertyIndex < pi.gettableProperties.length) {
                            PropertyInfo property = pi.gettableProperties[propertyIndex++];
                            return new AbstractMap.SimpleImmutableEntry<>(property.name,
                                    property.invokeGetter(JsonObject.this));
                        }
                        return dynamicIterator.next();
                    }
                };
            }

            @Override
            public int size() {
                return pi.gettableProperties.length + (dynamicMembers == null ? 0 : dynamicMembers.size());
            }
        };
    }

    /**
     * Performs the given visitor for each gettable member.
     */
    public void forEach(BiConsumer<String, Object> visitor) {
        Asserts.notNull(visitor, "visitor");
        if (pi != null) {
            for (PropertyInfo propertyInfo : pi.gettableProperties){
                visitor.accept(propertyInfo.name, propertyInfo.invokeGetter(this));
            }
        }
        if (dynamicMembers != null) {
            for (Map.Entry<String, Object> entry : dynamicMembers.entrySet()){
                visitor.accept(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Returns true if any gettable member matches the predicate.
     */
    public boolean anyMatch(BiPredicate<String, Object> predicate) {
        Asserts.notNull(predicate, "predicate");
        if (pi != null) {
            for (PropertyInfo propertyInfo : pi.gettableProperties){
                if (predicate.test(propertyInfo.name, propertyInfo.invokeGetter(this))) {
                    return true;
                }
            }
        }
        if (dynamicMembers != null) {
            for (Map.Entry<String, Object> entry : dynamicMembers.entrySet()){
                if (predicate.test(entry.getKey(), entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Replaces values for gettable-and-settable declared properties and dynamic
     * members in place. Only a change of value reference counts as a replacement.
     *
     * @return whether at least one value reference changed
     */
    public boolean replaceAll(BiFunction<String, Object, Object> mapper) {
        Asserts.notNull(mapper, "mapper");
        boolean changed = false;
        if (pi != null) {
            for (PropertyInfo propertyInfo : pi.gettableProperties){
                if (!propertyInfo.settable) {
                    continue;
                }
                Object oldValue = propertyInfo.invokeGetter(this);
                Object newValue = mapper.apply(propertyInfo.name, oldValue);
                if (newValue != oldValue) {
                    propertyInfo.invokeSetter(this, newValue);
                    changed = true;
                }
            }
        }
        if (dynamicMembers != null) {
            for (Map.Entry<String, Object> entry : dynamicMembers.entrySet()){
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
     * Returns a mutable shallow snapshot of gettable declared properties and
     * dynamic members. Dynamic members take precedence on duplicate keys;
     * nested values retain their original references.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (pi != null) {
            for (PropertyInfo propertyInfo : pi.gettableProperties){
                merged.put(propertyInfo.name, propertyInfo.invokeGetter(this));
            }
        }
        if (dynamicMembers != null) {
            merged.putAll(dynamicMembers);
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
     * JSON Binding
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
     * YAML Binding
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
     * Properties Binding
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
     * Mapping
     * --------------------------------------------------------------
     */

    /**
     * Structurally converts a value to a JsonObject.
     */
    public static JsonObject fromNode(Object node) {
        return (JsonObject) NodeMapper.convert(node, JsonObject.class, false, RuntimeContext.EMPTY);
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
     * Only gettable declared properties participate in this value view. When a
     * declared property is not gettable, lookup falls through to dynamic members
     * with the same key.
     */
    public Object getNode(String key) {
        if (key == null) return null;
        if (pi != null) {
            PropertyInfo propertyInfo = pi.getPropertyNoAlias(key);
            if (propertyInfo != null && propertyInfo.gettable) {
                return propertyInfo.invokeGetter(this);
            }
        }
        if (dynamicMembers != null) {
            return dynamicMembers.get(key);
        }
        return null;
    }

    /**
     * Returns the OBNT value for the given key, or the default when the key is
     * absent or its value is {@code null}.
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
     * Returns a JsonObject value using strict conversion. A Map value is
     * copied at the outer level; an existing JsonObject is returned as-is.
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
        Asserts.notNull(clazz, "clazz");
        try {
            return Nodes.toMap(getNode(key), clazz);
        } catch (Exception e) {
            throw _strict(key, clazz, Map.class, e);
        }
    }

    /**
     * Returns a JsonArray value using strict conversion. A List value is
     * copied at the outer level; an existing JsonArray is returned as-is.
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
        Asserts.notNull(clazz, "clazz");
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
        Asserts.notNull(clazz, "clazz");
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
        Asserts.notNull(clazz, "clazz");
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
        Asserts.notNull(clazz, "clazz");
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
            PropertyInfo fi = pi.getPropertyNoAlias(key);
            if (fi != null) {
                if (fi.settable) {
                    fi.invokeSetter(this, object);
                    return null;
                }
                throw new NodeException("property '" + key + "' is not settable");
            }
        }
        if (dynamicMembers == null) dynamicMembers = new LinkedHashMap<>();
        return dynamicMembers.put(key, object);
    }

    /**
     * Computes and stores a value when the current value is null.
     */
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(String key, Function<String, T> computer) {
        Asserts.notNull(key, "key");
        Asserts.notNull(computer, "computer");

        if (pi != null) {
            PropertyInfo propertyInfo = pi.getPropertyNoAlias(key);
            if (propertyInfo != null && propertyInfo.gettable) {
                T old = (T) propertyInfo.invokeGetter(this);
                if (old != null) {
                    return old;
                }
                T newNode = computer.apply(key);
                if (newNode != null) {
                    put(key, newNode);
                }
                return newNode;
            }
        }

        if (dynamicMembers == null) dynamicMembers = new LinkedHashMap<>();
        return (T) dynamicMembers.computeIfAbsent(key, computer);
    }

    /**
     * Copies all gettable members from the given object node representation.
     * <p>
     * Supported inputs follow {@link Nodes#forEachObject(Object, BiConsumer)}:
     * {@link Map}, {@link JsonObject}, JOJO/POJO, and supported backend-native
     * tree representations that participate as object nodes.
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
        if (pi != null && pi.getPropertyNoAlias(key) != null) {
            throw new NodeException("cannot remove declared property '" + key + "' from JOJO '" +
                    getClass().getName() + "'; only dynamic members are removable");
        }
        if (dynamicMembers != null) {
            return dynamicMembers.remove(key);
        }
        return null;
    }

    /**
     * Removes dynamic members that match the predicate.
     */
    public boolean removeIf(Predicate<Map.Entry<String, Object>> filter) {
        Asserts.notNull(filter, "filter");
        if (dynamicMembers != null) {
            return dynamicMembers.entrySet().removeIf(filter);
        }
        return false;
    }

    /**
     * Clears dynamic members only, keeping declared property values intact.
     */
    public void clear() {
        if (dynamicMembers != null) {
            dynamicMembers.clear();
        }
    }

    /**
     * Drops the dynamic map reference and keeps only declared property values.
     */
    public void prune() {
        dynamicMembers = null;
    }


    /*
     * --------------------------------------------------------------
     * Copy and Merge
     * --------------------------------------------------------------
     */

    /**
     * Creates a shallow copy of this JsonObject.
     * <p>
     * Plain {@link JsonObject} instances copy their dynamic members; nested values
     * are shared.
     * JOJO subtypes fall back to {@link Nodes#copy(Object)} so subtype properties
     * remain part of the copied object view.
     */
    public JsonObject copy() {
        if (getClass() == JsonObject.class) {
            return dynamicMembers == null ? new JsonObject() : new JsonObject(dynamicMembers);
        }
        return Nodes.copy(this);
    }

    /**
     * Creates a deep copy of this JsonObject.
     * <p>
     * Delegates to {@link Sjf4j#deepCopy(Object)} and {@link NodeMapper}.
     * Declared properties and dynamic members are copied structurally; unsupported nested
     * values can cause binding to fail rather than being copied.
     */
    public JsonObject deepCopy() {
        return Sjf4j.global().deepCopy(this);
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
