package org.sjf4j.binding.simple;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.StreamingIO;
import org.sjf4j.binding.StreamingWriter;
import org.sjf4j.exception.BindingException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimpleJsonWriterTest {

    @Test
    void writesPrimitivesAndEscapesStrings() throws Exception {
        StringWriter output = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(output)) {
            writer.startArray();
            writer.writeLongValue(1L); writer.separateElement();
            writer.writeIntValue(2); writer.separateElement();
            writer.writeShortValue((short) 3); writer.separateElement();
            writer.writeByteValue((byte) 4); writer.separateElement();
            writer.writeDoubleValue(1.5); writer.separateElement();
            writer.writeFloatValue(2.5f); writer.separateElement();
            writer.writeBooleanValue(true); writer.separateElement();
            writer.writeStringValue("\"\\\b\f\n\r\t\u0001x");
            writer.endArray();
            writer.flush();
        }
        assertEquals("[1,2,3,4,1.5,2.5,true,\"\\\"\\\\\\b\\f\\n\\r\\t\\u0001x\"]", output.toString());
    }

    @Test
    void interfaceBoxedDefaultsWriteNullsAndGenericNumbers() throws Exception {
        StringWriter output = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(output)) {
            writer.writeLong(null); writer.writeInt(null); writer.writeShort(null); writer.writeByte(null);
            writer.writeDouble(null); writer.writeFloat(null); writer.writeBoolean(null); writer.writeString(null);
            writer.writeNumber(null); writer.writeBigInteger(null); writer.writeBigDecimal(null);
            writer.writeNumberValue(new BigInteger("12345678901234567890"));
            writer.writeBigIntegerValue(new BigInteger("42"));
            writer.writeBigDecimalValue(new BigDecimal("1.2300"));
            writer.flush();
        }
        assertEquals("nullnullnullnullnullnullnullnullnullnullnull12345678901234567890421.2300", output.toString());
    }

    @Test
    void writesPreparedAndForeignNamesWithPropertyAndElementSeparators() throws Exception {
        StringWriter output = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(output)) {
            writer.startObject();
            writer.writeName("a\n");
            writer.writeInt(1);
            writer.separateProperty();
            writer.writeName(() -> "foreign");
            writer.startArray();
            writer.writeStringValue("x");
            writer.separateElement();
            writer.startObject();
            writer.writeName("ok");
            writer.writeBoolean(false);
            writer.endObject();
            writer.endArray();
            writer.endObject();
            writer.flush();
        }
        assertEquals("{\"a\\n\":1,\"foreign\":[\"x\",{\"ok\":false}]}", output.toString());
    }

    @Test
    void validatesNamesAndSupportsBufferedWriters() throws Exception {
        assertThrows(NullPointerException.class, () -> new SimpleJsonWriter(null));
        assertThrows(IOException.class, () -> new SimpleJsonWriter(new StringWriter()).writeName((String) null));
        assertThrows(NullPointerException.class, () -> new SimpleJsonWriter(new StringWriter()).writeName((StreamingWriter.PropertyName) null));

        StringWriter output = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(new BufferedWriter(output))) {
            writer.writeStringValue("buffered");
            writer.flush();
        }
        assertEquals("\"buffered\"", output.toString());
    }

    @Test
    void rejectsNonFiniteNumbersDirectlyAndThroughStreamingIo() throws Exception {
        StringWriter output = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(output)) {
            assertThrows(IOException.class, () -> writer.writeDoubleValue(Double.NaN));
            assertThrows(IOException.class, () -> writer.writeDoubleValue(Double.POSITIVE_INFINITY));
            assertThrows(IOException.class, () -> writer.writeFloatValue(Float.NEGATIVE_INFINITY));
            assertThrows(IOException.class, () -> writer.writeNumberValue(Double.NaN));
            assertThrows(IOException.class, () -> writer.writeNumberValue(Float.POSITIVE_INFINITY));
            writer.flush();
        }
        assertEquals("", output.toString());

        assertStreamingIoRejectsNonFiniteNumber(Double.NaN);
        assertStreamingIoRejectsNonFiniteNumber(Float.NEGATIVE_INFINITY);
    }

    @Test
    void rejectsUnpairedSurrogatesBeforeWritingStringsOrNames() throws Exception {
        String[] unpaired = {"before\uD800after", "before\uDC00after"};
        for (String value : unpaired) {
            StringWriter valueOutput = new StringWriter();
            try (SimpleJsonWriter writer = new SimpleJsonWriter(valueOutput)) {
                writer.writeStringValue("valid");
                assertThrows(IOException.class, () -> writer.writeStringValue(value));
                writer.flush();
            }
            assertEquals("\"valid\"", valueOutput.toString());

            StringWriter nameOutput = new StringWriter();
            try (SimpleJsonWriter writer = new SimpleJsonWriter(nameOutput)) {
                assertThrows(IOException.class, () -> writer.writeName(value));
                writer.flush();
            }
            assertEquals("", nameOutput.toString());
        }
    }

    @Test
    void rejectsNullStringValueBeforeWriting() throws Exception {
        StringWriter output = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(output)) {
            assertThrows(IOException.class, () -> writer.writeStringValue(null));
            writer.flush();
        }
        assertEquals("", output.toString());
    }

    @Test
    void writesOrdinaryStringRunsAsContiguousSpans() throws Exception {
        TrackingBufferedWriter output = new TrackingBufferedWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(output)) {
            writer.writeStringValue("first\nsecond");
            writer.flush();
        }
        assertEquals("\"first\\nsecond\"", output.output.toString());
        assertEquals(List.of("first", "\\n", "second"), output.stringWrites);
    }

    @Test
    void streamingIoWritesMapsArraysAndNodes() throws Exception {
        StringWriter output = new StringWriter();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("items", new int[]{1, 2});
        map.put("node", JsonObject.of("values", JsonArray.of("a", "b")));
        try (SimpleJsonWriter writer = new SimpleJsonWriter(output)) {
            StreamingIO.writeNode(writer, map, StreamingContext.EMPTY);
            writer.flush();
        }
        assertEquals("{\"items\":[1,2],\"node\":{\"values\":[\"a\",\"b\"]}}", output.toString());

        SeparatorWriter separators = new SeparatorWriter();
        StreamingIO.writeNode(separators, new int[]{1, 2}, StreamingContext.EMPTY);
        assertEquals(0, separators.properties);
        assertEquals(1, separators.elements);
    }

    private static void assertStreamingIoRejectsNonFiniteNumber(Number value) throws Exception {
        StringWriter output = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(output)) {
            BindingException exception = assertThrows(BindingException.class,
                    () -> StreamingIO.writeNode(writer, value, StreamingContext.EMPTY));
            assertEquals(IOException.class, exception.getCause().getClass());
            writer.flush();
        }
        assertEquals("", output.toString());
    }

    private static final class TrackingBufferedWriter extends BufferedWriter {
        private final StringWriter output;
        private final List<String> stringWrites = new ArrayList<>();

        private TrackingBufferedWriter() {
            this(new StringWriter());
        }

        private TrackingBufferedWriter(StringWriter output) {
            super(output);
            this.output = output;
        }

        @Override
        public void write(String value, int offset, int length) throws IOException {
            stringWrites.add(value.substring(offset, offset + length));
            super.write(value, offset, length);
        }
    }

    private static final class SeparatorWriter implements StreamingWriter {
        private int properties;
        private int elements;

        @Override public void startObject() {}
        @Override public void endObject() {}
        @Override public void startArray() {}
        @Override public void endArray() {}
        @Override public void writeName(String name) {}
        @Override public void writeNull() {}
        @Override public void writeStringValue(String value) {}
        @Override public void writeLongValue(long value) {}
        @Override public void writeIntValue(int value) {}
        @Override public void writeShortValue(short value) {}
        @Override public void writeByteValue(byte value) {}
        @Override public void writeDoubleValue(double value) {}
        @Override public void writeFloatValue(float value) {}
        @Override public void writeBooleanValue(boolean value) {}
        @Override public void writeNumberValue(Number value) {}
        @Override public void separateProperty() { properties++; }
        @Override public void separateElement() { elements++; }
        @Override public void flush() {}
        @Override public void close() {}
    }
}
