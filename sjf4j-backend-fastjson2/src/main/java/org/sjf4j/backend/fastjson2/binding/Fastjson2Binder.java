package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.NodeConfig;
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
 * Each binder owns one reader context with a fixed generic number policy.
 * The writer context is reused; SJF4J controls omission of null object members.
 */
public final class Fastjson2Binder extends Binder<Fastjson2Parser, Fastjson2Generator> {

    private final JSONReader.Context readerContext;
    private final long readerFeatures;
    private final JSONWriter.Context writerContext;

    /** Creates a binder with default Fastjson2 contexts. */
    public Fastjson2Binder() {
        this(JSONFactory.createReadContext(), JSONFactory.createWriteContext(), NodeConfig.DEFAULT);
    }

    /**
     * Creates a binder with default backend settings and supplied node configuration.
     * @param config the SJF4J node configuration
     */
    public Fastjson2Binder(NodeConfig config) {
        this(JSONFactory.createReadContext(), JSONFactory.createWriteContext(), config);
    }

    /**
     * Creates a binder with caller-supplied reader and writer contexts.
     * @param readerContext the Fastjson2 read context, copied for the configured numeric policy
     * @param writerContext the Fastjson2 write context
     */
    public Fastjson2Binder(JSONReader.Context readerContext, JSONWriter.Context writerContext) {
        this(readerContext, writerContext, NodeConfig.DEFAULT);
    }

    /**
     * Creates a binder with caller-supplied contexts and runtime settings.
     * @param readerContext the Fastjson2 read context, copied for the configured numeric policy
     * @param writerContext the Fastjson2 write context
     * @param config the SJF4J node configuration
     */
    public Fastjson2Binder(JSONReader.Context readerContext, JSONWriter.Context writerContext,
                           NodeConfig config) {
        this(readerContext, writerContext, config, Asserts.notNull(readerContext, "readerContext").getFeatures());
    }

    private Fastjson2Binder(JSONReader.Context readerContext, JSONWriter.Context writerContext,
                            NodeConfig config, long readerFeatures) {
        super(config);
        this.writerContext = Asserts.notNull(writerContext, "writerContext");
        // Never toggle features on a context shared by active readers/binders.
        this.readerContext = _copyReadContext(readerContext);
        this.readerFeatures = readerFeatures;
        this.readerContext.setFeatures(readerFeatures);
        this.readerContext.config(JSONReader.Feature.UseDoubleForDecimals, !config.exactNumbers);
        if (config.exactNumbers) {
            // Fastjson2 uses this feature for exponent literals as well.
            this.readerContext.config(JSONReader.Feature.UseBigDecimalForDoubles);
            this.readerContext.config(JSONReader.Feature.UseLongForInts, false);
        }
    }


    private static JSONReader.Context _copyReadContext(JSONReader.Context source) {
        JSONReader.Context copy = new JSONReader.Context(source.getProvider(), source.getFeatures());
        copy.setMaxLevel(source.getMaxLevel());
        copy.setBufferSize(source.getBufferSize());
        copy.setZoneId(source.getZoneId());
        copy.setLocale(source.getLocale());
        copy.setTimeZone(source.getTimeZone());
        copy.setDateFormat(source.getDateFormat());
        copy.setDateFormatter(source.getDateFormatter());
        copy.setObjectSupplier(source.getObjectSupplier());
        copy.setArraySupplier(source.getArraySupplier());
        copy.config(source.getContextAutoTypeBeforeHandler());
        copy.setExtraProcessor(source.getExtraProcessor());
        return copy;
    }

    /*
     * --------------------------------------------------------------
     * Read
     * --------------------------------------------------------------
     */

    /** Returns a binder with the requested numeric policy, retaining native settings. */
    public Fastjson2Binder withExactNumbers(boolean exactNumbers) {
        return config.exactNumbers == exactNumbers ? this
                : new Fastjson2Binder(readerContext, writerContext, numberConfig(exactNumbers), readerFeatures);
    }

    /**
     * Creates a streaming reader that wraps the supplied Fastjson2 reader.
     * In exact mode the supplied reader must already have an exact numeric
     * context; this method does not mutate caller-owned reader settings.
     * @param reader the Fastjson2 reader
     * @return a SJF4J streaming reader
     */
    public Fastjson2Parser createParser(JSONReader reader) {
        Asserts.notNull(reader, "reader");
        if (config.exactNumbers) {
            JSONReader.Context context = reader.getContext();
            if (context.isEnabled(JSONReader.Feature.UseDoubleForDecimals)
                    || context.isEnabled(JSONReader.Feature.UseLongForInts)
                    || !(context.isEnabled(JSONReader.Feature.UseBigDecimalForDoubles)
                    || context.isEnabled(JSONReader.Feature.UseBigDecimalForFloats))) {
                throw new BindingException("native Fastjson2 reader context does not support exactNumbers");
            }
        }
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
     * Creates an in-memory writer using this binder's writer context.
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
            Fastjson2Generator generator = new Fastjson2Generator(output);
            generator.start();
            StreamingIO.writeNode(generator, node, config);
            generator.end();
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
            Fastjson2Generator generator = new Fastjson2Generator(output);
            generator.start();
            StreamingIO.writeNode(generator, node, config);
            generator.end();
            return output.getBytes();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node)
                    + "' as UTF-8 JSON with Fastjson2", e);
        }
    }
}
