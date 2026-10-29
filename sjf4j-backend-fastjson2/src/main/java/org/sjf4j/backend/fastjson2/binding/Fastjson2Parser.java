package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONReader;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.exception.BindingException;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/** StreamingParser backed directly by a Fastjson2 {@link JSONReader}. */
public final class Fastjson2Parser extends StreamingParser {

    private final JSONReader reader;
    private Token peeked;

    /**
     * Creates a reader around a Fastjson2 JSON cursor.
     * @param reader the Fastjson2 reader
     */
    public Fastjson2Parser(JSONReader reader) {
        super(Backend.FASTJSON2);
        this.reader = Asserts.notNull(reader, "reader");
    }

    /**
     * Provides the native parser for compiled field-name and scalar-value
     * fast paths. Access invalidates the wrapper's cached token so generated
     * native reads cannot leave a stale peek.
     * @return the underlying Fastjson2 reader
     */
    public JSONReader rawReader() {
        // Generated native paths bypass wrapper reads, so invalidate a cached peek.
        peeked = null;
        return reader;
    }

    @Override
    protected NameMatcher createNameMatcher(PropertyInfo[] settableProperties) {
        return new Fastjson2NameMatcher(settableProperties);
    }

    @Override
    protected NameMatcher createNameMatcher(String[] names) {
        return new Fastjson2NameMatcher(names);
    }

    /**
     * Reads the next value as the raw SJF4J object graph.
     *
     * <p>Objects and arrays use Fastjson2's native mutable Map/List
     * containers (JSONObject/JSONArray with the default reader context).
     * These are valid OBNT containers and need no intermediate copy.</p>
     */
    @Override
    public Object readRaw() throws IOException {
        try {
            return _readRaw();
        } finally {
            peeked = null;
        }
    }

    @Override
    public Map<String, Object> readRawObject() throws IOException {
        try {
            // The native object loop is faster than a duplicate field/value
            // dispatcher. Validate the root shape without consuming it.
            if (reader.current() == '{') return reader.readObject();
            if (reader.nextIfNull()) return null;
            throw new BindingException("expected token '{', but was '" + reader.current() + "'");
        } finally {
            peeked = null;
        }
    }

    @Override
    public List<Object> readRawArray() throws IOException {
        try {
            if (reader.current() == '[') return reader.readArray();
            if (reader.nextIfNull()) return null;
            throw new BindingException("expected token '[', but was '" + reader.current() + "'");
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
    public void end() throws IOException {
        if (!reader.isEnd()) {
            throw new IOException("expected end of document");
        }
    }

    @Override
    public void readObjectStart() {
        peeked = null;
        if (!reader.nextIfObjectStart()) {
            throw _expected("OBJECT_START");
        }
    }

    @Override
    public void readObjectEnd() {
        peeked = null;
        if (!reader.nextIfObjectEnd()) {
            throw _expected("OBJECT_END");
        }
    }

    @Override
    public void readArrayStart() {
        peeked = null;
        if (!reader.nextIfArrayStart()) {
            throw _expected("ARRAY_START");
        }
    }

    @Override
    public void readArrayEnd() {
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
    public String currentName() {
        return reader.getFieldName();
    }

    @Override
    public int nextNameMatch(NameMatcher matcher) {
        return nextNameMatch(matcher, -1);
    }

    @Override
    public int nextNameMatch(NameMatcher matcher, int expectedIndex) {
        if (nextIfObjectEnd()) {
            return NameMatcher.OBJECT_END;
        }

        Fastjson2NameMatcher fastMatcher = (Fastjson2NameMatcher) matcher;
        if (fastMatcher.matchesExpectedRaw(reader, expectedIndex)) {
            peeked = null;
            return expectedIndex;
        }
        return nextNameMatchFallback(matcher, fastMatcher, expectedIndex);
    }

    /**
     * Out-of-order and unknown-name handling stays outside the ordered hot
     * method, keeping its expected-name branch small enough for JIT inlining.
     */
    private int nextNameMatchFallback(NameMatcher matcher,
                                      Fastjson2NameMatcher fastMatcher,
                                      int expectedIndex) {
        if (expectedIndex >= 0) {
            int rawIndex = fastMatcher.matchOtherRaw(reader, expectedIndex);
            if (rawIndex >= 0) {
                peeked = null;
                return rawIndex;
            }
        }

        long hash = reader.readFieldNameHashCode();
        peeked = null;
        int index = fastMatcher.matchHash(hash);
        return !fastMatcher.hashSafe() && index == Fastjson2NameMatcher.HASH_COLLISION
                ? matcher.fallback(reader.getFieldName())
                : index;
    }

    @Override
    public String readString() {
        peeked = null;
        return reader.readString();
    }

    @Override
    public UUID readUuid() {
        peeked = null;
        return reader.readUUID();
    }

    @Override
    public LocalDate readLocalDate() {
        peeked = null;
        return reader.readLocalDate();
    }

    @Override
    public LocalTime readLocalTime() {
        peeked = null;
        return reader.readLocalTime();
    }

    @Override
    public LocalDateTime readLocalDateTime() {
        peeked = null;
        // Fastjson2 readLocalDateTime() does not consume JSON null itself.
        return reader.nextIfNull() ? null : reader.readLocalDateTime();
    }

    @Override
    public Instant readInstant() {
        peeked = null;
        return reader.readInstant();
    }

    @Override
    public OffsetDateTime readOffsetDateTime() {
        peeked = null;
        return reader.nextIfNull() ? null : reader.readOffsetDateTime();
    }

    @Override
    public ZonedDateTime readZonedDateTime() {
        peeked = null;
        return reader.readZonedDateTime();
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
            throw new IOException("expected value, but was '" + peekToken() + "'");
        }
        peeked = null;
        reader.skipValue();
    }

    @Override
    public void close() {
        reader.close();
    }

    private Object _readRaw() throws IOException {
        char current = reader.current();
        switch (current) {
            case '{':
                return reader.readObject();
            case '[':
                return reader.readArray();
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
