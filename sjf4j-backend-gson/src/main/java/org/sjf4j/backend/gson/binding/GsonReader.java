package org.sjf4j.backend.gson.binding;

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
    private JsonToken token;
    private boolean initialized;

    /**
     * Wraps a reader exclusively owned by this instance.
     */
    public GsonReader(JsonReader reader) {
        Objects.requireNonNull(reader, "reader");
        this.reader = reader;
    }

    @Override
    public Token peekToken() throws IOException {
        ensureToken();
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
        invalidateToken();
    }

    @Override

    public void endObject() throws IOException {
        reader.endObject();
        invalidateToken();
    }

    @Override

    public void startArray() throws IOException {
        reader.beginArray();
        invalidateToken();
    }

    @Override

    public void endArray() throws IOException {
        reader.endArray();
        invalidateToken();
    }

    @Override

    public String nextName() throws IOException {
        String value = reader.nextName();
        invalidateToken();
        return value;
    }

    @Override

    public String nextString() throws IOException {
        String value = reader.nextString();
        invalidateToken();
        return value;
    }

    @Override

    public Number nextNumber() throws IOException {
        String value = reader.nextString();
        invalidateToken();
        return Numbers.parseNumber(value);
    }

    @Override

    public long nextLongValue() throws IOException {
        long value = reader.nextLong();
        invalidateToken();
        return value;
    }

    @Override

    public int nextIntValue() throws IOException {
        int value = reader.nextInt();
        invalidateToken();
        return value;
    }

    @Override

    public short nextShortValue() throws IOException {
        int value = reader.nextInt();
        invalidateToken();
        return Numbers.toShort(value);
    }

    @Override

    public byte nextByteValue() throws IOException {
        int value = reader.nextInt();
        invalidateToken();
        return Numbers.toByte(value);
    }

    @Override

    public double nextDoubleValue() throws IOException {
        double value = reader.nextDouble();
        invalidateToken();
        return value;
    }

    @Override

    public float nextFloatValue() throws IOException {
        double value = reader.nextDouble();
        invalidateToken();
        return Numbers.toFloat(value);
    }

    @Override

    public boolean nextBooleanValue() throws IOException {
        boolean value = reader.nextBoolean();
        invalidateToken();
        return value;
    }

    @Override

    public char nextCharValue() throws IOException {
        String str = reader.nextString();
        invalidateToken();
        if (str == null || str.isEmpty()) {
            throw new BindException("cannot read empty string as char");
        }
        return str.charAt(0);
    }

    @Override

    public BigInteger nextBigInteger() throws IOException {
        String value = reader.nextString();
        invalidateToken();
        return new BigInteger(value);
    }

    @Override

    public BigDecimal nextBigDecimal() throws IOException {
        String value = reader.nextString();
        invalidateToken();
        return new BigDecimal(value);
    }


    @Override


    public void nextNull() throws IOException {
        reader.nextNull();
        invalidateToken();
    }

    @Override

    public boolean nextIfNull() throws IOException {
        ensureToken();
        if (token != JsonToken.NULL) return false;
        reader.nextNull();
        invalidateToken();
        return true;
    }

    @Override

    public boolean nextIfObjectEnd() throws IOException {
        ensureToken();
        if (token != JsonToken.END_OBJECT) return false;
        reader.endObject();
        invalidateToken();
        return true;
    }

    @Override

    public boolean nextIfArrayEnd() throws IOException {
        ensureToken();
        if (token != JsonToken.END_ARRAY) return false;
        reader.endArray();
        invalidateToken();
        return true;
    }

    @Override

    public void skipNext() throws IOException {
        reader.skipValue();
        invalidateToken();
    }

    @Override

    public void close() throws IOException {
        reader.close();
    }

    private void ensureToken() throws IOException {
        if (!initialized) {
            initialized = true;
            try {
                token = reader.peek();
            } catch (EOFException e) {
                token = JsonToken.END_DOCUMENT;
            }
        }
    }

    private void invalidateToken() {
        initialized = false;
    }
}
