package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

/**
 * Resolves schema locations and loads JSON Schema documents without compiling
 * validation plans. Storage access is supplied by a small pluggable reader so
 * annotation processors and future remote-document providers can use the same
 * URI and parsing semantics.
 *
 * <p>The default reader supports local {@code classpath:} and {@code file:}
 * resources only; it never makes network requests.
 */
public final class SchemaLoader {

    /** Opens a resource, returning null when it does not exist.
     * The loader closes any non-null stream.
     */
    @FunctionalInterface
    public interface ResourceReader {
        InputStream open(URI uri) throws IOException;
    }

    private final URI baseUri;
    private final ResourceReader reader;

    public SchemaLoader() {
        this(SchemaRegistry.DEFAULT_JSON_SCHEMA_DIR, SchemaLoader::openLocal);
    }

    /** Creates a loader with the built-in local-resource reader. */
    public SchemaLoader(URI baseUri) {
        this(baseUri, SchemaLoader::openLocal);
    }

    /** Creates a loader with an alternative resource reader. */
    public SchemaLoader(URI baseUri, ResourceReader reader) {
        this.baseUri = Asserts.notNull(baseUri, "baseUri");
        this.reader = Asserts.notNull(reader, "reader");
        if (!baseUri.isAbsolute()) throw new IllegalArgumentException("schema base URI must be absolute");
    }

    /** Resolves a URI reference against this loader's base. */
    public URI resolve(String ref) {
        Asserts.notNull(ref, "ref");
        return baseUri.resolve(ref);
    }

    public URI baseUri() {
        return baseUri;
    }

    /** Loads a document by reference; null means the document was not found. */
    public JsonSchema load(String ref) {
        return load(resolve(ref));
    }

    /**
     * Loads the document containing the requested URI (ignoring any fragment).
     * The fragment remains the registry's responsibility.
     */
    public JsonSchema load(URI uri) {
        URI documentUri = documentUri(uri);
        String text = readText(documentUri);
        if (text == null) return null;
        JsonSchema schema = JsonSchema.fromJson(text);
        if (schema instanceof ObjectSchema) {
            ((ObjectSchema) schema).setRetrievalUri(documentUri);
        }
        return schema;
    }

    /**
     * Returns the original JSON text, or null for a missing document.
     * This is useful to processors that inspect the source during generation;
     * runtime callers normally want {@link #load(URI)} instead.
     */
    public String readText(URI uri) {
        URI documentUri = documentUri(uri);
        try (InputStream in = reader.open(documentUri)) {
            if (in == null) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int length; (length = in.read(buffer)) != -1;) {
                output.write(buffer, 0, length);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                    "failed to read schema resource", PathSegment.Root.INSTANCE, documentUri), e);
        }
    }

    /** Removes only the fragment; registry lookup still uses the complete URI. */
    public static URI documentUri(URI uri) {
        Asserts.notNull(uri, "uri");
        String value = uri.toString();
        int hash = value.indexOf('#');
        return hash < 0 ? uri : URI.create(value.substring(0, hash));
    }

    private static InputStream openLocal(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if ("file".equalsIgnoreCase(scheme)) {
            try {
                return Files.newInputStream(Paths.get(uri));
            } catch (NoSuchFileException e) {
                return null;
            }
        }
        if ("classpath".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            if (path == null || path.isEmpty()) path = uri.getSchemeSpecificPart();
            if (path.startsWith("/")) path = path.substring(1);
            return SchemaLoader.class.getClassLoader().getResourceAsStream(path);
        }
        throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                "unsupported schema URI scheme '" + scheme + "'",
                PathSegment.Root.INSTANCE, uri));
    }
}
