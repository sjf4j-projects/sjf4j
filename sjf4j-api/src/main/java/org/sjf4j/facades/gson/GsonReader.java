package org.sjf4j.facades.gson;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.sjf4j.facades.FacadeReader;
import org.sjf4j.util.NumberUtil;

import java.io.IOException;

public class GsonReader implements FacadeReader {

    private final JsonReader reader;

    public GsonReader(JsonReader reader) {
        this.reader = reader;
    }

    @Override
    public int peekTokenId() throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT:
                return ID_START_OBJECT;
            case END_OBJECT:
                return ID_END_OBJECT;
            case BEGIN_ARRAY:
                return ID_START_ARRAY;
            case END_ARRAY:
                return ID_END_ARRAY;
            case STRING:
                return ID_STRING;
            case NUMBER:
                return ID_NUMBER;
            case BOOLEAN:
                return ID_BOOLEAN;
            case NULL:
                return ID_NULL;
            default:
                return ID_UNKNOWN;
        }
    }

    @Override
    public void startDocument() {
        // Nothing
    }

    @Override
    public void endDocument() {
        // Nothing
    }

    @Override
    public void startObject() throws IOException {
        reader.beginObject();
    }

    @Override
    public void endObject() throws IOException {
        reader.endObject();
    }

    @Override
    public void startArray() throws IOException {
        reader.beginArray();
    }

    @Override
    public void endArray() throws IOException {
        reader.endArray();
    }

    @Override
    public String nextName() throws IOException {
        return reader.nextName();
    }

    @Override
    public String nextString() throws IOException {
        return reader.nextString();
    }

    @Override
    public Number nextNumber() throws IOException {
//        return reader.nextDouble();
        return NumberUtil.stringToNumber(reader.nextString());
    }

    @Override
    public Boolean nextBoolean() throws IOException {
        return reader.nextBoolean();
    }

    @Override
    public void nextNull() throws IOException {
        reader.nextNull();
    }

    @Override
    public boolean hasNext() throws IOException {
        int tid = peekTokenId();
        return tid != ID_END_OBJECT && tid != ID_END_ARRAY;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
