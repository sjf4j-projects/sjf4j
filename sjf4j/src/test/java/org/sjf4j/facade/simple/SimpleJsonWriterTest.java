package org.sjf4j.facade.simple;

import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimpleJsonWriterTest {

    @Test
    void testWritesAndEscapes() throws Exception {
        StringWriter out = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(out)) {
            writer.startObject();
            writer.writeName("a");
            writer.writeString("\"\\\b\f\n\r\t\u0001x");
            writer.writeObjectComma();
            writer.writeName("b");
            writer.startArray();
            writer.writeNumber(1);
            writer.writeArrayComma();
            writer.writeBoolean(false);
            writer.writeArrayComma();
            writer.writeNull();
            writer.endArray();
            writer.endObject();
            writer.flush();
        }

        assertEquals("{\"a\":\"\\\"\\\\\\b\\f\\n\\r\\t\\u0001x\",\"b\":[1,false,null]}", out.toString());
    }

    @Test
    void testNullHandlingAndNameValidation() {
        assertThrows(IOException.class, () -> {
            SimpleJsonWriter writer = new SimpleJsonWriter(new StringWriter());
            writer.writeName(null);
        });

        StringWriter out = new StringWriter();
        assertThrows(NullPointerException.class, () -> new SimpleJsonWriter(null));

        try (SimpleJsonWriter writer = new SimpleJsonWriter(out)) {
            writer.writeString(null);
            writer.writeNumber(null);
            writer.writeBoolean(null);
            writer.flush();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertEquals("nullnullnull", out.toString());
    }

    @Test
    void testWriteBooleanTrue() throws Exception {
        StringWriter out = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(out)) {
            writer.writeBoolean(true);
            writer.flush();
        }
        assertEquals("true", out.toString());
    }

    @Test
    void testBufferedWriterConstructorPath() throws Exception {
        StringWriter out = new StringWriter();
        try (SimpleJsonWriter writer = new SimpleJsonWriter(new BufferedWriter(out))) {
            writer.writeNumber(1);
            writer.flush();
        }
        assertEquals("1", out.toString());
    }
}
