package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BindingUtilityTest {

    @Test
    void formatValidatesAndComparesIdentifiers() {
        Format toml = Format.of("com.example:toml");

        assertEquals("com.example:toml", toml.id());
        assertEquals("com.example:toml", toml.toString());
        assertEquals(toml, toml);
        assertEquals(toml, Format.of("com.example:toml"));
        assertFalse(toml.equals(Format.JSON));
        assertFalse(toml.equals("com.example:toml"));
        assertEquals(toml.hashCode(), Format.of("com.example:toml").hashCode());
        assertThrows(NullPointerException.class, () -> Format.of(null));
        assertThrows(IllegalArgumentException.class, () -> Format.of(""));
        assertThrows(IllegalArgumentException.class, () -> Format.of(" json"));
        assertThrows(IllegalArgumentException.class, () -> Format.of("json "));
    }

    @Test
    void readerDefaultsHandleBoxedAndNullableValues() throws Exception {
        RecordingReader values = new RecordingReader(false, StreamingReader.Token.EOF);
        assertEquals("text", values.nextStringOrNull());
        assertEquals(1L, values.nextLong());
        assertEquals(2, values.nextInt());
        assertEquals((short) 3, values.nextShort());
        assertEquals((byte) 4, values.nextByte());
        assertEquals(5.5d, values.nextDouble());
        assertEquals(6.5f, values.nextFloat());
        assertEquals(true, values.nextBoolean());

        RecordingReader nulls = new RecordingReader(true, StreamingReader.Token.EOF);
        assertNull(nulls.nextStringOrNull());
        assertNull(nulls.nextLong());
        assertNull(nulls.nextInt());
        assertNull(nulls.nextShort());
        assertNull(nulls.nextByte());
        assertNull(nulls.nextDouble());
        assertNull(nulls.nextFloat());
        assertNull(nulls.nextBoolean());
    }

    @Test
    void readerTokenAndDocumentDefaultsUseExpectedSemantics() throws Exception {
        assertEquals(JsonType.OBJECT, StreamingReader.Token.START_OBJECT.jsonType());
        assertEquals(JsonType.ARRAY, StreamingReader.Token.START_ARRAY.jsonType());
        assertEquals(JsonType.STRING, StreamingReader.Token.STRING.jsonType());
        assertEquals(JsonType.NUMBER, StreamingReader.Token.NUMBER.jsonType());
        assertEquals(JsonType.BOOLEAN, StreamingReader.Token.BOOLEAN.jsonType());
        assertEquals(JsonType.NULL, StreamingReader.Token.NULL.jsonType());
        assertEquals(JsonType.UNKNOWN, StreamingReader.Token.EOF.jsonType());
        assertEquals(JsonType.UNKNOWN, StreamingReader.Token.UNKNOWN.jsonType());

        RecordingReader eof = new RecordingReader(false, StreamingReader.Token.EOF);
        eof.startDocument();
        eof.endDocument();
        assertNull(eof.nameMatcher(Object.class));

        RecordingReader data = new RecordingReader(false, StreamingReader.Token.STRING);
        assertThrows(IOException.class, data::endDocument);
    }

    private static final class RecordingReader implements StreamingReader {
        private final boolean nullValue;
        private final Token token;

        private RecordingReader(boolean nullValue, Token token) {
            this.nullValue = nullValue;
            this.token = token;
        }

        public Token peekToken() { return token; }
        public boolean nextIfNull() { return nullValue; }
        public boolean nextIfObjectEnd() { return false; }
        public boolean nextIfArrayEnd() { return false; }
        public void startObject() {} public void endObject() {} public void startArray() {} public void endArray() {}
        public String nextName() { return "name"; }
        public String nextString() { return "text"; }
        public Number nextNumber() { return 0; }
        public long nextLongValue() { return 1L; }
        public int nextIntValue() { return 2; }
        public short nextShortValue() { return 3; }
        public byte nextByteValue() { return 4; }
        public double nextDoubleValue() { return 5.5d; }
        public float nextFloatValue() { return 6.5f; }
        public boolean nextBooleanValue() { return true; }
        public char nextCharValue() { return 'x'; }
        public BigInteger nextBigInteger() { return BigInteger.ZERO; }
        public BigDecimal nextBigDecimal() { return BigDecimal.ZERO; }
        public void nextNull() {} public void skipNext() {} public void close() {}
    }
}
