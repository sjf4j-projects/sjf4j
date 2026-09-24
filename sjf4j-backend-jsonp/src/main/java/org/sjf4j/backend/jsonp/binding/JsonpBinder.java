package org.sjf4j.backend.jsonp.binding;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.util.Asserts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** JSON binder backed directly by a Jakarta JSON-P {@link JsonProvider}. */
public class JsonpBinder extends JsonBinder<JsonpReader, JsonpWriter> {

    private final JsonProvider provider;

    public JsonpBinder() {
        this(JsonProvider.provider());
    }

    public JsonpBinder(JsonProvider provider) {
        this(provider, StreamingContext.EMPTY);
    }

    public JsonpBinder(JsonProvider provider, StreamingContext context) {
        super(context);
        this.provider = Asserts.notNull(provider, "provider");
    }

    @Override
    public JsonpReader createReader(Reader input) throws IOException {
        return new JsonpReader(provider.createParser(Asserts.notNull(input, "input")));
    }

    @Override
    public JsonpReader createReader(InputStream input) throws IOException {
        return createReader(new InputStreamReader(
                Asserts.notNull(input, "input"), StandardCharsets.UTF_8));
    }

    @Override
    public JsonpReader createReader(String input) throws IOException {
        return createReader(new StringReader(Asserts.notNull(input, "input")));
    }

    @Override
    public JsonpReader createReader(byte[] input) throws IOException {
        return createReader(new InputStreamReader(
                new ByteArrayInputStream(Asserts.notNull(input, "input")), StandardCharsets.UTF_8));
    }

    /** Creates a streaming reader that wraps the supplied JSON-P parser. */
    public JsonpReader createReader(JsonParser parser) {
        return new JsonpReader(Asserts.notNull(parser, "parser"));
    }

    @Override
    public JsonpWriter createWriter(Writer output) throws IOException {
        return new JsonpWriter(this, provider.createGenerator(Asserts.notNull(output, "output")));
    }

    @Override
    public JsonpWriter createWriter(OutputStream output) throws IOException {
        return new JsonpWriter(this, provider.createGenerator(Asserts.notNull(output, "output")));
    }

    /** Creates a streaming writer that wraps the supplied JSON-P generator. */
    public JsonpWriter createWriter(JsonGenerator generator) {
        return new JsonpWriter(this, Asserts.notNull(generator, "generator"));
    }
}
