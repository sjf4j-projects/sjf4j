package org.sjf4j.backend.jackson2.binding;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Jackson 2 adapter for the V4 consuming-value protocol.
 *
 * <p>The normal compiled path follows Jackson's native forward-only cursor:
 *
 * <pre>{@code
 * nextNameMatch()
 * readIntValue()
 * nextNameMatch()
 * readString()
 * ...
 * }</pre>
 *
 * <p>On that path no additional reader state is required:
 *
 * <pre>
 * START_OBJECT
 *     -> FIELD_NAME
 *     -> VALUE
 *     -> FIELD_NAME
 *     -> VALUE
 *     -> END_OBJECT
 * </pre>
 *
 * <p>{@link #prefetched} is used only when an inspection or conditional
 * operation has to advance Jackson's parser in order to determine what comes
 * next without logically consuming a non-matching token. Typical examples
 * are {@link #peekToken()}, {@link #nextIfNull()}, and
 * {@link #nextIfArrayEnd()}.
 *
 * <p>Generated binding code should normally avoid those probe operations on
 * its hot object-binding path and use {@code nextNameMatch() + readXxx()}
 * directly.
 */
public final class Jackson2Reader implements StreamingReader {

    private final JsonParser parser;

    /**
     * {@code true} when {@link JsonParser#currentToken()} has been obtained
     * by a non-consuming SJF4J probe and therefore still represents the next
     * logical token to consume.
     *
     * <p>This flag is not used by the normal
     * {@code nextNameMatch() -> readXxx()} path.</p>
     */
    private boolean prefetched;


    public Jackson2ReaderV4(JsonParser parser) {
        Asserts.notNull(parser, "parser");
        this.parser = parser;
    }


    /**
     * --------------------------------------------------------------
     * Document
     * --------------------------------------------------------------
     */

    /**
     * Verifies that the complete document has been consumed.
     */
    @Override
    public void endDocument() throws IOException {
        if (prefetched) {
            prefetched = false;
            if (parser.currentToken() != null) {
                throw _expected("end of document", parser.currentToken());
            }
            return;
        }

        JsonToken token = parser.nextToken();
        if (token != null) {
            throw _expected("end of document", token);
        }
    }


    /**
     * --------------------------------------------------------------
     * Token Inspection
     * --------------------------------------------------------------
     */

    /**
     * Returns the next logical token without consuming it.
     *
     * <p>Jackson has no separate next-token lookahead cursor, so this method
     * physically advances the parser and records the token as prefetched.
     * Subsequent consuming operations then consume the current token instead
     * of advancing again.</p>
     */
    @Override
    public Token peekToken() throws IOException {
        if (!prefetched) {
            parser.nextToken();
            prefetched = true;
        }

        return _token(parser.currentToken());
    }


    /**
     * --------------------------------------------------------------
     * Property Matching
     * --------------------------------------------------------------
     */

    @Override
    public NameMatcher nameMatcher(Class<?> type) {
        return Jackson2NameMatcher.get(type);
    }


    /**
     * --------------------------------------------------------------
     * Conditional Consumption
     * --------------------------------------------------------------
     */

    @Override
    public boolean nextIfNull() throws IOException {
        return _nextIf(JsonToken.VALUE_NULL);
    }

    @Override
    public boolean nextIfObjectStart() throws IOException {
        return _nextIf(JsonToken.START_OBJECT);
    }

    @Override
    public boolean nextIfObjectEnd() throws IOException {
        return _nextIf(JsonToken.END_OBJECT);
    }

    @Override
    public boolean nextIfArrayStart() throws IOException {
        return _nextIf(JsonToken.START_ARRAY);
    }

    @Override
    public boolean nextIfArrayEnd() throws IOException {
        return _nextIf(JsonToken.END_ARRAY);
    }


    /**
     * --------------------------------------------------------------
     * Structural Consumption
     * --------------------------------------------------------------
     */

    @Override
    public void startObject() throws IOException {
        if (!nextIfObjectStart()) {
            throw _expected(JsonToken.START_OBJECT.name(), parser.currentToken());
        }
    }

    @Override
    public void endObject() throws IOException {
        if (!nextIfObjectEnd()) {
            throw _expected(JsonToken.END_OBJECT.name(), parser.currentToken());
        }
    }

    @Override
    public void startArray() throws IOException {
        if (!nextIfArrayStart()) {
            throw _expected(JsonToken.START_ARRAY.name(), parser.currentToken());
        }
    }

    @Override
    public void endArray() throws IOException {
        if (!nextIfArrayEnd()) {
            throw _expected(JsonToken.END_ARRAY.name(), parser.currentToken());
        }
    }


    /**
     * --------------------------------------------------------------
     * Property Names
     * --------------------------------------------------------------
     */

    /**
     * Consumes the next property name.
     *
     * <p>The parser remains positioned on {@link JsonToken#FIELD_NAME}.
     * The corresponding property value is intentionally not advanced here;
     * the following {@code readXxx()} operation advances to and consumes it.
     */
    @Override
    public String nextName() throws IOException {
        if (prefetched) {
            JsonToken current = parser.currentToken();
            prefetched = false;
            if (current == JsonToken.END_OBJECT) {
                return null;
            }
            if (current != JsonToken.FIELD_NAME) {
                throw _expected(JsonToken.FIELD_NAME.name(), current);
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
        throw _expected(JsonToken.FIELD_NAME.name(), parser.currentToken());
    }

    /**
     * Matches the next property name using the generic String path.
     *
     * <p>The object end is consumed and reported as
     * {@link NameMatcher#OBJECT_END}.</p>
     */
    @Override
    public int nextNameMatch(NameMatcher matcher) throws IOException {
        return nextNameMatch(matcher, -1);
    }

    /**
     * Matches the next property name, using Jackson's serialized-name fast
     * path when an expected property index is available.
     *
     * <p>On a successful property match the parser remains positioned on
     * {@link JsonToken#FIELD_NAME}. The subsequent value reader is therefore
     * able to use Jackson's fused {@code nextXxxValue()} methods directly.</p>
     */
    @Override
    public int nextNameMatch(NameMatcher matcher, int expectedIndex) throws IOException {

        /*
         * Slow/lookahead path.
         *
         * A previous peek/nextIf operation already advanced Jackson to the
         * next logical token.
         */
        if (prefetched) {
            JsonToken current = parser.currentToken();
            if (current == JsonToken.END_OBJECT) {
                prefetched = false;
                return NameMatcher.OBJECT_END;
            }
            if (current != JsonToken.FIELD_NAME) {
                throw _expected(JsonToken.FIELD_NAME.name(), current);
            }
            prefetched = false;
            return matcher.match(parser.currentName());
        }

        /*
         * Hot compiled path.
         *
         * Jackson 2 does not provide the multi-name PropertyNameMatcher used
         * by Jackson 3, but nextFieldName(SerializableString) gives us a
         * direct fast path for the expected property.
         */
        if (matcher instanceof Jackson2NameMatcher) {
            Jackson2NameMatcher jacksonMatcher = (Jackson2NameMatcher) matcher;
            if (expectedIndex >= 0 && expectedIndex < jacksonMatcher.serializedNames.length) {
                if (parser.nextFieldName(jacksonMatcher.serializedNames[expectedIndex])) {
                    return expectedIndex;
                }

                JsonToken current = parser.currentToken();
                if (current == JsonToken.END_OBJECT) {
                    return NameMatcher.OBJECT_END;
                }

                if (current != JsonToken.FIELD_NAME) {
                    throw _expected(JsonToken.FIELD_NAME.name(), current);
                }

                return matcher.match(parser.currentName());
            }
        }

        /*
         * Generic name path.
         */
        String name = parser.nextFieldName();
        if (name != null) {
            return matcher.match(name);
        }

        JsonToken current = parser.currentToken();
        if (current == JsonToken.END_OBJECT) {
            return NameMatcher.OBJECT_END;
        }

        throw _expected(JsonToken.FIELD_NAME.name(), current);
    }


    /**
     * --------------------------------------------------------------
     * String
     * --------------------------------------------------------------
     */

    /**
     * Reads a nullable String.
     *
     * <p>The normal path uses Jackson's fused
     * {@link JsonParser#nextTextValue()} operation.</p>
     */
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
            throw _expected("string or null", current);
        }

        String value = parser.nextTextValue();
        if (value != null) {
            return value;
        }

        JsonToken current = parser.currentToken();
        if (current == JsonToken.VALUE_NULL) {
            return null;
        }

        throw _expected("string or null", current);
    }


    /**
     * --------------------------------------------------------------
     * Generic Number
     * --------------------------------------------------------------
     */

    @Override
    public Number readNumber() throws IOException {
        if (prefetched) {
            prefetched = false;
            if (parser.currentToken() == JsonToken.VALUE_NULL) {
                return null;
            }
            return parser.getNumberValue();
        }

        JsonToken current = parser.nextToken();
        if (current == JsonToken.VALUE_NULL) {
            return null;
        }

        return parser.getNumberValue();
    }


    /**
     * --------------------------------------------------------------
     * Primitive Values
     * --------------------------------------------------------------
     */

    /**
     * Reads a primitive long.
     *
     * <p>The non-prefetched path uses {@code nextLongValue(0)}. Since
     * Jackson returns the supplied default for a non-integer token, zero is
     * used as a fast sentinel. Only the zero case needs to inspect the token
     * and fall back to {@code getLongValue()} when necessary.</p>
     */
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

    /**
     * Reads a primitive int using Jackson's fused integer fast path.
     */
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
        if (prefetched) {
            prefetched = false;
        } else {
            parser.nextToken();
        }

        return parser.getShortValue();
    }

    @Override
    public byte readByteValue() throws IOException {
        if (prefetched) {
            prefetched = false;
        } else {
            parser.nextToken();
        }

        return parser.getByteValue();
    }

    @Override
    public double readDoubleValue() throws IOException {
        if (prefetched) {
            prefetched = false;
        } else {
            parser.nextToken();
        }

        return parser.getDoubleValue();
    }

    @Override
    public float readFloatValue() throws IOException {
        if (prefetched) {
            prefetched = false;
        } else {
            parser.nextToken();
        }

        return parser.getFloatValue();
    }

    @Override
    public boolean readBooleanValue() throws IOException {
        if (prefetched) {
            prefetched = false;
            return parser.getBooleanValue();
        }

        Boolean value = parser.nextBooleanValue();

        if (value != null) {
            return value;
        }

        /*
         * Wrong token (including null): let Jackson's value accessor
         * produce the normal type error.
         */
        return parser.getBooleanValue();
    }

    @Override
    public char readCharValue() throws IOException {
        String value = readString();
        if (value == null) {
            throw _expected(JsonToken.VALUE_STRING.name(), parser.currentToken());
        }
        if (value.isEmpty()) {
            throw new IOException("cannot read empty string as char");
        }
        return value.charAt(0);
    }


    /**
     * --------------------------------------------------------------
     * Boxed Primitive Values
     * --------------------------------------------------------------
     *
     * These methods are overridden instead of using the interface defaults.
     *
     * A default implementation based on:
     *
     *     nextIfNull() ? null : readXxxValue()
     *
     * would prefetch every non-null value and therefore prevent the normal
     * Jackson fused read path from being used.
     */

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
        JsonToken current;

        if (prefetched) {
            prefetched = false;
            current = parser.currentToken();
        } else {
            current = parser.nextToken();
        }

        if (current == JsonToken.VALUE_NULL) {
            return null;
        }

        return parser.getShortValue();
    }

    @Override
    public Byte readByte() throws IOException {
        JsonToken current;

        if (prefetched) {
            prefetched = false;
            current = parser.currentToken();
        } else {
            current = parser.nextToken();
        }

        if (current == JsonToken.VALUE_NULL) {
            return null;
        }

        return parser.getByteValue();
    }

    @Override
    public Double readDouble() throws IOException {
        JsonToken current;

        if (prefetched) {
            prefetched = false;
            current = parser.currentToken();
        } else {
            current = parser.nextToken();
        }

        if (current == JsonToken.VALUE_NULL) {
            return null;
        }

        return parser.getDoubleValue();
    }

    @Override
    public Float readFloat() throws IOException {
        JsonToken current;

        if (prefetched) {
            prefetched = false;
            current = parser.currentToken();
        } else {
            current = parser.nextToken();
        }

        if (current == JsonToken.VALUE_NULL) {
            return null;
        }

        return parser.getFloatValue();
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
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.charAt(0);
    }


    /**
     * --------------------------------------------------------------
     * Arbitrary-Precision Numbers
     * --------------------------------------------------------------
     */

    @Override
    public BigInteger readBigInteger() throws IOException {
        JsonToken current;

        if (prefetched) {
            prefetched = false;
            current = parser.currentToken();
        } else {
            current = parser.nextToken();
        }

        if (current == JsonToken.VALUE_NULL) {
            return null;
        }

        return parser.getBigIntegerValue();
    }

    @Override
    public BigDecimal readBigDecimal() throws IOException {
        JsonToken current;

        if (prefetched) {
            prefetched = false;
            current = parser.currentToken();
        } else {
            current = parser.nextToken();
        }

        if (current == JsonToken.VALUE_NULL) {
            return null;
        }

        return parser.getDecimalValue();
    }


    /**
     * --------------------------------------------------------------
     * Null
     * --------------------------------------------------------------
     */

    @Override
    public void readNull() throws IOException {
        if (!nextIfNull()) {
            throw _expected(
                    JsonToken.VALUE_NULL.name(),
                    parser.currentToken());
        }
    }


    /**
     * --------------------------------------------------------------
     * Skipping
     * --------------------------------------------------------------
     */

    /**
     * Consumes exactly one complete value.
     *
     * <p>No additional value-token validation is performed here. Generated
     * code calls this method only when a value is pending, and Jackson's
     * {@code skipChildren()} handles structured values directly.</p>
     */
    @Override
    public void skipNode() throws IOException {
        if (prefetched) {
            prefetched = false;
        } else {
            parser.nextToken();
        }

        parser.skipChildren();
    }


    /**
     * --------------------------------------------------------------
     * Raw OBNT
     * --------------------------------------------------------------
     */

    @Override
    public Object readRawNode() throws IOException {
        JsonToken current;
        if (prefetched) {
            prefetched = false;
            current = parser.currentToken();
        } else {
            current = parser.nextToken();
        }

        return _readRawNode(current);
    }

    private Object _readRawNode(JsonToken current) throws IOException {
        if (current == null) throw _expected("raw node", null);
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
                throw _expected("raw node", current);
        }
    }

    private Map<String, Object> _readRawObject() throws IOException {
        Map<String, Object> value = new LinkedHashMap<>();
        String name;
        while ((name = parser.nextFieldName()) != null) {
            JsonToken token = parser.nextToken();
            value.put(name, _readRawNode(token));
        }
        return value;
    }

    private List<Object> _readRawArray() throws IOException {
        List<Object> value = new ArrayList<>();
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            value.add(_readRawNode(token));
        }
        return value;
    }


    /**
     * --------------------------------------------------------------
     * Lifecycle
     * --------------------------------------------------------------
     */

    @Override
    public void close() throws IOException {
        parser.close();
    }


    /**
     * --------------------------------------------------------------
     * Internal Helpers
     * --------------------------------------------------------------
     */

    /**
     * Conditionally consumes the specified next token.
     *
     * <p>When the token does not match, Jackson has already physically
     * advanced to it. It is therefore marked as prefetched so that the next
     * logical consuming operation uses the current token rather than
     * advancing again.</p>
     */
    private boolean _nextIf(JsonToken expected) throws IOException {
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

    private static IOException _expected(String expected, JsonToken actual) {
        return new IOException("expected " + expected + ", but was " + _token(actual));
    }

    private static Token _token(JsonToken token) {
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