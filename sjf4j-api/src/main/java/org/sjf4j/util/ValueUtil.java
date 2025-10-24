package org.sjf4j.util;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

public class ValueUtil {


    @SuppressWarnings("unchecked")
    public static Object convertToJsonValue(Object value) {
        if (!JsonType.of(value).isUnknown()) {
            return value; // include null
        } else if (value instanceof JsonObject.Builder) {
            return ((JsonObject.Builder) value).build();
        } else if (value instanceof Map) {
            return new JsonObject((Map<String, ?>) value);
        } else if (value instanceof Collection) {
            return new JsonArray((Collection<?>) value);
        } else if (value.getClass().isArray()) {
            if (value instanceof Object[]) {
                return new JsonArray((Object[]) value);
            } else {
                // int[] double[] ...
                int length = Array.getLength(value);
                Object[] vv = new Object[length];
                for (int i = 0; i < length; i++) {
                    vv[i] = Array.get(value, i);
                }
                return new JsonArray(vv);
            }
        } else if (value instanceof Character) {
            return String.valueOf(value);
        }
        throw new IllegalArgumentException("Unsupported value type '" + value.getClass().getName() + "'");
    }

}
