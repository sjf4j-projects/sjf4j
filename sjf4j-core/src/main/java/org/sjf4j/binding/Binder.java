package org.sjf4j.binding;

import org.sjf4j.RuntimeContext;
import org.sjf4j.exception.BindingException;
import org.sjf4j.util.Types;
import org.sjf4j.util.Asserts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * Base streaming binding for reading and writing one structured document.
 * String and byte-array read helpers close their internally created readers;
 * stream and {@link Reader} inputs remain owned by the caller. Writes flush
 * caller-supplied outputs but do not close them. Byte streams use UTF-8 unless
 * a backend overrides the corresponding reader or writer factory.
 */
public abstract class Binder<R extends StreamingReader, W extends StreamingWriter> {

    protected final RuntimeContext context;

    protected Binder(RuntimeContext context) {
        this.context = Asserts.notNull(context, "context");
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
        Asserts.notNull(input, "input");
        return createReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    /**
     * Creates a streaming reader from input string.
     */
    public R createReader(String input) throws IOException {
        Asserts.notNull(input, "input");
        return createReader(new FastStringReader(input));
    }

    /**
     * Creates a streaming reader from UTF-8 bytes.
     */
    public R createReader(byte[] input) throws IOException {
        Asserts.notNull(input, "input");
        return createReader(new ByteArrayInputStream(input));
    }

    /**
     * Reads one node from reader into target type.
     */
    public Object readNode(Reader input, Type type) {
        Asserts.notNull(type, "type");
        Asserts.notNull(input, "input");
        try {
            StreamingReader reader = createReader(input);
            reader.startDocument();
            Object node = StreamingIO.readNode(reader, type, context);
            reader.endDocument();
            return node;
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to read node as '" + type.getTypeName() + "'", e);
        }
    }

    /**
     * Reads one node from input stream into target type.
     */
    public Object readNode(InputStream input, Type type) {
        Asserts.notNull(type, "type");
        Asserts.notNull(input, "input");
        try {
            StreamingReader reader = createReader(input);
            reader.startDocument();
            Object node = StreamingIO.readNode(reader, type, context);
            reader.endDocument();
            return node;
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to read node as '" + type.getTypeName() + "'", e);
        }
    }

    /**
     * Reads one node from string into target type.
     */
    public Object readNode(String input, Type type) {
        Asserts.notNull(type, "type");
        Asserts.notNull(input, "input");
        try (StreamingReader reader = createReader(input)) {
            reader.startDocument();
            Object node = StreamingIO.readNode(reader, type, context);
            reader.endDocument();
            return node;
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to read node as '" + type.getTypeName() + "'", e);
        }
    }

    /**
     * Reads one node from bytes into target type.
     */
    public Object readNode(byte[] input, Type type) {
        Asserts.notNull(type, "type");
        Asserts.notNull(input, "input");
        try (StreamingReader reader = createReader(input)) {
            reader.startDocument();
            Object node = StreamingIO.readNode(reader, type, context);
            reader.endDocument();
            return node;
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to read node as '" + type.getTypeName() + "'", e);
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
        Asserts.notNull(output, "output");
        try {
            StreamingWriter writer = createWriter(output);
            writer.startDocument();
            StreamingIO.writeNode(writer, node, context);
            writer.endDocument();
            writer.flush();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node) + "' to streaming", e);
        }
    }

    /**
     * Writes one node to output stream.
     */
    public void writeNode(OutputStream output, Object node) {
        Asserts.notNull(output, "output");
        try {
            StreamingWriter writer = createWriter(output);
            writer.startDocument();
            StreamingIO.writeNode(writer, node, context);
            writer.endDocument();
            writer.flush();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to write node type '" + Types.name(node) + "' to streaming", e);
        }
    }

    /**
     * Serializes one node to string.
     */
    public String writeNodeAsString(Object node) {
        PooledStringWriter output = new PooledStringWriter();
        try {
            try (W writer = createWriter(output)) {
                writer.startDocument();
                StreamingIO.writeNode(writer, node, context);
                writer.endDocument();
                writer.flush();
            }
            return output.toString();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException(e);
        } finally {
            output.recycle();
        }
    }

    /**
     * Serializes one node to bytes.
     */
    public byte[] writeNodeAsBytes(Object node) {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            try (W writer = createWriter(output)) {
                writer.startDocument();
                StreamingIO.writeNode(writer, node, context);
                writer.endDocument();
                writer.flush();
            }
            return output.toByteArray();
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException(e);
        } finally {
            output.recycle();
        }
    }

}
