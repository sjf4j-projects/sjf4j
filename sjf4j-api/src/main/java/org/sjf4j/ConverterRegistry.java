package org.sjf4j;

import lombok.NonNull;
import org.sjf4j.util.TypeUtil;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIXME: Deprecated
 */
public class ConverterRegistry {

    /// Converter

    private static final Map<Class<?>, NodeConverter<?, ?>> CONVERTERS_CACHE = new ConcurrentHashMap<>();

    public static void putConverter(@NonNull NodeConverter<?, ?> converter) {
        if (!NodeType.of(converter.getPureType()).isPured()) {
            throw new JsonException("Invalid ObjectConverter: pure node " + converter.getPureType() +
                    " must be one of String/Number/Boolean/JsonObject/JsonArray");
        }
        CONVERTERS_CACHE.put(converter.getWrapType(), converter);
    }

    public static void clear() {
        CONVERTERS_CACHE.clear();
    }

    public static NodeConverter<?, ?> getConverter(Class<?> type) {
        return type == null ? null : CONVERTERS_CACHE.get(type);
    }

    public static boolean hasConverter(Class<?> type) {
        return type != null && CONVERTERS_CACHE.containsKey(type);
    }

    @SuppressWarnings("unchecked")
    public static Object tryWrap2Pure(Object wrap) {
        if (wrap == null) return null;

        NodeConverter<Object, Object> converter = (NodeConverter<Object, Object>)
                ConverterRegistry.getConverter(wrap.getClass());
        if (converter == null) {
            return wrap;
        }

        try {
            return converter.wrap2Pure(wrap);
        } catch (Exception e) {
            throw new JsonException("Failed to convert wrap node '" + wrap.getClass() +
                    "' to pure node '" + converter.getPureType() + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Object tryPure2Wrap(Object pure, Type objectType) {
        if (objectType == null || objectType == Object.class) return pure;
        NodeConverter<Object, Object> converter = (NodeConverter<Object, Object>)
                ConverterRegistry.getConverter(TypeUtil.getRawClass(objectType));
        if (converter == null) {
            return pure;
        }

        if (pure != null && !converter.getPureType().isAssignableFrom(pure.getClass())) {
            throw new JsonException("Converter expects wrap '" + converter.getWrapType() +
                    "' and pure '" + converter.getPureType() + "', but got pure node '" + pure.getClass() + "'");
        }

        try {
            return converter.pure2Wrap(pure);
        } catch (Exception e) {
            throw new JsonException("Failed to convert pure node '" + TypeUtil.typeName(pure) +
                    "' to object '" + objectType + "'", e);
        }
    }



}
