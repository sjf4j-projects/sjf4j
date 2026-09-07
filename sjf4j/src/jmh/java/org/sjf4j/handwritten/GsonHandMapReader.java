package org.sjf4j.handwritten;

import com.google.gson.stream.JsonReader;
import org.sjf4j.node.Numbers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Direct Gson streaming reader for an untyped JSON object graph. */
public final class GsonHandMapReader {

    private GsonHandMapReader() {}

    public static Object read(JsonReader reader) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                LinkedHashMap<String, Object> object = new LinkedHashMap<String, Object>();
                reader.beginObject();
                while (reader.hasNext()) {
                    object.put(reader.nextName(), read(reader));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                ArrayList<Object> array = new ArrayList<Object>();
                reader.beginArray();
                while (reader.hasNext()) {
                    array.add(read(reader));
                }
                reader.endArray();
                return array;
            case STRING:
                return reader.nextString();
            case NUMBER:
                // Matches the MyToNumberStrategy configured for Gson's native Map reader.
                return Numbers.parseNumber(reader.nextString());
            case BOOLEAN:
                return reader.nextBoolean();
            case NULL:
                reader.nextNull();
                return null;
            default:
                throw new IOException("Unexpected token: " + reader.peek());
        }
    }
}
