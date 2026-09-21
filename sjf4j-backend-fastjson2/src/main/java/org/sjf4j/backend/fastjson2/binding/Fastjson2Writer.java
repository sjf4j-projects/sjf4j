package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.binding.StreamingBinder;
import org.sjf4j.binding.StreamingWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** StreamingWriter backed directly by a Fastjson2 {@link JSONWriter}. */
public final class Fastjson2Writer extends StreamingWriter {

    private final JSONWriter writer;

    public Fastjson2Writer(StreamingBinder<?, ?> binder, JSONWriter writer) {
        super(binder);
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public void startObject() {
        writer.startObject();
    }

    @Override
    public void endObject() {
        writer.endObject();
    }

    @Override
    public void startArray() {
        writer.startArray();
    }

    @Override
    public void endArray() {
        writer.endArray();
    }

    // JSONWriter.writeName inserts the object-property separator itself.

    @Override
    public void separateProperty() {
        writer.writeComma();
    }

    @Override
    public void separateElement() {
        writer.writeComma();
    }

    @Override
    public void writeName(String name) {
        writer.writeName(Objects.requireNonNull(name, "name"));
        writer.writeColon();
    }

    @Override
    public void writeNull() {
        writer.writeNull();
    }

    @Override
    public void writeStringValue(String value) {
        writer.writeString(Objects.requireNonNull(value, "value"));
    }

    @Override
    public void writeLongValue(long value) {
        writer.writeInt64(value);
    }

    @Override
    public void writeIntValue(int value) {
        writer.writeInt32(value);
    }

    @Override
    public void writeShortValue(short value) {
        writer.writeInt16(value);
    }

    @Override
    public void writeByteValue(byte value) {
        writer.writeInt8(value);
    }

    @Override
    public void writeDoubleValue(double value) {
        writer.writeDouble(value);
    }

    @Override
    public void writeFloatValue(float value) {
        writer.writeFloat(value);
    }

    @Override
    public void writeBooleanValue(boolean value) {
        writer.writeBool(value);
    }

    @Override
    public void writeCharValue(char value) {
        writer.writeString(Character.toString(value));
    }

    @Override
    public void writeNumberValue(Number value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof Integer) {
            writer.writeInt32(value.intValue());
        } else if (value instanceof Long) {
            writer.writeInt64(value.longValue());
        } else if (value instanceof Double) {
            writer.writeDouble(value.doubleValue());
        } else if (value instanceof Float) {
            writer.writeFloat(value.floatValue());
        } else if (value instanceof Short) {
            writer.writeInt16(value.shortValue());
        } else if (value instanceof Byte) {
            writer.writeInt8(value.byteValue());
        } else if (value instanceof BigDecimal) {
            writer.writeDecimal((BigDecimal) value);
        } else if (value instanceof BigInteger) {
            writer.writeBigInt((BigInteger) value);
        } else {
            writer.writeRaw(value.toString());
        }
    }

    @Override
    public void writeBigIntegerValue(BigInteger value) {
        writer.writeBigInt(Objects.requireNonNull(value, "value"));
    }

    @Override
    public void writeBigDecimalValue(BigDecimal value) {
        writer.writeDecimal(Objects.requireNonNull(value, "value"));
    }

    @Override
    public void flush() throws IOException {}

    @Override
    public void close() throws IOException {
        writer.close();
    }

    @Override
    public void flushTo(Writer output) throws IOException {
        writer.flushTo(output);
    }

    @Override
    public void flushTo(OutputStream output) throws IOException {
        writer.flushTo(output);
    }

}
