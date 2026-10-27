package org.sjf4j.binding.simple;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.exception.BindingException;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimpleJsonParserTest {

    @Test
    void readsPrimitiveValues() throws Exception {
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader("[1,2,3,4,5.5,6.5,true]"));
            try {
                parser.startArray();
                assertEquals(1L, parser.readLongValue());
                assertEquals(2, parser.readIntValue());
                assertEquals((short) 3, parser.readShortValue());
                assertEquals((byte) 4, parser.readByteValue());
                assertEquals(5.5d, parser.readDoubleValue());
                assertEquals(6.5f, parser.readFloatValue());
                assertEquals(true, parser.readBooleanValue());
                parser.endArray();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void readsSmallStringInputAndCloses() throws Exception {
        {
            SimpleJsonParser parser = new SimpleJsonParser("[1,true]");
            try {
                parser.startArray();
                assertEquals(1, parser.readIntValue());
                assertTrue(parser.readBooleanValue());
                parser.endArray();
                parser.endRead();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void closePreventsReadsFromStringAndReaderInputs() throws Exception {
        SimpleJsonParser stringReader = new SimpleJsonParser("null");
        stringReader.release();
        assertThrows(IOException.class, stringReader::startRead);
        assertThrows(IOException.class, stringReader::peekToken);

        SimpleJsonParser readerInput = new SimpleJsonParser(new StringReader("null"));
        readerInput.release();
        assertThrows(IOException.class, readerInput::peekToken);
    }

    @Test
    void closeClearsBufferedInputAndEofState() throws Exception {
        SimpleJsonParser parser = new SimpleJsonParser("[1]");
        parser.startArray();
        parser.release();
        assertThrows(IOException.class, parser::readIntValue);

        SimpleJsonParser eofReader = new SimpleJsonParser("null");
        eofReader.nextIfNull();
        eofReader.endRead();
        eofReader.release();
        assertThrows(IOException.class, eofReader::peekToken);

        SimpleJsonParser readerEof = new SimpleJsonParser(new StringReader("null"));
        readerEof.nextIfNull();
        readerEof.endRead();
        readerEof.release();
        assertThrows(IOException.class, readerEof::peekToken);
    }

    @Test
    void readsCharValuesAndRejectsMultiUnitStrings() throws Exception {
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader(
                "[\"x\",\"\\\\\",\"\\u0041\",\"\\uD83D\\uDE00\",\"multiple\",2]"));
            try {
                parser.startArray();
                assertEquals('x', parser.readCharValue());
                assertEquals('\\', parser.readCharValue());
                assertEquals('A', parser.readCharValue());
                assertThrows(BindingException.class, parser::readCharValue);
                assertThrows(BindingException.class, parser::readCharValue);
                assertEquals(2, parser.readIntValue());
                parser.endArray();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void rejectsEmptyAndMalformedCharStringsAtValuePath() throws Exception {
        assertThrows(BindingException.class,
                () -> new SimpleJsonParser(new StringReader("\"\"")).readCharValue());

        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader("{\"a\":[{\"b\":\"x\\uD83D\"}]}"));
            try {
                parser.startObject();
                parser.nextName();
                parser.startArray();
                parser.startObject();
                parser.nextName();
                BindingException error = assertThrows(BindingException.class, parser::readCharValue);
                assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr());

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void readsNullableBoxedScalars() throws Exception {
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader("[null,null,null,null,null,null,null]"));
            try {
                parser.startArray();
                assertNull(parser.readLong());
                assertNull(parser.readInt());
                assertNull(parser.readShort());
                assertNull(parser.readByte());
                assertNull(parser.readDouble());
                assertNull(parser.readFloat());
                assertNull(parser.readBoolean());
                parser.endArray();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void classifiesRootTokensAndReadsAllJsonEscapes() throws Exception {
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader(
                " { \"name\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0041\\uD83D\\uDE00\", \"n\":123, \"b\": true, \"nil\": null } "));
            try {
                assertEquals(StreamingParser.Token.OBJECT_START, parser.peekToken());
                parser.startObject();
                assertEquals(StreamingParser.Token.NAME, parser.peekToken());
                assertEquals("name", parser.nextName());
                assertEquals("\"\\/\b\f\n\r\tA😀", parser.readString());
                assertEquals("n", parser.nextName());
                assertEquals(123, parser.readNumber().intValue());
                assertEquals("b", parser.nextName());
                assertTrue(parser.readBooleanValue());
                assertEquals("nil", parser.nextName());
                parser.nextIfNull();
                parser.endObject();
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());

            } finally {
                parser.release();
            }
        }
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader(""));
            try {
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());

            } finally {
                parser.release();
            }
        }
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader("x"));
            try {
                assertEquals(StreamingParser.Token.UNKNOWN, parser.peekToken());

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void readsNumericBoundariesAndBigNumbers() throws Exception {
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader(
                "[-9223372036854775808,9223372036854775807,-2147483648,2147483647," +
                        "-32768,32767,-128,127,1.25e2,123456789012345678901234567890,1.20e-3]"));
            try {
                parser.startArray();
                assertEquals(Long.MIN_VALUE, parser.readLongValue());
                assertEquals(Long.MAX_VALUE, parser.readLongValue());
                assertEquals(Integer.MIN_VALUE, parser.readIntValue());
                assertEquals(Integer.MAX_VALUE, parser.readIntValue());
                assertEquals(Short.MIN_VALUE, parser.readShortValue());
                assertEquals(Short.MAX_VALUE, parser.readShortValue());
                assertEquals(Byte.MIN_VALUE, parser.readByteValue());
                assertEquals(Byte.MAX_VALUE, parser.readByteValue());
                assertEquals(125d, parser.readDoubleValue());
                assertEquals(new BigInteger("123456789012345678901234567890"), parser.readBigInteger());
                assertEquals(new BigDecimal("1.20e-3"), parser.readBigDecimal());
                parser.endArray();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void rejectsNumericOverflowAndInvalidGrammar() {
        String[] invalid = {"01", "-", "1.", "1e", "1e+", "-.1", "1x"};
        for (String value : invalid) {
            assertThrows(BindingException.class, () -> new SimpleJsonParser(new StringReader(value)).readNumber(), value);
        }
        assertThrows(BindingException.class, () -> new SimpleJsonParser(new StringReader("128")).readByteValue());
        assertThrows(BindingException.class,
                () -> new SimpleJsonParser(new StringReader("9223372036854775808")).readLongValue());
        assertThrows(BindingException.class, () -> new SimpleJsonParser(new StringReader("1.0")).readLongValue());
        assertThrows(BindingException.class, () -> new SimpleJsonParser(new StringReader("1e400")).readDoubleValue());
    }

    @Test
    void retainsEofAfterRootScalarAndEndDocument() throws Exception {
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader("42"));
            try {
                assertEquals(42, parser.readIntValue());
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());
                parser.endRead();
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());
                parser.endRead();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void toleratesAZeroLengthReaderRead() throws Exception {
        Reader zeroThenData = new Reader() {
            private int reads;
            @Override
            public int read(char[] cbuf, int off, int len) {
                if (reads++ == 0) return 0;
                if (reads == 2) {
                    cbuf[off] = '1';
                    return 1;
                }
                return -1;
            }
            @Override
            public void close() { }
        };
        {
            SimpleJsonParser parser = new SimpleJsonParser(zeroThenData);
            try {
                assertEquals(1, parser.readIntValue());
                parser.endRead();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void recoversBufferCapacityAndStateAfterNumericErrors() throws Exception {
        String hugeExponent = "1e" + "9".repeat(2048);
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader("[" + hugeExponent + ",2]"));
            try {
                parser.startArray();
                assertThrows(BindingException.class, parser::readDoubleValue);
                assertEquals(2, parser.readIntValue());
                parser.endArray();

            } finally {
                parser.release();
            }
        }
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader("1e"));
            try {
                assertThrows(BindingException.class, parser::readDoubleValue);
                assertThrows(BindingException.class, parser::readDoubleValue);

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void reportsNestedPathsForDirectAndSkippedErrors() throws Exception {
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader("{\"a\":[{\"b\":1e}]}"));
            try {
                parser.startObject(); parser.nextName(); parser.startArray(); parser.startObject(); parser.nextName();
                BindingException error = assertThrows(BindingException.class, parser::readDoubleValue);
                assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr());

            } finally {
                parser.release();
            }
        }
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader("{\"a\":[{\"b\":\"\\uD83D\"}]}"));
            try {
                BindingException error = assertThrows(BindingException.class, parser::skipNode);
                assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr());
                assertThrows(BindingException.class, parser::skipNode);

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void skipNextRejectsMalformedNumbersAtTheirNestedPath() {
        for (String value : new String[]{"01", "-", "1.", "1e", "1e+", "1x"}) {
            assertSkippedValueFailsAtB(value);
        }
    }

    @Test
    void skipNextRejectsMalformedStringsEscapesAndSurrogatesAtTheirNestedPath() {
        for (String value : new String[]{"\"unterminated", "\"\\q\"", "\"\\u12xz\"",
                "\"\\uD83D\"", "\"\\uDE00\""}) {
            assertSkippedValueFailsAtB(value);
        }
    }

    @Test
    void skipNextRejectsInvalidAndTruncatedLiteralsAtTheirNestedPath() {
        for (String value : new String[]{"tru", "fals", "nul", "truex", "falsex", "nullx"}) {
            assertSkippedValueFailsAtB(value);
        }
    }

    private static void assertSkippedValueFailsAtB(String value) {
        try {
SimpleJsonParser parser = new SimpleJsonParser(new StringReader("{\"a\":[{\"b\":" + value + "}]}"));
try {
            BindingException error = assertThrows(BindingException.class, parser::skipNode, value);
            assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr(), value);

} finally { parser.release(); }
} catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void reportsContainerPathForNestedStructuralErrors() throws Exception {
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader("{\"a\":{x}}"));
            try {
                parser.startObject();
                parser.nextName();
                parser.startObject();
                BindingException error = assertThrows(BindingException.class, parser::peekToken);
                assertEquals("$.a", error.getPathSegment().rootedPathExpr());

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void enforcesStructuralDelimitersAndEscapes() throws Exception {
        for (String json : new String[]{"[1 2]", "{\"a\":1 \"b\":2}", "[1,]", "{\"a\":1,}"}) {
            {
                SimpleJsonParser parser = new SimpleJsonParser(new StringReader(json));
                try {
                    assertThrows(BindingException.class, parser::skipNode, json);

                } finally {
                    parser.release();
                }
            }
        }
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader("[\"\\uD83D\\uDE00\",null]"));
            try {
                parser.startArray();
                assertEquals("😀", parser.readString());
                assertTrue(parser.nextIfNull());
                assertFalse(parser.nextIfNull());
                parser.endArray();

            } finally {
                parser.release();
            }
        }
        assertThrows(BindingException.class,
                () -> new SimpleJsonParser(new StringReader("\"\\uDE00\"")).readString());
    }

    @Test
    void skipsEveryValueKind() throws Exception {
        {
            SimpleJsonParser parser = new SimpleJsonParser(new StringReader(
                "[\"x\",123,true,false,null,{\"a\":[1]},[2,3]]"));
            try {
                parser.startArray();
                for (int i = 0; i < 7; i++) parser.skipNode();
                parser.endArray();
                parser.endRead();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void propagatesReaderIoFailures() {
        Reader failing = new Reader() {
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                    throw new IOException("broken");
                }
            @Override
            public void close() { }
        };
        assertThrows(IOException.class, () -> new SimpleJsonParser(failing).peekToken());
    }

    @Test
    void propagatesReaderIoFailuresMidDocument() throws Exception {
        Reader failing = new Reader() {
            private boolean delivered;
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                if (delivered) throw new IOException("broken mid-document");
                delivered = true;
                cbuf[off] = '[';
                return 1;
            }
            @Override
            public void close() { }
        };
        {
            SimpleJsonParser parser = new SimpleJsonParser(failing);
            try {
                parser.startArray();
                assertThrows(IOException.class, parser::peekToken);

            } finally {
                parser.release();
            }
        }
    }
}
