package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.facade.StreamingWriter;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Streaming writer backed by Fastjson2's {@link JSONWriter}.
 */
public class Fastjson2Writer implements StreamingWriter {

    private final JSONWriter writer;

    /**
     * Creates writer adapter from Fastjson2 JSONWriter.
     */
    public Fastjson2Writer(JSONWriter writer) {
        this.writer = writer;
    }

    /**
     * Starts object scope.
     */
    @Override
    public void startObject() throws IOException {
        writer.startObject();
    }

    /**
     * Ends object scope.
     */
    @Override
    public void endObject() throws IOException {
        writer.endObject();
    }

    /**
     * Starts array scope.
     */
    @Override
    public void startArray() throws IOException {
        writer.startArray();
    }

    /**
     * Ends array scope.
     */
    @Override
    public void endArray() throws IOException {
        writer.endArray();
    }

    /**
     * Writes object field name.
     */
    @Override
    public void writeName(String name) throws IOException {
        writer.writeName(name);
        writer.writeColon();
    }

    /**
     * Writes string value.
     */
    @Override
    public void writeString(String value) throws IOException {
        writer.writeString(value);
    }

    /**
     * Writes numeric value using Fastjson2 numeric APIs.
     */
    @Override
    public void writeNumber(Number value) throws IOException {
        if (value instanceof Long || value instanceof Integer) {
            writer.writeInt64(value.longValue());
        } else if (value instanceof Float || value instanceof Double) {
            writer.writeDouble(value.doubleValue());
        } else if (value instanceof BigInteger) {
            writer.writeBigInt(((BigInteger) value));
        } else if (value instanceof BigDecimal) {
            writer.writeDecimal(((BigDecimal) value));
        } else {
            writer.writeInt64(value.longValue());
        }
    }

    /**
     * Writes boolean value.
     */
    @Override
    public void writeBoolean(Boolean value) throws IOException {
        writer.writeBool(value);
    }

    /**
     * Writes null value.
     */
    @Override
    public void writeNull() throws IOException {
        writer.writeNull();
    }

    /**
     * Writes array item comma.
     */
    @Override
    public void writeArrayComma() {
        writer.writeComma();
    }

    /**
     * Flush is no-op for this adapter.
     */
    @Override
    public void flush() {
        // Nothing
    }

    // Hacking
    /**
     * Flushes Fastjson2 buffer into target writer.
     */
    @Override
    public void flushTo(Writer out) throws IOException {
        writer.flushTo(out);
    }

    /**
     * Closes underlying Fastjson2 writer.
     */
    @Override
    public void close() throws IOException {
        writer.close();
    }
}
