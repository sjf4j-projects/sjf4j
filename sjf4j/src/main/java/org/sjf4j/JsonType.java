package org.sjf4j;

import org.sjf4j.facade.FacadeNodes;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.NodeRegistry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * High-level JSON-semantic classification for values in SJF4J's OBNT model.
 * <p>
 * Unlike {@link NodeKind}, which is a lower-level runtime dispatch category,
 * {@code JsonType} answers the simpler question "what JSON type does this value
 * behave like?". Different Java representations can therefore map to the same
 * {@code JsonType}; for example {@link Map}, {@link JsonObject}, JOJO, and POJO
 * all classify as {@link #OBJECT}.
 *
 * <p>This type is used when APIs need JSON-level semantics rather than exact Java
 * container identity, especially for validation, polymorphic resolution, node
 * comparison, and shape-based decisions.
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
     * Resolves the JSON-semantic type from a low-level {@link NodeKind}.
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
     * Resolves the JSON-semantic type of a runtime OBNT value.
     */
    public static JsonType of(Object node) {
        return of(NodeKind.of(node));
    }

    /**
     * Resolves the JSON-semantic type implied by a Java class.
     * <p>
     * This method checks plain container/value classes first, then SJF4J-managed
     * types such as {@code @NodeValue}, {@code @AnyOf}, POJO, JOJO, and facade
     * node classes.
     */
    public static JsonType rawOf(Class<?> clazz) {
        NodeKind kind = NodeKind.plainOf(clazz);
        if (kind != NodeKind.UNKNOWN) return of(kind);

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
        if (ti.valueCodecInfo != null) {
            return of(NodeKind.plainOf(ti.valueCodecInfo.rawClazz));
        } else if (ti.anyOfInfo != null) {
            return JsonType.UNKNOWN;
        } else if (ti.pojoInfo != null) {
            return OBJECT;
        }

        if (FacadeNodes.isNode(clazz)) {
            return of(FacadeNodes.kindOf(clazz));
        }
        return UNKNOWN;
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
            default: throw new JsonException("Unknown JSON Schema type: " + type);
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
