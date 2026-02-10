package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enumeration representing the different types of JSON nodes in the sjf4j library.
 * This enum categorizes nodes into value types (void, string, number, boolean),
 * object types (JSONObject, JoJo, Map, POJO), and array types (JSONArray, List, array).
 */
public enum NodeType {
    /** Represents a null or void value. */
    VALUE_NULL,
    /** Represents a string value. */
    VALUE_STRING,
    /** Represents a numeric value. */
    VALUE_NUMBER,
    /** Represents a boolean value. */
    VALUE_BOOLEAN,
    /** Represents a registered @NodeValue with custom codec. */
    VALUE_REGISTERED,

    /** Represents a {@link Map} object. */
    OBJECT_MAP,
    /** Represents a standard {@link JsonObject}. */
    OBJECT_JSON_OBJECT,
    /** Represents a custom JsonObject subtype (JOJO). */
    OBJECT_JOJO,
    /** Represents a Plain Old Java Object (POJO). */
    OBJECT_POJO,

    /** Represents a {@link List} collection. */
    ARRAY_LIST,
    /** Represents a {@link JsonArray}. */
    ARRAY_JSON_ARRAY,
    /** Represents a custom JsonArray subtype (JAJO). */
    ARRAY_JAJO,
    /** Represents a Java array. */
    ARRAY_ARRAY,
    /** Represents a {@link Set}. */
    ARRAY_SET,

    /** Represents an unknown node type. */
    UNKNOWN;

    /**
     * Determines the {@link NodeType} of a given object.
     *
     * @param node the object to determine the type of
     * @return the corresponding NodeType enum value
     */
    public static NodeType of(Object node) {
        if (node == null) return VALUE_NULL;
        Class<?> clazz = node.getClass();
        if (node instanceof CharSequence || node instanceof Character || clazz.isEnum()) {
            return VALUE_STRING;
        } else if (node instanceof Number) {
            return VALUE_NUMBER;
        } else if (node instanceof Boolean) {
            return VALUE_BOOLEAN;
        } else if (node instanceof Map) {
            return OBJECT_MAP;
        } else if (clazz == JsonObject.class) {
            return OBJECT_JSON_OBJECT;
        } else if (node instanceof JsonObject) {
            return OBJECT_JOJO;
        } else if (node instanceof List) {
            return ARRAY_LIST;
        } else if (clazz == JsonArray.class) {
            return ARRAY_JSON_ARRAY;
        } else if (node instanceof JsonArray) {
            return ARRAY_JAJO;
        } else if (clazz.isArray()) {
            return ARRAY_ARRAY;
        } else if (node instanceof Set) {
            return ARRAY_SET;
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
        if (ti.isNodeValue()) {
            return VALUE_REGISTERED;
        } else if (ti.isPojo()) {
            return OBJECT_POJO;
        }

        return UNKNOWN;
    }

    /**
     * Determines the {@link NodeType} from a given {@link Type}.
     *
     * @param type the Type to determine the NodeType from
     * @return the corresponding NodeType enum value
     */
    public static NodeType of(Type type) {
        Class<?> clazz = Types.rawClazz(type);

        if (clazz == Object.class) {
            return UNKNOWN;
        } else if (clazz.isPrimitive()) {
            if (clazz == char.class) {
                return VALUE_STRING;
            } else if (clazz == boolean.class) {
                return VALUE_BOOLEAN;
            } else if (clazz == void.class) {
                return VALUE_NULL;
            } else {
                return VALUE_NUMBER;
            }
        } else if (CharSequence.class.isAssignableFrom(clazz) || clazz == Character.class || clazz.isEnum()) {
            return VALUE_STRING;
        } else if (Number.class.isAssignableFrom(clazz)) {
            return VALUE_NUMBER;
        } else if (clazz == Boolean.class) {
            return VALUE_BOOLEAN;
        } else if (Map.class.isAssignableFrom(clazz)) {
            return OBJECT_MAP;
        } else if (clazz == JsonObject.class) {
            return OBJECT_JSON_OBJECT;
        } else if (JsonObject.class.isAssignableFrom(clazz)) {
            return OBJECT_JOJO;
        } else if (List.class.isAssignableFrom(clazz)) {
            return ARRAY_LIST;
        } else if (clazz == JsonArray.class) {
            return ARRAY_JSON_ARRAY;
        } else if (JsonArray.class.isAssignableFrom(clazz)) {
            return ARRAY_JAJO;
        } else if (clazz.isArray()) {
            return ARRAY_ARRAY;
        } else if (Set.class.isAssignableFrom(clazz)) {
            return ARRAY_SET;
        } else if (clazz == Void.class) {
            return VALUE_NULL;
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
        if (ti.isNodeValue()) {
            return VALUE_REGISTERED;
        } else if (ti.isPojo()) {
            return OBJECT_POJO;
        }

        return UNKNOWN;
    }

    public boolean isNumber() {
        return this == VALUE_NUMBER;
    }

    public boolean isString() {
        return this == VALUE_STRING;
    }

    public boolean isBoolean() {return this == VALUE_BOOLEAN;}

    public boolean isNull() {return this == VALUE_NULL;}

    public boolean isValue() {
        return this == VALUE_STRING || this == VALUE_NUMBER || this == VALUE_BOOLEAN
                || this == VALUE_NULL || this == VALUE_REGISTERED;
    }

    public boolean isObject() {
        return this == OBJECT_MAP || this == OBJECT_JSON_OBJECT || this == OBJECT_JOJO || this == OBJECT_POJO;
    }

    public boolean isArray() {
        return this == ARRAY_LIST || this == ARRAY_JSON_ARRAY || this == ARRAY_JAJO || this == ARRAY_ARRAY
                || this == ARRAY_SET;
    }

    public boolean isContainer() {
        return isObject() || isArray();
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    public boolean isRaw() {
        return this == VALUE_STRING || this == VALUE_NUMBER || this == VALUE_BOOLEAN || this == VALUE_NULL ||
                this == OBJECT_MAP || this == ARRAY_LIST;
    }
}
