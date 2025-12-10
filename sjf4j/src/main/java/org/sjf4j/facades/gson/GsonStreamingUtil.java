package org.sjf4j.facades.gson;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.PojoRegistry;
import org.sjf4j.facades.FacadeReader;
import org.sjf4j.util.NumberUtil;
import org.sjf4j.util.StreamingUtil;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * A Gson-specific, fully static implementation of the streaming JSON reader utilities.
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
 * perform constant folding, and optimize the reader loops specifically for the original API,
 * achieving lower per-call latency compared to the generic {@code StreamingUtil}.
 */
public class GsonStreamingUtil {

    /// Read

    public static Object readNode(JsonReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("Reader must not be null");
        FacadeReader.Token token = peekToken(reader);
        switch (token) {
            case START_OBJECT:
                return readObject(reader, type);
            case START_ARRAY:
                return readArray(reader, type);
            case STRING:
                return nextString(reader);
//                return ConverterRegistry.tryPure2Wrap(nextString(parser), type);
            case NUMBER:
                return nextNumber(reader);
//                return ConverterRegistry.tryPure2Wrap(nextNumber(parser), type);
            case BOOLEAN:
                return nextBoolean(reader);
//                return ConverterRegistry.tryPure2Wrap(nextBoolean(parser), type);
            case NULL:
                nextNull(reader);
                return null;
//                return ConverterRegistry.tryPure2Wrap(null, type);
            default:
                throw new JsonException("Unexpected token '" + token + "'");
        }
    }


    public static Object readObject(JsonReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("Reader must not be null");
        Class<?> rawClazz = TypeUtil.getRawClass(type);

//        NodeConverter<?, ?> converter = ConverterRegistry.getConverter(rawClazz);
//        if (converter != null ) {
//            if (converter.getPureType() == JsonObject.class) {
//                JsonObject jo = new JsonObject();
//                startObject(reader);
//                while (hasNext(reader)) {
//                    String key = nextName(reader);
//                    Object value = readNode(reader, Object.class);
//                    jo.put(key, value);
//                }
//                endObject(reader);
//                return ((NodeConverter<Object, Object>) converter).pure2Wrap(jo);
//            } else {
//                throw new JsonException("Converter expects object '" + converter.getWrapType() +
//                        "' and node '" + converter.getPureType() + "', but got node 'JsonObject'");
//            }
//        }

        if (rawClazz == null || rawClazz.isAssignableFrom(JsonObject.class)) {
            JsonObject jo = new JsonObject();
            startObject(reader);
            while (hasNext(reader)) {
                String key = nextName(reader);
                Object value = readNode(reader, Object.class);
                jo.put(key, value);
            }
            endObject(reader);
            return jo;
        }

        if (rawClazz == Map.class) {
            Type valueType = TypeUtil.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = JsonConfig.global().mapSupplier.create();
            startObject(reader);
            while (hasNext(reader)) {
                String key = nextName(reader);
                Object value = readNode(reader, valueType);
                map.put(key, value);
            }
            endObject(reader);
            return map;
        }

        if (JsonObject.class.isAssignableFrom(rawClazz)) {
            PojoRegistry.PojoInfo pi = PojoRegistry.registerOrElseThrow(rawClazz);
            Map<String, PojoRegistry.FieldInfo> fields = pi.getFields();
            JsonObject jojo = (JsonObject) pi.newInstance();
            startObject(reader);
            while (hasNext(reader)) {
                String key = nextName(reader);
                PojoRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(reader, fi.getType());
                    fi.invokeSetter(jojo, vv);
                } else {
                    Object vv = readNode(reader, Object.class);
                    jojo.put(key, vv);
                }
            }
            endObject(reader);
            return jojo;
        }

