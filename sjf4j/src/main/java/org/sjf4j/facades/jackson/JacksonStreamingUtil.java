package org.sjf4j.facades.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeConverter;
import org.sjf4j.PojoRegistry;
import org.sjf4j.facades.FacadeReader;
import org.sjf4j.util.StreamingUtil;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A Jackson-specific, fully static implementation of the streaming JSON parser utilities.
 * <p>
 * This class is a specialized and performance-optimized version of {@link StreamingUtil},
 * designed to maximize JIT inlining and reduce dynamic dispatch overhead.
 * <p>
 * Unlike {@code StreamingUtil}, this implementation:
 * <ul>
 *   <li>Removes all polymorphism and interface indirection (no {@code FacadeReader} abstraction).</li>
 *   <li>Uses only {@code static} methods for direct JIT inlining.</li>
 *   <li>Avoids reflection or dynamic lookups in the parsing hot path.</li>
 * </ul>
 * <p>
 * In practice, this allows the JVM JIT compiler to aggressively inline calls,
 * perform constant folding, and optimize the parser loops specifically for the original API,
 * achieving lower per-call latency compared to the generic {@code StreamingUtil}.
 */
public class JacksonStreamingUtil {

    /// Read

    public static Object readNode(JsonParser parser, Type type) throws IOException {
        if (parser == null) throw new IllegalArgumentException("Parser must not be null");
        int tid = peekTokenId(parser);
        switch (tid) {
            case FacadeReader.ID_START_OBJECT:
                return readObject(parser, type);
            case FacadeReader.ID_START_ARRAY:
                return readArray(parser, type);
            case FacadeReader.ID_STRING:
                return nextString(parser);
//                return ConverterRegistry.tryPure2Wrap(nextString(parser), type);
            case FacadeReader.ID_NUMBER:
                return nextNumber(parser);
//                return ConverterRegistry.tryPure2Wrap(nextNumber(parser), type);
            case FacadeReader.ID_BOOLEAN:
                return nextBoolean(parser);
//                return ConverterRegistry.tryPure2Wrap(nextBoolean(parser), type);
            case FacadeReader.ID_NULL:
                nextNull(parser);
                return null;
//                return ConverterRegistry.tryPure2Wrap(null, type);
            default:
                throw new JsonException("Unexpected token id '" + tid + "'");
        }
    }


    public static Object readObject(JsonParser parser, Type type) throws IOException {
        if (parser == null) throw new IllegalArgumentException("Parser must not be null");
        Class<?> rawClazz = TypeUtil.getRawClass(type);
//        NodeConverter<?, ?> converter = ConverterRegistry.getConverter(rawClazz);

//        if (converter != null ) {
//            if (converter.getPureType() == JsonObject.class) {
//                JsonObject jo = new JsonObject();
//                startObject(parser);
//                while (hasNext(parser)) {
//                    String key = nextName(parser);
//                    Object value = readNode(parser, Object.class);
//                    jo.put(key, value);
//                }
//                endObject(parser);
//                return ((NodeConverter<Object, Object>) converter).pure2Wrap(jo);
//            } else {
//                throw new JsonException("Converter expects object '" + converter.getWrapType() +
//                        "' and node '" + converter.getPureType() + "', but got node 'JsonObject'");
//            }
//        }

        if (rawClazz == null || rawClazz.isAssignableFrom(JsonObject.class)) {
            JsonObject jo = new JsonObject();
            startObject(parser);
            while (hasNext(parser)) {
                String key = nextName(parser);
                Object value = readNode(parser, Object.class);
                jo.put(key, value);
            }
            endObject(parser);
            return jo;
        }

        if (rawClazz == Map.class) {
            Type valueType = TypeUtil.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = JsonConfig.global().mapSupplier.create();
            startObject(parser);
            while (hasNext(parser)) {
                String key = nextName(parser);
                Object value = readNode(parser, valueType);
                map.put(key, value);
            }
            endObject(parser);
            return map;
        }

        if (JsonObject.class.isAssignableFrom(rawClazz)) {
            PojoRegistry.PojoInfo pi = PojoRegistry.registerOrElseThrow(rawClazz);
            Map<String, PojoRegistry.FieldInfo> fields = pi.getFields();
            JsonObject jojo = (JsonObject) pi.newInstance();
            startObject(parser);
            while (hasNext(parser)) {
                String key = nextName(parser);
                PojoRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(parser, fi.getType());
                    fi.invokeSetter(jojo, vv);
                } else {
                    Object vv = readNode(parser, Object.class);
                    jojo.put(key, vv);
                }
            }
            endObject(parser);
            return jojo;
        }

