package org.sjf4j.backend.gson.binding;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.Numbers;
import org.sjf4j.util.Asserts;

import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.BindException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GsonReader implements StreamingReader {

    private final JsonReader reader;
    private JsonToken token;
    private boolean initialized;

    /**
     * Wraps a reader exclusively owned by this instance.
     */
    public GsonReader(JsonReader reader) {
        Asserts.notNull(reader, "reader");
        this.reader = reader;
    }

    /**
     * Reads the next value as the raw SJF4J object graph.
     *
     * <p>Objects are represented by {@link LinkedHashMap} and arrays by
     * {@link ArrayList}.</p>
     */
    public Object readRawNode() throws IOException {
        try {
            return _readRawNode();
        } finally {
            initialized = false;
        }
    }

    @Override
    public Token peekToken() throws IOException {
        _ensureToken();
        return _token(token);
    }

    private static Token _token(JsonToken token) {
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
        initialized = false;
    }

    @Override
    public void endObject() throws IOException {
        reader.endObject();
        initialized = false;
    }

    @Override
    public void startArray() throws IOException {
        reader.beginArray();
        initialized = false;
    }

    @Override
    public void endArray() throws IOException {
        reader.endArray();
        initialized = false;
    }

    @Override
    public String nextName() throws IOException {
        String value = reader.nextName();
        initialized = false;
        return value;
    }

    @Override
    public String nextString() throws IOException {
        String value = reader.nextString();
        initialized = false;
        return value;
    }

    @Override
    public Number nextNumber() throws IOException {
        String value = reader.nextString();
        initialized = false;
        return Numbers.parseNumber(value);
    }

    @Override
    public long nextLongValue() throws IOException {
        long value = reader.nextLong();
        initialized = false;
        return value;
    }

    @Override
    public int nextIntValue() throws IOException {
        int value = reader.nextInt();
        initialized = false;
        return value;
    }

    @Override
    public short nextShortValue() throws IOException {
        int value = reader.nextInt();
        initialized = false;
        return Numbers.toShort(value);
    }

    @Override
    public byte nextByteValue() throws IOException {
        int value = reader.nextInt();
        initialized = false;
        return Numbers.toByte(value);
    }

    @Override
    public double nextDoubleValue() throws IOException {
        double value = reader.nextDouble();
        initialized = false;
        return value;
    }

    @Override
    public float nextFloatValue() throws IOException {
        double value = reader.nextDouble();
        initialized = false;
        return Numbers.toFloat(value);
    }

    @Override
    public boolean nextBooleanValue() throws IOException {
        boolean value = reader.nextBoolean();
        initialized = false;
        return value;
    }

    @Override
    public char nextCharValue() throws IOException {
        String str = reader.nextString();
        initialized = false;
        if (str == null || str.isEmpty()) {
            throw new BindException("cannot read empty string as char");
        }
        return str.charAt(0);
    }

    @Override
    public BigInteger nextBigInteger() throws IOException {
        String value = reader.nextString();
        initialized = false;
        return new BigInteger(value);
    }

    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        String value = reader.nextString();
        initialized = false;
        return new BigDecimal(value);
    }


    @Override
    public void nextNull() throws IOException {
        reader.nextNull();
        initialized = false;
    }

    @Override
    public boolean nextIfNull() throws IOException {
        _ensureToken();
        if (token != JsonToken.NULL) return false;
        reader.nextNull();
        initialized = false;
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() throws IOException {
        _ensureToken();
        if (token != JsonToken.END_OBJECT) return false;
        reader.endObject();
        initialized = false;
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() throws IOException {
        _ensureToken();
        if (token != JsonToken.END_ARRAY) return false;
        reader.endArray();
        initialized = false;
        return true;
    }

    @Override
    public void skipNext() throws IOException {
        reader.skipValue();
        initialized = false;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    private Object _readRawNode() throws IOException {
        JsonToken rawToken;
        try {
            rawToken = reader.peek();
        } catch (EOFException e) {
            rawToken = JsonToken.END_DOCUMENT;
        }
        switch (rawToken) {
            case BEGIN_OBJECT:
                return _readRawObject();
            case BEGIN_ARRAY:
                return _readRawArray();
            case STRING: {
                String value = reader.nextString();
                return value;
            }
            case NUMBER: {
                Number value = Numbers.parseNumber(reader.nextString());
                return value;
            }
            case BOOLEAN: {
                boolean value = reader.nextBoolean();
                return value;
            }
            case NULL:
                reader.nextNull();
                return null;
            default:
                throw new BindingException("unexpected token '" + _token(rawToken) + "'");
        }
    }

    private Map<String, Object> _readRawObject() throws IOException {
        Map<String, Object> value = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            value.put(name, _readRawNode());
        }
        reader.endObject();
        return value;
    }

    private List<Object> _readRawArray() throws IOException {
        List<Object> value = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            value.add(_readRawNode());
        }
        reader.endArray();
        return value;
    }


    private void _ensureToken() throws IOException {
        if (!initialized) {
            initialized = true;
            try {
                token = reader.peek();
            } catch (EOFException e) {
                token = JsonToken.END_DOCUMENT;
            }
        }
    }

}
