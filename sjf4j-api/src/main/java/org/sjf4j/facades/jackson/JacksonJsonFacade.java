package org.sjf4j.facades.jackson;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.NonNull;
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

//    public Object readNode(@NonNull Reader input, Type type) {
//        try {
//            JsonParser parser = objectMapper.getFactory().createParser(input);
//            JacksonStreamingUtil.startDocument(parser);
//            Object node = JacksonStreamingUtil.readNode(parser, type);
//            JacksonStreamingUtil.endDocument(parser);
//            JsonParser parser = objectMapper.getFactory().createParser(input);
//            JacksonReader reader = new JacksonReader(parser);
//            JacksonReader reader = createReader(input);
//            reader.startDocument();
//            Object node = StreamingUtil.readNode(reader, type);
//            reader.endDocument();
//            return node;
//        } catch (IOException e) {
//            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
//        }
//    }

    public void writeNode(Writer output, Object node) {
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
