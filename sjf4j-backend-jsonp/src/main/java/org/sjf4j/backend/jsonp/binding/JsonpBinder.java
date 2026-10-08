package org.sjf4j.backend.jsonp.binding;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.sjf4j.binding.Binder;
import org.sjf4j.RuntimeContext;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/** JSON binder backed directly by a Jakarta JSON-P {@link JsonProvider}. */
public final class JsonpBinder extends Binder<JsonpReader, JsonpWriter> {

    private final JsonProvider provider;

    public JsonpBinder() {
        this(JsonProvider.provider());
    }

    public JsonpBinder(JsonProvider provider) {
        this(provider, RuntimeContext.EMPTY);
    }

    public JsonpBinder(JsonProvider provider, RuntimeContext context) {
        super(context);
        this.provider = Asserts.notNull(provider, "provider");
    }

    /** Creates a streaming reader that wraps the supplied JSON-P parser. */
    public JsonpReader createReader(JsonParser parser) {
        return new JsonpReader(Asserts.notNull(parser, "parser"));
    }

    @Override
    public JsonpReader createReader(Reader input) throws IOException {
        return createReader(provider.createParser(Asserts.notNull(input, "input")));
    }

    /** Creates a streaming writer that wraps the supplied JSON-P generator. */
    public JsonpWriter createWriter(JsonGenerator generator) {
        return new JsonpWriter(Asserts.notNull(generator, "generator"));
    }

    @Override
    public JsonpWriter createWriter(Writer output) throws IOException {
        return createWriter(provider.createGenerator(Asserts.notNull(output, "output")));
    }
}
