package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonGenerator;
import org.sjf4j.backend.jackson2.binding.Jackson2PreparedName;
import org.sjf4j.binding.PreparedName;
import org.sjf4j.binding.StreamingBinder;
import org.sjf4j.binding.StreamingWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** StreamingWriter backed directly by a Jackson 2 {@link JsonGenerator}. */
public final class Jackson2Writer extends StreamingWriter {

    private final JsonGenerator generator;

    public Jackson2Writer(StreamingBinder<?, ?> binder, JsonGenerator generator) {
        super(binder);
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    @Override
    public void startObject() throws IOException {
        generator.writeStartObject();
    }

    @Override
    public void endObject() throws IOException {
        generator.writeEndObject();
    }

    @Override
    public void startArray() throws IOException {
        generator.writeStartArray();
    }

    @Override
    public void endArray() throws IOException {
        generator.writeEndArray();
    }

    @Override
    public void writeName(String name) throws IOException {
        generator.writeFieldName(Objects.requireNonNull(name, "name"));
    }

    @Override
    public void writeNull() throws IOException {
        generator.writeNull();
    }

    @Override
    public void writeStringValue(String value) throws IOException {
        generator.writeString(Objects.requireNonNull(value, "value"));
    }

    @Override
    public void writeLongValue(long value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void writeIntValue(int value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void writeShortValue(short value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void writeByteValue(byte value) throws IOException {
        generator.writeNumber((short) value);
    }

    @Override
    public void writeDoubleValue(double value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void writeFloatValue(float value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void writeBooleanValue(boolean value) throws IOException {
        generator.writeBoolean(value);
    }

    @Override
    public void writeCharValue(char value) throws IOException {
        generator.writeString(Character.toString(value));
    }

    @Override
    public void writeNumberValue(Number value) throws IOException {
        Objects.requireNonNull(value, "value");
        if (value instanceof Integer) generator.writeNumber(value.intValue());
        else if (value instanceof Long) generator.writeNumber(value.longValue());
        else if (value instanceof Double) generator.writeNumber(value.doubleValue());
        else if (value instanceof Short || value instanceof Byte) generator.writeNumber(value.shortValue());
        else if (value instanceof Float) generator.writeNumber(value.floatValue());
        else if (value instanceof BigInteger) generator.writeNumber((BigInteger) value);
        else if (value instanceof BigDecimal) generator.writeNumber((BigDecimal) value);
        else generator.writeNumber(value.toString());
    }

    @Override
    public void writeBigIntegerValue(BigInteger value) throws IOException {
        generator.writeNumber(Objects.requireNonNull(value, "value"));
    }

    @Override
    public void writeBigDecimalValue(BigDecimal value) throws IOException {
        generator.writeNumber(Objects.requireNonNull(value, "value"));
    }


    @Override
    public void writeName(PreparedName preparedName) throws IOException {
        Jackson2PreparedName jackson2NameWriter = (Jackson2PreparedName) preparedName;
        generator.writeFieldName(jackson2NameWriter.serializedName);
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
