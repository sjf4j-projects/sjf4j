package org.sjf4j.facade.simple;

import org.sjf4j.facade.JsonFacade;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Built-in lightweight JSON facade.
 */
public class SimpleJsonFacade implements JsonFacade<SimpleJsonReader, SimpleJsonWriter> {

    @Override
    public SimpleJsonReader createReader(Reader input) throws IOException {
        return new SimpleJsonReader(input);
    }

    @Override
    public SimpleJsonWriter createWriter(Writer output) throws IOException {
        return new SimpleJsonWriter(output);
    }



}
