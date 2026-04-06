package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.util.TokenBuffer;
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
import org.sjf4j.JsonType;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.ReflectUtil;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
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
                    if (JsonObject.class.isAssignableFrom(clazz)) {
                        return new JsonObjectDeserializer<>(beanDesc.getType());
                    }
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
                    if (ti.requiresFrameworkReader()) {
                        return new PojoDeserializer<>(ti.pojoInfo);
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
                        return new StreamingSerializer();
                    }
                    if (JsonArray.class.isAssignableFrom(clazz)) {
                        return new JsonArraySerializer();
                    }
                    NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
                    NodeRegistry.ValueCodecInfo vci = ti.valueCodecInfo;
                    if (vci != null) {
                        return new NodeValueSerializer<>(vci);
                    }
                    if (ti.requiresFrameworkWriter()) {
                        return new StreamingSerializer();
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

        public JsonObjectDeserializer(JavaType javaType) {
            this.ownerType = _toType(javaType);
            this.ownerRawClazz = Types.rawBox(ownerType);
            this.pi = ownerRawClazz == JsonObject.class ? null : NodeRegistry.registerPojoOrElseThrow(ownerRawClazz);
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
            return (T) JacksonStreamingIO.readPojo(p, ownerType, ownerRawClazz, pi);
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
            if (anyOfInfo.hasDiscriminator) {
                TokenBuffer rawBuffer = ctxt.bufferAsCopyOfValue(p);
                JsonParser discriminatorParser = rawBuffer.asParserOnFirstToken();
                Class<?> targetClazz;
                try {
                    targetClazz = org.sjf4j.facade.StreamingIO.resolveSelfDiscriminatorTarget(
                            ctxt.readValue(discriminatorParser, Object.class), anyOfInfo);
                } finally {
                    discriminatorParser.close();
                }
                if (targetClazz == null) {
                    return null;
                }
                JsonParser targetParser = rawBuffer.asParserOnFirstToken();
                try {
                    return (T) ctxt.readValue(targetParser, targetClazz);
                } finally {
                    targetParser.close();
                }
            }

            JsonNode rawNode = ctxt.readTree(p);
            Class<?> targetClazz;
            JsonType jsonType = toJsonType(rawNode);
            targetClazz = anyOfInfo.resolveByJsonType(jsonType);
            if (targetClazz == null && anyOfInfo.onNoMatch != AnyOf.OnNoMatch.FAILBACK_NULL) {
                throw new org.sjf4j.exception.BindingException("AnyOf mapping does not support jsonType=" + jsonType +
                        " for type '" + anyOfInfo.clazz.getName() + "'");
            }
            if (targetClazz == null) {
                return null;
            }
            return (T) ctxt.readTreeAsValue(rawNode, targetClazz);
        }

        private JsonType toJsonType(JsonNode rawNode) {
            if (rawNode.isObject()) return JsonType.OBJECT;
            if (rawNode.isArray()) return JsonType.ARRAY;
            if (rawNode.isTextual()) return JsonType.STRING;
            if (rawNode.isNumber()) return JsonType.NUMBER;
            if (rawNode.isBoolean()) return JsonType.BOOLEAN;
            if (rawNode.isNull()) return JsonType.NULL;
            return JsonType.UNKNOWN;
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
            return (T) JacksonStreamingIO.readPojo(p, pi.clazz, pi.clazz, pi);
        }
    }


    /// Write
    /**
     * Serializer for JsonObject preserving framework semantics.
     */
    class StreamingSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            JacksonStreamingIO.writeNode(gen, value);
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
                for (int i = 0; i < aliases.length; ++i) {
                    result.add(PropertyName.construct(aliases[i]));
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
