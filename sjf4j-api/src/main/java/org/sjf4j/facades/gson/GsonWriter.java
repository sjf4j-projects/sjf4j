package org.sjf4j.facades.gson;

import com.google.gson.stream.JsonWriter;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.ConverterRegistry;
import org.sjf4j.PojoRegistry;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

public class GsonWriter {


    public static void writeAny(@NonNull JsonWriter writer, Object object) throws IOException {
        Object value = ConverterRegistry.tryObject2Node(object);
        if (value == null) {
            writer.nullValue();
        } else if (value instanceof CharSequence || value instanceof Character) {
            writer.value(value.toString());
        } else if (value instanceof Number) {
            writer.value((Number) value);
        } else if (value instanceof Boolean) {
            writer.value((Boolean) value);
        } else if (value instanceof JsonObject) {
            writer.beginObject();
            ((JsonObject) value).forEach((k, v) -> {
                try {
                    writer.name(k);
                    writeAny(writer, v);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
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
        } else if (PojoRegistry.isPojo(value.getClass())) {
            writer.beginObject();
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry :
                    PojoRegistry.getPojoInfo(value.getClass()).getFields().entrySet()) {
                writer.name(entry.getKey());
                Object vv = entry.getValue().invokeGetter(value);
                writeAny(writer, vv);
            }
            writer.endObject();
        } else {
            throw new IllegalStateException("Unsupported object type '" + object.getClass().getName() +
                    "', expected one of [JsonObject, JsonArray, String, Number, Boolean] or a type registered in " +
                    "ObjectRegistry or a valid POJO, or a Map/List/Array of such elements.");
        }
    }


}
