package org.sjf4j.gson;


import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.util.NumberUtil;

import java.io.IOException;

public class SimpleJsonReader {

    public static Object readAny(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT:
                return readObject(reader);
            case BEGIN_ARRAY:
                return readArray(reader);
            case STRING:
                return reader.nextString();
            case NUMBER:
                return readNumber(reader);
            case BOOLEAN:
                return reader.nextBoolean();
            case NULL:
                reader.nextNull();
                return null;
            default:
                throw new IllegalStateException("Unexpected token: " + token);
        }
    }

    public static JsonObject readObject(JsonReader reader) throws IOException {
        JsonObject jo = new JsonObject();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            Object value = readAny(reader);
            jo.put(name, value);
        }
        reader.endObject();
        return jo;
    }

    public static JsonArray readArray(JsonReader reader) throws IOException {
        JsonArray ja = new JsonArray();
        reader.beginArray();
        while (reader.hasNext()) {
            Object value = readAny(reader);
            ja.add(value);
        }
        reader.endArray();
        return ja;
    }

    public static Object readNumber(JsonReader reader) throws IOException {
        String num = reader.nextString();
        return NumberUtil.stringToNumber(num);
    }

}
