package org.sjf4j.backend.jsonp.binding;

import jakarta.json.stream.JsonGenerator;
import org.sjf4j.binding.StreamingBinder;
import org.sjf4j.binding.StreamingWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** Streaming writer backed directly by a JSON-P {@link JsonGenerator}. */
public final class JsonpWriter extends StreamingWriter {

    private final JsonGenerator generator;

    public JsonpWriter(StreamingBinder<?, ?> binder, JsonGenerator generator) {
        super(binder);
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    @Override
    public void startObject() throws IOException {
        generator.writeStartObject();
    }

    @Override
    public void endObject() throws IOException {
        generator.writeEnd();
    }

    @Override
    public void startArray() throws IOException {
        generator.writeStartArray();
    }

    @Override
    public void endArray() throws IOException {
        generator.writeEnd();
    }

    @Override
    public void writeName(String name) throws IOException {
        generator.writeKey(Objects.requireNonNull(name, "name"));
    }

    @Override
    public void writeNull() throws IOException {
        generator.writeNull();
    }

    @Override
    public void writeStringValue(String value) throws IOException {
        generator.write(Objects.requireNonNull(value, "value"));
    }

    @Override
    public void writeLongValue(long value) throws IOException {
        generator.write(value);
    }

    @Override
    public void writeIntValue(int value) throws IOException {
        generator.write(value);
    }

    @Override
    public void writeShortValue(short value) throws IOException {
        generator.write((int) value);
    }

    @Override
    public void writeByteValue(byte value) throws IOException {
        generator.write((int) value);
    }

    @Override
    public void writeDoubleValue(double value) throws IOException {
        generator.write(value);
    }

    @Override
    public void writeFloatValue(float value) throws IOException {
        generator.write((double) value);
    }

    @Override
    public void writeBooleanValue(boolean value) throws IOException {
        generator.write(value);
    }

    @Override
    public void writeCharValue(char value) throws IOException {
        generator.write(String.valueOf(value));
    }

    @Override
    public void writeNumberValue(Number value) throws IOException {
        Objects.requireNonNull(value, "value");
        if (value instanceof Integer) generator.write(value.intValue());
        else if (value instanceof Long) generator.write(value.longValue());
        else if (value instanceof Short || value instanceof Byte) generator.write(value.intValue());
        else if (value instanceof Float || value instanceof Double) generator.write(value.doubleValue());
        else if (value instanceof BigInteger) generator.write((BigInteger) value);
        else if (value instanceof BigDecimal) generator.write((BigDecimal) value);
        else generator.write(new BigDecimal(value.toString()));
    }

    @Override
    public void writeBigIntegerValue(BigInteger value) throws IOException {
        generator.write(Objects.requireNonNull(value, "value"));
    }

    @Override
    public void writeBigDecimalValue(BigDecimal value) throws IOException {
        generator.write(Objects.requireNonNull(value, "value"));
    }

    @Override
    public void flush() throws IOException {
        generator.flush();
    }

    @Override
    public void close() throws IOException {
        generator.close();
    }
}
