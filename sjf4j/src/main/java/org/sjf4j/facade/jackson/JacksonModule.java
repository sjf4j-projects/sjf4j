package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableAnyProperty;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.facade.fastjson2.Fastjson2Module;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.ReflectUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Jackson module integration for JsonObject/JsonArray and @NodeValue types.
 */
public interface JacksonModule {

    /**
     * SimpleModule that wires framework serializers/deserializers.
     */
    class MySimpleModule extends SimpleModule {
        /**
         * Creates module and installs reader/writer modifiers.
         */
        public MySimpleModule() {

            setDeserializerModifier(new BeanDeserializerModifier() {
                @Override
                public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
                                                             BeanDescription beanDesc,
                                                             BeanDeserializerBuilder builder) {
                    if (JsonObject.class.isAssignableFrom(beanDesc.getBeanClass())) {
                        JavaType objType = config.constructType(Object.class);
                        if (builder.getAnySetter() == null) {
                            builder.setAnySetter(new JsonObjectAnySetter(objType));
                        }
                    }
                    return builder;
                }

                @Override
                public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                                                              BeanDescription beanDesc,
                                                              JsonDeserializer<?> deserializer) {
                    Class<?> clazz = beanDesc.getBeanClass();
                    if (JsonArray.class.isAssignableFrom(clazz)) {
                        return new JsonArrayDeserializer<>(clazz);
                    }
                    NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
                    if (ti.anyOfInfo != null) {
                        return new AnyOfDeserializer<>(ti.anyOfInfo);
                    }
                    if (ti.valueCodecInfo != null) {
                        return new NodeValueDeserializer<>(ti.valueCodecInfo);
                    }
                    if (ti.pojoInfo != null) {
                        for (NodeRegistry.FieldInfo fi : ti.pojoInfo.fields.values()) {
                            if (fi.anyOfInfo != null) {
                                if (fi.anyOfInfo.scope == AnyOf.Scope.PARENT) {
                                    return new PojoDeserializer<>(ti.pojoInfo);
                                }
                            }
                        }
                    }
                    return deserializer;
                }
            });

