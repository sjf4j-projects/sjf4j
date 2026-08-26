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
