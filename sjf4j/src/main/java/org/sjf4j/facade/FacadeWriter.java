package org.sjf4j.facade;

import java.io.IOException;

/**
 * Interface for writing JSON data in a streaming manner. This interface defines methods
 * for generating JSON tokens, constructing JSON structures, and writing values.
 */
public interface FacadeWriter {

    /**
     * Marks the start of a JSON document.
     *
     * @throws IOException if an I/O error occurs
     */
    void startDocument() throws IOException;

    /**
     * Marks the end of a JSON document.
     *
     * @throws IOException if an I/O error occurs
     */
    void endDocument() throws IOException;

    /**
     * Writes the start of a JSON object '{'
     *
     * @throws IOException if an I/O error occurs
     */
    void startObject() throws IOException;

    /**
     * Writes the end of a JSON object '}'
     *
     * @throws IOException if an I/O error occurs
     */
    void endObject() throws IOException;

    /**
     * Writes the start of a JSON array '['
     *
     * @throws IOException if an I/O error occurs
     */
    void startArray() throws IOException;

    /**
     * Writes the end of a JSON array ']'
     *
     * @throws IOException if an I/O error occurs
     */
    void endArray() throws IOException;

    /**
     * Writes a JSON object field name.
     *
     * @param name the field name to write
     * @throws IOException if an I/O error occurs
     */
    void writeName(String name) throws IOException;

    /**
     * Writes a JSON string value.
     *
     * @param value the string value to write
     * @throws IOException if an I/O error occurs
     */
    void writeValue(String value) throws IOException;

    /**
     * Writes a JSON number value.
     *
     * @param value the number value to write
     * @throws IOException if an I/O error occurs
     */
    void writeValue(Number value) throws IOException;

    /**
     * Writes a JSON boolean value.
     *
     * @param value the boolean value to write
     * @throws IOException if an I/O error occurs
     */
    void writeValue(Boolean value) throws IOException;

    /**
     * Writes a JSON null value.
     *
     * @throws IOException if an I/O error occurs
     */
    void writeNull() throws IOException;


    default void writeArrayComma() throws IOException {/* Only or Fastjson2 */}

    default void writeObjectComma() throws IOException {}

    /**
     * Flushes any buffered output.
     *
     * @throws IOException if an I/O error occurs
     */
    void flush() throws IOException;
}