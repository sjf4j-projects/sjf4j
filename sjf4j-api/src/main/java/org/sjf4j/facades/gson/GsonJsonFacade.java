package org.sjf4j.facades.gson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.facades.FacadeReader;
import org.sjf4j.facades.FacadeWriter;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.fastjson2.Fastjson2Reader;
import org.sjf4j.facades.fastjson2.Fastjson2Writer;
import org.sjf4j.facades.jackson.JacksonStreamingUtil;
import org.sjf4j.util.StreamingUtil;
import org.sjf4j.util.TypeReference;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public class GsonJsonFacade implements JsonFacade<GsonReader, GsonWriter> {

    @Getter
    private final Gson gson;

    public GsonJsonFacade(@NonNull Gson gson) {
        this.gson = gson;
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
            JsonReader reader = gson.newJsonReader(input);
            GsonStreamingUtil.startDocument(reader);
            Object node = GsonStreamingUtil.readNode(reader, type);
            GsonStreamingUtil.endDocument(reader);
            return node;
        } catch (IOException e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
        }
    }

    public void writeNode(Writer output, Object node) {
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
            return (T) readNode(input, type);
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
