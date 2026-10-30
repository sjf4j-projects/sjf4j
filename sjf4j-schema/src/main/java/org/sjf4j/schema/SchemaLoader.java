package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

/**
 * Loads a complete JSON Schema document by its absolute retrieval URI.
 * Resource loading does not select fragments or compile validation plans.
 */
@FunctionalInterface
public interface SchemaLoader {

    /** Default loader for classpath and local file resources; never makes network requests. */
    SchemaLoader LOCAL = new Local();

    /**
     * Returns the parsed document, or null if it is missing.
     * Any URI fragment is ignored for retrieval; fragment resolution belongs to SchemaRegistry.
     */
    JsonSchema load(URI uri);

    /** Discards the document fragment without changing the retrieval URI's base. */
    static URI documentUri(URI uri) {
        Asserts.notNull(uri, "uri");
        String text = uri.toString();
        int fragment = text.indexOf('#');
        return fragment < 0 ? uri : URI.create(text.substring(0, fragment));
    }

    /** Parses an already opened schema document and records its retrieval URI. */
    static JsonSchema parse(URI uri, Reader reader) throws IOException {
        URI document = documentUri(uri);
        char[] chars = new char[4096];
        StringBuilder json = new StringBuilder();
        for (int n; (n = reader.read(chars)) != -1;) json.append(chars, 0, n);
        JsonSchema schema = JsonSchema.fromJson(json.toString());
        if (schema instanceof ObjectSchema) ((ObjectSchema) schema).setRetrievalUri(document);
        return schema;
    }

    /** Local file/classpath implementation. */
    final class Local implements SchemaLoader {
        @Override
        public JsonSchema load(URI uri) {
            URI document = documentUri(uri);
            String scheme = document.getScheme();
            try (Reader reader = open(document, scheme)) {
                return reader == null ? null : SchemaLoader.parse(document, reader);
            } catch (IOException e) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                        "failed to read schema resource", PathSegment.Root.INSTANCE, document), e);
            }
        }

        private Reader open(URI uri, String scheme) throws IOException {
            if ("file".equalsIgnoreCase(scheme)) {
                try {
                    return Files.newBufferedReader(Paths.get(uri), StandardCharsets.UTF_8);
                } catch (NoSuchFileException e) {
                    return null;
                }
            }
            if ("classpath".equalsIgnoreCase(scheme)) {
                String path = uri.getPath();
                if (path == null || path.isEmpty()) path = uri.getSchemeSpecificPart();
                if (path.startsWith("/")) path = path.substring(1);
                java.io.InputStream stream = SchemaLoader.class.getClassLoader().getResourceAsStream(path);
                return stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8);
            }
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                    "unsupported schema URI scheme '" + scheme + "'",
                    PathSegment.Root.INSTANCE, uri));
        }
    }
}
