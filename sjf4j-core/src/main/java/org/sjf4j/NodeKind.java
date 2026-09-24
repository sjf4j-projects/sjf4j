package org.sjf4j;

import org.sjf4j.facade.FacadeNodes;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.TypeInfo;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.util.Asserts;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime representation and dispatch classification for OBNT values.
 * <p>
 * Each value identifies the Java representation used at runtime, such as a
 * {@link Map} object node, a {@link List} array node, or a POJO. Use
 * {@link JsonType} for JSON-semantic shape decisions rather than this
 * representation classification.
 */
public enum NodeKind {
    /** Represents a null value node. */
    VALUE_NULL,

    /** Represents a string value node. */
    VALUE_STRING,
    VALUE_STRING_CHARACTER,
    VALUE_STRING_ENUM,
    VALUE_STRING_EXTERNAL,

    /** Represents a numeric value node. */
    VALUE_NUMBER,
    VALUE_NUMBER_EXTERNAL,

    /** Represents a boolean value node. */
    VALUE_BOOLEAN,
    VALUE_BOOLEAN_EXTERNAL,

    /** Represents a {@code @NodeValue} logical value node with a configured value binding. */
    VALUE_NODE_VALUE,

    /** Represents a {@link Map} object. */
    OBJECT_MAP,
    /** Represents a standard {@link JsonObject}. */
    OBJECT_JSON_OBJECT,
    /** Represents a custom JsonObject subtype (JOJO). */
    OBJECT_JOJO,
    /** Represents a Plain Old Java Object (POJO). */
    OBJECT_POJO,
    OBJECT_EXTERNAL,

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
    ARRAY_EXTERNAL,

    /** Represents an unclassified runtime representation. */
    UNKNOWN,

    /**
     * The concrete node kind is not known statically and must be
     * resolved from the runtime value.
     */
    COMPILE_TIME_UNKNOWN;

    @SuppressWarnings("unchecked")
    public static NodeKind of(Object node) {
        if (node == null) return VALUE_NULL;
        Class<?> clazz = node.getClass();
        NodeKind kind = plainOf(clazz);
        if (kind != NodeKind.UNKNOWN) return kind;

        TypeInfo ti = TypeRegistry.registerTypeInfo(clazz);
        if (ti.valueInfos != null) {
            return NodeKind.VALUE_NODE_VALUE;
        } else if (ti.oneOfInfo != null) {
            return NodeKind.UNKNOWN;
        } else if (ti.externalNode != null) {
            return ((ExternalNode<Object>)ti.externalNode).nodeKind(node);
        } else if (ti.pojoInfo != null) {
            return NodeKind.OBJECT_POJO;
        }

        if (FacadeNodes.isNode(node)) {
            return FacadeNodes.kindOf(node);
        }

        return NodeKind.UNKNOWN;
    }

    public static NodeKind plainOf(Class<?> clazz) {
        Asserts.notNull(clazz, "clazz");
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

    public boolean isNumber() {
        return this == VALUE_NUMBER || this == VALUE_NUMBER_EXTERNAL;
    }

    public boolean isString() {
        return this == VALUE_STRING || this == VALUE_STRING_CHARACTER
                || this == VALUE_STRING_ENUM || this == VALUE_STRING_EXTERNAL;
    }

    public boolean isBoolean() {
        return this == VALUE_BOOLEAN || this == VALUE_BOOLEAN_EXTERNAL;
    }

    public boolean isNull() {
        return this == VALUE_NULL;
    }

    public boolean isValue() {
        return isNumber() || isString() || isBoolean() || isNull()
                || this == VALUE_NODE_VALUE;
    }

    public boolean isObject() {
        return this == OBJECT_MAP || this == OBJECT_JSON_OBJECT
                || this == OBJECT_JOJO || this == OBJECT_POJO
                || this == OBJECT_EXTERNAL;
    }

    public boolean isArray() {
        return this == ARRAY_LIST || this == ARRAY_JSON_ARRAY
                || this == ARRAY_JAJO || this == ARRAY_ARRAY
                || this == ARRAY_SET || this == ARRAY_EXTERNAL;
    }

    public boolean isContainer() {
        return isObject() || isArray();
    }

    /**
     * Returns true for unknown/unclassified kind.
     */
    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    /**
     * Returns true for raw codec representations: scalar values, {@link Map}, or
     * {@link List}.
     */
    public boolean isRaw() {
        return this == VALUE_STRING || this == VALUE_NUMBER || this == VALUE_BOOLEAN || this == VALUE_NULL ||
                this == OBJECT_MAP || this == ARRAY_LIST;
    }

}
