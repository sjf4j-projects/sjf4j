package org.sjf4j.facades.gson;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.JsonObject;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public class GsonJsonFacade implements JsonFacade {

    private final Gson gson;

    public GsonJsonFacade(@NonNull Gson gson) {
        this.gson = gson;
    }

//    public Object readNode(@NonNull Reader input, Type nodetype) {
//        try {
//            JsonReader reader = gson.newJsonReader(input);
//            return GsonReader.readAny(reader, nodetype);
//        } catch (Exception e) {
//            throw new JsonException("Failed to deserialize JSON-text into Node: " + e.getMessage(), e);
//        }
//    }

    public void writeNode(@NonNull Writer output, Object node) {
        try {
            JsonWriter writer = gson.newJsonWriter(output);
            GsonWriter.writeAny(writer, node);
        } catch (Exception e) {
            throw new JsonException("Failed to serialize Node to JSON-text: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonObject readObject(@NonNull Reader input) {
        Object value;
        try {
            JsonReader reader = gson.newJsonReader(input);
            value = GsonReader.readAny(reader);
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
    public JsonArray readArray(@NonNull Reader input) {
        Object value;
        try {
            JsonReader reader = gson.newJsonReader(input);
            value = GsonReader.readAny(reader);
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
    public void writeObject(@NonNull Writer output, JsonObject jo) {
        try {
            JsonWriter writer = gson.newJsonWriter(output);
            GsonWriter.writeAny(writer, jo);
        } catch (Exception e) {
            throw new JsonException("Failed to serialize JsonObject to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public void writeArray(@NonNull Writer output, JsonArray ja) {
        try {
            JsonWriter writer = gson.newJsonWriter(output);
            GsonWriter.writeAny(writer, ja);
        } catch (Exception e) {
            throw new JsonException("Failed to serialize JsonArray to JSON: " + e.getMessage(), e);
        }
    }


}
