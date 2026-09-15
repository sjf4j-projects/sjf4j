package org.sjf4j.facade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.fastjson2.JSONReader;
import com.google.gson.stream.JsonReader;
import jakarta.json.Json;
import org.junit.jupiter.api.Test;
import org.sjf4j.facade.fastjson2.Fastjson2Reader;
import org.sjf4j.facade.gson.GsonReader;
import org.sjf4j.facade.jackson2.Jackson2Reader;
import org.sjf4j.facade.jsonp.JsonpReader;
import org.sjf4j.facade.simple.SimpleJsonReader;
import org.sjf4j.facade.snake.SnakeReader;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingReaderFastPathTest {

    @Test
    void jackson2PrimitiveReads() throws IOException {
        assertPrimitiveReads(new Jackson2Reader(new ObjectMapper().getFactory().createParser(
                "[1,2,3,4,5.5,6.5,true,null]")));
    }

    @Test
    void simplePrimitiveReads() throws IOException {
        assertPrimitiveReadsAfterPeeks(new SimpleJsonReader(new StringReader("[1,2,3,4,5.5,6.5,true,null]")));
    }

    @Test
    void optionalBackendPrimitiveReads() throws IOException {
        assertPrimitiveReads(new Fastjson2Reader(JSONReader.of("[1,2,3,4,5.5,6.5,true,null]")));
        assertPrimitiveReads(new GsonReader(new JsonReader(new StringReader("[1,2,3,4,5.5,6.5,true,null]"))));
        assertPrimitiveReads(new JsonpReader(Json.createParser(new StringReader("[1,2,3,4,5.5,6.5,true,null]"))));
        assertPrimitiveReads(newSnakeReader("[1,2,3,4,5.5,6.5,true,null]"));
        assertEndProbes(new Fastjson2Reader(JSONReader.of("{}")));
        assertEndProbes(new GsonReader(new JsonReader(new StringReader("{}"))));
        assertEndProbes(new JsonpReader(Json.createParser(new StringReader("{}"))));
        assertEndProbes(newSnakeReader("{}"));
        assertArrayEndProbes(new Fastjson2Reader(JSONReader.of("[]")));
        assertArrayEndProbes(new GsonReader(new JsonReader(new StringReader("[]"))));
        assertArrayEndProbes(new JsonpReader(Json.createParser(new StringReader("[]"))));
        assertArrayEndProbes(newSnakeReader("[]"));
    }

    @Test
    void nativePrimitiveMethodsReportInvalidNumericTokens() throws IOException {
        assertNativeNumericFailures(json -> new Jackson2Reader(new ObjectMapper().getFactory().createParser(json)));
        assertNativeNumericFailures(json -> new Fastjson2Reader(JSONReader.of(json)));
        assertNativeNumericFailures(json -> new GsonReader(new JsonReader(new StringReader(json))));
        assertNativeNumericFailures(json -> new JsonpReader(Json.createParser(new StringReader(json))));
    }

    @Test
    void jsonpBooleanFastPathRejectsNonBooleanTokensWithoutConsuming() throws IOException {
        try (StreamingReader reader = new JsonpReader(Json.createParser(new StringReader("[1,null,\"wrong\",false,true]")))) {
            reader.startArray();

            assertThrows(Exception.class, reader::nextBooleanValue);
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            reader.nextIntValue();

            assertThrows(Exception.class, reader::nextBooleanValue);
            assertEquals(StreamingReader.Token.NULL, reader.peekToken());
            assertTrue(reader.nextIfNull());

            assertThrows(Exception.class, reader::nextBooleanValue);
            assertEquals(StreamingReader.Token.STRING, reader.peekToken());
            reader.nextString();

            assertFalse(reader.nextBooleanValue());
            assertTrue(reader.nextBooleanValue());
            assertTrue(reader.nextIfArrayEnd());
        }
    }

    @Test
    void fastjson2PeekCacheIsClearedAfterConditionalAndPrimitiveReads() throws IOException {
        try (StreamingReader reader = new Fastjson2Reader(JSONReader.of("[null,1,2]"))) {
            assertEquals(StreamingReader.Token.START_ARRAY, reader.peekToken());
            reader.startArray();
            assertEquals(StreamingReader.Token.NULL, reader.peekToken());
            assertTrue(reader.nextIfNull());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals(1, reader.nextIntValue());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals(2, reader.nextIntValue());
            assertEquals(StreamingReader.Token.END_ARRAY, reader.peekToken());
        }
    }

    @Test
    void fastjson2ConditionalFastPathsKeepCachedTokenOnFalse() throws IOException {
        try (StreamingReader reader = new Fastjson2Reader(JSONReader.of("[1]"))) {
            reader.startArray();
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertFalse(reader.nextIfNull());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals(1, reader.nextIntValue());

            assertEquals(StreamingReader.Token.END_ARRAY, reader.peekToken());
            assertFalse(reader.nextIfObjectEnd());
            assertEquals(StreamingReader.Token.END_ARRAY, reader.peekToken());
            assertTrue(reader.nextIfArrayEnd());
        }
    }

    @Test
    void fastjson2ObjectValueTransitionsAndScopeGrowth() throws IOException {
        assertObjectValueTransitions(json -> new Fastjson2Reader(JSONReader.of(json)));
        assertNestedObjectScopes(json -> new Fastjson2Reader(JSONReader.of(json)));
    }

    @Test
    void snakeValueTransitionsScopeGrowthAndDocumentBoundaries() throws IOException {
        assertObjectValueTransitions(StreamingReaderFastPathTest::newSnakeReader);
        assertNestedObjectScopes(StreamingReaderFastPathTest::newSnakeReader);

        SnakeReader reader = newSnakeReader("1");
        assertThrows(Exception.class, reader::endDocument);
        reader = newSnakeReader("1");
        reader.skipNext();
        reader.endDocument();
        assertThrows(Exception.class, reader::skipNext);

        reader = newSnakeReader("{}");
        reader.startObject();
        assertThrows(Exception.class, reader::skipNext);
        reader.endObject();
        reader = newSnakeReader("[]");
        reader.startArray();
        assertThrows(Exception.class, reader::skipNext);
        reader.endArray();
        assertThrows(Exception.class, () -> newSnakeReader("[1").skipNext());
        reader = newSnakeReader("--- 1\n--- 2");
        reader.skipNext();
        assertThrows(Exception.class, reader::endDocument);
    }

    @Test
    void fastjson2PrimitiveFailureClearsConsumedPeekCache() throws IOException {
        try (StreamingReader reader = new Fastjson2Reader(JSONReader.of("[\"wrong\",1]"))) {
            assertEquals(StreamingReader.Token.START_ARRAY, reader.peekToken());
            reader.startArray();
            assertEquals(StreamingReader.Token.STRING, reader.peekToken());

            assertThrows(Exception.class, reader::nextIntValue);

            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
        }
    }

    @Test
    void jackson2EofDoesNotCauseNpe() throws IOException {
        try (StreamingReader reader = new Jackson2Reader(new ObjectMapper().getFactory().createParser(""))) {
            assertEofContract(reader);
        }
    }

    @Test
    void documentContractIsConsistentAcrossJsonReaders() throws IOException {
        assertDocumentContract(json -> new SimpleJsonReader(new StringReader(json)));
        assertDocumentContract(json -> new Jackson2Reader(new ObjectMapper().getFactory().createParser(json)));
        assertDocumentContract(json -> new Fastjson2Reader(JSONReader.of(json)));
        assertDocumentContract(json -> new GsonReader(new JsonReader(new StringReader(json))));
        assertDocumentContract(json -> new JsonpReader(Json.createParser(new StringReader(json))));
    }

    @Test
    void snakeDocumentContractKeepsYamlEnvelopeChecks() throws IOException {
        SnakeReader reader = newSnakeReader("[1,{a:[true]}]");
        reader.skipNext();
        reader.endDocument();
        assertEquals(StreamingReader.Token.EOF, reader.peekToken());
        reader = newSnakeReader("{value: 1}");
        reader.startObject();
        assertThrows(Exception.class, reader::skipNext);
        assertObjectKeyState(newSnakeReader("{a: 1, b: {c: 2}, d: [{}]}"));
    }

    @Test
    void simpleSkipRequiresStrictRootValue() throws IOException {
        for (String json : new String[]{",1", ":1", "1,", "1:", "tru", "falsex", "nul",
                "-", "01", "1.", "1e", "1e+", "\"\\x\"", "\"line\nbreak\""}) {
            try (StreamingReader reader = new SimpleJsonReader(new StringReader(json))) {
                reader.startDocument();
                assertThrows(Exception.class, () -> {
                    reader.skipNext();
                    reader.endDocument();
                }, json);
            }
        }
        try (StreamingReader reader = new SimpleJsonReader(new StringReader("{\"" + (char) 1 + "\":1}"))) {
            reader.startDocument();
            assertThrows(Exception.class, reader::skipNext);
        }
    }

    @Test
    void endProbesConsumeOnlyTheirMatchingEndToken() throws IOException {
        assertEndProbes(new Jackson2Reader(new ObjectMapper().getFactory().createParser("{}")));
        assertEndProbes(new SimpleJsonReader(new StringReader("{}")));
        assertArrayEndProbes(new Jackson2Reader(new ObjectMapper().getFactory().createParser("[]")));
        assertArrayEndProbes(new SimpleJsonReader(new StringReader("[]")));
    }

    private static void assertPrimitiveReads(StreamingReader reader) throws IOException {
        try (StreamingReader closeable = reader) {
            assertEquals(StreamingReader.Token.START_ARRAY, closeable.peekToken());
            closeable.startArray();
            assertFalse(closeable.nextIfNull());
            assertEquals(1L, closeable.nextLongValue());
            assertEquals(2, closeable.nextIntValue());
            assertEquals((short) 3, closeable.nextShortValue());
            assertEquals((byte) 4, closeable.nextByteValue());
            assertEquals(5.5D, closeable.nextDoubleValue());
            assertEquals(6.5F, closeable.nextFloatValue());
            assertTrue(closeable.nextBooleanValue());

            assertTrue(closeable.nextIfNull());
            assertFalse(closeable.nextIfNull());
            assertTrue(closeable.nextIfArrayEnd());
        }
    }

    private static void assertPrimitiveReadsAfterPeeks(StreamingReader reader) throws IOException {
        try (StreamingReader closeable = reader) {
            closeable.startArray();
            assertEquals(StreamingReader.Token.NUMBER, closeable.peekToken());
            assertEquals(1L, closeable.nextLongValue());
            assertEquals(StreamingReader.Token.NUMBER, closeable.peekToken());
            assertEquals(2, closeable.nextIntValue());
            assertEquals(StreamingReader.Token.NUMBER, closeable.peekToken());
            assertEquals((short) 3, closeable.nextShortValue());
            assertEquals(StreamingReader.Token.NUMBER, closeable.peekToken());
            assertEquals((byte) 4, closeable.nextByteValue());
            assertEquals(StreamingReader.Token.NUMBER, closeable.peekToken());
            assertEquals(5.5D, closeable.nextDoubleValue());
            assertEquals(StreamingReader.Token.NUMBER, closeable.peekToken());
            assertEquals(6.5F, closeable.nextFloatValue());
            assertEquals(StreamingReader.Token.BOOLEAN, closeable.peekToken());
            assertTrue(closeable.nextBooleanValue());
            assertTrue(closeable.nextIfNull());
            assertTrue(closeable.nextIfArrayEnd());
            assertEquals(StreamingReader.Token.EOF, closeable.peekToken());
        }
    }

    private static void assertEndProbes(StreamingReader reader) throws IOException {
        try (StreamingReader closeable = reader) {
            assertEquals(StreamingReader.Token.START_OBJECT, closeable.peekToken());
            closeable.startObject();
            assertFalse(closeable.nextIfArrayEnd());
            assertTrue(closeable.nextIfObjectEnd());
        }
    }

    private static void assertObjectValueTransitions(ReaderFactory factory) throws IOException {
        try (StreamingReader reader = factory.create("{\"s\":\"text\",\"n\":7,\"b\":true,\"nil\":null,\"ifNil\":null,\"nested\":{\"items\":[1]},\"after\":2}")) {
            reader.startObject();
            assertEquals("s", reader.nextName());
            assertEquals("text", reader.nextString());
            assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
            assertEquals("n", reader.nextName());
            assertEquals(7, reader.nextIntValue());
            assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
            assertEquals("b", reader.nextName());
            assertTrue(reader.nextBooleanValue());
            assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
            assertEquals("nil", reader.nextName());
            reader.nextNull();
            assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
            assertEquals("ifNil", reader.nextName());
            assertTrue(reader.nextIfNull());
            assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
            assertEquals("nested", reader.nextName());
            reader.startObject();
            assertEquals("items", reader.nextName());
            reader.startArray();
            assertEquals(1, reader.nextIntValue());
            reader.endArray();
            reader.endObject();
            assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
            assertEquals("after", reader.nextName());
            assertEquals(2, reader.nextIntValue());
            assertTrue(reader.nextIfObjectEnd());
        }
    }

    private static void assertNestedObjectScopes(ReaderFactory factory) throws IOException {
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < 9; i++) json.append("{\"x\":");
        json.append('1');
        for (int i = 0; i < 9; i++) json.append('}');
        try (StreamingReader reader = factory.create(json.toString())) {
            for (int i = 0; i < 9; i++) {
                reader.startObject();
                assertEquals("x", reader.nextName());
            }
            assertEquals(1, reader.nextIntValue());
            for (int i = 0; i < 9; i++) reader.endObject();
            reader.endDocument();
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
        }
    }

    private static void assertArrayEndProbes(StreamingReader reader) throws IOException {
        try (StreamingReader closeable = reader) {
            assertEquals(StreamingReader.Token.START_ARRAY, closeable.peekToken());
            closeable.startArray();
            assertFalse(closeable.nextIfObjectEnd());
            assertTrue(closeable.nextIfArrayEnd());
        }
    }

    private static void assertNativeNumericFailures(ReaderFactory factory) throws IOException {
        for (PrimitiveRead read : PrimitiveRead.numericValues()) {
            try (StreamingReader reader = factory.create("[\"wrong\"]")) {
                assertEquals(StreamingReader.Token.START_ARRAY, reader.peekToken());
                reader.startArray();
                assertThrows(Exception.class, () -> read.read(reader), read.name());
            }
        }
    }

    private static void assertEofContract(StreamingReader reader) throws IOException {
        assertEquals(StreamingReader.Token.EOF, reader.peekToken());
        assertFalse(reader.nextIfNull());
        assertFalse(reader.nextIfObjectEnd());
        assertFalse(reader.nextIfArrayEnd());
        for (PrimitiveRead read : PrimitiveRead.values()) {
            assertThrows(IOException.class, () -> read.read(reader), read.name());
        }
    }

    private static void assertDocumentContract(ReaderFactory factory) throws IOException {
        for (String json : new String[]{"null", "1", "\"text\"", "[1,{\"a\":[true]}]"}) {
            try (StreamingReader reader = factory.create(json + " \n\t")) {
                reader.startDocument();
                reader.skipNext();
                reader.endDocument();
            }
        }
        for (String json : new String[]{"1 2", "1 garbage", "1,"}) {
            try (StreamingReader reader = factory.create(json)) {
                reader.startDocument();
                assertThrows(Exception.class, () -> {
                    reader.skipNext();
                    reader.endDocument();
                }, json);
            }
        }
        try (StreamingReader reader = factory.create("1")) {
            reader.startDocument();
            assertThrows(Exception.class, reader::endDocument);
        }
        try (StreamingReader reader = factory.create("1")) {
            reader.startDocument();
            reader.skipNext();
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            assertThrows(Exception.class, reader::skipNext);
        }
        try (StreamingReader reader = factory.create("{\"value\":1}")) {
            assertEquals(StreamingReader.Token.START_OBJECT, reader.peekToken());
            reader.startObject();
            assertThrows(Exception.class, reader::skipNext);
            reader.nextName();
            reader.skipNext();
            reader.endObject();
        }
        try (StreamingReader reader = factory.create("{\"a\":1,\"b\":{\"c\":2},\"d\":[{}]}")) {
            assertObjectKeyState(reader);
        }
    }

    private static void assertObjectKeyState(StreamingReader reader) throws IOException {
        assertEquals(StreamingReader.Token.START_OBJECT, reader.peekToken());
        reader.startObject();
        assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
        assertEquals("a", reader.nextName());
        reader.skipNext();
        assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
        assertEquals("b", reader.nextName());
        reader.startObject();
        assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
        assertEquals("c", reader.nextName());
        reader.skipNext();
        reader.endObject();
        assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
        assertEquals("d", reader.nextName());
        reader.startArray();
        reader.startObject();
        reader.endObject();
        reader.endArray();
        reader.endObject();
    }

    private static SnakeReader newSnakeReader(String json) {
        SnakeReader reader = new SnakeReader(new ParserImpl(new StreamReader(new StringReader(json)), new LoaderOptions()));
        reader.startDocument();
        return reader;
    }

    @FunctionalInterface
    private interface ReaderFactory {
        StreamingReader create(String json) throws IOException;
    }

    private enum PrimitiveRead {
        LONG(1L) {
            @Override
            Object read(StreamingReader reader) throws IOException {
                return reader.nextLongValue();
            }
        },
        INT(1) {
            @Override
            Object read(StreamingReader reader) throws IOException {
                return reader.nextIntValue();
            }
        },
        SHORT((short) 1) {
            @Override
            Object read(StreamingReader reader) throws IOException {
                return reader.nextShortValue();
            }
        },
        BYTE((byte) 1) {
            @Override
            Object read(StreamingReader reader) throws IOException {
                return reader.nextByteValue();
            }
        },
        DOUBLE(1D) {
            @Override
            Object read(StreamingReader reader) throws IOException {
                return reader.nextDoubleValue();
            }
        },
        FLOAT(1F) {
            @Override
            Object read(StreamingReader reader) throws IOException {
                return reader.nextFloatValue();
            }
        },
        BOOLEAN(true) {
            @Override
            Object read(StreamingReader reader) throws IOException {
                return reader.nextBooleanValue();
            }
        };

        final Object expected;

        PrimitiveRead(Object expected) {
            this.expected = expected;
        }

        abstract Object read(StreamingReader reader) throws IOException;

        static PrimitiveRead[] numericValues() {
            return new PrimitiveRead[]{LONG, INT, SHORT, BYTE, DOUBLE, FLOAT};
        }
    }
}
