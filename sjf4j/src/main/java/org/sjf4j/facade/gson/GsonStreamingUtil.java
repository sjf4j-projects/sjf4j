package org.sjf4j.facade.gson;

import com.fasterxml.jackson.core.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeRegistry;
import org.sjf4j.facade.FacadeReader;
import org.sjf4j.util.NumberUtil;
import org.sjf4j.util.StreamingUtil;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
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
                return readString(reader, type);
            case NUMBER:
                return readNumber(reader, type);
            case BOOLEAN:
                return readBoolean(reader, type);
            case NULL:
                return readNull(reader, type);
            default:
                throw new JsonException("Unexpected token '" + token + "'");
        }
    }

    public static Object readNull(JsonReader reader, Type type) throws IOException {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        reader.nextNull();

        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (ci != null) {
            return ci.unconvert(null);
        }

        return  null;
    }

    public static Object readBoolean(JsonReader reader, Type type) throws IOException {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        if (rawClazz == boolean.class || rawClazz.isAssignableFrom(Boolean.class)) {
            return reader.nextBoolean();
        }

        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (ci != null) {
            Boolean b = reader.nextBoolean();
            return ci.unconvert(b);
        }

        throw new JsonException("Type " + rawClazz.getName()
                + " cannot be deserialized from a Boolean value without a NodeConverter");
    }

    public static Object readNumber(JsonReader reader, Type type) throws IOException {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        if (rawClazz.isAssignableFrom(Number.class)) {
            return NumberUtil.toNumber(reader.nextString());
        }
        if (Number.class.isAssignableFrom(rawClazz) ||
                (rawClazz.isPrimitive() && rawClazz != boolean.class && rawClazz != char.class)) {
            Number n = NumberUtil.toNumber(reader.nextString());
            return NumberUtil.as(n, rawClazz);
        }

        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (ci != null) {
            Number n = NumberUtil.toNumber(reader.nextString());
            return ci.unconvert(n);
        }

        throw new JsonException("Type " + rawClazz.getName()
                + " cannot be deserialized from a Number value without a NodeConverter");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object readString(JsonReader reader, Type type) throws IOException {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        if (rawClazz.isAssignableFrom(String.class)) {
            return reader.nextString();
        }
        if (rawClazz == Character.class || rawClazz == char.class) {
            String s = reader.nextString();
            return s.charAt(0);
        }

        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (ci != null) {
            String s = reader.nextString();
            return ci.unconvert(s);
        }

        if (rawClazz.isEnum()) {
            String s = reader.nextString();
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }

        throw new JsonException("Type " + rawClazz.getName()
                + " cannot be deserialized from a String value without a NodeConverter");
    }


    public static Object readObject(JsonReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("Reader must not be null");
        Class<?> rawClazz = TypeUtil.getRawClass(type);

        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (rawClazz.isAssignableFrom(Map.class) || ci != null) {
            Type valueType = TypeUtil.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = JsonConfig.global().mapSupplier.create();
            reader.beginObject();
            while (reader.peek() != JsonToken.END_OBJECT) {
                String key = reader.nextName();
                Object value = readNode(reader, valueType);
                map.put(key, value);
            }
            reader.endObject();
            return ci != null ? ci.unconvert(map) : map;
        }

        if (rawClazz.isAssignableFrom(JsonObject.class)) {
            JsonObject jo = new JsonObject();
            reader.beginObject();
            while (reader.peek() != JsonToken.END_OBJECT) {
                String key = reader.nextName();
                Object value = readNode(reader, Object.class);
                jo.put(key, value);
            }
            reader.endObject();
            return jo;
        }

        if (JsonObject.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            JsonObject jojo = (JsonObject) pi.newInstance();
            reader.beginObject();
            while (reader.peek() != JsonToken.END_OBJECT) {
                String key = reader.nextName();
                NodeRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(reader, fi.getType());
                    fi.invokeSetter(jojo, vv);
                } else {
                    Object vv = readNode(reader, Object.class);
                    jojo.put(key, vv);
                }
            }
            reader.endObject();
            return jojo;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null) {
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            Object pojo = pi.newInstance();
            reader.beginObject();
            while (reader.peek() != JsonToken.END_OBJECT) {
                String key = reader.nextName();
                NodeRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(reader, fi.getType());
                    fi.invokeSetter(pojo, vv);
                }
            }
            reader.endObject();
            return pojo;
        }

        throw new JsonException("Unsupported type: " + type);
    }


    public static Object readArray(JsonReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("reader must not be null");
        Class<?> rawClazz = TypeUtil.getRawClass(type);


        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (rawClazz.isAssignableFrom(List.class) || ci != null) {
            Type valueType = TypeUtil.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            reader.beginArray();
            while (reader.peek() != JsonToken.END_ARRAY) {
                Object value = readNode(reader, valueType);
                list.add(value);
            }
            reader.endArray();
            return ci != null ? ci.unconvert(list) : list;
        }

        if (rawClazz.isAssignableFrom(JsonArray.class)) {
            JsonArray ja = new JsonArray();
            reader.beginArray();
            while (reader.peek() != JsonToken.END_ARRAY) {
                Object value = readNode(reader, Object.class);
                ja.add(value);
            }
            reader.endArray();
            return ja;
        }


        if (rawClazz.isArray()) {
            Class<?> valueClazz = rawClazz.getComponentType();
            List<Object> list = new ArrayList<>();
            reader.beginArray();
            while (reader.peek() != JsonToken.END_ARRAY) {
                Object value = readNode(reader, valueClazz);
                list.add(value);
            }
            reader.endArray();

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

//    public static void startDocument(JsonReader reader) throws IOException {
//        // Nothing
//    }
//
//    public static void endDocument(JsonReader reader) throws IOException {
//        // Nothing
//    }
//
//    public static void startObject(JsonReader reader) throws IOException {
//        reader.beginObject();
//    }
//
//    public static void endObject(JsonReader reader) throws IOException {
//        reader.endObject();
//    }
//
//    public static void startArray(JsonReader reader) throws IOException {
//        reader.beginArray();
//    }
//
//    public static void endArray(JsonReader reader) throws IOException {
//        reader.endArray();
//    }
//
//    public static String nextName(JsonReader reader) throws IOException {
//        return reader.nextName();
//    }

//    public static String nextString(JsonReader reader) throws IOException {
//        return reader.nextString();
//    }

//    public static Number nextNumber(JsonReader reader) throws IOException {
//        return NumberUtil.toNumber(reader.nextString());
//    }

//    public static Boolean nextBoolean(JsonReader reader) throws IOException {
//        return reader.nextBoolean();
//    }

//    public static void nextNull(JsonReader reader) throws IOException {
//        reader.nextNull();
//    }

//    public static boolean hasNext(JsonReader reader) throws IOException {
//        JsonToken token = reader.peek();
//        return token != JsonToken.END_OBJECT && token != JsonToken.END_ARRAY && token != JsonToken.END_DOCUMENT;
//    }

    
    /// Write

    public static void writeNode(JsonWriter writer, Object node) throws IOException {
        if (writer == null) throw new IllegalArgumentException("Write must not be null");
        if (node == null) {
            writer.nullValue();
            return;
        }

        Class<?> rawClazz = node.getClass();
        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (ci != null) {
            Object raw = ci.convert(node);
            writeNode(writer, raw);
            return;
        }

        if (node instanceof CharSequence || node instanceof Character) {
            writer.value(node.toString());
        } else if (node instanceof Enum) {
            writer.value(((Enum<?>) node).name());
        } else if (node instanceof Number) {
            writer.value((Number) node);
        } else if (node instanceof Boolean) {
            writer.value((Boolean) node);
        } else if (node instanceof JsonObject) {
            writer.beginObject();
            ((JsonObject) node).forEach((k, v) -> {
                try {
                    writer.name(k);
                    writeNode(writer, v);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            writer.endObject();
        } else if (node instanceof Map) {
            writer.beginObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                writer.name(entry.getKey().toString());
                writeNode(writer, entry.getValue());
            }
            writer.endObject();
        } else if (node instanceof JsonArray) {
            writer.beginArray();
            JsonArray ja = (JsonArray) node;
            for (Object v : ja) {
                writeNode(writer, v);
            }
            writer.endArray();
        } else if (node instanceof List) {
            writer.beginArray();
            List<?> list = (List<?>) node;
            for (Object v : list) {
                writeNode(writer, v);
            }
            writer.endArray();
        } else if (node.getClass().isArray()) {
            writer.beginArray();
            for (int i = 0; i < Array.getLength(node); i++) {
                writeNode(writer, Array.get(node, i));
            }
            writer.endArray();
        } else if (NodeRegistry.isPojo(node.getClass())) {
            writer.beginObject();
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry :
                    NodeRegistry.getPojoInfo(node.getClass()).getFields().entrySet()) {
                writer.name(entry.getKey());
                Object vv = entry.getValue().invokeGetter(node);
                writeNode(writer, vv);
            }
            writer.endObject();
        } else {
            throw new IllegalStateException("Unsupported node type '" + node.getClass().getName() +
                    "', expected one of [JsonObject, JsonArray, String, Number, Boolean] or a type registered in " +
                    "ConverterRegistry or a valid POJO/JOJO, or a Map/List/Array of such elements.");
        }
    }

    /// Writer

//    public static void startDocument(JsonWriter writer) throws IOException {
//        // Nothing
//    }
//
//    public static void endDocument(JsonWriter writer) throws IOException {
//        // Nothing
//    }
//
//    public static void startObject(JsonWriter writer) throws IOException {
//        writer.beginObject();
//    }
//
//    public static void endObject(JsonWriter writer) throws IOException {
//        writer.endObject();
//    }
//
//    public static void startArray(JsonWriter writer) throws IOException {
//        writer.beginArray();
//    }
//
//    public static void endArray(JsonWriter writer) throws IOException {
//        writer.endArray();
//    }

//    public static void writeName(JsonWriter writer, String name) throws IOException {
//        writer.name(name);
//    }

//    public static void writeValue(JsonWriter writer, String value) throws IOException {
//        writer.value(value);
//    }
//
//    public static void writeValue(JsonWriter writer, Number value) throws IOException {
//        writer.value(value);
//    }
//
//    public static void writeValue(JsonWriter writer, Boolean value) throws IOException {
//        writer.value(value);
//    }

//    public static void writeNull(JsonWriter writer) throws IOException {
//        writer.nullValue();
//    }

}
