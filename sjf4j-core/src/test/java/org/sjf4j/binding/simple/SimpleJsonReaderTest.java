package org.sjf4j.binding.simple;

import org.junit.jupiter.api.Test;
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
            assertEquals(1L, reader.nextLongValue());
            assertEquals(2, reader.nextIntValue());
            assertEquals((short) 3, reader.nextShortValue());
            assertEquals((byte) 4, reader.nextByteValue());
            assertEquals(5.5d, reader.nextDoubleValue());
            assertEquals(6.5f, reader.nextFloatValue());
            assertEquals(true, reader.nextBooleanValue());
            reader.endArray();
        }
    }

    @Test
    void readsCharValuesAndConsumesFullStrings() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(
                "[\"x\",\"\\\\\",\"\\u0041\",\"\\uD83D\\uDE00\",\"multiple\",2]"))) {
            reader.startArray();
            assertEquals('x', reader.nextCharValue());
            assertEquals('\\', reader.nextCharValue());
            assertEquals('A', reader.nextCharValue());
            assertEquals('\uD83D', reader.nextCharValue());
            assertEquals('m', reader.nextCharValue());
            assertEquals(2, reader.nextIntValue());
            reader.endArray();
        }
    }

    @Test
    void rejectsEmptyAndMalformedCharStringsAtValuePath() throws Exception {
        assertThrows(BindingException.class,
                () -> new SimpleJsonReader(new StringReader("\"\"")).nextCharValue());

        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"a\":[{\"b\":\"x\\uD83D\"}]}"))) {
            reader.startObject(); reader.nextName(); reader.startArray(); reader.startObject(); reader.nextName();
            BindingException error = assertThrows(BindingException.class, reader::nextCharValue);
            assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr());
        }
    }

    @Test
    void readsNullableBoxedScalars() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[null,null,null,null,null,null,null]"))) {
            reader.startArray();
            assertNull(reader.nextLong());
            assertNull(reader.nextInt());
            assertNull(reader.nextShort());
            assertNull(reader.nextByte());
            assertNull(reader.nextDouble());
            assertNull(reader.nextFloat());
            assertNull(reader.nextBoolean());
            reader.endArray();
        }
    }

    @Test
    void reportsNameTokensAndMatchesNames() throws Exception {
        StreamingReader.NameMatcher matcher = new StreamingReader.NameMatcher() {
            @Override public int size() { return 1; }
            @Override public String name(int index) { return "known"; }
            @Override public int match(String name) { return "known".equals(name) ? 0 : UNKNOWN; }
        };
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"known\":1,\"other\":2}"))) {
            reader.startObject();
            assertEquals(StreamingReader.Token.NAME, reader.peekToken());
            assertEquals(0, reader.nextNameMatch(matcher));
            assertEquals(1, reader.nextIntValue());
            assertEquals(StreamingReader.NameMatcher.UNKNOWN, reader.nextNameMatch(matcher, 0));
            assertEquals(2, reader.nextIntValue());
            assertEquals(StreamingReader.NameMatcher.END_OBJECT, reader.nextNameMatch(matcher));
            reader.endObject();
        }
    }

    @Test
    void classifiesRootTokensAndReadsAllJsonEscapes() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(
                " { \"name\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0041\\uD83D\\uDE00\", \"n\":123, \"b\": true, \"nil\": null } "))) {
            assertEquals(StreamingReader.Token.START_OBJECT, reader.peekToken());
            reader.startObject();
            assertEquals(StreamingReader.Token.NAME, reader.peekToken());
            assertEquals("name", reader.nextName());
            assertEquals("\"\\/\b\f\n\r\tA😀", reader.nextString());
            assertEquals("n", reader.nextName());
            assertEquals(123, reader.nextNumber().intValue());
            assertEquals("b", reader.nextName());
            assertTrue(reader.nextBooleanValue());
            assertEquals("nil", reader.nextName());
            reader.nextNull();
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
            assertEquals(Long.MIN_VALUE, reader.nextLongValue());
            assertEquals(Long.MAX_VALUE, reader.nextLongValue());
            assertEquals(Integer.MIN_VALUE, reader.nextIntValue());
            assertEquals(Integer.MAX_VALUE, reader.nextIntValue());
            assertEquals(Short.MIN_VALUE, reader.nextShortValue());
            assertEquals(Short.MAX_VALUE, reader.nextShortValue());
            assertEquals(Byte.MIN_VALUE, reader.nextByteValue());
            assertEquals(Byte.MAX_VALUE, reader.nextByteValue());
            assertEquals(125d, reader.nextDoubleValue());
            assertEquals(new BigInteger("123456789012345678901234567890"), reader.nextBigInteger());
            assertEquals(new BigDecimal("1.20e-3"), reader.nextBigDecimal());
            reader.endArray();
        }
    }

    @Test
    void rejectsNumericOverflowAndInvalidGrammar() {
        String[] invalid = {"01", "-", "1.", "1e", "1e+", "-.1", "1x"};
        for (String value : invalid) {
            assertThrows(BindingException.class, () -> new SimpleJsonReader(new StringReader(value)).nextNumber(), value);
        }
        assertThrows(BindingException.class, () -> new SimpleJsonReader(new StringReader("128")).nextByteValue());
        assertThrows(BindingException.class,
                () -> new SimpleJsonReader(new StringReader("9223372036854775808")).nextLongValue());
        assertThrows(BindingException.class, () -> new SimpleJsonReader(new StringReader("1.0")).nextLongValue());
        assertThrows(BindingException.class, () -> new SimpleJsonReader(new StringReader("1e400")).nextDoubleValue());
    }

    @Test
    void retainsEofAfterRootScalarAndEndDocument() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("42"))) {
            assertEquals(42, reader.nextIntValue());
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
            @Override public int read(char[] cbuf, int off, int len) {
                if (reads++ == 0) return 0;
                if (reads == 2) {
                    cbuf[off] = '1';
                    return 1;
                }
                return -1;
            }
            @Override public void close() { }
        };
        try (SimpleJsonReader reader = new SimpleJsonReader(zeroThenData)) {
            assertEquals(1, reader.nextIntValue());
            reader.endDocument();
        }
    }

    @Test
    void recoversBufferCapacityAndStateAfterNumericErrors() throws Exception {
        String hugeExponent = "1e" + "9".repeat(2048);
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[" + hugeExponent + ",2]"))) {
            reader.startArray();
            assertThrows(BindingException.class, reader::nextDoubleValue);
            assertEquals(2, reader.nextIntValue());
            reader.endArray();
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("1e"))) {
            assertThrows(BindingException.class, reader::nextDoubleValue);
            assertThrows(BindingException.class, reader::nextDoubleValue);
        }
    }

    @Test
    void reportsNestedPathsForDirectAndSkippedErrors() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"a\":[{\"b\":1e}]}"))) {
            reader.startObject(); reader.nextName(); reader.startArray(); reader.startObject(); reader.nextName();
            BindingException error = assertThrows(BindingException.class, reader::nextDoubleValue);
            assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr());
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"a\":[{\"b\":\"\\uD83D\"}]}"))) {
            BindingException error = assertThrows(BindingException.class, reader::skipNext);
            assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr());
            assertThrows(BindingException.class, reader::skipNext);
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
            BindingException error = assertThrows(BindingException.class, reader::skipNext, value);
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
                assertThrows(BindingException.class, reader::skipNext, json);
            }
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[\"\\uD83D\\uDE00\",null]"))) {
            reader.startArray();
            assertEquals("😀", reader.nextString());
            assertTrue(reader.nextIfNull());
            assertFalse(reader.nextIfNull());
            reader.endArray();
        }
        assertThrows(BindingException.class,
                () -> new SimpleJsonReader(new StringReader("\"\\uDE00\"")).nextString());
    }

    @Test
    void skipsEveryValueKind() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(
                "[\"x\",123,true,false,null,{\"a\":[1]},[2,3]]"))) {
            reader.startArray();
            for (int i = 0; i < 7; i++) reader.skipNext();
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void propagatesReaderIoFailures() {
        Reader failing = new Reader() {
            @Override public int read(char[] cbuf, int off, int len) throws IOException { throw new IOException("broken"); }
            @Override public void close() { }
        };
        assertThrows(IOException.class, () -> new SimpleJsonReader(failing).peekToken());
    }

    @Test
    void propagatesReaderIoFailuresMidDocument() throws Exception {
        Reader failing = new Reader() {
            private boolean delivered;
            @Override public int read(char[] cbuf, int off, int len) throws IOException {
                if (delivered) throw new IOException("broken mid-document");
                delivered = true;
                cbuf[off] = '[';
                return 1;
            }
            @Override public void close() { }
        };
        try (SimpleJsonReader reader = new SimpleJsonReader(failing)) {
            reader.startArray();
            assertThrows(IOException.class, reader::peekToken);
        }
    }
}
