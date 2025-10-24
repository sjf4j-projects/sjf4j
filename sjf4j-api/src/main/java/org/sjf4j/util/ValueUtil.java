package org.sjf4j.util;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

public class ValueUtil {

    /// Value to

    public static String valueToString(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof String) {
            return (String) value;
        }
        throw new JsonException("Unrecognized value type '" + value.getClass().getName() + "'");
    }

    public static String valueAsString(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }

    public static Long valueToLong(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsLong((Number) value);
        }
        throw new JsonException("Unrecognized value type '" + value.getClass().getName() + "'");
    }

    public static Integer valueToInteger(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsInteger((Number) value);
        }
        throw new JsonException("Unrecognized value type '" + value.getClass().getName() + "'");
    }

    public static Short valueToShort(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsShort((Number) value);
        }
        throw new JsonException("Unrecognized value type '" + value.getClass().getName() + "'");
    }

    public static Byte valueToByte(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsByte((Number) value);
        }
        throw new JsonException("Unrecognized value type '" + value.getClass().getName() + "'");
    }

    public static Double valueToDouble(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsDouble((Number) value);
        }
        throw new JsonException("Unrecognized value type '" + value.getClass().getName() + "'");
    }

    public static Float valueToFloat(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsFloat((Number) value);
        }
        throw new JsonException("Unrecognized value type '" + value.getClass().getName() + "'");
    }

    public static BigInteger valueToBigInteger(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsBigInteger((Number) value);
        }
        throw new JsonException("Unrecognized value type '" + value.getClass().getName() + "'");
    }

    public static BigDecimal valueToBigDecimal(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return NumberUtil.numberAsBigDecimal((Number) value);
        }
        throw new JsonException("Unrecognized value type '" + value.getClass().getName() + "'");
    }

    public static Boolean valueToBoolean(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new JsonException("Unrecognized value type '" + value.getClass().getName() + "'");
    }

    public static JsonObject valueToJsonObject(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof JsonObject) {
            return (JsonObject) value;
        }
        throw new JsonException("Unrecognized value type '" + value.getClass().getName() + "'");
    }

    public static JsonArray valueToJsonArray(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof JsonArray) {
            return (JsonArray) value;
        }
        throw new JsonException("Unrecognized value type '" + value.getClass().getName() + "'");
    }




    /// To value

    @SuppressWarnings("unchecked")
    public static Object objectToValue(Object object) {
        if (!JsonType.of(object).isUnknown()) {
            return object; // include null
        } else if (object instanceof JsonObject.Builder) {
            return ((JsonObject.Builder) object).build();
        } else if (object instanceof Map) {
            return new JsonObject((Map<String, ?>) object);
        } else if (object instanceof Collection) {
            return new JsonArray((Collection<?>) object);
        } else if (object.getClass().isArray()) {
            if (object instanceof Object[]) {
                return new JsonArray((Object[]) object);
            } else {
                // int[] double[] ...
                int length = Array.getLength(object);
                Object[] vv = new Object[length];
                for (int i = 0; i < length; i++) {
                    vv[i] = Array.get(object, i);
                }
                return new JsonArray(vv);
            }
        } else if (object instanceof Character) {
            return String.valueOf(object);
        }
        throw new IllegalArgumentException("Unsupported object type '" + object.getClass().getName() + "'");
    }


}
