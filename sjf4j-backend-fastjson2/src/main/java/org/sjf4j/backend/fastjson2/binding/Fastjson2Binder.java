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

import java.io.FilterInputStream;
import java.io.FilterReader;
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
public final class Fastjson2Binder extends Binder<Fastjson2Parser, Fastjson2Generator> {

    private final JSONReader.Context readerContext;
    private final JSONWriter.Context writerContext;

    /** Creates a binder with default Fastjson2 contexts. */
    public Fastjson2Binder() {
        this(JSONFactory.createReadContext(), JSONFactory.createWriteContext(), RuntimeContext.EMPTY);
    }

    /**
     * Creates a binder with default backend settings and a supplied runtime context.
     * @param context the SJF4J runtime settings
     */
    public Fastjson2Binder(RuntimeContext context) {
        this(JSONFactory.createReadContext(), JSONFactory.createWriteContext(), context);
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
    public Fastjson2Parser createParser(JSONReader reader) {
        Asserts.notNull(reader, "reader");
        return new Fastjson2Parser(reader);
    }

    @Override
    public Fastjson2Parser createParser(Reader input) throws IOException {
        Asserts.notNull(input, "input");
        Reader protectedInput = new FilterReader(input) {
            @Override public void close() { /* Caller owns input. */ }
        };
        return new Fastjson2Parser(JSONReader.of(protectedInput, readerContext));
    }

    @Override
    public Fastjson2Parser createParser(InputStream input) {
        Asserts.notNull(input, "input");
        InputStream protectedInput = new FilterInputStream(input) {
            @Override public void close() { /* Caller owns input. */ }
        };
        return new Fastjson2Parser(JSONReader.of(protectedInput, StandardCharsets.UTF_8, readerContext));
    }

    @Override
    public Fastjson2Parser createParser(String input) throws IOException {
        Asserts.notNull(input, "input");
        return new Fastjson2Parser(JSONReader.of(input, readerContext));
    }

    @Override
    public Fastjson2Parser createParser(byte[] input) throws IOException {
        Asserts.notNull(input, "input");
        return new Fastjson2Parser(JSONReader.of(input, readerContext));
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
    public Fastjson2Generator createGenerator(JSONWriter writer) {
        Asserts.notNull(writer, "writer");
        return new Fastjson2Generator(writer);
    }

    @Override
    public Fastjson2Generator createGenerator(Writer output) throws IOException {
        Asserts.notNull(output, "output");
        return new Fastjson2Generator(JSONWriter.of(writerContext), output);
    }

    @Override
    public Fastjson2Generator createGenerator(OutputStream output) throws IOException {
        Asserts.notNull(output, "output");
        return new Fastjson2Generator(JSONWriter.ofUTF8(writerContext), output);
    }

    /**
     * Creates an in-memory writer using this binder's configured context.
     * @return a writer with a Fastjson2-owned output buffer
     */
    public Fastjson2Generator createGenerator() {
        return new Fastjson2Generator(JSONWriter.of(writerContext));
    }

    /**
     * Creates a configured native UTF-16 writer for compiled String output.
     * @return the native writer
     */
    public JSONWriter createNativeWriter() {
        return JSONWriter.of(writerContext);
    }

    /**
     * Creates a configured native UTF-8 writer for compiled byte[] output.
     * @return the native writer
     */
    public JSONWriter createNativeWriterUtf8() {
        return JSONWriter.ofUTF8(writerContext);
    }

    /**
     * Checks whether a native UTF-8 writer can emit pre-escaped field names.
     * @param writer the native writer to check
     * @return whether raw UTF-8 names are supported
     */
    public static boolean supportsRawUtf8Names(JSONWriter writer) {
        return writer.isUTF8() && rawNamesCompatible(writer);
    }

    /**
     * Checks whether a native UTF-16 writer can emit pre-escaped field names.
     * @param writer the native writer to check
     * @return whether raw UTF-16 names are supported
     */
    public static boolean supportsRawUtf16Names(JSONWriter writer) {
        return writer.isUTF16() && rawNamesCompatible(writer);
    }

    /** Shared feature gate used by both native compiled and runtime writers. */
    static boolean rawNamesCompatible(JSONWriter writer) {
        return !writer.isUseSingleQuotes()
                && !writer.isEnabled(JSONWriter.Feature.UnquoteFieldName)
                && !writer.isEnabled(JSONWriter.Feature.BrowserSecure)
                && !writer.isEnabled(JSONWriter.Feature.EscapeNoneAscii);
    }

    /**
     * Creates an in-memory UTF-8 writer for compiled byte[] output.
     * @return the UTF-8 streaming writer
     */
    public Fastjson2Generator createGeneratorUtf8() {
        return new Fastjson2Generator(JSONWriter.ofUTF8(writerContext));
    }

    /**
     * Serializes through the SJF4J binding pipeline while retaining Fastjson2's
     * native in-memory output buffer. The base implementation writes through
     * a {@code Writer} and copies the completed document into a String.
     */
    @Override
    public String writeNodeAsString(Object node) {
        try (JSONWriter output = JSONWriter.of(writerContext)) {
            Fastjson2Generator writer = new Fastjson2Generator(output);
            writer.startWrite();
            StreamingIO.writeNode(writer, node, context);
            writer.endWrite();
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
            Fastjson2Generator writer = new Fastjson2Generator(output);
            writer.startWrite();
            StreamingIO.writeNode(writer, node, context);
            writer.endWrite();
            return output.getBytes();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node)
                    + "' as UTF-8 JSON with Fastjson2", e);
        }
    }
}
