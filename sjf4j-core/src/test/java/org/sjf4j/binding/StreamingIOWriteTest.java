package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.simple.SimpleJsonWriter;
import org.sjf4j.exception.BindingException;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamingIOWriteTest {

    @Test
    void writesMapsArraysAndNodes() throws Exception {
        StringWriter output = new StringWriter();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("items", new int[]{1, 2});
        map.put("node", JsonObject.of("values", JsonArray.of("a", "b")));
        try (SimpleJsonWriter writer = new SimpleJsonWriter(null, output)) {
            StreamingIO.writeNode(writer, map, RuntimeContext.EMPTY);
            writer.flush();
        }
        assertEquals("{\"items\":[1,2],\"node\":{\"values\":[\"a\",\"b\"]}}", output.toString());

        SeparatorWriter separators = new SeparatorWriter(null);
        StreamingIO.writeNode(separators, new int[]{1, 2}, RuntimeContext.EMPTY);
        assertEquals(0, separators.properties);
        assertEquals(1, separators.elements);
    }

    @Test
    void writesCharsetName() throws Exception {
        StringWriter output = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(null, output)) {
            StreamingIO.writeNode(writer, StandardCharsets.UTF_8, RuntimeContext.EMPTY);
            writer.flush();
        }
        assertEquals("\"UTF-8\"", output.toString());
    }

    @Test
    void writesPrimitiveAndObjectArrays() throws Exception {
        Object[] arrays = {
                new boolean[]{true, false}, new byte[]{1, 2}, new short[]{3, 4},
                new int[]{5, 6}, new long[]{7L, 8L}, new float[]{1.5f, 2.5f},
                new double[]{3.5d, 4.5d}, new char[]{'a', 'b'}, new int[0],
                new Object[]{"value", null, 9}
        };
        String[] json = {
                "[true,false]", "[1,2]", "[3,4]", "[5,6]", "[7,8]", "[1.5,2.5]",
                "[3.5,4.5]", "[\"a\",\"b\"]", "[]", "[\"value\",null,9]"
        };

        for (int i = 0; i < arrays.length; i++) {
            StringWriter output = new StringWriter();
            try (SimpleJsonWriter writer = new SimpleJsonWriter(null, output)) {
                StreamingIO.writeNode(writer, arrays[i], RuntimeContext.EMPTY);
                writer.flush();
            }
            assertEquals(json[i], output.toString());
        }
    }

    @Test
    void writesListsSetsAndMapsWithConfiguredNullHandling() throws Exception {
        StringWriter randomAccessOutput = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(null, randomAccessOutput)) {
            StreamingIO.writeNode(writer, List.of("a", 2), RuntimeContext.EMPTY);
            writer.flush();
        }
        assertEquals("[\"a\",2]", randomAccessOutput.toString());

        StringWriter linkedOutput = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(null, linkedOutput)) {
            StreamingIO.writeNode(writer, new LinkedList<>(List.of("a", 2)), RuntimeContext.EMPTY);
            writer.flush();
        }
        assertEquals("[\"a\",2]", linkedOutput.toString());

        StringWriter setOutput = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(null, setOutput)) {
            StreamingIO.writeNode(writer, new LinkedHashSet<>(List.of("a", 2)), RuntimeContext.EMPTY);
            writer.flush();
        }
        assertEquals("[\"a\",2]", setOutput.toString());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("included", 1);
        map.put("omitted", null);
        StringWriter mapOutput = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(null, mapOutput)) {
            StreamingIO.writeNode(writer, map, new RuntimeContext(false));
            writer.flush();
        }
        assertEquals("{\"included\":1}", mapOutput.toString());

        StringWriter includeNullsOutput = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(null, includeNullsOutput)) {
            StreamingIO.writeNode(writer, map, RuntimeContext.EMPTY);
            writer.flush();
        }
        assertEquals("{\"included\":1,\"omitted\":null}", includeNullsOutput.toString());
    }

    @Test
    void rejectsUnsupportedNodeTypes() throws Exception {
        try (SimpleJsonWriter writer = new SimpleJsonWriter(null, new StringWriter())) {
            assertThrows(BindingException.class,
                    () -> StreamingIO.writeNode(writer, new UnsupportedNode(), RuntimeContext.EMPTY));
        }
    }

    private static final class UnsupportedNode {}

    private static final class SeparatorWriter extends StreamingWriter {
        private int properties;
        private int elements;

        SeparatorWriter(Binder<?, ?> binder) {
            super(binder);
        }

        @Override
        public void startObject() {}
        @Override
        public void endObject() {}
        @Override
        public void startArray() {}
        @Override
        public void endArray() {}
        @Override
        public void writeName(String name) {}
        @Override
        public void writeNull() {}
        @Override
        public void writeStringValue(String value) {}
        @Override
        public void writeLongValue(long value) {}
        @Override
        public void writeIntValue(int value) {}
        @Override
        public void writeShortValue(short value) {}
        @Override
        public void writeByteValue(byte value) {}
        @Override
        public void writeDoubleValue(double value) {}
        @Override
        public void writeFloatValue(float value) {}
        @Override
        public void writeBooleanValue(boolean value) {}
        @Override
        public void writeCharValue(char value) {}
        @Override
        public void writeNumberValue(Number value) {}
        @Override
        public void separateProperty() {
                properties++;
            }
        @Override
        public void separateElement() {
                elements++;
            }
        @Override
        public void flush() {}
        @Override
        public void close() {}
    }
}
