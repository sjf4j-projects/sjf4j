package org.sjf4j.facade;

import org.sjf4j.exception.BindingException;
import org.sjf4j.node.Types;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * JSON facade interface with streaming support and runtime mode dispatch.
 *
 * <p>Implementations expose a concrete {@link StreamingContext.StreamingMode} and can override
 * the plugin-module and exclusive-IO hooks when they provide backend-native
 * read/write paths. The default methods in this interface route reads and writes
 * through the appropriate path and normalize common exception handling.</p>
 */
public interface JsonFacade<R extends StreamingReader, W extends StreamingWriter> extends StreamingFacade<R, W> {

    default StreamingContext.StreamingMode realStreamingMode() {
        return StreamingContext.StreamingMode.SHARED_IO;
    }

    @Override
    default Object readNode(Reader input, Type type) {
        Objects.requireNonNull(input, "input");
        StreamingContext.StreamingMode mode = realStreamingMode();
        switch (mode) {
            case SHARED_IO:
                return StreamingFacade.super.readNode(input, type);
            case EXCLUSIVE_IO:
                return readNodeExclusive(input, type);
            case PLUGIN_MODULE:
                return readNodePlugin(input, type);
            default:
                throw unsupportedMode(mode);
        }
    }

    @Override
    default Object readNode(InputStream input, Type type) {
        Objects.requireNonNull(input, "input");
        StreamingContext.StreamingMode mode = realStreamingMode();
        switch (mode) {
            case SHARED_IO:
                return StreamingFacade.super.readNode(input, type);
            case EXCLUSIVE_IO:
                return readNodeExclusive(input, type);
            case PLUGIN_MODULE:
                return readNodePlugin(input, type);
            default:
                throw unsupportedMode(mode);
        }
    }

    @Override
    default Object readNode(String input, Type type) {
        Objects.requireNonNull(input, "input");
        StreamingContext.StreamingMode mode = realStreamingMode();
        switch (mode) {
            case SHARED_IO:
                return StreamingFacade.super.readNode(input, type);
            case EXCLUSIVE_IO:
                return readNodeExclusive(input, type);
            case PLUGIN_MODULE:
                return readNodePlugin(input, type);
            default:
                throw unsupportedMode(mode);
        }
    }

    @Override
    default Object readNode(byte[] input, Type type) {
        Objects.requireNonNull(input, "input");
        StreamingContext.StreamingMode mode = realStreamingMode();
        switch (mode) {
            case SHARED_IO:
                return StreamingFacade.super.readNode(input, type);
            case EXCLUSIVE_IO:
                return readNodeExclusive(input, type);
            case PLUGIN_MODULE:
                return readNodePlugin(input, type);
            default:
                throw unsupportedMode(mode);
        }
    }

    default Object readNodeExclusive(Reader input, Type type) {
        throw unsupportedMode(StreamingContext.StreamingMode.EXCLUSIVE_IO);
    }

    default Object readNodeExclusive(InputStream input, Type type) {
        return readNodeExclusive(new InputStreamReader(input, StandardCharsets.UTF_8), type);
    }

    default Object readNodeExclusive(String input, Type type) {
        return readNodeExclusive(new StringReader(input), type);
    }

    default Object readNodeExclusive(byte[] input, Type type) {
        return readNodeExclusive(new ByteArrayInputStream(input), type);
    }

    default Object readNodePlugin(Reader input, Type type) {
        throw unsupportedMode(StreamingContext.StreamingMode.PLUGIN_MODULE);
    }

    default Object readNodePlugin(InputStream input, Type type) {
        return readNodePlugin(new InputStreamReader(input, StandardCharsets.UTF_8), type);
    }

    default Object readNodePlugin(String input, Type type) {
        return readNodePlugin(new StringReader(input), type);
    }

    default Object readNodePlugin(byte[] input, Type type) {
        return readNodePlugin(new ByteArrayInputStream(input), type);
    }

