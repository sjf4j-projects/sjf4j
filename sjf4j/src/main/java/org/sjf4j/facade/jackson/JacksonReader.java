package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.sjf4j.facade.StreamingReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

public class JacksonReader implements StreamingReader {

    private final JsonParser parser;

    public JacksonReader(JsonParser parser) {
        Objects.requireNonNull(parser, "parser is null");
        this.parser = parser;
    }

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

    @Override
    public void startObject() throws IOException {
        parser.nextToken();
    }

    @Override
    public void endObject() throws IOException {
        parser.nextToken();
    }

    @Override
    public void startArray() throws IOException {
        parser.nextToken();
    }

    @Override
    public void endArray() throws IOException {
        parser.nextToken();
    }

    @Override
    public String nextName() throws IOException {
        String name = parser.currentName();
        parser.nextToken();
        return name;
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
    public long nextLong() throws IOException {
        long value = parser.getLongValue();
        parser.nextToken();
        return value;
    }
    @Override
    public int nextInt() throws IOException {
        int value = parser.getIntValue();
        parser.nextToken();
        return value;
    }
    @Override
    public short nextShort() throws IOException {
        short value = parser.getShortValue();
        parser.nextToken();
        return value;
    }
    @Override
    public byte nextByte() throws IOException {
        byte value = parser.getByteValue();
        parser.nextToken();
        return value;
    }
    @Override
    public double nextDouble() throws IOException {
        double value = parser.getDoubleValue();
        parser.nextToken();
        return value;
    }
    @Override
    public float nextFloat() throws IOException {
        float value = parser.getFloatValue();
        parser.nextToken();
        return value;
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
    public boolean nextBoolean() throws IOException {
        boolean value = parser.getBooleanValue();
        parser.nextToken();
        return value;
    }

    @Override
    public void nextNull() throws IOException {
        parser.nextToken();
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

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
