package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.binding.StreamingReaderV2;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jackson 2 implementation of the intentionally unintegrated V2 cursor
 * sketch. The parser is advanced only by traversal, container skipping, and
 * document-boundary methods; scalar accessors only inspect the current token.
 */
public final class Jackson2ReaderV2 implements StreamingReaderV2 {

    private final JsonParser parser;
    private final Deque<Container> containers = new ArrayDeque<Container>();
    private boolean documentStarted;
    private boolean documentEnded;

    /**
     * Creates matching metadata that retains canonical String names,
     * Jackson SerializedString instances, and a String fallback lookup.
     */
    public static StreamingReaderV2.NameMatcher createNameMatcher(String... names) {
        return new Jackson2NameMatcher(names);
    }

    public Jackson2ReaderV2(JsonParser parser) {
        if (parser == null) {
            throw new NullPointerException("parser");
        }
        this.parser = parser;
    }

    @Override
    public void startDocument() throws IOException {
        if (documentStarted) {
            throw new IOException("Document has already been started");
        }
        JsonToken current = parser.currentToken();
        if (current == null) {
            current = parser.nextToken();
        }
        _requireValue("document root value", current);
        documentStarted = true;
    }

    @Override
    public void endDocument() throws IOException {
        _requireStarted();
        if (documentEnded) {
            throw new IOException("Document has already ended");
        }
        if (!containers.isEmpty()) {
            throw new IOException("Expected completed document root, but a container is still open");
        }

        JsonToken current = parser.currentToken();
        if (!_isCompletedValue(current)) {
            throw _expected("completed document root value", current);
        }
        if (parser.nextToken() != null) {
            throw _expected("end of document", parser.currentToken());
        }
        documentEnded = true;
    }

    @Override
    public Token currentToken() {
        return _token(parser.currentToken());
    }

    @Override
    public void beginObject() throws IOException {
        _requireStarted();
        _require(JsonToken.START_OBJECT);
        containers.push(new Container(true));
    }

    @Override
    public String nextObjectField() throws IOException {
        Container container = _beforeObjectField();
        if (container.atEnd) {
            return null;
        }

        String name = parser.nextFieldName();
        if (name == null) {
            _require(JsonToken.END_OBJECT);
            container.atEnd = true;
            return null;
        }
        _advanceToFieldValue();
        return name;
    }

    @Override
    public int nextObjectField(NameMatcher matcher) throws IOException {
        return nextObjectField(matcher, NO_EXPECTED_FIELD);
    }

    @Override
    public int nextObjectField(NameMatcher matcher, int expectedIndex)
            throws IOException {

        if (matcher == null) {
            throw new NullPointerException("matcher");
        }
        Container container = _beforeObjectField();
        if (container.atEnd) {
            return END_OF_OBJECT;
        }

        if (matcher instanceof Jackson2NameMatcher) {
            Jackson2NameMatcher jacksonMatcher = (Jackson2NameMatcher) matcher;
            if (expectedIndex >= 0 && expectedIndex < jacksonMatcher.names.length) {
                SerializableString expectedName = jacksonMatcher.serializedNames[expectedIndex];
                if (parser.nextFieldName(expectedName)) {
                    _advanceToFieldValue();
                    return expectedIndex;
                }
                return _matchCurrentField(jacksonMatcher);
            }
            return _matchNextField(jacksonMatcher);
        }

        return _matchNextField(matcher);
    }

    @Override
    public void endObject() throws IOException {
        Container container = _requireContainer(true);
        if (!container.atEnd) {
            throw new IOException("Object has not reached END_OBJECT");
        }
        _require(JsonToken.END_OBJECT);
        containers.pop();
    }

    @Override
    public void beginArray() throws IOException {
        _requireStarted();
        _require(JsonToken.START_ARRAY);
        containers.push(new Container(false));
    }

    @Override
    public boolean nextArrayElement() throws IOException {
        Container container = _requireContainer(false);
        JsonToken current = parser.currentToken();
        if (container.atEnd) {
            _require(JsonToken.END_ARRAY);
            return false;
        }

        if (container.atStart) {
            _require(JsonToken.START_ARRAY);
        } else if (!_isCompletedValue(current)) {
            throw _expected("array element or END_ARRAY", current);
        }

        container.atStart = false;
        current = parser.nextToken();
        if (current == JsonToken.END_ARRAY) {
            container.atEnd = true;
            return false;
        }
        _requireValue("array element or END_ARRAY", current);
        return true;
    }

    @Override
    public void endArray() throws IOException {
        Container container = _requireContainer(false);
        if (!container.atEnd) {
            throw new IOException("Array has not reached END_ARRAY");
        }
        _require(JsonToken.END_ARRAY);
        containers.pop();
    }

    @Override
    public String readString() throws IOException {
        _require(JsonToken.VALUE_STRING);
        return parser.getText();
    }

    @Override
    public Number readNumber() throws IOException {
        _requireNumber();
        return parser.getNumberValue();
    }

