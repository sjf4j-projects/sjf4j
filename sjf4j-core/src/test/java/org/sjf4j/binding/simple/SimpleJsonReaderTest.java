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

class SimpleJsonReaderTest {

    @Test
    void readsPrimitiveValues() throws Exception {
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader("[1,2,3,4,5.5,6.5,true]"));
try {
            reader.startArray();
            assertEquals(1L, reader.readLongValue());
            assertEquals(2, reader.readIntValue());
            assertEquals((short) 3, reader.readShortValue());
            assertEquals((byte) 4, reader.readByteValue());
            assertEquals(5.5d, reader.readDoubleValue());
            assertEquals(6.5f, reader.readFloatValue());
            assertEquals(true, reader.readBooleanValue());
            reader.endArray();
        
} finally { reader.release(); }
    }

    @Test
    void readsSmallStringInputAndCloses() throws Exception {
        SimpleJsonParser reader = new SimpleJsonParser("[1,true]");
try {
            reader.startArray();
            assertEquals(1, reader.readIntValue());
            assertTrue(reader.readBooleanValue());
            reader.endArray();
            reader.endRead();
        
} finally { reader.release(); }
    }

    @Test
    void closePreventsReadsFromStringAndReaderInputs() throws Exception {
        SimpleJsonParser stringReader = new SimpleJsonParser("null");
        stringReader.close();
        assertThrows(IOException.class, stringReader::startRead);
        assertThrows(IOException.class, stringReader::peekToken);

        SimpleJsonParser readerInput = new SimpleJsonParser(new StringReader("null"));
        readerInput.close();
        assertThrows(IOException.class, readerInput::peekToken);
    }

    @Test
    void closeClearsBufferedInputAndEofState() throws Exception {
        SimpleJsonParser reader = new SimpleJsonParser("[1]");
        reader.startArray();
        reader.close();
        assertThrows(IOException.class, reader::readIntValue);

        SimpleJsonParser eofReader = new SimpleJsonParser("null");
        eofReader.nextIfNull();
        eofReader.endRead();
        eofReader.close();
        assertThrows(IOException.class, eofReader::peekToken);

        SimpleJsonParser readerEof = new SimpleJsonParser(new StringReader("null"));
        readerEof.nextIfNull();
        readerEof.endRead();
        readerEof.close();
        assertThrows(IOException.class, readerEof::peekToken);
    }

    @Test
    void readsCharValuesAndRejectsMultiUnitStrings() throws Exception {
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader(
                "[\"x\",\"\\\\\",\"\\u0041\",\"\\uD83D\\uDE00\",\"multiple\",2]"));
try {
            reader.startArray();
            assertEquals('x', reader.readCharValue());
            assertEquals('\\', reader.readCharValue());
            assertEquals('A', reader.readCharValue());
            assertThrows(BindingException.class, reader::readCharValue);
            assertThrows(BindingException.class, reader::readCharValue);
            assertEquals(2, reader.readIntValue());
            reader.endArray();
        
} finally { reader.release(); }
    }

    @Test
    void rejectsEmptyAndMalformedCharStringsAtValuePath() throws Exception {
        assertThrows(BindingException.class,
                () -> new SimpleJsonParser(new StringReader("\"\"")).readCharValue());

        SimpleJsonParser reader = new SimpleJsonParser(new StringReader("{\"a\":[{\"b\":\"x\\uD83D\"}]}"));
try {
            reader.startObject(); 
            reader.nextName(); 
            reader.startArray(); 
            reader.startObject(); 
            reader.nextName();
            BindingException error = assertThrows(BindingException.class, reader::readCharValue);
            assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr());
        
} finally { reader.release(); }
    }

    @Test
    void readsNullableBoxedScalars() throws Exception {
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader("[null,null,null,null,null,null,null]"));
try {
            reader.startArray();
            assertNull(reader.readLong());
            assertNull(reader.readInt());
            assertNull(reader.readShort());
            assertNull(reader.readByte());
            assertNull(reader.readDouble());
            assertNull(reader.readFloat());
            assertNull(reader.readBoolean());
            reader.endArray();
        
} finally { reader.release(); }
    }

    @Test
    void classifiesRootTokensAndReadsAllJsonEscapes() throws Exception {
        try (SimpleJsonParser reader = new SimpleJsonParser(new StringReader(
                " { \"name\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0041\\uD83D\\uDE00\", \"n\":123, \"b\": true, \"nil\": null } "))) {
            assertEquals(StreamingParser.Token.OBJECT_START, reader.peekToken());
            reader.startObject();
            assertEquals(StreamingParser.Token.NAME, reader.peekToken());
            assertEquals("name", reader.nextName());
            assertEquals("\"\\/\b\f\n\r\tA😀", reader.readString());
            assertEquals("n", reader.nextName());
            assertEquals(123, reader.readNumber().intValue());
            assertEquals("b", reader.nextName());
            assertTrue(reader.readBooleanValue());
            assertEquals("nil", reader.nextName());
            reader.nextIfNull();
            reader.endObject();
            assertEquals(StreamingParser.Token.EOF, reader.peekToken());
        }
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader(""));
try {
            assertEquals(StreamingParser.Token.EOF, reader.peekToken());
        
} finally { reader.release(); }
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader("x"));
try {
            assertEquals(StreamingParser.Token.UNKNOWN, reader.peekToken());
        
} finally { reader.release(); }
    }

    @Test
    void readsNumericBoundariesAndBigNumbers() throws Exception {
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader(
                "[-9223372036854775808,9223372036854775807,-2147483648,2147483647," +
                        "-32768,32767,-128,127,1.25e2,123456789012345678901234567890,1.20e-3]"));
