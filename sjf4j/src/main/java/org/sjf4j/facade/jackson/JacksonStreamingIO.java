package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.facade.FacadeReader;
import org.sjf4j.node.Numbers;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A Jackson-specific, fully static implementation of the streaming JSON parser utilities.
 * <p>
 * This class is a specialized and performance-optimized version of {@link StreamingIO},
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
public class JacksonStreamingIO {

    /// Read

    public static Object readNode(JsonParser parser, Type type) throws IOException {
        if (parser == null) throw new IllegalArgumentException("Parser must not be null");
        FacadeReader.Token token = peekToken(parser);
        switch (token) {
            case START_OBJECT:
                return readObject(parser, type);
            case START_ARRAY:
                return readArray(parser, type);
            case STRING:
                return readString(parser, type);
            case NUMBER:
                return readNumber(parser, type);
            case BOOLEAN:
                return readBoolean(parser, type);
            case NULL:
                return readNull(parser, type);
            default:
                throw new JsonException("Unexpected token '" + token + "'");
        }
    }

    public static Object readNull(JsonParser parser, Type type) throws IOException {
        Class<?> rawClazz = Types.rawClazz(type);
        parser.nextToken();

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            return ci.decode(null);
        }

        return  null;
    }

    public static Object readBoolean(JsonParser parser, Type type) throws IOException {
        Class<?> rawClazz = Types.rawClazz(type);
        if (rawClazz.isAssignableFrom(Boolean.class)) {
            Boolean b = parser.getBooleanValue();
            parser.nextToken();
            return b;
        }

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            Boolean b = parser.getBooleanValue();
            parser.nextToken();
            return ci.decode(b);
        }
        throw new JsonException("Cannot deserialize JSON Boolean into type " + rawClazz.getName());
    }

    public static Object readNumber(JsonParser parser, Type type) throws IOException {
        Class<?> rawClazz = Types.rawClazz(type);
        if (rawClazz.isAssignableFrom(Number.class)) {
            return parser.getNumberValue();
        }
        if (Number.class.isAssignableFrom(rawClazz)) {
            Number n = parser.getNumberValue();
            parser.nextToken();
            return Numbers.to(n, rawClazz);
        }

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            Number n = parser.getNumberValue();
            parser.nextToken();
            return ci.decode(n);
        }
        throw new JsonException("Cannot deserialize JSON Number into type " + rawClazz.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object readString(JsonParser parser, Type type) throws IOException {
        Class<?> rawClazz = Types.rawClazz(type);
        if (rawClazz.isAssignableFrom(String.class)) {
            String s = parser.getText();
            parser.nextToken();
            return s;
        }
        if (rawClazz == Character.class) {
            String s = parser.getText();
            parser.nextToken();
            return s.length() > 0 ? s.charAt(0) : null;
        }

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            String s = parser.getText();
            parser.nextToken();
            return ci.decode(s);
        }

        if (rawClazz.isEnum()) {
            String s = parser.getText();
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }
        throw new JsonException("Cannot deserialize JSON String into type " + rawClazz.getName());
    }


    public static Object readObject(JsonParser parser, Type type) throws IOException {
        if (parser == null) throw new IllegalArgumentException("Parser must not be null");
        Class<?> rawClazz = Types.rawClazz(type);

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (rawClazz.isAssignableFrom(Map.class) || ci != null) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            parser.nextToken();
            while (parser.currentTokenId() != JsonTokenId.ID_END_OBJECT) {
                String key = parser.currentName();
                parser.nextToken();
                Object value = readNode(parser, valueType);
                map.put(key, value);
            }
            parser.nextToken();
            return ci != null ? ci.decode(map) : map;
        }

        if (rawClazz.isAssignableFrom(JsonObject.class)) {
            JsonObject jo = new JsonObject();
            parser.nextToken();
            while (parser.currentTokenId() != JsonTokenId.ID_END_OBJECT) {
                String key = parser.currentName();
                parser.nextToken();
                Object value = readNode(parser, Object.class);
                jo.put(key, value);
            }
            parser.nextToken();
            return jo;
        }

        if (JsonObject.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            JsonObject jojo = (JsonObject) pi.newInstance();
            parser.nextToken();
            while (parser.currentTokenId() != JsonTokenId.ID_END_OBJECT) {
                String key = parser.currentName();
                parser.nextToken();
                NodeRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(parser, fi.getType());
                    fi.invokeSetter(jojo, vv);
                } else {
                    Object vv = readNode(parser, Object.class);
                    jojo.put(key, vv);
                }
            }
            parser.nextToken();
            return jojo;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null) {
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            Object pojo = pi.newInstance();
            parser.nextToken();
            while (parser.currentTokenId() != JsonTokenId.ID_END_OBJECT) {
                String key = parser.currentName();
                parser.nextToken();
                NodeRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(parser, fi.getType());
                    fi.invokeSetter(pojo, vv);
                } else {
                    throw new JsonException("Undefined field '" + key + "' in POJO '" + pi.getType().getName() + "'");
                }
            }
            parser.nextToken();
            return pojo;
        }
        throw new JsonException("Cannot deserialize JSON Object into type " + rawClazz.getName());
    }


    public static Object readArray(JsonParser parser, Type type) throws IOException {
        if (parser == null) throw new IllegalArgumentException("Parser must not be null");
        Class<?> rawClazz = Types.rawClazz(type);

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (rawClazz.isAssignableFrom(List.class) || ci != null) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            parser.nextToken();
            while (parser.currentTokenId() != JsonTokenId.ID_END_ARRAY) {
                Object value = readNode(parser, valueType);
                list.add(value);
            }
            parser.nextToken();
            return ci != null ? ci.decode(list) : list;
        }

        if (rawClazz.isAssignableFrom(JsonArray.class)) {
            JsonArray ja = new JsonArray();
            parser.nextToken();
            while (parser.currentTokenId() != JsonTokenId.ID_END_ARRAY) {
                Object value = readNode(parser, Object.class);
                ja.add(value);
            }
            parser.nextToken();
            return ja;
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray ja = (JsonArray) pi.newInstance();
            parser.nextToken();
            while (parser.currentTokenId() != JsonTokenId.ID_END_ARRAY) {
                Object value = readNode(parser, ja.elementType());
                ja.add(value);
            }
            parser.nextToken();
            return ja;
        }

        if (rawClazz.isArray()) {
            Class<?> valueClazz = rawClazz.getComponentType();
            List<Object> list = new ArrayList<>();
            parser.nextToken();
            while (parser.currentTokenId() != JsonTokenId.ID_END_ARRAY) {
                Object value = readNode(parser, valueClazz);
                list.add(value);
            }
            parser.nextToken();

            Object array = Array.newInstance(valueClazz, list.size());
            for (int i = 0, len = list.size(); i < len; i++) {
                Array.set(array, i, list.get(i));
            }
            return array;
        }

        if (Set.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Set<Object> set = new LinkedHashSet<>();
            parser.nextToken();
            while (parser.currentTokenId() != JsonTokenId.ID_END_ARRAY) {
                Object value = readNode(parser, valueType);
                set.add(value);
            }
            parser.nextToken();
            return set;
        }

        throw new JsonException("Cannot deserialize JSON Array into type " + rawClazz.getName());
    }


    /// Reader

    public static FacadeReader.Token peekToken(JsonParser parser) throws IOException {
        int tokenId = parser.currentTokenId();
        if (tokenId == JsonTokenId.ID_NO_TOKEN) {
            JsonToken tk = parser.nextToken();
            tokenId = parser.currentTokenId();
        }
        switch (tokenId) {
            case JsonTokenId.ID_START_OBJECT:
                return FacadeReader.Token.START_OBJECT;
            case JsonTokenId.ID_END_OBJECT:
                return FacadeReader.Token.END_OBJECT;
            case JsonTokenId.ID_START_ARRAY:
                return FacadeReader.Token.START_ARRAY;
            case JsonTokenId.ID_END_ARRAY:
                return FacadeReader.Token.END_ARRAY;
            case JsonTokenId.ID_STRING:
                return FacadeReader.Token.STRING;
            case JsonTokenId.ID_NUMBER_INT:
            case JsonTokenId.ID_NUMBER_FLOAT:
                return FacadeReader.Token.NUMBER;
            case JsonTokenId.ID_TRUE:
            case JsonTokenId.ID_FALSE:
                return FacadeReader.Token.BOOLEAN;
            case JsonTokenId.ID_NULL:
                return FacadeReader.Token.NULL;
            default:
                return FacadeReader.Token.UNKNOWN;
        }
    }

