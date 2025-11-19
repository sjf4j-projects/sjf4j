package org.sjf4j.facades.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import lombok.NonNull;
import org.sjf4j.facades.FacadeReader;

import java.io.IOException;

public class JacksonReader implements FacadeReader {

    private final JsonParser parser;

    public JacksonReader(@NonNull JsonParser parser) throws IOException {
        this.parser = parser;
    }

    @Override
    public int peekTokenId() throws IOException {
        int tokenId = parser.currentTokenId();
        if (tokenId == JsonTokenId.ID_NO_TOKEN) {
            parser.nextToken();
            tokenId = parser.currentTokenId();
        }
        switch (tokenId) {
            case JsonTokenId.ID_START_OBJECT:
                return ID_START_OBJECT;
            case JsonTokenId.ID_END_OBJECT:
                return ID_END_OBJECT;
            case JsonTokenId.ID_START_ARRAY:
                return ID_START_ARRAY;
            case JsonTokenId.ID_END_ARRAY:
                return ID_END_ARRAY;
            case JsonTokenId.ID_STRING:
                return ID_STRING;
            case JsonTokenId.ID_NUMBER_INT:
            case JsonTokenId.ID_NUMBER_FLOAT:
                return ID_NUMBER;
            case JsonTokenId.ID_TRUE:
            case JsonTokenId.ID_FALSE:
                return ID_BOOLEAN;
            case JsonTokenId.ID_NULL:
                return ID_NULL;
            default:
                return ID_UNKNOWN;
        }
    }

    @Override
    public void startDocument() throws IOException {

    }

    @Override
    public void endDocument() throws IOException {

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
        int tid = peekTokenId();
        return tid != ID_END_OBJECT && tid != ID_END_ARRAY;
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

}
