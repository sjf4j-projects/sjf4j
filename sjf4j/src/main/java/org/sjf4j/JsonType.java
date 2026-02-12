package org.sjf4j;

import org.sjf4j.node.NodeKind;

public enum JsonType {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    INTEGER, // Defined in JSON-Schema; not generated automatically
    BOOLEAN,
    NULL,
    UNKNOWN;

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
//                throw new IllegalArgumentException("Cannot resolve JsonType from NodeType '" + nodeType + "'");
        }
    }

    public static JsonType of(Object node) {
        NodeKind nodeKind = NodeKind.of(node);
        return of(nodeKind);
    }

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

    public boolean isObject() {
        return this == OBJECT;
    }
    public boolean isArray() {
        return this == ARRAY;
    }
    public boolean isValue() {
        return this == STRING || this == NUMBER || this == INTEGER || this == BOOLEAN || this == NULL;
    }
    public boolean isString() {
        return this == STRING;
    }
    public boolean isNumber() {
        return this == NUMBER || this == INTEGER;
    }
    public boolean isBoolean() {
        return this == BOOLEAN;
    }
    public boolean isNull() {
        return this == NULL;
    }
    public boolean isUnknown() {
        return this == UNKNOWN;
    }

}
