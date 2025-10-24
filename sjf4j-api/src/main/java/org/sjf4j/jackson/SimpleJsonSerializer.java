package org.sjf4j.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

public class SimpleJsonSerializer extends JsonSerializer<Object> {

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else if (value instanceof String) {
            gen.writeString((String) value);
        } else if (value instanceof JsonObject) {
            JsonObject jo = (JsonObject) value;
            gen.writeStartObject();
            for (Map.Entry<String, Object> e : jo.entrySet()) {
                gen.writeFieldName(e.getKey());
                serialize(e.getValue(), gen, serializers);
            }
            gen.writeEndObject();
        } else if (value instanceof JsonArray) {
            JsonArray ja = (JsonArray) value;
            gen.writeStartArray();
            for (int i = 0; i < ja.size(); i++) {
                serialize(ja.getObject(i), gen, serializers);
            }
            gen.writeEndArray();
        } else if (value instanceof Number) {
            if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
                gen.writeNumber(((Number) value).longValue());
            } else if (value instanceof Float || value instanceof Double) {
                gen.writeNumber(((Number) value).doubleValue());
            } else if (value instanceof BigInteger) {
                gen.writeNumber(((BigInteger) value));
            } else if (value instanceof BigDecimal) {
                gen.writeNumber(((BigDecimal) value));
            } else {
                gen.writeNumber(((Number) value).doubleValue());
            }
        } else if (value instanceof Boolean) {
            gen.writeBoolean((Boolean) value);
        } else {
            throw new IllegalStateException("Unexpected value type: " + value.getClass().getName());
        }
    }


}
