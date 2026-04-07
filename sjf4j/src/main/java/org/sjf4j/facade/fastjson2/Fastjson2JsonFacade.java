package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.PropertyNamingStrategy;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.alibaba.fastjson2.writer.ObjectWriterProvider;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.node.NamingStrategy;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fastjson2-based JSON facade with selectable streaming modes.
 */
public class Fastjson2JsonFacade implements JsonFacade<Fastjson2Reader, Fastjson2Writer> {
    private static final AtomicBoolean DEFAULT_MODULES_REGISTERED = new AtomicBoolean();
    private static final Fastjson2Module.MyReaderModule READER_MODULE = new Fastjson2Module.MyReaderModule();
    private static final Fastjson2Module.MyWriterModule WRITER_MODULE = new Fastjson2Module.MyWriterModule();

    private final StreamingMode streamingMode;

//    private final JSONReader.Feature[] readerFeatures;
//    private final JSONWriter.Feature[] writerFeatures;
    private final JSONReader.Context readerContext;
    private final JSONWriter.Context writerContext;

    /**
     * Creates facade with default reader/writer features.
     */
    public Fastjson2JsonFacade() {
        this(new JSONReader.Feature[0], new JSONWriter.Feature[0], null);
    }

    public Fastjson2JsonFacade(StreamingMode streamingMode) {
        this(new JSONReader.Feature[0], new JSONWriter.Feature[0], streamingMode);
    }

    /**
     * Creates facade with custom writer features.
     */
    public Fastjson2JsonFacade(JSONWriter.Feature... writerFeatures) {
        this(new JSONReader.Feature[0], writerFeatures, null);
    }

    /**
     * Creates facade with custom reader features.
     */
    public Fastjson2JsonFacade(JSONReader.Feature... readerFeatures) {
        this(readerFeatures, new JSONWriter.Feature[0], null);
    }

    /**
     * Creates facade with custom reader and writer features.
     */
    public Fastjson2JsonFacade(JSONReader.Feature[] readerFeatures, JSONWriter.Feature[] writerFeatures,
                               StreamingMode streamingMode) {
        Objects.requireNonNull(readerFeatures, "readerFeatures");
        Objects.requireNonNull(writerFeatures, "writerFeatures");
        this.streamingMode = streamingMode == null ? StreamingMode.AUTO : streamingMode;

        this.readerContext = JSONFactory.createReadContext(readerFeatures);
        this.writerContext = JSONFactory.createWriteContext(writerFeatures);

        this.readerContext.config(JSONReader.Feature.UseDoubleForDecimals);
        this.writerContext.config(JSONWriter.Feature.WriteNulls);
        if (Sjf4jConfig.global().plainPojoFieldAccess == Sjf4jConfig.PlainPojoFieldAccess.FIELD_BASED) {
            this.readerContext.config(JSONReader.Feature.FieldBased);
            this.writerContext.config(JSONWriter.Feature.FieldBased);
        }

        // With Module
        if (usesPluginModule()) {
            ObjectReaderProvider readProvider = JSONFactory.getDefaultObjectReaderProvider();
            readProvider.setNamingStrategy(toFastjsonNamingStrategy(Sjf4jConfig.global().namingStrategy));
            ObjectWriterProvider writeProvider = JSONFactory.getDefaultObjectWriterProvider();
            writeProvider.setNamingStrategy(toFastjsonNamingStrategy(Sjf4jConfig.global().namingStrategy));
            ensureDefaultModulesRegistered(readProvider, writeProvider);
        }
    }

    private static PropertyNamingStrategy toFastjsonNamingStrategy(NamingStrategy namingStrategy) {
        if (namingStrategy == NamingStrategy.SNAKE_CASE) {
            return PropertyNamingStrategy.SnakeCase;
        }
        return PropertyNamingStrategy.CamelCase;
    }


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

