package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.binding.Backend;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamingParserDefaultMethodTest {
    @Test
    void readerDefaultsHandleBoxedAndNullableValues() throws Exception {
        RecordingReader values = new RecordingReader(false, StreamingParser.Token.EOF);
        assertEquals("text", values.readString());
        assertEquals(1L, values.readLong());
        assertEquals(2, values.readInt());
        assertEquals((short) 3, values.readShort());
        assertEquals((byte) 4, values.readByte());
        assertEquals(5.5d, values.readDouble());
        assertEquals(6.5f, values.readFloat());
        assertEquals(true, values.readBoolean());

        RecordingReader nulls = new RecordingReader(true, StreamingParser.Token.EOF);
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
        assertEquals(JsonType.OBJECT, StreamingParser.Token.OBJECT_START.jsonType());
        assertEquals(JsonType.ARRAY, StreamingParser.Token.ARRAY_START.jsonType());
        assertEquals(JsonType.STRING, StreamingParser.Token.STRING.jsonType());
        assertEquals(JsonType.NUMBER, StreamingParser.Token.NUMBER.jsonType());
        assertEquals(JsonType.BOOLEAN, StreamingParser.Token.BOOLEAN.jsonType());
        assertEquals(JsonType.NULL, StreamingParser.Token.NULL.jsonType());
        assertEquals(JsonType.UNKNOWN, StreamingParser.Token.EOF.jsonType());
        assertEquals(JsonType.UNKNOWN, StreamingParser.Token.UNKNOWN.jsonType());

        RecordingReader eof = new RecordingReader(false, StreamingParser.Token.EOF);
        eof.start();
        eof.end();
    }

    private static final class RecordingReader extends StreamingParser {
        private final boolean nullValue;
        private final Token token;

        private RecordingReader(boolean nullValue, Token token) {
            super(Backend.AUTO);
            this.nullValue = nullValue;
            this.token = token;
        }

        @Override
        public void end() throws IOException {}
        public Token peekToken() { return token; }
        public boolean nextIfNull() { return nullValue; }
        public boolean nextIfObjectStart() { return false; }
        public boolean nextIfObjectEnd() { return false; }
        public boolean nextIfArrayStart() { return false; }
        public boolean nextIfArrayEnd() { return false; }
        public void readObjectStart() {}
        public void readObjectEnd() {}
        public void readArrayStart() {}
        public void readArrayEnd() {}
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
        public Object readRaw() throws IOException { return null; }
        public void release() {}
    }
}
