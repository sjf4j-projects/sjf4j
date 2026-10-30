package org.sjf4j.processor.validation;

import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.util.Strings;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.schema.SchemaRegistry;
import org.sjf4j.schema.SchemaLoader;
import org.sjf4j.schema.JsonSchema;

import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves complete schema documents at annotation-processing time.
 * Schemas are analyzed for code generation, but runtime fallback loads the
 * original sources; the parsed document text is not emitted into generated code.
 */
final class SchemaResourceResolver {

    static final class Snapshot {
        final String[] uris;
        final String[] documents;
        final String selectedUri;

        Snapshot(Map<URI, String> resources, URI selectedUri) {
            this.selectedUri = selectedUri.toString();
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
        URI selectedUri;
        String rootDocument;

        if (!inline.isEmpty()) {
            rootUri = URI.create("sjf4j:/" + originName + "/");
            selectedUri = rootUri;
            rootDocument = inline;
        } else {
            URI baseDir = SchemaRegistry.DEFAULT_JSON_SCHEMA_DIR;
            if (!ref.isEmpty()) {
                selectedUri = baseDir.resolve(ref);
                if (!selectedUri.isAbsolute()) throw new IllegalArgumentException("non-absolute schema URI: " + ref);
                // Fragments select a schema within the resource; they are not
                // part of the document retrieval URI.
                rootUri = URI.create(selectedUri.toString().split("#", 2)[0]);
            } else {
                String simple = ownerName.substring(ownerName.lastIndexOf('.') + 1);
                rootUri = baseDir.resolve(simple + ".json");
                rootDocument = read(rootUri);
                if (rootDocument == null) {
                    String snake = Strings.toSnakeCase(simple);
                    rootUri = baseDir.resolve(snake + ".json");
                }
                selectedUri = rootUri;
            }
            rootDocument = read(rootUri);
            if (rootDocument == null) {
                throw new IllegalArgumentException("schema resource not found: " + rootUri
                        + " (make the resource visible on the compilation classpath)");
            }
        }

        Map<URI, String> resources = new LinkedHashMap<URI, String>();
        resources.put(rootUri, rootDocument);

        for (List<URI> queue = new ArrayList<URI>(resources.keySet()); !queue.isEmpty();) {
            URI uri = queue.remove(0);
            JsonSchema schema = JsonSchema.fromJson(resources.get(uri));
            scanRefs(schema, uri, resources, queue);
        }

        Snapshot snapshot = new Snapshot(resources, selectedUri);
        // Resolve the full graph now; unresolved refs and invalid keywords must
        // fail annotation processing instead of being deferred until execution.
        SchemaRegistry registry = new SchemaRegistry();
        boolean hasBooleanResource = false;
        for (int i = 0; i < snapshot.uris.length; i++) {
            JsonSchema document = JsonSchema.fromJson(snapshot.documents[i]);
            if (document instanceof org.sjf4j.schema.ObjectSchema) {
                registry.index(URI.create(snapshot.uris[i]), document);
            } else {
                hasBooleanResource = true;
            }
        }
        // Boolean document references cannot be indexed with the existing public
        // registry API. Keep their compile-time syntax validation, while leaving
        // complete reference resolution to the existing runtime machinery.
        if (!hasBooleanResource && registry.resolve(URI.create(snapshot.selectedUri)) == null) {
            throw new IllegalArgumentException("unresolved selected schema: " + snapshot.selectedUri);
        }
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
        URI document = SchemaLoader.documentUri(uri);
        try (java.io.Reader reader = openResource(document)) {
            if (reader == null) return null;
            StringBuilder text = new StringBuilder();
            char[] buf = new char[4096];
            for (int n; (n = reader.read(buf)) != -1;) text.append(buf, 0, n);
            return text.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot load schema: " + document, e);
        }
    }

    private java.io.Reader openResource(URI uri) throws IOException {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try {
                return java.nio.file.Files.newBufferedReader(Paths.get(uri), java.nio.charset.StandardCharsets.UTF_8);
            } catch (NoSuchFileException e) {
                return null;
            }
        }
        if (!"classpath".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("unsupported compile-time schema URI: " + uri);
        }
        String path = uri.getPath();
        if (path.startsWith("/")) path = path.substring(1);
        for (StandardLocation location : new StandardLocation[]{
                StandardLocation.CLASS_PATH, StandardLocation.CLASS_OUTPUT, StandardLocation.SOURCE_PATH}) {
            try {
                FileObject file = context.filer.getResource(location, "", path);
                return file.openReader(true);
            } catch (IOException | IllegalArgumentException ignored) {
                // Continue searching compiler resource locations.
            }
        }
        return null;
    }
}
