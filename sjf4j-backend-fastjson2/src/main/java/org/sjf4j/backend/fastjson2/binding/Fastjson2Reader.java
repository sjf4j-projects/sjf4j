package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONReader;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.BindException;
import java.util.Objects;

/** StreamingReader backed directly by a Fastjson2 {@link JSONReader}. */
public final class Fastjson2Reader implements StreamingReader {

    private final JSONReader reader;
    private Token peeked;

    public Fastjson2Reader(JSONReader reader) {
        this.reader = Asserts.notNull(reader, "reader");
    }

    @Override
    public Token peekToken() {
        if (peeked == null) {
            peeked = reader.isEnd() ? Token.EOF
                    : token(reader.current());
        }
        return peeked;
    }

    @Override
    public void endDocument() throws IOException {
        if (!reader.isEnd()) {
            throw new IOException("Expected end of document");
        }
    }

    @Override
    public void startObject() {
        peeked = null;
        if (!reader.nextIfObjectStart()) {
            throw expected("START_OBJECT");
        }
    }

    @Override
    public void endObject() {
        peeked = null;
        if (!reader.nextIfObjectEnd()) {
            throw expected("END_OBJECT");
        }
    }

    @Override
    public void startArray() {
        peeked = null;
        if (!reader.nextIfArrayStart()) {
            throw expected("START_ARRAY");
        }
    }

    @Override
    public void endArray() {
        peeked = null;
        if (!reader.nextIfArrayEnd()) {
            throw expected("END_ARRAY");
        }
    }

    @Override
    public String nextName() {
        peeked = null;
        return reader.readFieldName();
    }

    @Override
    public String nextString() {
        peeked = null;
        return reader.readString();
    }

    @Override
    public Number nextNumber() {
        peeked = null;
        return reader.readNumber();
    }

    @Override
    public long nextLongValue() {
        peeked = null;
        return reader.readInt64Value();
    }

    @Override
    public int nextIntValue() {
        peeked = null;
        return reader.readInt32Value();
    }

    @Override
    public short nextShortValue() {
        peeked = null;
        return reader.readInt16Value();
    }

    @Override
    public byte nextByteValue() {
        peeked = null;
        return reader.readInt8Value();
    }

    @Override
    public double nextDoubleValue() {
        peeked = null;
        return reader.readDoubleValue();
    }

    @Override
    public float nextFloatValue() {
        peeked = null;
        return reader.readFloatValue();
    }

    @Override
    public boolean nextBooleanValue() {
        peeked = null;
        return reader.readBoolValue();
    }

    @Override
    public char nextCharValue() throws IOException {
        String value = nextString();
        if (value == null || value.isEmpty()) {
            throw new BindException("cannot read empty string as char");
        }
        return value.charAt(0);
    }

    @Override
    public BigInteger nextBigInteger() {
        peeked = null;
        return reader.readBigInteger();
    }

    @Override
    public BigDecimal nextBigDecimal() {
        peeked = null;
        return reader.readBigDecimal();
    }

    @Override
    public void nextNull() {
        peeked = null;
        reader.readNull();
    }

    @Override
    public boolean nextIfNull() {
        if (!reader.nextIfNull()) {
            return false;
        }
        peeked = null;
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() {
        if (!reader.nextIfObjectEnd()) {
            return false;
        }
        peeked = null;
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() {
        if (!reader.nextIfArrayEnd()) {
            return false;
        }
        peeked = null;
        return true;
    }

    @Override
    public void skipNext() throws IOException {
        if (peekToken().jsonType() == org.sjf4j.JsonType.UNKNOWN) {
            throw new IOException("Expected value");
        }
        peeked = null;
        reader.skipValue();
    }

    @Override
    public void close() {
        reader.close();
    }

    private static Token token(char ch) {
        switch (ch) {
            case '{':
                return Token.START_OBJECT;
            case '}':
                return Token.END_OBJECT;
            case '[':
                return Token.START_ARRAY;
            case ']':
                return Token.END_ARRAY;
            case '"':
                return Token.STRING;
            case 't':
            case 'f':
                return Token.BOOLEAN;
            case 'n':
                return Token.NULL;
            case '-':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                return Token.NUMBER;
            default:
                return Token.UNKNOWN;
        }
    }

    private BindingException expected(String token) {
        return new BindingException("expected token '" + token + "', but got " + reader.current());
    }
}
