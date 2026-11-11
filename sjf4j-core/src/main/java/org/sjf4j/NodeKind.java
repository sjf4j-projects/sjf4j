package org.sjf4j;

import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.util.Asserts;
import org.sjf4j.value.ValueCodec;

import java.util.List;
import java.util.Map;
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
    /** Represents a character as a string value. */
    VALUE_STRING_CHARACTER,
    /** Represents an enum as a string value. */
    VALUE_STRING_ENUM,
    /** Represents an external string value. */
    VALUE_STRING_EXTERNAL,

    /** Represents a numeric value node. */
    VALUE_NUMBER,
    /** Represents an external numeric value. */
    VALUE_NUMBER_EXTERNAL,

    /** Represents a boolean value node. */
    VALUE_BOOLEAN,
    /** Represents an external boolean value. */
    VALUE_BOOLEAN_EXTERNAL,

    /**
     * Logical value bindings classified by their default raw encoding.
     * MAP and LIST remain atomic value nodes, not traversable object/array nodes.
     * A different RuntimeContext format may select another encoding.
     */
    VALUE_CODEC_STRING,
    VALUE_CODEC_NUMBER,
    VALUE_CODEC_BOOLEAN,
    VALUE_CODEC_MAP,
    VALUE_CODEC_LIST,

    /** Represents a {@link Map} object. */
    OBJECT_MAP,
    /** Represents a standard {@link JsonObject}. */
    OBJECT_JSON_OBJECT,
    /** Represents a custom JsonObject subtype (JOJO). */
    OBJECT_JOJO,
    /** Represents a Plain Old Java Object (POJO). */
    OBJECT_POJO,
    /** Represents an external object node. */
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
    /** Represents an external array node. */
    ARRAY_EXTERNAL,

    /** Represents an unclassified runtime representation. */
    UNKNOWN,

    /**
     * The concrete node kind is not known statically and must be
     * resolved from the runtime value.
     */
    COMPILE_TIME_UNKNOWN;


    /**
     * Classifies a runtime value, including registered codecs and external nodes.
     * The class of a non-null value may be analyzed and cached on first use.
     */
    public static NodeKind of(Object node) {
        if (node == null) return VALUE_NULL;
        Class<?> clazz = node.getClass();
        NodeKind kind = plainOf(clazz);
        if (kind != NodeKind.UNKNOWN) return kind;

        TypeInfo ti = TypeRegistry.registerTypeInfo(clazz);
        if (ti.valueSet != null) {
            switch (ti.valueSet.defaultInfo.rawJsonType) {
                case STRING: return VALUE_CODEC_STRING;
                case NUMBER: return VALUE_CODEC_NUMBER;
                case BOOLEAN: return VALUE_CODEC_BOOLEAN;
                case OBJECT: return VALUE_CODEC_MAP;
                case ARRAY: return VALUE_CODEC_LIST;
                default: throw new IllegalStateException("unsupported ValueCodec raw JSON type");
            }
        } else if (ti.oneOfInfo != null) {
            return NodeKind.UNKNOWN;
        } else if (ti.externalNode != null) {
            return ti.externalNode.nodeKind(node);
        } else if (ti.pojoInfo != null) {
            return NodeKind.OBJECT_POJO;
        }

        return NodeKind.UNKNOWN;
    }

    /**
     * Classifies a Java class without consulting registered codecs or adapters.
     * An unrecognized class returns {@link #UNKNOWN}.
     */
    public static NodeKind plainOf(Class<?> clazz) {
        Asserts.notNull(clazz, "clazz");
        if (clazz == Object.class) {
            return UNKNOWN;
        } else if (clazz == String.class) {
            return VALUE_STRING;
        } else if (clazz == Boolean.class) {
            return VALUE_BOOLEAN;
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
        } else if (Number.class.isAssignableFrom(clazz)) {
            return VALUE_NUMBER;
        } else if (Map.class.isAssignableFrom(clazz)) {
            return OBJECT_MAP;
        } else if (List.class.isAssignableFrom(clazz)) {
            return ARRAY_LIST;
        } else if (clazz == JsonObject.class) {
            return OBJECT_JSON_OBJECT;
        } else if (JsonObject.class.isAssignableFrom(clazz)) {
            return OBJECT_JOJO;
        } else if (clazz == JsonArray.class) {
            return ARRAY_JSON_ARRAY;
        } else if (JsonArray.class.isAssignableFrom(clazz)) {
            return ARRAY_JAJO;
        } else if (clazz.isArray()) {
            return ARRAY_ARRAY;
        } else if (Set.class.isAssignableFrom(clazz)) {
            return ARRAY_SET;
        } else if (clazz == Character.class || Enum.class.isAssignableFrom(clazz)) {
            return VALUE_STRING;
        } else if (clazz == Void.class) {
            return VALUE_NULL;
        }
        return UNKNOWN;
    }

    public boolean isNumber() {
        return this == VALUE_NUMBER || this == VALUE_NUMBER_EXTERNAL
                || this == VALUE_CODEC_NUMBER;
    }

    public boolean isString() {
        return this == VALUE_STRING || this == VALUE_STRING_CHARACTER
                || this == VALUE_STRING_ENUM || this == VALUE_STRING_EXTERNAL
                || this == VALUE_CODEC_STRING;
    }

    public boolean isBoolean() {
        return this == VALUE_BOOLEAN || this == VALUE_BOOLEAN_EXTERNAL
                || this == VALUE_CODEC_BOOLEAN;
    }

    public boolean isNull() {
        return this == VALUE_NULL;
    }

    public boolean isValueCodec() {
        return this == VALUE_CODEC_STRING || this == VALUE_CODEC_NUMBER
                || this == VALUE_CODEC_BOOLEAN || this == VALUE_CODEC_MAP
                || this == VALUE_CODEC_LIST;
    }

    public boolean isValue() {
        return isNumber() || isString() || isBoolean() || isNull()
                || this == VALUE_CODEC_MAP || this == VALUE_CODEC_LIST;
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
