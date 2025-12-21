package org.sjf4j.facade.jackson;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeRegistry;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public class JacksonJsonFacade implements JsonFacade<JacksonReader, JacksonWriter> {

    private final ObjectMapper objectMapper;
    private final JacksonModule.MySimpleModule module;

    public JacksonJsonFacade(ObjectMapper objectMapper) {
        if (objectMapper == null) throw new IllegalArgumentException("ObjectMapper must not be null");
        this.objectMapper = objectMapper;

        this.module = new JacksonModule.MySimpleModule();
        if (JsonConfig.global().readMode == JsonConfig.ReadMode.USE_MODULE) {
            this.module.addDeserializer(JsonArray.class, new JacksonModule.JsonArrayDeserializer());
        }
        if (JsonConfig.global().writeMode == JsonConfig.WriteMode.USE_MODULE) {
            this.module.addSerializer(JsonObject.class, new JacksonModule.JsonObjectSerializer());
            this.module.addSerializer(JsonArray.class, new JacksonModule.JsonArraySerializer());
        }
        registerConvertibles();
        this.objectMapper.registerModule(this.module);
    }

    private void registerConvertibles() {
        for (NodeRegistry.ConvertibleInfo ci : NodeRegistry.getAllConvertibles().values()) {
            registerConvertible(ci);
        }
    }

    @Override
    public void registerConvertible(NodeRegistry.ConvertibleInfo ci) {
        this.module.addSerializer(ci.getNodeClass(), new JacksonModule.ConvertibleSerializer<>(ci));
        this.module.addDeserializer(ci.getNodeClass(), new JacksonModule.ConvertibleDeserializer<>(ci));
    }


    @Override
    public JacksonReader createReader(Reader input) throws IOException {
        return new JacksonReader(objectMapper.getFactory().createParser(input));
    }

    @Override
    public JacksonReader createReader(InputStream input) throws IOException {
        return new JacksonReader(objectMapper.getFactory().createParser(input));
    }

    @Override
    public JacksonReader createReader(String input) throws IOException {
        return new JacksonReader(objectMapper.getFactory().createParser(input));
    }

    @Override
    public JacksonReader createReader(byte[] input) throws IOException {
        return new JacksonReader(objectMapper.getFactory().createParser(input));
    }

    @Override
    public JacksonWriter createWriter(Writer output) throws IOException {
        return new JacksonWriter(objectMapper.getFactory().createGenerator(output));
    }

    @Override
    public JacksonWriter createWriter(OutputStream output) throws IOException {
        return new JacksonWriter(objectMapper.getFactory().createGenerator(output));
    }



    /// API

    @Override
    public Object readNode(Reader input, Type type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        switch (JsonConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try (JsonParser parser = objectMapper.getFactory().createParser(input)) {
                    return JacksonStreamingUtil.readNode(parser, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(InputStream input, Type type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        switch (JsonConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try (JsonParser parser = objectMapper.getFactory().createParser(input)) {
                    return JacksonStreamingUtil.readNode(parser, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(String input, Type type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        switch (JsonConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try (JsonParser parser = objectMapper.getFactory().createParser(input)) {
                    return JacksonStreamingUtil.readNode(parser, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(byte[] input, Type type) {
        switch (JsonConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try (JsonParser parser = objectMapper.getFactory().createParser(input)) {
                    return JacksonStreamingUtil.readNode(parser, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }

    /// Write

    @Override
    public void writeNode(Writer output, Object node) {
        if (output == null) throw new IllegalArgumentException("Output must not be null");
        switch (JsonConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
                try {
                    JsonGenerator gen = objectMapper.getFactory().createGenerator(output);
                    JacksonStreamingUtil.writeNode(gen, node);
                    gen.flush();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            case USE_MODULE: {
                try {
                    objectMapper.writeValue(output, node);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + JsonConfig.global().writeMode + "'");
        }
    }

    @Override
    public void writeNode(OutputStream output, Object node) {
        if (output == null) throw new IllegalArgumentException("Output must not be null");
        switch (JsonConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
                try {
                    JsonGenerator gen = objectMapper.getFactory().createGenerator(output);
                    JacksonStreamingUtil.writeNode(gen, node);
                    gen.flush();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            case USE_MODULE: {
                try {
                    objectMapper.writeValue(output, node);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + JsonConfig.global().writeMode + "'");
        }
    }


    /// Private

}
