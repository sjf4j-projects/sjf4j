package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.BindException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Jackson2Reader implements StreamingReader {

    private final JsonParser parser;

    private static final ClassValue<NameMatcher> NAME_MATCHERS =
            new ClassValue<NameMatcher>() {
                @Override
                protected NameMatcher computeValue(Class<?> type) {
                    PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(type);
                    return createNameMatcher(
                            pi.properties.keySet().toArray(new String[0]));
                }
            };

    /** Creates prepared Jackson 2 name-matching metadata. */
    public static NameMatcher createNameMatcher(String... names) {
        return new Jackson2NameMatcher(names);
    }

    public Jackson2Reader(JsonParser parser) {
        this.parser = Asserts.notNull(parser, "parser");
    }

    @Override
    public Token currentToken() throws IOException {
        return _token(_currentToken());
    }

    @Override
    public void startDocument() throws IOException {
        _currentToken();
    }

    @Override
    public void startObject() throws IOException {
        JsonToken current = _currentToken();
        if (current != JsonToken.START_OBJECT) {
            throw _expected(JsonToken.START_OBJECT.name(), current);
        }
        parser.nextToken();
    }

    @Override
    public void endObject() throws IOException {
        JsonToken current = _currentToken();
        if (current != JsonToken.END_OBJECT) {
            throw _expected(JsonToken.END_OBJECT.name(), current);
        }
        parser.nextToken();
    }

    @Override
    public void startArray() throws IOException {
        JsonToken current = _currentToken();
        if (current != JsonToken.START_ARRAY) {
            throw _expected(JsonToken.START_ARRAY.name(), current);
        }
        parser.nextToken();
    }

    @Override
    public void endArray() throws IOException {
        JsonToken current = _currentToken();
        if (current != JsonToken.END_ARRAY) {
            throw _expected(JsonToken.END_ARRAY.name(), current);
        }
        parser.nextToken();
    }

    @Override
    public String nextName() throws IOException {
        String name = parser.currentName();
        parser.nextToken();
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
        JsonToken current = _currentToken();
        if (current != JsonToken.FIELD_NAME) {
            throw _expected(JsonToken.FIELD_NAME.name(), current);
        }

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

        parser.nextToken();
        return index;
    }

    @Override
    public String nextString() throws IOException {
        String value = parser.getText();
        parser.nextToken();
        return value;
    }

    @Override
    public Number nextNumber() throws IOException {
        Number value = parser.getNumberValue();
        parser.nextToken();
        return value;
    }

    @Override
    public long nextLongValue() throws IOException {
        long value = parser.getLongValue();
        parser.nextToken();
        return value;
    }

    @Override
    public int nextIntValue() throws IOException {
        int value = parser.getIntValue();
        parser.nextToken();
        return value;
    }

    @Override
    public short nextShortValue() throws IOException {
        short value = parser.getShortValue();
        parser.nextToken();
        return value;
    }

    @Override
    public byte nextByteValue() throws IOException {
        byte value = parser.getByteValue();
        parser.nextToken();
        return value;
    }

    @Override
    public double nextDoubleValue() throws IOException {
        double value = parser.getDoubleValue();
        parser.nextToken();
        return value;
    }

    @Override
    public float nextFloatValue() throws IOException {
        float value = parser.getFloatValue();
        parser.nextToken();
        return value;
    }

    @Override
    public boolean nextBooleanValue() throws IOException {
        boolean value = parser.getBooleanValue();
        parser.nextToken();
        return value;
    }

    @Override
    public char nextCharValue() throws IOException {
        String value = parser.getText();
        if (value.isEmpty()) {
            throw new BindException("cannot read empty string as char");
        }
        parser.nextToken();
        return value.charAt(0);
    }

    @Override
    public BigInteger nextBigInteger() throws IOException {
        BigInteger value = parser.getBigIntegerValue();
        parser.nextToken();
        return value;
    }

    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        BigDecimal value = parser.getDecimalValue();
        parser.nextToken();
        return value;
    }

    @Override
    public void nextNull() throws IOException {
        parser.nextToken();
    }

    @Override
    public boolean nextIfNull() throws IOException {
        if (_currentToken() != JsonToken.VALUE_NULL) return false;
        parser.nextToken();
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() throws IOException {
        if (_currentToken() != JsonToken.END_OBJECT) return false;
        parser.nextToken();
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() throws IOException {
        if (_currentToken() != JsonToken.END_ARRAY) return false;
        parser.nextToken();
        return true;
    }

    @Override
    public void skipNext() throws IOException {
        JsonToken current = _currentToken();
        if (current != JsonToken.START_OBJECT && current != JsonToken.START_ARRAY &&
                current != JsonToken.VALUE_STRING &&
                current != JsonToken.VALUE_NUMBER_INT && current != JsonToken.VALUE_NUMBER_FLOAT &&
                current != JsonToken.VALUE_TRUE && current != JsonToken.VALUE_FALSE &&
                current != JsonToken.VALUE_NULL) {
            throw _expected("value", current);
        }
        parser.skipChildren();
        parser.nextToken();
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }


    /**
     * Reads the next value as the raw SJF4J object graph.
     *
     * <p>Objects are represented by {@link LinkedHashMap} and arrays by
     * {@link ArrayList}.</p>
     */
    @Override
    public Object readRawNode() throws IOException {
        Object value = _readRawNode();
        parser.nextToken();
        return value;
    }

    private Object _readRawNode() throws IOException {
        JsonToken current = _currentToken();
        if (current == null) {
            throw new BindingException("unexpected token '" + _token(null) + "'");
        }
        switch (current) {
            case START_OBJECT:
                return _readRawObject();
            case START_ARRAY:
                return _readRawArray();
            case VALUE_STRING:
                return parser.getText();
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return parser.getNumberValue();
            case VALUE_TRUE:
            case VALUE_FALSE:
                return parser.getBooleanValue();
            case VALUE_NULL:
                return null;
            default:
                throw new BindingException("unexpected token '" + _token(current) + "'");
        }
    }

    private Map<String, Object> _readRawObject() throws IOException {
        Map<String, Object> value = new LinkedHashMap<>();
        String name;
        while ((name = parser.nextFieldName()) != null) {
            parser.nextToken();
            value.put(name, _readRawNode());
        }
        return value;
    }

    private List<Object> _readRawArray() throws IOException {
        List<Object> value = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            value.add(_readRawNode());
        }
        return value;
    }

    private JsonToken _currentToken() throws IOException {
        JsonToken current = parser.currentToken();
        return current == null ? parser.nextToken() : current;
    }

    private IOException _expected(String expected, JsonToken actual) {
        return new IOException("Expected " + expected + ", but was " + actual);
    }

    private static Token _token(JsonToken token) {
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
