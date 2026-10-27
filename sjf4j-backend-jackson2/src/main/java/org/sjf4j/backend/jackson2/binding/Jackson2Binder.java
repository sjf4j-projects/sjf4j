package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import org.sjf4j.binding.Binder;
import org.sjf4j.RuntimeContext;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

/**
 * JSON binder backed directly by a Jackson 2 {@link JsonFactory}.
 * The constructor disables charset detection and automatic source closing on
 * the supplied factory; writers do not close caller-supplied output targets.
 */
public final class Jackson2Binder extends Binder<Jackson2Parser, Jackson2Generator> {

    private final JsonFactory factory;

    /** Creates a binder with a default Jackson 2 factory. */
    public Jackson2Binder() {
        this(new JsonFactory(), RuntimeContext.EMPTY);
    }

    /**
     * Creates a binder with default backend settings and a supplied runtime context.
     * @param context the SJF4J runtime settings
     */
    public Jackson2Binder(RuntimeContext context) {
        this(new JsonFactory(), context);
    }

    /**
     * Creates a binder using a supplied factory, which is configured in place.
     * @param factory the Jackson 2 factory to configure
     */
    public Jackson2Binder(JsonFactory factory) {
        this(factory, RuntimeContext.EMPTY);
    }

    /**
     * Creates a binder using a supplied factory and runtime settings.
     * @param factory the Jackson 2 factory to configure
     * @param context the SJF4J runtime settings
     */
    @SuppressWarnings("deprecation")
    public Jackson2Binder(JsonFactory factory, RuntimeContext context) {
        super(context);
        Asserts.notNull(factory, "factory");
        this.factory = factory;
        this.factory.disable(JsonFactory.Feature.CHARSET_DETECTION);
        this.factory.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        this.factory.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    }

    /**
     * Creates a streaming reader that wraps the supplied Jackson parser.
     * @param parser the Jackson 2 parser
     * @return a SJF4J streaming reader
     */
    public Jackson2Parser createParser(JsonParser parser) {
        return new Jackson2Parser(Asserts.notNull(parser, "parser"));
    }

    @Override
    public Jackson2Parser createParser(Reader input) throws IOException {
        return new Jackson2Parser(factory.createParser(Asserts.notNull(input, "input")));
    }

    @Override
    public Jackson2Parser createParser(InputStream input) throws IOException {
        return new Jackson2Parser(factory.createParser(Asserts.notNull(input, "input")));
    }

    @Override
    public Jackson2Parser createParser(String input) throws IOException {
        Asserts.notNull(input, "input");
        return new Jackson2Parser(factory.createParser(input));
    }

    @Override
    public Jackson2Parser createParser(byte[] input) throws IOException {
        Asserts.notNull(input, "input");
        return new Jackson2Parser(factory.createParser(input));
    }



    /**
     * Creates a streaming writer that wraps the supplied Jackson generator.
     * @param generator the Jackson 2 generator
     * @return a SJF4J streaming writer
     */
    public Jackson2Generator createGenerator(JsonGenerator generator) {
        return new Jackson2Generator(Asserts.notNull(generator, "generator"));
    }

    @Override
    public Jackson2Generator createGenerator(Writer output) throws IOException {
        JsonGenerator generator = factory.createGenerator(Asserts.notNull(output, "output"));
        return new Jackson2Generator(generator);
    }

    @Override
    public Jackson2Generator createGenerator(OutputStream output) throws IOException {
        JsonGenerator generator = factory.createGenerator(Asserts.notNull(output, "output"), JsonEncoding.UTF8);
        return new Jackson2Generator(generator);
    }

}
