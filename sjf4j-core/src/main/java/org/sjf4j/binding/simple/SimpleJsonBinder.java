package org.sjf4j.binding.simple;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Built-in lightweight JSON binding.
 */
public final class SimpleJsonBinder extends Binder<SimpleJsonParser, SimpleJsonGenerator> {

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
    public SimpleJsonParser createParser(Reader input) throws IOException {
        return new SimpleJsonParser(input);
    }

    /**
     * Creates a binding reader from input string.
     */
    @Override
    public SimpleJsonParser createParser(String input) throws IOException {
        return new SimpleJsonParser(Asserts.notNull(input, "input"));
    }

    /**
     * Creates a binding writer to java.io.Writer.
     */
    @Override
    public SimpleJsonGenerator createGenerator(Writer output) throws IOException {
        return new SimpleJsonGenerator(this, output);
    }



    @Override
    public byte[] writeNodeAsBytes(Object node) {
        return writeNodeAsString(node).getBytes(StandardCharsets.UTF_8);
    }
}
