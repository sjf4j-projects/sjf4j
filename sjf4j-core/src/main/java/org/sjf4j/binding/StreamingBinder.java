package org.sjf4j.binding;

import org.sjf4j.exception.BindingException;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.Types;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Base streaming binding for reading and writing structured data.
 */
public abstract class StreamingBinder<R extends StreamingReader, W extends StreamingWriter> {

    protected final StreamingContext context;

    protected StreamingBinder(StreamingContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /*
     * --------------------------------------------------------------
     * Reader
     * --------------------------------------------------------------
     */

    /**
     * Creates a streaming reader from java.io.Reader.
     */
    public abstract R createReader(Reader input) throws IOException;

    /**
     * Creates a streaming reader from InputStream using UTF-8.
     */
    public R createReader(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        return createReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    /**
     * Creates a streaming reader from input string.
     */
    public R createReader(String input) throws IOException {
        Objects.requireNonNull(input, "input");
        return createReader(new FastStringReader(input));
    }

    /**
     * Creates a streaming reader from UTF-8 bytes.
     */
    public R createReader(byte[] input) throws IOException {
        Objects.requireNonNull(input, "input");
        return createReader(new ByteArrayInputStream(input));
    }

    /**
     * Reads one node from reader into target type.
     */
    public Object readNode(Reader input, Type type) {
        Objects.requireNonNull(input, "input");
        try {
            StreamingReader reader = createReader(input);
            reader.startDocument();
            Object node = StreamingIO.readNode(reader, type, context);
            reader.endDocument();
            return node;
        } catch (Exception e) {
            throw new BindingException("failed to read streaming into node of '" + type + "'", e);
        }
    }

    /**
     * Reads one node from input stream into target type.
     */
    public Object readNode(InputStream input, Type type) {
        Objects.requireNonNull(input, "input");
        try {
            StreamingReader reader = createReader(input);
            reader.startDocument();
            Object node = StreamingIO.readNode(reader, type, context);
            reader.endDocument();
            return node;
        } catch (Exception e) {
            throw new BindingException("failed to read streaming into node of '" + type + "'", e);
        }
    }

    /**
     * Reads one node from string into target type.
     */
    public Object readNode(String input, Type type) {
        Objects.requireNonNull(input, "input");
        try (StreamingReader reader = createReader(input)) {
            reader.startDocument();
            Object node = StreamingIO.readNode(reader, type, context);
            reader.endDocument();
            return node;
        } catch (Exception e) {
            throw new BindingException("failed to read streaming into node of '" + type + "'", e);
        }
    }

    /**
     * Reads one node from bytes into target type.
     */
    public Object readNode(byte[] input, Type type) {
        Objects.requireNonNull(input, "input");
        try (StreamingReader reader = createReader(input)) {
            reader.startDocument();
            Object node = StreamingIO.readNode(reader, type, context);
            reader.endDocument();
            return node;
        } catch (Exception e) {
            throw new BindingException("failed to read streaming into node of '" + type + "'", e);
        }
    }


    /*
     * --------------------------------------------------------------
     * Writer
     * --------------------------------------------------------------
     */

    /**
     * Creates a streaming writer to java.io.Writer.
     */
    public abstract W createWriter(Writer output) throws IOException;

    /**
     * Creates a streaming writer to OutputStream using UTF-8.
     */
    public W createWriter(OutputStream output) throws IOException {
        return createWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    }


    /**
     * Writes one node to writer.
     */
    public void writeNode(Writer output, Object node) {
        Objects.requireNonNull(output, "output");
        try {
            StreamingWriter writer = createWriter(output);
            writer.startDocument();
            StreamingIO.writeNode(writer, node, context);
            writer.endDocument();
            writer.flush();
            writer.flushTo(output);
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node) + "' to streaming", e);
        }
    }

    /**
     * Writes one node to output stream.
     */
    public void writeNode(OutputStream output, Object node) {
        Objects.requireNonNull(output, "output");
        try {
            StreamingWriter writer = createWriter(output);
            writer.startDocument();
            StreamingIO.writeNode(writer, node, context);
            writer.endDocument();
            writer.flush();
            writer.flushTo(output);
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node) + "' to streaming", e);
        }
    }

    /**
     * Serializes one node to string.
     */
    public String writeNodeAsString(Object node) {
        try (FastStringWriter output = new FastStringWriter()) {
            writeNode(output, node);
            return output.toString();
        } catch (Exception e) {
            throw new BindingException(e);
        }
    }

    /**
     * Serializes one node to bytes.
     */
    public byte[] writeNodeAsBytes(Object node) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeNode(output, node);
            return output.toByteArray();
        } catch (Exception e) {
            throw new BindingException(e);
        }
    }


    /*
     * --------------------------------------------------------------
     * PreparedName/NameMatcher Cache
     * --------------------------------------------------------------
     */

    private final ClassValue<PreparedName[]> preparedNameCache =
            new ClassValue<PreparedName[]>() {
                @Override
                protected PreparedName[] computeValue(Class<?> type) {
                    PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(type);
                    String[] names = pi.fieldNames;
                    PreparedName[] preparedNames = new PreparedName[names.length];
                    for (int i = 0; i < names.length; i++) {
                        preparedNames[i] = createPreparedName(names[i]);
                    }

                    return preparedNames;
                }
            };


    public PreparedName createPreparedName(String name) {
        return new PreparedName.SimplePreparedName(name);
    }

    public PreparedName[] getPreparedNames(Class<?> type) {
        return preparedNameCache.get(type);
    }

}
