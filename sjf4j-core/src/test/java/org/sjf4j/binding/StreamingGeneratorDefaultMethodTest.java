package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.binding.Backend;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamingGeneratorDefaultMethodTest {

    @Test
    void nullableWrappersDelegateToValueOrNullWriter() throws Exception {
        RecordingWriter generator = new RecordingWriter(null);
        generator.writeString("text");
        generator.writeString(null);
        generator.writeLong(1L);
        generator.writeLong(null);
        generator.writeInt(2);
        generator.writeInt(null);
        generator.writeShort((short) 3);
        generator.writeShort(null);
        generator.writeByte((byte) 4);
        generator.writeByte(null);
        generator.writeDouble(5.5d);
        generator.writeDouble(null);
        generator.writeFloat(6.5f);
        generator.writeFloat(null);
        generator.writeBoolean(true);
        generator.writeBoolean(null);
        generator.writeNumber(7);
        generator.writeNumber(null);
        generator.writeBigInteger(BigInteger.valueOf(8));
        generator.writeBigInteger(null);
        generator.writeBigDecimal(BigDecimal.valueOf(9));
        generator.writeBigDecimal(null);
        generator.writeName(new CompiledName("prepared"));

        assertEquals(List.of(
                "string:text", "null",
                "long:1", "null",
                "int:2", "null",
                "short:3", "null",
                "byte:4", "null",
                "double:5.5", "null",
                "float:6.5", "null",
                "boolean:true", "null",
                "number:7", "null",
                "number:8", "null",
                "number:9", "null",
                "name:prepared"), generator.events);
    }

    private static final class RecordingWriter extends StreamingGenerator {
        private final List<String> events = new ArrayList<>();

        RecordingWriter(Binder<?, ?> binder) {
            super(Backend.AUTO);
        }

        public void startObject() {} public void endObject() {} public void startArray() {} public void endArray() {}
        public void writeName(String name) {
            events.add("name:" + name);
        }
        public void writeNull() {
            events.add("null");
        }
        public void writeStringValue(String value) {
            events.add("string:" + value);
        }
        public void writeLongValue(long value) { events.add("long:" + value); }
        public void writeIntValue(int value) { events.add("int:" + value); }
        public void writeShortValue(short value) { events.add("short:" + value); }
        public void writeByteValue(byte value) { events.add("byte:" + value); }
        public void writeDoubleValue(double value) { events.add("double:" + value); }
        public void writeFloatValue(float value) { events.add("float:" + value); }
        public void writeBooleanValue(boolean value) { events.add("boolean:" + value); }
        public void writeCharValue(char value) {}
        public void writeNumberValue(Number value) { events.add("number:" + value); }
        public void release() {}
    }
}
