package org.sjf4j.facade.gson;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.node.Numbers;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.node.Types;

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

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            return ci.decode(null);
        }

        return  null;
    }

    public static Object readBoolean(JsonReader reader, Type type) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);
        if (rawClazz.isAssignableFrom(Boolean.class)) {
            return reader.nextBoolean();
        }

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            Boolean b = reader.nextBoolean();
            return ci.decode(b);
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

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            Number n = Numbers.asNumber(reader.nextString());
            return ci.decode(n);
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

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            String s = reader.nextString();
            return ci.decode(s);
        }

        if (rawClazz.isEnum()) {
            String s = reader.nextString();
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }
        throw new JsonException("Cannot deserialize JSON String into type " + rawClazz.getName());
    }


    public static Object readObject(JsonReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("Reader must not be null");
        Class<?> rawClazz = Types.rawBox(type);

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
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

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null && !JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.CreatorInfo ci = pi.getCreatorInfo();
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            Map<String, NodeRegistry.FieldInfo> aliasFields = pi.getAliasFields();
            boolean useArgsCreator = !ci.hasNoArgsCtor();
            Object pojo = useArgsCreator ? null : ci.newPojoNoArgs();
            Object[] args = useArgsCreator ? new Object[ci.getArgNames().length] : null;
            int remainingArgs = useArgsCreator ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;
            Map<String, Object> dynamicMap = null;
            boolean isJojo = JsonObject.class.isAssignableFrom(pi.getType());

            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();

                int argIdx = -1;
                if (pojo == null) {
                    argIdx = ci.getArgIndex(key);
                    if (argIdx < 0 && ci.getAliasMap() != null) {
                        String origin = ci.getAliasMap().get(key); // alias -> origin
                        if (origin != null) {
                            argIdx = ci.getArgIndex(origin);
                        }
                    }
                }
                if (argIdx >= 0) {
                    Type argType = ci.getArgTypes()[argIdx];
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

                NodeRegistry.FieldInfo fi = aliasFields != null ? aliasFields.get(key) : fields.get(key);
                if (fi != null) {
                    Object vv = readNode(reader, fi.getType());
                    if (pojo != null) {
                        fi.invokeSetterIfPresent(pojo, vv);
                    } else {
                        if (pendingFields == null) {
                            int cap = fields.size();
                            pendingFields = new NodeRegistry.FieldInfo[cap];
                            pendingValues = new Object[cap];
                        }
                        pendingFields[pendingSize] = fi;
                        pendingValues[pendingSize] = vv;
                        pendingSize++;
                    }
                    continue;
                }

                if (isJojo) {
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
            if (isJojo) ((JsonObject) pojo).setDynamicMap(dynamicMap);
            return pojo;
        }

        throw new JsonException("Cannot deserialize Object value into type " + rawClazz.getName());
    }


    public static Object readArray(JsonReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("reader must not be null");
        Class<?> rawClazz = Types.rawBox(type);

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (rawClazz.isAssignableFrom(List.class) || ci != null) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            reader.beginArray();
            while (reader.peek() != JsonToken.END_ARRAY) {
                Object value = readNode(reader, valueType);
                list.add(value);
            }
            reader.endArray();
            return ci != null ? ci.decode(list) : list;
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

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray ja = (JsonArray) pi.getCreatorInfo().forceNewPojo();
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

        if (Set.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Set<Object> set = Sjf4jConfig.global().setSupplier.create();
            reader.beginArray();
            while (reader.peek() != JsonToken.END_ARRAY) {
                Object value = readNode(reader, valueType);
                set.add(value);
            }
            reader.endArray();
            return set;
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
        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Object raw = vci.encode(node);
            writeNode(writer, raw);
            return;
        }

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

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            writer.beginObject();
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
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
