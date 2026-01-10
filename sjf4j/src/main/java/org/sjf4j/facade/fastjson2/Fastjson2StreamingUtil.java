package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.facade.FacadeReader;
import org.sjf4j.util.NumberUtil;
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

    public static Object readNull(JSONReader reader, Type type) throws IOException {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        reader.nextIfNull();

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            return ci.decode(null);
        }
        return  null;
    }

    public static Object readBoolean(JSONReader reader, Type type) throws IOException {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        if (rawClazz == boolean.class || rawClazz.isAssignableFrom(Boolean.class)) {
            return reader.readBoolValue();
        }

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            Boolean b = reader.readBoolValue();
            return ci.decode(b);
        }
        throw new JsonException("Cannot deserialize JSON Boolean into type " + rawClazz.getName());
    }

    public static Object readNumber(JSONReader reader, Type type) throws IOException {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        if (rawClazz.isAssignableFrom(Number.class)) {
            Number n = reader.readNumber();
            // Double is more popular and common usage
            if (n instanceof BigDecimal) {
                double f = n.doubleValue();
                if (Double.isFinite(f)) return f;
            }
            return n;
        }
        if (Number.class.isAssignableFrom(rawClazz) ||
                (rawClazz.isPrimitive() && rawClazz != boolean.class && rawClazz != char.class)) {
            Number n = reader.readNumber();
            return NumberUtil.as(n, rawClazz);
        }

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            Number n = reader.readNumber();
            return ci.decode(n);
        }
        throw new JsonException("Cannot deserialize JSON Number into type " + rawClazz.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object readString(JSONReader reader, Type type) throws IOException {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        if (rawClazz.isAssignableFrom(String.class)) {
            return reader.readString();
        }
        if (rawClazz == Character.class || rawClazz == char.class) {
            String s = reader.readString();
            return s.charAt(0);
        }

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            String s = reader.readString();
            return ci.decode(s);
        }

        if (rawClazz.isEnum()) {
            String s = reader.readString();
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }
        throw new JsonException("Cannot deserialize JSON String into type " + rawClazz.getName());
    }


    public static Object readObject(JSONReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("Reader must not be null");
        Class<?> rawClazz = TypeUtil.getRawClass(type);

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (rawClazz.isAssignableFrom(Map.class) || Map.class.isAssignableFrom(rawClazz) || ci != null) {
            Type valueType = TypeUtil.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            reader.nextIfObjectStart();
            while (!reader.nextIfObjectEnd()) {
                String key = reader.readFieldName();
                Object value = readNode(reader, valueType);
                map.put(key, value);
            }
            return ci != null ? ci.decode(map) : map;
        }

        if (rawClazz.isAssignableFrom(JsonObject.class)) {
            JsonObject jo = new JsonObject();
            reader.nextIfObjectStart();
            while (!reader.nextIfObjectEnd()) {
                String key = reader.readFieldName();
                Object value = readNode(reader, Object.class);
                jo.put(key, value);
            }
            return jo;
        }

        if (JsonObject.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            JsonObject jojo = (JsonObject) pi.newInstance();
            reader.nextIfObjectStart();
            while (!reader.nextIfObjectEnd()) {
                String key = reader.readFieldName();
                NodeRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(reader, fi.getType());
                    fi.invokeSetter(jojo, vv);
                } else {
                    Object vv = readNode(reader, Object.class);
                    jojo.put(key, vv);
                }
            }
            return jojo;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null) {
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            Object pojo = pi.newInstance();
            reader.nextIfObjectStart();
            while (!reader.nextIfObjectEnd()) {
                String key = reader.readFieldName();
                NodeRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(reader, fi.getType());
                    fi.invokeSetter(pojo, vv);
                } else {
                    throw new JsonException("Undefined field '" + key + "' in POJO '" + pi.getType().getName() + "'");
                }
            }
            return pojo;
        }
        throw new JsonException("Cannot deserialize JSON Object into type " + rawClazz.getName());
    }


    public static Object readArray(JSONReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("Reader must not be null");
        Class<?> rawClazz = TypeUtil.getRawClass(type);

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (rawClazz.isAssignableFrom(List.class) || List.class.isAssignableFrom(rawClazz) || ci != null) {
            Type valueType = TypeUtil.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            reader.nextIfArrayStart();
            while (!reader.nextIfArrayEnd()) {
                Object value = readNode(reader, valueType);
                list.add(value);
            }
            return ci != null ? ci.decode(list) : list;
        }

        if (rawClazz.isAssignableFrom(JsonArray.class)) {
            JsonArray ja = new JsonArray();
            reader.nextIfArrayStart();
            while (!reader.nextIfArrayEnd()) {
                Object value = readNode(reader, Object.class);
                ja.add(value);
            }
            return ja;
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray ja = (JsonArray) pi.newInstance();
            reader.nextIfArrayStart();
            while (!reader.nextIfArrayEnd()) {
                Object value = readNode(reader, ja.elementType());
                ja.add(value);
            }
            return ja;
        }

        if (rawClazz.isArray()) {
            Class<?> valueClazz = rawClazz.getComponentType();
            List<Object> list = new ArrayList<>();
            reader.nextIfArrayStart();
            while (!reader.nextIfArrayEnd()) {
                Object value = readNode(reader, valueClazz);
                list.add(value);
            }

            Object array = Array.newInstance(valueClazz, list.size());
            for (int i = 0; i < list.size(); i++) {
                Array.set(array, i, list.get(i));
            }
            return array;
        }
        throw new JsonException("Cannot deserialize JSON Array into type " + rawClazz.getName());
    }

    /// Reader

    public static FacadeReader.Token peekToken(JSONReader reader) throws IOException {
        if (reader.isObject()) {
            return FacadeReader.Token.START_OBJECT;
        } else if (reader.current() == '}') {
            return FacadeReader.Token.END_OBJECT;
        } else if (reader.isArray()) {
            return FacadeReader.Token.START_ARRAY;
        } else if (reader.current() == ']') {
            return FacadeReader.Token.END_ARRAY;
        } else if (reader.isString()) {
            return FacadeReader.Token.STRING;
        } else if (reader.isNumber()) {
            return FacadeReader.Token.NUMBER;
        } else if (reader.current() == 't' || reader.current() == 'f') {    // I can do it!
            return FacadeReader.Token.BOOLEAN;
        } else if (reader.isNull()) {
            return FacadeReader.Token.NULL;
        } else {
            return FacadeReader.Token.UNKNOWN;
        }
    }

