package org.sjf4j.facades;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.gson.GsonReader;
import org.sjf4j.facades.gson.GsonWriter;
import org.sjf4j.util.TypeReference;
import org.sjf4j.util.TypeUtil;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public interface YamlFacade {

    Object readNode(Reader input, Type type);

    void writeNode(Writer output, Object node);


    /// Default

    default JsonObject readObject(@NonNull Reader input) {
        Object value;
        try {
            value = readNode(input, null);
        } catch (Exception e) {
            throw new JsonException("Failed to read YAML into JsonObject: " + e.getMessage(), e);
        }

        if (value instanceof JsonObject) {
            return (JsonObject) value;
        } else {
            throw new JsonException("Expected JsonObject but got '" +
                    (value == null ? "[null]" : value.getClass()) + "'");
        }
    }


    @SuppressWarnings("unchecked")
    default <T> T readObject(@NonNull Reader input, @NonNull Class<T> clazz) {
        Object value;
        try {
            value = readNode(input, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to read YAML into " + clazz + ": " + e.getMessage(), e);
        }

        if (clazz.isInstance(value)) {
            return (T) value;
        }

        throw new JsonException("Expected " + clazz + " but got '" +
                (value == null ? "[null]" : value.getClass()) + "'");
    }


    @SuppressWarnings("unchecked")
    default <T> T readObject(@NonNull Reader input, @NonNull TypeReference<T> type) {
        Object value;
        try {
            value = readNode(input, type);
        } catch (Exception e) {
            throw new JsonException("Failed to read YAML into " + type + ": " + e.getMessage(), e);
        }

        Class<?> rawClazz = TypeUtil.getRawClass(type);
        if (rawClazz.isInstance(value)) {
            return (T) value;
        }

        throw new JsonException("Expected " + type + " but got '" +
                (value == null ? "[null]" : value.getClass()) + "'");
    }


    default JsonArray readArray(@NonNull Reader input) {
        Object value;
        try {
            value = readNode(input, null);
        } catch (Exception e) {
            throw new JsonException("Failed to read YAML into JsonArray: " + e.getMessage(), e);
        }

        if (value instanceof JsonArray) {
            return (JsonArray) value;
        } else {
            throw new JsonException("Expected JsonArray but got '" +
                    (value == null ? "[null]" : value.getClass()) + "'");
        }
    }

    default void writeObject(@NonNull Writer output, JsonObject jo) {
        try {
            writeNode(output, jo);
        } catch (Exception e) {
            throw new JsonException("Failed to write JsonObject to YAML: " + e.getMessage(), e);
        }
    }

    default void writeArray(@NonNull Writer output, JsonArray jo) {
        try {
            writeNode(output, jo);
        } catch (Exception e) {
            throw new JsonException("Failed to write JsonArray to YAML: " + e.getMessage(), e);
        }
    }


}
