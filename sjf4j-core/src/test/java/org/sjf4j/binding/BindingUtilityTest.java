package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.binding.Backend;

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
        assertEquals("text", values.readString());
        assertEquals(1L, values.readLong());
        assertEquals(2, values.readInt());
        assertEquals((short) 3, values.readShort());
        assertEquals((byte) 4, values.readByte());
        assertEquals(5.5d, values.readDouble());
        assertEquals(6.5f, values.readFloat());
        assertEquals(true, values.readBoolean());

        RecordingReader nulls = new RecordingReader(true, StreamingReader.Token.EOF);
        assertNull(nulls.readString());
        assertNull(nulls.readLong());
        assertNull(nulls.readInt());
        assertNull(nulls.readShort());
        assertNull(nulls.readByte());
        assertNull(nulls.readDouble());
        assertNull(nulls.readFloat());
        assertNull(nulls.readBoolean());
    }

    @Test
    void readerTokenAndDocumentDefaultsUseExpectedSemantics() throws Exception {
        assertEquals(JsonType.OBJECT, StreamingReader.Token.OBJECT_START.jsonType());
        assertEquals(JsonType.ARRAY, StreamingReader.Token.ARRAY_START.jsonType());
        assertEquals(JsonType.STRING, StreamingReader.Token.STRING.jsonType());
        assertEquals(JsonType.NUMBER, StreamingReader.Token.NUMBER.jsonType());
        assertEquals(JsonType.BOOLEAN, StreamingReader.Token.BOOLEAN.jsonType());
        assertEquals(JsonType.NULL, StreamingReader.Token.NULL.jsonType());
        assertEquals(JsonType.UNKNOWN, StreamingReader.Token.EOF.jsonType());
        assertEquals(JsonType.UNKNOWN, StreamingReader.Token.UNKNOWN.jsonType());

        RecordingReader eof = new RecordingReader(false, StreamingReader.Token.EOF);
        eof.startDocument();
        eof.endDocument();

        RecordingReader data = new RecordingReader(false, StreamingReader.Token.STRING);
        assertThrows(IOException.class, data::endDocument);
    }

    private static final class RecordingReader extends StreamingReader {
        private final boolean nullValue;
        private final Token token;

        private RecordingReader(boolean nullValue, Token token) {
            super(Backend.AUTO);
            this.nullValue = nullValue;
            this.token = token;
        }

        public Token peekToken() { return token; }
        public boolean nextIfNull() { return nullValue; }
        public boolean nextIfObjectStart() { return false; }
        public boolean nextIfObjectEnd() { return false; }
        public boolean nextIfArrayStart() { return false; }
        public boolean nextIfArrayEnd() { return false; }
        public void startObject() {} 
        public void endObject() {} 
        public void startArray() {} 
        public void endArray() {}
        public String nextName() { return "name"; }
        @Override
        public String readString() { return nullValue ? null : "text"; }
        @Override
        public Number readNumber() { return nullValue ? null : 1; }
        @Override
        public long readLongValue() { return 1L; }
        public int readIntValue() { return 2; }
        public short readShortValue() { return 3; }
        public byte readByteValue() { return 4; }
        public double readDoubleValue() { return 5.5d; }
        public float readFloatValue() { return 6.5f; }
        public boolean readBooleanValue() { return true; }
        public char readCharValue() { return 'x'; }
        public BigInteger readBigInteger() { return BigInteger.ZERO; }
        public BigDecimal readBigDecimal() { return BigDecimal.ZERO; }
        public void skipNode() {}

        @Override
        public Object readRawNode() throws IOException {
            return null;
        }

        public void close() {}
    }
}
