package org.sjf4j;

import org.sjf4j.facade.FacadeNodes;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.NodeRegistry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * High-level JSON node classification used across the library.
 */
public enum JsonType {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    INTEGER, // Defined in JSON-Schema; not generated automatically
    BOOLEAN,
    NULL,
    UNKNOWN;

    /**
     * Resolves JsonType from low-level NodeKind.
     */
    public static JsonType of(NodeKind nodeKind) {
        switch (nodeKind) {
            case OBJECT_MAP:
            case OBJECT_JSON_OBJECT:
            case OBJECT_JOJO:
            case OBJECT_POJO:
            case OBJECT_FACADE:
                return OBJECT;
            case ARRAY_LIST:
            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
            case ARRAY_ARRAY:
            case ARRAY_SET:
            case ARRAY_FACADE:
                return ARRAY;
            case VALUE_STRING:
            case VALUE_STRING_CHARACTER:
            case VALUE_STRING_ENUM:
            case VALUE_STRING_FACADE:
                return STRING;
            case VALUE_NUMBER:
            case VALUE_NUMBER_FACADE:
                return NUMBER;
            case VALUE_BOOLEAN:
            case VALUE_BOOLEAN_FACADE:
                return BOOLEAN;
            case VALUE_NULL:
                return NULL;
            default:
                return UNKNOWN;
        }
    }

    /**
     * Resolves JsonType from runtime node object.
     */
    public static JsonType of(Object node) {
        return of(NodeKind.of(node));
    }

    public static JsonType rawOf(Object node) {
        NodeKind kind = NodeKind.plainOf(node);
        if (kind != NodeKind.UNKNOWN) return of(kind);

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(node.getClass());
        if (ti.valueCodecInfo != null) {
            return of(NodeKind.plainOf(ti.valueCodecInfo.getRawClazz()));
        } else if (ti.anyOfInfo != null) {
            return JsonType.UNKNOWN;
        } else if (ti.pojoInfo != null) {
            return OBJECT;
        }

        if (FacadeNodes.isNode(node)) {
            return of(FacadeNodes.kindOf(node));
        }
        return UNKNOWN;
    }

    /**
     * Resolves JsonType from JSON Schema type keyword.
     */
    public static JsonType ofSchema(String type) {
        switch (type) {
            case "object": return JsonType.OBJECT;
            case "array": return JsonType.ARRAY;
            case "string": return JsonType.STRING;
            case "number": return JsonType.NUMBER;
            case "integer": return JsonType.INTEGER;
            case "boolean": return JsonType.BOOLEAN;
            case "null": return JsonType.NULL;
            default: throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    /**
     * Returns true when this type is object.
     */
    public boolean isObject() {
        return this == OBJECT;
    }
    /**
     * Returns true when this type is array.
     */
    public boolean isArray() {
        return this == ARRAY;
    }
    /**
     * Returns true when this type is a JSON primitive/value.
     */
    public boolean isValue() {
        return this == STRING || this == NUMBER || this == INTEGER || this == BOOLEAN || this == NULL;
    }
    /**
     * Returns true when this type is string.
     */
    public boolean isString() {
        return this == STRING;
    }
    /**
     * Returns true when this type is numeric.
     */
    public boolean isNumber() {
        return this == NUMBER || this == INTEGER;
    }
    /**
     * Returns true when this type is boolean.
     */
    public boolean isBoolean() {
        return this == BOOLEAN;
    }
    /**
     * Returns true when this type is null.
     */
    public boolean isNull() {
        return this == NULL;
    }
    /**
     * Returns true when this type is unknown.
     */
    public boolean isUnknown() {
        return this == UNKNOWN;
    }

}