    @Override
    public long readLongValue() throws IOException {
        _requireNumber();
        return parser.getLongValue();
    }

    @Override
    public int readIntValue() throws IOException {
        _requireNumber();
        return parser.getIntValue();
    }

    @Override
    public short readShortValue() throws IOException {
        _requireNumber();
        return parser.getShortValue();
    }

    @Override
    public byte readByteValue() throws IOException {
        _requireNumber();
        return parser.getByteValue();
    }

    @Override
    public double readDoubleValue() throws IOException {
        _requireNumber();
        return parser.getDoubleValue();
    }

    @Override
    public float readFloatValue() throws IOException {
        _requireNumber();
        return parser.getFloatValue();
    }

    @Override
    public boolean readBooleanValue() throws IOException {
        JsonToken current = parser.currentToken();
        if (current != JsonToken.VALUE_TRUE && current != JsonToken.VALUE_FALSE) {
            throw _expected("boolean value", current);
        }
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
        _requireNumber();
        return parser.getBigIntegerValue();
    }

    @Override
    public BigDecimal readBigDecimal() throws IOException {
        _requireNumber();
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
        beginObject();
        String name;
        while ((name = nextObjectField()) != null) {
            value.put(name, readRawNode());
        }
        endObject();
        return value;
    }

    private List<Object> _readRawArray() throws IOException {
        List<Object> value = new ArrayList<Object>();
        beginArray();
        while (nextArrayElement()) {
            value.add(readRawNode());
        }
        endArray();
        return value;
    }

    private int _matchCurrentField(NameMatcher matcher) throws IOException {
        JsonToken current = parser.currentToken();
        if (current == JsonToken.END_OBJECT) {
            containers.peek().atEnd = true;
            return END_OF_OBJECT;
        }
        if (current != JsonToken.FIELD_NAME) {
            throw _expected("FIELD_NAME or END_OBJECT", current);
        }

        int index = matcher.match(parser.currentName());
        _advanceToFieldValue();
        return index >= 0 ? index : UNKNOWN_FIELD;
    }

    private int _matchNextField(NameMatcher matcher) throws IOException {
        String name = parser.nextFieldName();
        if (name == null) {
            _require(JsonToken.END_OBJECT);
            containers.peek().atEnd = true;
            return END_OF_OBJECT;
        }
        int index = matcher.match(name);
        _advanceToFieldValue();
        return index >= 0 ? index : UNKNOWN_FIELD;
    }

    private Container _beforeObjectField() throws IOException {
        Container container = _requireContainer(true);
        JsonToken current = parser.currentToken();
        if (container.atEnd) {
            _require(JsonToken.END_OBJECT);
            return container;
        }

        if (container.atStart) {
            _require(JsonToken.START_OBJECT);
            return container;
        }
        if (!_isCompletedValue(current)) {
            throw _expected("object field or END_OBJECT", current);
        }
        return container;
    }

    private void _advanceToFieldValue() throws IOException {
        containers.peek().atStart = false;
        JsonToken value = parser.nextToken();
        _requireValue("field value", value);
    }

    private Container _requireContainer(boolean object) throws IOException {
        _requireStarted();
        Container container = containers.peek();
        if (container == null || container.object != object) {
            throw new IOException("Expected active " + (object ? "object" : "array") + " container");
        }
        return container;
    }

    private void _requireStarted() throws IOException {
        if (!documentStarted || documentEnded) {
            throw new IOException("Document is not active");
        }
    }

    private void _require(JsonToken expected) throws IOException {
        JsonToken current = parser.currentToken();
        if (current != expected) {
            throw _expected(expected.name(), current);
        }
    }

    private void _requireNumber() throws IOException {
        JsonToken current = parser.currentToken();
        if (current != JsonToken.VALUE_NUMBER_INT && current != JsonToken.VALUE_NUMBER_FLOAT) {
            throw _expected("number value", current);
        }
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
                return Token.UNKNOWN;
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

    private static final class Container {

        private final boolean object;
        private boolean atStart = true;
        private boolean atEnd;

        private Container(boolean object) {
            this.object = object;
        }
    }

    private static final class Jackson2NameMatcher implements NameMatcher {

        private final String[] names;
        private final SerializedString[] serializedNames;
        private final Map<String, Integer> fallbackLookup;

        private Jackson2NameMatcher(String... sourceNames) {
            if (sourceNames == null) {
                throw new NullPointerException("names");
            }
            names = sourceNames.clone();
            serializedNames = new SerializedString[names.length];
            fallbackLookup = new HashMap<String, Integer>(names.length * 2);
            for (int i = 0; i < names.length; i++) {
                String name = names[i];
                if (name == null) {
                    throw new NullPointerException("names[" + i + "]");
                }
                serializedNames[i] = new SerializedString(name);
                fallbackLookup.put(name, i);
            }
        }

        @Override
        public String name(int index) {
            return names[index];
        }

        @Override
        public int match(String name) {
            Integer index = fallbackLookup.get(name);
            return index == null ? UNKNOWN : index;
        }
    }
}
