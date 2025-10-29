package org.sjf4j.facades.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.JsonObject;

import java.io.Reader;
import java.io.Writer;

public class Fastjson2JsonFacade implements JsonFacade {

    private final JSONReader.Feature[] readerFeatures;
    private final JSONWriter.Feature[] writerFeatures;

    public Fastjson2JsonFacade() {
        this(new JSONReader.Feature[0], new JSONWriter.Feature[0]);
    }

    public Fastjson2JsonFacade(JSONWriter.Feature... writerFeatures) {
        this(new JSONReader.Feature[0], writerFeatures);
    }

    public Fastjson2JsonFacade(JSONReader.Feature... readerFeatures) {
        this(readerFeatures, new JSONWriter.Feature[0]);
    }

    public Fastjson2JsonFacade(@NonNull JSONReader.Feature[] readerFeatures,
                               @NonNull JSONWriter.Feature[] writerFeatures) {
        this.readerFeatures = readerFeatures;
        this.writerFeatures = writerFeatures;
    }

    @Override
    public JsonObject readObject(@NonNull Reader input) {
        Object value;
        try {
            JSONReader reader = JSONReader.of(input, new JSONReader.Context(readerFeatures));
            value = SimpleFastjson2Reader.readAny(reader);
        } catch (Exception e) {
            throw new JsonException("Failed to deserialize JSON into JsonObject: " + e.getMessage(), e);
        }

        if (value instanceof JsonObject) {
            return (JsonObject) value;
        } else {
            throw new JsonException("Expected JsonObject but got '" +
                    (value == null ? "[null]" : value.getClass().getName()) + "'");
        }
    }

    @Override
    public JsonArray readArray(@NonNull Reader input) {
        Object value;
        try {
            JSONReader reader = JSONReader.of(input, new JSONReader.Context(readerFeatures));
            value = SimpleFastjson2Reader.readAny(reader);
        } catch (Exception e) {
            throw new JsonException("Failed to deserialize JSON into JsonArray: " + e.getMessage(), e);
        }

        if (value instanceof JsonArray) {
            return (JsonArray) value;
        } else {
            throw new JsonException("Expected JsonArray but got '" +
                    (value == null ? "[null]" : value.getClass().getName()) + "'");
        }
    }

    @Override
    public void writeObject(@NonNull Writer output, JsonObject jo) {
        try (JSONWriter writer = JSONWriter.of(writerFeatures)) {
            SimpleFastjson2Writer.writeAny(writer, jo);
            output.write(writer.toString());
        } catch (Exception e) {
            throw new JsonException("Failed to serialize JsonObject to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public void writeArray(@NonNull Writer output, JsonArray ja) {
        try (JSONWriter writer = JSONWriter.of(writerFeatures)) {
            SimpleFastjson2Writer.writeAny(writer, ja);
            output.write(writer.toString());
        } catch (Exception e) {
            throw new JsonException("Failed to serialize JsonArray to JSON: " + e.getMessage(), e);
        }
    }

}
