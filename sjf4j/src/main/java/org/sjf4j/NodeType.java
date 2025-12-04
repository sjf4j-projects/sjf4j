package org.sjf4j;

import org.sjf4j.util.TypeUtil;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Enumeration representing the different types of JSON nodes in the sjf4j library.
 * This enum categorizes nodes into value types (void, string, number, boolean),
 * object types (JSONObject, JoJo, Map, POJO), and array types (JSONArray, List, array).
 */
public enum NodeType {
    /** Represents a null or void value. */
    VALUE_VOID,
    /** Represents a string value. */
    VALUE_STRING,
    /** Represents a numeric value. */
    VALUE_NUMBER,
    /** Represents a boolean value. */
    VALUE_BOOLEAN,
    /** Represents a standard {@link JsonObject}. */
    OBJECT_JSON_OBJECT,
    /** Represents a custom JsonObject subtype (JoJo). */
    OBJECT_JOJO,
    /** Represents a {@link Map} object. */
    OBJECT_MAP,
    /** Represents a Plain Old Java Object (POJO). */
    OBJECT_POJO,
    /** Represents a {@link JsonArray}. */
    ARRAY_JSON_ARRAY,
    /** Represents a {@link List} collection. */
    ARRAY_LIST,
    /** Represents a Java array. */
    ARRAY_ARRAY,
    /** Represents an unknown node type. */
    UNKNOWN;

    /**
     * Determines the {@link NodeType} of a given object.
     *
     * @param node the object to determine the type of
     * @return the corresponding NodeType enum value
     */
    public static NodeType of(Object node) {
        if (node == null) {
            return VALUE_VOID;
        } else if (node.getClass() == JsonObject.class) {
            return OBJECT_JSON_OBJECT;
        } else if (node instanceof JsonObject) {
            return OBJECT_JOJO;
        } else if (node instanceof JsonArray) {
            return ARRAY_JSON_ARRAY;
        } else if (node instanceof CharSequence || node instanceof Character) {
            return VALUE_STRING;
        } else if (node instanceof Number) {
            return VALUE_NUMBER;
        } else if (node instanceof Boolean) {
            return VALUE_BOOLEAN;
        } else if (node instanceof Map) {
            return OBJECT_MAP;
        } else if (node instanceof List) {
            return ARRAY_LIST;
        } else if (node.getClass().isArray()) {
            return ARRAY_ARRAY;
        } else if (PojoRegistry.isPojo(node.getClass())) {
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
        Class<?> clazz = TypeUtil.getRawClass(type);

        if (clazz.isPrimitive()) {
            if (clazz == char.class) {
                return VALUE_STRING;
            } else if (clazz == boolean.class) {
                return VALUE_BOOLEAN;
            } else if (clazz == void.class) {
                return VALUE_VOID;
            } else {
                return VALUE_NUMBER;
            }
        } else if (CharSequence.class.isAssignableFrom(clazz) || clazz == Character.class) {
            return VALUE_STRING;
        } else if (Number.class.isAssignableFrom(clazz)) {
            return VALUE_NUMBER;
        } else if (clazz == Boolean.class) {
            return VALUE_BOOLEAN;
        } else if (JsonObject.class.isAssignableFrom(clazz)) {
            return OBJECT_JOJO;
        } else if (JsonArray.class.isAssignableFrom(clazz)) {
            return ARRAY_JSON_ARRAY;
        } else if (clazz == Map.class) {
            return OBJECT_MAP;
        } else if (clazz == List.class) {
            return ARRAY_LIST;
        } else if (clazz.isArray()) {
            return ARRAY_ARRAY;
        } else if (clazz == Void.class) {
            return VALUE_VOID;
        } else if (PojoRegistry.isPojo(clazz)) {
            return OBJECT_POJO;
        }
        return UNKNOWN;
    }

    public boolean isNumber() {
        return this == VALUE_NUMBER;
    }
//    public boolean isNumberOrAny() {
//        return isNumber() || isAny();
//    }

    public boolean isValue() {
        return this == VALUE_STRING || this == VALUE_NUMBER || this == VALUE_BOOLEAN || this == VALUE_VOID;
    }
//    public boolean isValueOrAny() {
//        return isValue() || isAny();
//    }

    public boolean isObject() {
        return this == OBJECT_JSON_OBJECT || this == OBJECT_JOJO || this == OBJECT_MAP || this == OBJECT_POJO;
    }
//    public boolean isObjectOrAny() {
//        return isObject() || isAny();
//    }

    public boolean isArray() {
        return this == ARRAY_JSON_ARRAY || this == ARRAY_LIST || this == ARRAY_ARRAY;
    }
//    public boolean isArrayOrAny() {
//        return isArray() || isAny();
//    }

    public boolean isContainer() {
        return isObject() || isArray();
    }
//    public boolean isContainerOrAny() {
//        return isContainer() || isAny();
//    }

    public boolean isPured() {
        return isValue() || this == OBJECT_JOJO || this == ARRAY_JSON_ARRAY;
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }

}
