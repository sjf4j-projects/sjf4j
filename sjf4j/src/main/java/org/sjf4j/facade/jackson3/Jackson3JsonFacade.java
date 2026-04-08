package org.sjf4j.facade.jackson3;

import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.node.Types;
import tools.jackson.databind.AnnotationIntrospector;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.introspect.AnnotationIntrospectorPair;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Jackson3-based JSON facade with selectable streaming modes.
 */
public class Jackson3JsonFacade implements JsonFacade<Jackson3Reader, Jackson3Writer> {
    private final StreamingMode streamingMode;

    private final ObjectMapper objectMapper;

    public Jackson3JsonFacade() {
        this(JsonMapper.builderWithJackson2Defaults().build(), null);
    }

    public Jackson3JsonFacade(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    public Jackson3JsonFacade(StreamingMode streamingMode) {
        this(JsonMapper.builderWithJackson2Defaults().build(), streamingMode);
    }

    /**
     * Creates facade with configured ObjectMapper and SJF4J module.
     */
    public Jackson3JsonFacade(ObjectMapper objectMapper, StreamingMode streamingMode) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        this.streamingMode = streamingMode == null ? StreamingMode.AUTO : streamingMode;

        MapperBuilder<?, ?> builder = objectMapper.rebuild();
        builder.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        builder.addModule(new Jackson3Module.MySimpleModule());
        AnnotationIntrospector existing = builder.annotationIntrospector();
        builder.annotationIntrospector(AnnotationIntrospectorPair.create(
                new Jackson3Module.NodePropertyAnnotationIntrospector(), existing));

        this.objectMapper = builder.build();
    }


    /// Reader

    @Override
    public Jackson3Reader createReader(Reader input) throws java.io.IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson3Reader(objectMapper.createParser(input));
    }

    @Override
    public Jackson3Reader createReader(InputStream input) throws java.io.IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson3Reader(objectMapper.createParser(input));
    }

    @Override
    public Jackson3Reader createReader(String input) throws java.io.IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson3Reader(objectMapper.createParser(input));
    }

    @Override
    public Jackson3Reader createReader(byte[] input) throws java.io.IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson3Reader(objectMapper.createParser(input));
    }

    @Override
    public Object readNode(Reader input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (runtimeMode()) {
            case SHARED_IO:
                return JsonFacade.super.readNode(input, type);
            case PLUGIN_MODULE:
            case AUTO:
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
        }
    }

    @Override
    public Object readNode(InputStream input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (runtimeMode()) {
            case SHARED_IO:
                return JsonFacade.super.readNode(input, type);
            case PLUGIN_MODULE:
            case AUTO:
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + type + "'", e);
                }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
        }
    }

    @Override
    public Object readNode(String input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (runtimeMode()) {
            case SHARED_IO:
                return JsonFacade.super.readNode(input, type);
            case PLUGIN_MODULE:
            case AUTO:
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON string into node type '" + type + "'", e);
                }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
        }
    }

    @Override
    public Object readNode(byte[] input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (runtimeMode()) {
            case SHARED_IO:
                return JsonFacade.super.readNode(input, type);
            case PLUGIN_MODULE:
            case AUTO:
                try {
                    return objectMapper.readValue(input, objectMapper.constructType(type));
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON byte[] into node type '" + type + "'", e);
                }
            default:
                throw new JsonException("Unsupported read mode '" + streamingMode + "'");
        }
    }


    /// Writer

    @Override
    public Jackson3Writer createWriter(Writer output) throws java.io.IOException {
        Objects.requireNonNull(output, "output");
        return new Jackson3Writer(objectMapper.createGenerator(output));
    }

    @Override
    public Jackson3Writer createWriter(OutputStream output) throws java.io.IOException {
        Objects.requireNonNull(output, "output");
        return new Jackson3Writer(objectMapper.createGenerator(output));
    }

    @Override
    public void writeNode(Writer output, Object node) {
        Objects.requireNonNull(output, "output");
        switch (runtimeMode()) {
            case SHARED_IO:
                JsonFacade.super.writeNode(output, node);
                return;
            case PLUGIN_MODULE:
            case AUTO:
                try {
                    objectMapper.writeValue(output, node);
                    return;
                } catch (Exception e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON streaming", e);
                }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }

    @Override
    public void writeNode(OutputStream output, Object node) {
        Objects.requireNonNull(output, "output");
        switch (runtimeMode()) {
            case SHARED_IO:
                JsonFacade.super.writeNode(output, node);
                return;
            case PLUGIN_MODULE:
            case AUTO:
                try {
                    objectMapper.writeValue(output, node);
                    return;
                } catch (Exception e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON streaming", e);
                }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }

    @Override
    public String writeNodeAsString(Object node) {
        switch (runtimeMode()) {
            case SHARED_IO:
                return JsonFacade.super.writeNodeAsString(node);
            case PLUGIN_MODULE:
            case AUTO:
                try {
                    return objectMapper.writeValueAsString(node);
                } catch (Exception e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON string", e);
                }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }

    @Override
    public byte[] writeNodeAsBytes(Object node) {
        switch (runtimeMode()) {
            case SHARED_IO:
                return JsonFacade.super.writeNodeAsBytes(node);
            case PLUGIN_MODULE:
            case AUTO:
                try {
                    return objectMapper.writeValueAsBytes(node);
                } catch (Exception e) {
                    throw new JsonException("Failed to write node type '" + Types.name(node) + "' to JSON bytes", e);
                }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }

    private StreamingMode runtimeMode() {
        return StreamingFacade.resolveRuntimeMode(streamingMode, true, false);
    }
}
