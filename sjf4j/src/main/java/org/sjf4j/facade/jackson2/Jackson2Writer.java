package org.sjf4j.facade.jackson2;

import com.fasterxml.jackson.core.JsonGenerator;
import org.sjf4j.facade.StreamingWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Streaming writer backed by Jackson2's {@link JsonGenerator}.
 */
public class Jackson2Writer implements StreamingWriter {

    private final JsonGenerator gen;

    /**
     * Creates writer adapter from Jackson2 JsonGenerator.
     */
    public Jackson2Writer(JsonGenerator gen) {
        this.gen = gen;
    }

    /**
     * Starts object scope.
     */
    @Override
    public void startObject() throws IOException {
        gen.writeStartObject();
    }

    /**
     * Ends object scope.
     */
    @Override
    public void endObject() throws IOException {
        gen.writeEndObject();
    }

    /**
     * Starts array scope.
     */
    @Override
    public void startArray() throws IOException {
        gen.writeStartArray();
    }

    /**
     * Ends array scope.
     */
    @Override
    public void endArray() throws IOException {
        gen.writeEndArray();
    }

    /**
     * Writes object field name.
     */
    @Override
    public void writeName(String name) throws IOException {
        gen.writeFieldName(name);
    }

    /**
     * Writes string value.
     */
    @Override
    public void writeString(String value) throws IOException {
        gen.writeString(value);
    }

    /**
     * Writes numeric value using Jackson2 numeric APIs.
     */
    @Override
    public void writeNumber(Number value) throws IOException {
        if (value instanceof Long || value instanceof Integer) {
            gen.writeNumber(value.longValue());
        } else if (value instanceof Float || value instanceof Double) {
            gen.writeNumber(value.doubleValue());
        } else if (value instanceof BigInteger) {
            gen.writeNumber(((BigInteger) value));
        } else if (value instanceof BigDecimal) {
            gen.writeNumber(((BigDecimal) value));
        } else {
            gen.writeNumber(value.longValue());
        }
    }

    /**
     * Writes boolean value.
     */
    @Override
    public void writeBoolean(Boolean value) throws IOException {
        gen.writeBoolean(value);
    }

    /**
     * Writes null value.
     */
    @Override
    public void writeNull() throws IOException {
        gen.writeNull();
    }

    /**
     * Flushes generator output.
     */
    @Override
    public void flush() throws IOException {
        gen.flush();
    }

    /**
     * Closes underlying generator.
     */
    @Override
    public void close() throws IOException {
        gen.close();
    }

}
