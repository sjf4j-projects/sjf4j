package org.sjf4j.facade.jackson3;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.FacadeProvider;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Types;
import tools.jackson.databind.AnnotationIntrospector;
import tools.jackson.databind.DeserializationFeature;
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
    private final JsonMapper jsonMapper;
    private final StreamingContext streamingContext;

    public Jackson3JsonFacade() {
        this(new JsonMapper(), StreamingContext.EMPTY);
    }

    public Jackson3JsonFacade(JsonMapper jsonMapper) {
        this(jsonMapper, StreamingContext.EMPTY);
    }

    /**
     * Creates facade with configured JsonMapper and SJF4J module.
     */
    public Jackson3JsonFacade(JsonMapper jsonMapper, StreamingContext context) {
        Objects.requireNonNull(jsonMapper, "jsonMapper");
        Objects.requireNonNull(context, "context");

        JsonMapper.Builder builder = jsonMapper.rebuild();
        builder.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        builder.addModule(new Jackson3Module.TwoSimpleModule(context));
        AnnotationIntrospector existing = builder.annotationIntrospector();
        builder.annotationIntrospector(AnnotationIntrospectorPair.create(
                new Jackson3Module.NodePropertyAnnotationIntrospector(), existing));
        if (!context.includeNulls) {
            builder.changeDefaultPropertyInclusion(inclusion ->
                    JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL)
            );
        }
        this.jsonMapper = builder.build();
        this.streamingContext = context;
    }

    public static FacadeProvider<JsonFacade<?, ?>> provider() {
        return context -> new Jackson3JsonFacade(new JsonMapper(), context);
    }

    public static FacadeProvider<JsonFacade<?, ?>> provider(JsonMapper jsonMapper) {
        return context -> new Jackson3JsonFacade(jsonMapper, context);
    }

    @Override
    public StreamingContext streamingContext() {
        return streamingContext;
    }

    @Override
    public StreamingContext.StreamingMode realStreamingMode() {
        if (streamingContext.streamingMode == StreamingContext.StreamingMode.AUTO) {
            // Jackson defaults to module-backed read/write so AUTO matches the highest-fidelity path.
            return StreamingContext.StreamingMode.PLUGIN_MODULE;
        }
        return streamingContext.streamingMode;
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
