package org.sjf4j.facade.jackson3;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.exception.BindingException;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.ReflectUtil;
import org.sjf4j.node.Types;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.SettableAnyProperty;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.AnnotatedField;
import tools.jackson.databind.introspect.AnnotatedParameter;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Jackson3 module integration for JsonObject/JsonArray and framework node codecs.
 */
public interface Jackson3Module {

    class TwoSimpleModule extends SimpleModule {
        private final StreamingContext streamingContext;

        public TwoSimpleModule(StreamingContext streamingContext) {
            this.streamingContext = streamingContext;
            setDeserializerModifier(new ValueDeserializerModifier() {
                @Override
                public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
                                                             BeanDescription.Supplier beanDesc,
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
                public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
                                                               BeanDescription.Supplier beanDesc,
                                                               ValueDeserializer<?> deserializer) {
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
                    String valueFormat = streamingContext.valueFormatMapping.defaultValueFormat(clazz);
                    NodeRegistry.ValueCodecInfo vci = ti.getFormattedValueCodecInfo(valueFormat);
                    if (vci != null) {
                        return new NodeValueDeserializer<>(vci);
                    }
                    if (ti.requiresPojoReader()) {
                        return new PojoDeserializer<>(ti.pojoInfo, streamingContext);
                    }
                    return deserializer;
                }
            });

