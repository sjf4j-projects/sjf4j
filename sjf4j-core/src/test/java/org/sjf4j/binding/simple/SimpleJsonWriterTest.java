package org.sjf4j.binding.simple;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingWriter;
import org.sjf4j.exception.BindingException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

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
        assertThrows(BindingException.class, () -> new SimpleJsonWriter(new StringWriter()).writeName((String) null));
        assertThrows(BindingException.class, () -> new SimpleJsonWriter(new StringWriter()).writeName((StreamingWriter.PropertyName) null));

        StringWriter output = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(new BufferedWriter(output))) {
            writer.writeStringValue("buffered");
            writer.flush();
        }
        assertEquals("\"buffered\"", output.toString());
    }

    @Test
    void rejectsNonFiniteNumbersDirectlyAndThroughJsonBinder() throws Exception {
        StringWriter output = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(output)) {
            assertThrows(BindingException.class, () -> writer.writeDoubleValue(Double.NaN));
            assertThrows(BindingException.class, () -> writer.writeDoubleValue(Double.POSITIVE_INFINITY));
            assertThrows(BindingException.class, () -> writer.writeFloatValue(Float.NEGATIVE_INFINITY));
            assertThrows(BindingException.class, () -> writer.writeNumberValue(Double.NaN));
            assertThrows(BindingException.class, () -> writer.writeNumberValue(Float.POSITIVE_INFINITY));
            writer.flush();
        }
        assertEquals("", output.toString());

        assertJsonBinderRejectsNonFiniteNumber(Double.NaN);
        assertJsonBinderRejectsNonFiniteNumber(Float.NEGATIVE_INFINITY);
    }

    @Test
    void rejectsUnpairedSurrogatesBeforeWritingStringsOrNames() throws Exception {
        String[] unpaired = {"before\uD800after", "before\uDC00after"};
        for (String value : unpaired) {
            StringWriter valueOutput = new StringWriter();
            try (SimpleJsonWriter writer = new SimpleJsonWriter(valueOutput)) {
                writer.writeStringValue("valid");
                assertThrows(BindingException.class, () -> writer.writeStringValue(value));
                writer.flush();
            }
            assertEquals("\"valid\"", valueOutput.toString());

            StringWriter nameOutput = new StringWriter();
            try (SimpleJsonWriter writer = new SimpleJsonWriter(nameOutput)) {
                assertThrows(BindingException.class, () -> writer.writeName(value));
                writer.flush();
            }
            assertEquals("", nameOutput.toString());
        }
    }

    @Test
    void rejectsNullStringValueBeforeWriting() throws Exception {
        StringWriter output = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(output)) {
            assertThrows(BindingException.class, () -> writer.writeStringValue(null));
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

    private static void assertJsonBinderRejectsNonFiniteNumber(Number value) {
        StringWriter output = new StringWriter();
        assertThrows(BindingException.class, () -> new SimpleJsonBinder().writeNode(output, value));
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

}
