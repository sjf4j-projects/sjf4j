package org.sjf4j;

import lombok.NonNull;
import org.sjf4j.util.TypeReference;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConverterRegistry {

    /// Converter

    private static final Map<Class<?>, ObjectConverter<?, ?>> OBJECT_CONVERTERS_CACHE = new ConcurrentHashMap<>();

    public static void putConverter(@NonNull ObjectConverter<?, ?> converter) {
        if (!NodeType.of(converter.getNodeType()).isPured()) {
            throw new JsonException("Invalid ObjectConverter: node " + converter.getNodeType() +
                    " is not one of String/Number/Boolean/JsonObject/JsonArray.)");
        }
        OBJECT_CONVERTERS_CACHE.put(converter.getObjectType(), converter);
    }

    public static void clear() {
        OBJECT_CONVERTERS_CACHE.clear();
    }

    public static ObjectConverter<?, ?> getConverter(Class<?> type) {
        return type == null ? null : OBJECT_CONVERTERS_CACHE.get(type);
    }

    public static boolean hasConverter(Class<?> type) {
        return type != null && OBJECT_CONVERTERS_CACHE.containsKey(type);
    }

    @SuppressWarnings("unchecked")
    public static Object tryObject2Node(Object object) {
        if (object == null) return null;

        ObjectConverter<Object, Object> converter = (ObjectConverter<Object, Object>)
                ConverterRegistry.getConverter(object.getClass());
        if (converter == null) {
            return object;
        }

        try {
            return converter.object2Node(object);
        } catch (Exception e) {
            throw new JsonException("Failed to convert object '" + object.getClass() +
                    "' to node '" + converter.getNodeType() + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Object tryNode2Object(Object node, @NonNull Class<?> objectType) {
        if (node == null) return null;

        ObjectConverter<Object, Object> converter = (ObjectConverter<Object, Object>)
                ConverterRegistry.getConverter(objectType);
        if (converter == null) {
            return node;
        }

        if (!converter.getNodeType().isAssignableFrom(node.getClass())) {
            throw new JsonException("Converter expects object '" + converter.getObjectType() +
                    "' and node '" + converter.getNodeType() + "', but got node '" + node.getClass() + "'");
        }

        try {
            return converter.node2Object(node);
        } catch (Exception e) {
            throw new JsonException("Failed to convert node '" + node.getClass() +
                    "' to object '" + objectType + "'", e);
        }
    }



}
