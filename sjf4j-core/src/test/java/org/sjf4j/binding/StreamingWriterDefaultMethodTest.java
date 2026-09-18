package org.sjf4j.binding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamingWriterDefaultMethodTest {
    @Test
    void preparedStringPropertyDelegatesToNameAndValueMethods() throws Exception {
        RecordingWriter writer = new RecordingWriter();
        writer.writeStringProperty(() -> "name", "Ada");
        assertEquals(List.of("name:name", "string:Ada"), writer.events);
    }

    @Test
    void boxedNullDelegatesToNullWriter() throws Exception {
        RecordingWriter writer = new RecordingWriter();
        writer.writeInt(null);
        assertEquals(List.of("null"), writer.events);
    }

    private static final class RecordingWriter implements StreamingWriter {
        private final List<String> events = new ArrayList<>();
        public void startObject() {} public void endObject() {} public void startArray() {} public void endArray() {}
        public void writeName(String name) { events.add("name:" + name); } public void writeNull() { events.add("null"); }
        public void writeStringValue(String value) { events.add("string:" + value); }
        public void writeLongValue(long value) {} public void writeIntValue(int value) { events.add("int:" + value); }
        public void writeShortValue(short value) {} public void writeByteValue(byte value) {}
        public void writeDoubleValue(double value) {} public void writeFloatValue(float value) {}
        public void writeBooleanValue(boolean value) {} public void writeCharValue(char value) {}
        public void writeNumberValue(Number value) {} public void flush() {} public void close() {}
    }
}
