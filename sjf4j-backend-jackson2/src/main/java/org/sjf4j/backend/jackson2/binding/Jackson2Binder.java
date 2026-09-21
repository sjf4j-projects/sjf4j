package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.BufferRecycler;
import org.sjf4j.binding.FastStringWriter;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.PreparedName;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.StreamingIO;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;

/** JSON binder backed directly by a Jackson 2 {@link JsonFactory}. */
public class Jackson2Binder extends JsonBinder<Jackson2Reader, Jackson2Writer> {

    private final JsonFactory factory;

    public Jackson2Binder(JsonFactory factory) {
        this(factory, StreamingContext.EMPTY);
    }

    @SuppressWarnings("deprecation")
    public Jackson2Binder(JsonFactory factory, StreamingContext context) {
        super(context);
        Objects.requireNonNull(factory, "factory");
        this.factory = factory;
        this.factory.disable(JsonFactory.Feature.CHARSET_DETECTION);
    }

    @Override

    public Jackson2Reader createReader(Reader input) throws IOException {
        return new Jackson2Reader(factory.createParser(Objects.requireNonNull(input, "input")));
    }

    /** Creates a streaming reader that wraps the supplied Jackson parser. */
    public Jackson2Reader createReader(JsonParser parser) {
        return new Jackson2Reader(Objects.requireNonNull(parser, "parser"));
    }

    @Override

    public Jackson2Reader createReader(String input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson2Reader(factory.createParser(input));
    }

    @Override

    public Jackson2Reader createReader(byte[] input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson2Reader(factory.createParser(input));
    }




    @Override




    public Jackson2Writer createWriter(Writer output) throws IOException {
        return new Jackson2Writer(this, factory.createGenerator(Objects.requireNonNull(output, "output")));
    }

    /** Creates a streaming writer that wraps the supplied Jackson generator. */
    public Jackson2Writer createWriter(JsonGenerator generator) {
        return new Jackson2Writer(this, Objects.requireNonNull(generator, "generator"));
    }

    @Override

    public Jackson2Writer createWriter(OutputStream output) throws IOException {
        return new Jackson2Writer(this, factory.createGenerator(Objects.requireNonNull(output, "output"), JsonEncoding.UTF8));
    }

    @Override

    public String writeNodeAsString(Object node) {
        final BufferRecycler br = factory._getBufferRecycler();
        try (SegmentedStringWriter sw = new SegmentedStringWriter(br)) {
            JsonGenerator gen = factory.createGenerator(sw);
            StreamingIO.writeNode(new Jackson2Writer(this, gen), node, context);
            gen.flush();
            return sw.getAndClear();
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node) + "' into JSON", e);
        } finally {
            br.releaseToPool();
        }
    }

    public String writeNodeAsStringFast(Object node) {
        try (FastStringWriter output = new FastStringWriter()) {
            writeNode(output, node);
            return output.toString();
        } catch (Exception e) {
            throw new BindingException(e);
        }
    }


    @Override


    public PreparedName createPreparedName(String name) {
        return new Jackson2PreparedName(name);
    }


}
