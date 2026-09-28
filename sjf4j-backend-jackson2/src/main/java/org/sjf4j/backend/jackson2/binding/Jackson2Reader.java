package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeRegistry;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.BindException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Jackson2Reader implements StreamingReader {

    private final JsonParser parser;
    private JsonToken token;
    private boolean initialized;

    private static final ClassValue<NameMatcher> NAME_MATCHERS =
            new ClassValue<NameMatcher>() {
                @Override
                protected NameMatcher computeValue(Class<?> type) {
                    PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(type);
                    return createNameMatcher(
                            pi.properties.keySet().toArray(new String[0]));
                }
            };

    /** Creates prepared Jackson 2 name-matching metadata. */
    public static NameMatcher createNameMatcher(String... names) {
        return new Jackson2NameMatcher(names);
    }

    public Jackson2Reader(JsonParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    /**
     * Reads the next value as the raw SJF4J object graph.
     *
     * <p>Objects are represented by {@link LinkedHashMap} and arrays by
     * {@link ArrayList}.</p>
    */
    public Object readRawNode() throws IOException {
        return readRawNodeValue();
    }

    @Override
    public Token peekToken() throws IOException {
        if (!initialized) {
            initialized = true;
            token = parser.currentToken();
            if (token == null) {
                token = parser.nextToken();
            }
        }
        return token(token);
    }

    @Override
    public void startObject() throws IOException {
        require(JsonToken.START_OBJECT);
        advance();
    }

    @Override
    public void endObject() throws IOException {
        require(JsonToken.END_OBJECT);
        advance();
    }

    @Override
    public void startArray() throws IOException {
        require(JsonToken.START_ARRAY);
        advance();
    }

    @Override
    public void endArray() throws IOException {
        require(JsonToken.END_ARRAY);
        advance();
    }

    @Override
    public String nextName() throws IOException {
        require(JsonToken.FIELD_NAME);
        String name = parser.currentName();
        advance();
        return name;
    }

    @Override
    public NameMatcher nameMatcher(Class<?> type) {
        return NAME_MATCHERS.get(type);
    }

    @Override
    public int nextNameMatch(NameMatcher matcher) throws IOException {
        return nextNameMatch(matcher, -1);
    }

    @Override
    public int nextNameMatch(NameMatcher matcher, int expectedIndex) throws IOException {
        require(JsonToken.FIELD_NAME);

        String name = parser.currentName();
        int index;

        if (matcher instanceof Jackson2NameMatcher) {
            Jackson2NameMatcher jacksonMatcher = (Jackson2NameMatcher) matcher;
            if (expectedIndex >= 0 &&
                    expectedIndex < jacksonMatcher.size() &&
                    jacksonMatcher.name(expectedIndex).equals(name)) {
                index = expectedIndex;
            } else {
                index = jacksonMatcher.match(name);
            }
        } else {
            index = matcher.match(name);
        }

        advance();
        return index;
    }

    @Override
    public String nextString() throws IOException {
        require(JsonToken.VALUE_STRING);
        String value = parser.getText();
        advance();
        return value;
    }

    @Override
    public Number nextNumber() throws IOException {
        requireNumber();
        Number value = parser.getNumberValue();
        advance();
        return value;
    }

    @Override
    public long nextLongValue() throws IOException {
        requireInteger();
        long value = parser.getLongValue();
        advance();
        return value;
    }

    @Override
    public int nextIntValue() throws IOException {
        requireInteger();
        int value = parser.getIntValue();
        advance();
        return value;
    }

    @Override
    public short nextShortValue() throws IOException {
        requireInteger();
        short value = parser.getShortValue();
        advance();
        return value;
    }

    @Override
    public byte nextByteValue() throws IOException {
        requireInteger();
        byte value = parser.getByteValue();
        advance();
        return value;
    }

    @Override
    public double nextDoubleValue() throws IOException {
        requireNumber();
        double value = parser.getDoubleValue();
        advance();
        return value;
    }

    @Override
    public float nextFloatValue() throws IOException {
        requireNumber();
        float value = parser.getFloatValue();
        advance();
        return value;
    }

    @Override
    public boolean nextBooleanValue() throws IOException {
        ensureToken();
        if (token != JsonToken.VALUE_TRUE && token != JsonToken.VALUE_FALSE) {
            throw expected("boolean");
        }
        boolean value = parser.getBooleanValue();
        advance();
        return value;
    }

    @Override
    public char nextCharValue() throws IOException {
        requireString();
        String value = parser.getText();
        if (value.isEmpty()) {
            throw new BindException("cannot read empty string as char");
        }
        advance();
        return value.charAt(0);
    }

    @Override
    public BigInteger nextBigInteger() throws IOException {
        requireInteger();
        BigInteger value = parser.getBigIntegerValue();
        advance();
        return value;
    }

    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        requireNumber();
        BigDecimal value = parser.getDecimalValue();
        advance();
        return value;
    }

    @Override
    public void nextNull() throws IOException {
        require(JsonToken.VALUE_NULL);
        advance();
    }

    @Override
    public boolean nextIfNull() throws IOException {
        ensureToken();
        if (token != JsonToken.VALUE_NULL) return false;
        advance();
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() throws IOException {
        ensureToken();
        if (token != JsonToken.END_OBJECT) return false;
        advance();
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() throws IOException {
        ensureToken();
        if (token != JsonToken.END_ARRAY) return false;
        advance();
        return true;
    }

    @Override
    public void skipNext() throws IOException {
        ensureToken();
        if (token != JsonToken.START_OBJECT && token != JsonToken.START_ARRAY &&
                token != JsonToken.VALUE_STRING && !isNumber() &&
                token != JsonToken.VALUE_TRUE && token != JsonToken.VALUE_FALSE &&
                token != JsonToken.VALUE_NULL) {
            throw expected("value");
        }
        parser.skipChildren();
        advance();
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

    private Object readRawNodeValue() throws IOException {
        ensureToken();
        if (token == null) {
            throw new BindingException("unexpected token '" + token(token) + "'");
        }
        switch (token) {
            case START_OBJECT:
                return readRawObject();
            case START_ARRAY:
                return readRawArray();
            case VALUE_STRING: {
                String value = parser.getText();
                advance();
                return value;
            }
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT: {
                Number value = parser.getNumberValue();
                advance();
                return value;
            }
            case VALUE_TRUE:
            case VALUE_FALSE: {
                boolean value = parser.getBooleanValue();
                advance();
                return value;
            }
            case VALUE_NULL:
                advance();
                return null;
            default:
                throw new BindingException("unexpected token '" + token(token) + "'");
        }
    }

    private Map<String, Object> readRawObject() throws IOException {
        Map<String, Object> value = new LinkedHashMap<>();
        advance();
        while (token != JsonToken.END_OBJECT) {
            String name = parser.currentName();
            advance();
            value.put(name, readRawNodeValue());
        }
        advance();
        return value;
    }

    private List<Object> readRawArray() throws IOException {
        List<Object> value = new ArrayList<>();
        advance();
        while (token != JsonToken.END_ARRAY) {
            value.add(readRawNodeValue());
        }
        advance();
        return value;
    }

    private void require(JsonToken expected) throws IOException {
        ensureToken();
        if (token != expected) {
            throw expected(expected.name());
        }
    }

    private void requireString() throws IOException {
        ensureToken();
        if (token != JsonToken.VALUE_STRING) {
            throw expected("string");
        }
    }

    private void requireNumber() throws IOException {
        ensureToken();
        if (!isNumber()) {
            throw expected("number");
        }
    }

    private void requireInteger() throws IOException {
        ensureToken();
        if (token != JsonToken.VALUE_NUMBER_INT) {
            throw expected("integer number");
        }
    }

    private boolean isNumber() {
        return token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT;
    }

    private IOException expected(String expected) {
        return new IOException("Expected " + expected + ", but was " + token);
    }

    private void advance() throws IOException {
        initialized = true;
        token = parser.nextToken();
    }

    private void ensureToken() throws IOException {
        if (!initialized) {
            peekToken();
        }
    }

    private static Token token(JsonToken token) {
        if (token == null) {
            return Token.EOF;
        }
        switch (token) {
            case START_OBJECT:
                return Token.START_OBJECT;
            case END_OBJECT:
                return Token.END_OBJECT;
            case FIELD_NAME:
                return Token.NAME;
            case START_ARRAY:
                return Token.START_ARRAY;
            case END_ARRAY:
                return Token.END_ARRAY;
            case VALUE_STRING:
                return Token.STRING;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return Token.NUMBER;
            case VALUE_TRUE:
            case VALUE_FALSE:
                return Token.BOOLEAN;
            case VALUE_NULL:
                return Token.NULL;
            default:
                return Token.UNKNOWN;
        }
    }
}
