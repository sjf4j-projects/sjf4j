package org.sjf4j.util;

import lombok.NonNull;
import org.sjf4j.ConverterRegistry;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeConverter;
import org.sjf4j.PojoRegistry;
import org.sjf4j.facades.FacadeReader;
import org.sjf4j.facades.FacadeWriter;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreamingUtil {

    /// Read

    public static Object readNode(@NonNull FacadeReader reader, Type type) throws IOException {
        int tid = reader.peekTokenId();
        switch (tid) {
            case FacadeReader.ID_START_OBJECT:
                return readObject(reader, type);
            case FacadeReader.ID_START_ARRAY:
                return readArray(reader, type);
            case FacadeReader.ID_STRING:
//                return reader.nextString();
                return ConverterRegistry.tryPure2Wrap(reader.nextString(), type);
            case FacadeReader.ID_NUMBER:
//                return reader.nextNumber();
                return ConverterRegistry.tryPure2Wrap(reader.nextNumber(), type);
            case FacadeReader.ID_BOOLEAN:
//                return reader.nextBoolean();
                return ConverterRegistry.tryPure2Wrap(reader.nextBoolean(), type);
            case FacadeReader.ID_NULL:
                reader.nextNull();
//                return null;
                return ConverterRegistry.tryPure2Wrap(null, type);
            default:
                throw new JsonException("Unexpected token id '" + tid + "'");
        }
    }

    @SuppressWarnings("unchecked")
    public static Object readObject(@NonNull FacadeReader reader, Type type) throws IOException {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        NodeConverter<?, ?> converter = ConverterRegistry.getConverter(rawClazz);

        if (converter != null ) {
            if (converter.getPureType() == JsonObject.class) {
                JsonObject jo = new JsonObject();
                reader.startObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    Object value = readNode(reader, Object.class);
                    jo.put(key, value);
                }
                reader.endObject();
                return ((NodeConverter<Object, Object>) converter).pure2Wrap(jo);
            } else {
                throw new JsonException("Converter expects object '" + converter.getWrapType() +
                        "' and node '" + converter.getPureType() + "', but got node 'JsonObject'");
            }
        }

        if (rawClazz == null || rawClazz.isAssignableFrom(JsonObject.class)) {
            JsonObject jo = new JsonObject();
            reader.startObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                Object value = readNode(reader, Object.class);
                jo.put(key, value);
            }
            reader.endObject();
//            return null;
            return jo;
        }

        if (rawClazz == Map.class) {
            Type valueType = TypeUtil.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = new HashMap<>();
            reader.startObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                Object value = readNode(reader, valueType);
                map.put(key, value);
            }
            reader.endObject();
            return map;
        }

        if (JsonObject.class.isAssignableFrom(rawClazz)) {
            PojoRegistry.PojoInfo pi = PojoRegistry.registerOrElseThrow(rawClazz);
            Map<String, PojoRegistry.FieldInfo> fields = pi.getFields();
            JsonObject jojo = (JsonObject) pi.newInstance();
            reader.startObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                PojoRegistry.FieldInfo fi = fields.get(key);
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

        if (PojoRegistry.isPojo(rawClazz)) {
            PojoRegistry.PojoInfo pi = PojoRegistry.registerOrElseThrow(rawClazz);
            Map<String, PojoRegistry.FieldInfo> fields = pi.getFields();
            Object pojo = pi.newInstance();
            reader.startObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                PojoRegistry.FieldInfo fi = fields.get(key);
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


    @SuppressWarnings("unchecked")
    public static Object readArray(@NonNull FacadeReader reader, Type type) throws IOException {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        NodeConverter<?, ?> converter = ConverterRegistry.getConverter(rawClazz);

        if (converter != null ) {
            if (converter.getPureType() == JsonArray.class) {
                JsonArray ja = new JsonArray();
                reader.startArray();
                while (reader.hasNext()) {
                    Object value = readNode(reader, Object.class);
                    ja.add(value);
                }
                reader.endArray();
                return ((NodeConverter<Object, Object>) converter).pure2Wrap(ja);
            } else {
                throw new JsonException("Converter expects object '" + converter.getWrapType() +
                        "' and node '" + converter.getPureType() + "', but got node 'JsonArray'");
            }
        }

        if (rawClazz == null || rawClazz.isAssignableFrom(JsonArray.class)) {
            JsonArray ja = new JsonArray();
            reader.startArray();
            while (reader.hasNext()) {
                Object value = readNode(reader, Object.class);
                ja.add(value);
            }
            reader.endArray();
            return ja;
        }

        if (rawClazz == List.class) {
            Type valueType = TypeUtil.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            reader.startArray();
            while (reader.hasNext()) {
                Object value = readNode(reader, valueType);
                list.add(value);
            }
            reader.endArray();
            return list;
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
            for (int i = 0; i < list.size(); i++) {
                Array.set(array, i, list.get(i));
            }
            return array;
        }

        throw new JsonException("Unsupported type: " + type);
    }



    /// Write

    public static void writeNode(@NonNull FacadeWriter writer, Object node) throws IOException {
        node = ConverterRegistry.tryWrap2Pure(node);
        if (node == null) {
            writer.writeNull();
        } else if (node instanceof CharSequence || node instanceof Character) {
            writer.writeValue(node.toString());
        } else if (node instanceof Number) {
            writer.writeValue((Number) node);
        } else if (node instanceof Boolean) {
            writer.writeValue((Boolean) node);
        } else if (node instanceof JsonObject) {
            writer.startObject();
            ((JsonObject) node).forEach((k, v) -> {
                try {
                    writer.writeName(k);
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
                writeNode(writer, entry.getValue());
            }
            writer.endObject();
        } else if (node instanceof JsonArray) {
            writer.startArray();
            JsonArray ja = (JsonArray) node;
            for (int i = 0; i < ja.size(); i++) {
                if (i > 0) writer.writeComma();
                writeNode(writer, ja.getObject(i));
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
        } else if (PojoRegistry.isPojo(node.getClass())) {
            writer.startObject();
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry :
                    PojoRegistry.getPojoInfo(node.getClass()).getFields().entrySet()) {
                writer.writeName(entry.getKey());
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


}
