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
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Map;

public interface GsonModule {

    class MyTypeAdapterFactory implements TypeAdapterFactory {
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

            NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
            if (vci != null) {
                return new NodeValueAdapter<>(gson, vci);
            }
            return null;
        }
    }

    class JsonObjectAdapter<T extends JsonObject> extends TypeAdapter<T> {
        private final Gson gson;
        private final NodeRegistry.PojoInfo pi;
        private final TypeAdapter<?> objectAdapter;

        public JsonObjectAdapter(Gson gson, Class<?> clazz) {
            this.gson = gson;
            this.pi = clazz == JsonObject.class ? null : NodeRegistry.registerPojoOrElseThrow(clazz);
            this.objectAdapter = gson.getAdapter(Object.class);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T read(JsonReader in) throws IOException {
            if (pi == null) {
                JsonObject jo = new JsonObject();
                in.beginObject();
                while (in.hasNext()) {
                    String name = in.nextName();
                    Object value = objectAdapter.read(in);
                    jo.put(name, value);
                }
                in.endObject();
                return (T) jo;
            }

            NodeRegistry.CreatorInfo ci = pi.creatorInfo;
            Object pojo = ci.noArgsCtorHandle == null ? null : ci.newPojoNoArgs();
            Object[] args = ci.noArgsCtorHandle == null ? new Object[ci.argNames.length] : null;
            int remainingArgs = ci.noArgsCtorHandle == null ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;
            Map<String, Object> dynamicMap = null;

            in.beginObject();
            while (in.hasNext()) {
                String key = in.nextName();

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
                    args[argIdx] = gson.getAdapter(Types.rawClazz(argType)).read(in);
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
                    Object vv = gson.getAdapter(Types.rawClazz(fi.type)).read(in);
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

                if (dynamicMap == null) dynamicMap = Sjf4jConfig.global().mapSupplier.create();
                Object vv = objectAdapter.read(in);
                dynamicMap.put(key, vv);
            }
            in.endObject();

            if (pojo == null) {
                pojo = ci.newPojoWithArgs(args);
                for (int i = 0; i < pendingSize; i++) {
                    pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                }
            }
            ((JsonObject) pojo).setDynamicMap(dynamicMap);
            return (T) pojo;
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


    class JsonArrayAdapter<T extends JsonArray> extends TypeAdapter<T> {
        private final Gson gson;
        private final NodeRegistry.PojoInfo pi;

        public JsonArrayAdapter(Gson gson, Class<?> clazz) {
            this.gson = gson;
            this.pi = clazz == JsonArray.class ? null : NodeRegistry.registerPojoOrElseThrow(clazz);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T read(JsonReader in) throws IOException {
            T ja = pi == null ? (T) new JsonArray() : (T) pi.creatorInfo.forceNewPojo();
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
            for (int i = 0, len = ja.size(); i < len; i++) {
                Object v =  ja.getNode(i);
                adapter.write(out, v);
            }
            out.endArray();
        }
    }


    class NodeValueAdapter<T> extends TypeAdapter<T> {
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
    class MyToNumberStrategy implements ToNumberStrategy {
        @Override
        public Number readNumber(JsonReader in) throws IOException {
            return Numbers.parseNumber(in.nextString());
        }
    }

    /// NodeProperty
    class NodeFieldNamingStrategy implements FieldNamingStrategy {
        @Override
        public String translateName(Field field) {
            NodeProperty nf = field.getAnnotation(NodeProperty.class);
            if (nf != null && !nf.value().isEmpty()) return nf.value();
            return field.getName();
        }
    }


}
