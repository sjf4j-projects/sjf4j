package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.SerializableString;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.OrderedFieldReaderV3;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeRegistry;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jackson 2 implementation of the standalone V3 value-positioned protocol.
 *
 * <p>This reader retains no container stack or lifecycle state; traversal
 * relies on parser position and the V3 caller protocol. Base {@code readXxx}
 * methods are strict and do not advance. Ordered {@code nextXxx} methods are
 * deliberately direct Jackson consumers: long, int, boolean, and string use
 * Jackson fused/default/coercion accessors, while double advances then uses
 * Jackson's direct double accessor.</p>
 */
public final class Jackson2ReaderV3 implements OrderedFieldReaderV3 {

    private static final ClassValue<Jackson2NameMatcherV3> NAME_MATCHERS =
            new ClassValue<Jackson2NameMatcherV3>() {
                @Override
                protected Jackson2NameMatcherV3 computeValue(Class<?> type) {
                    PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(type);
                    return Jackson2NameMatcherV3.of(
                            pojoInfo.properties.keySet().toArray(new String[0]));
                }
            };

    private final JsonParser parser;

    public Jackson2ReaderV3(JsonParser parser) {
        if (parser == null) {
            throw new NullPointerException("parser");
        }
        this.parser = parser;
    }

    @Override
    public void startDocument() throws IOException {
        JsonToken current = parser.currentToken();
        if (current == null) {
            current = parser.nextToken();
        }
        _requireValue("document root value", current);
    }

    @Override
    public void endDocument() throws IOException {
        JsonToken current = parser.currentToken();
        if (!_isCompletedValue(current)) {
            throw _expected("completed document root value", current);
        }
        if (parser.nextToken() != null) {
            throw _expected("end of document", parser.currentToken());
        }
    }

    @Override
    public Token currentToken() {
        return _token(parser.currentToken());
    }

    @Override
    public NameMatcher nameMatcher(Class<?> type) {
        return NAME_MATCHERS.get(type);
    }

    @Override
    public void beginObject() {
    }

    @Override
    public String nextObjectName() throws IOException {
        String name = parser.nextFieldName();
        if (name != null) {
            parser.nextToken();
        }
        return name;
    }

    @Override
    public int nextObjectField(NameMatcher matcher, int expectedIndex)
            throws IOException {

        if (matcher == null) {
            throw new NullPointerException("matcher");
        }

        if (matcher instanceof Jackson2NameMatcherV3) {
            Jackson2NameMatcherV3 jacksonMatcher = (Jackson2NameMatcherV3) matcher;
            if (expectedIndex >= 0 && expectedIndex < jacksonMatcher.names.length) {
                SerializableString expectedName = jacksonMatcher.serializedNames[expectedIndex];
                if (parser.nextFieldName(expectedName)) {
                    parser.nextToken();
                    return expectedIndex;
                }
                return consumeCurrentObjectField(matcher);
            }
        }

        String name = parser.nextFieldName();
        if (name == null) {
            return END_OF_OBJECT;
        }
        int index = matcher.match(name);
        parser.nextToken();
        return _fieldIndex(index);
    }

    @Override
    public int consumeCurrentObjectField(NameMatcher matcher) throws IOException {
        if (matcher == null) {
            throw new NullPointerException("matcher");
        }
        JsonToken current = parser.currentToken();
        if (current == JsonToken.END_OBJECT) {
            return END_OF_OBJECT;
        }
        if (current != JsonToken.FIELD_NAME) {
            throw _expected("FIELD_NAME or END_OBJECT", current);
        }
        int index = matcher.match(parser.currentName());
        parser.nextToken();
        return _fieldIndex(index);
    }

    @Override
    public boolean nextExpectedName(NameMatcher matcher, int expectedIndex)
            throws IOException {

        if (matcher == null) {
            throw new NullPointerException("matcher");
        }
        if (expectedIndex < 0) {
            throw new IllegalArgumentException("expectedIndex");
        }
        if (matcher instanceof Jackson2NameMatcherV3) {
            Jackson2NameMatcherV3 jacksonMatcher = (Jackson2NameMatcherV3) matcher;
            if (expectedIndex >= jacksonMatcher.names.length) {
                throw new IllegalArgumentException("expectedIndex");
            }
            return parser.nextFieldName(jacksonMatcher.serializedNames[expectedIndex]);
        }
        String name = parser.nextFieldName();
        return name != null && matcher.name(expectedIndex).equals(name);
    }

    @Override
    public void nextValue() throws IOException {
        parser.nextToken();
    }

    @Override
    public long nextLongValue() throws IOException {
        return parser.nextLongValue(0L);
    }

    @Override
    public int nextIntValue() throws IOException {
        return parser.nextIntValue(0);
    }

