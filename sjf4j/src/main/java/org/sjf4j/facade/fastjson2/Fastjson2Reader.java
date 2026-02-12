package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.StreamingReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public class Fastjson2Reader implements StreamingReader {

    private final JSONReader reader;

    public Fastjson2Reader(JSONReader reader) {
        if (reader == null) throw new IllegalArgumentException("JSONReader must not be null");
        this.reader = reader;
    }

    private Token peeked;

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

    @Override
    public void startObject() throws IOException {
        peeked = null;
        if (!reader.nextIfObjectStart()) {
            throw new JsonException("Expected token 'START_OBJECT', but got " + reader.current());
        }
    }

    @Override
    public void endObject() throws IOException {
        peeked = null;
        if (!reader.nextIfObjectEnd()) {
            throw new JsonException("Expected token 'END_OBJECT', but got " + reader.current());
        }
    }

    @Override
    public void startArray() throws IOException {
        peeked = null;
        if (!reader.nextIfArrayStart()) {
            throw new JsonException("Expected token 'START_ARRAY', but got " + reader.current());
        }
    }

    @Override
    public void endArray() throws IOException {
        peeked = null;
        if (!reader.nextIfArrayEnd()) {
            throw new JsonException("Expected token 'END_ARRAY', but got " + reader.current());
        }
    }

    @Override
    public String nextName() throws IOException {
        peeked = null;
        return reader.readFieldName();
    }

    @Override
    public String nextString() throws IOException {
        peeked = null;
        return reader.readString();
    }

    @Override
    public Number nextNumber() throws IOException {
        peeked = null;
        return reader.readNumber();
    }
    @Override
    public long nextLong() throws IOException {
        peeked = null;
        return reader.getInt64Value();
    }
    @Override
    public int nextInt() throws IOException {
        peeked = null;
        return reader.readInt32Value();
    }
    @Override
    public short nextShort() throws IOException {
        peeked = null;
        return reader.readInt16Value();
    }
    @Override
    public byte nextByte() throws IOException {
        peeked = null;
        return reader.readInt8Value();
    }
    @Override
    public double nextDouble() throws IOException {
        peeked = null;
        return reader.readDoubleValue();
    }
    @Override
    public float nextFloat() throws IOException {
        peeked = null;
        return reader.readFloatValue();
    }
    @Override
    public BigInteger nextBigInteger() throws IOException {
        peeked = null;
        return reader.readBigInteger();
    }
    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        peeked = null;
        return reader.readBigDecimal();
    }

    @Override
    public boolean nextBoolean() throws IOException {
        peeked = null;
        return reader.readBoolValue();
    }

    @Override
    public void nextNull() throws IOException {
        peeked = null;
        reader.nextIfNull();
    }

    @Override
    public void close() {
        reader.close();
    }

    @Override
    public void nextSkip() throws IOException {
        peeked = null;
        reader.skipValue();
    }

}
