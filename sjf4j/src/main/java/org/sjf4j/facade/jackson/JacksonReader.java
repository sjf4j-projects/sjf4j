package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.sjf4j.facade.StreamingReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Streaming reader backed by Jackson's {@link JsonParser}.
 */
public class JacksonReader implements StreamingReader {

    private final JsonParser parser;

    /**
     * Creates reader adapter from Jackson JsonParser.
     */
    public JacksonReader(JsonParser parser) {
        Objects.requireNonNull(parser, "parser is null");
        this.parser = parser;
    }

    /**
     * Peeks next token from current parser state.
     */
    @Override
    public Token peekToken() throws IOException {
        JsonToken tk = parser.currentToken();
        if (tk == null) tk = parser.nextToken();
        switch (tk) {
            case START_OBJECT:
                return Token.START_OBJECT;
            case END_OBJECT:
                return Token.END_OBJECT;
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

    /**
     * Consumes and enters object scope.
     */
    @Override
    public void startObject() throws IOException {
        parser.nextToken();
    }

    /**
     * Consumes and exits object scope.
     */
    @Override
    public void endObject() throws IOException {
        parser.nextToken();
    }

    /**
     * Consumes and enters array scope.
     */
    @Override
    public void startArray() throws IOException {
        parser.nextToken();
    }

    /**
     * Consumes and exits array scope.
     */
    @Override
    public void endArray() throws IOException {
        parser.nextToken();
    }

    /**
     * Reads next field name.
     */
    @Override
    public String nextName() throws IOException {
        String name = parser.currentName();
        parser.nextToken();
        return name;
    }

    /**
     * Reads next scalar as string.
     */
    @Override
    public String nextString() throws IOException {
        String value = parser.getText();
        parser.nextToken();
        return value;
    }

    /**
     * Reads next scalar as number.
     */
    @Override
    public Number nextNumber() throws IOException {
        Number value = parser.getNumberValue();
        parser.nextToken();
        return value;
    }

    /**
     * Reads next scalar as long.
     */
    @Override
    public Long nextLong() throws IOException {
        long value = parser.getLongValue();
        parser.nextToken();
        return value;
    }

    /**
     * Reads next scalar as int.
     */
    @Override
    public Integer nextInt() throws IOException {
        int value = parser.getIntValue();
        parser.nextToken();
        return value;
    }

    /**
     * Reads next scalar as short.
     */
    @Override
    public Short nextShort() throws IOException {
        short value = parser.getShortValue();
        parser.nextToken();
        return value;
    }

    /**
     * Reads next scalar as byte.
     */
    @Override
    public Byte nextByte() throws IOException {
        byte value = parser.getByteValue();
        parser.nextToken();
        return value;
    }

    /**
     * Reads next scalar as double.
     */
    @Override
    public Double nextDouble() throws IOException {
        double value = parser.getDoubleValue();
        parser.nextToken();
        return value;
    }

    /**
     * Reads next scalar as float.
     */
    @Override
    public Float nextFloat() throws IOException {
        float value = parser.getFloatValue();
        parser.nextToken();
        return value;
    }

    /**
     * Reads next scalar as BigInteger.
     */
    @Override
    public BigInteger nextBigInteger() throws IOException {
        BigInteger value = parser.getBigIntegerValue();
        parser.nextToken();
        return value;
    }

    /**
     * Reads next scalar as BigDecimal.
     */
    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        BigDecimal value = parser.getDecimalValue();
        parser.nextToken();
        return value;
    }

    /**
     * Reads next scalar as boolean.
     */
    @Override
    public Boolean nextBoolean() throws IOException {
        boolean value = parser.getBooleanValue();
        parser.nextToken();
        return value;
    }

    /**
     * Consumes next null token.
     */
    @Override
    public void nextNull() throws IOException {
        parser.nextToken();
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
    public void nextSkip() throws IOException {
        JsonToken tk = parser.currentToken();
        if (tk.isScalarValue()) {
            parser.nextToken();
        } else {
            parser.skipChildren();
            parser.nextToken();
        }
    }


}
