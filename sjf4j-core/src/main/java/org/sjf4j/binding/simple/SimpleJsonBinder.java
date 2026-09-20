package org.sjf4j.binding.simple;

import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Built-in lightweight JSON binding.
 */
public final class SimpleJsonBinder extends JsonBinder<SimpleJsonReader, SimpleJsonWriter> {

    public SimpleJsonBinder() {
        this(StreamingContext.EMPTY);
    }

    public SimpleJsonBinder(StreamingContext context) {
        super(context);
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
        return new SimpleJsonWriter(this, output);
    }


}
