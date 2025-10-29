package org.sjf4j.facades.gson;

import com.google.gson.stream.JsonWriter;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.util.ValueHandler;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

public class SimpleGsonWriter {


    public static void writeAny(@NonNull JsonWriter writer, Object object) throws IOException {
        Object value = ValueHandler.object2Value(object);
        if (value == null) {
            writer.nullValue();
        } else if (value instanceof String || value instanceof Character) {
            writer.value(value.toString());
        } else if (value instanceof Number) {
            writer.value((Number) value);
        } else if (value instanceof Boolean) {
            writer.value((Boolean) value);
        } else if (value instanceof JsonObject) {
            writer.beginObject();
            for (Map.Entry<String, Object> entry : ((JsonObject) value).entrySet()) {
                writer.name(entry.getKey());
                writeAny(writer, entry.getValue());
            }
            writer.endObject();
        } else if (value instanceof Map) {
            writer.beginObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                writer.name(entry.getKey().toString());
                writeAny(writer, entry.getValue());
            }
            writer.endObject();
        } else if (value instanceof JsonArray) {
            writer.beginArray();
            for (Object v : (JsonArray) value) {
                writeAny(writer, v);
            }
            writer.endArray();
        } else if (value instanceof List) {
            writer.beginArray();
            for (Object v : (List<?>) value) {
                writeAny(writer, v);
            }
            writer.endArray();
        } else if (value.getClass().isArray()) {
            writer.beginArray();
            for (int i = 0; i < Array.getLength(value); i++) {
                writeAny(writer, Array.get(value, i));
            }
            writer.endArray();
        } else {
            throw new IllegalStateException("Unsupported object type '" + object.getClass().getName() +
                    "', expected one of [JsonObject, JsonArray, String, Number, Boolean] or a type registered in " +
                    "ValueRegistry, or a Map/List/Array of such elements.");
        }
    }


}
