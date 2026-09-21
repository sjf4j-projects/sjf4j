package org.sjf4j.backend.snake.binding;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnakeWriterTest {

    @Test
    void writesObjectsNamesAndNestedValuesForNativeSnakeYaml() throws Exception {
        SnakeBinder binder = new SnakeBinder();
        StringWriter output = new StringWriter();

        try (SnakeWriter writer = binder.createWriter(output)) {
            writer.startDocument();
            writer.startObject();
            writer.writeName("nullLike");
            writer.writeStringValue("null");
            writer.writeName("nested");
            writer.startObject();
            writer.writeName("booleanLike");
            writer.writeStringValue("true");
            writer.writeName("values");
            writer.startArray();
            writer.writeStringValue("123");
            writer.writeStringValue("~");
            writer.writeStringValue("hello: world");
            writer.endArray();
            writer.endObject();
            writer.endObject();
            writer.endDocument();
            writer.flush();
        }

        Map<?, ?> document = assertInstanceOf(Map.class, new Yaml().load(output.toString()));
        assertEquals("null", assertInstanceOf(String.class, document.get("nullLike")));

        Map<?, ?> nested = assertInstanceOf(Map.class, document.get("nested"));
        assertEquals("true", assertInstanceOf(String.class, nested.get("booleanLike")));

        List<?> values = assertInstanceOf(List.class, nested.get("values"));
        assertEquals("123", assertInstanceOf(String.class, values.get(0)));
        assertEquals("~", assertInstanceOf(String.class, values.get(1)));
        assertEquals("hello: world", assertInstanceOf(String.class, values.get(2)));
    }

    @Test
    void roundTripsQuotedStringsAndScalarTypes() throws Exception {
        SnakeBinder binder = new SnakeBinder();
        StringWriter output = new StringWriter();

        try (SnakeWriter writer = binder.createWriter(output)) {
            writer.startDocument();
            writer.startArray();
            writer.writeStringValue("");
            writer.writeStringValue("null");
            writer.writeStringValue("~");
            writer.writeStringValue("true");
            writer.writeStringValue("123");
            writer.writeStringValue("hello: world");
            writer.writeLongValue(1L);
            writer.writeIntValue(2);
            writer.writeShortValue((short) 3);
            writer.writeByteValue((byte) 4);
            writer.writeDoubleValue(5.5d);
            writer.writeFloatValue(6.5f);
            writer.writeBooleanValue(true);
            writer.writeCharValue('x');
            writer.writeNumberValue(new BigInteger("12345678901234567890"));
            writer.writeNumberValue(new BigDecimal("7.25"));
            writer.writeNull();
            writer.endArray();
            writer.endDocument();
            writer.flush();
        }

        try (SnakeReader reader = reader(binder, output.toString())) {
            reader.startArray();
            assertEquals("", reader.nextString());
            assertEquals("null", reader.nextString());
            assertEquals("~", reader.nextString());
            assertEquals("true", reader.nextString());
            assertEquals("123", reader.nextString());
            assertEquals("hello: world", reader.nextString());
            assertEquals(1L, reader.nextLongValue());
            assertEquals(2, reader.nextIntValue());
            assertEquals((short) 3, reader.nextShortValue());
            assertEquals((byte) 4, reader.nextByteValue());
            assertEquals(5.5d, reader.nextDoubleValue());
            assertEquals(6.5f, reader.nextFloatValue());
            assertTrue(reader.nextBooleanValue());
            assertEquals('x', reader.nextCharValue());
            assertEquals(new BigInteger("12345678901234567890"), reader.nextBigInteger());
            assertEquals(new BigDecimal("7.25"), reader.nextBigDecimal());
            reader.nextNull();
            reader.endArray();
            reader.endDocument();
        }
    }

    private static SnakeReader reader(SnakeBinder binder, String yaml) throws Exception {
        SnakeReader reader = binder.createReader(yaml);
        reader.startDocument();
        return reader;
    }
}
