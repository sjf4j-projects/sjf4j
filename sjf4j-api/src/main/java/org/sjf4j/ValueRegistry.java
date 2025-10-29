package org.sjf4j;

import lombok.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ValueRegistry {

    private static final Map<Class<?>, ValueConverter> CONVERTERS_CACHE = new ConcurrentHashMap<>();


    public static ValueConverter getConverter(@NonNull Class<?> clazz) {
        return CONVERTERS_CACHE.get(clazz);
    }

    public static boolean hasConverter(Class<?> clazz) {
        return CONVERTERS_CACHE.containsKey(clazz);
    }

    public static void putConverter(@NonNull Class<?> clazz, @NonNull ValueConverter converter) {
        if (ValueType.of(converter.getValueClass()).isValue()) {
            CONVERTERS_CACHE.put(clazz, converter);
        } else {
            throw new JsonException("Invalid ValueConverter: value type " + converter.getValueClass().getName() +
                    " is not one of String/Number/Boolean.)");
        }
    }

    public static void clear() {
        CONVERTERS_CACHE.clear();
    }


}
