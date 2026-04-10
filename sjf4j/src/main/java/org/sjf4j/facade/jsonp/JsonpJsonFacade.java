package org.sjf4j.facade.jsonp;


import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.node.Types;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Objects;


public class JsonpJsonFacade implements JsonFacade<JsonpReader, JsonpWriter> {
    private final JsonProvider jsonProvider;

    public JsonpJsonFacade() {
        this.jsonProvider = JsonProvider.provider();
    }

    @Override
    public StreamingMode streamingMode() {
        // JSON-P only participates through the shared StreamingIO path in this facade.
        return StreamingMode.SHARED_IO;
    }


    /// Reader

    /**
     * Creates a streaming reader from java.io.Reader.
     */
    @Override
    public JsonpReader createReader(Reader input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new JsonpReader(jsonProvider.createParser(input));
    }

    /**
     * Creates a streaming reader from InputStream.
     */
    @Override
    public JsonpReader createReader(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new JsonpReader(jsonProvider.createParser(input));
    }

    /**
     * Creates a streaming reader from JSON string.
     */
    @Override
    public JsonpReader createReader(String input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new JsonpReader(jsonProvider.createParser(new StringReader(input)));
    }

    /**
     * Creates a streaming reader from JSON bytes.
     */
    @Override
    public JsonpReader createReader(byte[] input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new JsonpReader(jsonProvider.createParser(new ByteArrayInputStream(input)));
    }


    /// Writer

    /**
     * Creates a streaming writer to java.io.Writer.
     */
    @Override
    public JsonpWriter createWriter(Writer output) throws IOException {
        return new JsonpWriter(jsonProvider.createGenerator(output));
    }

    /**
     * Creates a streaming writer to OutputStream.
     */
    @Override
    public JsonpWriter createWriter(OutputStream output) throws IOException {
        return new JsonpWriter(jsonProvider.createGenerator(output));
    }


}
