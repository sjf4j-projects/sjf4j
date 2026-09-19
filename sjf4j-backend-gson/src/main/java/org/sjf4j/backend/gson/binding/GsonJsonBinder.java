package org.sjf4j.backend.gson.binding;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;


public class GsonJsonBinder implements JsonBinder<GsonReader, GsonWriter> {

    private final Gson gson;
    private final StreamingContext context;

    public GsonJsonBinder(Gson gson) {
        this(gson, StreamingContext.EMPTY);
    }

    public GsonJsonBinder(Gson gson, StreamingContext context) {
        this.gson = Objects.requireNonNull(gson, "gson");
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public StreamingContext streamingContext() {
        return context;
    }

    @Override
    public GsonReader createReader(Reader input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new GsonReader(gson.newJsonReader(input));
    }

    @Override
    public GsonWriter createWriter(Writer output) throws IOException {
        Objects.requireNonNull(output, "output");
        JsonWriter writer = gson.newJsonWriter(output);
        writer.setSerializeNulls(true);
        return new GsonWriter(writer);
    }
}