        if (PojoRegistry.isPojo(rawClazz)) {
            PojoRegistry.PojoInfo pi = PojoRegistry.registerOrElseThrow(rawClazz);
            Map<String, PojoRegistry.FieldInfo> fields = pi.getFields();
            Object pojo = pi.newInstance();
            startObject(reader);
            while (hasNext(reader)) {
                String key = nextName(reader);
                PojoRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(reader, fi.getType());
                    fi.invokeSetter(pojo, vv);
                }
            }
            endObject(reader);
            return pojo;
        }

        throw new JsonException("Unsupported type: " + type);
    }


    public static Object readArray(JsonReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("reader must not be null");
        Class<?> rawClazz = TypeUtil.getRawClass(type);

//        NodeConverter<?, ?> converter = ConverterRegistry.getConverter(rawClazz);
//        if (converter != null ) {
//            if (converter.getPureType() == JsonArray.class) {
//                JsonArray ja = new JsonArray();
//                startArray(reader);
//                while (hasNext(reader)) {
//                    Object value = readNode(reader, Object.class);
//                    ja.add(value);
//                }
//                endArray(reader);
//                return ((NodeConverter<Object, Object>) converter).pure2Wrap(ja);
//            } else {
//                throw new JsonException("Converter expects object '" + converter.getWrapType() +
//                        "' and node '" + converter.getPureType() + "', but got node 'JsonArray'");
//            }
//        }

        if (rawClazz == null || rawClazz.isAssignableFrom(JsonArray.class)) {
            JsonArray ja = new JsonArray();
            startArray(reader);
            while (hasNext(reader)) {
                Object value = readNode(reader, Object.class);
                ja.add(value);
            }
            endArray(reader);
            return ja;
        }

        if (rawClazz == List.class) {
            Type valueType = TypeUtil.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            startArray(reader);
            while (hasNext(reader)) {
                Object value = readNode(reader, valueType);
                list.add(value);
            }
            endArray(reader);
            return list;
        }

        if (rawClazz.isArray()) {
            Class<?> valueClazz = rawClazz.getComponentType();
            List<Object> list = new ArrayList<>();
            startArray(reader);
            while (hasNext(reader)) {
                Object value = readNode(reader, valueClazz);
                list.add(value);
            }
            endArray(reader);

            Object array = Array.newInstance(valueClazz, list.size());
            for (int i = 0; i < list.size(); i++) {
                Array.set(array, i, list.get(i));
            }
            return array;
        }

        throw new JsonException("Unsupported type: " + type);
    }


    /// Reader

    public static FacadeReader.Token peekToken(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT:
                return FacadeReader.Token.START_OBJECT;
            case END_OBJECT:
                return FacadeReader.Token.END_OBJECT;
            case BEGIN_ARRAY:
                return FacadeReader.Token.START_ARRAY;
            case END_ARRAY:
                return FacadeReader.Token.END_ARRAY;
            case STRING:
                return FacadeReader.Token.STRING;
            case NUMBER:
                return FacadeReader.Token.NUMBER;
            case BOOLEAN:
                return FacadeReader.Token.BOOLEAN;
            case NULL:
                return FacadeReader.Token.NULL;
            default:
                return FacadeReader.Token.UNKNOWN;
        }
    }

    public static void startDocument(JsonReader reader) throws IOException {
        // Nothing
    }

    public static void endDocument(JsonReader reader) throws IOException {
        // Nothing
    }

    public static void startObject(JsonReader reader) throws IOException {
        reader.beginObject();
    }

    public static void endObject(JsonReader reader) throws IOException {
        reader.endObject();
    }

    public static void startArray(JsonReader reader) throws IOException {
        reader.beginArray();
    }

    public static void endArray(JsonReader reader) throws IOException {
        reader.endArray();
    }

    public static String nextName(JsonReader reader) throws IOException {
        return reader.nextName();
    }

    public static String nextString(JsonReader reader) throws IOException {
        return reader.nextString();
    }

    public static Number nextNumber(JsonReader reader) throws IOException {
        return NumberUtil.toNumber(reader.nextString());
    }

    public static Boolean nextBoolean(JsonReader reader) throws IOException {
        return reader.nextBoolean();
    }

    public static void nextNull(JsonReader reader) throws IOException {
        reader.nextNull();
    }

    public static boolean hasNext(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        return token != JsonToken.END_OBJECT && token != JsonToken.END_ARRAY && token != JsonToken.END_DOCUMENT;
    }

    
    /// Write

    public static void writeNode(JsonWriter writer, Object node) throws IOException {
        if (writer == null) throw new IllegalArgumentException("Write must not be null");
//        node = ConverterRegistry.tryWrap2Pure(node);
        if (node == null) {
            writeNull(writer);
        } else if (node instanceof CharSequence || node instanceof Character) {
            writeValue(writer, node.toString());
        } else if (node instanceof Number) {
            writeValue(writer, (Number) node);
        } else if (node instanceof Boolean) {
            writeValue(writer, (Boolean) node);
        } else if (node instanceof JsonObject) {
            startObject(writer);
            ((JsonObject) node).forEach((k, v) -> {
                try {
                    writeName(writer, k);
                    writeNode(writer, v);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            endObject(writer);
        } else if (node instanceof Map) {
            startObject(writer);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                writeName(writer, entry.getKey().toString());
                writeNode(writer, entry.getValue());
            }
            endObject(writer);
        } else if (node instanceof JsonArray) {
            startArray(writer);
            JsonArray ja = (JsonArray) node;
            for (Object v : ja) {
                writeNode(writer, v);
            }
            endArray(writer);
        } else if (node instanceof List) {
            startArray(writer);
            List<?> list = (List<?>) node;
            for (Object v : list) {
                writeNode(writer, v);
            }
            endArray(writer);
        } else if (node.getClass().isArray()) {
            startArray(writer);
            for (int i = 0; i < Array.getLength(node); i++) {
                writeNode(writer, Array.get(node, i));
            }
            endArray(writer);
        } else if (PojoRegistry.isPojo(node.getClass())) {
            startObject(writer);
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry :
                    PojoRegistry.getPojoInfo(node.getClass()).getFields().entrySet()) {
                writeName(writer, entry.getKey());
                Object vv = entry.getValue().invokeGetter(node);
                writeNode(writer, vv);
            }
            endObject(writer);
        } else {
            throw new IllegalStateException("Unsupported node type '" + node.getClass().getName() +
                    "', expected one of [JsonObject, JsonArray, String, Number, Boolean] or a type registered in " +
                    "ConverterRegistry or a valid POJO/JOJO, or a Map/List/Array of such elements.");
        }
    }

    /// Writer

    public static void startDocument(JsonWriter writer) throws IOException {
        // Nothing
    }

    public static void endDocument(JsonWriter writer) throws IOException {
        // Nothing
    }

    public static void startObject(JsonWriter writer) throws IOException {
        writer.beginObject();
    }

    public static void endObject(JsonWriter writer) throws IOException {
        writer.endObject();
    }

    public static void startArray(JsonWriter writer) throws IOException {
        writer.beginArray();
    }

    public static void endArray(JsonWriter writer) throws IOException {
        writer.endArray();
    }

    public static void writeName(JsonWriter writer, String name) throws IOException {
        writer.name(name);
    }

    public static void writeValue(JsonWriter writer, String value) throws IOException {
        writer.value(value);
    }

    public static void writeValue(JsonWriter writer, Number value) throws IOException {
        writer.value(value);
    }

    public static void writeValue(JsonWriter writer, Boolean value) throws IOException {
        writer.value(value);
    }

    public static void writeNull(JsonWriter writer) throws IOException {
        writer.nullValue();
    }

}
