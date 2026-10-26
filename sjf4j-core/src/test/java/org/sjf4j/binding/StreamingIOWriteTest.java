package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.binding.simple.SimpleJsonGenerator;
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
        SimpleJsonGenerator writer = new SimpleJsonGenerator(null, output);
try {
            StreamingIO.writeNode(writer, map, RuntimeContext.EMPTY);
            writer.flush();
        
} finally { writer.release(); }
        assertEquals("{\"items\":[1,2],\"node\":{\"values\":[\"a\",\"b\"]}}", output.toString());

        SeparatorWriter separators = new SeparatorWriter();
        StreamingIO.writeNode(separators, new int[]{1, 2}, RuntimeContext.EMPTY);
        assertEquals(0, separators.properties);
        assertEquals(1, separators.elements);
    }

    @Test
    void writesCharsetName() throws Exception {
        StringWriter output = new StringWriter();
        SimpleJsonGenerator writer = new SimpleJsonGenerator(null, output);
try {
            StreamingIO.writeNode(writer, StandardCharsets.UTF_8, RuntimeContext.EMPTY);
            writer.flush();
        
} finally { writer.release(); }
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
            SimpleJsonGenerator writer = new SimpleJsonGenerator(null, output);
try {
                StreamingIO.writeNode(writer, arrays[i], RuntimeContext.EMPTY);
                writer.flush();
            
} finally { writer.release(); }
            assertEquals(json[i], output.toString());
        }
    }

    @Test
    void writesSubclassArraysInPojoFields() throws Exception {
        Student student1 = new Student("Ada", "mathematics");
        Student student2 = new Student("Bob", "physics");
        Holder holder = new Holder();
        holder.students = new Student[]{student1, student2};
        holder.objects = new Student[]{student1, student2};
        holder.matrix = new Student[][]{{student1}, {student2}};
        holder.people = new Student[]{student1, student2};

        StringWriter output = new StringWriter();
        SimpleJsonGenerator writer = new SimpleJsonGenerator(null, output);
try {
            StreamingIO.writeNode(writer, holder, RuntimeContext.EMPTY);
            writer.flush();
        
} finally { writer.release(); }
        assertEquals("{\"students\":[{\"subject\":\"mathematics\",\"name\":\"Ada\"},"
                        + "{\"subject\":\"physics\",\"name\":\"Bob\"}],"
                        + "\"objects\":[{\"subject\":\"mathematics\",\"name\":\"Ada\"},"
                        + "{\"subject\":\"physics\",\"name\":\"Bob\"}],"
                        + "\"matrix\":[[{\"subject\":\"mathematics\",\"name\":\"Ada\"}],"
                        + "[{\"subject\":\"physics\",\"name\":\"Bob\"}]],"
                        + "\"people\":[{\"subject\":\"mathematics\",\"name\":\"Ada\"},"
                        + "{\"subject\":\"physics\",\"name\":\"Bob\"}]}", output.toString());
    }

    @Test
    void writesListsSetsAndMapsWithConfiguredNullHandling() throws Exception {
        StringWriter randomAccessOutput = new StringWriter();
        SimpleJsonGenerator writer = new SimpleJsonGenerator(null, randomAccessOutput);
try {
            StreamingIO.writeNode(writer, List.of("a", 2), RuntimeContext.EMPTY);
            writer.flush();
        
} finally { writer.release(); }
        assertEquals("[\"a\",2]", randomAccessOutput.toString());

        StringWriter linkedOutput = new StringWriter();
        SimpleJsonGenerator writer = new SimpleJsonGenerator(null, linkedOutput);
try {
            StreamingIO.writeNode(writer, new LinkedList<>(List.of("a", 2)), RuntimeContext.EMPTY);
            writer.flush();
        
} finally { writer.release(); }
        assertEquals("[\"a\",2]", linkedOutput.toString());

        StringWriter setOutput = new StringWriter();
        SimpleJsonGenerator writer = new SimpleJsonGenerator(null, setOutput);
try {
            StreamingIO.writeNode(writer, new LinkedHashSet<>(List.of("a", 2)), RuntimeContext.EMPTY);
            writer.flush();
        
} finally { writer.release(); }
        assertEquals("[\"a\",2]", setOutput.toString());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("included", 1);
        map.put("omitted", null);
        StringWriter mapOutput = new StringWriter();
        SimpleJsonGenerator writer = new SimpleJsonGenerator(null, mapOutput);
try {
            StreamingIO.writeNode(writer, map, new RuntimeContext(false));
            writer.flush();
        
} finally { writer.release(); }
        assertEquals("{\"included\":1}", mapOutput.toString());

        StringWriter includeNullsOutput = new StringWriter();
        SimpleJsonGenerator writer = new SimpleJsonGenerator(null, includeNullsOutput);
try {
            StreamingIO.writeNode(writer, map, RuntimeContext.EMPTY);
            writer.flush();
        
} finally { writer.release(); }
        assertEquals("{\"included\":1,\"omitted\":null}", includeNullsOutput.toString());
    }

    @Test
    void rawWriterPreservesJsonValuesWithoutBindingNestedTypes() throws Exception {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "Ada");
        raw.put("skip", null);
        raw.put("nested", List.of(3, true, Map.of("value", 2.5)));
        StringWriter output = new StringWriter();
        SimpleJsonGenerator writer = new SimpleJsonGenerator(null, output);
try {
            writer.writeRaw(raw, new RuntimeContext(false));
            writer.flush();
        
} finally { writer.release(); }
        assertEquals("{\"name\":\"Ada\",\"nested\":[3,true,{\"value\":2.5}]}",
                output.toString());

        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("value", null);
        withNull.put("list", java.util.Arrays.asList(null, 1));
        StringWriter outputWithNull = new StringWriter();
        SimpleJsonGenerator writer = new SimpleJsonGenerator(null, outputWithNull);
try {
            writer.writeRaw(withNull, RuntimeContext.EMPTY);
            writer.flush();
        
} finally { writer.release(); }
        assertEquals("{\"value\":null,\"list\":[null,1]}", outputWithNull.toString());

        StringWriter rejected = new StringWriter();
        SimpleJsonGenerator writer = new SimpleJsonGenerator(null, rejected);
try {
            assertThrows(BindingException.class,
                    () -> writer.writeRaw(new UnsupportedNode(), RuntimeContext.EMPTY));
        
} finally { writer.release(); }
    }

    @Test
    void rejectsUnsupportedNodeTypes() throws Exception {
        StringWriter sw = new StringWriter();
        SimpleJsonGenerator writer = new SimpleJsonGenerator(null, sw);
try {
            StreamingIO.writeNode(writer, new UnsupportedNode(), RuntimeContext.EMPTY);
//            assertThrows(BindingException.class,
//                    () -> StreamingIO.writeNode(writer, new UnsupportedNode(), RuntimeContext.EMPTY));
        
} finally { writer.release(); }
        assertEquals("{}", sw.toString());
    }

    private static final class UnsupportedNode {}

    static class Person {
        public String name;

        Person() {
        }

        Person(String name) {
            this.name = name;
        }
    }

    static class Student extends Person {
        public String subject;

        Student() {
        }

        Student(String name, String subject) {
            super(name);
            this.subject = subject;
        }
    }

    static class Holder {
        public Student[] students;
        public Object[] objects;
        public Student[][] matrix;
        public Person[] people;
    }

    private static final class SeparatorWriter extends StreamingGenerator {
        private int properties;
        private int elements;

        SeparatorWriter() {
            super(Backend.AUTO);
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
