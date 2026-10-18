package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.StreamingIO;
import org.sjf4j.exception.BindingException;
import org.sjf4j.util.Types;
import org.sjf4j.util.Asserts;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.io.SegmentedStringWriter;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.BufferRecycler;

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
public final class Jackson3Binder extends Binder<Jackson3Reader, Jackson3Writer> {

    private static final ObjectReadContext READ_CONTEXT = ObjectReadContext.empty();
    private static final ObjectWriteContext WRITE_CONTEXT = ObjectWriteContext.empty();

    private final JsonFactory factory;

    /** Creates a binder with a default Jackson 3 factory. */
    public Jackson3Binder() {
        this(new JsonFactory(), RuntimeContext.EMPTY);
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
    public Jackson3Reader createReader(Reader input) throws IOException {
        return new Jackson3Reader(factory.createParser(
                READ_CONTEXT,
                Asserts.notNull(input, "input")));
    }

    @Override
    public Jackson3Reader createReader(InputStream input) throws IOException {
        return new Jackson3Reader(factory.createParser(
                READ_CONTEXT,
                Asserts.notNull(input, "input")));
    }

    /**
     * Creates a streaming reader that wraps the supplied Jackson parser.
     * @param parser the Jackson 3 parser
     * @return a SJF4J streaming reader
     */
    public Jackson3Reader createReader(JsonParser parser) {
        return new Jackson3Reader(Asserts.notNull(parser, "parser"));
    }

    @Override
    public Jackson3Reader createReader(String input) throws IOException {
        return new Jackson3Reader(factory.createParser(
                READ_CONTEXT,
                Asserts.notNull(input, "input")));
    }

    @Override
    public Jackson3Reader createReader(byte[] input) throws IOException {
        return new Jackson3Reader(
                factory.createParser(READ_CONTEXT, Asserts.notNull(input, "input")));
    }

    @Override
    public Jackson3Writer createWriter(Writer output) throws IOException {
        JsonGenerator generator = factory.createGenerator(
                WRITE_CONTEXT, Asserts.notNull(output, "output"));
        return new Jackson3Writer(generator);
    }

    /**
     * Creates a streaming writer that wraps the supplied Jackson generator.
     * @param generator the Jackson 3 generator
     * @return a SJF4J streaming writer
     */
    public Jackson3Writer createWriter(JsonGenerator generator) {
        return new Jackson3Writer(Asserts.notNull(generator, "generator"));
    }

    @Override
    public Jackson3Writer createWriter(OutputStream output) throws IOException {
        JsonGenerator generator = factory.createGenerator(
                WRITE_CONTEXT, Asserts.notNull(output, "output"));
        return new Jackson3Writer(generator);
    }

    /**
     * Creates an in-memory writer backed by a pooled Jackson buffer.
     * @return a writer that owns the pooled buffer
     * @throws IOException if the generator cannot be created
     */
    public Jackson3Writer createWriter() throws IOException {
        BufferRecycler recycler = factory._getBufferRecycler();
        SegmentedStringWriter output = new SegmentedStringWriter(recycler);
        try {
            return new Jackson3Writer(factory.createGenerator(WRITE_CONTEXT, output), output, recycler);
        } catch (RuntimeException e) {
            recycler.releaseToPool();
            throw e;
        }
    }

    /** Creates an owned UTF-8 writer using Jackson's pooled output buffer. */
    public Jackson3Writer createWriterUtf8() throws IOException {
        BufferRecycler recycler = factory._getBufferRecycler();
        ByteArrayBuilder output = new ByteArrayBuilder(recycler);
        try {
            return new Jackson3Writer(factory.createGenerator(WRITE_CONTEXT, output), output, recycler);
        } catch (RuntimeException e) {
            output.release();
            recycler.releaseToPool();
            throw e;
        }
    }

    @Override
    public String writeNodeAsString(Object node) {
        final BufferRecycler recycler = factory._getBufferRecycler();
        final SegmentedStringWriter output = new SegmentedStringWriter(recycler);

        try {
            try (JsonGenerator generator = factory.createGenerator(WRITE_CONTEXT, output)) {
                StreamingIO.writeNode(new Jackson3Writer(generator), node, context);
            }
            return output.getAndClear();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node) + "' into JSON", e);
        } finally {
            recycler.releaseToPool();
        }
    }

    @Override
    public byte[] writeNodeAsBytes(Object node) {
        final BufferRecycler recycler = factory._getBufferRecycler();
        final ByteArrayBuilder output = new ByteArrayBuilder(recycler);

        try {
            try (JsonGenerator generator = factory.createGenerator(WRITE_CONTEXT, output)) {
                StreamingIO.writeNode(new Jackson3Writer(generator), node, context);
            }
            return output.toByteArray();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node) + "' into JSON", e);
        } finally {
            output.release();
            recycler.releaseToPool();
        }
    }
}
