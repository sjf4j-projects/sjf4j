package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.FastStringWriter;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.CompiledName;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.Types;
import org.sjf4j.util.Asserts;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.io.SegmentedStringWriter;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.BufferRecycler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

/** JSON binder backed directly by a Jackson 3 {@link JsonFactory}. */
public final class Jackson3Binder extends Binder<Jackson3Reader, Jackson3Writer> {

    private static final ObjectReadContext READ_CONTEXT = ObjectReadContext.empty();
    private static final ObjectWriteContext WRITE_CONTEXT = ObjectWriteContext.empty();

    private final JsonFactory factory;

    public Jackson3Binder() {
        this(new JsonFactory(), RuntimeContext.EMPTY);
    }

    public Jackson3Binder(JsonFactory factory) {
        this(factory, RuntimeContext.EMPTY);
    }

    public Jackson3Binder(JsonFactory factory, RuntimeContext context) {
        super(context);
        Asserts.notNull(factory, "factory");
        this.factory = factory.rebuild()
                .disable(TokenStreamFactory.Feature.CHARSET_DETECTION)
                .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .build();
    }

    @Override
    public Jackson3Reader createReader(Reader input) throws IOException {
        return new Jackson3Reader(factory.createParser(
                READ_CONTEXT,
                Asserts.notNull(input, "input")));
    }

    /** Creates a streaming reader that wraps the supplied Jackson parser. */
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
        return new Jackson3Writer(this,
                factory.createGenerator(WRITE_CONTEXT, Asserts.notNull(output, "output")));
    }

    /** Creates a streaming writer that wraps the supplied Jackson generator. */
    public Jackson3Writer createWriter(JsonGenerator generator) {
        return new Jackson3Writer(this,
                Asserts.notNull(generator, "generator"));
    }

    @Override
    public Jackson3Writer createWriter(OutputStream output) throws IOException {
        return new Jackson3Writer(this,
                factory.createGenerator(WRITE_CONTEXT, Asserts.notNull(output, "output")));
    }

    @Override
    public String writeNodeAsString(Object node) {
        final BufferRecycler recycler = factory._getBufferRecycler();
        final SegmentedStringWriter output = new SegmentedStringWriter(recycler);

        try {
            try (JsonGenerator generator = factory.createGenerator(WRITE_CONTEXT, output)) {
                StreamingIO.writeNode(new Jackson3Writer(this, generator), node, context);
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

    public String writeNodeAsStringFast(Object node) {
        try (FastStringWriter output = new FastStringWriter()) {
            writeNode(output, node);
            return output.toString();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException(e);
        }
    }

    @Override
    public CompiledName createPreparedName(String name) {
        return new Jackson3Name(name);
    }
}
