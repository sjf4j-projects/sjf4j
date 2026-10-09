package org.sjf4j.backend.snake.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.CompiledName;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnakeWriterTest {

    @Test
    void writesObjectsNamesAndNestedValuesForNativeSnakeYaml() throws Exception {
        SnakeBinder binder = new SnakeBinder();
        StringWriter output = new StringWriter();

        try (SnakeWriter writer = binder.createWriter(output)) {
            writer.startDocument();
            writer.startObject();
            writer.writeName(new CompiledName("nullLike"));
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
            assertEquals("", reader.readString());
            assertEquals("null", reader.readString());
            assertEquals("~", reader.readString());
            assertEquals("true", reader.readString());
            assertEquals("123", reader.readString());
            assertEquals("hello: world", reader.readString());
            assertEquals(1L, reader.readLongValue());
            assertEquals(2, reader.readIntValue());
            assertEquals((short) 3, reader.readShortValue());
            assertEquals((byte) 4, reader.readByteValue());
            assertEquals(5.5d, reader.readDoubleValue());
            assertEquals(6.5f, reader.readFloatValue());
            assertTrue(reader.readBooleanValue());
            assertEquals('x', reader.readCharValue());
            assertEquals(new BigInteger("12345678901234567890"), reader.readBigInteger());
            assertEquals(new BigDecimal("7.25"), reader.readBigDecimal());
            assertTrue(reader.nextIfNull());
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void rejectsNonFiniteJsonNumbers() throws Exception {
        try (SnakeWriter writer = new SnakeBinder().createWriter(new StringWriter())) {
            assertThrows(java.io.IOException.class, () -> writer.writeDoubleValue(Double.NaN));
            assertThrows(java.io.IOException.class, () -> writer.writeDoubleValue(Double.POSITIVE_INFINITY));
            assertThrows(java.io.IOException.class, () -> writer.writeFloatValue(Float.NaN));
            assertThrows(java.io.IOException.class, () -> writer.writeFloatValue(Float.NEGATIVE_INFINITY));
            assertThrows(java.io.IOException.class, () -> writer.writeNumberValue(Double.NaN));
            assertThrows(java.io.IOException.class, () -> writer.writeNumberValue(Float.POSITIVE_INFINITY));
        }
    }

    @Test
    void honorsExplicitDocumentMarkersAndFlowStyle() {
        DumperOptions options = new DumperOptions();
        options.setExplicitStart(true);
        options.setExplicitEnd(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        SnakeBinder binder = new SnakeBinder(new LoaderOptions(), options, org.sjf4j.RuntimeContext.EMPTY);
        String yaml = binder.writeNodeAsString(Map.of("values", List.of(1, 2)));
        assertTrue(yaml.startsWith("---"));
        assertTrue(yaml.contains("[1, 2]"));
        assertTrue(yaml.contains("..."));
    }

    private static SnakeReader reader(SnakeBinder binder, String yaml) throws Exception {
        SnakeReader reader = binder.createReader(yaml);
        reader.startDocument();
        return reader;
    }
}
