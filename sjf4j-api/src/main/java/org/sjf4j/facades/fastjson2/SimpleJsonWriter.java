package org.sjf4j.facades.fastjson2;


import com.alibaba.fastjson2.JSONWriter;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.util.Map;


public class SimpleJsonWriter {

    public static String toJson(@NonNull JsonObject jo, JSONWriter.Feature[] writerFeatures) {
        try (JSONWriter writer = JSONWriter.of(writerFeatures)) {
            writer.startObject();
            for (Map.Entry<String, Object> entry : jo.entrySet()) {
                writer.writeName(entry.getKey());
                writer.writeColon();
                Object value = entry.getValue();
                if (value instanceof JsonObject) {
                    writer.writeRaw(toJson((JsonObject) value, writerFeatures));
                } else if (value instanceof JsonArray) {
                    writer.writeRaw(toJson((JsonArray) value, writerFeatures));
                } else {
                    writer.writeAny(value);
                }
            }
            writer.endObject();
            return writer.toString();
        }
    }

    public static String toJson(@NonNull JsonArray ja, JSONWriter.Feature[] writerFeatures) {
        try (JSONWriter writer = JSONWriter.of(writerFeatures)) {
            writer.startArray();
            for (int i = 0; i < ja.size(); i++) {
                if (i > 0) writer.writeComma();
                Object value = ja.getObject(i);
                if (value instanceof JsonObject) {
                    writer.writeRaw(toJson((JsonObject) value, writerFeatures)); // 递归
                } else if (value instanceof JsonArray) {
                    writer.writeRaw(toJson((JsonArray) value, writerFeatures));
                } else {
                    writer.writeAny(value);
                }
            }
            writer.endArray();
            return writer.toString();
        }
    }
}
