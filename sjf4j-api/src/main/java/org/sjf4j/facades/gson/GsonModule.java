package org.sjf4j.facades.gson;

import com.google.gson.Gson;
import com.google.gson.ToNumberStrategy;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.PojoRegistry;
import org.sjf4j.util.NumberUtil;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.util.Map;

public class GsonModule {

    public static class MyTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<?> raw = type.getRawType();
            if (JsonObject.class.isAssignableFrom(raw)) {
//                return null;
                return new JsonObjectTypeAdapter<>(gson, type);
            }
            if (JsonArray.class.isAssignableFrom(raw)) {
                return new JsonArrayTypeAdapter<>(gson, type);
            }
            return null;
        }
    }

    public static class JsonObjectTypeAdapter<T> extends TypeAdapter<T> {
        private final Gson gson;
        private final PojoRegistry.PojoInfo pi;

        public JsonObjectTypeAdapter(Gson gson, TypeToken<T> type) {
            this.gson = gson;
            Class<? super T> clazz = type.getRawType();
            if (clazz == JsonObject.class) {
                this.pi = null;
            } else {
                this.pi = PojoRegistry.registerOrElseThrow(type.getRawType());
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public T read(JsonReader in) throws IOException {
            JsonObject jo = pi == null ? new JsonObject() : (JsonObject) pi.newInstance();
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                PojoRegistry.FieldInfo fi;
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
            return (T) jo;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void write(JsonWriter out, T node) throws IOException {
            JsonObject jo = (JsonObject) node;
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


    public static class JsonArrayTypeAdapter<T> extends TypeAdapter<T> {
        private final Gson gson;

        public JsonArrayTypeAdapter(Gson gson, TypeToken<T> type) {
            this.gson = gson;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T read(JsonReader in) throws IOException {
            JsonArray ja = new JsonArray();
            in.beginArray();
            TypeAdapter<?> adapter = gson.getAdapter(Object.class);
            while (in.hasNext()) {
                Object value = adapter.read(in);
                ja.add(value);
            }
            in.endArray();
            return (T) ja;
        }

        @Override
        public void write(JsonWriter out, T node) throws IOException {
            JsonArray ja = (JsonArray) node;
            out.beginArray();
            TypeAdapter<Object> adapter = gson.getAdapter(Object.class);
            for (Object v : ja) {
                adapter.write(out, v);
            }
            out.endArray();
        }
    }


    public static class MyToNumberStrategy implements ToNumberStrategy {
        @Override
        public Number readNumber(JsonReader in) throws IOException {
            return NumberUtil.toNumber(in.nextString());
        }
    }

}
