package org.sjf4j.facade.gson;

import com.google.gson.Gson;
import com.google.gson.ToNumberStrategy;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeRegistry;
import org.sjf4j.util.NumberUtil;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.util.Map;

public class GsonModule {

    public static class MyTypeAdapterFactory implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<?> rawClazz = type.getRawType();
            if (JsonObject.class.isAssignableFrom(rawClazz)) {
                return (TypeAdapter<T>) new JsonObjectAdapter(gson, rawClazz);
            }
            if (JsonArray.class.isAssignableFrom(rawClazz)) {
                return (TypeAdapter<T>) new JsonArrayAdapter(gson, rawClazz);
            }

            NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
            if (ci != null) {
                return new ConvertibleAdapter<>(gson, rawClazz, ci);
            }
            return null;
        }
    }

    public static class JsonObjectAdapter extends TypeAdapter<JsonObject> {
        private final Gson gson;
        private final NodeRegistry.PojoInfo pi;

        public JsonObjectAdapter(Gson gson, Class<?> clazz) {
            this.gson = gson;
            if (clazz == JsonObject.class) {
                this.pi = null;
            } else {
                this.pi = NodeRegistry.registerPojoOrElseThrow(clazz);
            }
        }

        @Override
        public JsonObject read(JsonReader in) throws IOException {
            JsonObject jo = pi == null ? new JsonObject() : (JsonObject) pi.newInstance();
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                NodeRegistry.FieldInfo fi;
                if (pi != null && (fi = pi.getFields().get(name)) != null) {
                    TypeAdapter<?> adapter = gson.getAdapter(TypeUtil.getRawClass(fi.getType()));
                    Object value = adapter.read(in);
                    fi.invokeSetter(jo, value);
                } else {
                    TypeAdapter<?> adapter = gson.getAdapter(Object.class);
                    Object value = adapter.read(in);
                    jo.put(name, value);
                }
            }
            in.endObject();
            return jo;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void write(JsonWriter out, JsonObject jo) throws IOException {
            out.beginObject();
            for (Map.Entry<String, Object> entry : jo.entrySet()) {
                out.name(entry.getKey());
                Object value = entry.getValue();
                TypeAdapter<Object> adapter = (TypeAdapter<Object>) gson.getAdapter(value.getClass());
                adapter.write(out, value);
            }
            out.endObject();
        }
    }


    public static class JsonArrayAdapter extends TypeAdapter<JsonArray> {
        private final Gson gson;

        public JsonArrayAdapter(Gson gson, Class<?> clazz) {
            this.gson = gson;
        }

        @Override
        public JsonArray read(JsonReader in) throws IOException {
            JsonArray ja = new JsonArray();
            in.beginArray();
            TypeAdapter<?> adapter = gson.getAdapter(Object.class);
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


    public static class ConvertibleAdapter<T> extends TypeAdapter<T> {
        private final Gson gson;
        private final NodeRegistry.ConvertibleInfo ci;

        public ConvertibleAdapter(Gson gson, Class<?> clazz, NodeRegistry.ConvertibleInfo ci) {
            this.gson = gson;
            this.ci = ci;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T read(JsonReader in) throws IOException {
            TypeAdapter<?> adapter = gson.getAdapter(Object.class);
            Object raw = adapter.read(in);
            return (T) ci.unconvert(raw);
        }

        @Override
        public void write(JsonWriter out, T node) throws IOException {
            Object raw = ci.convert(node);
            TypeAdapter<Object> adapter = gson.getAdapter(Object.class);
            adapter.write(out, raw);
        }
    }


    /// To Number

    public static class MyToNumberStrategy implements ToNumberStrategy {
        @Override
        public Number readNumber(JsonReader in) throws IOException {
            return NumberUtil.toNumber(in.nextString());
        }
    }

}
