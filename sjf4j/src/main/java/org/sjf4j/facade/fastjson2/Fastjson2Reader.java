package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.StreamingReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Streaming reader backed by Fastjson2's {@link JSONReader}.
 */
public class Fastjson2Reader implements StreamingReader {

    private final JSONReader reader;

    /**
     * Creates reader adapter from Fastjson2 JSONReader.
     */
    public Fastjson2Reader(JSONReader reader) {
        if (reader == null) throw new IllegalArgumentException("JSONReader must not be null");
        this.reader = reader;
    }

    private Token peeked;

    /**
     * Peeks next token from current JSONReader state.
     */
    @Override
    public Token peekToken() throws IOException {
        if (peeked != null) return peeked;

        switch (reader.current()) {
            case '{':
                peeked = Token.START_OBJECT;
                break;
            case '}':
                peeked = Token.END_OBJECT;
                break;
            case '[':
                peeked = Token.START_ARRAY;
                break;
            case ']':
                peeked = Token.END_ARRAY;
                break;
            case '"':
                peeked = Token.STRING;
                break;
            case 't':
            case 'f':
                peeked = Token.BOOLEAN;
                break;
            case 'n':
                peeked = Token.NULL;
                break;
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
                peeked = Token.NUMBER;
                break;
            default:
                if (reader.isObject()) {
                    peeked = Token.START_OBJECT;
                } else if (reader.current() == '}') {
                    peeked = Token.END_OBJECT;
                } else if (reader.isArray()) {
                    peeked = Token.START_ARRAY;
                } else if (reader.current() == ']') {
                    peeked = Token.END_ARRAY;
                } else if (reader.isString()) {
                    peeked = Token.STRING;
                } else if (reader.isNumber()) {
                    peeked = Token.NUMBER;
                } else if (reader.current() == 't' || reader.current() == 'f') {    // I can do it!
                    peeked = Token.BOOLEAN;
                } else if (reader.isNull()) {
                    peeked = Token.NULL;
                } else {
                    peeked = Token.UNKNOWN;
                }
                break;
        }
        return peeked;
    }

    /**
     * Consumes and enters object scope.
     */
    @Override
    public void startObject() throws IOException {
        peeked = null;
        if (!reader.nextIfObjectStart()) {
            throw new JsonException("Expected token 'START_OBJECT', but got " + reader.current());
        }
    }

    /**
     * Consumes and exits object scope.
     */
    @Override
    public void endObject() throws IOException {
        peeked = null;
        if (!reader.nextIfObjectEnd()) {
            throw new JsonException("Expected token 'END_OBJECT', but got " + reader.current());
        }
    }

    /**
     * Consumes and enters array scope.
     */
    @Override
    public void startArray() throws IOException {
        peeked = null;
        if (!reader.nextIfArrayStart()) {
            throw new JsonException("Expected token 'START_ARRAY', but got " + reader.current());
        }
    }

    /**
     * Consumes and exits array scope.
     */
    @Override
    public void endArray() throws IOException {
        peeked = null;
        if (!reader.nextIfArrayEnd()) {
            throw new JsonException("Expected token 'END_ARRAY', but got " + reader.current());
        }
    }

    /**
     * Reads next field name.
     */
    @Override
    public String nextName() throws IOException {
        peeked = null;
        return reader.readFieldName();
    }

    /**
     * Reads next scalar as string.
     */
    @Override
    public String nextString() throws IOException {
        peeked = null;
        return reader.readString();
    }

    /**
     * Reads next scalar as number.
     */
    @Override
    public Number nextNumber() throws IOException {
        peeked = null;
        return reader.readNumber();
    }

    /**
     * Reads next scalar as long.
     */
    @Override
    public long nextLong() throws IOException {
        peeked = null;
        return reader.readInt64Value();
    }

    /**
     * Reads next scalar as int.
     */
    @Override
    public int nextInt() throws IOException {
        peeked = null;
        return reader.readInt32Value();
    }

    /**
     * Reads next scalar as short.
     */
    @Override
    public short nextShort() throws IOException {
        peeked = null;
        return reader.readInt16Value();
    }

    /**
     * Reads next scalar as byte.
     */
    @Override
    public byte nextByte() throws IOException {
        peeked = null;
        return reader.readInt8Value();
    }

    /**
     * Reads next scalar as double.
     */
    @Override
    public double nextDouble() throws IOException {
        peeked = null;
        return reader.readDoubleValue();
    }

    /**
     * Reads next scalar as float.
     */
    @Override
    public float nextFloat() throws IOException {
        peeked = null;
        return reader.readFloatValue();
    }

    /**
     * Reads next scalar as BigInteger.
     */
    @Override
    public BigInteger nextBigInteger() throws IOException {
        peeked = null;
        return reader.readBigInteger();
    }

    /**
     * Reads next scalar as BigDecimal.
     */
    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        peeked = null;
        return reader.readBigDecimal();
    }

    /**
     * Reads next scalar as boolean.
     */
    @Override
    public boolean nextBoolean() throws IOException {
        peeked = null;
        return reader.readBoolValue();
    }

    /**
     * Consumes next null token.
     */
    @Override
    public void nextNull() throws IOException {
        peeked = null;
        reader.readNull();
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
    public void nextSkip() throws IOException {
        peeked = null;
        reader.skipValue();
    }

}
