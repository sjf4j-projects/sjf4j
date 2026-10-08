package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.JsonType;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.PropertyInfo;
import org.sjf4j.util.Asserts;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.sym.PropertyNameMatcher;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** StreamingReader backed directly by a Jackson 3 {@link JsonParser}. */
public final class Jackson3Reader extends StreamingReader {

    private final JsonParser parser;
    private boolean prefetched;

    public Jackson3Reader(JsonParser parser) {
        super(Backend.JACKSON3);
        this.parser = Asserts.notNull(parser, "parser");
        this.prefetched = parser.currentToken() != null;
    }

    @Override
    public void endDocument() throws IOException {
        JsonToken token;
        if (prefetched) {
            prefetched = false;
            token = parser.currentToken();
        } else {
            token = parser.nextToken();
        }
        if (token != null) {
            throw expected("end of document", token);
        }
    }

    @Override
    public Token peekToken() throws IOException {
        if (!prefetched) {
            parser.nextToken();
            prefetched = true;
        }
        return token(parser.currentToken());
    }

    @Override
    protected NameMatcher createNameMatcher(PropertyInfo[] writableProperties) {
        return new Jackson3NameMatcher(writableProperties);
    }

    @Override
    public boolean nextIfNull() throws IOException {
        return nextIf(JsonToken.VALUE_NULL);
    }

    @Override
    public boolean nextIfObjectStart() throws IOException {
        return nextIf(JsonToken.START_OBJECT);
    }

    @Override
    public boolean nextIfObjectEnd() throws IOException {
        return nextIf(JsonToken.END_OBJECT);
    }

    @Override
    public boolean nextIfArrayStart() throws IOException {
        return nextIf(JsonToken.START_ARRAY);
    }

    @Override
    public boolean nextIfArrayEnd() throws IOException {
        return nextIf(JsonToken.END_ARRAY);
    }

    @Override
    public void startObject() throws IOException {
        if (!nextIfObjectStart()) {
            throw expected(JsonToken.START_OBJECT.name(), parser.currentToken());
        }
    }

    @Override
    public void endObject() throws IOException {
        if (!nextIfObjectEnd()) {
            throw expected(JsonToken.END_OBJECT.name(), parser.currentToken());
        }
    }

    @Override
    public void startArray() throws IOException {
        if (!nextIfArrayStart()) {
            throw expected(JsonToken.START_ARRAY.name(), parser.currentToken());
        }
    }

    @Override
    public void endArray() throws IOException {
        if (!nextIfArrayEnd()) {
            throw expected(JsonToken.END_ARRAY.name(), parser.currentToken());
        }
    }

    @Override
    public String nextName() throws IOException {
        if (prefetched) {
            JsonToken current = parser.currentToken();
            prefetched = false;
            if (current == JsonToken.END_OBJECT) {
                return null;
            }
            if (current != JsonToken.PROPERTY_NAME) {
                throw expected(JsonToken.PROPERTY_NAME.name(), current);
            }
            return parser.currentName();
        }

        String name = parser.nextName();
        if (name != null) {
            return name;
        }
        if (parser.currentToken() == JsonToken.END_OBJECT) {
            return null;
        }
        throw expected(JsonToken.PROPERTY_NAME.name(), parser.currentToken());
    }

    @Override
    public int nextNameMatch(NameMatcher matcher) throws IOException {
        if (!(matcher instanceof Jackson3NameMatcher)) {
            String name = nextName();
            return name == null ? NameMatcher.OBJECT_END : matcher.fallback(name);
        }

        Jackson3NameMatcher jacksonMatcher = (Jackson3NameMatcher) matcher;
        int match;
        if (prefetched) {
            JsonToken current = parser.currentToken();
            prefetched = false;
            if (current == JsonToken.END_OBJECT) {
                return NameMatcher.OBJECT_END;
            }
            if (current != JsonToken.PROPERTY_NAME) {
                throw expected(JsonToken.PROPERTY_NAME.name(), current);
            }
            match = parser.currentNameMatch(jacksonMatcher.matcher);
        } else {
            match = parser.nextNameMatch(jacksonMatcher.matcher);
            if (match == PropertyNameMatcher.MATCH_END_OBJECT) {
                return NameMatcher.OBJECT_END;
            }
            if (match == PropertyNameMatcher.MATCH_ODD_TOKEN) {
                throw expected(JsonToken.PROPERTY_NAME.name(), parser.currentToken());
            }
        }

        if (match >= 0) {
            return match;
        }
        if (match == PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
            return matcher.fallback(parser.currentName());
        }
        throw expected(JsonToken.PROPERTY_NAME.name(), parser.currentToken());
    }

    @Override
    public String readString() throws IOException {
        if (prefetched) {
            prefetched = false;
            JsonToken current = parser.currentToken();
            if (current == JsonToken.VALUE_NULL) {
                return null;
            }
            if (current == JsonToken.VALUE_STRING) {
                return parser.getString();
            }
            throw expected("string or null", current);
        }

        String value = parser.nextStringValue();
        if (value != null) {
            return value;
        }
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        throw expected("string or null", parser.currentToken());
    }

