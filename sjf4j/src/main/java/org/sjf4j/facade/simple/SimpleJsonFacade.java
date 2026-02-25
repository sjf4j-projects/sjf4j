package org.sjf4j.facade.simple;

import org.sjf4j.facade.JsonFacade;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Built-in lightweight JSON facade.
 */
public class SimpleJsonFacade implements JsonFacade<SimpleJsonReader, SimpleJsonWriter> {

    /**
     * Creates a streaming reader from java.io.Reader.
     */
    @Override
    public SimpleJsonReader createReader(Reader input) throws IOException {
        return new SimpleJsonReader(input);
    }

    /**
     * Creates a streaming writer to java.io.Writer.
     */
    @Override
    public SimpleJsonWriter createWriter(Writer output) throws IOException {
        return new SimpleJsonWriter(output);
    }



}
