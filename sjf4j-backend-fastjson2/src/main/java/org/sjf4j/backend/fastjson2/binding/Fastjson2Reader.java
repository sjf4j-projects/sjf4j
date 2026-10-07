package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONReader;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.PropertyInfo;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** StreamingReader backed directly by a Fastjson2 {@link JSONReader}. */
public final class Fastjson2Reader extends StreamingReader {

    private final JSONReader reader;
    private Token peeked;

    public Fastjson2Reader(JSONReader reader) {
        super(Backend.FASTJSON2);
        this.reader = Asserts.notNull(reader, "reader");
    }

    @Override
    protected NameMatcher createNameMatcher(PropertyInfo[] writableProperties) {
        return new Fastjson2NameMatcher(writableProperties);
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
            peeked = null;
        }
    }

    @Override
    public Token peekToken() {
        if (peeked == null) {
            peeked = reader.isEnd() ? Token.EOF : _token(reader.current());
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
        peeked = null;
        if (!reader.nextIfObjectStart()) {
            throw _expected("OBJECT_START");
        }
    }

    @Override
    public void endObject() {
        peeked = null;
        if (!reader.nextIfObjectEnd()) {
            throw _expected("OBJECT_END");
        }
    }

    @Override
    public void startArray() {
        peeked = null;
        if (!reader.nextIfArrayStart()) {
            throw _expected("ARRAY_START");
        }
    }

    @Override
    public void endArray() {
        peeked = null;
        if (!reader.nextIfArrayEnd()) {
            throw _expected("ARRAY_END");
        }
    }

    @Override
    public String nextName() {
        if (nextIfObjectEnd()) {
            return null;
        }
        peeked = null;
        return reader.readFieldName();
    }

    @Override
    public int nextNameMatch(NameMatcher matcher) {
        if (nextIfObjectEnd()) {
            return NameMatcher.OBJECT_END;
        }

        long hash = reader.readFieldNameHashCode();
        peeked = null;

        Fastjson2NameMatcher fastMatcher = (Fastjson2NameMatcher) matcher;
        if (fastMatcher.hashSafe()) {
            return fastMatcher.matchHash(hash);
        }

        int index = fastMatcher.matchHash(hash);
        return index == Fastjson2NameMatcher.HASH_COLLISION
                ? matcher.fallback(reader.getFieldName())
                : index;
    }

    @Override
    public String readString() {
        peeked = null;
        return reader.readString();
    }

    @Override
    public Number readNumber() {
        peeked = null;
        return reader.readNumber();
    }

    @Override
    public long readLongValue() {
        peeked = null;
        return reader.readInt64Value();
    }

    @Override
    public int readIntValue() {
        peeked = null;
        return reader.readInt32Value();
    }

    @Override
    public short readShortValue() {
        peeked = null;
        return reader.readInt16Value();
    }

    @Override
    public byte readByteValue() {
        peeked = null;
        return reader.readInt8Value();
    }

    @Override
    public double readDoubleValue() {
        peeked = null;
        return reader.readDoubleValue();
    }

    @Override
    public float readFloatValue() {
        peeked = null;
        return reader.readFloatValue();
    }

    @Override
    public boolean readBooleanValue() {
        peeked = null;
        return reader.readBoolValue();
    }

    @Override
    public BigInteger readBigInteger() {
        peeked = null;
        return reader.readBigInteger();
    }

    @Override
    public BigDecimal readBigDecimal() {
        peeked = null;
        return reader.readBigDecimal();
    }

    @Override
    public boolean nextIfNull() {
        if (!reader.nextIfNull()) {
            return false;
        }
        peeked = null;
        return true;
    }

    @Override
    public boolean nextIfObjectStart() {
        if (!reader.nextIfObjectStart()) {
            return false;
        }
        peeked = null;
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() {
        if (!reader.nextIfObjectEnd()) {
            return false;
        }
        peeked = null;
        return true;
    }

    @Override
    public boolean nextIfArrayStart() {
        if (!reader.nextIfArrayStart()) {
            return false;
        }
        peeked = null;
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() {
        if (!reader.nextIfArrayEnd()) {
            return false;
        }
        peeked = null;
        return true;
    }

    @Override
    public void skipNode() throws IOException {
        if (peekToken().jsonType() == JsonType.UNKNOWN) {
            throw new IOException("Expected value");
        }
        peeked = null;
        reader.skipValue();
    }

    @Override
    public void close() {
        reader.close();
    }

    private Object _readRawNode() throws IOException {
        char current = reader.current();
        switch (current) {
            case '{':
                return _readRawObject();
            case '[':
                return _readRawArray();
            case '"':
                return reader.readString();
            case 't':
            case 'f':
                return reader.readBoolValue();
            case 'n':
                reader.readNull();
                return null;
            default:
                if (current == '-' || current >= '0' && current <= '9') {
                    return reader.readNumber();
                }
                Token token = reader.isEnd() ? Token.EOF : _token(current);
                throw new BindingException("unexpected token '" + token + "'");
        }
    }

    private Map<String, Object> _readRawObject() throws IOException {
        if (!reader.nextIfObjectStart()) {
            throw new BindingException("expected token '{', but was " + reader.current());
        }

        Map<String, Object> value = new LinkedHashMap<>();
        while (!reader.nextIfObjectEnd()) {
            value.put(reader.readFieldName(), _readRawNode());
        }
        return value;
    }

    private List<Object> _readRawArray() throws IOException {
        if (!reader.nextIfArrayStart()) {
            throw new BindingException("expected token '[', but was " + reader.current());
        }

        List<Object> value = new ArrayList<>();
        while (!reader.nextIfArrayEnd()) {
            value.add(_readRawNode());
        }
        return value;
    }

    private static Token _token(char ch) {
        switch (ch) {
            case '{':
                return Token.OBJECT_START;
            case '}':
                return Token.OBJECT_END;
            case '[':
                return Token.ARRAY_START;
            case ']':
                return Token.ARRAY_END;
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

    private BindingException _expected(String token) {
        return new BindingException("expected token '" + token + "', but got " + reader.current());
    }
}