    @Override
    public Number readNumber() throws IOException {
        JsonToken current = nextValue();
        if (current == JsonToken.VALUE_NULL) {
            return null;
        }
        if (!isNumber(current)) {
            throw expected("number or null", current);
        }
        return parser.getNumberValue();
    }

    @Override
    public long readLongValue() throws IOException {
        if (prefetched) {
            prefetched = false;
            return parser.getLongValue();
        }

        long value = parser.nextLongValue(0L);
        if (value != 0L || parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return value;
        }
        return parser.getLongValue();
    }

    @Override
    public int readIntValue() throws IOException {
        if (prefetched) {
            prefetched = false;
            return parser.getIntValue();
        }

        int value = parser.nextIntValue(0);
        if (value != 0 || parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return value;
        }
        return parser.getIntValue();
    }

    @Override
    public short readShortValue() throws IOException {
        return Numbers.toShort(requireNumber());
    }

    @Override
    public byte readByteValue() throws IOException {
        return Numbers.toByte(requireNumber());
    }

    @Override
    public double readDoubleValue() throws IOException {
        return Numbers.toDouble(requireNumber());
    }

    @Override
    public float readFloatValue() throws IOException {
        return Numbers.toFloat(requireNumber());
    }

    @Override
    public boolean readBooleanValue() throws IOException {
        if (prefetched) {
            prefetched = false;
            JsonToken current = parser.currentToken();
            if (current == JsonToken.VALUE_TRUE || current == JsonToken.VALUE_FALSE) {
                return parser.getBooleanValue();
            }
            throw expected("boolean", current);
        }

        Boolean value = parser.nextBooleanValue();
        if (value != null) {
            return value;
        }
        throw expected("boolean", parser.currentToken());
    }

    @Override
    public Long readLong() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toLong(value);
    }

    @Override
    public Integer readInt() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toInt(value);
    }

    @Override
    public Short readShort() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toShort(value);
    }

    @Override
    public Byte readByte() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toByte(value);
    }

    @Override
    public Double readDouble() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toDouble(value);
    }

    @Override
    public Float readFloat() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toFloat(value);
    }

    @Override
    public Boolean readBoolean() throws IOException {
        if (prefetched) {
            if (parser.currentToken() == JsonToken.VALUE_NULL) {
                prefetched = false;
                return null;
            }
            return readBooleanValue();
        }

        Boolean value = parser.nextBooleanValue();
        if (value != null) {
            return value;
        }
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        throw expected("boolean or null", parser.currentToken());
    }

    @Override
    public BigInteger readBigInteger() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toBigInteger(value);
    }

    @Override
    public BigDecimal readBigDecimal() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toBigDecimal(value);
    }

    @Override
    public void skipNode() throws IOException {
        JsonToken current = nextValue();
        if (token(current).jsonType() == JsonType.UNKNOWN) {
            throw expected("value", current);
        }
        parser.skipChildren();
    }

    @Override
    public Object readRawNode() throws IOException {
        return readRawNode(nextValue());
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

    private Number requireNumber() throws IOException {
        Number value = readNumber();
        if (value == null) {
            throw expected("number", parser.currentToken());
        }
        return value;
    }

    private Object readRawNode(JsonToken current) throws IOException {
        if (current == JsonToken.START_OBJECT) {
            Map<String, Object> value = new LinkedHashMap<>();
            String name;
            while ((name = parser.nextName()) != null) {
                value.put(name, readRawNode(parser.nextToken()));
            }
            return value;
        }
        if (current == JsonToken.START_ARRAY) {
            List<Object> value = new ArrayList<>();
            JsonToken element;
            while ((element = parser.nextToken()) != JsonToken.END_ARRAY) {
                if (element == null) {
                    throw expected("array value", null);
                }
                value.add(readRawNode(element));
            }
            return value;
        }
        if (current == JsonToken.VALUE_STRING) {
            return parser.getString();
        }
        if (isNumber(current)) {
            return parser.getNumberValue();
        }
        if (current == JsonToken.VALUE_TRUE || current == JsonToken.VALUE_FALSE) {
            return parser.getBooleanValue();
        }
        if (current == JsonToken.VALUE_NULL) {
            return null;
        }
        throw expected("raw node", current);
    }

    private boolean nextIf(JsonToken expected) throws IOException {
        JsonToken current;
        if (prefetched) {
            current = parser.currentToken();
            if (current != expected) {
                return false;
            }
            prefetched = false;
            return true;
        }

        current = parser.nextToken();
        if (current == expected) {
            return true;
        }
        prefetched = true;
        return false;
    }

    private JsonToken nextValue() throws IOException {
        if (prefetched) {
            prefetched = false;
            return parser.currentToken();
        }
        return parser.nextToken();
    }

    private static boolean isNumber(JsonToken token) {
        return token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT;
    }

    private static IOException expected(String expected, JsonToken actual) {
        return new IOException("expected " + expected + ", but was " + token(actual));
    }

    private static Token token(JsonToken token) {
        if (token == null) {
            return Token.EOF;
        }
        switch (token) {
            case START_OBJECT:
                return Token.OBJECT_START;
            case END_OBJECT:
                return Token.OBJECT_END;
            case PROPERTY_NAME:
                return Token.NAME;
            case START_ARRAY:
                return Token.ARRAY_START;
            case END_ARRAY:
                return Token.ARRAY_END;
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
