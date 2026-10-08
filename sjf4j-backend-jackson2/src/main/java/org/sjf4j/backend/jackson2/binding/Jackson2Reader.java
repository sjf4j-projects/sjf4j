package org.sjf4j.backend.jackson2.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.PropertyInfo;
import org.sjf4j.util.Asserts;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jackson 2 adapter for the consuming-value streaming protocol.
 *
 * <p>The normal object-binding path keeps Jackson's cursor on FIELD_NAME
 * after {@link #nextNameMatch(NameMatcher)}. The following read operation
 * advances directly to its value using Jackson's fused nextXxxValue methods
 * where available. No lookahead flag is needed on that hot path.</p>
 *
 * <p>{@code prefetched} is used only for a failed conditional probe,
 * {@link #peekToken()}, or a parser supplied with an existing current token.
 * Such a token has been fetched physically but remains unconsumed logically.</p>
 */
public final class Jackson2Reader extends StreamingReader {

    private final JsonParser parser;
    private boolean prefetched;

    public Jackson2Reader(JsonParser parser) {
        super(Backend.JACKSON2);
        this.parser = Asserts.notNull(parser, "parser");
        // A caller may supply a parser already positioned on the first token.
        this.prefetched = parser.currentToken() != null;
    }

    /* --------------------------------------------------------------
     * Document / inspection
     * -------------------------------------------------------------- */

    @Override
    public void endDocument() throws IOException {
        if (prefetched) {
            prefetched = false;
            JsonToken current = parser.currentToken();
            if (current != null) {
                throw expected("end of document", current);
            }
            return;
        }

        JsonToken current = parser.nextToken();
        if (current != null) {
            throw expected("end of document", current);
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
        return new Jackson2NameMatcher(writableProperties);
    }

    /* --------------------------------------------------------------
     * Conditional consumption
     * -------------------------------------------------------------- */

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

    /* --------------------------------------------------------------
     * Structural consumption
     * -------------------------------------------------------------- */

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

    /* --------------------------------------------------------------
     * Property names
     * -------------------------------------------------------------- */

    @Override
    public String nextName() throws IOException {
        if (prefetched) {
            prefetched = false;
            JsonToken current = parser.currentToken();
            if (current == JsonToken.END_OBJECT) {
                return null;
            }
            if (current != JsonToken.FIELD_NAME) {
                throw expected(JsonToken.FIELD_NAME.name(), current);
            }
            return parser.currentName();
        }

        String name = parser.nextFieldName();
        if (name != null) {
            return name;
        }
        if (parser.currentToken() == JsonToken.END_OBJECT) {
            return null;
        }
        throw expected(JsonToken.FIELD_NAME.name(), parser.currentToken());
    }

    /**
     * Matches the next name. Jackson 2 can match an expected serialized name
     * without materializing a String; remaining names use the fallback table.
     * The parser stays at FIELD_NAME, so the value remains pending.
     */
    @Override
    public int nextNameMatch(NameMatcher matcher) throws IOException {
        return nextNameMatch(matcher, -1);
    }

    @Override
    public int nextNameMatch(NameMatcher matcher, int expectedIndex) throws IOException {
        Jackson2NameMatcher jacksonMatcher = (Jackson2NameMatcher) matcher;
        if (prefetched) {
            JsonToken current = parser.currentToken();
            prefetched = false;
            if (current == JsonToken.END_OBJECT) {
                return NameMatcher.OBJECT_END;
            }
            if (current != JsonToken.FIELD_NAME) {
                throw expected(JsonToken.FIELD_NAME.name(), current);
            }
            return matcher.fallback(parser.currentName());
        }

        if (expectedIndex >= 0 && expectedIndex < jacksonMatcher.serializedNames.length) {
            if (parser.nextFieldName(jacksonMatcher.serializedNames[expectedIndex])) {
                return expectedIndex;
            }
            // On mismatch, nextFieldName already advanced to the actual name/end.
            JsonToken current = parser.currentToken();
            if (current == JsonToken.FIELD_NAME) {
                return matcher.fallback(parser.currentName());
            }
            if (current == JsonToken.END_OBJECT) {
                return NameMatcher.OBJECT_END;
            }
            throw expected(JsonToken.FIELD_NAME.name(), current);
        }

        String name = parser.nextFieldName();
        if (name != null) {
            return matcher.fallback(name);
        }
        if (parser.currentToken() == JsonToken.END_OBJECT) {
            return NameMatcher.OBJECT_END;
        }
        throw expected(JsonToken.FIELD_NAME.name(), parser.currentToken());
    }

    /* --------------------------------------------------------------
     * String / generic number
     * -------------------------------------------------------------- */

    @Override
    public String readString() throws IOException {
        if (prefetched) {
            prefetched = false;
            JsonToken current = parser.currentToken();
            if (current == JsonToken.VALUE_NULL) {
                return null;
            }
            if (current == JsonToken.VALUE_STRING) {
                return parser.getText();
            }
            throw expected("string or null", current);
        }

        String value = parser.nextTextValue();
        if (value != null) {
            return value;
        }
        JsonToken current = parser.currentToken();
        if (current == JsonToken.VALUE_NULL) {
            return null;
        }
        throw expected("string or null", current);
    }

    @Override
    public Number readNumber() throws IOException {
        JsonToken current = nextValue();
        if (current == JsonToken.VALUE_NULL) {
            return null;
        }
        // Jackson 3 checks the token's numeric type in getNumberValue().
        return parser.getNumberValue();
    }

    /* --------------------------------------------------------------
     * Primitive values (non-null)
     * -------------------------------------------------------------- */

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
        // Includes floating-point values and invalid tokens: defer to parser.
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
        nextValue();
        return Numbers.toShort(parser.getNumberValue());
    }

    @Override
    public byte readByteValue() throws IOException {
        nextValue();
        // JSON -> Java signed byte. Do not allow Jackson's unsigned 128..255 path.
        return Numbers.toByte(parser.getNumberValue());
    }

    @Override
    public double readDoubleValue() throws IOException {
        nextValue();
        return parser.getDoubleValue();
    }

    @Override
    public float readFloatValue() throws IOException {
        nextValue();
        return parser.getFloatValue();
    }

    @Override
    public boolean readBooleanValue() throws IOException {
        if (prefetched) {
            prefetched = false;
            return parser.getBooleanValue();
        }

        Boolean value = parser.nextBooleanValue();
        return value != null ? value : parser.getBooleanValue();
    }

    /* --------------------------------------------------------------
     * Boxed primitive values (nullable)
     * -------------------------------------------------------------- */

    @Override
    public Long readLong() throws IOException {
        if (prefetched) {
            prefetched = false;
            if (parser.currentToken() == JsonToken.VALUE_NULL) {
                return null;
            }
            return parser.getLongValue();
        }

        long value = parser.nextLongValue(0L);
        if (value != 0L) {
            return value;
        }
        JsonToken current = parser.currentToken();
        if (current == JsonToken.VALUE_NUMBER_INT) {
            return 0L;
        }
        if (current == JsonToken.VALUE_NULL) {
            return null;
        }
        return parser.getLongValue();
    }

    @Override
    public Integer readInt() throws IOException {
        if (prefetched) {
            prefetched = false;
            if (parser.currentToken() == JsonToken.VALUE_NULL) {
                return null;
            }
            return parser.getIntValue();
        }

        int value = parser.nextIntValue(0);
        if (value != 0) {
            return value;
        }
        JsonToken current = parser.currentToken();
        if (current == JsonToken.VALUE_NUMBER_INT) {
            return 0;
        }
        if (current == JsonToken.VALUE_NULL) {
            return null;
        }
        return parser.getIntValue();
    }

    @Override
    public Short readShort() throws IOException {
        JsonToken current = nextValue();
        return current == JsonToken.VALUE_NULL ? null
                : Numbers.toShort(parser.getNumberValue());
    }

    @Override
    public Byte readByte() throws IOException {
        JsonToken current = nextValue();
        return current == JsonToken.VALUE_NULL ? null
                : Numbers.toByte(parser.getNumberValue());
    }

    @Override
    public Double readDouble() throws IOException {
        JsonToken current = nextValue();
        return current == JsonToken.VALUE_NULL ? null : parser.getDoubleValue();
    }

    @Override
    public Float readFloat() throws IOException {
        JsonToken current = nextValue();
        return current == JsonToken.VALUE_NULL ? null : parser.getFloatValue();
    }

    @Override
    public Boolean readBoolean() throws IOException {
        if (prefetched) {
            prefetched = false;
            if (parser.currentToken() == JsonToken.VALUE_NULL) {
                return null;
            }
            return parser.getBooleanValue();
        }

        Boolean value = parser.nextBooleanValue();
        if (value != null) {
            return value;
        }
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        return parser.getBooleanValue();
    }

    @Override
    public Character readChar() throws IOException {
        String value = readString();
        if (value == null) {
            return null;
        }
        if (value.length() != 1) {
            throw new BindingException("cannot read char: expected single-character string, but length was "
                    + value.length());
        }
        return value.charAt(0);
    }

    /* --------------------------------------------------------------
     * Arbitrary-precision numbers
     * -------------------------------------------------------------- */

    @Override
    public BigInteger readBigInteger() throws IOException {
        JsonToken current = nextValue();
        return current == JsonToken.VALUE_NULL ? null : parser.getBigIntegerValue();
    }

    @Override
    public BigDecimal readBigDecimal() throws IOException {
        // Preserve the existing Jackson2Reader's numeric conversion semantics:
        // a floating literal read as Double is converted using BigDecimal.valueOf.
        // Switching to getDecimalValue() changes scale (e.g. 1.20e-3).
        Number number = readNumber();
        return number == null ? null : Numbers.toBigDecimal(number);
    }

    /* --------------------------------------------------------------
     * Skip / raw OBNT
     * -------------------------------------------------------------- */

    @Override
    public void skipNode() throws IOException {
        nextValue();
        parser.skipChildren();
    }

    @Override
    public Object readRawNode() throws IOException {
        return readRawNode(nextValue());
    }

    private Object readRawNode(JsonToken current) throws IOException {
        if (current == null) {
            throw expected("raw node", null);
        }

        switch (current) {
            case START_OBJECT:
                return readRawObject();
            case START_ARRAY:
                return readRawArray();
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
                throw expected("raw node", current);
        }
    }

    private Map<String, Object> readRawObject() throws IOException {
        Map<String, Object> value = new LinkedHashMap<>();
        String name;
        while ((name = parser.nextFieldName()) != null) {
            value.put(name, readRawNode(parser.nextToken()));
        }
        return value;
    }

    private List<Object> readRawArray() throws IOException {
        List<Object> value = new ArrayList<>();
        JsonToken current;
        while ((current = parser.nextToken()) != JsonToken.END_ARRAY) {
            value.add(readRawNode(current));
        }
        return value;
    }

    /* --------------------------------------------------------------
     * Lifecycle / helpers
     * -------------------------------------------------------------- */

    @Override
    public void close() throws IOException {
        parser.close();
    }

    /** Consumes a prefetched token, or advances the parser exactly once. */
    private JsonToken nextValue() throws IOException {
        if (prefetched) {
            prefetched = false;
            return parser.currentToken();
        }
        return parser.nextToken();
    }

    /**
     * On mismatch the physical cursor has already advanced, so retain that
     * token for the next logical consuming operation.
     */
    private boolean nextIf(JsonToken expected) throws IOException {
        if (prefetched) {
            if (parser.currentToken() != expected) {
                return false;
            }
            prefetched = false;
            return true;
        }

        JsonToken current = parser.nextToken();
        if (current == expected) {
            return true;
        }
        prefetched = true;
        return false;
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
            case FIELD_NAME:
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
