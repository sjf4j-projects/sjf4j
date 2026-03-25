package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.facade.FacadeNodes;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Low-level classification of OBNT node shapes and SJF4J-managed Java types.
 * <p>
 * {@code NodeKind} is mainly an internal dispatch enum, but it also explains how
 * SJF4J groups values at runtime: scalar values, object-like nodes, array-like
 * nodes, facade-native nodes, and structurally managed Java types such as JOJO,
 * JAJO, POJO, and {@code @NodeValue} types.
 */
public enum NodeKind {
    /** Represents a null or void value. */
    VALUE_NULL,
    /** Represents a string value. */
    VALUE_STRING,
    VALUE_STRING_CHARACTER,
    VALUE_STRING_ENUM,
    VALUE_STRING_FACADE,
    /** Represents a numeric value. */
    VALUE_NUMBER,
    VALUE_NUMBER_FACADE,
    /** Represents a boolean value. */
    VALUE_BOOLEAN,
    VALUE_BOOLEAN_FACADE,
    /** Represents a registered @NodeValue with custom codec. */
    VALUE_NODE_VALUE,

    /** Represents a {@link Map} object. */
    OBJECT_MAP,
    /** Represents a standard {@link JsonObject}. */
    OBJECT_JSON_OBJECT,
    /** Represents a custom JsonObject subtype (JOJO). */
    OBJECT_JOJO,
    /** Represents a Plain Old Java Object (POJO). */
    OBJECT_POJO,
    OBJECT_FACADE,

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
    ARRAY_FACADE,

    /** Represents an unknown node type. */
    UNKNOWN;

    public static NodeKind of(Object node) {
        if (node == null) return VALUE_NULL;
        Class<? extends Object> clazz = node.getClass();
        NodeKind kind = plainOf(clazz);
        if (kind != NodeKind.UNKNOWN) return kind;

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
        if (ti.valueCodecInfo != null) {
            return NodeKind.VALUE_NODE_VALUE;
        } else if (ti.anyOfInfo != null) {
            return NodeKind.UNKNOWN;
        } else if (ti.pojoInfo != null) {
            return NodeKind.OBJECT_POJO;
        }

        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.kindOf(node);
        }
        return NodeKind.UNKNOWN;
    }

    /**
     * Determines the {@link NodeKind} of a given object.
     *
     * @param node the object to determine the type of
     * @return the corresponding NodeType enum value
     */
    public static NodeKind plainOf(Object node) {
        if (node == null) return VALUE_NULL;
        Class<?> clazz = node.getClass();
        return plainOf(clazz);
    }


    public static NodeKind plainOf(Class<?> clazz) {
        Objects.requireNonNull(clazz);
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
        } else if (clazz == String.class || clazz == Character.class || clazz.isEnum()) {
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
        return UNKNOWN;
    }

//    public boolean isNumber() {
//        return this == VALUE_NUMBER;
//    }
//
//    public boolean isString() {
//        return this == VALUE_STRING;
//    }
//
//    public boolean isBoolean() {return this == VALUE_BOOLEAN;}
//
//    public boolean isNull() {return this == VALUE_NULL;}
//
//    public boolean isValue() {
//        return this == VALUE_STRING || this == VALUE_NUMBER || this == VALUE_BOOLEAN
//                || this == VALUE_NULL || this == VALUE_REGISTERED;
//    }
//
//    public boolean isObject() {
//        return this == OBJECT_MAP || this == OBJECT_JSON_OBJECT || this == OBJECT_JOJO || this == OBJECT_POJO;
//    }
//
//    public boolean isArray() {
//        return this == ARRAY_LIST || this == ARRAY_JSON_ARRAY || this == ARRAY_JAJO || this == ARRAY_ARRAY
//                || this == ARRAY_SET;
//    }
//
//    public boolean isContainer() {
//        return isObject() || isArray();
//    }

    /**
     * Returns true for unknown/unclassified kind.
     */
    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    /**
     * Returns true for raw JSON-compatible kinds.
     */
    public boolean isRaw() {
        return this == VALUE_STRING || this == VALUE_NUMBER || this == VALUE_BOOLEAN || this == VALUE_NULL ||
                this == OBJECT_MAP || this == ARRAY_LIST;
    }

}
