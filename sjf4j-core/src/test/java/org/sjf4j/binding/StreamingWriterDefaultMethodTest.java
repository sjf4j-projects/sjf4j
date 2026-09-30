package org.sjf4j.binding;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamingWriterDefaultMethodTest {

    @Test
    void nullableWrappersDelegateToValueOrNullWriter() throws Exception {
        RecordingWriter writer = new RecordingWriter(null);
        writer.writeString("text");
        writer.writeString(null);
        writer.writeLong(1L);
        writer.writeLong(null);
        writer.writeInt(2);
        writer.writeInt(null);
        writer.writeShort((short) 3);
        writer.writeShort(null);
        writer.writeByte((byte) 4);
        writer.writeByte(null);
        writer.writeDouble(5.5d);
        writer.writeDouble(null);
        writer.writeFloat(6.5f);
        writer.writeFloat(null);
        writer.writeBoolean(true);
        writer.writeBoolean(null);
        writer.writeNumber(7);
        writer.writeNumber(null);
        writer.writeBigInteger(BigInteger.valueOf(8));
        writer.writeBigInteger(null);
        writer.writeBigDecimal(BigDecimal.valueOf(9));
        writer.writeBigDecimal(null);
        writer.writeName(new PreparedName.SimplePreparedName("prepared"));

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
                "name:prepared"), writer.events);
    }

    private static final class RecordingWriter extends StreamingWriter {
        private final List<String> events = new ArrayList<>();

        RecordingWriter(Binder<?, ?> binder) {
            super(binder);
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
        public void flush() {} public void close() {}
    }
}
