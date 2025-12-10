package org.sjf4j.facades.simple;

import org.sjf4j.facades.JsonFacade;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

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
