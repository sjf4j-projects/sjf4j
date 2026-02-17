package org.sjf4j;

import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeStream;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
    protected transient Map<String, Object> dynamicMap;
    
    /**
     * Stores field information for POJO mapping.
     */
    protected transient Map<String, NodeRegistry.FieldInfo> fieldMap =
            this.getClass() == JsonObject.class
            ? null
            : NodeRegistry.registerPojoOrElseThrow(this.getClass()).fields;

    /**
     * Creates an empty JsonObject instance.
     */
    public JsonObject() {
        super();
    }

    public JsonObject(Map<String, Object> dynamicMap) {
        this();
        this.dynamicMap = dynamicMap;
    }

    public JsonObject(JsonObject jo) {
        this();
        putAll(jo);
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
        if (node == null) return;
        if (node instanceof Map) {
            this.dynamicMap = (Map<String, Object>) node;
            return;
        }
        if (node instanceof JsonObject) {
            putAll((JsonObject) node);
            return;
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null && !pi.isJajo) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                Object v = entry.getValue().invokeGetter(node);
                put(entry.getKey(), v);
            }
            return;
        }
        throw new JsonException("Cannot wrap value of type '" + node.getClass().getName() +
                "' into JsonObject. Supported types are: JsonObject, Map, or POJO.");
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

    public void setDynamicMap(Map<String, Object> map) {
        this.dynamicMap = map;
    }

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
//        return Nodes.hash(this);
        int hash = dynamicMap == null ? 0 : dynamicMap.hashCode();
        if (fieldMap != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : fieldMap.entrySet()){
                hash += Objects.hashCode(entry.getKey()) ^
                        Objects.hashCode(entry.getValue().invokeGetter(this));
            }
        }
        return hash;
    }

    @Override
    public boolean equals(Object target) {
//        return Nodes.equals(this, target);
        if (target == this) return true;
        if (target == null || target.getClass() != this.getClass()) return false;
        JsonObject targetJo = (JsonObject) target;
        if (targetJo.size() != this.size()) return false;
        for (Map.Entry<String, Object> entry : entrySet()) {
            Object value = entry.getValue();
            Object targetValue = targetJo.get(entry.getKey());
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
     * Returns the total number of entries in this JsonObject, including both
     * POJO fields and dynamic nodes.
     *
     * @return total number of entries
     */
    @Override
    public int size() {
        return (fieldMap == null ? 0 : fieldMap.size()) + (dynamicMap == null ? 0 : dynamicMap.size());
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
            return dynamicMap == null ? Collections.emptySet() : dynamicMap.keySet();
        } else if (dynamicMap == null) {
            return fieldMap.keySet();
        } else {
            Set<String> merged = new LinkedHashSet<>(fieldMap.keySet());
            merged.addAll(dynamicMap.keySet());
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
        if (key == null) return false;
        return (fieldMap != null && fieldMap.containsKey(key)) || (dynamicMap != null && dynamicMap.containsKey(key));
    }

    /**
     * Checks if this JsonObject contains the specified key with a non-null value.
     *
     * @param key the key to check
     * @return true if the key exists and has a non-null value, false otherwise
     */
    public boolean hasNonNull(String key) {
        if (key == null) return false;
        return getNode(key) != null;
    }

    /**
     * Performs the given action for each entry in this JsonObject, including both
     * POJO fields and dynamic nodes.
     *
     * @param action the action to be performed for each entry
     */
    public void forEach(BiConsumer<String, Object> action) {
        Objects.requireNonNull(action, "action is null");
        if (fieldMap != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : fieldMap.entrySet()){
                action.accept(entry.getKey(), entry.getValue().invokeGetter(this));
            }
        }
        if (dynamicMap != null) {
            for (Map.Entry<String, Object> entry : dynamicMap.entrySet()){
                action.accept(entry.getKey(), entry.getValue());
            }
        }
    }


    public Map<String, Object> toMap() {
        Map<String, Object> merged = Sjf4jConfig.global().mapSupplier.create();
        if (fieldMap != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : fieldMap.entrySet()){
                merged.put(entry.getKey(), entry.getValue().invokeGetter(this));
            }
        }
        if (dynamicMap != null) {
            merged.putAll(dynamicMap);
        }
        return merged;
    }

    public <T> Map<String, T> toMap(Class<T> clazz) {
        return Nodes.toMap(toMap(), clazz);
    }

    public <T> T toPojo(Class<T> clazz) {
        return Nodes.toPojo(this, clazz);
    }

    public Set<Map.Entry<String, Object>> entrySet() {
        Set<Map.Entry<String, Object>> set = new LinkedHashSet<>(size());
        if (fieldMap != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : fieldMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue().invokeGetter(this);
                set.add(new AbstractMap.SimpleEntry<>(key, value));
            }
        }
        if (dynamicMap != null) {
            set.addAll(dynamicMap.entrySet());
        }
        return set;
    }

    public boolean removeIf(Predicate<Map.Entry<String, Object>> filter) {
        if (dynamicMap != null) {
            return  dynamicMap.entrySet().removeIf(filter);
        }
        return false;
    }


    /// JSON Facade

    public static JsonObject fromJson(String input) {
        return Sjf4j.fromJson(input, JsonObject.class);
    }

    public static <T extends JsonObject> T fromJson(String input, Class<T> clazz) {
        return Sjf4j.fromJson(input, clazz);
    }

    public static <T extends JsonObject> T fromJson(String input, TypeReference<T> type) {
        return Sjf4j.fromJson(input, type);
    }

    public String toJson() {
        return Sjf4j.toJsonString(this);
    }

    ///  YAML Facade

    public static JsonObject fromYaml(String input) {
        return Sjf4j.fromYaml(input, JsonObject.class);
    }

    public static <T extends JsonObject> T fromYaml(String input, Class<T> clazz) {
        return Sjf4j.fromYaml(input, clazz);
    }

    public static <T extends JsonObject> T fromYaml(String input, TypeReference<T> type) {
        return Sjf4j.fromYaml(input, type);
    }

    public String toYaml() {
        return Sjf4j.toYamlString(this);
    }

    /// Node Facade

    public static JsonObject fromNode(Object node) {
        return Sjf4j.fromNode(node, JsonObject.class);
    }

    public static <T extends JsonObject> T fromNode(Object node, Class<T> clazz) {
        return Sjf4j.fromNode(node, clazz);
    }

    public static <T extends JsonObject> T fromNode(Object node, TypeReference<T> type) {
        return Sjf4j.fromNode(node, type);
    }

    public Object toRaw() {
        return Sjf4j.toRaw(this);
    }



    /// Properties Facade

    public static JsonObject fromProperties(Properties props) {
        return Sjf4j.fromProperties(props, JsonObject.class);
    }

    public Properties toProperties() {
        return Sjf4j.toProperties(this);
    }


    /// Getter

    public Object getNode(String key) {
        if (key == null) return null;
        if (fieldMap != null) {
            NodeRegistry.FieldInfo fi = fieldMap.get(key);
            if (fi != null) {
                return fi.invokeGetter(this);
            }
        }
        if (dynamicMap != null) {
            return dynamicMap.get(key);
        }
        return null;
    }

    public Object getNode(String key, Object defaultValue) {
        Object value = getNode(key);
        return value == null ? defaultValue : value;
    }

    public String getString(String key) {
        try {
            Object value = getNode(key);
            return Nodes.toString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get String for key '" + key + "'", e);
        }
    }

    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value == null ? defaultValue : value;
    }

    public String getAsString(String key) {
        Object value = getNode(key);
        return Nodes.asString(value);
    }

    public String getAsString(String key, String defaultValue) {
        String value = getAsString(key);
        return value == null ? defaultValue : value;
    }

    public Number getNumber(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toNumber(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Number for key '" + key + "'", e);
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
    public Number getAsNumber(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asNumber(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Number", e);
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
    public Number getAsNumber(String key, Number defaultValue) {
        Number value = getAsNumber(key);
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
            return Nodes.toLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Long for key '" + key + "'", e);
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
    public Long getAsLong(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asLong(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Long", e);
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
    public long getAsLong(String key, long defaultValue) {
        Long value = getAsLong(key);
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
            return Nodes.toInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Integer for key '" + key + "'", e);
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
    public Integer getAsInteger(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Integer", e);
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
    public int getAsInteger(String key, int defaultValue) {
        Integer value = getAsInteger(key);
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
            return Nodes.toShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Short for key '" + key + "'", e);
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
    public Short getAsShort(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asShort(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Short", e);
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
    public short getAsShort(String key, short defaultValue) {
        Short value = getAsShort(key);
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
            return Nodes.toByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Byte for key '" + key + "'", e);
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
    public Byte getAsByte(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asByte(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Byte", e);
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
    public byte getAsByte(String key, byte defaultValue) {
        Byte value = getAsByte(key);
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
            return Nodes.toDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Double for key '" + key + "'", e);
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
    public Double getAsDouble(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asDouble(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Double", e);
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
    public double getAsDouble(String key, double defaultValue) {
        Double value = getAsDouble(key);
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
            return Nodes.toFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Float for key '" + key + "'", e);
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
    public Float getAsFloat(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asFloat(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Float", e);
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
    public float getAsFloat(String key, float defaultValue) {
        Float value = getAsFloat(key);
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
            return Nodes.toBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigInteger for key '" + key + "'", e);
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
    public BigInteger getAsBigInteger(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asBigInteger(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to BigInteger", e);
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
    public BigInteger getAsBigInteger(String key, BigInteger defaultValue) {
        BigInteger value = getAsBigInteger(key);
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
            return Nodes.toBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get BigDecimal for key '" + key + "'", e);
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
    public BigDecimal getAsBigDecimal(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asBigDecimal(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to BigDecimal", e);
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
    public BigDecimal getAsBigDecimal(String key, BigDecimal defaultValue) {
        BigDecimal value = getAsBigDecimal(key);
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
            return Nodes.toBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Boolean for key '" + key + "'", e);
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
    public Boolean getAsBoolean(String key) {
        Object value = getNode(key);
        try {
            return Nodes.asBoolean(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Boolean", e);
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
    public boolean getAsBoolean(String key, boolean defaultValue) {
        Boolean value = getAsBoolean(key);
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
            return Nodes.toJsonObject(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonObject for key '" + key + "'", e);
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

    public Map<String, Object> getMap(String key) {
        Object value = getNode(key);
        try {
            return Nodes.toMap(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Map<String, Object> for key '" + key + "'", e);
        }
    }
    public Map<String, Object> getMap(String key, Map<String, Object> defaultValue) {
        Map<String, Object> value = getMap(key);
        return value == null ? defaultValue : value;
    }

    public <T> Map<String, T> getMap(String key, Class<T> clazz) {
        Object value = getNode(key);
        try {
            return Nodes.toMap(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to Map<String, " + clazz.getName() + ">", e);
        }
    }
    public <T> Map<String, T> getMap(String key, Class<T> clazz, Map<String, T> defaultValue) {
        Map<String, T> value = getMap(key, clazz);
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
            return Nodes.toJsonArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get JsonArray for key '" + key + "'", e);
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

    public List<Object> getList(String key) {
        try {
            Object value = getNode(key);
            return Nodes.toList(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get List<Object> for key '" + key + "'", e);
        }
    }
    public List<Object> getList(String key, List<Object> defaultValue) {
        List<Object> value = getList(key);
        return value == null ? defaultValue : value;
    }

    public <T> List<T> getList(String key, Class<T> clazz) {
        try {
            Object value = getNode(key);
            return Nodes.toList(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to List<" + clazz.getName() + ">", e);
        }
    }
    public <T> List<T> getList(String key, Class<T> clazz, List<T> defaultValue) {
        List<T> value = getList(key, clazz);
        return value == null ? defaultValue : value;
    }

    public Object[] getArray(String key) {
        try {
            Object value = getNode(key);
            return Nodes.toArray(value);
        } catch (Exception e) {
            throw new JsonException("Failed to get Object[] for key '" + key + "'", e);
        }
    }
    public Object[] getArray(String key, Object[] defaultValue) {
        Object[] value = getArray(key);
        return value == null ? defaultValue : value;
    }

    public <T> T[] getArray(String key, Class<T> clazz) {
        try {
            Object value = getNode(key);
            return Nodes.toArray(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to " + clazz.getName() + "[]", e);
        }
    }
    public <T> T[] getArray(String key, Class<T> clazz, T[] defaultValue) {
        T[] value = getArray(key, clazz);
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
            return Nodes.to(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to get " + clazz.getName() + " for key '" + key + "'", e);
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
    public <T> T getAs(String key, Class<T> clazz) {
        Object value = getNode(key);
        try {
            return Nodes.as(value, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert key '" + key + "' to " + clazz.getName(), e);
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
    public <T> T getAs(String key, T... reified) {
        if (reified.length > 0) throw new IllegalArgumentException("`reified` should be empty.");
        Class<T> clazz = (Class<T>) reified.getClass().getComponentType();
        return getAs(key, clazz);
    }


    /// Putter

    public Object put(String key, Object object) {
        Objects.requireNonNull(key, "key is null");
        if (fieldMap != null) {
            NodeRegistry.FieldInfo fi = fieldMap.get(key);
            if (fi != null) {
                Object old = fi.invokeGetter(this);
                fi.invokeSetter(this, object);
                return old;
            }
        }
        if (dynamicMap == null) {
            dynamicMap = Sjf4jConfig.global().mapSupplier.create();
        }
        return dynamicMap.put(key, object);
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

    public Object replace(String key, Object value) {
        Object old = get(key);
        if (old != null) {
            return put(key, value);
        }
        return null;
    }

    // Try generic
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(String key, Function<String, T> computer) {
        Objects.requireNonNull(computer, "computer is null");
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

    public void putAll(Map<String, Object> map) {
        if (map == null) return;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public void putAll(JsonObject jo) {
        if (jo == null) return;
        jo.forEach(this::put);
    }

    @SuppressWarnings("unchecked")
    public void putAll(Object node) {
        if (node == null) return;
        if (node instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) node).entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
            return;
        }
        if (node instanceof JsonObject) {
            ((JsonObject) node).forEach(this::put);
            return;
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null && !pi.isJajo) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                put(entry.getKey(), entry.getValue().invokeGetter(node));
            }
            return;
        }
        throw new JsonException("Cannot wrap value of type '" + node.getClass().getName() +
                "' into JsonObject. Supported types are: JsonObject, Map, or POJO.");
    }

    public Object remove(String key) {
        Objects.requireNonNull(key, "key is null");
        if (fieldMap != null && fieldMap.containsKey(key)) {
            throw new JsonException("Cannot remove key '" + key + "' from JOJO '" + getClass() +
                    "'. Only dynamic properties in JsonObject are removable.");
        }
        if (dynamicMap != null) {
            return dynamicMap.remove(key);
        }
        return null;
    }

    /**
     * Clear all dynamic nodes while keeping the nodeMap container.
     */
    public void clear() {
        if (dynamicMap != null) {
            dynamicMap.clear();
        }
    }

    /**
     * Clears the dynamic nodeMap container by setting it to null.
     *
     * <p>This effectively disables all dynamic properties for this JsonObject,
     * leaving only the statically defined POJO fields in fieldMap.
     * Can help reduce memory usage if the dynamic map is no longer needed.</p>
     */
    public void prune() {
        dynamicMap = null;
    }


    /// Copy, merge

    /**
     * Creates a shallow copy of the current JsonObject.
     * <p>
     * A shallow copy replicates only the object itself, not the reference-type fields it contains.
     * The copied object shares the same internal data structure references as the original.
     *
     * @param <T> the target JsonObject type
     * @return a shallow copy of the current JsonObject
     */
    @SuppressWarnings("unchecked")
    public <T extends JsonObject>  T copy() {
        return (T) Nodes.copy(this);
    }

    /**
     * Creates a deep copy of the current JsonObject.
     * <p>
     * A deep copy recursively replicates the object and all its contained reference-type fields,
     * ensuring the copy is completely independent from the original. Modifications to the copy
     * will not affect the original object.
     *
     * @param <T> the target JsonObject type
     * @return a deep copy of the current JsonObject
     */
    @SuppressWarnings("unchecked")
    public <T extends JsonObject>  T deepCopy() {
        return (T) Sjf4j.deepNode(this);
    }

    /// Stream

    public NodeStream<JsonObject> stream() {
        return NodeStream.of(this);
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
            jo.ensurePutByPath(path, value);
            return this;
        }
        public Builder putNonNullByPath(String path, Object value) {
            jo.ensurePutNonNullByPath(path, value);
            return this;
        }
        public Builder putIfAbsentByPath(String path, Object value) {
            jo.ensurePutIfAbsentByPath(path, value);
            return this;
        }
        public JsonObject build() {
            return jo;
        }
    }


}
