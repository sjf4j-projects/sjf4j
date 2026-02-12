package org.sjf4j.facade.gson;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.node.Numbers;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public class GsonReader implements StreamingReader {

    private final JsonReader reader;

    public GsonReader(JsonReader reader) {
        if (reader == null) throw new IllegalArgumentException("Reader must not be null");
        this.reader = reader;
    }

    @Override
    public Token peekToken() throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT:
                return Token.START_OBJECT;
            case END_OBJECT:
                return Token.END_OBJECT;
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
    public long nextLong() throws IOException {
        return reader.nextLong();
    }
    @Override
    public int nextInt() throws IOException {
        return reader.nextInt();
    }
    @Override
    public short nextShort() throws IOException {
        return Short.parseShort(reader.nextString());
    }
    @Override
    public byte nextByte() throws IOException {
        return Byte.parseByte(reader.nextString());
    }
    @Override
    public double nextDouble() throws IOException {
        return reader.nextDouble();
    }
    @Override
    public float nextFloat() throws IOException {
        return Float.parseFloat(reader.nextString());
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
    public boolean nextBoolean() throws IOException {
        return reader.nextBoolean();
    }

    @Override
    public void nextNull() throws IOException {
        reader.nextNull();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    @Override
    public void nextSkip() throws IOException {
        reader.skipValue();
    }

}
