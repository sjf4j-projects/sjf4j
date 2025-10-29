package org.sjf4j.facades.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.util.ValueHandler;

import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public class SimpleJacksonSerializer extends JsonSerializer<Object> {

    @Override
    public void serialize(Object object, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        Object value = ValueHandler.object2Value(object);
        if (value == null) {
            gen.writeNull();
        } else if (value instanceof String || value instanceof Character) {
            gen.writeString(value.toString());
        } else if (value instanceof Number) {
            if (value instanceof Long || value instanceof Integer) {
                gen.writeNumber(((Number) value).longValue());
            } else if (value instanceof Float || value instanceof Double) {
                gen.writeNumber(((Number) value).doubleValue());
            } else if (value instanceof BigInteger) {
                gen.writeNumber(((BigInteger) value));
            } else if (value instanceof BigDecimal) {
                gen.writeNumber(((BigDecimal) value));
            } else {
                gen.writeNumber(((Number) value).longValue());
            }
        } else if (value instanceof Boolean) {
            gen.writeBoolean((Boolean) value);
        } else if (value instanceof JsonObject) {
            gen.writeStartObject();
            for (Map.Entry<String, Object> entry : ((JsonObject) value).entrySet()) {
                gen.writeFieldName(entry.getKey());
                serialize(entry.getValue(), gen, serializers);
            }
            gen.writeEndObject();
        } else if (value instanceof Map) {
            gen.writeStartObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                gen.writeFieldName(entry.getKey().toString());
                serialize(entry.getValue(), gen, serializers);
            }
            gen.writeEndObject();
        } else if (value instanceof JsonArray) {
            gen.writeStartArray();
            for (Object v : (JsonArray) value) {
                serialize(v, gen, serializers);
            }
            gen.writeEndArray();
        } else if (value instanceof List) {
            gen.writeStartArray();
            for (Object v : (List<?>) value) {
                serialize(v, gen, serializers);
            }
            gen.writeEndArray();
        } else if (value.getClass().isArray()) {
            gen.writeStartArray();
            for (int i = 0; i < Array.getLength(value); i++) {
                serialize(Array.get(value, i), gen, serializers);
            }
            gen.writeEndArray();
        } else {
            throw new IllegalStateException("Unsupported object type '" + object.getClass().getName() +
                    "', expected one of [JsonObject, JsonArray, String, Number, Boolean] or a type registered in " +
                    "ValueRegistry, or a Map/List/Array of such elements.");
        }
    }


}
