package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.alibaba.fastjson2.writer.ObjectWriterProvider;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.FacadeProvider;
import org.sjf4j.facade.JsonFacade;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Fastjson2-based JSON facade with selectable streaming modes.
 */
public class Fastjson2JsonFacade implements JsonFacade<Fastjson2Reader, Fastjson2Writer> {
    private final JSONReader.Context readerContext;
    private final JSONWriter.Context writerContext;
    private final StreamingContext streamingContext;

    /**
     * Creates facade with default reader/writer features.
     */
    public Fastjson2JsonFacade() {
        this(null, null, StreamingContext.EMPTY);
    }

    /**
     * Creates facade with custom reader and writer features.
     */
    public Fastjson2JsonFacade(JSONReader.Feature[] readerFeatures, JSONWriter.Feature[] writerFeatures) {
        this(readerFeatures, writerFeatures, StreamingContext.EMPTY);
    }

    public Fastjson2JsonFacade(JSONReader.Feature[] readerFeatures, JSONWriter.Feature[] writerFeatures,
                               StreamingContext context) {
        Objects.requireNonNull(context, "context");
        readerFeatures = readerFeatures == null ? new JSONReader.Feature[0] : readerFeatures;
        writerFeatures = writerFeatures == null ? new JSONWriter.Feature[0] : writerFeatures;

        Fastjson2Module.SimpleReaderModule readerModule = new Fastjson2Module.SimpleReaderModule(context);
        Fastjson2Module.SimpleWriterModule writerModule = new Fastjson2Module.SimpleWriterModule(context);
        ObjectReaderProvider readerProvider = new ObjectReaderProvider();
        ObjectWriterProvider writerProvider = new ObjectWriterProvider();
        readerProvider.register(readerModule);
        writerProvider.register(writerModule);
        this.readerContext = JSONFactory.createReadContext(readerProvider, readerFeatures);
        this.writerContext = JSONFactory.createWriteContext(writerProvider, writerFeatures);
        this.readerContext.config(JSONReader.Feature.UseDoubleForDecimals);
        if (context.includeNulls) {
            this.writerContext.config(JSONWriter.Feature.WriteNulls);
        }

        this.streamingContext = context;
    }

    public static FacadeProvider<JsonFacade<?, ?>> provider() {
        return context -> new Fastjson2JsonFacade(new JSONReader.Feature[0], new JSONWriter.Feature[0], context);
    }

    public static FacadeProvider<JsonFacade<?, ?>> provider(JSONReader.Feature[] readerFeatures,
                                                            JSONWriter.Feature[] writerFeatures) {
        return context -> new Fastjson2JsonFacade(readerFeatures, writerFeatures, context);
    }

    @Override
    public StreamingContext streamingContext() {
        return streamingContext;
    }


    @Override
    public StreamingContext.StreamingMode realStreamingMode() {
        if (streamingContext.streamingMode == StreamingContext.StreamingMode.AUTO) {
            // Fastjson2 defaults to module-backed read/write so AUTO matches the highest-fidelity path.
            return StreamingContext.StreamingMode.PLUGIN_MODULE;
        }
        return streamingContext.streamingMode;
    }


    /// Reader

    /**
     * Creates a streaming reader from Reader.
     */
    @Override
    public Fastjson2Reader createReader(Reader input) {
        Objects.requireNonNull(input, "input");
        JSONReader reader = JSONReader.of(input, readerContext);
        return new Fastjson2Reader(reader);
    }

    /**
     * Creates a streaming reader from InputStream.
     */
    @Override
    public Fastjson2Reader createReader(InputStream input) {
        Objects.requireNonNull(input, "input");
        JSONReader reader = JSONReader.of(input, StandardCharsets.UTF_8, readerContext);
        return new Fastjson2Reader(reader);
    }

    /**
     * Creates a streaming reader from JSON string.
     */
    @Override
    public Fastjson2Reader createReader(String input) {
        Objects.requireNonNull(input, "input");
        JSONReader reader = JSONReader.of(input, readerContext);
        return new Fastjson2Reader(reader);
    }

    /**
     * Creates a streaming reader from JSON bytes.
     */
    @Override
    public Fastjson2Reader createReader(byte[] input) {
        Objects.requireNonNull(input, "input");
        JSONReader reader = JSONReader.of(input, readerContext);
        return new Fastjson2Reader(reader);
    }

