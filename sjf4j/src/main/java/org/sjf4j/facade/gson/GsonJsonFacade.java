package org.sjf4j.facade.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.sjf4j.exception.BindingException;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.node.ReflectUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Gson-based JSON facade with selectable streaming modes.
 */
public class GsonJsonFacade implements JsonFacade<GsonReader, GsonWriter> {
    private final StreamingMode streamingMode;
    private final Gson gson;

    public GsonJsonFacade() {
        this(new GsonBuilder(), null);
    }

    public GsonJsonFacade(StreamingMode streamingMode) {
        this(new GsonBuilder(), streamingMode);
    }

    public GsonJsonFacade(GsonBuilder gsonBuilder) {
        this(gsonBuilder, null);
    }

    /**
     * Creates facade with configured GsonBuilder and optional plugin module.
     */
    public GsonJsonFacade(GsonBuilder gsonBuilder, StreamingMode streamingMode) {
        Objects.requireNonNull(gsonBuilder, "gsonBuilder");
        // Gson has no separate exclusive streaming implementation, so AUTO resolves to plugin-backed binding.
        this.streamingMode = streamingMode == null || streamingMode == StreamingMode.AUTO ?
                StreamingMode.PLUGIN_MODULE : streamingMode;

        if (this.streamingMode == StreamingMode.PLUGIN_MODULE) {
            gsonBuilder.registerTypeAdapterFactory(new GsonModule.MyTypeAdapterFactory());
        }

        gsonBuilder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
        gsonBuilder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
        gsonBuilder.setFieldNamingStrategy(field -> {
            String name = ReflectUtil.getExplicitName(field);
            return name != null ? name : field.getName();
        });
        this.gson = gsonBuilder.create();
    }

    @Override
    public StreamingMode streamingMode() {
        return streamingMode;
    }


    /// Read

    /**
     * Creates a streaming reader from java.io.Reader.
     */
    @Override
    public GsonReader createReader(Reader input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new GsonReader(gson.newJsonReader(input));
    }

    @Override
    public Object readNodePlugin(Reader input, Type type) {
        try {
            return gson.fromJson(input, type);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodePlugin(String input, Type type) {
        try {
            return gson.fromJson(input, type);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }


    /// Write

    /**
     * Creates a streaming writer to java.io.Writer.
     */
    @Override
    public GsonWriter createWriter(Writer output) throws IOException {
        Objects.requireNonNull(output, "output");
        return new GsonWriter(gson.newJsonWriter(output));
    }

    @Override
    public void writeNodePlugin(Writer output, Object node) {
        try {
            gson.toJson(node, output);
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }

}
