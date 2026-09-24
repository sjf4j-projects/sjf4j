package org.sjf4j.facade.gson;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.sjf4j.JsonType;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.node.Numbers;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.io.EOFException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Streaming reader backed by Gson's {@link JsonReader}.
 */
public final class GsonReader implements StreamingReader {

    private final JsonReader reader;

    /**
     * Creates reader adapter from Gson JsonReader.
     */
    public GsonReader(JsonReader reader) {
        Asserts.notNull(reader, "reader");
        this.reader = reader;
    }

    /**
     * Peeks next token from current reader state.
     */
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
            case NAME:
                return Token.FIELD_NAME;
            default:
                return Token.UNKNOWN;
        }
    }

    @Override
    public void endDocument() throws IOException {
        try {
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Expected end of document");
        } catch (EOFException ignored) {
            // Gson versions that report physical EOF instead of END_DOCUMENT are also complete.
        }
    }

    /**
     * Consumes and enters object scope.
     */
    @Override
    public void startObject() throws IOException {
        reader.beginObject();
    }

    /**
     * Consumes and exits object scope.
     */
    @Override
    public void endObject() throws IOException {
        reader.endObject();
    }

    /**
     * Consumes and enters array scope.
     */
    @Override
    public void startArray() throws IOException {
        reader.beginArray();
    }

    /**
     * Consumes and exits array scope.
     */
    @Override
    public void endArray() throws IOException {
        reader.endArray();
    }

    /**
     * Reads next field name.
     */
    @Override
    public String nextName() throws IOException {
        return reader.nextName();
    }

    /**
     * Reads next scalar as string.
     */
    @Override
    public String nextString() throws IOException {
        return reader.nextString();
    }

    /**
     * Reads next scalar as number.
     */
    @Override
    public Number nextNumber() throws IOException {
        return Numbers.parseNumber(reader.nextString());
    }

    /**
     * Reads next scalar as long.
     */
    @Override
    public Long nextLong() throws IOException {
        return reader.nextLong();
    }

    /**
     * Reads next scalar as int.
     */
    @Override
    public Integer nextInt() throws IOException {
        return reader.nextInt();
    }

    /**
     * Reads next scalar as short.
     */
    @Override
    public Short nextShort() throws IOException {
        return Short.parseShort(reader.nextString());
    }

    /**
     * Reads next scalar as byte.
     */
    @Override
    public Byte nextByte() throws IOException {
        return Byte.parseByte(reader.nextString());
    }

    /**
     * Reads next scalar as double.
     */
    @Override
    public Double nextDouble() throws IOException {
        return reader.nextDouble();
    }

    /**
     * Reads next scalar as float.
     */
    @Override
    public Float nextFloat() throws IOException {
        return Float.parseFloat(reader.nextString());
    }

    /**
     * Reads next scalar as BigInteger.
     */
    @Override
    public BigInteger nextBigInteger() throws IOException {
        return new BigInteger(reader.nextString());
    }

    /**
     * Reads next scalar as BigDecimal.
     */
    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        return new BigDecimal(reader.nextString());
    }

    /**
     * Reads next scalar as boolean.
     */
    @Override
    public Boolean nextBoolean() throws IOException {
        return reader.nextBoolean();
    }

    /**
     * Consumes next null token.
     */
    @Override
    public void nextNull() throws IOException {
        reader.nextNull();
    }

    @Override
    public boolean nextIfNull() throws IOException {
        if (reader.peek() != JsonToken.NULL) return false;
        reader.nextNull();
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() throws IOException {
        if (reader.peek() != JsonToken.END_OBJECT) return false;
        reader.endObject();
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() throws IOException {
        if (reader.peek() != JsonToken.END_ARRAY) return false;
        reader.endArray();
        return true;
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
        return Short.parseShort(reader.nextString());
    }

    @Override
    public byte nextByteValue() throws IOException {
        return Byte.parseByte(reader.nextString());
    }

    @Override
    public double nextDoubleValue() throws IOException {
        return reader.nextDouble();
    }

    @Override
    public float nextFloatValue() throws IOException {
        return Float.parseFloat(reader.nextString());
    }

    @Override
    public boolean nextBooleanValue() throws IOException {
        return reader.nextBoolean();
    }

    /**
     * Closes underlying reader.
     */
    @Override
    public void close() throws IOException {
        reader.close();
    }

    /**
     * Skips next scalar or nested value.
     */
    @Override
    public void skipNext() throws IOException {
        Token token = peekToken();
        if (token.jsonType() == JsonType.UNKNOWN) {
            throw new IOException("Expected value to skip, but was " + token);
        }
        reader.skipValue();
    }

}
