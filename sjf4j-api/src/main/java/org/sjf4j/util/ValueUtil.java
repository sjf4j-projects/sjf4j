package org.sjf4j.util;

import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.ObjectRegistry;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ValueUtil {

    /// Output

    public static String valueToString(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof CharSequence || value instanceof Character) {
            return value.toString();
        }
        throw new JsonException("Expected value type CharSequence or Character, but got " + value.getClass().getName());
    }

    public static String valueAsString(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof String || value instanceof Character) {
            return value.toString();
        }
        return value.toString();
    }

    public static Number valueToNumber(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return (Number) value;
        }
        throw new JsonException("Expected value type Number, but got " + value.getClass().getName());
    }

    public static Long valueAsLong(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsLong((Number) value);
        }
        throw new JsonException("Expected value type Number, but got " + value.getClass().getName());
    }

    public static Integer valueAsInteger(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsInteger((Number) value);
        }
        throw new JsonException("Expected value type Number, but got " + value.getClass().getName());
    }

    public static Short valueAsShort(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsShort((Number) value);
        }
        throw new JsonException("Expected value type Number, but got " + value.getClass().getName());
    }

    public static Byte valueAsByte(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsByte((Number) value);
        }
        throw new JsonException("Expected value type Number, but got " + value.getClass().getName());
    }

    public static Double valueAsDouble(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsDouble((Number) value);
        }
        throw new JsonException("Expected value type Number, but got " + value.getClass().getName());
    }

    public static Float valueAsFloat(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsFloat((Number) value);
        }
        throw new JsonException("Expected value type Number, but got " + value.getClass().getName());
    }

    public static BigInteger valueAsBigInteger(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsBigInteger((Number) value);
        }
        throw new JsonException("Expected value type Number, but got " + value.getClass().getName());
    }

    public static BigDecimal valueAsBigDecimal(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsBigDecimal((Number) value);
        }
        throw new JsonException("Expected value type Number, but got " + value.getClass().getName());
    }

    public static Boolean valueToBoolean(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new JsonException("Expected value type Boolean, but got " + value.getClass().getName());
    }

    public static JsonObject valueToJsonObject(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof JsonObject) {
            return (JsonObject) value;
        }
        throw new JsonException("Expected value type JsonObject, but got " + value.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static JsonObject valueAsJsonObject(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof JsonObject) {
            return (JsonObject) value;
        } else if (value instanceof Map) {
            return new JsonObject((Map<String, Object>) value);
        }
        throw new JsonException("Expected value type JsonObject or Map, but got " + value.getClass().getName());
    }

    public static JsonArray valueToJsonArray(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof JsonArray) {
            return (JsonArray) value;
        }
        throw new JsonException("Expected value type JsonArray, but got " + value.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static JsonArray valueAsJsonArray(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof JsonArray) {
            return (JsonArray) value;
        } else if (value instanceof List) {
            return new JsonArray((List<Object>) value);
        }
        throw new JsonException("Expected value type JsonArray or List, but got " + value.getClass().getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T valueTo(Object value, @NonNull Class<T> clazz) {
        if (value == null) {
            return null;
        } else if (clazz.isAssignableFrom(value.getClass())) {
            return (T) value;
        } else if (clazz.isPrimitive()) {
            if (clazz == boolean.class && value instanceof Boolean) {
                return (T) value;
            } else if (clazz == char.class && value instanceof Character) {
                return (T) value;
            } else if (value instanceof Number) {
                return NumberUtil.numberAs((Number) value, clazz);
            }
        } else if (clazz.isEnum()) {
            return (T) Enum.valueOf((Class<? extends Enum>) clazz, value.toString());
        }
        throw new JsonException("Type mismatch, expected " + clazz.getName() + ", but got " +
                value.getClass().getName());
    }



}
