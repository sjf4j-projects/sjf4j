package org.sjf4j.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonFacade;
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
    public JsonObject readObject(Reader input) {
        try {
            return SimpleJsonReader.readObject(input, readerFeatures);
        } catch (Exception e) {
            throw new JsonException("Failed to deserialize input into JsonObject: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonArray readArray(Reader input) {
        try {
            return SimpleJsonReader.readArray(input, readerFeatures);
        } catch (Exception e) {
            throw new JsonException("Failed to deserialize input into JsonArray: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(Writer output, JsonObject jo) {
        try {
            String json = SimpleJsonWriter.toJson(jo, writerFeatures);
            output.write(json);
        } catch (Exception e) {
            throw new JsonException("Failed to serialize JsonObject: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(Writer output, JsonArray ja) {
        try {
            String json = SimpleJsonWriter.toJson(ja, writerFeatures);
            output.write(json);
        } catch (Exception e) {
            throw new JsonException("Failed to serialize JsonArray: " + e.getMessage(), e);
        }
    }

}
