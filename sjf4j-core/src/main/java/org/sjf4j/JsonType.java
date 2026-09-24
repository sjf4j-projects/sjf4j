package org.sjf4j;

import org.sjf4j.exception.NodeException;
import org.sjf4j.facade.FacadeNodes;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.TypeInfo;

import java.util.Map;

/**
 * JSON-semantic shape of an OBNT value.
 * <p>
 * Different Java representations can have the same shape; for example
 * {@link Map}, {@link JsonObject}, JOJO, and POJO all have {@link #OBJECT}
 * shape. Unlike {@link NodeKind}, this type is for object, array, and value
 * node shape decisions rather than runtime representation dispatch.
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
     * Resolves the JSON-semantic shape from a {@link NodeKind}.
     * <p>
     * {@link NodeKind#VALUE_NODE_VALUE} resolves to {@link #UNKNOWN} because the
     * configured value binding's raw OBNT representation is not encoded in {@code NodeKind}.
     */
    public static JsonType of(NodeKind nodeKind) {
        switch (nodeKind) {
            case OBJECT_MAP:
            case OBJECT_JSON_OBJECT:
            case OBJECT_JOJO:
            case OBJECT_POJO:
            case OBJECT_EXTERNAL:
                return OBJECT;
            case ARRAY_LIST:
            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
            case ARRAY_ARRAY:
            case ARRAY_SET:
            case ARRAY_EXTERNAL:
                return ARRAY;
            case VALUE_STRING:
            case VALUE_STRING_CHARACTER:
            case VALUE_STRING_ENUM:
            case VALUE_STRING_EXTERNAL:
                return STRING;
            case VALUE_NUMBER:
            case VALUE_NUMBER_EXTERNAL:
                return NUMBER;
            case VALUE_BOOLEAN:
            case VALUE_BOOLEAN_EXTERNAL:
                return BOOLEAN;
            case VALUE_NULL:
                return NULL;
            default:
                return UNKNOWN;
        }
    }

    /**
     * Resolves the JSON-semantic shape of an OBNT value.
     * <p>
     * A {@code @NodeValue} instance resolves to {@link #UNKNOWN}; use
     * {@link #rawOf(Class)} to classify its configured raw representation.
     */
    public static JsonType of(Object node) {
        return of(NodeKind.of(node));
    }

    /**
     * Resolves the JSON-semantic type implied by a Java class.
     * <p>
     * This method checks plain object, array, and value representations first,
     * then SJF4J-managed types. For {@code @NodeValue}, the configured value
     * binding's raw OBNT representation determines the shape.
     */
    public static JsonType rawOf(Class<?> clazz) {
        NodeKind kind = NodeKind.plainOf(clazz);
        if (kind != NodeKind.UNKNOWN) return of(kind);

        TypeInfo ti = TypeRegistry.registerTypeInfo(clazz);
        if (ti.valueInfos != null) {
            return of(NodeKind.plainOf(ti.valueInfos[0].rawClazz));
        } else if (ti.oneOfInfo != null) {
            return JsonType.UNKNOWN;
        } else if (ti.externalNode != null) {
            return _externalRawOf(ti.externalNode, clazz);
        } else if (ti.pojoInfo != null) {
            return OBJECT;
        }

        if (FacadeNodes.isNode(clazz)) {
            return of(FacadeNodes.kindOf(clazz));
        }
        return UNKNOWN;
    }

    @SuppressWarnings("unchecked")
    private static JsonType _externalRawOf(ExternalNode<?> externalNode, Class<?> clazz) {
        return ((ExternalNode<Object>) externalNode).jsonTypeOfClass(clazz);
    }

    /**
     * Resolves {@link JsonType} from a JSON Schema {@code type} keyword.
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
            default: throw new NodeException("Unknown JSON Schema type: " + type);
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
