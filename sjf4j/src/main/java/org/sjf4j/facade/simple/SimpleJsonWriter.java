package org.sjf4j.facade.simple;

import org.sjf4j.facade.StreamingWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/**
 * Minimal JSON writer for the built-in facade.
 */
public class SimpleJsonWriter implements StreamingWriter {

    private final Writer writer;

    /**
     * Creates writer over output characters.
     */
    public SimpleJsonWriter(Writer output) {
        Objects.requireNonNull(output, "output");

        if (!(output instanceof BufferedWriter)) {
            output = new BufferedWriter(output);
        }
        this.writer = output;
    }

    /**
     * Starts object scope.
     */
    @Override
    public void startObject() throws IOException {
        writer.write('{');
    }

    /**
     * Ends object scope.
     */
    @Override
    public void endObject() throws IOException {
        writer.write('}');
    }

    /**
     * Starts array scope.
     */
    @Override
    public void startArray() throws IOException {
        writer.write('[');
    }

    /**
     * Ends array scope.
     */
    @Override
    public void endArray() throws IOException {
        writer.write(']');
    }

    /**
     * Writes object field name.
     */
    @Override
    public void writeName(String name) throws IOException {
        if (name == null) throw new IOException("Name must not be null");
        nativeWrite(name);
        writer.write(':');
    }

    /**
     * Writes string value.
     */
    @Override
    public void writeString(String value) throws IOException {
        if (value == null) writeNull();
        else nativeWrite(value);
    }

    /**
     * Writes numeric value.
     */
    @Override
    public void writeNumber(Number value) throws IOException {
        if (value == null) writeNull();
        else writer.write(value.toString());
    }

    /**
     * Writes boolean value.
     */
    @Override
    public void writeBoolean(Boolean value) throws IOException {
        if (value == null) writeNull();
        else writer.write(value ? "true" : "false");
    }

    /**
     * Writes null value.
     */
    @Override
    public void writeNull() throws IOException {
        writer.write("null");
    }

    /**
     * Writes array comma separator.
     */
    @Override
    public void writeArrayComma() throws IOException {
        writer.write(',');
    }

    /**
     * Writes object comma separator.
     */
    @Override
    public void writeObjectComma() throws IOException {
        writer.write(',');
    }

    /**
     * Flushes writer output.
     */
    @Override
    public void flush() throws IOException {
        writer.flush();
    }


    /// Private

    private void nativeWrite(String s) throws IOException {
        writer.write('"');
        final int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': writer.write("\\\""); break;
                case '\\': writer.write("\\\\"); break;
                case '\b': writer.write("\\b"); break;
                case '\f': writer.write("\\f"); break;
                case '\n': writer.write("\\n"); break;
                case '\r': writer.write("\\r"); break;
                case '\t': writer.write("\\t"); break;
                default:
                    if (c < 0x20) {
                        // Control characters must be escaped as \\uXXXX
                        writer.write(String.format("\\u%04X", (int) c));
                    } else {
                        writer.write(c);
                    }
            }
        }
        writer.write('"');
    }


    /**
     * Closes underlying writer.
     */
    @Override
    public void close() throws IOException {
        writer.close();
    }
}
