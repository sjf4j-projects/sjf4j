package org.sjf4j.testbench.binding.handwritten;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Direct Jackson2 streaming reader for an untyped JSON object graph. */
public final class Jackson2HandMapReader {

    private Jackson2HandMapReader() {}

    public static Object read(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) {
            parser.nextToken();
        }
        return readValue(parser);
    }

    private static Object readValue(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        switch (token) {
            case START_OBJECT:
                LinkedHashMap<String, Object> object = new LinkedHashMap<>();
                parser.nextToken();
                while (parser.currentToken() != JsonToken.END_OBJECT) {
                    String name = parser.currentName();
                    parser.nextToken();
                    object.put(name, readValue(parser));
                }
                parser.nextToken();
                return object;
            case START_ARRAY:
                ArrayList<Object> array = new ArrayList<>();
                parser.nextToken();
                while (parser.currentToken() != JsonToken.END_ARRAY) {
                    array.add(readValue(parser));
                }
                parser.nextToken();
                return array;
            case VALUE_STRING: {
                String value = parser.getText();
                parser.nextToken();
                return value;
            }
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT: {
                Number value = parser.getNumberValue();
                parser.nextToken();
                return value;
            }
            case VALUE_TRUE:
            case VALUE_FALSE: {
                boolean value = parser.getBooleanValue();
                parser.nextToken();
                return value;
            }
            case VALUE_NULL:
                parser.nextToken();
                return null;
            default:
                throw new IOException("Unexpected token: " + token);
        }
    }
}
