package org.sjf4j.integration.gson.binding;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.node.Numbers;

import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.BindException;
import java.util.Objects;

public class GsonReader implements StreamingReader {

    private final JsonReader reader;

    public GsonReader(JsonReader reader) {
        Objects.requireNonNull(reader, "reader");
        this.reader = reader;
    }

    @Override
    public Token peekToken() throws IOException {
        JsonToken token;
        try {
            token = reader.peek();
        } catch (EOFException e) {
            return Token.EOF;
        }
        switch (token) {
            case BEGIN_OBJECT:
                return Token.START_OBJECT;
            case END_OBJECT:
                return Token.END_OBJECT;
            case NAME:
                return Token.NAME;
            case BEGIN_ARRAY:
                return Token.START_ARRAY;
            case END_ARRAY:
                return Token.END_ARRAY;
            case STRING:
                return Token.STRING;
            case NUMBER:
                return Token.NUMBER;
            case BOOLEAN:
                return Token.BOOLEAN;
            case NULL:
                return Token.NULL;
            case END_DOCUMENT:
                return Token.EOF;
            default:
                return Token.UNKNOWN;
        }
    }

    @Override
    public void startObject() throws IOException {
        reader.beginObject();
    }

    @Override
    public void endObject() throws IOException {
        reader.endObject();
    }

    @Override
    public void startArray() throws IOException {
        reader.beginArray();
    }

    @Override
    public void endArray() throws IOException {
        reader.endArray();
    }

    @Override
    public String nextName() throws IOException {
        return reader.nextName();
    }

    @Override
    public String nextString() throws IOException {
        return reader.nextString();
    }

    @Override
    public Number nextNumber() throws IOException {
        return Numbers.parseNumber(reader.nextString());
    }

    @Override
    public long nextLongValue() throws IOException {
        return reader.nextLong();
    }

    @Override
    public int nextIntValue() throws IOException {
        return reader.nextInt();
    }

    @Override
    public short nextShortValue() throws IOException {
        return Numbers.toShort(reader.nextInt());
    }

    @Override
    public byte nextByteValue() throws IOException {
        return Numbers.toByte(reader.nextInt());
    }

    @Override
    public double nextDoubleValue() throws IOException {
        return reader.nextDouble();
    }

    @Override
    public float nextFloatValue() throws IOException {
        return Numbers.toFloat(reader.nextDouble());
    }

    @Override
    public boolean nextBooleanValue() throws IOException {
        return reader.nextBoolean();
    }

    @Override
    public char nextCharValue() throws IOException {
        String str = reader.nextString();
        if (str == null || str.isEmpty()) {
            throw new BindException("cannot read empty string as char");
        }
        return str.charAt(0);
    }

    @Override
    public BigInteger nextBigInteger() throws IOException {
        return new BigInteger(reader.nextString());
    }

    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        return new BigDecimal(reader.nextString());
    }


    @Override
    public void nextNull() throws IOException {
        reader.nextNull();
    }

    @Override
    public void skipNext() throws IOException {
        reader.skipValue();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
