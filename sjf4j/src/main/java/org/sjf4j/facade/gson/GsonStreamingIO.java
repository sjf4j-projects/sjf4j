package org.sjf4j.facade.gson;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.node.Numbers;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.node.Types;
import org.sjf4j.path.PathSegment;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * A Gson-specific, fully static implementation of the streaming JSON reader utilities.
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
 * perform constant folding, and optimize the reader loops specifically for the original API,
 * achieving lower per-call latency compared to the generic {@code StreamingUtil}.
 */
public class GsonStreamingIO {

    /// Read

    public static Object readNode(JsonReader reader, Type type) throws IOException {
        StreamingReader.Token token = peekToken(reader);
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
        Class<?> rawClazz = Types.rawBox(type);
        reader.nextNull();

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            return vci.decode(null);
        }

        return  null;
    }

    public static Object readBoolean(JsonReader reader, Type type) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);
        if (rawClazz.isAssignableFrom(Boolean.class)) {
            return reader.nextBoolean();
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Boolean b = reader.nextBoolean();
            return vci.decode(b);
        }
        throw new JsonException("Cannot deserialize JSON Boolean into type " + rawClazz.getName());
    }

    public static Object readNumber(JsonReader reader, Type type) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);
        if (rawClazz.isAssignableFrom(Number.class)) {
            return Numbers.asNumber(reader.nextString());
        }
        if (Number.class.isAssignableFrom(rawClazz)) {
            Number n = Numbers.asNumber(reader.nextString());
            return Numbers.to(n, rawClazz);
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Number n = Numbers.asNumber(reader.nextString());
            return vci.decode(n);
        }
        throw new JsonException("Cannot deserialize JSON Number into type " + rawClazz.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object readString(JsonReader reader, Type type) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);
        if (rawClazz.isAssignableFrom(String.class)) {
            return reader.nextString();
        }
        if (rawClazz == Character.class) {
            String s = reader.nextString();
            return s.length() > 0 ? s.charAt(0) : null;
        }
        if (rawClazz.isEnum()) {
            String s = reader.nextString();
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            String s = reader.nextString();
            return vci.decode(s);
        }

        throw new JsonException("Cannot deserialize JSON String into type " + rawClazz.getName());
    }


    public static Object readObject(JsonReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("Reader must not be null");
        Class<?> rawClazz = Types.rawBox(type);

        if (rawClazz == Object.class || rawClazz == Map.class) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            reader.beginObject();
            while (reader.peek() != JsonToken.END_OBJECT) {
                String key = reader.nextName();
                Object value = readNode(reader, valueType);
                map.put(key, value);
            }
            reader.endObject();
            return map;
        }

        if (rawClazz == JsonObject.class) {
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

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
        NodeRegistry.ValueCodecInfo vci = ti.valueCodecInfo;
        if (rawClazz.isAssignableFrom(Map.class) || vci != null) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            reader.beginObject();
            while (reader.peek() != JsonToken.END_OBJECT) {
                String key = reader.nextName();
                Object value = readNode(reader, valueType);
                map.put(key, value);
            }
            reader.endObject();
            return vci != null ? vci.decode(map) : map;
        }

        NodeRegistry.PojoInfo pi = ti.pojoInfo;
        if (pi != null && !pi.isJajo) {
            NodeRegistry.CreatorInfo ci = pi.creatorInfo;
            Object pojo = ci.noArgsCtorHandle == null ? null : ci.newPojoNoArgs();
            Object[] args = ci.noArgsCtorHandle == null ? new Object[ci.argNames.length] : null;
            int remainingArgs = ci.noArgsCtorHandle == null ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;
            Map<String, Object> dynamicMap = null;

            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();

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
                    args[argIdx] = readNode(reader, argType);
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
                    Object vv = readNode(reader, fi.type);
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
                    Object vv = readNode(reader, Object.class);
                    dynamicMap.put(key, vv);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();

            if (pojo == null) {
                pojo = ci.newPojoWithArgs(args);
                for (int i = 0; i < pendingSize; i++) {
                    pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                }
            }
            if (pi.isJojo) ((JsonObject) pojo).setDynamicMap(dynamicMap);
            return pojo;
        }

        throw new JsonException("Cannot deserialize Object value into type " + rawClazz.getName());
    }


    public static Object readArray(JsonReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("reader must not be null");
        Class<?> rawClazz = Types.rawBox(type);

        if (rawClazz == Object.class || rawClazz == List.class) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            reader.beginArray();
            while (reader.peek() != JsonToken.END_ARRAY) {
                Object value = readNode(reader, valueType);
                list.add(value);
            }
            reader.endArray();
            return list;
        }

        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            reader.beginArray();
            while (reader.peek() != JsonToken.END_ARRAY) {
                Object value = readNode(reader, Object.class);
                ja.add(value);
            }
            reader.endArray();
            return ja;
        }

        if (rawClazz == Set.class) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray ja = (JsonArray) pi.creatorInfo.forceNewPojo();
            reader.beginArray();
            while (reader.peek() != JsonToken.END_ARRAY) {
                Object value = readNode(reader, ja.elementType());
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

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            JsonArray ja = (JsonArray) NodeRegistry.registerPojoOrElseThrow(rawClazz).creatorInfo.forceNewPojo();
            int i = 0;
            reader.beginArray();
            while (reader.peek() != JsonToken.END_ARRAY) {
                Object value = readNode(reader, Object.class);
                ja.add(value);
            }
            reader.endArray();
            return ja;
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            reader.beginArray();
            while (reader.peek() != JsonToken.END_ARRAY) {
                Object value = readNode(reader, valueType);
                list.add(value);
            }
            reader.endArray();
            return vci.decode(list);
        }

        throw new JsonException("Cannot deserialize JSON Array into type " + rawClazz.getName());
    }


    /// Reader

    public static StreamingReader.Token peekToken(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT:
                return StreamingReader.Token.START_OBJECT;
            case END_OBJECT:
                return StreamingReader.Token.END_OBJECT;
            case BEGIN_ARRAY:
                return StreamingReader.Token.START_ARRAY;
            case END_ARRAY:
                return StreamingReader.Token.END_ARRAY;
            case STRING:
                return StreamingReader.Token.STRING;
            case NUMBER:
                return StreamingReader.Token.NUMBER;
            case BOOLEAN:
                return StreamingReader.Token.BOOLEAN;
            case NULL:
                return StreamingReader.Token.NULL;
            default:
                return StreamingReader.Token.UNKNOWN;
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
        if (node instanceof CharSequence || node instanceof Character) {
            writer.value(node.toString());
            return;
        }

        if (node instanceof Enum) {
            writer.value(((Enum<?>) node).name());
            return;
        }

        if (node instanceof Number) {
            writer.value((Number) node);
            return;
        }

        if (node instanceof Boolean) {
            writer.value((Boolean) node);
            return;
        }

        if (node instanceof Map) {
            writer.beginObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                writer.name(entry.getKey().toString());
                writeNode(writer, entry.getValue());
            }
            writer.endObject();
            return;
        }

        if (node instanceof List) {
            writer.beginArray();
            List<?> list = (List<?>) node;
            for (Object v : list) {
                writeNode(writer, v);
            }
            writer.endArray();
            return;
        }

        if (node instanceof JsonObject) {
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
            return;
        }

        if (node instanceof JsonArray) {
            writer.beginArray();
            JsonArray ja = (JsonArray) node;
            for (int i = 0; i < ja.size(); i++) {
                Object v = ja.getNode(i);
                writeNode(writer, v);
            }
            writer.endArray();
            return;
        }

        if (node.getClass().isArray()) {
            writer.beginArray();
            int len = Array.getLength(node);
            for (int i = 0; i < len; i++) {
                writeNode(writer, Array.get(node, i));
            }
            writer.endArray();
            return;
        }

        if (node instanceof Set) {
            writer.beginArray();
            for (Object v : (Set<?>) node) {
                writeNode(writer, v);
            }
            writer.endArray();
            return;
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
        NodeRegistry.ValueCodecInfo vci = ti.valueCodecInfo;
        if (vci != null) {
            Object raw = vci.encode(node);
            writeNode(writer, raw);
            return;
        }

        NodeRegistry.PojoInfo pi = ti.pojoInfo;
        if (pi != null) {
            writer.beginObject();
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                writer.name(entry.getKey());
                Object vv = entry.getValue().invokeGetter(node);
                writeNode(writer, vv);
            }
            writer.endObject();
            return;
        }

        throw new IllegalStateException("Unsupported node type '" + node.getClass().getName() + "'");
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
