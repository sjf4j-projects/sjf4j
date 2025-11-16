package org.sjf4j.facades.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import lombok.NonNull;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.util.TypeReference;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public class Fastjson2JsonFacade implements JsonFacade<Fastjson2Reader, Fastjson2Writer> {

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
    public Fastjson2Reader createReader(Reader input) {
        JSONReader reader = JSONReader.of(input, new JSONReader.Context(readerFeatures));
        return new Fastjson2Reader(reader);
    }

    @Override
    public Fastjson2Writer createWriter(Writer output) {
        JSONWriter writer = JSONWriter.of(writerFeatures);
        return new Fastjson2Writer(writer);
    }


    public Object readNode(@NonNull Reader input, Type type) {
        try {
            JSONReader reader = JSONReader.of(input, new JSONReader.Context(readerFeatures));
            return Fastjson2StreamingUtil.readNode(reader, type);
        } catch (IOException e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
        }
    }

    public void writeNode(@NonNull Writer output, Object node) {
        try {
            JSONWriter writer = JSONWriter.of(writerFeatures);
            Fastjson2StreamingUtil.startDocument(writer);
            Fastjson2StreamingUtil.writeNode(writer, node);
            Fastjson2StreamingUtil.endDocument(writer);
            writer.flushTo(output);
        } catch (IOException e) {
            throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) + "'", e);
        }
    }


    @SuppressWarnings("unchecked")
    public <T> T readObject(@NonNull Reader input, @NonNull Class<T> clazz) {
        try {
            return (T) readNode(input, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + clazz + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    public  <T> T readObject(@NonNull Reader input, @NonNull TypeReference<T> type) {
        try {
            return (T) readNode(input, type);
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
        }
    }

    public JsonObject readObject(@NonNull Reader input) {
        try {
            return readObject(input, JsonObject.class);
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type 'JsonObject'", e);
        }
    }

}
