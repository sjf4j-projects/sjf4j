package org.sjf4j;

import org.sjf4j.node.NodeType;

public enum JsonType {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    INTEGER, // Defined in JSON-Schema
    BOOLEAN,
    NULL,
    UNKNOWN;

    public static JsonType of(NodeType nodeType) {
        switch (nodeType) {
            case OBJECT_MAP:
            case OBJECT_JSON_OBJECT:
            case OBJECT_JOJO:
            case OBJECT_POJO:
                return OBJECT;
            case ARRAY_LIST:
            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
            case ARRAY_ARRAY:
                return ARRAY;
            case VALUE_STRING:
                return STRING;
            case VALUE_NUMBER:
                return NUMBER;
            case VALUE_BOOLEAN:
                return BOOLEAN;
            case VALUE_NULL:
                return NULL;
            default:
                return UNKNOWN;
//                throw new IllegalArgumentException("Cannot resolve JsonType from NodeType '" + nodeType + "'");
        }
    }

    public static JsonType of(Object node) {
        NodeType nodeType = NodeType.of(node);
        return of(nodeType);
    }

    public static JsonType from(String type) {
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
}
