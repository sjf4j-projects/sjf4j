package org.sjf4j.facade;

import org.sjf4j.exception.JsonException;
import org.sjf4j.node.Types;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
 * Base streaming facade for reading/writing JSON-like data.
 */
public interface StreamingFacade<R extends StreamingReader, W extends StreamingWriter> {

    enum StreamingMode {
        AUTO,
        SHARED_IO,
        EXCLUSIVE_IO,       // Backend-specific streaming implementation.
        PLUGIN_MODULE
    }

    /// Reader

    /**
     * Creates a streaming reader from java.io.Reader.
     */
    R createReader(Reader input) throws IOException;

    /**
     * Creates a streaming reader from InputStream using UTF-8.
     */
    default R createReader(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        return createReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    /**
     * Creates a streaming reader from input string.
     */
    default R createReader(String input) throws IOException {
        Objects.requireNonNull(input, "input");
        return createReader(new StringReader(input));
    }

    /**
     * Creates a streaming reader from UTF-8 bytes.
     */
    default R createReader(byte[] input) throws IOException {
        Objects.requireNonNull(input, "input");
        return createReader(new ByteArrayInputStream(input));
    }

    /**
     * Reads one node from reader into target type.
     */
    default Object readNode(Reader input, Type type) {
        Objects.requireNonNull(input, "input");
        try {
            StreamingReader reader = createReader(input);
            reader.startDocument();
            Object node = StreamingIO.readNode(reader, type);
            reader.endDocument();
            return node;
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of '" + type + "'", e);
        }
    }

    /**
     * Reads one node from input stream into target type.
     */
    default Object readNode(InputStream input, Type type) {
        Objects.requireNonNull(input, "input");
        try {
            StreamingReader reader = createReader(input);
            reader.startDocument();
            Object node = StreamingIO.readNode(reader, type);
            reader.endDocument();
            return node;
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of '" + type + "'", e);
        }
    }

    /**
     * Reads one node from string into target type.
     */
    default Object readNode(String input, Type type) {
        Objects.requireNonNull(input, "input");
        try (StreamingReader reader = createReader(input)) {
            reader.startDocument();
            Object node = StreamingIO.readNode(reader, type);
            reader.endDocument();
            return node;
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of '" + type + "'", e);
        }
    }

    /**
     * Reads one node from bytes into target type.
     */
    default Object readNode(byte[] input, Type type) {
        Objects.requireNonNull(input, "input");
        try (StreamingReader reader = createReader(input)) {
            reader.startDocument();
            Object node = StreamingIO.readNode(reader, type);
            reader.endDocument();
            return node;
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of '" + type + "'", e);
        }
    }


    /// Writer

    /**
     * Creates a streaming writer to java.io.Writer.
     */
    W createWriter(Writer output) throws IOException;

    /**
     * Creates a streaming writer to OutputStream using UTF-8.
     */
    default W createWriter(OutputStream output) throws IOException {
        return createWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    }


    /**
     * Writes one node to writer.
     */
    default void writeNode(Writer output, Object node) {
        Objects.requireNonNull(output, "output");
        try {
            StreamingWriter writer = createWriter(output);
            writer.startDocument();
            StreamingIO.writeNode(writer, node);
            writer.endDocument();
            writer.flush();
            writer.flushTo(output);
        } catch (Exception e) {
            throw new JsonException("Failed to write node type '" + Types.name(node) + "' to streaming", e);
        }
    }

    /**
     * Writes one node to output stream.
     */
    default void writeNode(OutputStream output, Object node) {
        Objects.requireNonNull(output, "output");
        try {
            StreamingWriter writer = createWriter(output);
            writer.startDocument();
            StreamingIO.writeNode(writer, node);
            writer.endDocument();
            writer.flush();
            writer.flushTo(output);
        } catch (Exception e) {
            throw new JsonException("Failed to write node type '" + Types.name(node) + "' to streaming", e);
        }
    }

    /**
     * Serializes one node to string.
     */
    default String writeNodeAsString(Object node) {
        try (StringWriter output = new StringWriter()) {
            writeNode(output, node);
            return output.toString();
        } catch (Exception e) {
            throw new JsonException(e);
        }
    }

    /**
     * Serializes one node to bytes.
     */
    default byte[] writeNodeAsBytes(Object node) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeNode(output, node);
            return output.toByteArray();
        } catch (Exception e) {
            throw new JsonException(e);
        }
    }


}