    // Plugin read
    @Override
    public Object readNodePlugin(Reader input, Type type) {
        try {
            JSONReader reader = JSONReader.of(input, readerContext);
            return reader.read(type);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodePlugin(InputStream input, Type type) {
        try {
            JSONReader reader = JSONReader.of(input, StandardCharsets.UTF_8, readerContext);
            return reader.read(type);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodePlugin(String input, Type type) {
        try (JSONReader reader = JSONReader.of(input, readerContext)) {
            return reader.read(type);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodePlugin(byte[] input, Type type) {
        try (JSONReader reader = JSONReader.of(input, readerContext)) {
            return reader.read(type);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    // Exclusive read
    @Override
    public Object readNodeExclusive(Reader input, Type type) {
        try {
            JSONReader reader = JSONReader.of(input, readerContext);
            return Fastjson2StreamingIO.readNode(reader, type, streamingContext);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodeExclusive(InputStream input, Type type) {
        try {
            JSONReader reader = JSONReader.of(input, StandardCharsets.UTF_8, readerContext);
            return Fastjson2StreamingIO.readNode(reader, type, streamingContext);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodeExclusive(String input, Type type) {
        try (JSONReader reader = JSONReader.of(input, readerContext)) {
            return Fastjson2StreamingIO.readNode(reader, type, streamingContext);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodeExclusive(byte[] input, Type type) {
        try (JSONReader reader = JSONReader.of(input, readerContext)) {
            return Fastjson2StreamingIO.readNode(reader, type, streamingContext);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }


    /// Write

    /**
     * Creates a streaming writer to Writer.
     */
    @Override
    public Fastjson2Writer createWriter(Writer output) {
        Objects.requireNonNull(output, "output");
        JSONWriter writer = JSONWriter.of(writerContext);
        // The adapter writes into Fastjson2's internal buffer; callers flush to the real Writer separately.
        return new Fastjson2Writer(writer);
    }

    /**
     * Creates a streaming writer to OutputStream.
     */
    @Override
    public Fastjson2Writer createWriter(OutputStream output) {
        Objects.requireNonNull(output, "output");
        JSONWriter writer = JSONWriter.ofUTF8(writerContext);
        // The adapter writes into Fastjson2's internal buffer; callers flush to the real OutputStream separately.
        return new Fastjson2Writer(writer);
    }

    // Plugin write
    @Override
    public void writeNodePlugin(Writer output, Object node) {
        try (JSONWriter writer = JSONWriter.of(writerContext)) {
            writer.writeAny(node);
            writer.flushTo(output);
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public void writeNodePlugin(OutputStream output, Object node) {
        try (JSONWriter writer = JSONWriter.ofUTF8(writerContext)) {
            writer.writeAny(node);
            writer.flushTo(output);
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public String writeNodeAsStringPlugin(Object node) {
        try (JSONWriter writer = JSONWriter.of(writerContext)) {
            writer.writeAny(node);
            return writer.toString();
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public byte[] writeNodeAsBytesPlugin(Object node) {
        try (JSONWriter writer = JSONWriter.ofUTF8(writerContext)) {
            writer.writeAny(node);
            return writer.getBytes();
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }

    // Exclusive write
    @Override
    public void writeNodeExclusive(Writer output, Object node) {
        try (JSONWriter writer = JSONWriter.of(writerContext)) {
            Fastjson2StreamingIO.writeNode(writer, node, streamingContext);
            writer.flushTo(output);
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public void writeNodeExclusive(OutputStream output, Object node) {
        try (JSONWriter writer = JSONWriter.ofUTF8(writerContext)) {
            Fastjson2StreamingIO.writeNode(writer, node, streamingContext);
            writer.flushTo(output);
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public String writeNodeAsStringExclusive(Object node) {
        try (JSONWriter writer = JSONWriter.of(writerContext)) {
            Fastjson2StreamingIO.writeNode(writer, node, streamingContext);
            return writer.toString();
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }


    @Override
    public byte[] writeNodeAsBytesExclusive(Object node) {
        try (JSONWriter writer = JSONWriter.ofUTF8(writerContext)) {
            Fastjson2StreamingIO.writeNode(writer, node, streamingContext);
            return writer.getBytes();
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }

}