//    public static void startDocument(JsonParser parser) throws IOException {
//        // Nothing
//    }
//
//    public static void endDocument(JsonParser parser) throws IOException {
//        // Nothing
//    }
//
//    public static void startObject(JsonParser parser) throws IOException {
//        parser.nextToken();
//    }
//
//    public static void endObject(JsonParser parser) throws IOException {
//        parser.nextToken();
//    }
//
//    public static void startArray(JsonParser parser) throws IOException {
//        parser.nextToken();
//    }
//
//    public static void endArray(JsonParser parser) throws IOException {
//        parser.nextToken();
//    }

//    public static String nextName(JsonParser parser) throws IOException {
//        String name = parser.currentName();
//        parser.nextToken();
//        return name;
//    }

//    public static String nextString(JsonParser parser) throws IOException {
//        String value = parser.getText();
//        parser.nextToken();
//        return value;
//    }
//
//    public static Number nextNumber(JsonParser parser) throws IOException {
//        Number value = parser.getNumberValue();
//        parser.nextToken();
//        return value;
//    }
//
//    public static Boolean nextBoolean(JsonParser parser) throws IOException {
//        Boolean value = parser.getBooleanValue();
//        parser.nextToken();
//        return value;
//    }
//
//    public static void nextNull(JsonParser parser) throws IOException {
//        parser.nextToken();
//    }

