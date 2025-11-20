package org.sjf4j.util;

import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.PojoRegistry;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public class NodeUtil {

    /// Output

    public static String toString(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof CharSequence || node instanceof Character || node.getClass().isEnum()) {
            return node.toString();
        }
        throw new JsonException("Expected node type CharSequence or Character, but got " + node.getClass());
    }

    public static String asString(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof String || node instanceof Character) {
            return node.toString();
        }
        return node.toString();
    }

    public static Number toNumber(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return (Number) node;
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass());
    }

    public static Long asLong(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsLong((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Integer asInteger(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsInteger((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Short asShort(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsShort((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Byte asByte(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsByte((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Double asDouble(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsDouble((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Float asFloat(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsFloat((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static BigInteger asBigInteger(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsBigInteger((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static BigDecimal asBigDecimal(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsBigDecimal((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Boolean toBoolean(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Boolean) {
            return (Boolean) node;
        }
        throw new JsonException("Expected node type Boolean, but got " + node.getClass().getName());
    }

    public static JsonObject toJsonObject(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonObject) {
            return (JsonObject) node;
        }
        throw new JsonException("Expected node type JsonObject, but got " + node.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static JsonObject asJsonObject(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonObject) {
            return (JsonObject) node;
        } else if (node instanceof Map) {
            return new JsonObject((Map<String, Object>) node);
        } else if (PojoRegistry.isPojo(node.getClass())) {
            //fixme

        }
        throw new JsonException("Expected node type JsonObject/Map/POJO, but got " + node.getClass().getName());
    }

    public static JsonArray toJsonArray(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonArray) {
            return (JsonArray) node;
        }
        throw new JsonException("Expected node type JsonArray, but got " + node.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static JsonArray asJsonArray(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof JsonArray) {
            return (JsonArray) value;
        } else if (value instanceof List) {
            return new JsonArray((List<Object>) value);
        } else if (value.getClass().isArray()) {
            return new JsonArray(value);
        }
        throw new JsonException("Expected node type JsonArray/List/Array, but got " + value.getClass().getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T to(Object node, @NonNull Class<T> clazz) {
        if (node == null) {
            return null;
        } else if (clazz.isAssignableFrom(node.getClass())) {
            return (T) node;
        } else if (Number.class.isAssignableFrom(clazz)) {
            if (node instanceof Number) {
                return NumberUtil.numberAs((Number) node, clazz);
            }
        } else if (clazz.isPrimitive()) {
            if (clazz == boolean.class && node instanceof Boolean) {
                return (T) node;
            } else if (clazz == char.class && node instanceof Character) {
                return (T) node;
            } else if (node instanceof Number) {
                return NumberUtil.numberAs((Number) node, clazz);
            }
        } else if (clazz.isEnum()) {
            return (T) Enum.valueOf((Class<? extends Enum>) clazz, node.toString());
        }
        throw new JsonException("Type mismatch, expected " + clazz.getName() + ", but got " +
                node.getClass().getName());
    }



}