//    public static void startObject(JSONReader reader) throws IOException {
//        reader.nextIfObjectStart();
//    }

//    public static void endObject(JSONReader reader) throws IOException {
//        reader.nextIfObjectEnd();
//    }

//    public static void startArray(JSONReader reader) throws IOException {
//        reader.nextIfArrayStart();
//    }

//    public static void endArray(JSONReader reader) throws IOException {
//        reader.nextIfArrayEnd();
//    }

//    public static String nextName(JSONReader reader) throws IOException {
//        return reader.readFieldName();
//    }

//    public static String nextString(JSONReader reader) throws IOException {
//        return reader.readString();
//    }

//    public static Number nextNumber(JSONReader reader) throws IOException {
//        Number n = reader.readNumber();
//        // Double is more popular and common usage
//        if (n instanceof BigDecimal) {
//            double f = n.doubleValue();
//            if (Double.isFinite(f)) return f;
//        }
//        return n;
//    }

//    public static Boolean nextBoolean(JSONReader reader) throws IOException {
//        return reader.readBoolValue();
//    }

//    public static void nextNull(JSONReader reader) throws IOException {
//        reader.nextIfNull();
//    }

//    public static boolean hasNext(JSONReader reader) throws IOException {
//        char current = reader.current();
//        return current != '}' && current != ']';
//    }


    /// Write

    public static void writeNode(JSONWriter writer, Object node) throws IOException {
        if (writer == null) throw new IllegalArgumentException("Writer must not be null");
        if (node == null) {
            writer.writeNull();
            return;
        }

        Class<?> rawClazz = node.getClass();
        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            Object raw = ci.encode(node);
            writeNode(writer, raw);
            return;
        }

        if (node instanceof CharSequence || node instanceof Character) {
            writer.writeString(node.toString());
        } else if (node instanceof Enum) {
            writer.writeString(((Enum<?>) node).name());
        } else if (node instanceof Number) {
            writeNumber(writer, (Number) node);
        } else if (node instanceof Boolean) {
            writer.writeBool((Boolean) node);
        } else if (node instanceof JsonObject) {
            writer.startObject();
            ((JsonObject) node).forEach((k, v) -> {
                try {
                    writer.writeName(k);
                    writer.writeColon();
                    writeNode(writer, v);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            writer.endObject();
        } else if (node instanceof Map) {
            writer.startObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                writer.writeName(entry.getKey().toString());
                writer.writeColon();
                writeNode(writer, entry.getValue());
            }
            writer.endObject();
        } else if (node instanceof JsonArray) {
            writer.startArray();
            JsonArray ja = (JsonArray) node;
            for (int i = 0; i < ja.size(); i++) {
                if (i > 0) writer.writeComma();
                writeNode(writer, ja.getNode(i));
            }
            writer.endArray();
        } else if (node instanceof List) {
            writer.startArray();
            List<?> list = (List<?>) node;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) writer.writeComma();
                writeNode(writer, list.get(i));
            }
            writer.endArray();
        } else if (node.getClass().isArray()) {
            writer.startArray();
            for (int i = 0; i < Array.getLength(node); i++) {
                if (i > 0) writer.writeComma();
                writeNode(writer, Array.get(node, i));
            }
            writer.endArray();
        } else {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
            if (pi != null) {
                writer.startObject();
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
                    writer.writeName(entry.getKey());
                    writer.writeColon();
                    Object vv = entry.getValue().invokeGetter(node);
                    writeNode(writer, vv);
                }
                writer.endObject();
            } else {
                throw new IllegalStateException("Unsupported node type '" + node.getClass().getName() + "'");
            }
        }
    }

    /// Writer

//    public static void startDocument(JSONWriter writer) throws IOException {
//        // Nothing
//    }
//
//    public static void endDocument(JSONWriter writer) throws IOException {
//        // Nothing
//    }

//    public static void startObject(JSONWriter writer) throws IOException {
//        writer.startObject();
//    }

//    public static void endObject(JSONWriter writer) throws IOException {
//        writer.endObject();
//    }

//    public static void startArray(JSONWriter writer) throws IOException {
//        writer.startArray();
//    }

//    public static void endArray(JSONWriter writer) throws IOException {
//        writer.endArray();
//    }

//    public static void writeName(JSONWriter writer, String name) throws IOException {
//        writer.writeName(name);
//        writer.writeColon();
//    }

//    public static void writeValue(JSONWriter writer, String value) throws IOException {
//        writer.writeString(value);
//    }

    public static void writeNumber(JSONWriter writer, Number value) throws IOException {
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

//    public static void writeValue(JSONWriter writer, Boolean value) throws IOException {
//        writer.writeBool(value);
//    }

//    public static void writeNull(JSONWriter writer) throws IOException {
//        writer.writeNull();
//    }

//    public static void writeComma(JSONWriter writer) {
//        writer.writeComma();
//    }

}
