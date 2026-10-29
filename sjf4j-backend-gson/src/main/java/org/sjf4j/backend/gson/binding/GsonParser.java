package org.sjf4j.backend.gson.binding;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.exception.BindingException;
import org.sjf4j.util.Numbers;
import org.sjf4j.util.Asserts;

import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** StreamingParser backed directly by a Gson {@link JsonReader}. */
public final class GsonParser extends StreamingParser {

    private final JsonReader reader;

    /**
     * Creates a reader around a Gson JSON cursor.
     * @param reader the Gson reader
     */
    public GsonParser(JsonReader reader) {
        super(Backend.GSON);
        this.reader = Asserts.notNull(reader, "reader");
    }

    @Override
    public void end() throws IOException {
        if (reader.peek() != JsonToken.END_DOCUMENT) {
            throw new IOException("expected end of document");
        }
    }

    @Override
    public Token peekToken() throws IOException {
        try {
            return _token(reader.peek());
        } catch (EOFException e) {
            return Token.EOF;
        }
    }

    @Override
    public boolean nextIfNull() throws IOException {
        if (reader.peek() != JsonToken.NULL) {
            return false;
        }
        reader.nextNull();
        return true;
    }

    @Override
    public boolean nextIfObjectStart() throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            return false;
        }
        reader.beginObject();
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() throws IOException {
        if (reader.peek() != JsonToken.END_OBJECT) {
            return false;
        }
        reader.endObject();
        return true;
    }

    @Override
    public boolean nextIfArrayStart() throws IOException {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            return false;
        }
        reader.beginArray();
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() throws IOException {
        if (reader.peek() != JsonToken.END_ARRAY) {
            return false;
        }
        reader.endArray();
        return true;
    }

    @Override
    public void readObjectStart() throws IOException {
        reader.beginObject();
    }

    @Override
    public void readObjectEnd() throws IOException {
        reader.endObject();
    }

    @Override
    public void readArrayStart() throws IOException {
        reader.beginArray();
    }

    @Override
    public void readArrayEnd() throws IOException {
        reader.endArray();
    }

    @Override
    public String nextName() throws IOException {
        if (!reader.hasNext()) {
            reader.endObject();
            return null;
        }
        return reader.nextName();
    }

    @Override
    public String readString() throws IOException {
        return nextIfNull() ? null : reader.nextString();
    }

    @Override
    public Number readNumber() throws IOException {
        return nextIfNull() ? null : Numbers.parseNumber(reader.nextString());
    }

    @Override
    public long readLongValue() throws IOException {
        return reader.nextLong();
    }

    @Override
    public int readIntValue() throws IOException {
        return reader.nextInt();
    }

    @Override
    public short readShortValue() throws IOException {
        return Numbers.toShort(reader.nextInt());
    }

    @Override
    public byte readByteValue() throws IOException {
        return Numbers.toByte(reader.nextInt());
    }

    @Override
    public double readDoubleValue() throws IOException {
        return reader.nextDouble();
    }

    @Override
    public float readFloatValue() throws IOException {
        return Numbers.toFloat(reader.nextDouble());
    }

    @Override
    public boolean readBooleanValue() throws IOException {
        return reader.nextBoolean();
    }

    @Override
    public BigInteger readBigInteger() throws IOException {
        return nextIfNull() ? null : new BigInteger(reader.nextString());
    }

    @Override
    public BigDecimal readBigDecimal() throws IOException {
        return nextIfNull() ? null : new BigDecimal(reader.nextString());
    }

    @Override
    public void skipNode() throws IOException {
        reader.skipValue();
    }

    @Override
    public Object readRaw() throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT:
                return _readRawObject();
            case BEGIN_ARRAY:
                return _readRawArray();
            case STRING:
                return reader.nextString();
            case NUMBER:
                return Numbers.parseNumber(reader.nextString());
            case BOOLEAN:
                return reader.nextBoolean();
            case NULL:
                reader.nextNull();
                return null;
            default:
                throw new BindingException("unexpected token '" + token + "'");
        }
    }

    @Override
    public Map<String, Object> readRawObject() throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        if (token != JsonToken.BEGIN_OBJECT) {
            throw new BindingException("expected raw object or null, but was '" + token + "'");
        }
        return _readRawObject();
    }

    @Override
    public List<Object> readRawArray() throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        if (token != JsonToken.BEGIN_ARRAY) {
            throw new BindingException("expected raw array or null, but was '" + token + "'");
        }
        return _readRawArray();
    }

    @Override
    public void close() {
        // Gson has no pooled parser resources; the caller owns the input.
    }

    // Hold up to three members locally, then allocate the backing map.
    private Map<String, Object> _readRawObject() throws IOException {
        reader.beginObject();
        String name1 = reader.hasNext() ? reader.nextName() : null;
        if (name1 == null) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>(0);
            reader.endObject();
            return result;
        }
        Object value1 = readRaw();
        String name2 = reader.hasNext() ? reader.nextName() : null;
        if (name2 == null) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>(2);
            result.put(name1, value1);
            reader.endObject();
            return result;
        }
        Object value2 = readRaw();
        String name3 = reader.hasNext() ? reader.nextName() : null;
        if (name3 == null) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>(4);
            result.put(name1, value1);
            result.put(name2, value2);
            reader.endObject();
            return result;
        }
        Object value3 = readRaw();
        String name = reader.hasNext() ? reader.nextName() : null;
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(name == null ? 4 : 16);
        result.put(name1, value1);
        result.put(name2, value2);
        result.put(name3, value3);
        while (name != null) {
            result.put(name, readRaw());
            name = reader.hasNext() ? reader.nextName() : null;
        }
        reader.endObject();
        return result;
    }

    private List<Object> _readRawArray() throws IOException {
        reader.beginArray();
        if (!(reader.hasNext())) {
            ArrayList<Object> result = new ArrayList<>(0);
            reader.endArray();
            return result;
        }
        Object item1 = readRaw();
        if (!(reader.hasNext())) {
            ArrayList<Object> result = new ArrayList<>(1);
            result.add(item1);
            reader.endArray();
            return result;
        }
        Object item2 = readRaw();
        if (!(reader.hasNext())) {
            ArrayList<Object> result = new ArrayList<>(2);
            result.add(item1);
            result.add(item2);
            reader.endArray();
            return result;
        }
        Object item3 = readRaw();
        boolean hasMore = reader.hasNext();
        ArrayList<Object> result = new ArrayList<>(hasMore ? 10 : 3);
        result.add(item1);
        result.add(item2);
        result.add(item3);
        while (hasMore) {
            result.add(readRaw());
            hasMore = reader.hasNext();
        }
        reader.endArray();
        return result;
    }

    private static Token _token(JsonToken token) {
        switch (token) {
            case BEGIN_OBJECT:
                return Token.OBJECT_START;
            case END_OBJECT:
                return Token.OBJECT_END;
            case NAME:
                return Token.NAME;
            case BEGIN_ARRAY:
                return Token.ARRAY_START;
            case END_ARRAY:
                return Token.ARRAY_END;
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
}
