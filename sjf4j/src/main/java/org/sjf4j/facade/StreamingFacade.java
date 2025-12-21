package org.sjf4j.facade;

import org.sjf4j.JsonException;
import org.sjf4j.NodeRegistry;
import org.sjf4j.facade.fastjson2.Fastjson2Writer;
import org.sjf4j.util.StreamingUtil;
import org.sjf4j.util.TypeUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * Interface for streaming that provides methods for reading and writing
 * JSON-like data through facade readers and writers. This interface serves as the base for
 * all facade implementations, defining common streaming operations.
 *
 * @param <R> the type of FacadeReader associated with this facade
 * @param <W> the type of FacadeWriter associated with this facade
 */
public interface StreamingFacade<R extends FacadeReader, W extends FacadeWriter> {

    /// Reader and Writer

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
        return createReader(new ByteArrayInputStream(input));
    }

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

    /// Default read and write

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
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        // Always use try-with-resources here.
        // It enables JVM optimizations (escape analysis, inlining) that significantly improve performance.
        try (FacadeReader reader = createReader(input)) {
            reader.startDocument();
            Object node = StreamingUtil.readNode(reader, type);
            reader.endDocument();
            return node;
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
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
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        // Always use try-with-resources here.
        // It enables JVM optimizations (escape analysis, inlining) that significantly improve performance.
        try (FacadeReader reader = createReader(input)) {
            reader.startDocument();
            Object node = StreamingUtil.readNode(reader, type);
            reader.endDocument();
            return node;
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
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
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        // Always use try-with-resources here.
        // It enables JVM optimizations (escape analysis, inlining) that significantly improve performance.
        try (FacadeReader reader = createReader(input)) {
            reader.startDocument();
            Object node = StreamingUtil.readNode(reader, type);
            reader.endDocument();
            return node;
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
        }
    }

    default Object readNode(byte[] input, Type type) {
        // Always use try-with-resources here.
        // It enables JVM optimizations (escape analysis, inlining) that significantly improve performance.
        try (FacadeReader reader = createReader(input)) {
            reader.startDocument();
            Object node = StreamingUtil.readNode(reader, type);
            reader.endDocument();
            return node;
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
        }
    }


    default void writeNode(Writer output, Object node) {
        if (output == null) throw new IllegalArgumentException("Output must not be null");
        try {
            FacadeWriter writer = createWriter(output);
            writer.startDocument();
            StreamingUtil.writeNode(writer, node);
            writer.endDocument();
            writer.flush();

            if (writer instanceof Fastjson2Writer) {
                ((Fastjson2Writer) writer).flushTo(output);
            }
        } catch (Exception e) {
            throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) + "' to streaming", e);
        }
    }

    default void writeNode(OutputStream output, Object node) {
        if (output == null) throw new IllegalArgumentException("Output must not be null");
        try {
            FacadeWriter writer = createWriter(output);
            writer.startDocument();
            StreamingUtil.writeNode(writer, node);
            writer.endDocument();
            writer.flush();

            if (writer instanceof Fastjson2Writer) {
                ((Fastjson2Writer) writer).flushTo(output);
            }
        } catch (Exception e) {
            throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) + "' to streaming", e);
        }
    }


    /// Convertible

    default void registerConvertible(NodeRegistry.ConvertibleInfo ci) {

    }

}
