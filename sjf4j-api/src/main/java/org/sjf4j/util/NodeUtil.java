package org.sjf4j.util;

import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeType;
import org.sjf4j.PojoRegistry;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class NodeUtil {

    /// Output

    public static String toString(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof CharSequence || node instanceof Character) {
            return node.toString();
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).name();
        }
        throw new JsonException("Expected node type CharSequence or Character, but got " + node.getClass());
    }

    public static String asString(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof CharSequence || node instanceof Character) {
            return node.toString();
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).name();
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

    public static Number asNumber(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return (Number) node;
        } else if (node instanceof CharSequence || node instanceof Character) {
            String str = node.toString();
            if (NumberUtil.isNumeric(str)) {
                return NumberUtil.toNumber(node.toString());
            }
            throw new JsonException("Cannot convert String '" + node + "' to Number: not a number");
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).ordinal();
        }
        throw new JsonException("Cannot convert " + node.getClass().getName() + " '" + node +
                "' to Number: unsupported type");
    }


    public static Long toLong(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asLong((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Long asLong(Object node) {
        return NumberUtil.asLong(asNumber(node));
    }

    public static Integer toInteger(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asInteger((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Integer asInteger(Object node) {
        return NumberUtil.asInteger(asNumber(node));
    }

    public static Short toShort(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asShort((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Short asShort(Object node) {
        return NumberUtil.asShort(asNumber(node));
    }

    public static Byte toByte(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asByte((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Byte asByte(Object node) {
        return NumberUtil.asByte(asNumber(node));
    }

    public static Double toDouble(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asDouble((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Double asDouble(Object node) {
        return NumberUtil.asDouble(asNumber(node));
    }

    public static Float toFloat(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asFloat((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Float asFloat(Object node) {
        return NumberUtil.asFloat(asNumber(node));
    }

    public static BigInteger toBigInteger(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asBigInteger((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static BigInteger asBigInteger(Object node) {
        return NumberUtil.asBigInteger(asNumber(node));
    }

    public static BigDecimal toBigDecimal(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asBigDecimal((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static BigDecimal asBigDecimal(Object node) {
        return NumberUtil.asBigDecimal(asNumber(node));
    }

    public static Boolean toBoolean(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Boolean) {
            return (Boolean) node;
        }
        throw new JsonException("Expected type Boolean, but got " + node.getClass().getName());
    }

    public static Boolean asBoolean(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Boolean) {
            return (Boolean) node;
        } else if (node instanceof String) {
            String str = ((String) node).toLowerCase();
            if ("true".equals(str) || "yes".equals(str) || "on".equals(str) || "1".equals(str)) return true;
            if ("false".equals(str) || "no".equals(str) || "off".equals(str) || "0".equals(str)) return false;
            throw new JsonException("Cannot convert String '" + node +
                    "' to Boolean: supported formats: true/false, yes/no, on/off, 1/0");
        } else if (node instanceof Number) {
            int i = ((Number) node).intValue();
            if (i == 1) return true;
            if (i == 0) return false;
            throw new JsonException("Cannot convert Number '" + node +
                    "' to Boolean: numeric values other than 0 or 1");
        }
        throw new JsonException("Cannot convert " + node.getClass().getName() + " '" + node +
                "' to Boolean: unsupported type");
    }

    public static JsonObject toJsonObject(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonObject) {
            return (JsonObject) node;
        }
        throw new JsonException("Expected node type JsonObject, but got " + node.getClass().getName());
    }

    public static JsonObject asJsonObject(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonObject) {
            return (JsonObject) node;
        }
        return new JsonObject(node);
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
    public static JsonArray asJsonArray(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonArray) {
            return (JsonArray) node;
        }
        return new JsonArray(node);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T to(Object node, @NonNull Class<T> clazz) {
        if (node == null) {
            return null;
        } else if (clazz.isInstance(node)) {
            return (T) node;
        } else if (clazz == JsonObject.class) {
            return (T) toJsonObject(node);
        } else if (clazz == JsonArray.class) {
            return (T) toJsonArray(node);
        } else if (clazz == String.class) {
            return (T) toString(node);
        } else if (clazz == Boolean.class) {
            return (T) toBoolean(node);
        } else if (Number.class.isAssignableFrom(clazz)) {
            Number n = toNumber(node);
            return NumberUtil.as(n, clazz);
        } else if (clazz.isPrimitive()) {
            if (clazz == boolean.class) {
                return (T) toBoolean(node);
            } else if (clazz == char.class && node instanceof Character) {
                return (T) node;
            } else {
                Number n = toNumber(node);
                return NumberUtil.as(n, clazz);
            }
        } else if (clazz.isEnum()) {
            return (T) Enum.valueOf((Class<? extends Enum>) clazz, toString(node));
        }
        throw new JsonException("Type mismatch, expected " + clazz.getName() + ", but got " +
                node.getClass().getName());
    }


    /// Only support JsonObject/JsonArray, but not POJO/Map/List
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T as(Object node, @NonNull Class<T> clazz) {
        if (node == null) {
            return null;
        } else if (clazz.isInstance(node)) {
            return (T) node;
        } else if (clazz == JsonObject.class) {
            return (T) asJsonObject(node);
        } else if (clazz == JsonArray.class) {
            return (T) asJsonArray(node);
        } else if (clazz == String.class) {
            return (T) asString(node);
        } else if (clazz == Boolean.class) {
            return (T) asBoolean(node);
        } else if (Number.class.isAssignableFrom(clazz)) {
            Number n = asNumber(node);
            return NumberUtil.as(n, clazz);
        } else if (clazz.isPrimitive()) {
            if (clazz == boolean.class) {
                return (T) asBoolean(node);
            } else if (clazz == char.class && node instanceof Character) {
                return (T) (Character) asString(node).charAt(0);
            } else {
                Number n = asNumber(node);
                return NumberUtil.as(n, clazz);
            }
        } else if (clazz.isEnum()) {
            return (T) Enum.valueOf((Class<? extends Enum>) clazz, asString(node));
        }
        throw new JsonException("Cannot convert " + node.getClass().getName()  +
                " to " + clazz.getName() + ": unsupported type");
    }



}
