package org.sjf4j.facade.jackson3;

import org.sjf4j.facade.StreamingReader;
import org.sjf4j.JsonType;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.util.TokenBuffer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Streaming reader backed by Jackson3 JsonParser.
 */
public final class Jackson3Reader implements StreamingReader {

    private final JsonParser parser;
    private final boolean advanceForkSource;

    public Jackson3Reader(JsonParser parser) {
        this(parser, true);
    }

    Jackson3Reader(JsonParser parser, boolean advanceForkSource) {
        Objects.requireNonNull(parser, "parser");
        this.parser = parser;
        this.advanceForkSource = advanceForkSource;
    }

    @Override
    public Token peekToken() throws IOException {
        JsonToken tk = parser.currentToken();
        if (tk == null) tk = parser.nextToken();
        if (tk == null) return Token.EOF;
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
            case PROPERTY_NAME:
                return Token.FIELD_NAME;
            default:
                return Token.UNKNOWN;
        }
    }


    @Override
    public void endDocument() throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) token = parser.nextToken();
        if (token != null) throw new IOException("Expected end of document, but was " + peekToken());
    }


    @Override
    public StreamingReader forkValue() throws IOException {
        // Discriminator OneOf needs to consume the complete object before choosing its target type.
        // Jackson databind may keep using this parser after a field deserializer returns, so doing
        // that work on the source parser can violate its expected cursor state. Buffering advances
        // the source past this value while the returned parser reads an isolated copy instead.
        TokenBuffer rawBuffer = TokenBuffer.forBuffering(parser, parser.objectReadContext());
        rawBuffer.copyCurrentStructure(parser);
        // copyCurrentStructure leaves the parser on the value's closing token.
        if (advanceForkSource) parser.nextToken();
        return new Jackson3Reader(rawBuffer.asParserOnFirstToken(parser.objectReadContext(), parser));
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
        String value = parser.getString();
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
    public Long nextLong() throws IOException {
        long value = parser.getLongValue();
        parser.nextToken();
        return value;
    }

    @Override
    public Integer nextInt() throws IOException {
        int value = parser.getIntValue();
        parser.nextToken();
        return value;
    }

    @Override
    public Short nextShort() throws IOException {
        short value = parser.getShortValue();
        parser.nextToken();
        return value;
    }

    @Override
    public Byte nextByte() throws IOException {
        byte value = parser.getByteValue();
        parser.nextToken();
        return value;
    }

    @Override
    public Double nextDouble() throws IOException {
        double value = parser.getDoubleValue();
        parser.nextToken();
        return value;
    }

    @Override
    public Float nextFloat() throws IOException {
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
    public Boolean nextBoolean() throws IOException {
        boolean value = parser.getBooleanValue();
        parser.nextToken();
        return value;
    }

    @Override
    public void nextNull() throws IOException {
        parser.nextToken();
    }

    @Override
    public boolean nextIfNull() throws IOException {
        if (peekToken() != Token.NULL) return false;
        parser.nextToken();
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() throws IOException {
        if (peekToken() != Token.END_OBJECT) return false;
        parser.nextToken();
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() throws IOException {
        if (peekToken() != Token.END_ARRAY) return false;
        parser.nextToken();
        return true;
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
    public void close() throws IOException {
        parser.close();
    }

    @Override
    public void skipNext() throws IOException {
        Token token = peekToken();
        if (token.jsonType() == JsonType.UNKNOWN) {
            throw new IOException("Expected value to skip, but was " + token);
        }
        JsonToken tk = parser.currentToken();
        if (tk.isScalarValue()) {
            parser.nextToken();
        } else {
            parser.skipChildren();
            parser.nextToken();
        }
    }
}
