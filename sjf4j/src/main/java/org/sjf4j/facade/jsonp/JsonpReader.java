package org.sjf4j.facade.jsonp;

import jakarta.json.stream.JsonParser;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.node.Numbers;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;


public class JsonpReader implements StreamingReader {

    private final JsonParser parser;
    private JsonParser.Event current;

    /** Creates reader adapter from JSON-P parser. */
    public JsonpReader(JsonParser parser) {
        Objects.requireNonNull(parser, "parser");
        this.parser = parser;
        this.current = parser.hasNext() ? parser.next() : null;
    }

    /**
     * Peeks next token from current parser state.
     */
    @Override
    public Token peekToken() throws IOException {
        if (current == null) return Token.EOF;
        switch (current) {
            case START_OBJECT:
                return Token.START_OBJECT;
            case END_OBJECT:
                return Token.END_OBJECT;
            case KEY_NAME:
                return Token.FIELD_NAME;
            case START_ARRAY:
                return Token.START_ARRAY;
            case END_ARRAY:
                return Token.END_ARRAY;
            case VALUE_STRING:
                return Token.STRING;
            case VALUE_NUMBER:
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

    /**
     * Consumes and enters object scope.
     */
    @Override
    public void startObject() throws IOException {
        if (current != JsonParser.Event.START_OBJECT) {
            throw new JsonException("Expected START_OBJECT, but was " + current);
        }
        _advance();
    }

    /**
     * Consumes and exits object scope.
     */
    @Override
    public void endObject() throws IOException {
        if (current != JsonParser.Event.END_OBJECT) {
            throw new JsonException("Expected END_OBJECT, but was " + current);
        }
        _advance();
    }

    /**
     * Consumes and enters array scope.
     */
    @Override
    public void startArray() throws IOException {
        if (current != JsonParser.Event.START_ARRAY) {
            throw new JsonException("Expected START_ARRAY, but was " + current);
        }
        _advance();
    }

    /**
     * Consumes and exits array scope.
     */
    @Override
    public void endArray() throws IOException {
        if (current != JsonParser.Event.END_ARRAY) {
            throw new JsonException("Expected END_ARRAY, but was " + current);
        }
        _advance();
    }

    /**
     * Reads next field name.
     */
    @Override
    public String nextName() throws IOException {
        if (current != JsonParser.Event.KEY_NAME) {
            throw new JsonException("Expected KEY_NAME, but was " + current);
        }
        String name = parser.getString();
        _advance();
        return name;
    }

    /**
     * Reads next scalar as string.
     */
    @Override
    public String nextString() throws IOException {
        String value = parser.getString();
        _advance();
        return value;
    }

    /**
     * Reads next scalar as number.
     */
    @Override
    public Number nextNumber() throws IOException {
        BigDecimal value = parser.getBigDecimal();
        _advance();
        return _normalizeNumber(value);
    }

    /**
     * Reads next scalar as long.
     */
    @Override
    public Long nextLong() throws IOException {
        long value = parser.getLong();
        _advance();
        return value;
    }

    /**
     * Reads next scalar as int.
     */
    @Override
    public Integer nextInt() throws IOException {
        int value = parser.getInt();
        _advance();
        return value;
    }

    /**
     * Reads next scalar as short.
     */
    @Override
    public Short nextShort() throws IOException {
        short value = Numbers.toShort(parser.getInt());
        _advance();
        return value;
    }

    /**
     * Reads next scalar as byte.
     */
    @Override
    public Byte nextByte() throws IOException {
        byte value = Numbers.toByte(parser.getInt());
        _advance();
        return value;
    }

    /**
     * Reads next scalar as double.
     */
    @Override
    public Double nextDouble() throws IOException {
        double value = Numbers.toDouble(parser.getBigDecimal());
        _advance();
        return value;
    }

    /**
     * Reads next scalar as float.
     */
    @Override
    public Float nextFloat() throws IOException {
        float value = Numbers.toFloat(parser.getBigDecimal());
        _advance();
        return value;
    }

    /**
     * Reads next scalar as BigInteger.
     */
    @Override
    public BigInteger nextBigInteger() throws IOException {
        BigInteger value = Numbers.toBigInteger(parser.getBigDecimal());
        _advance();
        return value;
    }

    /**
     * Reads next scalar as BigDecimal.
     */
    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        BigDecimal value = parser.getBigDecimal();
        _advance();
        return value;
    }

    /**
     * Reads next scalar as boolean.
     */
    @Override
    public Boolean nextBoolean() throws IOException {
        boolean value = current == JsonParser.Event.VALUE_TRUE;
        _advance();
        return value;
    }

    /**
     * Consumes next null token.
     */
    @Override
    public void nextNull() throws IOException {
        _advance();
    }

    /**
     * Closes underlying parser.
     */
    @Override
    public void close() throws IOException {
        parser.close();
    }

    /**
     * Skips next scalar or nested value.
     */
    @Override
    public void skipNext() throws IOException {
        if (current == JsonParser.Event.VALUE_STRING || current == JsonParser.Event.VALUE_NUMBER
                || current == JsonParser.Event.VALUE_TRUE || current == JsonParser.Event.VALUE_FALSE
                || current == JsonParser.Event.VALUE_NULL) {
            _advance();
        } else if (current == JsonParser.Event.START_OBJECT) {
            _skipComposite();
        } else if (current == JsonParser.Event.START_ARRAY) {
            _skipComposite();
        }
    }

    private void _advance() {
        current = parser.hasNext() ? parser.next() : null;
    }

    private void _skipComposite() {
        int depth = 0;
        while (true) {
            if (current == null) return;
            if (current == JsonParser.Event.START_OBJECT || current == JsonParser.Event.START_ARRAY) {
                depth++;
            } else if (current == JsonParser.Event.END_OBJECT || current == JsonParser.Event.END_ARRAY) {
                depth--;
                if (depth == 0) {
                    _advance();
                    return;
                }
            }
            _advance();
        }
    }

    private static Number _normalizeNumber(BigDecimal n) {
        if (n.scale() <= 0) {
            try {
                return n.intValueExact();
            } catch (ArithmeticException ignore) {
                // try long
            }
            try {
                return n.longValueExact();
            } catch (ArithmeticException ignore) {
                // try big integer
            }
            return n.toBigIntegerExact();
        }

        double d = n.doubleValue();
        if (Double.isFinite(d) && BigDecimal.valueOf(d).compareTo(n) == 0) {
            return d;
        }
        return n;
    }


}
