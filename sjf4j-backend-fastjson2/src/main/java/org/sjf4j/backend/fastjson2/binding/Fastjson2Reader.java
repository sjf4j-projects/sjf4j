package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONReader;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.BindException;
import java.util.Objects;

/** StreamingReader backed directly by a Fastjson2 {@link JSONReader}. */
public final class Fastjson2Reader implements StreamingReader {

    private final JSONReader reader;
    private Token peeked;
    private int[] scopes = new int[8];
    private int depth;
    private boolean objectEndConsumed;

    public Fastjson2Reader(JSONReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    @Override
    public Token peekToken() {
        if (peeked == null) {
            peeked = reader.isEnd() ? Token.EOF
                    : expectsName() && reader.current() == '"' ? Token.NAME : token(reader.current());
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
        clearPeek();
        if (!reader.nextIfObjectStart()) {
            throw expected("START_OBJECT");
        }
        push(1);
    }

    @Override
    public void endObject() {
        clearPeek();
        if (objectEndConsumed) {
            objectEndConsumed = false;
            popValueDone();
            return;
        }
        if (!reader.nextIfObjectEnd()) {
            throw expected("END_OBJECT");
        }
        popValueDone();
    }

    @Override
    public void startArray() {
        clearPeek();
        if (!reader.nextIfArrayStart()) {
            throw expected("START_ARRAY");
        }
        push(0);
    }

    @Override
    public void endArray() {
        clearPeek();
        if (!reader.nextIfArrayEnd()) {
            throw expected("END_ARRAY");
        }
        popValueDone();
    }

    @Override
    public String nextName() {
        clearPeek();
        scopes[depth - 1] = 2;
        return reader.readFieldName();
    }

    @Override
    public String nextString() {
        clearPeek();
        String value = reader.readString();
        valueDone();
        return value;
    }

    @Override
    public Number nextNumber() {
        clearPeek();
        Number value = reader.readNumber();
        valueDone();
        return value;
    }

    @Override
    public long nextLongValue() {
        clearPeek();
        long value = reader.readInt64Value();
        valueDone();
        return value;
    }

    @Override
    public int nextIntValue() {
        clearPeek();
        int value = reader.readInt32Value();
        valueDone();
        return value;
    }

    @Override
    public short nextShortValue() {
        clearPeek();
        short value = reader.readInt16Value();
        valueDone();
        return value;
    }

    @Override
    public byte nextByteValue() {
        clearPeek();
        byte value = reader.readInt8Value();
        valueDone();
        return value;
    }

    @Override
    public double nextDoubleValue() {
        clearPeek();
        double value = reader.readDoubleValue();
        valueDone();
        return value;
    }

    @Override
    public float nextFloatValue() {
        clearPeek();
        float value = reader.readFloatValue();
        valueDone();
        return value;
    }

    @Override
    public boolean nextBooleanValue() {
        clearPeek();
        boolean value = reader.readBoolValue();
        valueDone();
        return value;
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
        clearPeek();
        BigInteger value = reader.readBigInteger();
        valueDone();
        return value;
    }

    @Override
    public BigDecimal nextBigDecimal() {
        clearPeek();
        BigDecimal value = reader.readBigDecimal();
        valueDone();
        return value;
    }

    @Override
    public void nextNull() {
        clearPeek();
        reader.readNull();
        valueDone();
    }

    @Override
    public boolean nextIfNull() {
        if (!reader.nextIfNull()) {
            return false;
        }
        clearPeek();
        valueDone();
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() {
        if (!reader.nextIfObjectEnd()) {
            return false;
        }
        clearPeek();
        popValueDone();
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() {
        if (!reader.nextIfArrayEnd()) {
            return false;
        }
        clearPeek();
        popValueDone();
        return true;
    }

    @Override
    public void skipNext() throws IOException {
        if (peekToken().jsonType() == org.sjf4j.JsonType.UNKNOWN) {
            throw new IOException("Expected value");
        }
        clearPeek();
        reader.skipValue();
        valueDone();
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
    private void clearPeek() {
        peeked = null;
    }

    private boolean expectsName() {
        return depth > 0 && scopes[depth - 1] == 1;
    }

    private void push(int scope) {
        if (depth == scopes.length) {
            int[] next = new int[depth << 1];
            System.arraycopy(scopes, 0, next, 0, depth);
            scopes = next;
        }
        scopes[depth++] = scope;
    }

    private void popValueDone() {
        if (depth > 0) {
            depth--;
        }
        valueDone();
    }

    private void valueDone() {
        if (depth > 0 && scopes[depth - 1] == 2) {
            scopes[depth - 1] = 1;
        }
    }
}
