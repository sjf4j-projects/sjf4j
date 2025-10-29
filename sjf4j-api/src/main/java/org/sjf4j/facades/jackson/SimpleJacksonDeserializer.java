package org.sjf4j.facades.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.io.IOException;

public class SimpleJacksonDeserializer extends JsonDeserializer<Object> {


    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        if (p.currentToken() == null) {
            p.nextToken();
        }
        return readAny(p);
    }


    /// private

    private Object readAny(JsonParser p) throws IOException {
        JsonToken token = p.currentToken();
        switch (token) {
            case START_OBJECT:
                return readObject(p);
            case START_ARRAY:
                return readArray(p);
            case VALUE_STRING:
                return p.getText();
            case VALUE_NUMBER_INT:
                return p.getNumberValue();
            case VALUE_NUMBER_FLOAT:
                return p.getNumberValue();
            case VALUE_TRUE:
                return Boolean.TRUE;
            case VALUE_FALSE:
                return Boolean.FALSE;
            case VALUE_NULL:
                return null;
            default:
                throw new IllegalStateException("Unexpected token: " + token);
        }
    }

    private JsonObject readObject(JsonParser p) throws IOException {
        JsonObject jo = new JsonObject();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = p.currentName();
            p.nextToken(); // move to value
            jo.put(fieldName, readAny(p));
        }
        return jo;
    }

    private JsonArray readArray(JsonParser p) throws IOException {
        JsonArray ja = new JsonArray();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            ja.add(readAny(p));
        }
        return ja;
    }

}
