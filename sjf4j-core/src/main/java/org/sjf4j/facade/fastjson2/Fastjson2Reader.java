package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import org.sjf4j.JsonType;
import org.sjf4j.exception.BindingException;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Streaming reader backed by Fastjson2's {@link JSONReader}.
 */
public final class Fastjson2Reader implements StreamingReader {

    private final JSONReader reader;

    /**
     * Creates reader adapter from Fastjson2 JSONReader.
     */
    public Fastjson2Reader(JSONReader reader) {
        Asserts.notNull(reader, "reader");
        this.reader = reader;
    }

    private Token peeked;
    private int[] scopes = new int[8];
    private int depth;

    /**
     * Peeks next token from current JSONReader state.
     */
    @Override
    public Token peekToken() throws IOException {
        if (peeked == null) {
            peeked = reader.isEnd() ? Token.EOF
                    : _expectsName() && reader.current() == '"' ? Token.FIELD_NAME : mappingToken(reader.current());
        }
        return peeked;
    }

    @Override
    public void endDocument() throws IOException {
        if (!reader.isEnd()) throw new IOException("Expected end of document");
    }

    static Token mappingToken(char ch) {
        switch (ch) {
            case '{':
                return StreamingReader.Token.START_OBJECT;
            case '}':
                return StreamingReader.Token.END_OBJECT;
            case '[':
                return StreamingReader.Token.START_ARRAY;
            case ']':
                return StreamingReader.Token.END_ARRAY;
            case '"':
                return StreamingReader.Token.STRING;
            case 't':
            case 'f':
                return StreamingReader.Token.BOOLEAN;
            case 'n':
                return StreamingReader.Token.NULL;
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
                return StreamingReader.Token.NUMBER;
            default:
                return StreamingReader.Token.UNKNOWN;
        }
    }


    /**
     * Consumes and enters object scope.
     */
    @Override
    public void startObject() throws IOException {
        peeked = null;
        if (!reader.nextIfObjectStart()) {
            throw new BindingException("expected token 'START_OBJECT', but got " + reader.current());
        }
        _push(1);
    }

    /**
     * Consumes and exits object scope.
     */
    @Override
    public void endObject() throws IOException {
        peeked = null;
        if (!reader.nextIfObjectEnd()) {
            throw new BindingException("expected token 'END_OBJECT', but got " + reader.current());
        }
        _popValueDone();
    }

    /**
     * Consumes and enters array scope.
     */
    @Override
    public void startArray() throws IOException {
        peeked = null;
        if (!reader.nextIfArrayStart()) {
            throw new BindingException("expected token 'START_ARRAY', but got " + reader.current());
        }
        _push(0);
    }

    /**
     * Consumes and exits array scope.
     */
    @Override
    public void endArray() throws IOException {
        peeked = null;
        if (!reader.nextIfArrayEnd()) {
            throw new BindingException("expected token 'END_ARRAY', but got " + reader.current());
        }
        _popValueDone();
    }

    /**
     * Reads next field name.
     */
    @Override
    public String nextName() throws IOException {
        peeked = null;
        scopes[depth - 1] = 2;
        return reader.readFieldName();
    }

    /**
     * Reads next scalar as string.
     */
    @Override
    public String nextString() throws IOException {
        peeked = null;
        String value = reader.readString();
        _valueDone();
        return value;
    }

    /**
     * Reads next scalar as number.
     */
    @Override
    public Number nextNumber() throws IOException {
        peeked = null;
        Number value = reader.readNumber();
        _valueDone();
        return value;
    }

    /**
     * Reads next scalar as long.
     */
    @Override
    public Long nextLong() throws IOException {
        peeked = null;
        Long value = reader.readInt64Value();
        _valueDone();
        return value;
    }

    /**
     * Reads next scalar as int.
     */
    @Override
    public Integer nextInt() throws IOException {
        peeked = null;
        Integer value = reader.readInt32Value();
        _valueDone();
        return value;
    }

    /**
     * Reads next scalar as short.
     */
    @Override
    public Short nextShort() throws IOException {
        peeked = null;
        Short value = reader.readInt16Value();
        _valueDone();
        return value;
    }

