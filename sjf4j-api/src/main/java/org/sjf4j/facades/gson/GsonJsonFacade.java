package org.sjf4j.facades.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.NonNull;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public class GsonJsonFacade implements JsonFacade<GsonReader, GsonWriter> {

    @Getter
    private final Gson gson;

    public GsonJsonFacade(@NonNull GsonBuilder gsonBuilder) {
        gsonBuilder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
        gsonBuilder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
        if (JsonConfig.global().readMode == JsonConfig.ReadMode.USE_MODULE ||
                JsonConfig.global().writeMode == JsonConfig.WriteMode.USE_MODULE) {
            gsonBuilder.registerTypeAdapterFactory(new GsonModule.MyTypeAdapterFactory());
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

    @Override
    public Object readNode(@NonNull Reader input, Type type) {
        switch (JsonConfig.global().readMode) {
            case STREAMING_GENERAL:
                return readNodeWithGeneral(input, type);
            case STREAMING_SPECIFIC:
                return readNodeWithSpecific(input, type);
            case USE_MODULE:
                return readNodeWithModule(input, type);
            default:
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }

    public Object readNodeWithGeneral(@NonNull Reader input, Type type){
        return JsonFacade.super.readNode(input, type);
    }

    public Object readNodeWithSpecific(@NonNull Reader input, Type type) {
        try (JsonReader reader = gson.newJsonReader(input)) {
            return GsonStreamingUtil.readNode(reader, type);
        } catch (Exception e) {
            throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
        }
    }

    public Object readNodeWithModule(@NonNull Reader input, Type type) {
        try (JsonReader reader = gson.newJsonReader(input)) {
            return gson.fromJson(reader, type);
        } catch (Exception e) {
            throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
        }
    }


    @Override
    public void writeNode(@NonNull Writer output, Object node) {
        switch (JsonConfig.global().writeMode) {
            case STREAMING_GENERAL:
                writeNodeWithGeneral(output, node);
                break;
            case STREAMING_SPECIFIC:
                writeNodeWithSpecific(output, node);
                break;
            case USE_MODULE:
                writeNodeWithModule(output, node);
                break;
            default:
                throw new JsonException("Unsupported write mode '" + JsonConfig.global().writeMode + "'");
        }
    }

    public void writeNodeWithGeneral(Writer output, Object node) {
        JsonFacade.super.writeNode(output, node);
    }

    public void writeNodeWithSpecific(@NonNull Writer output, Object node) {
        try {
            JsonWriter writer = gson.newJsonWriter(output);
            GsonStreamingUtil.startDocument(writer);
            GsonStreamingUtil.writeNode(writer, node);
            GsonStreamingUtil.endDocument(writer);
            writer.flush();
        } catch (IOException e) {
            throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                    "' to JSON streaming", e);
        }
    }

    public void writeNodeWithModule(Writer output, Object node) {
        JsonFacade.super.writeNode(output, node);
    }



}
