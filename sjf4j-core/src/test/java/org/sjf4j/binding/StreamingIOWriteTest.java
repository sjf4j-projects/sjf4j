package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.binding.simple.SimpleJsonWriter;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamingIOWriteTest {

    @Test
    void writesMapsArraysAndNodes() throws Exception {
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

    @Test
    void writesCharsetName() throws Exception {
        StringWriter output = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(output)) {
            StreamingIO.writeNode(writer, StandardCharsets.UTF_8, StreamingContext.EMPTY);
            writer.flush();
        }
        assertEquals("\"UTF-8\"", output.toString());
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
        @Override public void writeCharValue(char value) {}
        @Override public void writeNumberValue(Number value) {}
        @Override public void separateProperty() { properties++; }
        @Override public void separateElement() { elements++; }
        @Override public void flush() {}
        @Override public void close() {}
    }
}
