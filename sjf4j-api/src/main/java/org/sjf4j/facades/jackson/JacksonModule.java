package org.sjf4j.facades.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableAnyProperty;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.io.IOException;

public class JacksonModule {

    public static class JsonObjectAnySetter extends SettableAnyProperty {

        public JsonObjectAnySetter(JavaType type) {
            super(null, null, type, null, null, null);
        }

        public JsonObjectAnySetter(JavaType type, JsonDeserializer<Object> deser) {
            super(null, null, type, null, deser, null);
        }

        @Override
        public SettableAnyProperty withValueDeserializer(JsonDeserializer<Object> deser) {
            // A JsonDeserializer cannot be resolved during the initialization phase of JsonObjectModule
            return new JsonObjectAnySetter(this.getType(), deser);
        }

        @Override
        protected void _set(Object instance, Object propName, Object value) throws Exception {
            ((JsonObject) instance).put((String) propName, value);
        }

        @Override
        public void fixAccess(DeserializationConfig config) {
            // Do not need to fix
        }

    }

    public static class JsonArrayDeserializer extends JsonDeserializer<JsonArray> {
        @Override
        public JsonArray deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            if (p.currentToken() == null) {
                p.nextToken();
            }
            if (p.currentToken() != JsonToken.START_ARRAY) {
                ctx.reportInputMismatch(JsonArray.class,
                        "JsonArray must start with [");
            }

            JsonArray ja = new JsonArray();
            JsonDeserializer<Object> elementDeser =
                    ctx.findRootValueDeserializer(ctx.constructType(Object.class));
            while (p.nextToken() != JsonToken.END_ARRAY) {
                Object v = elementDeser.deserialize(p, ctx);
                ja.add(v);
            }
            return ja;
        }
    }


    public static class JsonObjectModule extends SimpleModule {
        public JsonObjectModule() {
            setDeserializerModifier(new BeanDeserializerModifier() {
                @Override
                public BeanDeserializerBuilder updateBuilder(
                        DeserializationConfig config,
                        BeanDescription beanDesc,
                        BeanDeserializerBuilder builder) {
                    Class<?> clazz = beanDesc.getBeanClass();
                    if (JsonObject.class.isAssignableFrom(clazz)) {
                        JavaType objType = config.constructType(Object.class);
                        builder.setAnySetter(new JsonObjectAnySetter(objType));
                    }
                    return builder;
                }
            });
        }
    }


}
