package org.sjf4j.binding.simple;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingReader;
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

class SimpleJsonReaderTest {

    @Test
    void readsPrimitiveValues() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[1,2,3,4,5.5,6.5,true]"))) {
            reader.startArray();
            assertEquals(1L, reader.readLongValue());
            assertEquals(2, reader.readIntValue());
            assertEquals((short) 3, reader.readShortValue());
            assertEquals((byte) 4, reader.readByteValue());
            assertEquals(5.5d, reader.readDoubleValue());
            assertEquals(6.5f, reader.readFloatValue());
            assertEquals(true, reader.readBooleanValue());
            reader.endArray();
        }
    }

    @Test
    void readsSmallStringInputAndCloses() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader("[1,true]")) {
            reader.startArray();
            assertEquals(1, reader.readIntValue());
            assertTrue(reader.readBooleanValue());
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void closePreventsReadsFromStringAndReaderInputs() throws Exception {
        SimpleJsonReader stringReader = new SimpleJsonReader("null");
        stringReader.close();
        assertThrows(IOException.class, stringReader::startDocument);
        assertThrows(IOException.class, stringReader::peekToken);

        SimpleJsonReader readerInput = new SimpleJsonReader(new StringReader("null"));
        readerInput.close();
        assertThrows(IOException.class, readerInput::peekToken);
    }

    @Test
    void closeClearsBufferedInputAndEofState() throws Exception {
        SimpleJsonReader reader = new SimpleJsonReader("[1]");
        reader.startArray();
        reader.close();
        assertThrows(IOException.class, reader::readIntValue);

        SimpleJsonReader eofReader = new SimpleJsonReader("null");
        eofReader.nextIfNull();
        eofReader.endDocument();
        eofReader.close();
        assertThrows(IOException.class, eofReader::peekToken);

        SimpleJsonReader readerEof = new SimpleJsonReader(new StringReader("null"));
        readerEof.nextIfNull();
        readerEof.endDocument();
        readerEof.close();
        assertThrows(IOException.class, readerEof::peekToken);
    }

    @Test
    void readsCharValuesAndRejectsMultiUnitStrings() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(
                "[\"x\",\"\\\\\",\"\\u0041\",\"\\uD83D\\uDE00\",\"multiple\",2]"))) {
            reader.startArray();
            assertEquals('x', reader.readCharValue());
            assertEquals('\\', reader.readCharValue());
            assertEquals('A', reader.readCharValue());
            assertThrows(BindingException.class, reader::readCharValue);
            assertThrows(BindingException.class, reader::readCharValue);
            assertEquals(2, reader.readIntValue());
            reader.endArray();
        }
    }

    @Test
    void rejectsEmptyAndMalformedCharStringsAtValuePath() throws Exception {
        assertThrows(BindingException.class,
                () -> new SimpleJsonReader(new StringReader("\"\"")).readCharValue());

        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"a\":[{\"b\":\"x\\uD83D\"}]}"))) {
            reader.startObject(); 
            reader.nextName(); 
            reader.startArray(); 
            reader.startObject(); 
            reader.nextName();
            BindingException error = assertThrows(BindingException.class, reader::readCharValue);
            assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr());
        }
    }

    @Test
    void readsNullableBoxedScalars() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[null,null,null,null,null,null,null]"))) {
            reader.startArray();
            assertNull(reader.readLong());
            assertNull(reader.readInt());
            assertNull(reader.readShort());
            assertNull(reader.readByte());
            assertNull(reader.readDouble());
            assertNull(reader.readFloat());
            assertNull(reader.readBoolean());
            reader.endArray();
        }
    }

    @Test
    void classifiesRootTokensAndReadsAllJsonEscapes() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(
                " { \"name\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0041\\uD83D\\uDE00\", \"n\":123, \"b\": true, \"nil\": null } "))) {
            assertEquals(StreamingReader.Token.OBJECT_START, reader.peekToken());
            reader.startObject();
            assertEquals(StreamingReader.Token.NAME, reader.peekToken());
            assertEquals("name", reader.nextName());
            assertEquals("\"\\/\b\f\n\r\tA😀", reader.readString());
            assertEquals("n", reader.nextName());
            assertEquals(123, reader.readNumber().intValue());
            assertEquals("b", reader.nextName());
            assertTrue(reader.readBooleanValue());
            assertEquals("nil", reader.nextName());
            reader.nextIfNull();
            reader.endObject();
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(""))) {
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("x"))) {
            assertEquals(StreamingReader.Token.UNKNOWN, reader.peekToken());
        }
    }

    @Test
    void readsNumericBoundariesAndBigNumbers() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(
                "[-9223372036854775808,9223372036854775807,-2147483648,2147483647," +
                        "-32768,32767,-128,127,1.25e2,123456789012345678901234567890,1.20e-3]"))) {
            reader.startArray();
            assertEquals(Long.MIN_VALUE, reader.readLongValue());
            assertEquals(Long.MAX_VALUE, reader.readLongValue());
            assertEquals(Integer.MIN_VALUE, reader.readIntValue());
            assertEquals(Integer.MAX_VALUE, reader.readIntValue());
            assertEquals(Short.MIN_VALUE, reader.readShortValue());
            assertEquals(Short.MAX_VALUE, reader.readShortValue());
            assertEquals(Byte.MIN_VALUE, reader.readByteValue());
            assertEquals(Byte.MAX_VALUE, reader.readByteValue());
            assertEquals(125d, reader.readDoubleValue());
            assertEquals(new BigInteger("123456789012345678901234567890"), reader.readBigInteger());
            assertEquals(new BigDecimal("1.20e-3"), reader.readBigDecimal());
            reader.endArray();
        }
    }

    @Test
    void rejectsNumericOverflowAndInvalidGrammar() {
        String[] invalid = {"01", "-", "1.", "1e", "1e+", "-.1", "1x"};
        for (String value : invalid) {
            assertThrows(BindingException.class, () -> new SimpleJsonReader(new StringReader(value)).readNumber(), value);
        }
        assertThrows(BindingException.class, () -> new SimpleJsonReader(new StringReader("128")).readByteValue());
        assertThrows(BindingException.class,
                () -> new SimpleJsonReader(new StringReader("9223372036854775808")).readLongValue());
        assertThrows(BindingException.class, () -> new SimpleJsonReader(new StringReader("1.0")).readLongValue());
        assertThrows(BindingException.class, () -> new SimpleJsonReader(new StringReader("1e400")).readDoubleValue());
    }

    @Test
    void retainsEofAfterRootScalarAndEndDocument() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("42"))) {
            assertEquals(42, reader.readIntValue());
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            reader.endDocument();
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            reader.endDocument();
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
        try (SimpleJsonReader reader = new SimpleJsonReader(zeroThenData)) {
            assertEquals(1, reader.readIntValue());
            reader.endDocument();
        }
    }

    @Test
    void recoversBufferCapacityAndStateAfterNumericErrors() throws Exception {
        String hugeExponent = "1e" + "9".repeat(2048);
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[" + hugeExponent + ",2]"))) {
            reader.startArray();
            assertThrows(BindingException.class, reader::readDoubleValue);
            assertEquals(2, reader.readIntValue());
            reader.endArray();
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("1e"))) {
            assertThrows(BindingException.class, reader::readDoubleValue);
            assertThrows(BindingException.class, reader::readDoubleValue);
        }
    }

    @Test
    void reportsNestedPathsForDirectAndSkippedErrors() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"a\":[{\"b\":1e}]}"))) {
            reader.startObject(); reader.nextName(); reader.startArray(); reader.startObject(); reader.nextName();
            BindingException error = assertThrows(BindingException.class, reader::readDoubleValue);
            assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr());
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"a\":[{\"b\":\"\\uD83D\"}]}"))) {
            BindingException error = assertThrows(BindingException.class, reader::skipNode);
            assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr());
            assertThrows(BindingException.class, reader::skipNode);
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
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"a\":[{\"b\":" + value + "}]}"))) {
            BindingException error = assertThrows(BindingException.class, reader::skipNode, value);
            assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr(), value);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void reportsContainerPathForNestedStructuralErrors() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"a\":{x}}"))) {
            reader.startObject();
            reader.nextName();
            reader.startObject();
            BindingException error = assertThrows(BindingException.class, reader::peekToken);
            assertEquals("$.a", error.getPathSegment().rootedPathExpr());
        }
    }

    @Test
    void enforcesStructuralDelimitersAndEscapes() throws Exception {
        for (String json : new String[]{"[1 2]", "{\"a\":1 \"b\":2}", "[1,]", "{\"a\":1,}"}) {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(json))) {
                assertThrows(BindingException.class, reader::skipNode, json);
            }
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[\"\\uD83D\\uDE00\",null]"))) {
            reader.startArray();
            assertEquals("😀", reader.readString());
            assertTrue(reader.nextIfNull());
            assertFalse(reader.nextIfNull());
            reader.endArray();
        }
        assertThrows(BindingException.class,
                () -> new SimpleJsonReader(new StringReader("\"\\uDE00\"")).readString());
    }

    @Test
    void skipsEveryValueKind() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(
                "[\"x\",123,true,false,null,{\"a\":[1]},[2,3]]"))) {
            reader.startArray();
            for (int i = 0; i < 7; i++) reader.skipNode();
            reader.endArray();
            reader.endDocument();
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
        assertThrows(IOException.class, () -> new SimpleJsonReader(failing).peekToken());
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
        try (SimpleJsonReader reader = new SimpleJsonReader(failing)) {
            reader.startArray();
            assertThrows(IOException.class, reader::peekToken);
        }
    }
}
