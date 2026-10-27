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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnakeGeneratorTest {

    @Test
    void writesObjectsNamesAndNestedValuesForNativeSnakeYaml() throws Exception {
        SnakeBinder binder = new SnakeBinder();
        StringWriter output = new StringWriter();

        {
            SnakeGenerator generator = binder.createGenerator(output);
            try {
                generator.startWrite();
                generator.startObject();
                generator.writeName(new CompiledName("nullLike"));
                generator.writeStringValue("null");
                generator.writeName("nested");
                generator.startObject();
                generator.writeName("booleanLike");
                generator.writeStringValue("true");
                generator.writeName("values");
                generator.startArray();
                generator.writeStringValue("123");
                generator.writeStringValue("~");
                generator.writeStringValue("hello: world");
                generator.endArray();
                generator.endObject();
                generator.endObject();
                generator.endWrite();

            } finally {
                generator.release();
            }
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

        {
            SnakeGenerator generator = binder.createGenerator(output);
            try {
                generator.startWrite();
                generator.startArray();
                generator.writeStringValue("");
                generator.writeStringValue("null");
                generator.writeStringValue("~");
                generator.writeStringValue("true");
                generator.writeStringValue("123");
                generator.writeStringValue("hello: world");
                generator.writeLongValue(1L);
                generator.writeIntValue(2);
                generator.writeShortValue((short) 3);
                generator.writeByteValue((byte) 4);
                generator.writeDoubleValue(5.5d);
                generator.writeFloatValue(6.5f);
                generator.writeBooleanValue(true);
                generator.writeCharValue('x');
                generator.writeNumberValue(new BigInteger("12345678901234567890"));
                generator.writeNumberValue(new BigDecimal("7.25"));
                generator.writeNull();
                generator.endArray();
                generator.endWrite();

            } finally {
                generator.release();
            }
        }

        {
            SnakeParser parser = parser(binder, output.toString());
            try {
                parser.startArray();
                assertEquals("", parser.readString());
                assertEquals("null", parser.readString());
                assertEquals("~", parser.readString());
                assertEquals("true", parser.readString());
                assertEquals("123", parser.readString());
                assertEquals("hello: world", parser.readString());
                assertEquals(1L, parser.readLongValue());
                assertEquals(2, parser.readIntValue());
                assertEquals((short) 3, parser.readShortValue());
                assertEquals((byte) 4, parser.readByteValue());
                assertEquals(5.5d, parser.readDoubleValue());
                assertEquals(6.5f, parser.readFloatValue());
                assertTrue(parser.readBooleanValue());
                assertEquals('x', parser.readCharValue());
                assertEquals(new BigInteger("12345678901234567890"), parser.readBigInteger());
                assertEquals(new BigDecimal("7.25"), parser.readBigDecimal());
                assertTrue(parser.nextIfNull());
                parser.endArray();
                parser.endRead();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void rejectsNonFiniteJsonNumbers() throws Exception {
        {
            SnakeGenerator generator = new SnakeBinder().createGenerator(new StringWriter());
            try {
                assertThrows(java.io.IOException.class, () -> generator.writeDoubleValue(Double.NaN));
                assertThrows(java.io.IOException.class, () -> generator.writeDoubleValue(Double.POSITIVE_INFINITY));
                assertThrows(java.io.IOException.class, () -> generator.writeFloatValue(Float.NaN));
                assertThrows(java.io.IOException.class, () -> generator.writeFloatValue(Float.NEGATIVE_INFINITY));
                assertThrows(java.io.IOException.class, () -> generator.writeNumberValue(Double.NaN));
                assertThrows(java.io.IOException.class, () -> generator.writeNumberValue(Float.POSITIVE_INFINITY));

            } finally {
                generator.release();
            }
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

    @Test
    void endDocumentFlushesWithoutRedundantCloseFlush() throws Exception {
        final int[] flushes = {0};
        final boolean[] closed = {false};
        StringWriter output = new StringWriter() {
            @Override
            public void flush() {
                flushes[0]++;
            }

            @Override
            public void close() {
                closed[0] = true;
            }
        };

        int flushesAfterDocument;
        {
            SnakeGenerator generator = new SnakeBinder().createGenerator(output);
            try {
                generator.startWrite();
                generator.writeStringValue("value");
                generator.endWrite();
                flushesAfterDocument = flushes[0];
                assertTrue(flushesAfterDocument > 0);

            } finally {
                generator.release();
            }
        }

        assertEquals(flushesAfterDocument, flushes[0]);
        assertFalse(closed[0]);
    }

    private static SnakeParser parser(SnakeBinder binder, String yaml) throws Exception {
        SnakeParser parser = binder.createParser(yaml);
        parser.startRead();
        return parser;
    }
}
