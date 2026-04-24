package org.sjf4j.facade.gson;

import com.google.gson.Gson;
import com.google.gson.ToNumberStrategy;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Gson adapters for JsonObject/JsonArray and @NodeValue types.
 */
public interface GsonModule {

    /**
     * Adapter factory for JsonObject/JsonArray and @NodeValue types.
     */
    class MyTypeAdapterFactory implements TypeAdapterFactory {
        private final StreamingContext streamingContext;

        public MyTypeAdapterFactory(StreamingContext streamingContext) {
            this.streamingContext = streamingContext;
        }

        /**
         * Returns adapter for supported framework types.
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<?> rawClazz = type.getRawType();
            if (JsonObject.class.isAssignableFrom(rawClazz)) {
                return (TypeAdapter<T>) new JsonObjectAdapter(gson, type.getType(), streamingContext);
            }
            if (JsonArray.class.isAssignableFrom(rawClazz)) {
                return (TypeAdapter<T>) new JsonArrayAdapter(gson, rawClazz);
            }

            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
            if (ti.anyOfInfo != null) {
                return new AnyOfAdapter<>(ti.anyOfInfo, streamingContext);
            }

            if (ti.hasValueCodecs()) {
                String valueFormat = streamingContext.valueFormatMapping.defaultValueFormat(rawClazz);
                NodeRegistry.ValueCodecInfo vci = ti.getFormattedValueCodecInfo(valueFormat);
                if (vci != null) {
                    return new NodeValueAdapter<>(gson, vci);
                }
            }

            if (ti.requiresPojoReader()) {
                return new PojoAdapter<>(type.getType(), ti.pojoInfo, streamingContext);
            }
            return null;
        }
    }

    class JsonObjectAdapter<T extends JsonObject> extends TypeAdapter<T> {
        private final Gson gson;
        private final Type ownerType;
        private final StreamingContext streamingContext;

        /**
         * Creates adapter for JsonObject or subclass.
         */
        public JsonObjectAdapter(Gson gson, Type ownerType, StreamingContext streamingContext) {
            this.gson = gson;
            this.ownerType = ownerType;
            this.streamingContext = streamingContext == null ? StreamingContext.EMPTY : streamingContext;
        }

        /**
         * Reads JSON object into JsonObject/JOJO instance.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return (T) StreamingIO.readNode(new GsonReader(in), ownerType, streamingContext);
        }

        /**
         * Writes JsonObject entries as JSON object fields.
         */
        @Override
        public void write(JsonWriter out, JsonObject jo) throws IOException {
            StreamingIO.writeNode(new GsonWriter(out), jo, streamingContext);
        }
    }


    class JsonArrayAdapter<T extends JsonArray> extends TypeAdapter<T> {
        private final Gson gson;
        private final NodeRegistry.PojoInfo pi;

        /**
         * Creates adapter for JsonArray or subclass.
         */
        public JsonArrayAdapter(Gson gson, Class<?> clazz) {
            this.gson = gson;
            this.pi = clazz == JsonArray.class ? null : NodeRegistry.registerPojoOrElseThrow(clazz);
        }

        /**
         * Reads JSON array into JsonArray/JAJO instance.
         */
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

        /**
         * Writes JsonArray elements as JSON array items.
         */
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


    class AnyOfAdapter<T> extends TypeAdapter<T> {
        private final NodeRegistry.AnyOfInfo anyOfInfo;
        private final StreamingContext streamingContext;

        public AnyOfAdapter(NodeRegistry.AnyOfInfo anyOfInfo) {
            this(anyOfInfo, StreamingContext.EMPTY);
        }

        public AnyOfAdapter(NodeRegistry.AnyOfInfo anyOfInfo, StreamingContext streamingContext) {
            this.anyOfInfo = anyOfInfo;
            this.streamingContext = streamingContext == null ? StreamingContext.EMPTY : streamingContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return (T) StreamingIO.readAnyOf(new GsonReader(in), anyOfInfo, streamingContext);
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException {
            StreamingIO.writeNode(new GsonWriter(out), value, streamingContext);
        }
    }

    class PojoAdapter<T> extends TypeAdapter<T> {
        private final Type ownerType;
        private final NodeRegistry.PojoInfo pojoInfo;
        private final StreamingContext streamingContext;

        public PojoAdapter(Type ownerType, NodeRegistry.PojoInfo pojoInfo, StreamingContext streamingContext) {
            this.ownerType = ownerType;
            this.pojoInfo = pojoInfo;
            this.streamingContext = streamingContext == null ? StreamingContext.EMPTY : streamingContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            Type resolvedOwnerType = ownerType != null ? ownerType : pojoInfo.clazz;
            Class<?> ownerRawClass = Types.rawBox(resolvedOwnerType);
            return (T) StreamingIO.readPojo(new GsonReader(in), resolvedOwnerType, ownerRawClass, pojoInfo, streamingContext);
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException {
            StreamingIO.writeNode(new GsonWriter(out), value, streamingContext);
        }
    }


    class NodeValueAdapter<T> extends TypeAdapter<T> {
        private final Gson gson;
        private final NodeRegistry.ValueCodecInfo valueCodecInfo;

        /**
         * Creates adapter backed by ValueCodec metadata.
         */
        public NodeValueAdapter(Gson gson, NodeRegistry.ValueCodecInfo valueCodecInfo) {
            this.gson = gson;
            this.valueCodecInfo = valueCodecInfo;
        }

        /**
         * Reads raw value and decodes via ValueCodec.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T read(JsonReader in) throws IOException {
            TypeAdapter<?> adapter = gson.getAdapter(Object.class);
            Object raw = adapter.read(in);
            return (T) valueCodecInfo.rawToValue(raw);
        }

        /**
         * Encodes value via ValueCodec and writes raw value.
         */
        @Override
        public void write(JsonWriter out, T node) throws IOException {
            Object raw = valueCodecInfo.valueToRaw(node);
            TypeAdapter<Object> adapter = gson.getAdapter(Object.class);
            adapter.write(out, raw);
        }
    }

    /// To Number
    /**
     * Number strategy that preserves integer/decimal intent.
     */
    class MyToNumberStrategy implements ToNumberStrategy {
        /**
         * Reads next number token as framework Number type.
         */
        @Override
        public Number readNumber(JsonReader in) throws IOException {
            return Numbers.parseNumber(in.nextString());
        }
    }

}
