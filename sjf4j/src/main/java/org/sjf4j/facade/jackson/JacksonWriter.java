package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import org.sjf4j.facade.StreamingWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public class JacksonWriter implements StreamingWriter {

    private final JsonGenerator gen;

    public JacksonWriter(JsonGenerator gen) {
        this.gen = gen;
    }

    @Override
    public void startObject() throws IOException {
        gen.writeStartObject();
    }

    @Override
    public void endObject() throws IOException {
        gen.writeEndObject();
    }

    @Override
    public void startArray() throws IOException {
        gen.writeStartArray();
    }

    @Override
    public void endArray() throws IOException {
        gen.writeEndArray();
    }

    @Override
    public void writeName(String name) throws IOException {
        gen.writeFieldName(name);
    }

    @Override
    public void writeString(String value) throws IOException {
        gen.writeString(value);
    }

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

    @Override
    public void writeBoolean(Boolean value) throws IOException {
        gen.writeBoolean(value);
    }

    @Override
    public void writeNull() throws IOException {
        gen.writeNull();
    }

    @Override
    public void flush() throws IOException {
        gen.flush();
    }

    @Override
    public void close() throws IOException {
        gen.close();
    }

}