//    public static boolean hasNext(JsonParser parser) throws IOException {
//        int tid = parser.currentTokenId();
//        return tid != JsonTokenId.ID_END_OBJECT && tid != JsonTokenId.ID_END_ARRAY;
//    }



    /// Write

    public static void writeNode(JsonGenerator gen, Object node) throws IOException {
        Objects.requireNonNull(gen, "gen is null");
        if (node == null) {
            gen.writeNull();
            return;
        }

        Class<?> rawClazz = node.getClass();
        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            Object raw = ci.encode(node);
            writeNode(gen, raw);
            return;
        }

        if (node instanceof CharSequence || node instanceof Character) {
            gen.writeString(node.toString());
            return;
        }
        if (node instanceof Enum) {
            gen.writeString(((Enum<?>) node).name());
            return;
        }

        if (node instanceof Number) {
            writeValue(gen, (Number) node);
            return;
        }

        if (node instanceof Boolean) {
            gen.writeBoolean((Boolean) node);
            return;
        }

        if (node instanceof Map) {
            gen.writeStartObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                gen.writeFieldName(entry.getKey().toString());
                writeNode(gen, entry.getValue());
            }
            gen.writeEndObject();
            return;
        }

        if (node instanceof List) {
            gen.writeStartArray();
            List<?> list = (List<?>) node;
            for (Object v : list) {
                writeNode(gen, v);
            }
            gen.writeEndArray();
            return;
        }

        if (node instanceof JsonObject) {
            gen.writeStartObject();
            ((JsonObject) node).forEach((k, v) -> {
                try {
                    gen.writeFieldName(k);
                    writeNode(gen, v);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            gen.writeEndObject();
            return;
        }

        if (node instanceof JsonArray) {
            gen.writeStartArray();
            JsonArray ja = (JsonArray) node;
            for (int i = 0, len = ja.size(); i < len; i++) {
                Object v = ja.getNode(i);
                writeNode(gen, v);
            }
            gen.writeEndArray();
            return;
        }

        if (node.getClass().isArray()) {
            gen.writeStartArray();
            int len = Array.getLength(node);
            for (int i = 0; i < len; i++) {
                writeNode(gen, Array.get(node, i));
            }
            gen.writeEndArray();
            return;
        }

        if (node instanceof Set) {
            gen.writeStartArray();
            for (Object v : (Set<?>) node) {
                writeNode(gen, v);
            }
            gen.writeEndArray();
            return;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            gen.writeStartObject();
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
                gen.writeFieldName(entry.getKey());
                Object vv = entry.getValue().invokeGetter(node);
                writeNode(gen, vv);
            }
            gen.writeEndObject();
            return;
        }

        throw new IllegalStateException("Unsupported node type " + node.getClass().getName());
    }

    /// Writer

//    public static void startDocument(JsonGenerator gen) throws IOException {
//        // Nothing
//    }
//
//    public static void endDocument(JsonGenerator gen) throws IOException {
//        // Nothing
//    }
//
//    public static void startObject(JsonGenerator gen) throws IOException {
//        gen.writeStartObject();
//    }
//
//    public static void endObject(JsonGenerator gen) throws IOException {
//        gen.writeEndObject();
//    }
//
//    public static void startArray(JsonGenerator gen) throws IOException {
//        gen.writeStartArray();
//    }
//
//    public static void endArray(JsonGenerator gen) throws IOException {
//        gen.writeEndArray();
//    }
//
//    public static void writeName(JsonGenerator gen, String name) throws IOException {
//        gen.writeFieldName(name);
//    }

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

}
