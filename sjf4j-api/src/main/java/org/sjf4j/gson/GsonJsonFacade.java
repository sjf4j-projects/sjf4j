package org.sjf4j.gson;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonFacade;
import org.sjf4j.JsonObject;

import java.io.Reader;
import java.io.Writer;

public class GsonJsonFacade implements JsonFacade {

    private final Gson gson;

    public GsonJsonFacade(Gson gson) {
        this.gson = gson;
    }

    @Override
    public JsonObject readObject(Reader input) {
        JsonReader reader = gson.newJsonReader(input);
        Object value;
        try {
            value = SimpleJsonReader.readAny(reader);
        } catch (Exception e) {
            throw new JsonException("Failed to deserialize input into JsonObject: " + e.getMessage(), e);
        }

        if (value instanceof JsonObject) {
            return (JsonObject) value;
        } else {
            throw new JsonException("Expected JsonObject but got '" +
                    (value == null ? "[null]" : value.getClass().getName()) + "'");
        }
    }

    @Override
    public JsonArray readArray(Reader input) {
        JsonReader reader = gson.newJsonReader(input);
        Object value;
        try {
            value = SimpleJsonReader.readAny(reader);
        } catch (Exception e) {
            throw new JsonException("Failed to deserialize input into JsonArray: " + e.getMessage(), e);
        }

        if (value instanceof JsonArray) {
            return (JsonArray) value;
        } else {
            throw new JsonException("Expected JsonArray but got '" +
                    (value == null ? "[null]" : value.getClass().getName()) + "'");
        }
    }

    @Override
    public void write(Writer output, JsonObject jo) {
        try {
            JsonWriter writer = gson.newJsonWriter(output);
            SimpleJsonWriter.writeValue(writer, jo);
        } catch (Exception e) {
            throw new JsonException("Failed to serialize JsonObject: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(Writer output, JsonArray ja) {
        try {
            JsonWriter writer = gson.newJsonWriter(output);
            SimpleJsonWriter.writeValue(writer, ja);
        } catch (Exception e) {
            throw new JsonException("Failed to serialize JsonArray: " + e.getMessage(), e);
        }
    }


}
