package org.sjf4j.binding.simple;

import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;

/**
 * Built-in lightweight JSON binding.
 */
public final class SimpleJsonBinder implements JsonBinder<SimpleJsonReader, SimpleJsonWriter> {
    private final StreamingContext streamingContext;

    public SimpleJsonBinder() {
        this(StreamingContext.EMPTY);
    }

    public SimpleJsonBinder(StreamingContext streamingContext) {
        this.streamingContext = Objects.requireNonNull(streamingContext, "streamingContext");
    }


    @Override
    public StreamingContext streamingContext() {
        return streamingContext;
    }

    /**
     * Creates a binding reader from java.io.Reader.
     */
    @Override
    public SimpleJsonReader createReader(Reader input) throws IOException {
        return new SimpleJsonReader(input);
    }

    /**
     * Creates a binding writer to java.io.Writer.
     */
    @Override
    public SimpleJsonWriter createWriter(Writer output) throws IOException {
        return new SimpleJsonWriter(output);
    }
}
