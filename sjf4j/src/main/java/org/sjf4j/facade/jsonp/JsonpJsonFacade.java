package org.sjf4j.facade.jsonp;


import jakarta.json.spi.JsonProvider;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.FacadeProvider;
import org.sjf4j.facade.JsonFacade;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;


public class JsonpJsonFacade implements JsonFacade<JsonpReader, JsonpWriter> {
    private final JsonProvider jsonProvider;
    private final StreamingContext streamingContext;

    public JsonpJsonFacade() {
        this(StreamingContext.EMPTY);
    }

    public JsonpJsonFacade(StreamingContext context) {
        Objects.requireNonNull(context, "context");
        this.jsonProvider = JsonProvider.provider();
        this.streamingContext = context;
    }

    public static FacadeProvider<JsonFacade<?, ?>> provider() {
        return JsonpJsonFacade::new;
    }

    @Override
    public StreamingContext streamingContext() {
        return streamingContext;
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
