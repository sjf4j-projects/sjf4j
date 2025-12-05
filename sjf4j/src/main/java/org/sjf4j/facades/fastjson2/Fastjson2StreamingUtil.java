package org.sjf4j.facades.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
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
 * A Fastjson2-specific, fully static implementation of the streaming JSON reader utilities.
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
public class Fastjson2StreamingUtil {

    /// Read

    public static Object readNode(JSONReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("Reader must not be null");
        int tid = peekTokenId(reader);
        switch (tid) {
            case FacadeReader.ID_START_OBJECT:
                return readObject(reader, type);
            case FacadeReader.ID_START_ARRAY:
                return readArray(reader, type);
            case FacadeReader.ID_STRING:
                return nextString(reader);
//                return ConverterRegistry.tryPure2Wrap(nextString(parser), type);
            case FacadeReader.ID_NUMBER:
                return nextNumber(reader);
//                return ConverterRegistry.tryPure2Wrap(nextNumber(parser), type);
            case FacadeReader.ID_BOOLEAN:
                return nextBoolean(reader);
//                return ConverterRegistry.tryPure2Wrap(nextBoolean(parser), type);
            case FacadeReader.ID_NULL:
                nextNull(reader);
                return null;
//                return ConverterRegistry.tryPure2Wrap(null, type);
            default:
                throw new JsonException("Unexpected token id '" + tid + "'");
        }
    }


    public static Object readObject(JSONReader reader, Type type) throws IOException {
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


    public static Object readArray(JSONReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("Reader must not be null");
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

    public static int peekTokenId(JSONReader reader) throws IOException {
        if (reader.isObject()) {
            return FacadeReader.ID_START_OBJECT;
        } else if (reader.current() == '}') {
            return FacadeReader.ID_END_OBJECT;
        } else if (reader.isArray()) {
            return FacadeReader.ID_START_ARRAY;
        } else if (reader.current() == ']') {
            return FacadeReader.ID_END_ARRAY;
        } else if (reader.isString()) {
            return FacadeReader.ID_STRING;
        } else if (reader.isNumber()) {
            return FacadeReader.ID_NUMBER;
        } else if (reader.current() == 't' || reader.current() == 'f') {    // I can do it!
            return FacadeReader.ID_BOOLEAN;
        } else if (reader.isNull()) {
            return FacadeReader.ID_NULL;
        } else {
            return FacadeReader.ID_UNKNOWN;
        }
    }

    public static void startObject(JSONReader reader) throws IOException {
        reader.nextIfObjectStart();
    }

    public static void endObject(JSONReader reader) throws IOException {
        reader.nextIfObjectEnd();
    }

    public static void startArray(JSONReader reader) throws IOException {
        reader.nextIfArrayStart();
    }

    public static void endArray(JSONReader reader) throws IOException {
        reader.nextIfArrayEnd();
    }

    public static String nextName(JSONReader reader) throws IOException {
        return reader.readFieldName();
    }

    public static String nextString(JSONReader reader) throws IOException {
        return reader.readString();
    }

    public static Number nextNumber(JSONReader reader) throws IOException {
        Number n = reader.readNumber();
        // Double is more popular and common usage
        if (n instanceof BigDecimal) {
            double f = n.doubleValue();
            if (Double.isFinite(f)) return f;
        }
        return n;
    }

    public static Boolean nextBoolean(JSONReader reader) throws IOException {
        return reader.readBoolValue();
    }

    public static void nextNull(JSONReader reader) throws IOException {
        reader.nextIfNull();
    }

    public static boolean hasNext(JSONReader reader) throws IOException {
        char current = reader.current();
        return current != '}' && current != ']';
    }


    /// Write

    public static void writeNode(JSONWriter writer, Object node) throws IOException {
        if (writer == null) throw new IllegalArgumentException("Writer must not be null");
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
            for (int i = 0; i < ja.size(); i++) {
                if (i > 0) writeComma(writer);
                writeNode(writer, ja.getNode(i));
            }
            endArray(writer);
        } else if (node instanceof List) {
            startArray(writer);
            List<?> list = (List<?>) node;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) writeComma(writer);
                writeNode(writer, list.get(i));
            }
            endArray(writer);
        } else if (node.getClass().isArray()) {
            startArray(writer);
            for (int i = 0; i < Array.getLength(node); i++) {
                if (i > 0) writeComma(writer);
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

    public static void startDocument(JSONWriter writer) throws IOException {
        // Nothing
    }

    public static void endDocument(JSONWriter writer) throws IOException {
        // Nothing
    }

    public static void startObject(JSONWriter writer) throws IOException {
        writer.startObject();
    }

    public static void endObject(JSONWriter writer) throws IOException {
        writer.endObject();
    }

    public static void startArray(JSONWriter writer) throws IOException {
        writer.startArray();
    }

    public static void endArray(JSONWriter writer) throws IOException {
        writer.endArray();
    }

    public static void writeName(JSONWriter writer, String name) throws IOException {
        writer.writeName(name);
        writer.writeColon();
    }

    public static void writeValue(JSONWriter writer, String value) throws IOException {
        writer.writeString(value);
    }

    public static void writeValue(JSONWriter writer, Number value) throws IOException {
        if (value instanceof Long || value instanceof Integer) {
            writer.writeInt64(value.longValue());
        } else if (value instanceof Float || value instanceof Double) {
            writer.writeDouble(value.doubleValue());
        } else if (value instanceof BigInteger) {
            writer.writeBigInt(((BigInteger) value));
        } else if (value instanceof BigDecimal) {
            writer.writeDecimal(((BigDecimal) value));
        } else {
            writer.writeInt64(value.longValue());
        }
    }

    public static void writeValue(JSONWriter writer, Boolean value) throws IOException {
        writer.writeBool(value);
    }

    public static void writeNull(JSONWriter writer) throws IOException {
        writer.writeNull();
    }

    public static void writeComma(JSONWriter writer) {
        writer.writeComma();
    }

}
