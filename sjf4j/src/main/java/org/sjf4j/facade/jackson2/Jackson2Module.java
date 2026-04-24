package org.sjf4j.facade.jackson2;

import com.fasterxml.jackson.annotation.JsonCreator;
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
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.ReflectUtil;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Jackson2 module integration for JsonObject/JsonArray and @NodeValue types.
 */
public interface Jackson2Module {

    /**
     * SimpleModule that wires framework serializers/deserializers.
     */
    class TwoSimpleModule extends SimpleModule {
        private final StreamingContext streamingContext;

        /**
         * Creates module and installs reader/writer modifiers.
         */
        public TwoSimpleModule(StreamingContext streamingContext) {
            this.streamingContext = streamingContext;

            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(Instant.class);
            String valueFormat = streamingContext.valueFormatMapping.defaultValueFormat(Instant.class);
            NodeRegistry.ValueCodecInfo vci = ti.getFormattedValueCodecInfo(valueFormat);
            addDeserializer(Instant.class, new NodeValueDeserializer<>(vci));

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
                    if (JsonObject.class.isAssignableFrom(clazz)) {
                        return new JsonObjectDeserializer<>(beanDesc.getType(), streamingContext);
                    }
                    if (JsonArray.class.isAssignableFrom(clazz)) {
                        return new JsonArrayDeserializer<>(clazz);
                    }
                    NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
                    if (ti.anyOfInfo != null) {
                        return new AnyOfDeserializer<>(ti.anyOfInfo, streamingContext);
                    }
                    if (ti.hasValueCodecs()) {
                        String valueFormat = streamingContext.valueFormatMapping.defaultValueFormat(clazz);
                        NodeRegistry.ValueCodecInfo vci = ti.getFormattedValueCodecInfo(valueFormat);
                        if (vci != null) {
                            return new NodeValueDeserializer<>(vci);
                        }
                    }
                    if (ti.requiresPojoReader()) {
                        return new PojoDeserializer<>(ti.pojoInfo, streamingContext);
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
                        return new StreamingSerializer(streamingContext);
                    }
                    if (JsonArray.class.isAssignableFrom(clazz)) {
                        return new JsonArraySerializer();
                    }
                    NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
                    if (ti.hasValueCodecs()) {
                        String valueFormat = streamingContext.valueFormatMapping.defaultValueFormat(clazz);
                        NodeRegistry.ValueCodecInfo vci = ti.getFormattedValueCodecInfo(valueFormat);
                        if (vci != null) {
                            return new NodeValueSerializer<>(vci);
                        }
                    }
                    if (ti.requiresPojoWriter()) {
                        return new StreamingSerializer(streamingContext);
                    }

                    return serializer;
                }
            });
        }

    }


    class JsonObjectDeserializer<T extends JsonObject> extends JsonDeserializer<T> {
        private final Type ownerType;
        private final Class<?> ownerRawClazz;
        private final NodeRegistry.PojoInfo pi;
        private final StreamingContext streamingContext;

        public JsonObjectDeserializer(JavaType javaType, StreamingContext streamingContext) {
            this.ownerType = _toType(javaType);
            this.ownerRawClazz = Types.rawBox(ownerType);
            this.pi = ownerRawClazz == JsonObject.class ? null : NodeRegistry.registerPojoOrElseThrow(ownerRawClazz);
            this.streamingContext = streamingContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (pi == null) {
                Object value = ctxt.readValue(p, Object.class);
                if (value == null) return null;
                if (value instanceof JsonObject) return (T) value;
                if (value instanceof Map) return (T) new JsonObject((Map<String, Object>) value);
                throw new IOException("Expected object value for JsonObject, but got " + value.getClass().getName());
            }
            return (T) Jackson2StreamingIO.readPojo(p, ownerType, ownerRawClazz, pi, streamingContext);
        }

        private static Type _toType(JavaType javaType) {
            if (javaType == null) return Object.class;
            Class<?> raw = javaType.getRawClass();
            if (raw == null) return Object.class;
            int n = javaType.containedTypeCount();
            if (n <= 0) return raw;
            Type[] args = new Type[n];
            for (int i = 0; i < n; i++) {
                args[i] = _toType(javaType.containedType(i));
            }
            return new Types.ParameterizedTypeImpl(raw, args, raw.getDeclaringClass());
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
        private final StreamingContext streamingContext;
        /**
         * Creates serializer backed by ValueCodec metadata.
         */
        public AnyOfDeserializer(NodeRegistry.AnyOfInfo anyOfInfo, StreamingContext streamingContext) {
            this.anyOfInfo = anyOfInfo;
            this.streamingContext = streamingContext;
        }

        /**
         * Encodes value via codec and serializes raw value.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return (T) Jackson2StreamingIO.readAnyOf(p, anyOfInfo, streamingContext);
        }
    }

    class PojoDeserializer<T> extends JsonDeserializer<T> {
        private final NodeRegistry.PojoInfo pi;
        private final StreamingContext streamingContext;
        /**
         * Creates serializer backed by ValueCodec metadata.
         */
        public PojoDeserializer(NodeRegistry.PojoInfo pi, StreamingContext streamingContext) {
            this.pi = pi;
            this.streamingContext = streamingContext;
        }

        /**
         * Encodes value via codec and serializes raw value.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return (T) Jackson2StreamingIO.readPojo(p, pi.clazz, pi.clazz, pi, streamingContext);
        }
    }


    /// Write
    /**
     * Serializer for JsonObject preserving framework semantics.
     */
    class StreamingSerializer extends JsonSerializer<Object> {
        private final StreamingContext streamingContext;

        public StreamingSerializer(StreamingContext streamingContext) {
            this.streamingContext = streamingContext;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            Jackson2StreamingIO.writeNode(gen, value, streamingContext);
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
     * Annotation introspector mapping NodeProperty/NodeCreator to Jackson2 metadata.
     */
    class NodePropertyAnnotationIntrospector extends JacksonAnnotationIntrospector {
        /**
         * Resolves serialization name from @NodeProperty.
         */
        @Override
        public PropertyName findNameForSerialization(Annotated ann) {
            if (ann instanceof AnnotatedField) {
                String name = ReflectUtil.getExplicitName(((AnnotatedField) ann).getAnnotated());
                if (name != null && !name.isEmpty()) {
                    return PropertyName.construct(name);
                }
            }
            return super.findNameForSerialization(ann);
        }

        /**
         * Resolves deserialization name from @NodeProperty.
         */
        @Override
        public PropertyName findNameForDeserialization(Annotated ann) {
            if (ann instanceof AnnotatedField) {
                String name = ReflectUtil.getExplicitName(((AnnotatedField) ann).getAnnotated());
                if (name != null && !name.isEmpty()) {
                    return PropertyName.construct(name);
                }
            } else if (ann instanceof AnnotatedParameter) {
                Parameter parameter = getJavaParameter((AnnotatedParameter) ann);
                if (parameter != null) {
                    String name = ReflectUtil.getExplicitName(parameter);
                    if (name != null && !name.isEmpty()) {
                        return PropertyName.construct(name);
                    }
                }
            }
            return super.findNameForDeserialization(ann);
        }

        /**
         * Resolves field aliases from @NodeProperty aliases.
         */
        @Override
        public List<PropertyName> findPropertyAliases(Annotated ann) {
            String[] aliases = null;
            if (ann instanceof AnnotatedField) {
                aliases = ReflectUtil.getAliases(((AnnotatedField) ann).getAnnotated());
            } else if (ann instanceof AnnotatedParameter) {
                Parameter parameter = getJavaParameter((AnnotatedParameter) ann);
                if (parameter != null) aliases = ReflectUtil.getAliases(parameter);
            }
            if (aliases != null && aliases.length > 0) {
                List<PropertyName> result = new ArrayList<>(aliases.length);
                for (String alias : aliases) {
                    result.add(PropertyName.construct(alias));
                }
                return result;
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

        private Parameter getJavaParameter(AnnotatedParameter ann) {
            if (!(ann.getOwner().getAnnotated() instanceof Executable)) return null;
            Executable executable = (Executable) ann.getOwner().getAnnotated();
            int index = ann.getIndex();
            Parameter[] parameters = executable.getParameters();
            return index >= 0 && index < parameters.length ? parameters[index] : null;
        }

    }

}
