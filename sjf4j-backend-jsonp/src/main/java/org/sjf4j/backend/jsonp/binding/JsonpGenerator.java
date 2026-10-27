package org.sjf4j.backend.jsonp.binding;

import jakarta.json.stream.JsonGenerator;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.binding.StreamingGenerator;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/** Streaming writer backed directly by a JSON-P {@link JsonGenerator}. */
public final class JsonpGenerator extends StreamingGenerator {

    private final JsonGenerator generator;

    /**
     * Creates a writer around a JSON-P generator.
     * @param generator the JSON-P generator
     */
    public JsonpGenerator(JsonGenerator generator) {
        super(Backend.JSONP);
        this.generator = Asserts.notNull(generator, "generator");
    }

    /**
     * Native JSON-P writer for compiled binding hot paths.
     * @return the native generator
     */
    public JsonGenerator nativeWriter() { return generator; }

    @Override
    public void writeObjectStart() throws IOException {
        generator.writeStartObject();
    }

    @Override
    public void writeObjectEnd() throws IOException {
        generator.writeEnd();
    }

    @Override
    public void writeArrayStart() throws IOException {
        generator.writeStartArray();
    }

    @Override
    public void writeArrayEnd() throws IOException {
        generator.writeEnd();
    }

    @Override
    public void writeName(String name) throws IOException {
        generator.writeKey(Asserts.notNull(name, "name"));
    }

    @Override
    public void writeNull() throws IOException {
        generator.writeNull();
    }

    @Override
    public void writeStringValue(String value) throws IOException {
        generator.write(Asserts.notNull(value, "value"));
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
        Asserts.notNull(value, "value");
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
        generator.write(Asserts.notNull(value, "value"));
    }

    @Override
    public void writeBigDecimalValue(BigDecimal value) throws IOException {
        generator.write(Asserts.notNull(value, "value"));
    }

    @Override
    public void end() throws IOException {
        generator.flush();
    }

    @Override
    public void release() throws IOException {
        generator.close();
    }
}
