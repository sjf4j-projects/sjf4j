package org.sjf4j;

import org.sjf4j.util.TypeUtil;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages converters between wrapped Java objects and pure JSON nodes. This class
 * maintains a registry of {@link NodeConverter} instances that handle the conversion
 * between custom Java types (wrapped) and standard JSON types (pure).
 *
 * <p><b>Note:</b> This class is marked as deprecated according to the original comment.
 *
 * @deprecated This class has been marked as deprecated.
 */
public class ConverterRegistry {

//    /// Converter
//
//    /**
//     * A thread-safe cache that maps wrapped object types to their corresponding converters.
//     */
//    private static final Map<Class<?>, NodeConverter<?, ?>> CONVERTERS_CACHE = new ConcurrentHashMap<>();
//
//    /**
//     * Registers a new converter in the registry.
//     *
//     * @param converter the converter to register
//     * @throws JsonException if the converter's pure type is not a valid JSON type
//     */
//    public static void putConverter(NodeConverter<?, ?> converter) {
//        if (converter == null) throw new IllegalArgumentException("Converter must not be null");
//        if (!NodeType.of(converter.getPureType()).isPured()) {
//            throw new JsonException("Invalid ObjectConverter: pure node " + converter.getPureType() +
//                    " must be one of String/Number/Boolean/JsonObject/JsonArray");
//        }
//        CONVERTERS_CACHE.put(converter.getWrapType(), converter);
//    }
//
//    /**
//     * Clears all registered converters from the registry.
//     */
//    public static void clear() {
//        CONVERTERS_CACHE.clear();
//    }
//
//    /**
//     * Retrieves the converter associated with the specified wrapped type.
//     *
//     * @param type the wrapped object type to get the converter for
//     * @return the converter for the specified type, or null if none exists
//     */
//    public static NodeConverter<?, ?> getConverter(Class<?> type) {
//        return type == null ? null : CONVERTERS_CACHE.get(type);
//    }
//
//    /**
//     * Checks if a converter exists for the specified wrapped type.
//     *
//     * @param type the wrapped object type to check
//     * @return true if a converter exists for the type, false otherwise
//     */
//    public static boolean hasConverter(Class<?> type) {
//        return type != null && CONVERTERS_CACHE.containsKey(type);
//    }
//
//    /**
//     * Attempts to convert a wrapped object to its corresponding pure JSON node type.
//     * If no converter exists for the object's type, the object is returned as-is.
//     *
//     * @param wrap the wrapped object to convert
//     * @return the pure JSON node representation, or the original object if no converter exists
//     * @throws JsonException if conversion fails
//     */
//    @SuppressWarnings("unchecked")
//    public static Object tryWrap2Pure(Object wrap) {
//        if (wrap == null) return null;
//
//        NodeConverter<Object, Object> converter = (NodeConverter<Object, Object>)
//                ConverterRegistry.getConverter(wrap.getClass());
//        if (converter == null) {
//            return wrap;
//        }
//
//        try {
//            return converter.wrap2Pure(wrap);
//        } catch (Exception e) {
//            throw new JsonException("Failed to convert wrap node '" + wrap.getClass() +
//                    "' to pure node '" + converter.getPureType() + "'", e);
//        }
//    }
//
//    /**
//     * Attempts to convert a pure JSON node to its corresponding wrapped object type.
//     * If no converter exists for the specified type, the node is returned as-is.
//     *
//     * @param pure the pure JSON node to convert
//     * @param objectType the target wrapped object type
//     * @return the wrapped object representation, or the original node if no converter exists
//     * @throws JsonException if conversion fails
//     */
//    @SuppressWarnings("unchecked")
//    public static Object tryPure2Wrap(Object pure, Type objectType) {
//        if (objectType == null || objectType == Object.class) return pure;
//        NodeConverter<Object, Object> converter = (NodeConverter<Object, Object>)
//                ConverterRegistry.getConverter(TypeUtil.getRawClass(objectType));
//        if (converter == null) {
//            return pure;
//        }
//
//        if (pure != null && !converter.getPureType().isAssignableFrom(pure.getClass())) {
//            throw new JsonException("Converter expects wrap '" + converter.getWrapType() +
//                    "' and pure '" + converter.getPureType() + "', but got pure node '" + pure.getClass() + "'");
//        }
//
//        try {
//            return converter.pure2Wrap(pure);
//        } catch (Exception e) {
//            throw new JsonException("Failed to convert pure node '" + TypeUtil.typeName(pure) +
//                    "' to object '" + objectType + "'", e);
//        }
//    }
}