            setSerializerModifier(new ValueSerializerModifier() {
                @Override
                public ValueSerializer<?> modifySerializer(SerializationConfig config,
                                                           BeanDescription.Supplier beanDesc,
                                                           ValueSerializer<?> serializer) {
                    Class<?> clazz = beanDesc.getBeanClass();
                    if (JsonObject.class.isAssignableFrom(clazz)) {
                        return new StreamingSerializer(streamingContext);
                    }
                    if (JsonArray.class.isAssignableFrom(clazz)) {
                        return new JsonArraySerializer();
                    }
                    NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
                    String valueFormat = streamingContext.valueFormatMapping.defaultValueFormat(clazz);
                    NodeRegistry.ValueCodecInfo vci = ti.getFormattedValueCodecInfo(valueFormat);
                    if (vci != null) {
                        return new NodeValueSerializer<>(vci);
                    }
                    if (ti.requiresPojoWriter()) {
                        return new StreamingSerializer(streamingContext);
                    }
                    return serializer;
                }
            });
        }
    }

    class JsonObjectDeserializer<T extends JsonObject> extends ValueDeserializer<T> {
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
        public T deserialize(JsonParser p, DeserializationContext ctxt) {
            if (pi == null) {
                Object value = ctxt.readValue(p, Object.class);
                if (value == null) return null;
                if (value instanceof JsonObject) return (T) value;
                if (value instanceof Map) return (T) new JsonObject(value);
                ctxt.reportInputMismatch(JsonObject.class,
                        "Expected object value for JsonObject, but got %s", value.getClass().getName());
                return null;
            }
            try {
                return (T) StreamingIO.readPojo(new Jackson3Reader(p), ownerType, ownerRawClazz, pi, streamingContext);
            } catch (IOException e) {
                throw new BindingException(e);
            }
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

    class JsonObjectAnySetter extends SettableAnyProperty {
        public JsonObjectAnySetter(JavaType type) {
            super(null, null, type, null, null, null);
        }

        public JsonObjectAnySetter(JavaType type, ValueDeserializer<Object> deser) {
            super(null, null, type, null, deser, null);
        }

        @Override
        public SettableAnyProperty withValueDeserializer(ValueDeserializer<Object> deser) {
            return new JsonObjectAnySetter(this.getType(), deser);
        }

        @Override
        protected void _set(DeserializationContext ctxt, Object instance, Object propName, Object value) {
            ((JsonObject) instance).put((String) propName, value);
        }

        @Override
        public void fixAccess(DeserializationConfig config) {
        }
    }

    class JsonArrayDeserializer<T extends JsonArray> extends ValueDeserializer<T> {
        private final NodeRegistry.PojoInfo pi;

        public JsonArrayDeserializer(Class<?> clazz) {
            this.pi = clazz == JsonArray.class ? null : NodeRegistry.registerPojoOrElseThrow(clazz);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctx) {
            if (p.currentToken() == null) {
                p.nextToken();
            }
            if (p.currentToken() != JsonToken.START_ARRAY) {
                ctx.reportInputMismatch(JsonArray.class, "JsonArray must start with [");
            }

            T ja = pi == null ? (T) new JsonArray() : (T) pi.creatorInfo.forceNewPojo();
            ValueDeserializer<Object> deserializer = ctx.findRootValueDeserializer(ctx.constructType(ja.elementType()));
            while (p.nextToken() != JsonToken.END_ARRAY) {
                Object v = deserializer.deserialize(p, ctx);
                ja.add(v);
            }
            return ja;
        }
    }

    class NodeValueDeserializer<T> extends ValueDeserializer<T> {
        private final NodeRegistry.ValueCodecInfo valueCodecInfo;

        public NodeValueDeserializer(NodeRegistry.ValueCodecInfo valueCodecInfo) {
            this.valueCodecInfo = valueCodecInfo;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) {
            Object raw = ctxt.readValue(p, Object.class);
            return (T) valueCodecInfo.rawToValue(raw);
        }
    }

    class AnyOfDeserializer<T> extends ValueDeserializer<T> {
        private final NodeRegistry.AnyOfInfo anyOfInfo;
        private final StreamingContext streamingContext;

        public AnyOfDeserializer(NodeRegistry.AnyOfInfo anyOfInfo, StreamingContext streamingContext) {
            this.anyOfInfo = anyOfInfo;
            this.streamingContext = streamingContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) {
            try {
                return (T) StreamingIO.readAnyOf(new Jackson3Reader(p), anyOfInfo, streamingContext);
            } catch (IOException e) {
                throw new BindingException(e);
            }
        }
    }

    class PojoDeserializer<T> extends ValueDeserializer<T> {
        private final NodeRegistry.PojoInfo pi;
        private final StreamingContext streamingContext;

        public PojoDeserializer(NodeRegistry.PojoInfo pi, StreamingContext streamingContext) {
            this.pi = pi;
            this.streamingContext = streamingContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) {
            try {
                return (T) StreamingIO.readPojo(new Jackson3Reader(p), pi.clazz, pi.clazz, pi, streamingContext);
            } catch (IOException e) {
                throw new BindingException(e);
            }
        }
    }

    class StreamingSerializer extends ValueSerializer<Object> {
        private final StreamingContext streamingContext;

        public StreamingSerializer(StreamingContext streamingContext) {
            this.streamingContext = streamingContext;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext serializers) {
            try {
                StreamingIO.writeNode(new Jackson3Writer(gen), value, streamingContext);
            } catch (IOException e) {
                throw new BindingException(e);
            }
        }
    }

    class JsonArraySerializer extends ValueSerializer<JsonArray> {
        @Override
        public void serialize(JsonArray ja, JsonGenerator gen, SerializationContext serializers) {
            gen.writeStartArray();
            for (int i = 0, len = ja.size(); i < len; i++) {
                serializers.writeValue(gen, ja.getNode(i));
            }
            gen.writeEndArray();
        }
    }

    class NodeValueSerializer<T> extends ValueSerializer<T> {
        private final NodeRegistry.ValueCodecInfo valueCodecInfo;

        public NodeValueSerializer(NodeRegistry.ValueCodecInfo valueCodecInfo) {
            this.valueCodecInfo = valueCodecInfo;
        }

        @Override
        public void serialize(T value, JsonGenerator gen, SerializationContext serializers) {
            serializers.writeValue(gen, valueCodecInfo.valueToRaw(value));
        }
    }

    class NodePropertyAnnotationIntrospector extends JacksonAnnotationIntrospector {
        @Override
        public PropertyName findNameForSerialization(MapperConfig<?> config, Annotated ann) {
            if (ann instanceof AnnotatedField) {
                String name = ReflectUtil.getExplicitName(((AnnotatedField) ann).getAnnotated());
                if (name != null && !name.isEmpty()) {
                    return PropertyName.construct(name);
                }
            }
            return super.findNameForSerialization(config, ann);
        }

        @Override
        public PropertyName findNameForDeserialization(MapperConfig<?> config, Annotated ann) {
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
            return super.findNameForDeserialization(config, ann);
        }

        @Override
        public List<PropertyName> findPropertyAliases(MapperConfig<?> config, Annotated ann) {
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
            return super.findPropertyAliases(config, ann);
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
