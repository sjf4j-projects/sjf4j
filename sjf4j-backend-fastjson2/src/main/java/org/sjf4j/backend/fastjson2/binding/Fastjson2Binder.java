package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.StreamingIO;
import org.sjf4j.exception.BindingException;
import org.sjf4j.util.Asserts;
import org.sjf4j.util.Types;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * JSON binder backed directly by Fastjson2's streaming reader and writer.
 * The supplied contexts are configured in place to use doubles for decimal
 * reads and to write nulls; SJF4J controls omission of null object properties.
 */
public final class Fastjson2Binder extends Binder<Fastjson2Reader, Fastjson2Writer> {

    private final JSONReader.Context readerContext;
    private final JSONWriter.Context writerContext;

    /** Creates a binder with default Fastjson2 contexts. */
    public Fastjson2Binder() {
        this(JSONFactory.createReadContext(), JSONFactory.createWriteContext(), RuntimeContext.EMPTY);
    }

    /**
     * Creates a binder with caller-supplied reader and writer contexts.
     * @param readerContext the read context, configured in place
     * @param writerContext the write context, configured in place
     */
    public Fastjson2Binder(JSONReader.Context readerContext, JSONWriter.Context writerContext) {
        this(readerContext, writerContext, RuntimeContext.EMPTY);
    }

    /**
     * Creates a binder with caller-supplied contexts and runtime settings.
     * @param readerContext the read context, configured in place
     * @param writerContext the write context, configured in place
     * @param context the SJF4J runtime settings
     */
    public Fastjson2Binder(JSONReader.Context readerContext, JSONWriter.Context writerContext,
                           RuntimeContext context) {
        super(context);
        this.readerContext = Asserts.notNull(readerContext, "readerContext");
        this.writerContext = Asserts.notNull(writerContext, "writerContext");
        this.readerContext.config(JSONReader.Feature.UseDoubleForDecimals);
        this.writerContext.config(JSONWriter.Feature.WriteNulls);
    }


    /*
     * --------------------------------------------------------------
     * Read
     * --------------------------------------------------------------
     */

    /**
     * Creates a streaming reader that wraps the supplied Fastjson2 reader.
     * @param reader the Fastjson2 reader
     * @return a SJF4J streaming reader
     */
    public Fastjson2Reader createReader(JSONReader reader) {
        Asserts.notNull(reader, "reader");
        return new Fastjson2Reader(reader);
    }

    @Override
    public Fastjson2Reader createReader(Reader input) throws IOException {
        Asserts.notNull(input, "input");
        return new Fastjson2Reader(JSONReader.of(input, readerContext));
    }

    @Override
    public Fastjson2Reader createReader(InputStream input) {
        Asserts.notNull(input, "input");
        return new Fastjson2Reader(JSONReader.of(input, StandardCharsets.UTF_8, readerContext));
    }

    @Override
    public Fastjson2Reader createReader(String input) throws IOException {
        Asserts.notNull(input, "input");
        return new Fastjson2Reader(JSONReader.of(input, readerContext));
    }

    @Override
    public Fastjson2Reader createReader(byte[] input) throws IOException {
        Asserts.notNull(input, "input");
        return new Fastjson2Reader(JSONReader.of(input, readerContext));
    }


    /*
     * --------------------------------------------------------------
     * Write
     * --------------------------------------------------------------
     */


    /**
     * Creates a streaming writer that wraps the supplied Fastjson2 writer.
     * @param writer the Fastjson2 writer
     * @return a SJF4J streaming writer
     */
    public Fastjson2Writer createWriter(JSONWriter writer) {
        Asserts.notNull(writer, "writer");
        return new Fastjson2Writer(writer);
    }

    @Override
    public Fastjson2Writer createWriter(Writer output) throws IOException {
        Asserts.notNull(output, "output");
        return new Fastjson2Writer(JSONWriter.of(writerContext), output);
    }

    @Override
    public Fastjson2Writer createWriter(OutputStream output) throws IOException {
        Asserts.notNull(output, "output");
        return new Fastjson2Writer(JSONWriter.ofUTF8(writerContext), output);
    }

    /**
     * Creates an in-memory writer using this binder's configured context.
     * @return a writer with a Fastjson2-owned output buffer
     */
    public Fastjson2Writer createWriter() {
        return new Fastjson2Writer(JSONWriter.of(writerContext));
    }

    /**
     * Serializes through the SJF4J binding pipeline while retaining Fastjson2's
     * native in-memory output buffer. The base implementation writes through
     * a {@code Writer} and copies the completed document into a String.
     */
    @Override
    public String writeNodeAsString(Object node) {
        try (JSONWriter output = JSONWriter.of(writerContext)) {
            Fastjson2Writer writer = new Fastjson2Writer(output);
            writer.startDocument();
            StreamingIO.writeNode(writer, node, context);
            writer.endDocument();
            return output.toString();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node) + "' as JSON with Fastjson2", e);
        }
    }


    /** Serializes directly into Fastjson2's UTF-8 byte buffer. */
    @Override
    public byte[] writeNodeAsBytes(Object node) {
        try (JSONWriter output = JSONWriter.ofUTF8(writerContext)) {
            Fastjson2Writer writer = new Fastjson2Writer(output);
            writer.startDocument();
            StreamingIO.writeNode(writer, node, context);
            writer.endDocument();
            return output.getBytes();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node)
                    + "' as UTF-8 JSON with Fastjson2", e);
        }
    }
}
