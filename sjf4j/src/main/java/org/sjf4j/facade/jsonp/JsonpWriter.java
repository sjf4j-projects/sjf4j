package org.sjf4j.facade.jsonp;

import jakarta.json.stream.JsonGenerator;
import org.sjf4j.facade.StreamingWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/** Streaming writer backed by JSON-P {@link JsonGenerator}. */
public class JsonpWriter implements StreamingWriter {

    private final JsonGenerator gen;

    /** Creates writer adapter from JSON-P generator. */
    public JsonpWriter(JsonGenerator gen) {
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
        gen.writeEnd();
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
        gen.writeEnd();
    }

    /**
     * Writes object field name.
     */
    @Override
    public void writeName(String name) throws IOException {
        gen.writeKey(name);
    }

    /**
     * Writes string value.
     */
    @Override
    public void writeString(String value) throws IOException {
        gen.write(value);
    }

    /**
     * Writes numeric value using Jackson numeric APIs.
     */
    @Override
    public void writeNumber(Number value) throws IOException {
        if (value instanceof Long || value instanceof Integer) {
            gen.write(value.longValue());
        } else if (value instanceof Float || value instanceof Double) {
            gen.write(value.doubleValue());
        } else if (value instanceof BigInteger) {
            gen.write(((BigInteger) value));
        } else if (value instanceof BigDecimal) {
            gen.write(((BigDecimal) value));
        } else {
            gen.write(value.longValue());
        }
    }

    /**
     * Writes boolean value.
     */
    @Override
    public void writeBoolean(Boolean value) throws IOException {
        gen.write(value);
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
