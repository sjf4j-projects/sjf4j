package org.sjf4j.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses official-suite-style externalSchemas mappings: retrieval URI to an
 * inline JSON Schema (object or boolean). No network access is performed.
 */
public final class ExternalSchemas {
    private ExternalSchemas() {}

    public static Map<URI, JsonSchema> parse(String json) {
        Object root = Sjf4j.global().fromJson(json);
        if (JsonType.of(root) != JsonType.OBJECT) {
            throw new IllegalArgumentException("externalSchemas must be a JSON object");
        }
        Map<URI, JsonSchema> schemas = new LinkedHashMap<>();
        JsonObject object = Nodes.toJsonObject(root);
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            URI uri;
            try {
                uri = URI.create(entry.getKey());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid external schema URI: " + entry.getKey(), e);
            }
            if (!uri.isAbsolute() || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("external schema retrieval URI must be absolute without a fragment: " + uri);
            }
            Object value = entry.getValue();
            JsonType type = JsonType.of(value);
            if (type != JsonType.OBJECT && type != JsonType.BOOLEAN) {
                throw new IllegalArgumentException("external schema must be an object or boolean: " + uri);
            }
            schemas.put(uri, JsonSchema.fromNode(value));
        }
        return schemas;
    }

    /**
     * Loads an inline mapping or its local JSON resource. Inline takes priority
     * over ref. Relative refs resolve against baseUri; HTTP retrieval is forbidden.
     */
    public static Map<URI, JsonSchema> load(String inline, String ref, URI baseUri, ClassLoader classLoader) {
        if (inline != null && !inline.isEmpty()) return parse(inline);
        if (ref == null || ref.trim().isEmpty()) return Collections.emptyMap();
        URI uri = SchemaLoader.documentUri(baseUri.resolve(URI.create(ref.trim())));
        String scheme = uri.getScheme();
        if (!"file".equalsIgnoreCase(scheme) && !"classpath".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("externalSchemasRef must use file: or classpath: " + uri);
        }
        try {
            byte[] contents;
            if ("file".equalsIgnoreCase(scheme)) {
                contents = Files.readAllBytes(Paths.get(uri));
            } else {
                String path = uri.getPath();
                if (path == null) throw new IllegalArgumentException("invalid classpath resource: " + uri);
                while (path.startsWith("/")) path = path.substring(1);
                ClassLoader loader = classLoader == null ? ExternalSchemas.class.getClassLoader() : classLoader;
                try (InputStream in = loader.getResourceAsStream(path)) {
                    if (in == null) throw new IllegalArgumentException("external schema mapping not found: " + uri);
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    for (int n; (n = in.read(buffer)) != -1;) bytes.write(buffer, 0, n);
                    contents = bytes.toByteArray();
                }
            }
            return parse(new String(contents, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot load external schema mapping: " + uri, e);
        }
    }
}