            setSerializerModifier(new BeanSerializerModifier() {
                @Override
                public JsonSerializer<?> modifySerializer(SerializationConfig config,
                                                          BeanDescription beanDesc,
                                                          JsonSerializer<?> serializer) {
                    Class<?> clazz = beanDesc.getBeanClass();
                    if (JsonObject.class.isAssignableFrom(clazz)) {
                        return new JsonObjectSerializer();
                    }
                    if (JsonArray.class.isAssignableFrom(clazz)) {
                        return new JsonArraySerializer();
                    }
                    NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(clazz);
                    if (vci != null) {
                        return new NodeValueSerializer<>(vci);
                    }

                    return serializer;
                }
            });
        }

    }


    /// Extra
    /**
     * Any-setter bridge for dynamic fields in JsonObject.
     */
    class JsonObjectAnySetter extends SettableAnyProperty {

        /**
         * Creates setter with target value type.
         */
        public JsonObjectAnySetter(JavaType type) {
            super(null, null, type, null, null, null);
        }

        /**
         * Creates setter with explicit value deserializer.
         */
        public JsonObjectAnySetter(JavaType type, JsonDeserializer<Object> deser) {
            super(null, null, type, null, deser, null);
        }

        /**
         * Returns a copy bound to resolved value deserializer.
         */
        @Override
        public SettableAnyProperty withValueDeserializer(JsonDeserializer<Object> deser) {
            // A JsonDeserializer cannot be resolved during the initialization phase of JsonObjectModule
            return new JsonObjectAnySetter(this.getType(), deser);
        }

        /**
         * Writes unknown property into JsonObject dynamic map.
         */
        @Override
        protected void _set(Object instance, Object propName, Object value) throws Exception {
            ((JsonObject) instance).put((String) propName, value);
        }

        /**
         * No access fix is needed for JsonObject any-setter.
         */
        @Override
        public void fixAccess(DeserializationConfig config) {
            // Do nothing
        }

    }

    class JsonArrayDeserializer<T extends JsonArray> extends JsonDeserializer<T> {
        private final NodeRegistry.PojoInfo pi;
        /**
         * Creates deserializer for JsonArray or subclass.
         */
        public JsonArrayDeserializer(Class<?> clazz) {
            super();
            this.pi = clazz == JsonArray.class ? null : NodeRegistry.registerPojoOrElseThrow(clazz);
        }

        /**
         * Deserializes one JSON array into framework JsonArray type.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            if (p.currentToken() == null) {
                p.nextToken();
            }
            if (p.currentToken() != JsonToken.START_ARRAY) {
                ctx.reportInputMismatch(JsonArray.class, "JsonArray must start with [");
            }

            T ja = pi == null ? (T) new JsonArray() : (T) pi.creatorInfo.forceNewPojo();
            JsonDeserializer<Object> deserializer =
                    ctx.findRootValueDeserializer(ctx.constructType(ja.elementType()));
            while (p.nextToken() != JsonToken.END_ARRAY) {
                Object v = deserializer.deserialize(p, ctx);
                ja.add(v);
            }
            return ja;
        }
    }

    class NodeValueDeserializer<T> extends JsonDeserializer<T> {
        private final NodeRegistry.ValueCodecInfo valueCodecInfo;
        /**
         * Creates deserializer backed by ValueCodec metadata.
         */
        public NodeValueDeserializer(NodeRegistry.ValueCodecInfo valueCodecInfo) {
            this.valueCodecInfo = valueCodecInfo;
        }

        /**
         * Deserializes raw value then decodes via ValueCodec.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            Object raw = ctxt.readValue(p, Object.class);
            return (T) valueCodecInfo.rawToValue(raw);
        }
    }

    class AnyOfDeserializer<T> extends JsonDeserializer<T> {
        private final NodeRegistry.AnyOfInfo anyOfInfo;
        /**
         * Creates serializer backed by ValueCodec metadata.
         */
        public AnyOfDeserializer(NodeRegistry.AnyOfInfo anyOfInfo) {
            this.anyOfInfo = anyOfInfo;
        }

        /**
         * Encodes value via codec and serializes raw value.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return (T) JacksonStreamingIO.readAnyOf(p, anyOfInfo);
        }
    }

    class PojoDeserializer<T> extends JsonDeserializer<T> {
        private final NodeRegistry.PojoInfo pi;
        /**
         * Creates serializer backed by ValueCodec metadata.
         */
        public PojoDeserializer(NodeRegistry.PojoInfo pi) {
            this.pi = pi;
        }

        /**
         * Encodes value via codec and serializes raw value.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return (T) JacksonStreamingIO.readPojo(p, pi);
        }
    }


    /// Write
    /**
     * Serializer for JsonObject preserving framework semantics.
     */
    class JsonObjectSerializer extends JsonSerializer<JsonObject> {
        /**
         * Serializes JsonObject entries as fields.
         */
        @Override
        public void serialize(JsonObject jo, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();
            for (Map.Entry<String, Object> entry : jo.entrySet()) {
                serializers.defaultSerializeField(entry.getKey(), entry.getValue(), gen);
            }
            gen.writeEndObject();
        }
    }

    /**
     * Serializer for JsonArray preserving framework semantics.
     */
    class JsonArraySerializer extends JsonSerializer<JsonArray> {
        /**
         * Serializes JsonArray elements as array items.
         */
        @Override
        public void serialize(JsonArray ja, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartArray();
            for (int i = 0, len = ja.size(); i < len; i++) {
                Object v = ja.getNode(i);
                serializers.defaultSerializeValue(v, gen);
            }
            gen.writeEndArray();
        }
    }

    class NodeValueSerializer<T> extends JsonSerializer<T> {
        private final NodeRegistry.ValueCodecInfo valueCodecInfo;
        /**
         * Creates serializer backed by ValueCodec metadata.
         */
        public NodeValueSerializer(NodeRegistry.ValueCodecInfo valueCodecInfo) {
            this.valueCodecInfo = valueCodecInfo;
        }

        /**
         * Encodes value via codec and serializes raw value.
         */
        @Override
        public void serialize(T value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            Object raw = valueCodecInfo.valueToRaw(value);
            serializers.defaultSerializeValue(raw, gen);
        }
    }


    /// NodeProperty
    /**
     * Annotation introspector mapping NodeProperty/NodeCreator to Jackson metadata.
     */
    class NodePropertyAnnotationIntrospector extends JacksonAnnotationIntrospector {
        /**
         * Resolves serialization name from @NodeProperty.
         */
        @Override
        public PropertyName findNameForSerialization(Annotated ann) {
            NodeProperty nf = ann.getAnnotation(NodeProperty.class);
            if (nf != null && !nf.value().isEmpty()) {
                return PropertyName.construct(nf.value());
            }
            return super.findNameForSerialization(ann);
        }

        /**
         * Resolves deserialization name from @NodeProperty.
         */
        @Override
        public PropertyName findNameForDeserialization(Annotated ann) {
            NodeProperty nf = ann.getAnnotation(NodeProperty.class);
            if (nf != null && !nf.value().isEmpty()) {
                return PropertyName.construct(nf.value());
            }
            return super.findNameForDeserialization(ann);
        }

        /**
         * Resolves field aliases from @NodeProperty aliases.
         */
        @Override
        public List<PropertyName> findPropertyAliases(Annotated ann) {
            NodeProperty nf = ann.getAnnotation(NodeProperty.class);
            if (nf != null) {
                String[] aliases = nf.aliases();
                if (aliases != null && aliases.length > 0) {
                    List<PropertyName> result = new ArrayList<>(aliases.length);
                    for (int i = 0; i < aliases.length; ++i) {
                        result.add(PropertyName.construct(aliases[i]));
                    }
                    return result;
                }
            }
            return super.findPropertyAliases(ann);
        }

        @Override
        public JsonCreator.Mode findCreatorAnnotation(MapperConfig<?> config, Annotated ann) {
            NodeCreator nc = ann.getAnnotation(NodeCreator.class);
            if (nc != null) {
                return JsonCreator.Mode.PROPERTIES;
            }
            return super.findCreatorAnnotation(config, ann);
        }

    }

}
