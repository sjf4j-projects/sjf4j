package org.sjf4j.facades.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.NonNull;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.JsonObject;
import org.sjf4j.util.TypeReference;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public class GsonJsonFacade implements JsonFacade<GsonReader, GsonWriter> {

    @Getter
    private final Gson gson;

    public GsonJsonFacade(@NonNull GsonBuilder gsonBuilder) {
        if (JsonConfig.global().facadeMode == JsonConfig.FacadeMode.MODULE_EXTRA) {
            gsonBuilder.registerTypeAdapterFactory(new GsonModule.MyTypeAdapterFactory());
            gsonBuilder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
            gsonBuilder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
        }
        this.gson = gsonBuilder.create();
    }

    @Override
    public GsonReader createReader(@NonNull Reader input) throws IOException {
        return new GsonReader(gson.newJsonReader(input));
    }

    @Override
    public GsonWriter createWriter(Writer output) throws IOException {
        return new GsonWriter(gson.newJsonWriter(output));
    }


    /// API

    public Object readNode(@NonNull Reader input, Type type) {
        try {
            switch (JsonConfig.global().facadeMode) {
                case STREAMING_GENERAL:
                    return JsonFacade.super.readNode(input, type);
                case STREAMING_SPECIFIC:
                    return readNodeWithSpecific(input, type);
                case MODULE_EXTRA:
                default:
                    return readNodeWithExtra(input, type);
            }
        } catch (Exception e) {
            throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
        }
    }

    public Object readNodeWithSpecific(@NonNull Reader input, Type type) throws IOException {
        JsonReader reader = gson.newJsonReader(input);
        return GsonStreamingUtil.readNode(reader, type);
    }

    public Object readNodeWithExtra(@NonNull Reader input, Type type) throws IOException {
        return gson.fromJson(input, type);
    }


    public void writeNode(@NonNull Writer output, Object node) {
        try {
            JsonWriter writer = gson.newJsonWriter(output);
            GsonStreamingUtil.startDocument(writer);
            GsonStreamingUtil.writeNode(writer, node);
            GsonStreamingUtil.endDocument(writer);
            writer.flush();
        } catch (IOException e) {
            throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) + "' to streaming", e);
        }
    }


    @SuppressWarnings("unchecked")
    public <T> T readObject(@NonNull Reader input, @NonNull Class<T> clazz) {
        try {
            return (T) readNode(input, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + clazz + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    public  <T> T readObject(@NonNull Reader input, @NonNull TypeReference<T> type) {
        try {
            return (T) readNode(input, type.getType());
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
        }
    }

    public JsonObject readObject(@NonNull Reader input) {
        try {
            return readObject(input, JsonObject.class);
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type 'JsonObject'", e);
        }
    }



}
