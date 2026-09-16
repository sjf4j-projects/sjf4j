package org.sjf4j.integration.gson.binding;

import com.google.gson.Gson;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.binding.StreamingWriter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;


public class GsonJsonBinder implements JsonBinder {

    private final Gson gson;
    private final StreamingContext streamingContext;

    public GsonJsonBinder(Gson gson) {
        this(gson, StreamingContext.EMPTY);
    }

    public GsonJsonBinder(Gson gson, StreamingContext streamingContext) {
        this.gson = gson;
        this.streamingContext = streamingContext;
    }

    @Override
    public StreamingContext streamingContext() {
        return streamingContext;
    }

    @Override
    public StreamingReader createReader(Reader input) throws IOException {
        return new GsonReader(gson.newJsonReader(input));
    }

    @Override
    public StreamingWriter createWriter(Writer output) throws IOException {
        return null;
    }
}
