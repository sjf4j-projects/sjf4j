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
import java.nio.charset.StandardCharsets;

/**
 * Streaming JSON binder backed by a Jakarta JSON-P {@link JsonProvider}.
 *
 * <p>Reads POJOs and directly populated Java containers. Streaming reads
 * do not construct native JSON-P target types (including scalar JsonValue);
  * obtain these via {@link org.sjf4j.node.NodeMapper} conversion instead. Existing native JSON-P
 * nodes can still be written.
 */
public final class JsonpBinder extends Binder<JsonpParser, JsonpGenerator> {

    private final JsonProvider provider;

    /** Creates a binder with the installed JSON-P provider. */
    public JsonpBinder() {
        this(JsonProvider.provider());
    }

    /**
     * Creates a binder with default backend settings and a supplied runtime context.
     * @param context the SJF4J runtime settings
     */
    public JsonpBinder(RuntimeContext context) {
        this(JsonProvider.provider(), context);
    }

    /**
     * Creates a binder with an explicit JSON-P provider.
     * @param provider the JSON-P provider
     */
    public JsonpBinder(JsonProvider provider) {
        this(provider, RuntimeContext.EMPTY);
    }

    /**
     * Creates a binder with an explicit provider and runtime settings.
     * @param provider the JSON-P provider
     * @param context the SJF4J runtime settings
     */
    public JsonpBinder(JsonProvider provider, RuntimeContext context) {
        super(context);
        this.provider = Asserts.notNull(provider, "provider");
    }

    /**
     * Creates a streaming reader that wraps the supplied JSON-P parser.
     * @param parser the JSON-P parser
     * @return a SJF4J streaming reader
     */
    public JsonpParser createParser(JsonParser parser) {
        return new JsonpParser(Asserts.notNull(parser, "parser"));
    }

    @Override
    public JsonpParser createParser(Reader input) throws IOException {
        Reader protectedInput = new java.io.FilterReader(Asserts.notNull(input, "input")) {
            @Override public void close() { /* Caller owns input. */ }
        };
        return createParser(provider.createParser(protectedInput));
    }

    /**
     * Creates a streaming writer that wraps the supplied JSON-P generator.
     * @param generator the JSON-P generator
     * @return a SJF4J streaming writer
     */
    public JsonpGenerator createGenerator(JsonGenerator generator) {
        return new JsonpGenerator(Asserts.notNull(generator, "generator"));
    }

    @Override
    public JsonpGenerator createGenerator(Writer output) throws IOException {
        Writer protectedOutput = new java.io.FilterWriter(Asserts.notNull(output, "output")) {
            @Override public void close() { /* Caller owns output. */ }
        };
        return createGenerator(provider.createGenerator(protectedOutput));
    }

    @Override
    public byte[] writeNodeAsBytes(Object node) {
        return writeNodeAsString(node).getBytes(StandardCharsets.UTF_8);
    }
}
