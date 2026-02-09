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
 * Interface for streaming that provides methods for reading and writing
 * JSON-like data through facade readers and writers. This interface serves as the base for
 * all facade implementations, defining common streaming operations.
 *
 * @param <R> the type of FacadeReader associated with this facade
 * @param <W> the type of FacadeWriter associated with this facade
 */
public interface StreamingFacade<R extends StreamingReader, W extends StreamingWriter> {

    enum StreamingMode {
        SHARED_IO,
        EXCLUSIVE_IO,
        PLUGIN_MODULE
    }

    /// Reader

    /**
     * Creates a new FacadeReader from the provided Reader.
     *
     * @param input the Reader to read from
     * @return a new FacadeReader instance
     * @throws IOException if an I/O error occurs
     */
    R createReader(Reader input) throws IOException;

    /**
     * Creates a new FacadeReader from the provided InputStream, using UTF-8 charset.
     *
     * @param input the InputStream to read from
     * @return a new FacadeReader instance
     * @throws IOException if an I/O error occurs
     */
    default R createReader(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input is null");
        return createReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    /**
     * Creates a new FacadeReader from the provided String.
     *
     * @param input the String to read from
     * @return a new FacadeReader instance
     * @throws IOException if an I/O error occurs
     */
    default R createReader(String input) throws IOException {
        Objects.requireNonNull(input, "input is null");
        return createReader(new StringReader(input));
    }

    /**
     * Creates a new FacadeReader from the provided byte array, using UTF-8 charset.
     *
     * @param input the byte array to read from
     * @return a new FacadeReader instance
     * @throws IOException if an I/O error occurs
     */
    default R createReader(byte[] input) throws IOException {
        Objects.requireNonNull(input, "input is null");
        return createReader(new ByteArrayInputStream(input));
    }

    /**
     * Reads a JSON node of the specified type from the provided Reader.
     *
     * @param input the Reader to read from
     * @param type the target type of the node
     * @return the read JSON node
     * @throws IllegalArgumentException if input is null
     * @throws JsonException if reading fails
     */
    default Object readNode(Reader input, Type type) {
        Objects.requireNonNull(input, "input is null");
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
     * Reads a JSON node of the specified type from the provided InputStream.
     *
     * @param input the InputStream to read from
     * @param type the target type of the node
     * @return the read JSON node
     * @throws IllegalArgumentException if input is null
     * @throws JsonException if reading fails
     */
    default Object readNode(InputStream input, Type type) {
        Objects.requireNonNull(input, "input is null");
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
     * Reads a JSON node of the specified type from the provided String.
     *
     * @param input the String to read from
     * @param type the target type of the node
     * @return the read JSON node
     * @throws IllegalArgumentException if input is null
     * @throws JsonException if reading fails
     */
    default Object readNode(String input, Type type) {
        Objects.requireNonNull(input, "input is null");
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

    default Object readNode(byte[] input, Type type) {
        Objects.requireNonNull(input, "input is null");
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


    /// Writer

    /**
     * Creates a new FacadeWriter from the provided Writer.
     *
     * @param output the Writer to write to
     * @return a new FacadeWriter instance
     * @throws IOException if an I/O error occurs
     */
    W createWriter(Writer output) throws IOException;

    /**
     * Creates a new FacadeWriter from the provided OutputStream, using UTF-8 charset.
     *
     * @param output the OutputStream to write to
     * @return a new FacadeWriter instance
     * @throws IOException if an I/O error occurs
     */
    default W createWriter(OutputStream output) throws IOException {
        return createWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    }


    default void writeNode(Writer output, Object node) {
        Objects.requireNonNull(output, "output is null");
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

    default void writeNode(OutputStream output, Object node) {
        Objects.requireNonNull(output, "output is null");
        writeNode(new OutputStreamWriter(output, StandardCharsets.UTF_8), node);
    }

    default String writeNodeAsString(Object node) {
        StringWriter output = new StringWriter();
        writeNode(output, node);
        return output.toString();
    }

    default byte[] writeNodeAsBytes(Object node) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeNode(output, node);
        return output.toByteArray();
    }


}
