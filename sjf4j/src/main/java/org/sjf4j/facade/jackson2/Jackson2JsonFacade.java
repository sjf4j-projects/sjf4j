package org.sjf4j.facade.jackson2;


import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.node.NamingStrategy;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Jackson2-based JSON facade with selectable streaming modes.
 */
public class Jackson2JsonFacade implements JsonFacade<Jackson2Reader, Jackson2Writer> {
    private final StreamingMode streamingMode;

    private final ObjectMapper objectMapper;

    public Jackson2JsonFacade() {
        this(new ObjectMapper(), null);
    }

    public Jackson2JsonFacade(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    public Jackson2JsonFacade(StreamingMode streamingMode) {
        this(new ObjectMapper(), streamingMode);
    }

    /**
     * Creates facade with configured ObjectMapper and SJF4J module.
     */
    public Jackson2JsonFacade(ObjectMapper objectMapper, StreamingMode streamingMode) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        this.streamingMode = streamingMode == null ? StreamingMode.AUTO : streamingMode;

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        if (Sjf4jConfig.global().namingStrategy == NamingStrategy.SNAKE_CASE) {
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        }
//        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        this.objectMapper = objectMapper;
        this.objectMapper.registerModule(new Jackson2Module.MySimpleModule());
        AnnotationIntrospector serializationAi = this.objectMapper.getSerializationConfig().getAnnotationIntrospector();
        AnnotationIntrospector deserializationAi = this.objectMapper.getDeserializationConfig().getAnnotationIntrospector();
        this.objectMapper.setAnnotationIntrospectors(
                AnnotationIntrospectorPair.create(new Jackson2Module.NodePropertyAnnotationIntrospector(), serializationAi),
                AnnotationIntrospectorPair.create(new Jackson2Module.NodePropertyAnnotationIntrospector(), deserializationAi)
        );
    }


    /// Reader

    /**
     * Creates a streaming reader from java.io.Reader.
     */
    @Override
    public Jackson2Reader createReader(Reader input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson2Reader(objectMapper.getFactory().createParser(input));
    }

    /**
     * Creates a streaming reader from InputStream.
     */
    @Override
    public Jackson2Reader createReader(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson2Reader(objectMapper.getFactory().createParser(input));
    }

    /**
     * Creates a streaming reader from JSON string.
     */
    @Override
    public Jackson2Reader createReader(String input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson2Reader(objectMapper.getFactory().createParser(input));
    }

    /**
     * Creates a streaming reader from JSON bytes.
     */
    @Override
    public Jackson2Reader createReader(byte[] input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson2Reader(objectMapper.getFactory().createParser(input));
    }

