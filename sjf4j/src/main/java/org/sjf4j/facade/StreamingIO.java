package org.sjf4j.facade;

import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class for streaming JSON processing. Provides methods for reading JSON nodes from
 * a {@link FacadeReader} and writing JSON nodes to a {@link FacadeWriter}, with support
 * for type conversion and POJO handling.
 */
public class StreamingIO {

    /// Read

    /**
     * Reads a JSON node from the provided {@link FacadeReader} and converts it to the appropriate
     * Java object based on the token type and optional target type.
     *
     * @param reader the FacadeReader to read from
     * @param type the target type for conversion (may be null)
     * @return the parsed and converted JSON node
     * @throws IOException if an I/O error occurs during reading
     * @throws JsonException if an unexpected token is encountered
     */
    public static Object readNode(FacadeReader reader, Type type) throws IOException {
        if (reader == null) throw new IllegalArgumentException("Reader must not be null");
        FacadeReader.Token token = reader.peekToken();
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

    public static Object readNull(FacadeReader reader, Type type) throws IOException {
        Class<?> rawClazz = Types.rawClazz(type);
        reader.nextNull();

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            return ci.decode(null);
        }
        return null;
    }

    public static Object readBoolean(FacadeReader reader, Type type) throws IOException {
        Class<?> rawClazz = Types.rawClazz(type);
        if (rawClazz.isAssignableFrom(Boolean.class)) {
            return reader.nextBoolean();
        }

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            Boolean b = reader.nextBoolean();
            return ci.decode(b);
        }
        throw new JsonException("Cannot deserialize Boolean value into type " + rawClazz.getName());
    }

    public static Object readNumber(FacadeReader reader, Type type) throws IOException {
        Class<?> rawClazz = Types.rawClazz(type);
        if (rawClazz.isAssignableFrom(Number.class)) {
            return reader.nextNumber();
        }
        if (Number.class.isAssignableFrom(rawClazz)) {
            Number n = reader.nextNumber();
            return Numbers.to(n, rawClazz);
        }

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            Number n = reader.nextNumber();
            return ci.decode(n);
        }
        throw new JsonException("Cannot deserialize Number value into type " + rawClazz.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object readString(FacadeReader reader, Type type) throws IOException {
        Class<?> rawClazz = Types.rawClazz(type);
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
        throw new JsonException("Cannot deserialize String value into type " + rawClazz.getName());
    }

    /**
     * Reads a JSON object from the provided {@link FacadeReader} and converts it to the appropriate
     * Java object based on the target type.
     *
     * @param reader the FacadeReader to read from
     * @param type the target type for conversion
     * @return the parsed and converted JSON object
     * @throws IOException if an I/O error occurs during reading
     */
    public static Object readObject(FacadeReader reader, Type type) throws IOException {
        Objects.requireNonNull(reader, "reader is null");
        Class<?> rawClazz = Types.rawClazz(type);

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (rawClazz.isAssignableFrom(Map.class) || ci != null) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            reader.startObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                Object value = readNode(reader, valueType);
                map.put(key, value);
            }
            reader.endObject();
            return ci != null ? ci.decode(map) : map;
        }

        if (rawClazz.isAssignableFrom(JsonObject.class)) {
            JsonObject jo = new JsonObject();
            reader.startObject();
            while (reader.hasNext()) {
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
            reader.startObject();
            while (reader.hasNext()) {
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
            reader.startObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                NodeRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(reader, fi.getType());
                    fi.invokeSetter(pojo, vv);
                } else {
                    throw new JsonException("Undefined field '" + key + "' in POJO '" + pi.getType().getName() + "'");
                }
            }
            reader.endObject();
            return pojo;
        }
        throw new JsonException("Cannot deserialize Object value into type " + rawClazz.getName());
    }


    public static Object readArray(FacadeReader reader, Type type) throws IOException {
        Objects.requireNonNull(reader, "reader is null");
        Class<?> rawClazz = Types.rawClazz(type);

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (rawClazz.isAssignableFrom(List.class) || ci != null) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            reader.startArray();
            while (reader.hasNext()) {
                Object value = readNode(reader, valueType);
                list.add(value);
            }
            reader.endArray();
            return ci != null ? ci.decode(list) : list;
        }

        if (rawClazz.isAssignableFrom(JsonArray.class)) {
            JsonArray ja = new JsonArray();
            reader.startArray();
            while (reader.hasNext()) {
                Object value = readNode(reader, Object.class);
                ja.add(value);
            }
            reader.endArray();
            return ja;
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray ja = (JsonArray) pi.newInstance();
            reader.startArray();
            while (reader.hasNext()) {
                Object value = readNode(reader, ja.elementType());
                ja.add(value);
            }
            reader.endArray();
            return ja;
        }

        if (rawClazz.isArray()) {
            Class<?> valueClazz = rawClazz.getComponentType();
            List<Object> list = new ArrayList<>();
            reader.startArray();
            while (reader.hasNext()) {
                Object value = readNode(reader, valueClazz);
                list.add(value);
            }
            reader.endArray();

            Object array = Array.newInstance(valueClazz, list.size());
            for (int i = 0, len = list.size(); i < len; i++) {
                Array.set(array, i, list.get(i));
            }
            return array;
        }

        if (Set.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Set<Object> set = Sjf4jConfig.global().setSupplier.create();
            reader.startArray();
            while (reader.hasNext()) {
                Object value = readNode(reader, valueType);
                set.add(value);
            }
            reader.endArray();
            return set;
        }

        throw new JsonException("Cannot deserialize Array value into type " + rawClazz.getName());
    }


    /// Write

    public static void writeNode(FacadeWriter writer, Object node) throws IOException {
        if (writer == null) throw new IllegalArgumentException("Writer must not be null");
        if (node == null) {
            writer.writeNull();
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
            writer.writeString(node.toString());
            return;
        }
        if (node instanceof Enum) {
            writer.writeString(((Enum<?>) node).name());
            return;
        }

        if (node instanceof Number) {
            writer.writeNumber((Number) node);
            return;
        }

        if (node instanceof Boolean) {
            writer.writeBoolean((Boolean) node);
            return;
        }

        if (node instanceof Map) {
            writer.startObject();
            boolean veryStart = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                if (veryStart) veryStart = false;
                else writer.writeObjectComma();
                writer.writeName(entry.getKey().toString());
                writeNode(writer, entry.getValue());
            }
            writer.endObject();
            return;
        }

        if (node instanceof List) {
            writer.startArray();
            List<?> list = (List<?>) node;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) writer.writeArrayComma();
                writeNode(writer, list.get(i));
            }
            writer.endArray();
            return;
        }

        if (node instanceof JsonObject) {
            writer.startObject();
            final boolean[] veryStart = { true };
            ((JsonObject) node).forEach((k, v) -> {
                try {
                    if (veryStart[0]) veryStart[0] = false;
                    else writer.writeObjectComma();
                    writer.writeName(k);
                    writeNode(writer, v);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            writer.endObject();
            return;
        }

        if (node instanceof JsonArray) {
            writer.startArray();
            JsonArray ja = (JsonArray) node;
            for (int i = 0, len = ja.size(); i < len; i++) {
                if (i > 0) writer.writeArrayComma();
                writeNode(writer, ja.getNode(i));
            }
            writer.endArray();
            return;
        }

        if (node.getClass().isArray()) {
            writer.startArray();
            for (int i = 0, len = Array.getLength(node); i < len; i++) {
                if (i > 0) writer.writeArrayComma();
                writeNode(writer, Array.get(node, i));
            }
            writer.endArray();
            return;
        }

        if (node instanceof Set) {
            writer.startArray();
            boolean veryStart = true;
            for (Object v : (Set<?>) node) {
                if (veryStart) veryStart = false;
                else writer.writeArrayComma();
                writeNode(writer, v);
            }
            writer.endArray();
            return;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null) {
            writer.startObject();
            boolean veryStart = true;
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
                if (veryStart) veryStart = false;
                else writer.writeObjectComma();
                writer.writeName(entry.getKey());
                Object vv = entry.getValue().invokeGetter(node);
                writeNode(writer, vv);
            }
            writer.endObject();
            return;
        }

        throw new IllegalStateException("Unsupported node type '" + node.getClass().getName() + "'");
    }


}
