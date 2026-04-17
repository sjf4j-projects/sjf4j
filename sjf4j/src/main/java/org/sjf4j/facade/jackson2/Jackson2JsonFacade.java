package org.sjf4j.facade.jackson2;


import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.JsonFacade;
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
        // Jackson defaults to module-backed read/write so AUTO matches the highest-fidelity path.
        this.streamingMode = streamingMode == null || streamingMode == StreamingMode.AUTO ?
                StreamingMode.PLUGIN_MODULE : streamingMode;

        this.objectMapper = objectMapper;
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        AnnotationIntrospector serializationAi = this.objectMapper.getSerializationConfig().getAnnotationIntrospector();
        AnnotationIntrospector deserializationAi = this.objectMapper.getDeserializationConfig().getAnnotationIntrospector();
        this.objectMapper.setAnnotationIntrospectors(
                AnnotationIntrospectorPair.create(new Jackson2Module.NodePropertyAnnotationIntrospector(), serializationAi),
                AnnotationIntrospectorPair.create(new Jackson2Module.NodePropertyAnnotationIntrospector(), deserializationAi)
        );
        if (this.streamingMode == StreamingMode.PLUGIN_MODULE) {
            this.objectMapper.registerModule(new Jackson2Module.MySimpleModule());
        }
    }

    @Override
    public StreamingMode streamingMode() {
        return streamingMode;
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

    // Plugin

    @Override
    public Object readNodePlugin(Reader input, Type type) {
        try {
            return objectMapper.readValue(input, objectMapper.constructType(type));
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodePlugin(InputStream input, Type type) {
        try {
            return objectMapper.readValue(input, objectMapper.constructType(type));
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodePlugin(String input, Type type) {
        try {
            return objectMapper.readValue(input, objectMapper.constructType(type));
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodePlugin(byte[] input, Type type) {
        try {
            return objectMapper.readValue(input, objectMapper.constructType(type));
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    // Exclusive

    @Override
    public Object readNodeExclusive(Reader input, Type type) {
        try {
            JsonParser parser = objectMapper.getFactory().createParser(input);
            return Jackson2StreamingIO.readNode(parser, type);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodeExclusive(InputStream input, Type type) {
        try {
            JsonParser parser = objectMapper.getFactory().createParser(input);
            return Jackson2StreamingIO.readNode(parser, type);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }

    @Override
    public Object readNodeExclusive(String input, Type type) {
        try {
            JsonParser parser = objectMapper.getFactory().createParser(input);
            return Jackson2StreamingIO.readNode(parser, type);
        } catch (Exception e) {
            throw failedToRead(type, e);
        }
    }
    @Override
    public Object readNodeExclusive(byte[] input, Type type) {
        try {
            JsonParser parser = objectMapper.getFactory().createParser(input);
            return Jackson2StreamingIO.readNode(parser, type);
        } catch (Exception e) {
            throw failedToRead(type, e);
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

    // Plugin write
    @Override
    public void writeNodePlugin(Writer output, Object node) {
        try {
            objectMapper.writeValue(output, node);
        } catch (IOException e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public void writeNodePlugin(OutputStream output, Object node) {
        try {
            objectMapper.writeValue(output, node);
        } catch (IOException e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public String writeNodeAsStringPlugin(Object node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public byte[] writeNodeAsBytesPlugin(Object node) {
        try {
            return objectMapper.writeValueAsBytes(node);
        } catch (IOException e) {
            throw failedToWrite(node, e);
        }
    }

    // Exclusive write
    @Override
    public void writeNodeExclusive(Writer output, Object node) {
        try {
            JsonGenerator gen = objectMapper.getFactory().createGenerator(output);
            Jackson2StreamingIO.writeNode(gen, node);
            gen.flush();
        } catch (IOException e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public void writeNodeExclusive(OutputStream output, Object node) {
        try {
            JsonGenerator gen = objectMapper.getFactory().createGenerator(output);
            Jackson2StreamingIO.writeNode(gen, node);
            gen.flush();
        } catch (IOException e) {
            throw failedToWrite(node, e);
        }
    }

    @Override
    public String writeNodeAsStringExclusive(Object node) {
        final BufferRecycler br = objectMapper.getFactory()._getBufferRecycler();
        try (SegmentedStringWriter sw = new SegmentedStringWriter(br)) {
            JsonGenerator gen = objectMapper.getFactory().createGenerator(sw);
            Jackson2StreamingIO.writeNode(gen, node);
            gen.flush();
            return sw.getAndClear();
        } catch (Exception e) {
            throw failedToWrite(node, e);
        } finally {
            br.releaseToPool();
        }
    }

    @Override
    public byte[] writeNodeAsBytesExclusive(Object node) {
        final BufferRecycler br = objectMapper.getFactory()._getBufferRecycler();
        try (ByteArrayBuilder bb = new ByteArrayBuilder(br)) {
            JsonGenerator gen = objectMapper.getFactory().createGenerator(bb);
            Jackson2StreamingIO.writeNode(gen, node);
            gen.flush();
            final byte[] result = bb.toByteArray();
            bb.release();
            return result;
        } catch (Exception e) {
            throw failedToWrite(node, e);
        } finally {
            br.releaseToPool();
        }
    }

}