    /**
     * Reads next scalar as byte.
     */
    @Override
    public Byte nextByte() throws IOException {
        peeked = null;
        Byte value = reader.readInt8Value();
        _valueDone();
        return value;
    }

    /**
     * Reads next scalar as double.
     */
    @Override
    public Double nextDouble() throws IOException {
        peeked = null;
        Double value = reader.readDoubleValue();
        _valueDone();
        return value;
    }

    /**
     * Reads next scalar as float.
     */
    @Override
    public Float nextFloat() throws IOException {
        peeked = null;
        Float value = reader.readFloatValue();
        _valueDone();
        return value;
    }

    /**
     * Reads next scalar as BigInteger.
     */
    @Override
    public BigInteger nextBigInteger() throws IOException {
        peeked = null;
        BigInteger value = reader.readBigInteger();
        _valueDone();
        return value;
    }

    /**
     * Reads next scalar as BigDecimal.
     */
    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        peeked = null;
        BigDecimal value = reader.readBigDecimal();
        _valueDone();
        return value;
    }

    /**
     * Reads next scalar as boolean.
     */
    @Override
    public Boolean nextBoolean() throws IOException {
        peeked = null;
        Boolean value = reader.readBoolValue();
        _valueDone();
        return value;
    }

    /**
     * Consumes next null token.
     */
    @Override
    public void nextNull() throws IOException {
        peeked = null;
        reader.readNull();
        _valueDone();
    }

    @Override
    public boolean nextIfNull() throws IOException {
        if (!reader.nextIfNull()) return false;
        peeked = null;
        _valueDone();
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() throws IOException {
        if (!reader.nextIfObjectEnd()) return false;
        peeked = null;
        _popValueDone();
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() throws IOException {
        if (!reader.nextIfArrayEnd()) return false;
        peeked = null;
        _popValueDone();
        return true;
    }

    @Override
    public long nextLongValue() throws IOException {
        peeked = null;
        long value = reader.readInt64Value();
        _valueDone();
        return value;
    }

    @Override
    public int nextIntValue() throws IOException {
        peeked = null;
        int value = reader.readInt32Value();
        _valueDone();
        return value;
    }

    @Override
    public short nextShortValue() throws IOException {
        peeked = null;
        short value = reader.readInt16Value();
        _valueDone();
        return value;
    }

    @Override
    public byte nextByteValue() throws IOException {
        peeked = null;
        byte value = reader.readInt8Value();
        _valueDone();
        return value;
    }

    @Override
    public double nextDoubleValue() throws IOException {
        peeked = null;
        double value = reader.readDoubleValue();
        _valueDone();
        return value;
    }

    @Override
    public float nextFloatValue() throws IOException {
        peeked = null;
        float value = reader.readFloatValue();
        _valueDone();
        return value;
    }

    @Override
    public boolean nextBooleanValue() throws IOException {
        peeked = null;
        boolean value = reader.readBoolValue();
        _valueDone();
        return value;
    }

    /**
     * Closes underlying Fastjson2 reader.
     */
    @Override
    public void close() {
        reader.close();
    }

    /**
     * Skips next value.
     */
    @Override
    public void skipNext() throws IOException {
        Token token = peekToken();
        if (token.jsonType() == JsonType.UNKNOWN) {
            throw new IOException("Expected value to skip, but was " + token);
        }
        peeked = null;
        reader.skipValue();
        _valueDone();
    }

    private boolean _expectsName() {
        return depth > 0 && scopes[depth - 1] == 1;
    }

    private void _push(int scope) {
        if (depth == scopes.length) {
            int[] next = new int[depth << 1];
            System.arraycopy(scopes, 0, next, 0, depth);
            scopes = next;
        }
        scopes[depth++] = scope;
    }

    private void _popValueDone() {
        if (depth > 0) depth--;
        _valueDone();
    }

    private void _valueDone() {
        if (depth > 0 && scopes[depth - 1] == 2) scopes[depth - 1] = 1;
    }

}
