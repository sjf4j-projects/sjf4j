package org.sjf4j.facade.gson;

import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.ToNumberStrategy;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeField;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public interface GsonModule {

    public static class MyTypeAdapterFactory implements TypeAdapterFactory {
        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<?> rawClazz = type.getRawType();
            if (JsonObject.class.isAssignableFrom(rawClazz)) {
                return (TypeAdapter<T>) new JsonObjectAdapter(gson, rawClazz);
            }
            if (JsonArray.class.isAssignableFrom(rawClazz)) {
                return (TypeAdapter<T>) new JsonArrayAdapter(gson, rawClazz);
            }

            NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
            if (ci != null) {
                return new NodeValueAdapter<>(gson, ci);
            }
            return null;
        }
    }

    public static class JsonObjectAdapter<T extends JsonObject> extends TypeAdapter<T> {
        private final Gson gson;
        private final NodeRegistry.PojoInfo pi;
        private final Map<String, TypeAdapter<?>> fieldAdapters;
        private final TypeAdapter<?> objectAdapter;

        public JsonObjectAdapter(Gson gson, Class<?> clazz) {
            this.gson = gson;
            this.pi = clazz == JsonObject.class ? null : NodeRegistry.registerPojoOrElseThrow(clazz);
            if (pi != null) {
                Map<String, TypeAdapter<?>> map = new HashMap<>();
                for (Map.Entry<String, NodeRegistry.FieldInfo> e : pi.getFields().entrySet()) {
                    map.put(e.getKey(), gson.getAdapter(Types.getRawClass(e.getValue().getType())));
                }
                this.fieldAdapters = map;
            } else {
                this.fieldAdapters = null;
            }
            this.objectAdapter = gson.getAdapter(Object.class);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T read(JsonReader in) throws IOException {
            JsonObject jo = pi == null ? new JsonObject() : (JsonObject) pi.newInstance();
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                NodeRegistry.FieldInfo fi;
                if (pi != null && (fi = pi.getFields().get(name)) != null) {
                    TypeAdapter<?> adapter = fieldAdapters.get(name);
                    Object value = adapter.read(in);
                    fi.invokeSetter(jo, value);
                } else {
                    Object value = objectAdapter.read(in);
                    jo.put(name, value);
                }
            }
            in.endObject();
            return (T) jo;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void write(JsonWriter out, JsonObject jo) throws IOException {
            out.beginObject();
            for (Map.Entry<String, Object> entry : jo.entrySet()) {
                out.name(entry.getKey());
                Object value = entry.getValue();
                if (value == null) {
                    out.nullValue();
                } else {
                    TypeAdapter<Object> adapter = (TypeAdapter<Object>) gson.getAdapter(value.getClass());
                    adapter.write(out, value);
                }
            }
            out.endObject();
        }
    }


    public static class JsonArrayAdapter<T extends JsonArray> extends TypeAdapter<T> {
        private final Gson gson;
        private final NodeRegistry.PojoInfo pi;

        public JsonArrayAdapter(Gson gson, Class<?> clazz) {
            this.gson = gson;
            this.pi = clazz == JsonArray.class ? null : NodeRegistry.registerPojoOrElseThrow(clazz);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T read(JsonReader in) throws IOException {
            T ja = pi == null ? (T) new JsonArray() : (T) pi.newInstance();
            in.beginArray();
            TypeAdapter<?> adapter = gson.getAdapter(ja.elementType());
            while (in.hasNext()) {
                Object value = adapter.read(in);
                ja.add(value);
            }
            in.endArray();
            return ja;
        }

        @Override
        public void write(JsonWriter out, JsonArray ja) throws IOException {
            out.beginArray();
            TypeAdapter<Object> adapter = gson.getAdapter(Object.class);
            for (Object v : ja) {
                adapter.write(out, v);
            }
            out.endArray();
        }
    }


    public static class NodeValueAdapter<T> extends TypeAdapter<T> {
        private final Gson gson;
        private final NodeRegistry.ValueCodecInfo valueCodecInfo;

        public NodeValueAdapter(Gson gson, NodeRegistry.ValueCodecInfo valueCodecInfo) {
            this.gson = gson;
            this.valueCodecInfo = valueCodecInfo;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T read(JsonReader in) throws IOException {
            TypeAdapter<?> adapter = gson.getAdapter(Object.class);
            Object raw = adapter.read(in);
            return (T) valueCodecInfo.decode(raw);
        }

        @Override
        public void write(JsonWriter out, T node) throws IOException {
            Object raw = valueCodecInfo.encode(node);
            TypeAdapter<Object> adapter = gson.getAdapter(Object.class);
            adapter.write(out, raw);
        }
    }

    /// To Number
    public static class MyToNumberStrategy implements ToNumberStrategy {
        @Override
        public Number readNumber(JsonReader in) throws IOException {
            return Numbers.toNumber(in.nextString());
        }
    }

    /// NodeField
    class NodeFieldNamingStrategy implements FieldNamingStrategy {
        @Override
        public String translateName(Field field) {
            NodeField nf = field.getAnnotation(NodeField.class);
            if (nf != null && !nf.value().isEmpty()) return nf.value();
            return field.getName();
        }
    }


}
