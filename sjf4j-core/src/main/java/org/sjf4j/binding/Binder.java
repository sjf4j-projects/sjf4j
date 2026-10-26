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
 * String and byte-array read helpers release their internally created parsers;
 * caller-supplied inputs and outputs remain owned by the caller. Writing
 * finishes the document and flushes output as required by the backend.
 * Byte streams use UTF-8 unless a backend overrides the corresponding factory.
 */
public abstract class Binder<R extends StreamingParser, W extends StreamingGenerator> {

    protected final RuntimeContext context;

    protected Binder(RuntimeContext context) {
        this.context = Asserts.notNull(context, "context");
    }

    /*
     * --------------------------------------------------------------
     * Parsing
     * --------------------------------------------------------------
     */

    /**
     * Creates a streaming parser from java.io.Reader.
     */
    public abstract R createParser(Reader input) throws IOException;

    /**
     * Creates a streaming parser from InputStream using UTF-8.
     */
    public R createParser(InputStream input) throws IOException {
        Asserts.notNull(input, "input");
        return createParser(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    /**
     * Creates a streaming parser from input string.
     */
    public R createParser(String input) throws IOException {
        Asserts.notNull(input, "input");
        return createParser(new FastStringReader(input));
    }

    /**
     * Creates a streaming parser from UTF-8 bytes.
     */
    public R createParser(byte[] input) throws IOException {
        Asserts.notNull(input, "input");
        return createParser(new ByteArrayInputStream(input));
    }

    /**
     * Reads one node from reader into target type.
     */
    public Object readNode(Reader input, Type type) {
        Asserts.notNull(type, "type");
        Asserts.notNull(input, "input");
        try {
            StreamingParser reader = createParser(input);
            try {
                reader.startRead();
                Object node = StreamingIO.readNode(reader, type, context);
                reader.endRead();
                return node;
            } finally {
                reader.release();
            }
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
            StreamingParser reader = createParser(input);
            try {
                reader.startRead();
                Object node = StreamingIO.readNode(reader, type, context);
                reader.endRead();
                return node;
            } finally {
                reader.release();
            }
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
        try {
            StreamingParser reader = createParser(input);
            try {
                reader.startRead();
                Object node = StreamingIO.readNode(reader, type, context);
                reader.endRead();
                return node;
            } finally {
                reader.release();
            }
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
        try {
            StreamingParser reader = createParser(input);
            try {
                reader.startRead();
                Object node = StreamingIO.readNode(reader, type, context);
                reader.endRead();
                return node;
            } finally {
                reader.release();
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to read node as '" + type.getTypeName() + "'", e);
        }
    }

    /*
     * --------------------------------------------------------------
     * Generation
     * --------------------------------------------------------------
     */

    /**
     * Creates a streaming generator to java.io.Writer.
     */
    public abstract W createGenerator(Writer output) throws IOException;

    /**
     * Creates a streaming generator to OutputStream using UTF-8.
     */
    public W createGenerator(OutputStream output) throws IOException {
        return createGenerator(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    }

    /**
     * Writes one node to writer.
     */
    public void writeNode(Writer output, Object node) {
        Asserts.notNull(output, "output");
        try {
            StreamingGenerator writer = createGenerator(output);
            try {
                writer.startWrite();
                StreamingIO.writeNode(writer, node, context);
                writer.endWrite();
            } finally {
                writer.release();
            }
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
            StreamingGenerator writer = createGenerator(output);
            try {
                writer.startWrite();
                StreamingIO.writeNode(writer, node, context);
                writer.endWrite();
            } finally {
                writer.release();
            }
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
            W writer = createGenerator(output);
            try {
                writer.startWrite();
                StreamingIO.writeNode(writer, node, context);
                writer.endWrite();
            } finally {
                writer.release();
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
            W writer = createGenerator(output);
            try {
                writer.startWrite();
                StreamingIO.writeNode(writer, node, context);
                writer.endWrite();
            } finally {
                writer.release();
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
