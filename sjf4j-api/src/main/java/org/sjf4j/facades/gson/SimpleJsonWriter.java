package org.sjf4j.facades.gson;

import com.google.gson.stream.JsonWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

public class SimpleJsonWriter {


    public static void writeValue(JsonWriter writer, Object value) throws IOException {
        if (value == null) {
            writer.nullValue();
        } else if (value instanceof JsonObject) {
            JsonObject jo = (JsonObject) value;
            writer.beginObject();
            for (Map.Entry<String, Object> entry : jo.entrySet()) {
                writer.name(entry.getKey());
                writeValue(writer, entry.getValue());
            }
            writer.endObject();
        } else if (value instanceof JsonArray) {
            JsonArray ja = (JsonArray) value;
            writer.beginArray();
            for (int i = 0; i < ja.size(); i++) {
                writeValue(writer, ja.getObject(i));
            }
            writer.endArray();
        } else if (value instanceof String) {
            writer.value((String) value);
        } else if (value instanceof Number) {
            writer.value((Number) value);
        } else if (value instanceof Boolean) {
            writer.value((Boolean) value);
        } else {
            throw new IllegalStateException("Unexpected value type: " + value.getClass().getName());
        }
    }


}