    /**
     * Reads JSON from reader into target type.
     */
    @Override
    public Object readNode(Reader input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (runtimeMode()) {
            case SHARED_IO: {
                return JsonFacade.super.readNode(input, type);
            }
            case EXCLUSIVE_IO: {
                try {
                    JsonParser parser = objectMapper.getFactory().createParser(input);
                    return Jackson2StreamingIO.readNode(parser, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            }
            case PLUGIN_MODULE:
            {
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
        }
    }

    /**
     * Reads JSON from input stream into target type.
     */
    @Override
    public Object readNode(InputStream input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (runtimeMode()) {
            case SHARED_IO: {
                return JsonFacade.super.readNode(input, type);
            }
            case EXCLUSIVE_IO: {
                try {
                    JsonParser parser = objectMapper.getFactory().createParser(input);
                    return Jackson2StreamingIO.readNode(parser, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            }
            case PLUGIN_MODULE:
            {
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
        }
    }

    /**
     * Reads JSON from string into target type.
     */
    @Override
    public Object readNode(String input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (runtimeMode()) {
            case SHARED_IO: {
                return JsonFacade.super.readNode(input, type);
            }
            case EXCLUSIVE_IO: {
                try (JsonParser parser = objectMapper.getFactory().createParser(input)) {
                    return Jackson2StreamingIO.readNode(parser, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON string into node type '" + type + "'", e);
                }
            }
            case PLUGIN_MODULE:
            {
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON string into node type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
        }
    }

    /**
     * Reads JSON from bytes into target type.
     */
    @Override
    public Object readNode(byte[] input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (runtimeMode()) {
            case SHARED_IO: {
                return JsonFacade.super.readNode(input, type);
            }
            case EXCLUSIVE_IO: {
                try (JsonParser parser = objectMapper.getFactory().createParser(input)) {
                    return Jackson2StreamingIO.readNode(parser, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON byte[] into node type '" + type + "'", e);
                }
            }
            case PLUGIN_MODULE:
            {
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON byte[] into node type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
        }
    }


    /// Writer

    /**
     * Creates a streaming writer to java.io.Writer.
     */
    @Override
    public Jackson2Writer createWriter(Writer output) throws IOException {
        return new Jackson2Writer(objectMapper.getFactory().createGenerator(output));
    }

    /**
     * Creates a streaming writer to OutputStream.
     */
    @Override
    public Jackson2Writer createWriter(OutputStream output) throws IOException {
        return new Jackson2Writer(objectMapper.getFactory().createGenerator(output, JsonEncoding.UTF8));
    }

    /**
     * Writes node as JSON to writer.
     */
    @Override
    public void writeNode(Writer output, Object node) {
        Objects.requireNonNull(output, "output");
        switch (runtimeMode()) {
            case SHARED_IO: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case EXCLUSIVE_IO: {
                try {
                    JsonGenerator gen = objectMapper.getFactory().createGenerator(output);
                    Jackson2StreamingIO.writeNode(gen, node);
                    gen.flush();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON streaming", e);
                }
                break;
            }
            case PLUGIN_MODULE:
            {
                try {
                    objectMapper.writeValue(output, node);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON streaming", e);
                }
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }

    /**
     * Writes node as JSON to output stream.
     */
    @Override
    public void writeNode(OutputStream output, Object node) {
        Objects.requireNonNull(output, "output");
        switch (runtimeMode()) {
            case SHARED_IO: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case EXCLUSIVE_IO: {
                try {
                    JsonGenerator gen = objectMapper.getFactory().createGenerator(output);
                    Jackson2StreamingIO.writeNode(gen, node);
                    gen.flush();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON streaming", e);
                }
                break;
            }
            case PLUGIN_MODULE:
            {
                try {
                    objectMapper.writeValue(output, node);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON streaming", e);
                }
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }

    /**
     * Serializes node as JSON string.
     */
    @Override
    public String writeNodeAsString(Object node) {
        switch (runtimeMode()) {
            case SHARED_IO: {
                return JsonFacade.super.writeNodeAsString(node);
            }
            case EXCLUSIVE_IO: {
                final BufferRecycler br = objectMapper.getFactory()._getBufferRecycler();
                try (SegmentedStringWriter sw = new SegmentedStringWriter(br)) {
                    JsonGenerator gen = objectMapper.getFactory().createGenerator(sw);
                    Jackson2StreamingIO.writeNode(gen, node);
                    return sw.getAndClear();
                } catch (Exception e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON string", e);
                } finally {
                    br.releaseToPool();
                }
            }
            case PLUGIN_MODULE:
            {
                try {
                    return objectMapper.writeValueAsString(node);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON string", e);
                }
            }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }

    /**
     * Serializes node as JSON bytes.
     */
    @Override
    public byte[] writeNodeAsBytes(Object node) {
        switch (runtimeMode()) {
            case SHARED_IO: {
                return JsonFacade.super.writeNodeAsBytes(node);
            }
            case EXCLUSIVE_IO: {
                final BufferRecycler br = objectMapper.getFactory()._getBufferRecycler();
                try (ByteArrayBuilder bb = new ByteArrayBuilder(br)) {
                    JsonGenerator gen = objectMapper.getFactory().createGenerator(bb);
                    Jackson2StreamingIO.writeNode(gen, node);
                    final byte[] result = bb.toByteArray();
                    bb.release();
                    return result;
                } catch (Exception e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON bytes", e);
                } finally {
                    br.releaseToPool();
                }
            }
            case PLUGIN_MODULE:
            {
                try {
                    return objectMapper.writeValueAsBytes(node);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON bytes", e);
                }
            }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }

    private StreamingMode runtimeMode() {
        return StreamingFacade.resolveRuntimeMode(streamingMode, true, true);
    }



}
