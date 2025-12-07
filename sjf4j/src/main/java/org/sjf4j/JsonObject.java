package org.sjf4j;

import org.sjf4j.util.ContainerUtil;
import org.sjf4j.util.TypeReference;
import org.sjf4j.util.NodeUtil;

import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Represents a JSON object that extends {@link JsonContainer} and provides a map-like interface
 * for working with JSON data. JsonObject supports both dynamic node-based storage and POJO field mapping,
 * allowing seamless integration between JSON structures and Java objects.
 *
 * <p>JsonObject can be initialized from various sources:
 * <ul>
 *   <li>Empty constructor for creating a new JsonObject</li>
 *   <li>Existing JsonObject instances (deep copy)</li>
 *   <li>Map objects</li>
 *   <li>POJO objects (automatically maps fields)</li>
 *   <li>Direct key-value pairs</li>
 * </ul>
 *
 * <p>This class implements map-like operations while maintaining JSON type safety and
 * providing convenient methods for type conversion and navigation.
 */
public class JsonObject extends JsonContainer {

    /**
     * Stores dynamic JSON nodes as key-value pairs.
     */
    protected transient Map<String, Object> nodeMap;
    
    /**
     * Stores field information for POJO mapping.
     */
    protected transient Map<String, PojoRegistry.FieldInfo> fieldMap;

    /**
     * Creates an empty JsonObject instance.
     * If this is called from a subclass, it registers the subclass fields for POJO mapping.
     */
    public JsonObject() {
        if (this.getClass() != JsonObject.class) {
            fieldMap = PojoRegistry.registerOrElseThrow(this.getClass()).getFields();
        }
    }

    /**
     * Creates a JsonObject from an existing object, supporting multiple input types.
     *
     * @param node the object to wrap or copy
     * @throws JsonException if the input object type is not supported
     * @see #put(String, Object)
     */
    @SuppressWarnings("unchecked")
    public JsonObject(Object node) {
        this();
        if (node == null) throw new IllegalArgumentException("Node must not be null");
        if (node instanceof JsonObject) {
            JsonObject jo = (JsonObject) node;
            if (jo.fieldMap != null) {
                for (Map.Entry<String, PojoRegistry.FieldInfo> fi : jo.fieldMap.entrySet()) {
                    Object v = fi.getValue().invokeGetter(node);
                    put(fi.getKey(), v);
                }
            }
            if (jo.nodeMap != null) {
                if (this.nodeMap == null) {
                    this.nodeMap = jo.nodeMap;
                } else {
                    putAll(jo.nodeMap);
                }
            }
        } else if (node instanceof Map) {
            this.nodeMap = (Map<String, Object>) node;
        } else if (PojoRegistry.isPojo(node.getClass())) {
            PojoRegistry.PojoInfo pi = PojoRegistry.getPojoInfo(node.getClass());
            for (Map.Entry<String, PojoRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                Object v = fi.getValue().invokeGetter(node);
                put(fi.getKey(), v);
            }
        } else {
            throw new JsonException("Cannot wrap value of type '" + node.getClass().getName() +
                        "' into JsonObject. Supported types are: JsonObject, Map, or POJO.");
        }
    }

    /**
     * Creates a JsonObject with a single key-value pair.
     *
     * @param key1 the first key
     * @param value1 the first value
     */
    public JsonObject(String key1, Object value1) {
        this();
        put(key1, value1);
    }
    
    /**
     * Creates a JsonObject with two key-value pairs.
     *
     * @param key1 the first key
     * @param value1 the first value
     * @param key2 the second key
     * @param value2 the second value
     */
    public JsonObject(String key1, Object value1,
                      String key2, Object value2) {
        this(key1, value1);
        put(key2, value2);
    }
    
    /**
     * Creates a JsonObject with three key-value pairs.
     *
     * @param key1 the first key
     * @param value1 the first value
     * @param key2 the second key
     * @param value2 the second value
     * @param key3 the third key
     * @param value3 the third value
     */
    public JsonObject(String key1, Object value1,
                      String key2, Object value2,
                      String key3, Object value3) {
        this(key1, value1, key2, value2);
        put(key3, value3);
    }
    
