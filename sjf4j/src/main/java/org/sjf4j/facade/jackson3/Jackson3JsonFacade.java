package org.sjf4j.facade.jackson3;

import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.node.Types;
import tools.jackson.databind.AnnotationIntrospector;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.introspect.AnnotationIntrospectorPair;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
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
    private final JsonMapper jsonMapper;

    public Jackson3JsonFacade() {
        this(new JsonMapper(), null);
    }

    public Jackson3JsonFacade(JsonMapper jsonMapper) {
        this(jsonMapper, null);
    }

    public Jackson3JsonFacade(StreamingMode streamingMode) {
        this(new JsonMapper(), streamingMode);
    }

    /**
     * Creates facade with configured JsonMapper and SJF4J module.
     */
    public Jackson3JsonFacade(JsonMapper jsonMapper, StreamingMode streamingMode) {
        Objects.requireNonNull(jsonMapper, "jsonMapper");
        // Jackson defaults to module-backed read/write so AUTO matches the highest-fidelity path.
        this.streamingMode = streamingMode == null || streamingMode == StreamingMode.AUTO ?
                StreamingMode.PLUGIN_MODULE : streamingMode;

        MapperBuilder<?, ?> builder = jsonMapper.rebuild();
        builder.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        builder.addModule(new Jackson3Module.TwoSimpleModule());
        AnnotationIntrospector existing = builder.annotationIntrospector();
        builder.annotationIntrospector(AnnotationIntrospectorPair.create(
                new Jackson3Module.NodePropertyAnnotationIntrospector(), existing));

        this.jsonMapper = (JsonMapper) builder.build();
    }

    @Override
    public StreamingMode streamingMode() {
        return streamingMode;
    }


    /// Reader

    @Override
    public Jackson3Reader createReader(Reader input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson3Reader(jsonMapper.createParser(input));
    }

    @Override
    public Jackson3Reader createReader(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson3Reader(jsonMapper.createParser(input));
    }

    @Override
    public Jackson3Reader createReader(String input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson3Reader(jsonMapper.createParser(input));
    }

    @Override
    public Jackson3Reader createReader(byte[] input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Jackson3Reader(jsonMapper.createParser(input));
    }

    @Override
    public Object readNodePlugin(Reader input, Type type) {
        try {
            return jsonMapper.readValue(input, jsonMapper.constructType(type));
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodePlugin(InputStream input, Type type) {
        try {
            return jsonMapper.readValue(input, jsonMapper.constructType(type));
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodePlugin(String input, Type type) {
        try {
            return jsonMapper.readValue(input, jsonMapper.constructType(type));
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodePlugin(byte[] input, Type type) {
        try {
            return jsonMapper.readValue(input, jsonMapper.constructType(type));
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }


    /// Writer

    @Override
    public Jackson3Writer createWriter(Writer output) {
        Objects.requireNonNull(output, "output");
        return new Jackson3Writer(jsonMapper.createGenerator(output));
    }

    @Override
    public Jackson3Writer createWriter(OutputStream output) {
        Objects.requireNonNull(output, "output");
        return new Jackson3Writer(jsonMapper.createGenerator(output));
    }

    @Override
    public void writeNodePlugin(Writer output, Object node) {
        try {
            jsonMapper.writeValue(output, node);
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public void writeNodePlugin(OutputStream output, Object node) {
        try {
            jsonMapper.writeValue(output, node);
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public String writeNodeAsStringPlugin(Object node) {
        try {
            return jsonMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public byte[] writeNodeAsBytesPlugin(Object node) {
        try {
            return jsonMapper.writeValueAsBytes(node);
        } catch (Exception e) {
            throw failedToWrite(node, e);
        }
    }

}