    @Override
    default void writeNode(Writer output, Object node) {
        Objects.requireNonNull(output, "output");
        StreamingContext.StreamingMode mode = realStreamingMode();
        switch (mode) {
            case SHARED_IO:
                StreamingFacade.super.writeNode(output, node);
                return;
            case EXCLUSIVE_IO:
                writeNodeExclusive(output, node);
                return;
            case PLUGIN_MODULE:
                writeNodePlugin(output, node);
                return;
            default:
                throw unsupportedMode(mode);
        }
    }

    @Override
    default void writeNode(OutputStream output, Object node) {
        Objects.requireNonNull(output, "output");
        StreamingContext.StreamingMode mode = realStreamingMode();
        switch (mode) {
            case SHARED_IO:
                StreamingFacade.super.writeNode(output, node);
                return;
            case EXCLUSIVE_IO:
                writeNodeExclusive(output, node);
                return;
            case PLUGIN_MODULE:
                writeNodePlugin(output, node);
                return;
            default:
                throw unsupportedMode(mode);
        }
    }

    @Override
    default String writeNodeAsString(Object node) {
        StreamingContext.StreamingMode mode = realStreamingMode();
        switch (mode) {
            case SHARED_IO:
                return StreamingFacade.super.writeNodeAsString(node);
            case EXCLUSIVE_IO:
                return writeNodeAsStringExclusive(node);
            case PLUGIN_MODULE:
                return writeNodeAsStringPlugin(node);
            default:
                throw unsupportedMode(mode);
        }
    }

    @Override
    default byte[] writeNodeAsBytes(Object node) {
        StreamingContext.StreamingMode mode = realStreamingMode();
        switch (mode) {
            case SHARED_IO:
                return StreamingFacade.super.writeNodeAsBytes(node);
            case EXCLUSIVE_IO:
                return writeNodeAsBytesExclusive(node);
            case PLUGIN_MODULE:
                return writeNodeAsBytesPlugin(node);
            default:
                throw unsupportedMode(mode);
        }
    }

    default void writeNodeExclusive(Writer output, Object node) {
        throw unsupportedMode(StreamingContext.StreamingMode.EXCLUSIVE_IO);
    }

    default void writeNodeExclusive(OutputStream output, Object node) {
        OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writeNodeExclusive(writer, node);
        try {
            writer.flush();
        } catch (Exception e) {
            throw new BindingException(e);
        }
    }

    default String writeNodeAsStringExclusive(Object node) {
        try (StringWriter output = new StringWriter()) {
            writeNodeExclusive(output, node);
            return output.toString();
        } catch (Exception e) {
            throw new BindingException(e);
        }
    }

    default byte[] writeNodeAsBytesExclusive(Object node) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeNodeExclusive(output, node);
            return output.toByteArray();
        } catch (Exception e) {
            throw new BindingException(e);
        }
    }

    default void writeNodePlugin(Writer output, Object node) {
        throw unsupportedMode(StreamingContext.StreamingMode.PLUGIN_MODULE);
    }

    default void writeNodePlugin(OutputStream output, Object node) {
        OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writeNodePlugin(writer, node);
        try {
            writer.flush();
        } catch (Exception e) {
            throw new BindingException(e);
        }
    }

    default String writeNodeAsStringPlugin(Object node) {
        try (StringWriter output = new StringWriter()) {
            writeNodePlugin(output, node);
            return output.toString();
        } catch (Exception e) {
            throw new BindingException(e);
        }
    }

    default byte[] writeNodeAsBytesPlugin(Object node) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeNodePlugin(output, node);
            return output.toByteArray();
        } catch (Exception e) {
            throw new BindingException(e);
        }
    }

    default BindingException unsupportedMode(StreamingContext.StreamingMode mode) {
        return new BindingException("Unsupported streaming mode '" + mode + "'");
    }

    default BindingException failedToRead(Type type, Exception e) {
        throw new BindingException("Failed to read JSON into type '" + type + "'", e);
    }

    default BindingException failedToWrite(Object node, Exception e) {
        throw new BindingException("Failed to write node type '" + Types.name(node) + "' into JSON", e);
    }


}