try {
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
        
} finally { reader.release(); }
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
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader("42"));
try {
            assertEquals(42, reader.readIntValue());
            assertEquals(StreamingParser.Token.EOF, reader.peekToken());
            assertEquals(StreamingParser.Token.EOF, reader.peekToken());
            reader.endRead();
            assertEquals(StreamingParser.Token.EOF, reader.peekToken());
            reader.endRead();
        
} finally { reader.release(); }
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
        SimpleJsonParser reader = new SimpleJsonParser(zeroThenData);
try {
            assertEquals(1, reader.readIntValue());
            reader.endRead();
        
} finally { reader.release(); }
    }

    @Test
    void recoversBufferCapacityAndStateAfterNumericErrors() throws Exception {
        String hugeExponent = "1e" + "9".repeat(2048);
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader("[" + hugeExponent + ",2]"));
try {
            reader.startArray();
            assertThrows(BindingException.class, reader::readDoubleValue);
            assertEquals(2, reader.readIntValue());
            reader.endArray();
        
} finally { reader.release(); }
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader("1e"));
try {
            assertThrows(BindingException.class, reader::readDoubleValue);
            assertThrows(BindingException.class, reader::readDoubleValue);
        
} finally { reader.release(); }
    }

    @Test
    void reportsNestedPathsForDirectAndSkippedErrors() throws Exception {
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader("{\"a\":[{\"b\":1e}]}"));
try {
            reader.startObject(); reader.nextName(); reader.startArray(); reader.startObject(); reader.nextName();
            BindingException error = assertThrows(BindingException.class, reader::readDoubleValue);
            assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr());
        
} finally { reader.release(); }
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader("{\"a\":[{\"b\":\"\\uD83D\"}]}"));
try {
            BindingException error = assertThrows(BindingException.class, reader::skipNode);
            assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr());
            assertThrows(BindingException.class, reader::skipNode);
        
} finally { reader.release(); }
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
SimpleJsonParser reader = new SimpleJsonParser(new StringReader("{\"a\":[{\"b\":" + value + "}]}"));
try {
            BindingException error = assertThrows(BindingException.class, reader::skipNode, value);
            assertEquals("$.a[0].b", error.getPathSegment().rootedPathExpr(), value);
        
} finally { reader.release(); }
} catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void reportsContainerPathForNestedStructuralErrors() throws Exception {
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader("{\"a\":{x}}"));
try {
            reader.startObject();
            reader.nextName();
            reader.startObject();
            BindingException error = assertThrows(BindingException.class, reader::peekToken);
            assertEquals("$.a", error.getPathSegment().rootedPathExpr());
        
} finally { reader.release(); }
    }

    @Test
    void enforcesStructuralDelimitersAndEscapes() throws Exception {
        for (String json : new String[]{"[1 2]", "{\"a\":1 \"b\":2}", "[1,]", "{\"a\":1,}"}) {
            SimpleJsonParser reader = new SimpleJsonParser(new StringReader(json));
try {
                assertThrows(BindingException.class, reader::skipNode, json);
            
} finally { reader.release(); }
        }
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader("[\"\\uD83D\\uDE00\",null]"));
try {
            reader.startArray();
            assertEquals("😀", reader.readString());
            assertTrue(reader.nextIfNull());
            assertFalse(reader.nextIfNull());
            reader.endArray();
        
} finally { reader.release(); }
        assertThrows(BindingException.class,
                () -> new SimpleJsonParser(new StringReader("\"\\uDE00\"")).readString());
    }

    @Test
    void skipsEveryValueKind() throws Exception {
        SimpleJsonParser reader = new SimpleJsonParser(new StringReader(
                "[\"x\",123,true,false,null,{\"a\":[1]},[2,3]]"));
try {
            reader.startArray();
            for (int i = 0; i < 7; i++) reader.skipNode();
            reader.endArray();
            reader.endRead();
        
} finally { reader.release(); }
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
        SimpleJsonParser reader = new SimpleJsonParser(failing);
try {
            reader.startArray();
            assertThrows(IOException.class, reader::peekToken);
        
} finally { reader.release(); }
    }
}
