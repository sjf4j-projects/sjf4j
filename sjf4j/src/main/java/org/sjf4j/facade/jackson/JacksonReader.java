package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.sjf4j.facade.StreamingReader;

import java.io.IOException;

public class JacksonReader implements StreamingReader {

    private final JsonParser parser;

    public JacksonReader(JsonParser parser) {
        if (parser == null) throw new IllegalArgumentException("Parser must not be null");
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
    public Boolean nextBoolean() throws IOException {
        Boolean value = parser.getBooleanValue();
        parser.nextToken();
        return value;
    }

    @Override
    public void nextNull() throws IOException {
        parser.nextToken();
    }

    @Override
    public boolean hasNext() throws IOException {
        Token token = peekToken();
        return token != Token.END_OBJECT && token != Token.END_ARRAY;
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

    /// Skip

    @Override
    public void skipNode() throws IOException {
        JsonToken tk = parser.currentToken();
        if (tk.isScalarValue()) parser.nextToken();
        else parser.skipChildren();
    }


}
