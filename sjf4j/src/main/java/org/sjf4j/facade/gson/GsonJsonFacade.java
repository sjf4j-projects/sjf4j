package org.sjf4j.facade.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Objects;

public class GsonJsonFacade implements JsonFacade<GsonReader, GsonWriter> {
    private final StreamingMode streamingMode = Sjf4jConfig.global().streamingMode != null
            ? Sjf4jConfig.global().streamingMode : StreamingMode.SHARED_IO;

    private final Gson gson;

    public GsonJsonFacade(GsonBuilder gsonBuilder) {
        if (gsonBuilder == null) throw new IllegalArgumentException("GsonBuilder must not be null");
        gsonBuilder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
        gsonBuilder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
        if (streamingMode == StreamingMode.PLUGIN_MODULE) {
            gsonBuilder.registerTypeAdapterFactory(new GsonModule.MyTypeAdapterFactory());
        }
        // TODO: Retrieve the original FieldNamingStrategy via reflection?
        gsonBuilder.setFieldNamingStrategy(new GsonModule.NodeFieldNamingStrategy());
        this.gson = gsonBuilder.create();
    }

    /// Read

    @Override
    public GsonReader createReader(Reader input) throws IOException {
        Objects.requireNonNull(input, "input is null");
        return new GsonReader(gson.newJsonReader(input));
    }

    @Override
    public Object readNode(Reader input, Type type) {
        Objects.requireNonNull(input, "input is null");
        switch (streamingMode) {
            case SHARED_IO: {
                return JsonFacade.super.readNode(input, type);
            }
            case EXCLUSIVE_IO: {
                try {
                    JsonReader reader = gson.newJsonReader(input);
                    return GsonStreamingIO.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type " + type, e);
                }
            }
            case PLUGIN_MODULE: {
                try {
                    return gson.fromJson(input, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type " + type, e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
        }
    }

    /// Write

    @Override
    public GsonWriter createWriter(Writer output) throws IOException {
        Objects.requireNonNull(output, "output is null");
        return new GsonWriter(gson.newJsonWriter(output));
    }

    @Override
    public void writeNode(Writer output, Object node) {
        Objects.requireNonNull(output, "output is null");
        switch (streamingMode) {
            case SHARED_IO: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case EXCLUSIVE_IO: {
                try {
                    JsonWriter writer = gson.newJsonWriter(output);
                    GsonStreamingIO.writeNode(writer, node);
                    writer.flush();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type " + Types.name(node) + " to JSON streaming", e);
                }
                break;
            }
            case PLUGIN_MODULE: {
                gson.toJson(node, output);
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }



}
