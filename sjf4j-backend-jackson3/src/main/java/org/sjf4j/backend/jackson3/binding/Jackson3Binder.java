package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.json.JsonFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

/**
 * JSON binder backed by a Jackson 3 {@link JsonFactory}. The supplied factory
 * is rebuilt with charset detection and automatic source closing disabled;
 * caller-supplied output targets are not closed by the binder.
 */
public final class Jackson3Binder extends Binder<Jackson3Parser, Jackson3Generator> {

    private static final ObjectReadContext READ_CONTEXT = ObjectReadContext.empty();
    private static final ObjectWriteContext WRITE_CONTEXT = ObjectWriteContext.empty();

    private final JsonFactory factory;

    /** Creates a binder with a default Jackson 3 factory. */
    public Jackson3Binder() {
        this(new JsonFactory(), RuntimeContext.EMPTY);
    }

    /**
     * Creates a binder with default backend settings and a supplied runtime context.
     * @param context the SJF4J runtime settings
     */
    public Jackson3Binder(RuntimeContext context) {
        this(new JsonFactory(), context);
    }

    /**
     * Creates a binder from a supplied factory without mutating that factory.
     * @param factory the Jackson 3 factory to rebuild
     */
    public Jackson3Binder(JsonFactory factory) {
        this(factory, RuntimeContext.EMPTY);
    }

    /**
     * Creates a binder from a supplied factory with runtime settings.
     * @param factory the Jackson 3 factory to rebuild
     * @param context the SJF4J runtime settings
     */
    public Jackson3Binder(JsonFactory factory, RuntimeContext context) {
        super(context);
        Asserts.notNull(factory, "factory");
        this.factory = factory.rebuild()
                .disable(TokenStreamFactory.Feature.CHARSET_DETECTION)
                .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .build();
    }

    @Override
    public Jackson3Parser createParser(Reader input) throws IOException {
        return new Jackson3Parser(factory.createParser(
                READ_CONTEXT,
                Asserts.notNull(input, "input")));
    }

    @Override
    public Jackson3Parser createParser(InputStream input) throws IOException {
        return new Jackson3Parser(factory.createParser(
                READ_CONTEXT,
                Asserts.notNull(input, "input")));
    }

    /**
     * Creates a streaming reader that wraps the supplied Jackson parser.
     * @param parser the Jackson 3 parser
     * @return a SJF4J streaming reader
     */
    public Jackson3Parser createParser(JsonParser parser) {
        return new Jackson3Parser(Asserts.notNull(parser, "parser"));
    }

    @Override
    public Jackson3Parser createParser(String input) throws IOException {
        return new Jackson3Parser(factory.createParser(
                READ_CONTEXT,
                Asserts.notNull(input, "input")));
    }

    @Override
    public Jackson3Parser createParser(byte[] input) throws IOException {
        return new Jackson3Parser(
                factory.createParser(READ_CONTEXT, Asserts.notNull(input, "input")));
    }

    @Override
    public Jackson3Generator createGenerator(Writer output) throws IOException {
        JsonGenerator generator = factory.createGenerator(
                WRITE_CONTEXT, Asserts.notNull(output, "output"));
        return new Jackson3Generator(generator);
    }

    /**
     * Creates a streaming writer that wraps the supplied Jackson generator.
     * @param generator the Jackson 3 generator
     * @return a SJF4J streaming writer
     */
    public Jackson3Generator createGenerator(JsonGenerator generator) {
        return new Jackson3Generator(Asserts.notNull(generator, "generator"));
    }

    @Override
    public Jackson3Generator createGenerator(OutputStream output) throws IOException {
        JsonGenerator generator = factory.createGenerator(
                WRITE_CONTEXT, Asserts.notNull(output, "output"));
        return new Jackson3Generator(generator);
    }

}
