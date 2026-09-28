package org.sjf4j.binding.simple;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.StreamingBinder;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Built-in lightweight JSON binding.
 */
public final class SimpleJsonBinder extends StreamingBinder<SimpleJsonReader, SimpleJsonWriter> {

    public SimpleJsonBinder() {
        this(RuntimeContext.EMPTY);
    }

    public SimpleJsonBinder(RuntimeContext context) {
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
     * Creates a binding reader from input string.
     */
    @Override
    public SimpleJsonReader createReader(String input) throws IOException {
        return new SimpleJsonReader(Asserts.notNull(input, "input"));
    }

    /**
     * Creates a binding writer to java.io.Writer.
     */
    @Override
    public SimpleJsonWriter createWriter(Writer output) throws IOException {
        return new SimpleJsonWriter(this, output);
    }


}
