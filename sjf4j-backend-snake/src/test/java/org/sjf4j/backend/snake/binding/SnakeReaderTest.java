package org.sjf4j.backend.snake.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingReader;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnakeReaderTest {

    @Test
    void readsPrimitiveScalarsAndStructures() throws Exception {
        try (SnakeReader reader = reader("[1,2,3,4,5.5,6.5,true,\"x\",12345678901234567890,7.25,null]")) {
            reader.startArray();
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
            assertTrue(reader.nextIfNull());
            assertTrue(reader.nextIfArrayEnd());
            reader.endDocument();
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
        }

        try (SnakeReader reader = reader("first: text\nnested:\n  items: [1]\nnil: null\n")) {
            reader.startObject();
            assertEquals("first", reader.nextName());
            assertFalse(reader.nextIfNull());
            assertEquals("text", reader.nextString());
            assertEquals("nested", reader.nextName());
            reader.startObject();
            assertEquals("items", reader.nextName());
            reader.startArray();
            assertEquals(1, reader.nextIntValue());
            reader.endArray();
            reader.endObject();
            assertEquals("nil", reader.nextName());
            reader.nextNull();
            assertTrue(reader.nextIfObjectEnd());
            reader.endDocument();
        }
    }

    @Test
    void readsYamlDecimalFormsAndExplicitTags() throws Exception {
        String yaml = "plus: +12\nunderscored: 1_000.5_0\n"
                + "string: !!str null\ninteger: !!int \"12\"\nfloat: !!float \"1.5\"\n";

        try (SnakeReader reader = reader(yaml)) {
            reader.startObject();
            assertEquals("plus", reader.nextName());
            assertEquals(12, reader.nextIntValue());
            assertEquals("underscored", reader.nextName());
            assertEquals(1000.5d, reader.nextDoubleValue());
            assertEquals("string", reader.nextName());
            assertEquals("null", reader.nextString());
            assertEquals("integer", reader.nextName());
            assertEquals(12, reader.nextIntValue());
            assertEquals("float", reader.nextName());
            assertEquals(1.5d, reader.nextDoubleValue());
            reader.endObject();
            reader.endDocument();
        }
    }

    @Test
    void falseProbesLeaveNextValuesConsumable() throws Exception {
        try (SnakeReader reader = reader("[text, 1]")) {
            reader.startArray();
            assertFalse(reader.nextIfNull());
            assertEquals("text", reader.nextString());
            assertFalse(reader.nextIfArrayEnd());
            assertEquals(1, reader.nextIntValue());
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void handlesDeepStructuresAndSkipsNestedValues() throws Exception {
        StringBuilder yaml = new StringBuilder();
        for (int i = 0; i < 12; i++) yaml.append("{x: ");
        yaml.append('1');
        for (int i = 0; i < 12; i++) yaml.append('}');

        try (SnakeReader reader = reader(yaml.toString())) {
            for (int i = 0; i < 12; i++) {
                reader.startObject();
                assertEquals("x", reader.nextName());
            }
            assertEquals(1, reader.nextIntValue());
            for (int i = 0; i < 12; i++) reader.endObject();
            reader.endDocument();
        }

        try (SnakeReader reader = reader("discard: {items: [1, {nested: true}, null]}\nkept: value\n")) {
            reader.startObject();
            assertEquals("discard", reader.nextName());
            reader.skipNext();
            assertEquals("kept", reader.nextName());
            assertEquals("value", reader.nextString());
            reader.endObject();
            reader.endDocument();
        }
    }

    @Test
    void enforcesSingleCompleteDocument() throws Exception {
        try (SnakeReader reader = new SnakeBinder().createReader("1")) {
            reader.startDocument();
            assertThrows(IOException.class, reader::endDocument);
        }
        try (SnakeReader reader = new SnakeBinder().createReader("[1")) {
            reader.startDocument();
            assertThrows(IOException.class, reader::skipNext);
        }
        try (SnakeReader reader = new SnakeBinder().createReader("--- 1\n--- 2")) {
            reader.startDocument();
            reader.skipNext();
            assertThrows(IOException.class, reader::endDocument);
        }
        try (SnakeReader reader = reader("[1,{a: [true]}]")) {
            reader.skipNext();
            reader.endDocument();
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            assertThrows(IOException.class, reader::skipNext);
        }
    }

    @Test
    void rejectsYamlAliases() throws Exception {
        try (SnakeReader reader = reader("first: &value 1\nsecond: *value\n")) {
            reader.startObject();
            assertEquals("first", reader.nextName());
            reader.skipNext();
            assertEquals("second", reader.nextName());
            assertThrows(IOException.class, reader::peekToken);
            assertThrows(IOException.class, reader::skipNext);
        }
    }

    private static SnakeReader reader(String yaml) throws IOException {
        SnakeReader reader = new SnakeBinder().createReader(new StringReader(yaml));
        reader.startDocument();
        return reader;
    }
}
