package org.sjf4j.facade.jsonp;


import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.node.Types;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Objects;


public class JsonpJsonFacade implements JsonFacade<JsonpReader, JsonpWriter> {
    private final StreamingMode streamingMode;
    private final JsonProvider jsonProvider;

    public JsonpJsonFacade() {
        this(null);
    }

    /**
     * Creates facade with configured ObjectMapper and SJF4J module.
     */
    public JsonpJsonFacade(StreamingMode streamingMode) {
        this.streamingMode = streamingMode == null ? StreamingMode.AUTO : streamingMode;
        this.jsonProvider = JsonProvider.provider();
    }


    /// Reader

    /**
     * Creates a streaming reader from java.io.Reader.
     */
    @Override
    public JsonpReader createReader(Reader input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new JsonpReader(jsonProvider.createParser(input));
    }

    /**
     * Creates a streaming reader from InputStream.
     */
    @Override
    public JsonpReader createReader(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new JsonpReader(jsonProvider.createParser(input));
    }

    /**
     * Creates a streaming reader from JSON string.
     */
    @Override
    public JsonpReader createReader(String input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new JsonpReader(jsonProvider.createParser(new StringReader(input)));
    }

    /**
     * Creates a streaming reader from JSON bytes.
     */
    @Override
    public JsonpReader createReader(byte[] input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new JsonpReader(jsonProvider.createParser(new ByteArrayInputStream(input)));
    }

    /**
     * Reads JSON from reader into target type.
     */
    @Override
    public Object readNode(Reader input, Type type) {
        Objects.requireNonNull(input, "input");
        switch (streamingMode) {
            case SHARED_IO:
            case AUTO: {
                return JsonFacade.super.readNode(input, type);
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
        switch (streamingMode) {
            case SHARED_IO:
            case AUTO: {
                return JsonFacade.super.readNode(input, type);
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
        switch (streamingMode) {
            case SHARED_IO:
            case AUTO: {
                return JsonFacade.super.readNode(input, type);
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
        switch (streamingMode) {
            case SHARED_IO:
            case AUTO: {
                return JsonFacade.super.readNode(input, type);
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
    public JsonpWriter createWriter(Writer output) throws IOException {
        return new JsonpWriter(jsonProvider.createGenerator(output));
    }

    /**
     * Creates a streaming writer to OutputStream.
     */
    @Override
    public JsonpWriter createWriter(OutputStream output) throws IOException {
        return new JsonpWriter(jsonProvider.createGenerator(output));
    }

    /**
     * Writes node as JSON to writer.
     */
    @Override
    public void writeNode(Writer output, Object node) {
        Objects.requireNonNull(output, "output");
        switch (streamingMode) {
            case SHARED_IO:
            case AUTO: {
                JsonFacade.super.writeNode(output, node);
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
        switch (streamingMode) {
            case SHARED_IO:
            case AUTO: {
                JsonFacade.super.writeNode(output, node);
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
        switch (streamingMode) {
            case SHARED_IO:
            case AUTO: {
                return JsonFacade.super.writeNodeAsString(node);
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
        switch (streamingMode) {
            case SHARED_IO:
            case AUTO: {
                return JsonFacade.super.writeNodeAsBytes(node);
            }
            default:
                throw new JsonException("Unsupported write mode '" + streamingMode + "'");
        }
    }



}
