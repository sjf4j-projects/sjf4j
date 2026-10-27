package org.sjf4j.binding.simple;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.CompiledName;
import org.sjf4j.RuntimeContext;
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

class SimpleJsonGeneratorTest {

    @Test
    void writesPrimitivesAndEscapesStrings() throws Exception {
        StringWriter output = new StringWriter();
        {
            SimpleJsonGenerator generator = new SimpleJsonGenerator(null, output);
            try {
                generator.startArray();
                generator.writeLongValue(1L); generator.separateElement();
                generator.writeIntValue(2); generator.separateElement();
                generator.writeShortValue((short) 3); generator.separateElement();
                generator.writeByteValue((byte) 4); generator.separateElement();
                generator.writeDoubleValue(1.5); generator.separateElement();
                generator.writeFloatValue(2.5f); generator.separateElement();
                generator.writeBooleanValue(true); generator.separateElement();
                generator.writeStringValue("\"\\\b\f\n\r\t\u0001x");
                generator.endArray();
                generator.endWrite();

            } finally {
                generator.release();
            }
        }
        assertEquals("[1,2,3,4,1.5,2.5,true,\"\\\"\\\\\\b\\f\\n\\r\\t\\u0001x\"]", output.toString());
    }

    @Test
    void interfaceBoxedDefaultsWriteNullsAndGenericNumbers() throws Exception {
        StringWriter output = new StringWriter();
        {
            SimpleJsonGenerator generator = new SimpleJsonGenerator(null, output);
            try {
                generator.writeLong(null); generator.writeInt(null); generator.writeShort(null); generator.writeByte(null);
                generator.writeDouble(null); generator.writeFloat(null); generator.writeBoolean(null); generator.writeString(null);
                generator.writeNumber(null); generator.writeBigInteger(null); generator.writeBigDecimal(null);
                generator.writeNumberValue(new BigInteger("12345678901234567890"));
                generator.writeBigIntegerValue(new BigInteger("42"));
                generator.writeBigDecimalValue(new BigDecimal("1.2300"));
                generator.endWrite();

            } finally {
                generator.release();
            }
        }
        assertEquals("nullnullnullnullnullnullnullnullnullnullnull12345678901234567890421.2300", output.toString());
    }

    @Test
    void writesPreparedAndForeignNamesWithPropertyAndElementSeparators() throws Exception {
        StringWriter output = new StringWriter();
        {
            SimpleJsonGenerator generator = new SimpleJsonGenerator(null, output);
            try {
                generator.startObject();
                generator.writeName("a\n");
                generator.writeInt(1);
                generator.separateProperty();
                generator.writeName(new CompiledName("foreign"));
                generator.startArray();
                generator.writeStringValue("x");
                generator.separateElement();
                generator.startObject();
                generator.writeName("ok");
                generator.writeBoolean(false);
                generator.endObject();
                generator.endArray();
                generator.endObject();
                generator.endWrite();

            } finally {
                generator.release();
            }
        }
        assertEquals("{\"a\\n\":1,\"foreign\":[\"x\",{\"ok\":false}]}", output.toString());
    }

    @Test
    void validatesNamesAndSupportsBufferedWriters() throws Exception {
        assertThrows(NullPointerException.class, () -> new SimpleJsonGenerator(null, null));
        assertThrows(BindingException.class, () -> new SimpleJsonGenerator(null, new StringWriter()).writeName((String) null));
        assertThrows(NullPointerException.class, () -> new SimpleJsonGenerator(null, new StringWriter()).writeName((CompiledName) null));

        StringWriter output = new StringWriter();
        {
            SimpleJsonGenerator generator = new SimpleJsonGenerator(null, new BufferedWriter(output));
            try {
                generator.writeStringValue("buffered");
                generator.endWrite();

            } finally {
                generator.release();
            }
        }
        assertEquals("\"buffered\"", output.toString());
    }

    @Test
    void rejectsNonFiniteNumbersDirectlyAndThroughJsonBinder() throws Exception {
        StringWriter output = new StringWriter();
        {
            SimpleJsonGenerator generator = new SimpleJsonGenerator(null, output);
            try {
                assertThrows(BindingException.class, () -> generator.writeDoubleValue(Double.NaN));
                assertThrows(BindingException.class, () -> generator.writeDoubleValue(Double.POSITIVE_INFINITY));
                assertThrows(BindingException.class, () -> generator.writeFloatValue(Float.NEGATIVE_INFINITY));
                assertThrows(BindingException.class, () -> generator.writeNumberValue(Double.NaN));
                assertThrows(BindingException.class, () -> generator.writeNumberValue(Float.POSITIVE_INFINITY));
                generator.endWrite();

            } finally {
                generator.release();
            }
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
            {
                SimpleJsonGenerator generator = new SimpleJsonGenerator(null, valueOutput);
                try {
                    generator.writeStringValue("valid");
                    assertThrows(BindingException.class, () -> generator.writeStringValue(value));
                    generator.endWrite();

                } finally {
                    generator.release();
                }
            }
            assertEquals("\"valid\"", valueOutput.toString());

            StringWriter nameOutput = new StringWriter();
            {
                SimpleJsonGenerator generator = new SimpleJsonGenerator(null, nameOutput);
                try {
                    assertThrows(BindingException.class, () -> generator.writeName(value));
                    generator.endWrite();

                } finally {
                    generator.release();
                }
            }
            assertEquals("", nameOutput.toString());
        }
    }

    @Test
    void rejectsNullStringValueBeforeWriting() throws Exception {
        StringWriter output = new StringWriter();
        {
            SimpleJsonGenerator generator = new SimpleJsonGenerator(null, output);
            try {
                assertThrows(BindingException.class, () -> generator.writeStringValue(null));
                generator.endWrite();

            } finally {
                generator.release();
            }
        }
        assertEquals("", output.toString());
    }

    @Test
    void writesOrdinaryStringRunsAsContiguousSpans() throws Exception {
        TrackingBufferedWriter output = new TrackingBufferedWriter();
        {
            SimpleJsonGenerator generator = new SimpleJsonGenerator(null, output);
            try {
                generator.writeStringValue("first\nsecond");
                generator.endWrite();

            } finally {
                generator.release();
            }
        }
        assertEquals("\"first\\nsecond\"", output.output.toString());
        assertEquals(List.of("first", "\\n", "second"), output.stringWrites);
    }

    private static void assertJsonBinderRejectsNonFiniteNumber(Number value) {
        StringWriter output = new StringWriter();
        assertThrows(BindingException.class, () -> new SimpleJsonBinder(RuntimeContext.EMPTY).writeNode(output, value));
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
