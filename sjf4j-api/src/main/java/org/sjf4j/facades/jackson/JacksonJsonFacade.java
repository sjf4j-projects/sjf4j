package org.sjf4j.facades.jackson;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.JsonObject;

import java.io.Reader;
import java.io.Writer;

public class JacksonJsonFacade implements JsonFacade {

    private final ObjectMapper objectMapper;

    public JacksonJsonFacade(@NonNull ObjectMapper objectMapper) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Object.class, new SimpleJacksonDeserializer());
        module.addSerializer(Object.class, new SimpleJacksonSerializer());
        objectMapper.registerModule(module);
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonObject readObject(@NonNull Reader reader) {
        Object value;
        try {
            value = objectMapper.readValue(reader, Object.class);
        } catch (Exception e) {
            throw new JsonException("Failed to deserialize JSON into JsonObject: " + e.getMessage(), e);
        }

        if (value instanceof JsonObject) {
            return (JsonObject) value;
        } else {
            throw new JsonException("Expected JsonObject but got '" +
                    (value == null ? "[null]" : value.getClass().getName()) + "'");
        }
    }

    @Override
    public JsonArray readArray(@NonNull Reader reader) {
        Object value;
        try {
            value = objectMapper.readValue(reader, Object.class);
        } catch (Exception e) {
            throw new JsonException("Failed to deserialize JSON into JsonArray: " + e.getMessage(), e);
        }

        if (value instanceof JsonArray) {
            return (JsonArray) value;
        } else {
            throw new JsonException("Expected JsonArray but got '" +
                    (value == null ? "[null]" : value.getClass().getName()) + "'");
        }
    }

    @Override
    public void writeObject(@NonNull Writer writer, JsonObject jo) {
        try {
            objectMapper.writeValue(writer, jo);
        } catch (Exception e) {
            throw new JsonException("Failed to serialize JsonObject to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public void writeArray(@NonNull Writer writer, JsonArray ja) {
        try {
            objectMapper.writeValue(writer, ja);
        } catch (Exception e) {
            throw new JsonException("Failed to serialize JsonArray to JSON: " + e.getMessage(), e);
        }
    }


}
