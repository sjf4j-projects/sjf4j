package org.sjf4j.facade;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * Streaming writer abstraction for structured outputs.
 */
public interface StreamingWriter extends Closeable {

    /**
     * Marks the start of a document.
     *
     * @throws IOException if an I/O error occurs
     */
    default void startDocument() throws IOException {}

    /**
     * Marks the end of a document.
     *
     * @throws IOException if an I/O error occurs
     */
    default void endDocument() throws IOException {}

    /**
     * Writes the start of an object-like structure (for JSON, '{').
     *
     * @throws IOException if an I/O error occurs
     */
    void startObject() throws IOException;

    /**
     * Writes the end of an object-like structure (for JSON, '}').
     *
     * @throws IOException if an I/O error occurs
     */
    void endObject() throws IOException;

    /**
     * Writes the start of an array-like structure (for JSON, '[').
     *
     * @throws IOException if an I/O error occurs
     */
    void startArray() throws IOException;

    /**
     * Writes the end of an array-like structure (for JSON, ']').
     *
     * @throws IOException if an I/O error occurs
     */
    void endArray() throws IOException;

    /**
     * Writes an object member name.
     *
     * @param name the field name to write
     * @throws IOException if an I/O error occurs
     */
    void writeName(String name) throws IOException;

    /**
     * Writes a string value.
     *
     * @param value the string value to write
     * @throws IOException if an I/O error occurs
     */
    void writeString(String value) throws IOException;

    /**
     * Writes a number value.
     *
     * @param value the number value to write
     * @throws IOException if an I/O error occurs
     */
    void writeNumber(Number value) throws IOException;

    /**
     * Writes a boolean value.
     *
     * @param value the boolean value to write
     * @throws IOException if an I/O error occurs
     */
    void writeBoolean(Boolean value) throws IOException;

    /**
     * Writes a null value.
     *
     * @throws IOException if an I/O error occurs
     */
    void writeNull() throws IOException;


    default void writeArrayComma() throws IOException {
        /* Only or Fastjson2 */
    }

    default void writeObjectComma() throws IOException {}

    /**
     * Flushes any buffered output.
     *
     * @throws IOException if an I/O error occurs
     */
    void flush() throws IOException;

    /**
     * Transfers buffered output to {@code output}.
     *
     * <p>For implementations whose native writer is not backed by a
     * {@link Writer} or {@link OutputStream}. The default implementation does nothing.</p>
     */
    default void flushTo(Writer output) throws IOException {}

    /**
     * Transfers buffered output to {@code output}.
     *
     * <p>For implementations whose native writer is not backed by a
     * {@link Writer} or {@link OutputStream}. The default implementation does nothing.</p>
     */
    default void flushTo(OutputStream output) throws IOException {}
}
