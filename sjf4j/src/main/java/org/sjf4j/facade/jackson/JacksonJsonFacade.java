package org.sjf4j.facade.jackson;


import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Objects;

public class JacksonJsonFacade implements JsonFacade<JacksonReader, JacksonWriter> {

    private final ObjectMapper objectMapper;
    private final JacksonModule.MySimpleModule module;

    public JacksonJsonFacade(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper is null");

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        this.objectMapper = objectMapper;
        this.module = new JacksonModule.MySimpleModule();
        this.objectMapper.registerModule(this.module);
        this.objectMapper.setAnnotationIntrospector(new JacksonModule.NodePropertyAnnotationIntrospector());
    }


    /// Reader

    @Override
    public JacksonReader createReader(Reader input) throws IOException {
        Objects.requireNonNull(input, "input is null");
        return new JacksonReader(objectMapper.getFactory().createParser(input));
    }

    @Override
    public JacksonReader createReader(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input is null");
        return new JacksonReader(objectMapper.getFactory().createParser(input));
    }

    @Override
    public JacksonReader createReader(String input) throws IOException {
        Objects.requireNonNull(input, "input is null");
        return new JacksonReader(objectMapper.getFactory().createParser(input));
    }

    @Override
    public JacksonReader createReader(byte[] input) throws IOException {
        Objects.requireNonNull(input, "input is null");
        return new JacksonReader(objectMapper.getFactory().createParser(input));
    }

    @Override
    public Object readNode(Reader input, Type type) {
        Objects.requireNonNull(input, "input is null");
        switch (Sjf4jConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try {
                    JsonParser parser = objectMapper.getFactory().createParser(input);
                    return JacksonStreamingIO.readNode(parser, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + Sjf4jConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(InputStream input, Type type) {
        Objects.requireNonNull(input, "input is null");
        switch (Sjf4jConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try {
                    JsonParser parser = objectMapper.getFactory().createParser(input);
                    return JacksonStreamingIO.readNode(parser, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + Sjf4jConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(String input, Type type) {
        Objects.requireNonNull(input, "input is null");
        switch (Sjf4jConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try {
                    JsonParser parser = objectMapper.getFactory().createParser(input);
                    return JacksonStreamingIO.readNode(parser, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON string into node type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON string into node type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + Sjf4jConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(byte[] input, Type type) {
        Objects.requireNonNull(input, "input is null");
        switch (Sjf4jConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try {
                    JsonParser parser = objectMapper.getFactory().createParser(input);
                    return JacksonStreamingIO.readNode(parser, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON byte[] into node type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON byte[] into node type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + Sjf4jConfig.global().readMode + "'");
        }
    }


    /// Writer

    @Override
    public JacksonWriter createWriter(Writer output) throws IOException {
        return new JacksonWriter(objectMapper.getFactory().createGenerator(output));
    }

    @Override
    public JacksonWriter createWriter(OutputStream output) throws IOException {
        return new JacksonWriter(objectMapper.getFactory().createGenerator(output, JsonEncoding.UTF8));
    }

    @Override
    public void writeNode(Writer output, Object node) {
        Objects.requireNonNull(output, "output is null");
        switch (Sjf4jConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
                try {
                    JsonGenerator gen = objectMapper.getFactory().createGenerator(output);
                    JacksonStreamingIO.writeNode(gen, node);
                    gen.flush();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON streaming", e);
                }
                break;
            }
            case USE_MODULE: {
                try {
                    objectMapper.writeValue(output, node);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON streaming", e);
                }
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + Sjf4jConfig.global().writeMode + "'");
        }
    }

    @Override
    public void writeNode(OutputStream output, Object node) {
        Objects.requireNonNull(output, "output is null");
        switch (Sjf4jConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
                try {
                    JsonGenerator gen = objectMapper.getFactory().createGenerator(output);
                    JacksonStreamingIO.writeNode(gen, node);
                    gen.flush();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON streaming", e);
                }
                break;
            }
            case USE_MODULE: {
                try {
                    objectMapper.writeValue(output, node);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON streaming", e);
                }
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + Sjf4jConfig.global().writeMode + "'");
        }
    }

    @Override
    public String writeNodeAsString(Object node) {
        switch (Sjf4jConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.writeNodeAsString(node);
            }
            case STREAMING_SPECIFIC: {
                final BufferRecycler br = objectMapper.getFactory()._getBufferRecycler();
                try (SegmentedStringWriter sw = new SegmentedStringWriter(br)) {
                    JsonGenerator gen = objectMapper.getFactory().createGenerator(sw);
                    JacksonStreamingIO.writeNode(gen, node);
                    return sw.getAndClear();
                } catch (Exception e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON string", e);
                } finally {
                    br.releaseToPool();
                }
            }
            case USE_MODULE: {
                try {
                    return objectMapper.writeValueAsString(node);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON string", e);
                }
            }
            default:
                throw new JsonException("Unsupported write mode '" + Sjf4jConfig.global().writeMode + "'");
        }
    }

    @Override
    public byte[] writeNodeAsBytes(Object node) {
        switch (Sjf4jConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.writeNodeAsBytes(node);
            }
            case STREAMING_SPECIFIC: {
                final BufferRecycler br = objectMapper.getFactory()._getBufferRecycler();
                try (ByteArrayBuilder bb = new ByteArrayBuilder(br)) {
                    JsonGenerator gen = objectMapper.getFactory().createGenerator(bb);
                    JacksonStreamingIO.writeNode(gen, node);
                    final byte[] result = bb.toByteArray();
                    bb.release();
                    return result;
                } catch (Exception e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON bytes", e);
                } finally {
                    br.releaseToPool();
                }
            }
            case USE_MODULE: {
                try {
                    return objectMapper.writeValueAsBytes(node);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON bytes", e);
                }
            }
            default:
                throw new JsonException("Unsupported write mode '" + Sjf4jConfig.global().writeMode + "'");
        }
    }



}