    /**
     * Creates a JsonObject with four key-value pairs.
     *
     * @param key1 the first key
     * @param value1 the first value
     * @param key2 the second key
     * @param value2 the second value
     * @param key3 the third key
     * @param value3 the third value
     * @param key4 the fourth key
     * @param value4 the fourth value
     */
    public JsonObject(String key1, Object value1,
                      String key2, Object value2,
                      String key3, Object value3,
                      String key4, Object value4) {
        this(key1, value1, key2, value2, key3, value3);
        put(key4, value4);
    }
    
    /**
     * Creates a JsonObject with five key-value pairs.
     *
     * @param key1 the first key
     * @param value1 the first value
     * @param key2 the second key
     * @param value2 the second value
     * @param key3 the third key
     * @param value3 the third value
     * @param key4 the fourth key
     * @param value4 the fourth value
     * @param key5 the fifth key
     * @param value5 the fifth value
     */
    public JsonObject(String key1, Object value1,
                      String key2, Object value2,
                      String key3, Object value3,
                      String key4, Object value4,
                      String key5, Object value5) {
        this(key1, value1, key2, value2, key3, value3, key4, value4);
        put(key5, value5);
    }
    
    /**
     * Creates a JsonObject with six key-value pairs.
     *
     * @param key1 the first key
     * @param value1 the first value
     * @param key2 the second key
     * @param value2 the second value
     * @param key3 the third key
     * @param value3 the third value
     * @param key4 the fourth key
     * @param value4 the fourth value
     * @param key5 the fifth key
     * @param value5 the fifth value
     * @param key6 the sixth key
     * @param value6 the sixth value
     */
    public JsonObject(String key1, Object value1,
                      String key2, Object value2,
                      String key3, Object value3,
                      String key4, Object value4,
                      String key5, Object value5,
                      String key6, Object value6) {
        this(key1, value1, key2, value2, key3, value3, key4, value4, key5, value5);
        put(key6, value6);
    }
    
    /**
     * Creates a JsonObject with seven key-value pairs.
     *
     * @param key1 the first key
     * @param value1 the first value
     * @param key2 the second key
     * @param value2 the second value
     * @param key3 the third key
     * @param value3 the third value
     * @param key4 the fourth key
     * @param value4 the fourth value
     * @param key5 the fifth key
     * @param value5 the fifth value
     * @param key6 the sixth key
     * @param value6 the sixth value
     * @param key7 the seventh key
     * @param value7 the seventh value
     */
    public JsonObject(String key1, Object value1,
                      String key2, Object value2,
                      String key3, Object value3,
                      String key4, Object value4,
                      String key5, Object value5,
                      String key6, Object value6,
                      String key7, Object value7) {
        this(key1, value1, key2, value2, key3, value3, key4, value4, key5, value5, key6, value6);
        put(key7, value7);
    }
    
    /**
     * Creates a JsonObject with eight key-value pairs.
     *
     * @param key1 the first key
     * @param value1 the first value
     * @param key2 the second key
     * @param value2 the second value
     * @param key3 the third key
     * @param value3 the third value
     * @param key4 the fourth key
     * @param value4 the fourth value
     * @param key5 the fifth key
     * @param value5 the fifth value
     * @param key6 the sixth key
     * @param value6 the sixth value
     * @param key7 the seventh key
     * @param value7 the seventh value
     * @param key8 the eighth key
     * @param value8 the eighth value
     */
    public JsonObject(String key1, Object value1,
                      String key2, Object value2,
                      String key3, Object value3,
                      String key4, Object value4,
                      String key5, Object value5,
                      String key6, Object value6,
                      String key7, Object value7,
                      String key8, Object value8) {
        this(key1, value1, key2, value2, key3, value3, key4, value4, key5, value5, key6, value6, key7, value7);
        put(key8, value8);
    }

    /// Subclass

//    @Deprecated
//    public <T extends JsonObject> T cast(Class<T> clazz) {
//        //TODO
//        try {
//            Constructor<T> constructor = clazz.getConstructor(JsonObject.class);
//            return constructor.newInstance(this);
//        } catch (Throwable e) {
//            throw new JsonException("Failed to cast JsonObject to '" + clazz.getName() + "'", e);
//        }
//    }

    /// Map

