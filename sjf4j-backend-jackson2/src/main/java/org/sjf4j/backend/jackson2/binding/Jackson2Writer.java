package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.binding.CompiledName;
import org.sjf4j.binding.StreamingWriter;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Jackson 2 adapter for the SJF4J streaming writer protocol.
 *
 * <p>Jackson owns object/array separators. The separated name overloads therefore
 * have exactly the same behavior as their non-separated counterparts.</p>
 */
public final class Jackson2Writer extends StreamingWriter {

    private final JsonGenerator generator;

    /** Access for backend-specific compiled POJO property writers. */
    public JsonGenerator nativeWriter() { return generator; }
    private final SegmentedStringWriter stringOutput;
    private final ByteArrayBuilder byteOutput;
    private final BufferRecycler recycler;

    /**
     * Creates a writer around an existing Jackson 2 generator.
     * @param generator the Jackson 2 generator
     */
    public Jackson2Writer(JsonGenerator generator) {
        this(generator, null, null, null);
    }

    Jackson2Writer(JsonGenerator generator,
                   SegmentedStringWriter output, BufferRecycler recycler) {
        this(generator, output, null, recycler);
    }

    Jackson2Writer(JsonGenerator generator,
                   ByteArrayBuilder output, BufferRecycler recycler) {
        this(generator, null, output, recycler);
    }

    private Jackson2Writer(JsonGenerator generator,
                   SegmentedStringWriter stringOutput, ByteArrayBuilder byteOutput,
                   BufferRecycler recycler) {
        super(Backend.JACKSON2);
        this.generator = Asserts.notNull(generator, "generator");
        this.stringOutput = stringOutput;
        this.byteOutput = byteOutput;
        this.recycler = recycler;
    }


    /*
     * Structure
     */

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


    /*
     * Property Names
     */

    @Override
    public void writeName(String name) throws IOException {
        generator.writeFieldName(Asserts.notNull(name, "name"));
    }

    @Override
    public void writeName(String name, boolean separated) throws IOException {
        // Jackson's generator emits the necessary separator itself.
        writeName(name);
    }

    @Override
    public CompiledName createCompiledName(String name) {
        return new Jackson2CompiledName(name);
    }

    @Override
    public void writeName(CompiledName name) throws IOException {
        writeName(name, false);
    }

    @Override
    public void writeName(CompiledName name, boolean separated) throws IOException {
        generator.writeFieldName(((Jackson2CompiledName) name).serializedName);
    }


    /*
     * Null / String
     */

    @Override
    public void writeNull() throws IOException {
        generator.writeNull();
    }

    @Override
    public void writeStringValue(String value) throws IOException {
        generator.writeString(Asserts.notNull(value, "value"));
    }


    /*
     * Primitive Values
     */

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


    /*
     * Numbers
     */

    @Override
    public void writeNumberValue(Number value) throws IOException {
        Asserts.notNull(value, "value");
        if (value instanceof Integer) {
            generator.writeNumber(value.intValue());
        } else if (value instanceof Long) {
            generator.writeNumber(value.longValue());
        } else if (value instanceof Double) {
            generator.writeNumber(value.doubleValue());
        } else if (value instanceof Short || value instanceof Byte) {
            generator.writeNumber(value.shortValue());
        } else if (value instanceof Float) {
            generator.writeNumber(value.floatValue());
        } else if (value instanceof BigInteger) {
            generator.writeNumber((BigInteger) value);
        } else if (value instanceof BigDecimal) {
            generator.writeNumber((BigDecimal) value);
        } else {
            // Keep the existing support for custom Number subclasses.
            generator.writeNumber(value.toString());
        }
    }

    @Override
    public void writeBigIntegerValue(BigInteger value) throws IOException {
        generator.writeNumber(Asserts.notNull(value, "value"));
    }

    @Override
    public void writeBigDecimalValue(BigDecimal value) throws IOException {
        generator.writeNumber(Asserts.notNull(value, "value"));
    }


    /*
     * Lifecycle
     */

    @Override
    public void flush() throws IOException {
        generator.flush();
    }

    @Override
    public String toString() {
        if (stringOutput == null) {
            return super.toString();
        }
        try {
            return stringOutput.getAndClear();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Returns buffered UTF-8 after the generator has been flushed. */
    public byte[] toBytes() {
        if (byteOutput == null) throw new IllegalStateException("not an in-memory UTF-8 writer");
        return byteOutput.toByteArray();
    }

    @Override
    public void close() throws IOException {
        try {
            generator.close();
        } finally {
            if (byteOutput != null) byteOutput.release();
            if (recycler != null) {
                recycler.releaseToPool();
            }
        }
    }
}
