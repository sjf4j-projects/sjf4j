package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.StreamingIO;
import org.sjf4j.RuntimeContext;
import org.sjf4j.exception.BindingException;
import org.sjf4j.util.Types;
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
public final class Jackson2Binder extends Binder<Jackson2Reader, Jackson2Writer> {

    private final JsonFactory factory;

    /** Creates a binder with a default Jackson 2 factory. */
    public Jackson2Binder() {
        this(new JsonFactory(), RuntimeContext.EMPTY);
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
    }

    /**
     * Creates a streaming reader that wraps the supplied Jackson parser.
     * @param parser the Jackson 2 parser
     * @return a SJF4J streaming reader
     */
    public Jackson2Reader createReader(JsonParser parser) {
        return new Jackson2Reader(Asserts.notNull(parser, "parser"));
    }

    @Override
    public Jackson2Reader createReader(Reader input) throws IOException {
        return new Jackson2Reader(factory.createParser(Asserts.notNull(input, "input")));
    }

    @Override
    public Jackson2Reader createReader(InputStream input) throws IOException {
        return new Jackson2Reader(factory.createParser(Asserts.notNull(input, "input")));
    }

    @Override
    public Jackson2Reader createReader(String input) throws IOException {
        Asserts.notNull(input, "input");
        return new Jackson2Reader(factory.createParser(input));
    }

    @Override
    public Jackson2Reader createReader(byte[] input) throws IOException {
        Asserts.notNull(input, "input");
        return new Jackson2Reader(factory.createParser(input));
    }



    /**
     * Creates a streaming writer that wraps the supplied Jackson generator.
     * @param generator the Jackson 2 generator
     * @return a SJF4J streaming writer
     */
    public Jackson2Writer createWriter(JsonGenerator generator) {
        return new Jackson2Writer(Asserts.notNull(generator, "generator"));
    }

    @Override
    public Jackson2Writer createWriter(Writer output) throws IOException {
        JsonGenerator generator = factory.createGenerator(Asserts.notNull(output, "output"));
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        return new Jackson2Writer(generator);
    }

    @Override
    public Jackson2Writer createWriter(OutputStream output) throws IOException {
        JsonGenerator generator = factory.createGenerator(Asserts.notNull(output, "output"), JsonEncoding.UTF8);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        return new Jackson2Writer(generator);
    }

    /**
     * Creates an in-memory writer backed by a pooled Jackson buffer.
     * @return a writer that owns the pooled buffer
     * @throws IOException if the generator cannot be created
     */
    public Jackson2Writer createWriter() throws IOException {
        BufferRecycler recycler = factory._getBufferRecycler();
        SegmentedStringWriter output = new SegmentedStringWriter(recycler);
        try {
            return new Jackson2Writer(factory.createGenerator(output), output, recycler);
        } catch (IOException | RuntimeException e) {
            recycler.releaseToPool();
            throw e;
        }
    }

    /** Creates an owned UTF-8 writer using Jackson's pooled output buffer. */
    public Jackson2Writer createWriterUtf8() throws IOException {
        BufferRecycler recycler = factory._getBufferRecycler();
        ByteArrayBuilder output = new ByteArrayBuilder(recycler);
        try {
            return new Jackson2Writer(factory.createGenerator(output, JsonEncoding.UTF8), output, recycler);
        } catch (IOException | RuntimeException e) {
            output.release();
            recycler.releaseToPool();
            throw e;
        }
    }

    @Override
    public String writeNodeAsString(Object node) {
        final BufferRecycler br = factory._getBufferRecycler();
        try (SegmentedStringWriter sw = new SegmentedStringWriter(br)) {
            try (Jackson2Writer writer = new Jackson2Writer(factory.createGenerator(sw))) {
                writer.startDocument();
                StreamingIO.writeNode(writer, node, context);
                writer.endDocument();
            }
            return sw.getAndClear();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node) + "' into JSON", e);
        } finally {
            br.releaseToPool();
        }
    }

    @Override
    public byte[] writeNodeAsBytes(Object node) {
        final BufferRecycler br = factory._getBufferRecycler();
        final ByteArrayBuilder output = new ByteArrayBuilder(br);
        try {
            try (Jackson2Writer writer = new Jackson2Writer(factory.createGenerator(output, JsonEncoding.UTF8))) {
                writer.startDocument();
                StreamingIO.writeNode(writer, node, context);
                writer.endDocument();
            }
            return output.toByteArray();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node) + "' into JSON", e);
        } finally {
            output.release();
            br.releaseToPool();
        }
    }
}
