package org.sjf4j.backend.jsonp.binding;

import jakarta.json.stream.JsonParser;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.node.Numbers;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** Streaming reader backed directly by a JSON-P {@link JsonParser}. */
public final class JsonpReader implements StreamingReader {

    private final JsonParser parser;
    private JsonParser.Event current;

    /**
     * Creates a reader around a JSON-P parser.
     *
     * <p>When {@link JsonParser#currentEvent()} is supported, an already-positioned parser is preserved.
     * Providers that do not support this JSON-P 2.1 method must be supplied an untouched parser because its
     * current event cannot be recovered.</p>
     */
    public JsonpReader(JsonParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
        try {
            current = parser.currentEvent();
        } catch (UnsupportedOperationException ignored) {
            advance();
            return;
        }
        if (current == null) advance();
    }

    @Override
    public Token peekToken() {
        if (current == null) return Token.EOF;
        switch (current) {
            case START_OBJECT: return Token.START_OBJECT;
            case END_OBJECT: return Token.END_OBJECT;
            case KEY_NAME: return Token.NAME;
            case START_ARRAY: return Token.START_ARRAY;
            case END_ARRAY: return Token.END_ARRAY;
            case VALUE_STRING: return Token.STRING;
            case VALUE_NUMBER: return Token.NUMBER;
            case VALUE_TRUE:
            case VALUE_FALSE: return Token.BOOLEAN;
            case VALUE_NULL: return Token.NULL;
            default: return Token.UNKNOWN;
        }
    }

    @Override
    public void startObject() throws IOException {
        require(JsonParser.Event.START_OBJECT, "start object");
        advance();
    }

    @Override
    public void endObject() throws IOException {
        require(JsonParser.Event.END_OBJECT, "end object");
        advance();
    }

    @Override
    public void startArray() throws IOException {
        require(JsonParser.Event.START_ARRAY, "start array");
        advance();
    }

    @Override
    public void endArray() throws IOException {
        require(JsonParser.Event.END_ARRAY, "end array");
        advance();
    }

    @Override
    public String nextName() throws IOException {
        require(JsonParser.Event.KEY_NAME, "name");
        String value = parser.getString();
        advance();
        return value;
    }

    @Override
    public String nextString() throws IOException {
        require(JsonParser.Event.VALUE_STRING, "string");
        String value = parser.getString();
        advance();
        return value;
    }

    @Override
    public Number nextNumber() throws IOException {
        BigDecimal value = number();
        boolean integral = parser.isIntegralNumber();
        advance();
        return normalize(value, integral);
    }

    @Override
    public long nextLongValue() throws IOException {
        long value = integralNumber().longValueExact();
        advance();
        return value;
    }

    @Override
    public int nextIntValue() throws IOException {
        int value = integralNumber().intValueExact();
        advance();
        return value;
    }

    @Override
    public short nextShortValue() throws IOException {
        short value = Numbers.toShort(integralNumber().longValueExact());
        advance();
        return value;
    }

    @Override
    public byte nextByteValue() throws IOException {
        byte value = Numbers.toByte(integralNumber().longValueExact());
        advance();
        return value;
    }

    @Override
    public double nextDoubleValue() throws IOException {
        double value = Numbers.toDouble(number());
        advance();
        return value;
    }

    @Override
    public float nextFloatValue() throws IOException {
        float value = Numbers.toFloat(number());
        advance();
        return value;
    }

    @Override
    public boolean nextBooleanValue() throws IOException {
        if (current != JsonParser.Event.VALUE_TRUE && current != JsonParser.Event.VALUE_FALSE) {
            throw expected("boolean");
        }
        boolean value = current == JsonParser.Event.VALUE_TRUE;
        advance();
        return value;
    }

    @Override
    public char nextCharValue() throws IOException {
        require(JsonParser.Event.VALUE_STRING, "string");
        String value = parser.getString();
        if (value.isEmpty()) throw new IOException("cannot read empty string as char");
        advance();
        return value.charAt(0);
    }

    @Override
    public BigInteger nextBigInteger() throws IOException {
        BigInteger value = integralNumber().toBigIntegerExact();
        advance();
        return value;
    }

    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        BigDecimal value = number();
        advance();
        return value;
    }

    @Override
    public void nextNull() throws IOException {
        require(JsonParser.Event.VALUE_NULL, "null");
        advance();
    }

    @Override
    public boolean nextIfNull() {
        if (current != JsonParser.Event.VALUE_NULL) return false;
        advance();
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() {
        if (current != JsonParser.Event.END_OBJECT) return false;
        advance();
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() {
        if (current != JsonParser.Event.END_ARRAY) return false;
        advance();
        return true;
    }

    @Override
    public void skipNext() throws IOException {
        if (current == null) throw expected("value");
        switch (current) {
            case VALUE_STRING:
            case VALUE_NUMBER:
            case VALUE_TRUE:
            case VALUE_FALSE:
            case VALUE_NULL:
                advance();
                return;
            case START_OBJECT:
            case START_ARRAY:
                skipComposite();
                return;
            default:
                throw expected("value");
        }
    }

    @Override
    public void close() {
        parser.close();
    }

    private BigDecimal number() throws IOException {
        require(JsonParser.Event.VALUE_NUMBER, "number");
        return parser.getBigDecimal();
    }

    private BigDecimal integralNumber() throws IOException {
        require(JsonParser.Event.VALUE_NUMBER, "integer number");
        if (!parser.isIntegralNumber()) throw expected("integer number");
        return parser.getBigDecimal();
    }

    private void skipComposite() {
        int depth = 0;
        do {
            if (current == JsonParser.Event.START_OBJECT || current == JsonParser.Event.START_ARRAY) {
                depth++;
            } else if (current == JsonParser.Event.END_OBJECT || current == JsonParser.Event.END_ARRAY) {
                depth--;
            }
            advance();
        } while (depth != 0);
    }

    private void require(JsonParser.Event event, String name) throws IOException {
        if (current != event) throw expected(name);
    }

    private IOException expected(String name) {
        return new IOException("Expected " + name + ", but was " + current);
    }

    private void advance() {
        current = parser.hasNext() ? parser.next() : null;
    }

    private static Number normalize(BigDecimal value, boolean integral) {
        if (integral) {
            try {
                return value.intValueExact();
            } catch (ArithmeticException ignored) {
            }
            try {
                return value.longValueExact();
            } catch (ArithmeticException ignored) {
            }
            return value.toBigIntegerExact();
        }
        double doubleValue = value.doubleValue();
        return Double.isFinite(doubleValue) && BigDecimal.valueOf(doubleValue).compareTo(value) == 0
                ? doubleValue : value;
    }
}