    /**
     * Returns a JSON-like string representation of this JsonObject.
     *
     * @return JSON-like string representation
     */
    @Override
    public String toString() {
        return inspect();
    }

    /**
     * Computes the hash code for this JsonObject by combining the hash codes of
     * both fieldMap and nodeMap entries.
     *
     * @return the computed hash code
     */
    @Override
    public int hashCode() {
        int hash = nodeMap == null ? 0 : nodeMap.hashCode();
        if (fieldMap != null) {
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry : fieldMap.entrySet()){
                hash += Objects.hashCode(entry.getKey()) ^
                        Objects.hashCode(entry.getValue().invokeGetter(this));
            }
        }
        return hash;
    }

    /**
     * Returns the total number of entries in this JsonObject, including both
     * POJO fields and dynamic nodes.
     *
     * @return total number of entries
     */
    @Override
    public int size() {
        return (fieldMap == null ? 0 : fieldMap.size()) + (nodeMap == null ? 0 : nodeMap.size());
    }

    /**
     * Checks if this JsonObject is empty (contains no entries).
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns a set containing all keys in this JsonObject, including both
     * POJO field names and dynamic node keys.
     *
     * @return set of all keys
     */
    public Set<String> keySet() {
        if (fieldMap == null) {
            return nodeMap == null ? Collections.emptySet() : nodeMap.keySet();
        } else if (nodeMap == null) {
            return fieldMap.keySet();
        } else {
            Set<String> merged = fieldMap.keySet();
            merged.addAll(nodeMap.keySet());
            return merged;
        }
    }

    /**
     * Checks if this JsonObject contains the specified key, either as a POJO field
     * or a dynamic node key.
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     */
    public boolean containsKey(String key) {
        if (key == null) throw new IllegalArgumentException("Key must not be null");
        return (fieldMap != null && fieldMap.containsKey(key)) || (nodeMap != null && nodeMap.containsKey(key));
    }

    /**
     * Checks if this JsonObject contains the specified key with a non-null value.
     *
     * @param key the key to check
     * @return true if the key exists and has a non-null value, false otherwise
     */
    public boolean hasNonNull(String key) {
        if (key == null) throw new IllegalArgumentException("Key must not be null");
        return getNode(key) != null;
    }

    /**
     * Performs the given action for each entry in this JsonObject, including both
     * POJO fields and dynamic nodes.
     *
     * @param action the action to be performed for each entry
     */
    public void forEach(BiConsumer<String, Object> action) {
        if (action == null) throw new IllegalArgumentException("Action must not be null");
        if (fieldMap != null) {
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry : fieldMap.entrySet()){
                action.accept(entry.getKey(), entry.getValue().invokeGetter(this));
            }
        }
        if (nodeMap != null) {
            for (Map.Entry<String, Object> entry : nodeMap.entrySet()){
                action.accept(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Converts this JsonObject to a standard Map, merging both POJO fields and dynamic nodes.
     * The returned map is a new instance if this JsonObject contains both POJO fields and dynamic nodes,
     * otherwise it returns the underlying map or an empty map.
     *
     * @return a Map containing all entries from this JsonObject
     */
    public Map<String, Object> toMap() {
        if (fieldMap == null) {
            return nodeMap == null ? Collections.emptyMap() : nodeMap;
        } else {
            Map<String, Object> merged = JsonConfig.global().mapSupplier.create();
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry : fieldMap.entrySet()){
                merged.put(entry.getKey(), entry.getValue().invokeGetter(this));
            }
            if (nodeMap != null) {
                merged.putAll(nodeMap);
            }
            return merged;
        }
    }

    /**
     * Returns a Set view of the mappings contained in this JsonObject, including both POJO fields
     * and dynamic nodes. The set is backed by the JsonObject, so changes to the JsonObject are
     * reflected in the set, and vice-versa.
     *
     * @return a Set view of the mappings in this JsonObject
     */
    public Set<Map.Entry<String, Object>> entrySet() {
        Set<Map.Entry<String, Object>> set = new LinkedHashSet<>();
        if (fieldMap != null) {
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry : fieldMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue().invokeGetter(this);
                set.add(new AbstractMap.SimpleEntry<>(key, value));
            }
        }
        if (nodeMap != null) {
            set.addAll(nodeMap.entrySet());
        }
        return set;
    }

    /**
     * Removes all entries from this JsonObject that satisfy the given predicate. Only dynamic
     * node entries are removed; POJO fields are not affected by this operation.
     *
     * @param filter the predicate used to filter entries to be removed
     * @return true if any entries were removed, false otherwise
     */
    public boolean removeIf(Predicate<Map.Entry<String, Object>> filter) {
        if (nodeMap != null) {
            return  nodeMap.entrySet().removeIf(filter);
        }
        return false;
    }


    /// JSON Facade

    public static JsonObject fromJson(String input) {
        return Sjf4j.fromJson(input);
    }

    public static <T extends JsonObject> T fromJson(String input, Class<T> clazz) {
        return Sjf4j.fromJson(input, clazz);
    }

    public static JsonObject fromJson(Reader input) {
        return Sjf4j.fromJson(input);
    }

    public static <T extends JsonObject> T fromJson(Reader input, Class<T> clazz) {
        return Sjf4j.fromJson(input, clazz);
    }


    public String toJson() {
        return Sjf4j.toJson(this);
    }

    ///  YAML Facade

    public static JsonObject fromYaml(String input) {
        return Sjf4j.fromYaml(input);
    }

    public static JsonObject fromYaml(Reader input) {
        return Sjf4j.fromYaml(input);
    }

    public String toYaml() {
        return Sjf4j.toYaml(this);
    }

    public void toYaml(Writer output) {
        Sjf4j.toYaml(output, this);
    }


    /// POJO Facade

    public <T> T toPojo(Class<T> clazz) {
        return Sjf4j.fromPojo(this, clazz);
    }

    public <T> T toPojo(TypeReference<T> type) {
        return Sjf4j.fromPojo(this, type);
    }

    public static JsonObject fromPojo(Object pojo) {
        return Sjf4j.fromPojo(pojo);
    }

    /// Properties Facade

    public static JsonObject fromProperties(Properties props) {
        return Sjf4j.fromProperties(props);
    }

    public void toProperties(Properties props) {
        Sjf4j.toProperties(props, this);
    }


    /// Getter

    /**
     * Gets the raw node value associated with the specified key, checking both POJO fields
     * and dynamic nodes. Returns null if the key does not exist.
     *
     * @param key the key to retrieve
     * @return the value associated with the key, or null if the key doesn't exist
     * @throws IllegalArgumentException if key is null
     */
    public Object getNode(String key) {
        if (key == null) throw new IllegalArgumentException("Key must not be null");
        if (fieldMap != null) {
            PojoRegistry.FieldInfo fi = fieldMap.get(key);
            if (fi != null) {
                return fi.invokeGetter(this);
            }
        }
        if (nodeMap != null) {
            return nodeMap.get(key);
        }
        return null;
    }

    /**
     * Gets the node value associated with the specified key, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the value associated with the key, or defaultValue if the key doesn't exist
     */
    public Object getNode(String key, Object defaultValue) {
        Object value = getNode(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as a String.
     *
     * @param key the key to retrieve
     * @return the String value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a String
     */
    public String getString(String key) {
        try {
            Object value = getNode(key);
            return NodeUtil.toString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get String of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as a String, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the String value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a String
     */
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as a String.
     *
     * @param key the key to retrieve
     * @return the converted String value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a String
     */
    public String asString(String key) {
        Object value = getNode(key);
        return NodeUtil.asString(value);
    }
    
    /**
     * Converts and gets the value associated with the specified key as a String, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted String value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a String
     */
    public String asString(String key, String defaultValue) {
        String value = asString(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as a Number.
     *
     * @param key the key to retrieve
     * @return the Number value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Number
     */
    public Number getNumber(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.toNumber(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Number of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as a Number, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the Number value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Number
     */
    public Number getNumber(String key, Number defaultValue) {
        Number value = getNumber(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as a Number.
     *
     * @param key the key to retrieve
     * @return the converted Number value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Number
     */
    public Number asNumber(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.asNumber(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Number: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key as a Number, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted Number value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Number
     */
    public Number asNumber(String key, Number defaultValue) {
        Number value = asNumber(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as a Long.
     *
     * @param key the key to retrieve
     * @return the Long value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Long
     */
    public Long getLong(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.toLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Long of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as a long, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the long value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a long
     */
    public long getLong(String key, long defaultValue) {
        Long value = getLong(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as a Long.
     *
     * @param key the key to retrieve
     * @return the converted Long value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Long
     */
    public Long asLong(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.asLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Long: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key as a long, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted long value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a long
     */
    public long asLong(String key, long defaultValue) {
        Long value = asLong(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as an Integer.
     *
     * @param key the key to retrieve
     * @return the Integer value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to an Integer
     */
    public Integer getInteger(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.toInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Integer of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as an int, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the int value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to an int
     */
    public int getInteger(String key, int defaultValue) {
        Integer value = getInteger(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as an Integer.
     *
     * @param key the key to retrieve
     * @return the converted Integer value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to an Integer
     */
    public Integer asInteger(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.asInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Integer: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key as an int, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted int value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to an int
     */
    public int asInteger(String key, int defaultValue) {
        Integer value = asInteger(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as a Short.
     *
     * @param key the key to retrieve
     * @return the Short value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Short
     */
    public Short getShort(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.toShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Short of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as a short, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the short value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a short
     */
    public short getShort(String key, short defaultValue) {
        Short value = getShort(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as a Short.
     *
     * @param key the key to retrieve
     * @return the converted Short value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Short
     */
    public Short asShort(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.asShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Short: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key as a short, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted short value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a short
     */
    public short asShort(String key, short defaultValue) {
        Short value = asShort(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as a Byte.
     *
     * @param key the key to retrieve
     * @return the Byte value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Byte
     */
    public Byte getByte(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.toByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Byte of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as a byte, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the byte value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a byte
     */
    public byte getByte(String key, byte defaultValue) {
        Byte value = getByte(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as a Byte.
     *
     * @param key the key to retrieve
     * @return the converted Byte value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Byte
     */
    public Byte asByte(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.asByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Byte: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key as a byte, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted byte value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a byte
     */
    public byte asByte(String key, byte defaultValue) {
        Byte value = asByte(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as a Double.
     *
     * @param key the key to retrieve
     * @return the Double value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Double
     */
    public Double getDouble(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.toDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Double of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as a double, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the double value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a double
     */
    public double getDouble(String key, double defaultValue) {
        Double value = getDouble(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as a Double.
     *
     * @param key the key to retrieve
     * @return the converted Double value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Double
     */
    public Double asDouble(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.asDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Double: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key as a double, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted double value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a double
     */
    public double asDouble(String key, double defaultValue) {
        Double value = asDouble(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as a Float.
     *
     * @param key the key to retrieve
     * @return the Float value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Float
     */
    public Float getFloat(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.toFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Float of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as a float, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the float value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a float
     */
    public float getFloat(String key, float defaultValue) {
        Float value = getFloat(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as a Float.
     *
     * @param key the key to retrieve
     * @return the converted Float value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Float
     */
    public Float asFloat(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.asFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Float: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key as a float, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted float value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a float
     */
    public float asFloat(String key, float defaultValue) {
        Float value = asFloat(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as a BigInteger.
     *
     * @param key the key to retrieve
     * @return the BigInteger value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a BigInteger
     */
    public BigInteger getBigInteger(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.toBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigInteger of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as a BigInteger, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the BigInteger value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a BigInteger
     */
    public BigInteger getBigInteger(String key, BigInteger defaultValue) {
        BigInteger value = getBigInteger(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as a BigInteger.
     *
     * @param key the key to retrieve
     * @return the converted BigInteger value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a BigInteger
     */
    public BigInteger asBigInteger(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.asBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to BigInteger: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key as a BigInteger, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted BigInteger value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a BigInteger
     */
    public BigInteger asBigInteger(String key, BigInteger defaultValue) {
        BigInteger value = asBigInteger(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as a BigDecimal.
     *
     * @param key the key to retrieve
     * @return the BigDecimal value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a BigDecimal
     */
    public BigDecimal getBigDecimal(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.toBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigDecimal of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as a BigDecimal, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the BigDecimal value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a BigDecimal
     */
    public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as a BigDecimal.
     *
     * @param key the key to retrieve
     * @return the converted BigDecimal value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a BigDecimal
     */
    public BigDecimal asBigDecimal(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.asBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to BigDecimal: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key as a BigDecimal, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted BigDecimal value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a BigDecimal
     */
    public BigDecimal asBigDecimal(String key, BigDecimal defaultValue) {
        BigDecimal value = asBigDecimal(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as a Boolean.
     *
     * @param key the key to retrieve
     * @return the Boolean value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Boolean
     */
    public Boolean getBoolean(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.toBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Boolean of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as a boolean, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the boolean value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a boolean
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Boolean value = getBoolean(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as a Boolean.
     *
     * @param key the key to retrieve
     * @return the converted Boolean value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a Boolean
     */
    public Boolean asBoolean(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.asBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Boolean: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key as a boolean, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted boolean value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a boolean
     */
    public boolean asBoolean(String key, boolean defaultValue) {
        Boolean value = asBoolean(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as a JsonObject.
     *
     * @param key the key to retrieve
     * @return the JsonObject value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a JsonObject
     */
    public JsonObject getJsonObject(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.toJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonObject of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as a JsonObject, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the JsonObject value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a JsonObject
     */
    public JsonObject getJsonObject(String key, JsonObject defaultValue) {
        JsonObject value = getJsonObject(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as a JsonObject.
     *
     * @param key the key to retrieve
     * @return the converted JsonObject value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a JsonObject
     */
    public JsonObject asJsonObject(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.asJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to JsonObject: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key as a JsonObject, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted JsonObject value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a JsonObject
     */
    public JsonObject asJsonObject(String key, JsonObject defaultValue) {
        JsonObject value = asJsonObject(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key as a JsonArray.
     *
     * @param key the key to retrieve
     * @return the JsonArray value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a JsonArray
     */
    public JsonArray getJsonArray(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.toJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonArray of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key as a JsonArray, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the JsonArray value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a JsonArray
     */
    public JsonArray getJsonArray(String key, JsonArray defaultValue) {
        JsonArray value = getJsonArray(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Converts and gets the value associated with the specified key as a JsonArray.
     *
     * @param key the key to retrieve
     * @return the converted JsonArray value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a JsonArray
     */
    public JsonArray asJsonArray(String key) {
        Object value = getNode(key);
        try {
            return NodeUtil.asJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to JsonArray: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key as a JsonArray, returning the default value
     * if the key does not exist or the value is null.
     *
     * @param key the key to retrieve
     * @param defaultValue the value to return if the key doesn't exist or is null
     * @return the converted JsonArray value, or defaultValue if the key doesn't exist
     * @throws JsonException if the value cannot be converted to a JsonArray
     */
    public JsonArray asJsonArray(String key, JsonArray defaultValue) {
        JsonArray value = asJsonArray(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets the value associated with the specified key and converts it to the given class type.
     *
     * @param <T> the type of the value to return
     * @param key the key to retrieve
     * @param clazz the class type to convert the value to
     * @return the converted value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to the specified type
     */
    public <T> T get(String key, Class<T> clazz) {
        Object value = getNode(key);
        try {
            return NodeUtil.to(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to get " + clazz.getName() + " of key '" + key + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the value associated with the specified key and converts it to the inferred class type.
     * This is a convenience method that uses reified type parameters for type inference.
     *
     * @param <T> the type of the value to return
     * @param key the key to retrieve
     * @param reified an empty array of the target type (used for type inference only)
     * @return the converted value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to the specified type
     * @throws IllegalArgumentException if reified is not empty
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return get(key, clazz);
    }

    /**
     * Converts and gets the value associated with the specified key to the given class type.
     *
     * @param <T> the type of the value to return
     * @param key the key to retrieve
     * @param clazz the class type to convert the value to
     * @return the converted value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to the specified type
     */
    public <T> T as(String key, Class<T> clazz) {
        Object value = getNode(key);
        try {
            return NodeUtil.as(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to " + clazz.getName() +
                    ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts and gets the value associated with the specified key to the inferred class type.
     * This is a convenience method that uses reified type parameters for type inference.
     *
     * @param <T> the type of the value to return
     * @param key the key to retrieve
     * @param reified an empty array of the target type (used for type inference only)
     * @return the converted value, or null if the key doesn't exist
     * @throws JsonException if the value cannot be converted to the specified type
     * @throws IllegalArgumentException if reified is not empty
     */
    @SuppressWarnings("unchecked")
    public <T> T as(String key, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return as(key, clazz);
    }


    /// Putter

    public Object put(String key, Object object) {
        if (key == null) throw new IllegalArgumentException("Key must not be null");
        if (fieldMap != null) {
            PojoRegistry.FieldInfo fi = fieldMap.get(key);
            if (fi != null) {
                Object old = fi.invokeGetter(this);
                fi.invokeSetter(this, object);
                return old;
            }
        }
        if (nodeMap == null) {
            nodeMap = JsonConfig.global().mapSupplier.create();
        }
        return nodeMap.put(key, object);
    }

    public Object putNonNull(String key, Object node) {
        if (node != null) {
            return put(key, node);
        } else {
            return getNode(key);
        }
    }

    public Object putIfAbsent(String key, Object node) {
        Object old = get(key);
        if (old == null) {
            old = put(key, node);
        }
        return old;
    }

    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(String key, Function<String, T> computer) {
        if (computer == null) throw new IllegalArgumentException("Computer must not be null");
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

    public void putAll(JsonObject jsonObject) {
        if (jsonObject == null) throw new IllegalArgumentException("JsonObject must not be null");
        jsonObject.forEach(this::put);
    }

    public void putAll(Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            put(entry.getKey().toString(), entry.getValue());
        }
    }

    public void putAll(Object pojo) {
        PojoRegistry.PojoInfo pi = PojoRegistry.registerOrElseThrow(pojo.getClass());
        for (Map.Entry<String, PojoRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
            put(entry.getKey(), entry.getValue().invokeGetter(pojo));
        }
    }

    public Object remove(String key) {
        if (key == null) throw new IllegalArgumentException("Key must not be null");
        if (fieldMap != null && fieldMap.containsKey(key)) {
            throw new JsonException("Cannot remove key '" + key + "' from JOJO '" + getClass() +
                    "'. Only dynamic properties in JsonObject are removable.");
        }
        if (nodeMap != null) {
            return nodeMap.remove(key);
        }
        return null;
    }

    public void clear() {
        if (nodeMap != null) {
            nodeMap.clear();
        }
    }

    /// Copy, merge

    @SuppressWarnings("unchecked")
    public <T extends JsonObject>  T copy() {
        return (T) ContainerUtil.copy(this);
    }

    @SuppressWarnings("unchecked")
    public <T extends JsonObject>  T deepCopy() {
        return (T) ContainerUtil.deepCopy(this);
    }

    /**
     * Merges the given target JsonObject into the current JsonObject.
     *
     * <p>All key-value pairs from the target object will be merged into this object.
     * When a key exists in both objects, the conflict resolution depends on the
     * {@code preferTarget} parameter.</p>
     *
     */
    public void merge(JsonObject target, boolean preferTarget, boolean needCopy) {
        ContainerUtil.merge(this, target, preferTarget, needCopy);
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

    public void deepPruneNulls() {
        JsonWalker.walk(this, JsonWalker.Target.CONTAINER, JsonWalker.Order.BOTTOM_UP,
                (path, node) -> {
                    if (node instanceof JsonObject) {
                        ((JsonObject) node).removeIf(e -> e.getValue() == null);
                    } else if (node instanceof Map) {
                        ((Map<?, ?>) node).entrySet().removeIf(e -> e.getValue() == null);
                    }
                    return JsonWalker.Control.CONTINUE;
                });
    }

    /// Stream

    public JsonStream<JsonObject> stream() {
        return JsonStream.of(this);
    }


    /// builder

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
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
        public Builder putNonNull(String key, Object value) {
            jo.putNonNull(key, value);
            return this;
        }
        public Builder putIfAbsent(String key, Object value) {
            jo.putIfAbsent(key, value);
            return this;
        }
        public Builder putByPath(String path, Object value) {
            jo.putByPath(path, value);
            return this;
        }
        public Builder putNonNullByPath(String path, Object value) {
            jo.putNonNullByPath(path, value);
            return this;
        }
        public Builder putIfAbsentByPath(String path, Object value) {
            jo.putIfAbsentByPath(path, value);
            return this;
        }
        public JsonObject build() {
            return jo;
        }
    }


}
