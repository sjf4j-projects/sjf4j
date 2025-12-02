package org.sjf4j.facades.jackson;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    public Object readNode(@NonNull Reader input, Type type) {
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
    public Object readNode(@NonNull InputStream input, Type type) {
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
    public Object readNode(@NonNull String input, Type type) {
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
    public void writeNode(@NonNull Writer output, Object node) {
        switch (JsonConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
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
    public void writeNode(@NonNull OutputStream output, Object node) {
        switch (JsonConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
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
