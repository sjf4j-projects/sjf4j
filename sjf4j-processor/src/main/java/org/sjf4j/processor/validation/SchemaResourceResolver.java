package org.sjf4j.processor.validation;

import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.schema.CompiledSchemaSnapshot;
import org.sjf4j.schema.JsonSchema;

import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves complete schema documents at annotation-processing time.
 * No runtime resource lookup is emitted: every locally referenced document
 * is captured in the generated validator's fixed schema snapshot.
 */
final class SchemaResourceResolver {

    static final class Snapshot {
        final String[] uris;
        final String[] documents;

        Snapshot(Map<URI, String> resources) {
            uris = new String[resources.size()];
            documents = new String[resources.size()];
            int i = 0;
            for (Map.Entry<URI, String> entry : resources.entrySet()) {
                uris[i] = entry.getKey().toString();
                documents[i++] = entry.getValue();
            }
        }
    }

    private final ProcessorContext context;

    SchemaResourceResolver(ProcessorContext context) {
        this.context = context;
    }

    Snapshot resolve(ValidJsonSchema annotation, String ownerName, String originName) {
        String inline = annotation.value();
        String ref = annotation.ref().trim();
        URI rootUri;
        String rootDocument;

        if (!inline.isEmpty()) {
            rootUri = URI.create("sjf4j:/" + originName + "/");
            rootDocument = inline;
        } else {
            URI baseDir = URI.create("classpath:/json-schemas/");
            if (!ref.isEmpty()) {
                rootUri = baseDir.resolve(ref);
                if (!rootUri.isAbsolute()) throw new IllegalArgumentException("non-absolute schema URI: " + ref);
            } else {
                String simple = ownerName.substring(ownerName.lastIndexOf('.') + 1);
                rootUri = baseDir.resolve(simple + ".json");
                rootDocument = read(rootUri);
                if (rootDocument == null) {
                    String snake = simple.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
                    rootUri = baseDir.resolve(snake + ".json");
                }
            }
            rootDocument = read(rootUri);
            if (rootDocument == null) {
                throw new IllegalArgumentException("schema resource not found: " + rootUri
                        + " (set -Asjf4j.schemaDir=<resource-root> for source resources)");
            }
        }

        Map<URI, String> resources = new LinkedHashMap<URI, String>();
        resources.put(rootUri, rootDocument);

        for (List<URI> queue = new ArrayList<URI>(resources.keySet()); !queue.isEmpty();) {
            URI uri = queue.remove(0);
            JsonSchema schema = JsonSchema.fromJson(resources.get(uri));
            scanRefs(schema, uri, resources, queue);
        }

        Snapshot snapshot = new Snapshot(resources);
        // Resolve the full graph now; unresolved refs and invalid keywords must
        // fail annotation processing instead of being deferred until execution.
        CompiledSchemaSnapshot.create(snapshot.uris, snapshot.documents, 0);
        return snapshot;
    }

    private URI effectiveBase(URI base, Object node) {
        if (JsonType.of(node) != JsonType.OBJECT) return base;
        Object id = Nodes.getInObject(node, "$id");
        return id instanceof String ? base.resolve((String) id) : base;
    }

    private void scanRefs(Object node, URI base, Map<URI, String> resources, List<URI> queue) {
        JsonType type = JsonType.of(node);
        if (type == JsonType.OBJECT) {
            URI local = effectiveBase(base, node);
            for (String keyword : new String[]{"$ref", "$dynamicRef", "$recursiveRef"}) {
                Object ref = Nodes.getInObject(node, keyword);
                if (!(ref instanceof String)) continue;
                URI target = local.resolve((String) ref);
                URI document = URI.create(target.toString().split("#", 2)[0]);
                if (resources.containsKey(document)) continue;
                if (!"classpath".equals(document.getScheme()) && !"file".equals(document.getScheme())) {
                    continue; // Embedded $id and built-in meta-schema refs resolve in the planner.
                }
                String contents = read(document);
                if (contents == null) {
                    // Could be a nested $id within an already indexed resource.
                    // The planner decides whether that resource actually exists.
                    continue;
                }
                resources.put(document, contents);
                queue.add(document);
            }
            // Descend only through schema-bearing keywords. Objects under
            // const/enum/default/examples are instance data, not schemas.
            for (String keyword : new String[]{
                    "$defs", "definitions", "properties", "patternProperties",
                    "dependentSchemas", "dependencies"}) {
                Object mapping = Nodes.getInObject(node, keyword);
                if (JsonType.of(mapping) != JsonType.OBJECT) continue;
                for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(mapping)) {
                    scanRefs(entry.getValue(), local, resources, queue);
                }
            }
            for (String keyword : new String[]{
                    "items", "additionalItems", "additionalProperties",
                    "contains", "propertyNames", "not", "if", "then", "else",
                    "unevaluatedItems", "unevaluatedProperties", "contentSchema",
                    "allOf", "anyOf", "oneOf", "prefixItems"}) {
                Object subschema = Nodes.getInObject(node, keyword);
                if (subschema != null) scanRefs(subschema, local, resources, queue);
            }
        } else if (type == JsonType.ARRAY) {
            Iterator<Object> iter = Nodes.iteratorInArray(node);
            while (iter.hasNext()) scanRefs(iter.next(), base, resources, queue);
        }
    }

    private String read(URI uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try {
                Path path = Paths.get(uri);
                if (Files.exists(path)) return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalArgumentException("failed to read schema: " + uri, e);
            }
            return null;
        }
        if (!"classpath".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("only file:/ and classpath:/ schema resources are supported: " + uri);
        }

        String path = uri.getPath();
        if (path.startsWith("/")) path = path.substring(1);
        String sourceDir = context.options.get("sjf4j.schemaDir");
        if (sourceDir != null && !sourceDir.isEmpty()) {
            try {
                Path source = Paths.get(sourceDir).resolve(path).normalize();
                if (Files.isRegularFile(source)) {
                    return new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("failed to read schema resource: " + uri, e);
            }
        }
        for (StandardLocation location : new StandardLocation[]{
                StandardLocation.CLASS_PATH, StandardLocation.CLASS_OUTPUT, StandardLocation.SOURCE_PATH}) {
            try {
                FileObject file = context.filer.getResource(location, "", path);
                try (InputStream in = file.openInputStream()) {
                    return new String(readBytes(in), StandardCharsets.UTF_8);
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // Try the next annotation-processing location.
            }
        }
        return null;
    }

    private static byte[] readBytes(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int n; (n = in.read(buffer)) != -1;) bytes.write(buffer, 0, n);
        return bytes.toByteArray();
    }
}
