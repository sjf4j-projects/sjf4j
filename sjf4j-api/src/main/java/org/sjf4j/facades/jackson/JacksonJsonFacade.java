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
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public class JacksonJsonFacade implements JsonFacade<JacksonReader, JacksonWriter> {

    private final ObjectMapper objectMapper;

    public JacksonJsonFacade(@NonNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        JacksonModule.MySimpleModule module = null;
        if (JsonConfig.global().readMode == JsonConfig.ReadMode.USE_MODULE) {
            module = new JacksonModule.MySimpleModule();
            module.addDeserializer(JsonArray.class, new JacksonModule.JsonArrayDeserializer());
        }
        if (JsonConfig.global().writeMode == JsonConfig.WriteMode.USE_MODULE) {
            if (module == null) module = new JacksonModule.MySimpleModule();
            module.addSerializer(JsonObject.class, new JacksonModule.JsonObjectSerializer());
            module.addSerializer(JsonArray.class, new JacksonModule.JsonArraySerializer());
        }
        if (module != null) this.objectMapper.registerModule(module);
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

    @Override
    public Object readNode(@NonNull Reader input, Type type) {
        switch (JsonConfig.global().readMode) {
            case STREAMING_GENERAL:
                return readNodeWithGeneral(input, type);
            case STREAMING_SPECIFIC:
                return readNodeWithSpecific(input, type);
            case USE_MODULE:
                return readNodeWithModule(input, type);
            default:
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }

    public Object readNodeWithGeneral(@NonNull Reader input, Type type) {
        return JsonFacade.super.readNode(input, type);
    }

    public Object readNodeWithSpecific(@NonNull Reader input, Type type) {
        try (JsonParser parser = objectMapper.getFactory().createParser(input)) {
            return JacksonStreamingUtil.readNode(parser, type);
        } catch (Exception e) {
            throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
        }
    }

    public Object readNodeWithModule(@NonNull Reader input, Type type) {
        try {
            if (JsonConfig.global().readMode == JsonConfig.ReadMode.USE_MODULE) {
                return objectMapper.readValue(input, objectMapper.constructType(type));
            } else {
                JacksonModule.MySimpleModule module = new JacksonModule.MySimpleModule();
                module.addDeserializer(JsonArray.class, new JacksonModule.JsonArrayDeserializer());
                ObjectMapper om = new ObjectMapper();
                om.registerModule(module);
                return om.readValue(input, om.constructType(type));
            }
        } catch (Exception e) {
            throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
        }
    }

    /// Write

    @Override
    public void writeNode(@NonNull Writer output, Object node) {
        switch (JsonConfig.global().writeMode) {
            case STREAMING_GENERAL:
                writeNodeWithGeneral(output, node);
                break;
            case STREAMING_SPECIFIC:
                writeNodeWithSpecific(output, node);
                break;
            case USE_MODULE:
                writeNodeWithModule(output, node);
                break;
            default:
                throw new JsonException("Unsupported write mode '" + JsonConfig.global().writeMode + "'");
        }
    }

    public void writeNodeWithGeneral(Writer output, Object node) {
        JsonFacade.super.writeNode(output, node);
    }

    public void writeNodeWithSpecific(@NonNull Writer output, Object node) {
        try {
            JsonGenerator gen = objectMapper.getFactory().createGenerator(output);
            JacksonStreamingUtil.startDocument(gen);
            JacksonStreamingUtil.writeNode(gen, node);
            JacksonStreamingUtil.endDocument(gen);
            gen.flush();
        } catch (IOException e) {
            throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                    "' to JSON streaming", e);
        }
    }

    public void writeNodeWithModule(Writer output, Object node) {
        try {
            if (JsonConfig.global().writeMode == JsonConfig.WriteMode.USE_MODULE) {
                objectMapper.writeValue(output, node);
            } else {
                SimpleModule module = new JacksonModule.MySimpleModule();
                module.addSerializer(JsonObject.class, new JacksonModule.JsonObjectSerializer());
                module.addSerializer(JsonArray.class, new JacksonModule.JsonArraySerializer());
                ObjectMapper om = new ObjectMapper();
                om.registerModule(module);
                om.writeValue(output, node);
            }
        } catch (IOException e) {
            throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                    "' to JSON streaming", e);
        }
    }

}
