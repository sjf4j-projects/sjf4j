package org.sjf4j;

import org.sjf4j.exception.NodeException;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.meta.TypeInfo;

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
     * Resolves the logical JSON shape from a {@link NodeKind}.
     * Codec-backed Map/List values are atomic leaves (UNKNOWN), not OBJECT/ARRAY.
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
            case VALUE_CODEC_STRING:
                return STRING;
            case VALUE_NUMBER:
            case VALUE_NUMBER_EXTERNAL:
            case VALUE_CODEC_NUMBER:
                return NUMBER;
            case VALUE_BOOLEAN:
            case VALUE_BOOLEAN_EXTERNAL:
            case VALUE_CODEC_BOOLEAN:
                return BOOLEAN;
            case VALUE_NULL:
                return NULL;
            default:
                return UNKNOWN;
        }
    }

    /**
     * Resolves the default logical JSON shape of an OBNT value, without
     * executing a codec or inspecting RuntimeContext.
     * Use {@link #rawOf(Class)} for the default raw encoding shape.
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
        if (ti.valueSet != null) {
            return of(NodeKind.plainOf(ti.valueSet.defaultInfo.rawClazz));
        } else if (ti.oneOfInfo != null) {
            return JsonType.UNKNOWN;
        } else if (ti.externalNode != null) {
            return ti.externalNode.jsonTypeOfClass(clazz);
        } else if (ti.pojoInfo != null) {
            return OBJECT;
        }

        return UNKNOWN;
    }

    /**
     * Resolves {@link JsonType} from a JSON Schema {@code type} keyword.
     * The {@code integer} schema type is distinct from {@code number}, even
     * though ordinary numeric Java values classify as {@link #NUMBER}.
     *
     * @throws NodeException if the keyword is not a recognized JSON Schema type
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
            default: throw new NodeException("unknown JSON Schema type '" + type + "'");
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
     * Returns true when this type is a scalar JSON shape, including null.
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
