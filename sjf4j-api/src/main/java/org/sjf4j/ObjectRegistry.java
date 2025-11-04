package org.sjf4j;

import lombok.NonNull;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ObjectRegistry {

    /// Converter

    private static final Map<Type, ObjectConverter> OBJECT_CONVERTERS_CACHE = new ConcurrentHashMap<>();

    public static void putConverter(@NonNull Type type, @NonNull ObjectConverter converter) {
        if (ValueType.of(converter.getValueType()).isUnknown()) {
            throw new JsonException("Invalid ValueConverter: value " + converter.getValueType() +
                    " is unknown (not one of JsonObject/JsonArray/String/Number/Boolean.)");
        }
        OBJECT_CONVERTERS_CACHE.put(type, converter);
    }

    public static void clear() {
        OBJECT_CONVERTERS_CACHE.clear();
    }

    public static ObjectConverter getConverter(Type type) {
        return type == null ? null : OBJECT_CONVERTERS_CACHE.get(type);
    }

    public static boolean hasConverter(Type type) {
        return type != null && OBJECT_CONVERTERS_CACHE.containsKey(type);
    }

    public static Object tryObject2Value(Object object) {
        if (object == null) return null;

        ObjectConverter converter = ObjectRegistry.getConverter(object.getClass());
        if (converter == null) {
            return object;
        }

        try {
            return converter.object2Value(object);
        } catch (Exception e) {
            throw new JsonException("Failed to convert object '" + object.getClass() +
                    "' to value '" + converter.getValueType() + "'", e);
        }
    }


    public static Object tryValue2Object(Object value, Type objectType) {
        if (value == null) return null;

        ObjectConverter converter = ObjectRegistry.getConverter(objectType);
        if (converter == null) {
            return value;
        }
        Class<?> rawType = getRawClass(converter.getValueType());
        if (!rawType.isAssignableFrom(value.getClass())) {
            throw new JsonException("Expected converter object '" + converter.getObjectType() +
                    "' and value '" + converter.getValueType() + "', but got value '" +
                    value.getClass() + "'");
        }

        try {
            return converter.value2Object(value);
        } catch (Exception e) {
            throw new JsonException("Failed to convert value '" + value.getClass() +
                    "' to object '" + objectType + "'", e);
        }
    }


    /// private

    private static Class<?> getRawClass(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        } else if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            Class<?> rawComponent = getRawClass(componentType);
            return Array.newInstance(rawComponent, 0).getClass();
        } else {
            throw new IllegalArgumentException("Cannot get raw class from type: " + type);
        }
    }

}
