package org.sjf4j.binding;

import org.sjf4j.NodeConfig;
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

    protected final NodeConfig config;

    protected Binder(NodeConfig config) {
        this.config = Asserts.notNull(config, "config");
    }

    /** Returns this binder's immutable SJF4J node configuration. */
    public final NodeConfig config() {
        return config;
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
     * Creates a reader with an explicit generic numeric policy. Backends that
     * support policy overrides should implement this overload. The default
     * implementation preserves existing custom binders when the policy agrees
     * with their configuration, and rejects unsupported policies.
     */
    public R createParser(Reader input, boolean exactNumbers) throws IOException {
        if (exactNumbers != config.exactNumbers) {
            throw new BindingException("backend does not support overriding exactNumbers");
        }
        return _requireNumberPolicy(createParser(input), exactNumbers);
    }

    /** Creates a streaming parser from InputStream with an explicit numeric policy. */
    public R createParser(InputStream input, boolean exactNumbers) throws IOException {
        Asserts.notNull(input, "input");
        if (exactNumbers == config.exactNumbers) {
            return _requireNumberPolicy(createParser(input), exactNumbers);
        }
        return createParser(new InputStreamReader(input, StandardCharsets.UTF_8), exactNumbers);
    }

    /** Creates a streaming parser from String with an explicit numeric policy. */
    public R createParser(String input, boolean exactNumbers) throws IOException {
        Asserts.notNull(input, "input");
        if (exactNumbers == config.exactNumbers) {
            return _requireNumberPolicy(createParser(input), exactNumbers);
        }
        return createParser(new FastStringReader(input), exactNumbers);
    }

    /** Creates a streaming parser from UTF-8 bytes with an explicit numeric policy. */
    public R createParser(byte[] input, boolean exactNumbers) throws IOException {
        Asserts.notNull(input, "input");
        if (exactNumbers == config.exactNumbers) {
            return _requireNumberPolicy(createParser(input), exactNumbers);
        }
        return createParser(new ByteArrayInputStream(input), exactNumbers);
    }

    private R _requireNumberPolicy(R parser, boolean exactNumbers) throws IOException {
        if (parser.exactNumbers() != exactNumbers) {
            BindingException failure = new BindingException("backend does not support requested exactNumbers policy");
            try {
                parser.close();
            } catch (IOException e) {
                failure.addSuppressed(e);
            }
            throw failure;
        }
        return parser;
    }

    /**
     * Reads one node from parser into target type.
     */
    public Object readNode(Reader input, Type type) {
        Asserts.notNull(type, "type");
        Asserts.notNull(input, "input");
        try {
            try (StreamingParser parser = createParser(input, config.exactNumbers)) {
                parser.start();
                Object node = StreamingIO.readNode(parser, type, config);
                parser.end();
                return node;
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
            try (StreamingParser parser = createParser(input, config.exactNumbers)) {
                parser.start();
                Object node = StreamingIO.readNode(parser, type, config);
                parser.end();
                return node;
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
            try (StreamingParser parser = createParser(input, config.exactNumbers)) {
                parser.start();
                Object node = StreamingIO.readNode(parser, type, config);
                parser.end();
                return node;
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
            try (StreamingParser parser = createParser(input, config.exactNumbers)) {
                parser.start();
                Object node = StreamingIO.readNode(parser, type, config);
                parser.end();
                return node;
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
     * Writes one node to generator.
     */
    public void writeNode(Writer output, Object node) {
        Asserts.notNull(output, "output");
        try {
            try (StreamingGenerator generator = createGenerator(output)) {
                generator.start();
                StreamingIO.writeNode(generator, node, config);
                generator.end();
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
            try (StreamingGenerator generator = createGenerator(output)) {
                generator.start();
                StreamingIO.writeNode(generator, node, config);
                generator.end();
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
            try (W generator = createGenerator(output)) {
                generator.start();
                StreamingIO.writeNode(generator, node, config);
                generator.end();
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
            try (W generator = createGenerator(output)) {
                generator.start();
                StreamingIO.writeNode(generator, node, config);
                generator.end();
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