    @Override
    public boolean nextBooleanValue() throws IOException {
        return Boolean.TRUE.equals(parser.nextBooleanValue());
    }

    @Override
    public double nextDoubleValue() throws IOException {
        parser.nextToken();
        return parser.getDoubleValue();
    }

    @Override
    public String nextStringOrNull() throws IOException {
        return parser.nextTextValue();
    }

    @Override
    public void endObject() {
    }

    @Override
    public void beginArray() {
    }

    @Override
    public boolean nextArrayElement() throws IOException {
        return parser.nextToken() != JsonToken.END_ARRAY;
    }

    @Override
    public void endArray() {
    }

    @Override
    public String readString() throws IOException {
        _require(JsonToken.VALUE_STRING);
        return parser.getText();
    }

    @Override
    public String readStringOrNull() throws IOException {
        JsonToken current = parser.currentToken();
        if (current == JsonToken.VALUE_NULL) {
            return null;
        }
        if (current != JsonToken.VALUE_STRING) {
            throw _expected(JsonToken.VALUE_STRING.name(), current);
        }
        return parser.getText();
    }

    @Override
    public Number readNumber() throws IOException {
        return parser.getNumberValue();
    }

    @Override
    public long readLongValue() throws IOException {
        return parser.getLongValue();
    }

    @Override
    public int readIntValue() throws IOException {
        return parser.getIntValue();
    }

    @Override
    public short readShortValue() throws IOException {
        return parser.getShortValue();
    }

    @Override
    public byte readByteValue() throws IOException {
        return parser.getByteValue();
    }

    @Override
    public double readDoubleValue() throws IOException {
        return parser.getDoubleValue();
    }

    @Override
    public float readFloatValue() throws IOException {
        return parser.getFloatValue();
    }

    @Override
    public boolean readBooleanValue() throws IOException {
        return parser.getBooleanValue();
    }

    @Override
    public char readCharValue() throws IOException {
        String value = readString();
        if (value.isEmpty()) {
            throw new IOException("Cannot read empty string as char");
        }
        return value.charAt(0);
    }

    @Override
    public BigInteger readBigInteger() throws IOException {
        return parser.getBigIntegerValue();
    }

    @Override
    public BigDecimal readBigDecimal() throws IOException {
        return parser.getDecimalValue();
    }

    @Override
    public void readNull() throws IOException {
        _require(JsonToken.VALUE_NULL);
    }

    @Override
    public void skipValue() throws IOException {
        JsonToken current = parser.currentToken();
        _requireValue("value", current);
        if (current == JsonToken.START_OBJECT || current == JsonToken.START_ARRAY) {
            parser.skipChildren();
        }
    }

    @Override
    public Object readRawNode() throws IOException {
        JsonToken current = parser.currentToken();
        _requireValue("value", current);
        switch (current) {
            case START_OBJECT:
                return _readRawObject();
            case START_ARRAY:
                return _readRawArray();
            case VALUE_STRING:
                return readString();
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return readNumber();
            case VALUE_TRUE:
            case VALUE_FALSE:
                return readBooleanValue();
            case VALUE_NULL:
                readNull();
                return null;
            default:
                throw _expected("value", current);
        }
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

    private Map<String, Object> _readRawObject() throws IOException {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        String name;
        while ((name = parser.nextFieldName()) != null) {
            parser.nextToken();
            value.put(name, readRawNode());
        }
        return value;
    }

    private List<Object> _readRawArray() throws IOException {
        List<Object> value = new ArrayList<Object>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            value.add(readRawNode());
        }
        return value;
    }

    private void _require(JsonToken expected) throws IOException {
        JsonToken current = parser.currentToken();
        if (current != expected) {
            throw _expected(expected.name(), current);
        }
    }

    private static int _fieldIndex(int index) {
        return index >= 0 ? index : NameMatcher.UNKNOWN_FIELD;
    }

    private static void _requireValue(String expected, JsonToken actual) throws IOException {
        if (!_isValue(actual)) {
            throw _expected(expected, actual);
        }
    }

    private static boolean _isValue(JsonToken token) {
        return token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY
                || token == JsonToken.VALUE_STRING || token == JsonToken.VALUE_NUMBER_INT
                || token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_TRUE
                || token == JsonToken.VALUE_FALSE || token == JsonToken.VALUE_NULL;
    }

    private static boolean _isCompletedValue(JsonToken token) {
        return token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY
                || token == JsonToken.VALUE_STRING || token == JsonToken.VALUE_NUMBER_INT
                || token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_TRUE
                || token == JsonToken.VALUE_FALSE || token == JsonToken.VALUE_NULL;
    }

    private static IOException _expected(String expected, JsonToken actual) {
        return new IOException("Expected " + expected + ", but was " + _token(actual));
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
                return Token.FIELD_NAME;
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
