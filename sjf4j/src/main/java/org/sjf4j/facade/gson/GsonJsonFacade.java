package org.sjf4j.facade.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.FacadeProvider;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.node.ReflectUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Gson-based JSON facade with selectable streaming modes.
 */
public class GsonJsonFacade implements JsonFacade<GsonReader, GsonWriter> {
    private final Gson gson;
    private final StreamingContext streamingContext;

    public GsonJsonFacade() {
        this(new GsonBuilder(), StreamingContext.EMPTY);
    }

    public GsonJsonFacade(GsonBuilder gsonBuilder) {
        this(gsonBuilder, StreamingContext.EMPTY);
    }

    public GsonJsonFacade(GsonBuilder gsonBuilder, StreamingContext context) {
        Objects.requireNonNull(gsonBuilder, "gsonBuilder");
        Objects.requireNonNull(context, "context");

        gsonBuilder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
        gsonBuilder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
        gsonBuilder.setFieldNamingStrategy(field -> {
            String name = ReflectUtil.getExplicitName(field);
            return name != null ? name : field.getName();
        });
        if (context.streamingMode == StreamingContext.StreamingMode.PLUGIN_MODULE ||
                context.streamingMode == StreamingContext.StreamingMode.AUTO) {
            gsonBuilder.registerTypeAdapterFactory(new GsonModule.MyTypeAdapterFactory(context));
        }
        if (context.includeNulls) {
            gsonBuilder.serializeNulls();
        }
        this.gson = gsonBuilder.create();
        this.streamingContext = context;
    }


    public static FacadeProvider<JsonFacade<?, ?>> provider() {
        return config -> new GsonJsonFacade(new GsonBuilder(), config);
    }

    public static FacadeProvider<JsonFacade<?, ?>> provider(GsonBuilder gsonBuilder) {
        return config -> new GsonJsonFacade(gsonBuilder, config);
    }

    @Override
    public StreamingContext streamingContext() {
        return streamingContext;
    }


    @Override
    public StreamingContext.StreamingMode realStreamingMode() {
        StreamingContext.StreamingMode mode = streamingContext.streamingMode;
        if (mode == StreamingContext.StreamingMode.AUTO) {
            // Gson has no separate exclusive streaming implementation, so AUTO resolves to plugin-backed binding.
            return StreamingContext.StreamingMode.PLUGIN_MODULE;
        }
        return mode;
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