    /**
     * Reads JSON from reader into target type.
     */
    @Override
    public Object readNode(Reader input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (runtimeMode()) {
            case SHARED_IO: {
                return JsonFacade.super.readNode(input, type);
            }
            case EXCLUSIVE_IO: {
                try {
                    JSONReader reader = JSONReader.of(input, readerContext);
                    return Fastjson2StreamingIO.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            }
            case PLUGIN_MODULE:
            {
                try {
                    JSONReader reader = JSONReader.of(input, readerContext);
                    return reader.read(type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
        }
    }

    /**
     * Reads JSON from input stream into target type.
     */
    @Override
    public Object readNode(InputStream input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (runtimeMode()) {
            case SHARED_IO: {
                return JsonFacade.super.readNode(input, type);
            }
            case EXCLUSIVE_IO: {
                try {
                    JSONReader reader = JSONReader.of(input, StandardCharsets.UTF_8, readerContext);
                    return Fastjson2StreamingIO.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            }
            case PLUGIN_MODULE:
            {
                try {
                    JSONReader reader = JSONReader.of(input, StandardCharsets.UTF_8, readerContext);
                    return reader.read(type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
        }
    }

    /**
     * Reads JSON from string into target type.
     */
    @Override
    public Object readNode(String input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (runtimeMode()) {
            case SHARED_IO: {
                return JsonFacade.super.readNode(input, type);
            }
            case EXCLUSIVE_IO: {
                try (JSONReader reader = JSONReader.of(input, readerContext)) {
                    return Fastjson2StreamingIO.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON string into node type '" + type + "'", e);
                }
            }
            case PLUGIN_MODULE:
            {
                try (JSONReader reader = JSONReader.of(input, readerContext)) {
                    return reader.read(type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON string into node type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
        }
    }

    /**
     * Reads JSON from bytes into target type.
     */
    @Override
    public Object readNode(byte[] input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (runtimeMode()) {
            case SHARED_IO: {
                return JsonFacade.super.readNode(input, type);
            }
            case EXCLUSIVE_IO: {
                try (JSONReader reader = JSONReader.of(input, readerContext)) {
                    return Fastjson2StreamingIO.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON bytes into node type '" + type + "'", e);
                }
            }
            case PLUGIN_MODULE:
            {
                try (JSONReader reader = JSONReader.of(input, readerContext)) {
                    return reader.read(type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON bytes into node type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
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
        return new Fastjson2Writer(writer);     // Fake writer
    }

    /**
     * Creates a streaming writer to OutputStream.
     */
    @Override
    public Fastjson2Writer createWriter(OutputStream output) {
        Objects.requireNonNull(output, "output");
        JSONWriter writer = JSONWriter.ofUTF8(writerContext);
        return new Fastjson2Writer(writer);     // Fake writer
    }


    /**
     * Writes node as JSON to writer.
     */
    @Override
    public void writeNode(Writer output, Object node) {
        Objects.requireNonNull(output, "output");
        switch (runtimeMode()) {
            case SHARED_IO: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case EXCLUSIVE_IO: {
                try (JSONWriter writer = JSONWriter.of(writerContext)) {
                    Fastjson2StreamingIO.writeNode(writer, node);
                    writer.flushTo(output);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type " + Types.name(node) + " to JSON streaming", e);
                }
                break;
            }
            case PLUGIN_MODULE:
            {
                try (JSONWriter writer = JSONWriter.of(writerContext)) {
                    writer.writeAny(node);
                    writer.flushTo(output);
                } catch (Exception e) {
                    throw new JsonException("Failed to write node of type '" + Types.name(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }


    /**
     * Writes node as JSON to output stream.
     */
    @Override
    public void writeNode(OutputStream output, Object node) {
        Objects.requireNonNull(output, "output");
        switch (runtimeMode()) {
            case SHARED_IO: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case EXCLUSIVE_IO: {
                try (JSONWriter writer = JSONWriter.ofUTF8(writerContext)) {
                    Fastjson2StreamingIO.writeNode(writer, node);
                    writer.flushTo(output);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type " + Types.name(node) + " to JSON streaming", e);
                }
                break;
            }
            case PLUGIN_MODULE:
            {
                try {
                    JSON.writeTo(output, node, writerContext);
                } catch (Exception e) {
                    throw new JsonException("Failed to write node of type '" + Types.name(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }

    /**
     * Serializes node as JSON string.
     */
    @Override
    public String writeNodeAsString(Object node) {
        switch (runtimeMode()) {
            case SHARED_IO: {
                return JsonFacade.super.writeNodeAsString(node);
            }
            case EXCLUSIVE_IO: {
                try (JSONWriter writer = JSONWriter.of(writerContext)) {
                    Fastjson2StreamingIO.writeNode(writer, node);
                    return writer.toString();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type " + Types.name(node) + " to JSON string", e);
                }
            }
            case PLUGIN_MODULE:
            {
                try {
                    return JSON.toJSONString(node, writerContext);
                } catch (Exception e) {
                    throw new JsonException("Failed to write node of type '" + Types.name(node) + "' to JSON string", e);
                }
            }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }

    /**
     * Serializes node as JSON bytes.
     */
    @Override
    public byte[] writeNodeAsBytes(Object node) {
        switch (runtimeMode()) {
            case SHARED_IO: {
                return JsonFacade.super.writeNodeAsBytes(node);
            }
            case EXCLUSIVE_IO: {
                try (JSONWriter writer = JSONWriter.ofUTF8(writerContext)) {
                    Fastjson2StreamingIO.writeNode(writer, node);
                    return writer.getBytes();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type " + Types.name(node) + " to JSON bytes", e);
                }
            }
            case PLUGIN_MODULE:
            {
                try {
                    return JSON.toJSONBytes(node, StandardCharsets.UTF_8, writerContext);
                } catch (Exception e) {
                    throw new JsonException("Failed to write node of type '" + Types.name(node) + "' to JSON bytes", e);
                }
            }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }

    private boolean usesPluginModule() {
        return streamingMode == StreamingMode.AUTO || streamingMode == StreamingMode.PLUGIN_MODULE;
    }

    private StreamingMode runtimeMode() {
        return StreamingFacade.resolveRuntimeMode(streamingMode, true, true);
    }

    private static void ensureDefaultModulesRegistered(ObjectReaderProvider readProvider,
                                                       ObjectWriterProvider writeProvider) {
        if (DEFAULT_MODULES_REGISTERED.compareAndSet(false, true)) {
            readProvider.register(READER_MODULE);
            writeProvider.register(WRITER_MODULE);
        }
    }




}
