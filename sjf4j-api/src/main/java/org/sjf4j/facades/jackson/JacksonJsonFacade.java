package org.sjf4j.facades.jackson;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.FacadeReader;
import org.sjf4j.facades.FacadeWriter;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.fastjson2.Fastjson2Writer;
import org.sjf4j.util.StreamingUtil;
import org.sjf4j.util.TypeReference;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public class JacksonJsonFacade implements JsonFacade<JacksonReader, JacksonWriter> {

    private final ObjectMapper objectMapper;

    public JacksonJsonFacade(@NonNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        if (JsonConfig.global().facadeMode == JsonConfig.FacadeMode.MODULE_EXTRA) {
            JacksonModule.JsonObjectModule module = new JacksonModule.JsonObjectModule();
            module.addDeserializer(JsonArray.class, new JacksonModule.JsonArrayDeserializer());
            this.objectMapper.registerModule(module);
        }
    }

    @Override
    public JacksonReader createReader(Reader input) throws IOException {
        JsonParser parser = objectMapper.getFactory().createParser(input);
        return new JacksonReader(parser);
    }

    @Override
    public JacksonWriter createWriter(Writer output) throws IOException {
        JsonGenerator gen = objectMapper.getFactory().createGenerator(output);
        return new JacksonWriter(gen);
    }

    /// API

    public Object readNode(@NonNull Reader input, Type type) {
        try {
            switch (JsonConfig.global().facadeMode) {
                case STREAMING_GENERAL:
                    return readNodeWithSpecific(input, type);
                case STREAMING_SPECIFIC:
                    return readNodeWithSpecific(input, type);
                case MODULE_EXTRA:
                default:
                    return readNodeWithExtra(input, type);
            }
        } catch (Exception e) {
            throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
        }
    }

    public Object readNodeWithGeneral(@NonNull Reader input, Type type) throws IOException {
        return JsonFacade.super.readNode(input, type);
    }

    public Object readNodeWithSpecific(@NonNull Reader input, Type type) throws IOException {
        try (JsonParser parser = objectMapper.getFactory().createParser(input)) {
            return JacksonStreamingUtil.readNode(parser, type);
        }
    }

    public Object readNodeWithExtra(@NonNull Reader input, Type type) throws IOException {
        if (JsonConfig.global().facadeMode != JsonConfig.FacadeMode.MODULE_EXTRA) {
            ObjectMapper om = new ObjectMapper();
            JacksonModule.JsonObjectModule module = new JacksonModule.JsonObjectModule();
            module.addDeserializer(JsonArray.class, new JacksonModule.JsonArrayDeserializer());
            om.registerModule(module);
            return om.readValue(input, om.constructType(type));
        } else {
            return objectMapper.readValue(input, objectMapper.constructType(type));
        }
    }

    public void writeNode(@NonNull Writer output, Object node) {
        try {
            JsonGenerator gen = objectMapper.getFactory().createGenerator(output);
            JacksonStreamingUtil.startDocument(gen);
            JacksonStreamingUtil.writeNode(gen, node);
            JacksonStreamingUtil.endDocument(gen);
            gen.flush();
        } catch (IOException e) {
            throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) + "' to streaming", e);
        }
    }



}
