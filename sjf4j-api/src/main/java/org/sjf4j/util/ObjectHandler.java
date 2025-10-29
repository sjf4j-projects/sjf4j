package org.sjf4j.util;

import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.ValueRegistry;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

public class ObjectHandler {

    /// Object to

    @SuppressWarnings("unchecked")
    public static <T> T objectTo(Object object, @NonNull Class<T> clazz) {
        if (object == null) {
            return null;
        } else if (clazz.isAssignableFrom(object.getClass())) {
            return (T) object;
        }
        throw new JsonException("Type mismatch, expected " + clazz.getName() + ", but got " +
                object.getClass().getName());
    }

    public static String objectToString(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof String || object instanceof Character) {
            return object.toString();
        }
        throw new JsonException("Unrecognized object type '" + object.getClass().getName() + "'");
    }

    public static String objectAsString(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof String || object instanceof Character) {
            return object.toString();
        }
        return object.toString();
    }

    public static Long objectToLong(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof Number) {
            return NumberHandler.numberAsLong((Number) object);
        }
        throw new JsonException("Unrecognized object type '" + object.getClass().getName() + "'");
    }

    public static Integer objectToInteger(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof Number) {
            return NumberHandler.numberAsInteger((Number) object);
        }
        throw new JsonException("Unrecognized object type '" + object.getClass().getName() + "'");
    }

    public static Short objectToShort(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof Number) {
            return NumberHandler.numberAsShort((Number) object);
        }
        throw new JsonException("Unrecognized object type '" + object.getClass().getName() + "'");
    }

    public static Byte objectToByte(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof Number) {
            return NumberHandler.numberAsByte((Number) object);
        }
        throw new JsonException("Unrecognized object type '" + object.getClass().getName() + "'");
    }

    public static Double objectToDouble(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof Number) {
            return NumberHandler.numberAsDouble((Number) object);
        }
        throw new JsonException("Unrecognized object type '" + object.getClass().getName() + "'");
    }

    public static Float objectToFloat(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof Number) {
            return NumberHandler.numberAsFloat((Number) object);
        }
        throw new JsonException("Unrecognized object type '" + object.getClass().getName() + "'");
    }

    public static BigInteger objectToBigInteger(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof Number) {
            return NumberHandler.numberAsBigInteger((Number) object);
        }
        throw new JsonException("Unrecognized object type '" + object.getClass().getName() + "'");
    }

    public static BigDecimal objectToBigDecimal(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof Number) {
            return NumberHandler.numberAsBigDecimal((Number) object);
        }
        throw new JsonException("Unrecognized object type '" + object.getClass().getName() + "'");
    }

    public static Boolean objectToBoolean(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof Boolean) {
            return (Boolean) object;
        }
        throw new JsonException("Unrecognized object type '" + object.getClass().getName() + "'");
    }

    public static JsonObject objectToJsonObject(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof JsonObject) {
            return (JsonObject) object;
        }
        throw new JsonException("Unrecognized object type '" + object.getClass().getName() + "'");
    }

    public static JsonArray objectToJsonArray(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof JsonArray) {
            return (JsonArray) object;
        }
        throw new JsonException("Unrecognized object type '" + object.getClass().getName() + "'");
    }


    /// To object

    @SuppressWarnings("unchecked")
    public static Object checkAndWrap(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof JsonObject || object instanceof JsonArray || object instanceof String ||
                    object instanceof Character || object instanceof Number || object instanceof Boolean) {
            return object;
        } else if (object instanceof JsonObject.Builder) {
            return ((JsonObject.Builder) object).build();
        } else if (object instanceof Map) {
            return new JsonObject((Map<String, ?>) object);
        } else if (object instanceof Collection) {
            return new JsonArray((Collection<?>) object);
        } else{
            Class<?> clazz = object.getClass();
            if (ValueRegistry.hasConverter(clazz)) {
                return object;
            } else if (clazz.isArray()) {
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
            }
        }
        throw new IllegalStateException("Unsupported type '" + object.getClass().getName() +
                "', expected one of [JsonObject, JsonArray, String, Number, Boolean] or a type registered in " +
                "ValueRegistry, or a Map/Collection/Array of such elements.");
    }


}
