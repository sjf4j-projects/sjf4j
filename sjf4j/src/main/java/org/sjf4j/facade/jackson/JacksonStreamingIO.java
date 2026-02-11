package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.node.Numbers;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
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

    public static void skipNode(JsonParser parser) throws IOException {
        JsonToken tk = parser.currentToken();
        if (tk.isScalarValue()) parser.nextToken();
        else {
            parser.skipChildren();
            parser.nextToken();
        }
    }

    public static Object readNode(JsonParser parser, Type type) throws IOException {
        StreamingReader.Token token = peekToken(parser);
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
        Class<?> rawClazz = Types.rawBox(type);
        parser.nextToken();

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            return vci.decode(null);
        }

        return  null;
    }

    public static Object readBoolean(JsonParser parser, Type type) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);
        if (rawClazz.isAssignableFrom(Boolean.class)) {
            Boolean b = parser.getBooleanValue();
            parser.nextToken();
            return b;
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Boolean b = parser.getBooleanValue();
            parser.nextToken();
            return vci.decode(b);
        }
        throw new JsonException("Cannot deserialize JSON Boolean into type " + rawClazz.getName());
    }

    public static Object readNumber(JsonParser parser, Type type) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);
        if (rawClazz.isAssignableFrom(Number.class)) {
            Number n = parser.getNumberValue();
            parser.nextToken();
            return n;
        }
        if (Number.class.isAssignableFrom(rawClazz)) {
            Number n = parser.getNumberValue();
            parser.nextToken();
            return Numbers.to(n, rawClazz);
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Number n = parser.getNumberValue();
            parser.nextToken();
            return vci.decode(n);
        }
        throw new JsonException("Cannot deserialize JSON Number into type " + rawClazz.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object readString(JsonParser parser, Type type) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);
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
        if (rawClazz.isEnum()) {
            String s = parser.getText();
            parser.nextToken();
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            String s = parser.getText();
            parser.nextToken();
            return vci.decode(s);
        }

        throw new JsonException("Cannot deserialize JSON String into type " + rawClazz.getName());
    }


    public static Object readObject(JsonParser parser, Type type) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);

        if (rawClazz == Object.class || rawClazz == Map.class) {
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_OBJECT) {
                String key = parser.currentName();parser.nextToken();
                Object value = readNode(parser, vt);
                map.put(key, value);
            }
            parser.nextToken();
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_OBJECT) {
                String key = parser.currentName();parser.nextToken();
                Object value = readNode(parser, Object.class);
                jo.put(key, value);
            }
            parser.nextToken();
            return jo;
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
        NodeRegistry.ValueCodecInfo vci = ti.valueCodecInfo;
        if (vci != null) {
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_OBJECT) {
                String key = parser.currentName();parser.nextToken();
                Object value = readNode(parser, vt);
                map.put(key, value);
            }
            parser.nextToken();
            return vci.decode(map);
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null && !pi.isJajo) {
            NodeRegistry.CreatorInfo ci = pi.creatorInfo;
            Object pojo = ci.noArgsCtorHandle == null ? null : ci.newPojoNoArgs();
            Object[] args = ci.noArgsCtorHandle == null ? new Object[ci.argNames.length] : null;
            int remainingArgs = ci.noArgsCtorHandle == null ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;
            Map<String, Object> dynamicMap = null;

            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_OBJECT) {
                String key = parser.currentName();parser.nextToken();

                int argIdx = -1;
                if (pojo == null) {
                    argIdx = ci.getArgIndex(key);
                    if (argIdx < 0 && ci.aliasMap != null) {
                        String origin = ci.aliasMap.get(key); // alias -> origin
                        if (origin != null) {
                            argIdx = ci.getArgIndex(origin);
                        }
                    }
                }
                if (argIdx >= 0) {
                    Type argType = ci.argTypes[argIdx];
                    assert args != null;
                    args[argIdx] = readNode(parser, argType);
                    remainingArgs--;
                    if (remainingArgs == 0) {
                        pojo = ci.newPojoWithArgs(args);
                        for (int i = 0; i < pendingSize; i++) {
                            pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                        }
                        pendingSize = 0;
                    }
                    continue;
                }

                NodeRegistry.FieldInfo fi = pi.aliasFields != null ? pi.aliasFields.get(key) : pi.fields.get(key);
                if (fi != null) {
                    Object vv = readField(parser, fi);
                    if (pojo != null) {
                        fi.invokeSetterIfPresent(pojo, vv);
                    } else {
                        if (pendingFields == null) {
                            int cap = pi.fieldCount;
                            pendingFields = new NodeRegistry.FieldInfo[cap];
                            pendingValues = new Object[cap];
                        }
                        pendingFields[pendingSize] = fi;
                        pendingValues[pendingSize] = vv;
                        pendingSize++;
                    }
                    continue;
                }

                if (pi.isJojo) {
                    if (dynamicMap == null) dynamicMap = Sjf4jConfig.global().mapSupplier.create();
                    Object vv = readNode(parser, Object.class);
                    dynamicMap.put(key, vv);
                } else {
                    skipNode(parser);
                }
            }
            parser.nextToken();

            if (pojo == null) {
                pojo = ci.newPojoWithArgs(args);
                for (int i = 0; i < pendingSize; i++) {
                    pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                }
            }
            if (pi.isJojo) ((JsonObject) pojo).setDynamicMap(dynamicMap);
            return pojo;
        }

        throw new JsonException("Cannot deserialize JSON Object into type " + rawClazz.getName());
    }


    public static Object readArray(JsonParser parser, Type type) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);

        if (rawClazz == Object.class || rawClazz == List.class) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            return readListWithElementType(parser, vt);
        }

        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_ARRAY) {
                Object value = readNode(parser, Object.class);
                ja.add(value);
            }
            parser.nextToken();
            return ja;
        }

        if (rawClazz == Set.class) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            return readSetWithElementType(parser, vt);
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray ja = (JsonArray) pi.creatorInfo.forceNewPojo();
            Class<?> elemType = ja.elementType();
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_ARRAY) {
                Object value = readNode(parser, elemType);
                ja.add(value);
            }
            parser.nextToken();
            return ja;
        }

        if (rawClazz.isArray()) {
            Class<?> valueClazz = rawClazz.getComponentType();
            List<Object> list = new ArrayList<>();
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_ARRAY) {
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

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = readListWithElementType(parser, vt);
            return vci.decode(list);
        }

        throw new JsonException("Cannot deserialize JSON Array into type " + rawClazz.getName());
    }

    private static Object readField(JsonParser parser, NodeRegistry.FieldInfo fi) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return readNull(parser, fi.rawType);
        }
        switch (fi.containerKind) {
            case MAP:
                return readMapWithValueType(parser, fi.argType);
            case LIST:
                return readListWithElementType(parser, fi.argType);
            case SET:
                return readSetWithElementType(parser, fi.argType);
            default:
                return readNode(parser, fi.type);
        }
    }

    private static Map<String, Object> readMapWithValueType(JsonParser parser, Type vt) throws IOException {
        Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_OBJECT) {
            String key = parser.currentName();
            parser.nextToken();
            Object value = readNode(parser, vt);
            map.put(key, value);
        }
        parser.nextToken();
        return map;
    }

    private static List<Object> readListWithElementType(JsonParser parser, Type vt) throws IOException {
        List<Object> list = new ArrayList<>();
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_ARRAY) {
            Object value = readNode(parser, vt);
            list.add(value);
        }
        parser.nextToken();
        return list;
    }

    private static Set<Object> readSetWithElementType(JsonParser parser, Type vt) throws IOException {
        Set<Object> set = Sjf4jConfig.global().setSupplier.create();
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_ARRAY) {
            Object value = readNode(parser, vt);
            set.add(value);
        }
        parser.nextToken();
        return set;
    }


    /// Reader

    public static StreamingReader.Token peekToken(JsonParser parser) throws IOException {
        JsonToken tk = parser.currentToken();
        if (tk == null) tk = parser.nextToken();
        switch (tk) {
            case START_OBJECT:
                return StreamingReader.Token.START_OBJECT;
            case END_OBJECT:
                return StreamingReader.Token.END_OBJECT;
            case START_ARRAY:
                return StreamingReader.Token.START_ARRAY;
            case END_ARRAY:
                return StreamingReader.Token.END_ARRAY;
            case VALUE_STRING:
                return StreamingReader.Token.STRING;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return StreamingReader.Token.NUMBER;
            case VALUE_TRUE:
            case VALUE_FALSE:
                return StreamingReader.Token.BOOLEAN;
            case VALUE_NULL:
                return StreamingReader.Token.NULL;
            default:
                return StreamingReader.Token.UNKNOWN;
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
        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Object raw = vci.encode(node);
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
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
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
