package org.sjf4j.facades.fastjson2;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.jackson.JacksonStreamingUtil;
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

        // With Extra
        if (JsonConfig.global().facadeMode == JsonConfig.FacadeMode.MODULE_EXTRA) {
            ObjectReaderProvider provider = JSONFactory.getDefaultObjectReaderProvider();
            provider.register(JsonArray.class, new Fastjson2Module.JsonArrayReader());
        }
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


    /// Read

    public Object readNode(@NonNull Reader input, Type type) {
        try {
            switch (JsonConfig.global().facadeMode) {
                case STREAMING_GENERAL:
                    return JsonFacade.super.readNode(input, type);
                case STREAMING_SPECIFIC:
                    return readNodeWithSpecific(input, type);
                case MODULE_EXTRA:
                default:
                    return readNodeWithExtra(input, type);
            }
        } catch (IOException e) {
            throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
        }
    }

    public Object readNodeWithSpecific(@NonNull Reader input, Type type) throws IOException {
        JSONReader reader = JSONReader.of(input, new JSONReader.Context(readerFeatures));
        return Fastjson2StreamingUtil.readNode(reader, type);
    }

    public Object readNodeWithExtra(@NonNull Reader input, Type type) {
        JSONReader.Context ctx = JSONFactory.createReadContext(readerFeatures);
        ctx.setExtraProcessor((object, key, value) -> {
            if (object instanceof JsonObject) {
                ((JsonObject) object).put(key, value);
            }
        });
        return JSONReader.of(input, ctx).read(type);
    }


    /// Write

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


}
