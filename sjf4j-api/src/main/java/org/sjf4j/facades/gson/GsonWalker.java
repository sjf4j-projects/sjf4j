package org.sjf4j.facades.gson;


import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.io.IOException;

public class GsonWalker {

    public static void walk2Null(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case NULL:
                reader.nextNull();
                return;
            case STRING:
                reader.nextString();
                return;
            case NUMBER:
                reader.nextString();
                return;
            case BOOLEAN:
                reader.nextBoolean();
                return;
            case BEGIN_OBJECT:
                reader.beginObject();
                while (reader.hasNext()) {
                    reader.nextName();
                    walk2Null(reader);
                }
                reader.endObject();
                return;
            case BEGIN_ARRAY:
                reader.beginArray();
                while (reader.hasNext()) {
                    walk2Null(reader);
                }
                reader.endArray();
                return;
            default:
                throw new IllegalStateException("Unexpected token: " + token);
        }
    }


    public static Object walk2Jo(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case NULL:
                reader.nextNull();
                return null;
            case STRING:
                return reader.nextString();
            case NUMBER:
                return reader.nextString();
            case BOOLEAN:
                return reader.nextBoolean();
            case BEGIN_OBJECT:
                JsonObject jo = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    Object value = walk2Jo(reader);
                    jo.put(key, value);
                }
                reader.endObject();
                return jo;
            case BEGIN_ARRAY:
                JsonArray ja = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    Object value = walk2Jo(reader);
                    ja.add(value);
                }
                reader.endArray();
                return ja;
            default:
                throw new IllegalStateException("Unexpected token: " + token);
        }
    }

}