        if (PojoRegistry.isPojo(rawClazz)) {
            PojoRegistry.PojoInfo pi = PojoRegistry.registerOrElseThrow(rawClazz);
            Map<String, PojoRegistry.FieldInfo> fields = pi.getFields();
            Object pojo = pi.newInstance();
            startObject(parser);
            while (hasNext(parser)) {
                String key = nextName(parser);
                PojoRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(parser, fi.getType());
                    fi.invokeSetter(pojo, vv);
                }
            }
            endObject(parser);
            return pojo;
        }

        throw new JsonException("Unsupported type: " + type);
    }


    public static Object readArray(JsonParser parser, Type type) throws IOException {
        if (parser == null) throw new IllegalArgumentException("Parser must not be null");
        Class<?> rawClazz = TypeUtil.getRawClass(type);

//        NodeConverter<?, ?> converter = ConverterRegistry.getConverter(rawClazz);
//        if (converter != null ) {
//            if (converter.getPureType() == JsonArray.class) {
//                JsonArray ja = new JsonArray();
//                startArray(parser);
//                while (hasNext(parser)) {
//                    Object value = readNode(parser, Object.class);
//                    ja.add(value);
//                }
//                endArray(parser);
//                return ((NodeConverter<Object, Object>) converter).pure2Wrap(ja);
//            } else {
//                throw new JsonException("Converter expects object '" + converter.getWrapType() +
//                        "' and node '" + converter.getPureType() + "', but got node 'JsonArray'");
//            }
//        }

        if (rawClazz == null || rawClazz.isAssignableFrom(JsonArray.class)) {
            JsonArray ja = new JsonArray();
            startArray(parser);
            while (hasNext(parser)) {
                Object value = readNode(parser, Object.class);
                ja.add(value);
            }
            endArray(parser);
            return ja;
        }

        if (rawClazz == List.class) {
            Type valueType = TypeUtil.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            startArray(parser);
            while (hasNext(parser)) {
                Object value = readNode(parser, valueType);
                list.add(value);
            }
            endArray(parser);
            return list;
        }

        if (rawClazz.isArray()) {
            Class<?> valueClazz = rawClazz.getComponentType();
            List<Object> list = new ArrayList<>();
            startArray(parser);
            while (hasNext(parser)) {
                Object value = readNode(parser, valueClazz);
                list.add(value);
            }
            endArray(parser);

            Object array = Array.newInstance(valueClazz, list.size());
            for (int i = 0; i < list.size(); i++) {
                Array.set(array, i, list.get(i));
            }
            return array;
        }

        throw new JsonException("Unsupported type: " + type);
    }


    /// Reader

    public static int peekTokenId(JsonParser parser) throws IOException {
        int tokenId = parser.currentTokenId();
        if (tokenId == JsonTokenId.ID_NO_TOKEN) {
            JsonToken tk = parser.nextToken();
            tokenId = parser.currentTokenId();
        }
        switch (tokenId) {
            case JsonTokenId.ID_START_OBJECT:
                return FacadeReader.ID_START_OBJECT;
            case JsonTokenId.ID_END_OBJECT:
                return FacadeReader.ID_END_OBJECT;
            case JsonTokenId.ID_START_ARRAY:
                return FacadeReader.ID_START_ARRAY;
            case JsonTokenId.ID_END_ARRAY:
                return FacadeReader.ID_END_ARRAY;
            case JsonTokenId.ID_STRING:
                return FacadeReader.ID_STRING;
            case JsonTokenId.ID_NUMBER_INT:
            case JsonTokenId.ID_NUMBER_FLOAT:
                return FacadeReader.ID_NUMBER;
            case JsonTokenId.ID_TRUE:
            case JsonTokenId.ID_FALSE:
                return FacadeReader.ID_BOOLEAN;
            case JsonTokenId.ID_NULL:
                return FacadeReader.ID_NULL;
            default:
                return FacadeReader.ID_UNKNOWN;
        }
    }

    public static void startDocument(JsonParser parser) throws IOException {
        // Nothing
    }

    public static void endDocument(JsonParser parser) throws IOException {
        // Nothing
    }

    public static void startObject(JsonParser parser) throws IOException {
        parser.nextToken();
    }

    public static void endObject(JsonParser parser) throws IOException {
        parser.nextToken();
    }

    public static void startArray(JsonParser parser) throws IOException {
        parser.nextToken();
    }

    public static void endArray(JsonParser parser) throws IOException {
        parser.nextToken();
    }

    public static String nextName(JsonParser parser) throws IOException {
        String name = parser.currentName();
        parser.nextToken();
        return name;
    }

    public static String nextString(JsonParser parser) throws IOException {
        String value = parser.getText();
        parser.nextToken();
        return value;
    }

    public static Number nextNumber(JsonParser parser) throws IOException {
        Number value = parser.getNumberValue();
        parser.nextToken();
        return value;
    }

    public static Boolean nextBoolean(JsonParser parser) throws IOException {
        Boolean value = parser.getBooleanValue();
        parser.nextToken();
        return value;
    }

    public static void nextNull(JsonParser parser) throws IOException {
        parser.nextToken();
    }

    public static boolean hasNext(JsonParser parser) throws IOException {
        int tid = parser.currentTokenId();
        return tid != JsonTokenId.ID_END_OBJECT && tid != JsonTokenId.ID_END_ARRAY;
    }



    /// Write

    public static void writeNode(JsonGenerator gen, Object node) throws IOException {
        if (gen == null) throw new IllegalArgumentException("Gen must not be null");
//        node = ConverterRegistry.tryWrap2Pure(node);
        if (node == null) {
            writeNull(gen);
        } else if (node instanceof CharSequence || node instanceof Character) {
            writeValue(gen, node.toString());
        } else if (node instanceof Number) {
            writeValue(gen, (Number) node);
        } else if (node instanceof Boolean) {
            writeValue(gen, (Boolean) node);
        } else if (node instanceof JsonObject) {
            startObject(gen);
            ((JsonObject) node).forEach((k, v) -> {
                try {
                    writeName(gen, k);
                    writeNode(gen, v);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            endObject(gen);
        } else if (node instanceof Map) {
            startObject(gen);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                writeName(gen, entry.getKey().toString());
                writeNode(gen, entry.getValue());
            }
            endObject(gen);
        } else if (node instanceof JsonArray) {
            startArray(gen);
            JsonArray ja = (JsonArray) node;
            for (Object v : ja) {
                writeNode(gen, v);
            }
            endArray(gen);
        } else if (node instanceof List) {
            startArray(gen);
            List<?> list = (List<?>) node;
            for (Object v : list) {
                writeNode(gen, v);
            }
            endArray(gen);
        } else if (node.getClass().isArray()) {
            startArray(gen);
            for (int i = 0; i < Array.getLength(node); i++) {
                writeNode(gen, Array.get(node, i));
            }
            endArray(gen);
        } else if (PojoRegistry.isPojo(node.getClass())) {
            startObject(gen);
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry :
                    PojoRegistry.getPojoInfo(node.getClass()).getFields().entrySet()) {
                writeName(gen, entry.getKey());
                Object vv = entry.getValue().invokeGetter(node);
                writeNode(gen, vv);
            }
            endObject(gen);
        } else {
            throw new IllegalStateException("Unsupported node type '" + node.getClass().getName() +
                    "', expected one of [JsonObject, JsonArray, String, Number, Boolean] or a type registered in " +
                    "ConverterRegistry or a valid POJO/JOJO, or a Map/List/Array of such elements.");
        }
    }

    /// Writer

    public static void startDocument(JsonGenerator gen) throws IOException {
        // Nothing
    }

    public static void endDocument(JsonGenerator gen) throws IOException {
        // Nothing
    }

    public static void startObject(JsonGenerator gen) throws IOException {
        gen.writeStartObject();
    }

    public static void endObject(JsonGenerator gen) throws IOException {
        gen.writeEndObject();
    }

    public static void startArray(JsonGenerator gen) throws IOException {
        gen.writeStartArray();
    }

    public static void endArray(JsonGenerator gen) throws IOException {
        gen.writeEndArray();
    }

    public static void writeName(JsonGenerator gen, String name) throws IOException {
        gen.writeFieldName(name);
    }

    public static void writeValue(JsonGenerator gen, String value) throws IOException {
        gen.writeString(value);
    }

    public static void writeValue(JsonGenerator gen, Number value) throws IOException {
        if (value instanceof Long || value instanceof Integer) {
            gen.writeNumber(value.longValue());
        } else if (value instanceof Float || value instanceof Double) {
            gen.writeNumber(value.doubleValue());
        } else if (value instanceof BigInteger) {
            gen.writeNumber(((BigInteger) value));
        } else if (value instanceof BigDecimal) {
            gen.writeNumber(((BigDecimal) value));
        } else {
            gen.writeNumber(value.longValue());
        }
    }

    public static void writeValue(JsonGenerator gen, Boolean value) throws IOException {
        gen.writeBoolean(value);
    }

    public static void writeNull(JsonGenerator gen) throws IOException {
        gen.writeNull();
    }

}
