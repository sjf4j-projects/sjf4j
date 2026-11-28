package org.sjf4j.processor.schema;

import org.sjf4j.util.Strings;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.processor.CompilationContext;
import org.sjf4j.schema.SchemaException;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.SchemaRegistry;
import org.sjf4j.schema.SchemaDialect;
import org.sjf4j.schema.SchemaUtil;
import org.sjf4j.schema.SchemaLoader;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaSupport;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

/**
 * Resolves complete schema documents at annotation-processing time.
 * Schemas are analyzed for code generation, but runtime fallback loads the
 * original sources; the parsed document text is not emitted into generated code.
 */
final class SchemaResourceResolver {

    static final class Snapshot {
        final String[] uris;
        final String[] documents;
        final Set<String> externalUris;
        final String selectedUri;

        SchemaRegistry registry;
        boolean customDialect;
        boolean hasDynamicRef;

        SchemaPlan resolvePlan(String location) {
            if (registry == null) throw new IllegalStateException("schema registry not initialized");
            return registry.resolve(URI.create(location));
        }

        /** Resolve the effective draft for a node, including nested $schema scopes. */
        SchemaDialect dialect(String location) {
            String candidate = canonicalLocation(location);
            for (;;) {
                try {
                    SchemaPlan plan = resolvePlan(candidate);
                    if (plan != null && plan.dialect() != null) return plan.dialect();
                } catch (RuntimeException missingNode) {
                    // Synthetic sibling/coverage schemas have no standalone plan.
                }
                int hash = candidate.indexOf('#');
                if (hash < 0) break;
                int slash = candidate.lastIndexOf('/');
                candidate = slash > hash ? candidate.substring(0, slash)
                        : candidate.substring(0, hash);
            }
            return registry.getDefaultDialect();
        }

        String canonicalLocation(String location) {
            try {
                SchemaPlan plan = resolvePlan(location);
                return plan == null || plan.schemaUri() == null
                        ? location : plan.schemaUri().toString();
            } catch (IllegalArgumentException | SchemaException missingSubschema) {
                // Some compiler keyword locations are synthetic (e.g. reduced
                // sibling assertion maps), not independent runtime subschemas.
                return location;
            }
        }

        Snapshot(Map<URI, String> resources, URI selectedUri, Set<URI> externalSchemas) {
            this.selectedUri = selectedUri.toString();
            this.externalUris = new HashSet<String>();
            for (URI uri : externalSchemas) externalUris.add(uri.toString());
            uris = new String[resources.size()];
            documents = new String[resources.size()];
            int i = 0;
            for (Map.Entry<URI, String> entry : resources.entrySet()) {
                uris[i] = entry.getKey().toString();
                documents[i++] = entry.getValue();
            }
        }
    }

    private final CompilationContext context;
    SchemaResourceResolver(CompilationContext context) {
        this.context = context;
    }

    Snapshot resolve(ValidJsonSchema annotation, String ownerName, String originName,
                     Element sourceElement) {
        Path resourceDirectory = findResourceDirectory(sourceElement);
        String externalJson = annotation.externalSchemas();
        if (externalJson.isEmpty() && !annotation.externalSchemasRef().trim().isEmpty()) {
            URI externalRef = SchemaLoader.Local.DEFAULT_JSON_SCHEMA_DIR.resolve(
                    annotation.externalSchemasRef().trim());
            externalJson = read(externalRef, resourceDirectory);
            if (externalJson == null) {
                throw new IllegalArgumentException("externalSchemasRef not found: " + externalRef);
            }
        }
        Map<URI, JsonSchema> external = externalJson.isEmpty()
                ? Collections.emptyMap() : SchemaLoader.parseExternalSchemas(externalJson);
        String inline = annotation.value();
        String ref = annotation.ref().trim();
        URI documentUri;
        URI selectedUri;
        String rootDocument;
        boolean rootFromExternal = false;

        if (!inline.isEmpty()) {
            documentUri = URI.create("sjf4j:/" + originName + "/");
            selectedUri = documentUri;
            rootDocument = inline;
        } else {
            URI baseDir = SchemaLoader.Local.DEFAULT_JSON_SCHEMA_DIR;
            if (!ref.isEmpty()) {
                selectedUri = baseDir.resolve(ref);
                if (!selectedUri.isAbsolute()) throw new IllegalArgumentException("non-absolute schema URI: " + ref);
                // Fragments select a schema within the resource; they are not
                // part of the document retrieval URI.
                documentUri = SchemaUtil.documentUri(selectedUri);
                if ("classpath".equals(documentUri.getScheme()) || "file".equals(documentUri.getScheme())) {
                    rootDocument = read(documentUri, resourceDirectory);
                } else {
                    JsonSchema mapped = external.get(documentUri);
                    if (mapped != null) {
                        rootDocument = SchemaSupport.writeJsonValue(mapped);
                        rootFromExternal = true;
                    } else {
                        rootDocument = null;
                    }
                }
            } else {
                String simple = ownerName.substring(ownerName.lastIndexOf('.') + 1);
                documentUri = baseDir.resolve(simple + ".json");
                rootDocument = read(documentUri, resourceDirectory);
                if (rootDocument == null) {
                    String snake = Strings.toSnakeCase(simple);
                    documentUri = baseDir.resolve(snake + ".json");
                }
                selectedUri = documentUri;
                rootDocument = read(documentUri, resourceDirectory);
            }
            if (rootDocument == null) {
                throw new IllegalArgumentException("schema resource not found: " + documentUri
                        + " (resource not found on the compilation classpath or alongside the source set)");
            }
        }

        Map<URI, String> resources = new LinkedHashMap<URI, String>();
        resources.put(documentUri, rootDocument);

        // externalSchemas is an input representation; the registry only sees
        // ordinary (retrieval URI, JsonSchema) pairs.
        for (Map.Entry<URI, JsonSchema> entry : external.entrySet()) {
            String document = SchemaSupport.writeJsonValue(entry.getValue());
            String present = resources.get(entry.getKey());
            if (present != null) {
                if (!(entry.getKey().equals(documentUri) && rootFromExternal)) {
                    throw new IllegalArgumentException("duplicate external schema URI: " + entry.getKey());
                }
            } else {
                resources.put(entry.getKey(), document);
            }
        }

        boolean customDialect = false;
        boolean hasDynamicRef = false;
        for (List<URI> queue = new ArrayList<URI>(resources.keySet()); !queue.isEmpty();) {
            URI uri = queue.remove(0);
            SchemaAnalyzer.Analysis analysis = SchemaAnalyzer.analyze(
                    uri, JsonSchema.fromJson(resources.get(uri)));
            customDialect |= analysis.customDialect;
            hasDynamicRef |= analysis.hasDynamicRef;
            for (URI document : analysis.referencedDocuments) {
                if (resources.containsKey(document)) continue;
                if (!"classpath".equals(document.getScheme())
                        && !"file".equals(document.getScheme())) continue;
                String contents = read(document, resourceDirectory);
                if (contents == null) continue;
                resources.put(document, contents);
                queue.add(document);
            }
        }

        Snapshot snapshot = new Snapshot(resources, selectedUri, external.keySet());
        // Every binding owns a fresh registry. Index its root, directly loaded
        // references, and explicit external resources without sharing plans.
        SchemaRegistry registry = new SchemaRegistry();
        for (int i = 0; i < snapshot.uris.length; i++) {
            if (snapshot.externalUris.contains(snapshot.uris[i])) continue;
            registry.index(URI.create(snapshot.uris[i]), JsonSchema.fromJson(snapshot.documents[i]));
        }
        registry.index(external);
        // Boolean resources are now indexed alongside object resources, so the
        // selected URI must always resolve during annotation processing.
        if (registry.resolve(URI.create(snapshot.selectedUri)) == null) {
            throw new IllegalArgumentException("unresolved selected schema: " + snapshot.selectedUri);
        }
        snapshot.registry = registry;
        // SchemaAnalyzer inspected schema-bearing positions before plan construction.
        snapshot.customDialect = customDialect;
        snapshot.hasDynamicRef = hasDynamicRef;
        return snapshot;
    }

    private String read(URI uri, Path resourceDirectory) {
        URI document = SchemaUtil.documentUri(uri);
        try (Reader reader = openResource(document, resourceDirectory)) {
            if (reader == null) return null;
            StringBuilder text = new StringBuilder();
            char[] buf = new char[4096];
            for (int n; (n = reader.read(buf)) != -1;) text.append(buf, 0, n);
            return text.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot load schema: " + document, e);
        }
    }

    private Reader openResource(URI uri, Path resourceDirectory) throws IOException {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try {
                return Files.newBufferedReader(Paths.get(uri), StandardCharsets.UTF_8);
            } catch (NoSuchFileException e) {
                return null;
            }
        }
        if (!"classpath".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("unsupported compile-time schema URI: " + uri);
        }
        String path = uri.getPath();
        // classpath:/name and classpath:///name are both absolute classpath
        // resource references; classpath resources themselves have no leading slash.
        while (path.startsWith("/")) path = path.substring(1);
        for (StandardLocation location : new StandardLocation[]{
                StandardLocation.CLASS_PATH, StandardLocation.CLASS_OUTPUT, StandardLocation.SOURCE_PATH}) {
            try {
                FileObject file = context.filer.getResource(location, "", path);
                return file.openReader(true);
            } catch (IOException | IllegalArgumentException ignored) {
                // Continue searching compiler resource locations.
            }
        }
        // In javac/IDEA, resources in src/<sourceSet>/resources may not have
        // been copied to the compilation classpath when annotation processing runs.
        // Resolve them relative to the Java source being compiled, not user.dir
        // (which can point to a different module in multi-project builds).
        if (resourceDirectory != null) {
            Path file = resourceDirectory.resolve(path).normalize();
            if (file.startsWith(resourceDirectory) && Files.isRegularFile(file)) {
                return Files.newBufferedReader(file, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * Finds the conventional resources root belonging to the Java compilation
     * unit. This is a fallback only: compiler resource locations take precedence.
     * No Gradle/Maven/IDEA-specific APIs or project-wide directory search.
     */
    private Path findResourceDirectory(Element sourceElement) {
        if (sourceElement == null) return null;
        try {
            URI uri = sourceUri(sourceElement);
            if (uri == null) return null;
            if (!"file".equalsIgnoreCase(uri.getScheme())) return null;
            Path directory = Paths.get(uri).toAbsolutePath().normalize().getParent();
            while (directory != null) {
                Path name = directory.getFileName();
                if (name != null && "java".equals(name.toString())) {
                    Path sourceSet = directory.getParent();
                    if (sourceSet != null) {
                        Path src = sourceSet.getParent();
                        Path srcName = src == null ? null : src.getFileName();
                        if (srcName != null && "src".equals(srcName.toString())) {
                            return sourceSet.resolve("resources").normalize();
                        }
                    }
                }
                directory = directory.getParent();
            }
        } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
            // Some source URIs cannot be represented as local file paths.
        }
        return null;
    }

    /**
     * Uses the javac Tree API when present without linking it at compile time:
     * this processor is compiled with --release 8, where com.sun.source.* is
     * intentionally unavailable on the Java platform API surface.
     */
    private URI sourceUri(Element element) {
        try {
            Class<?> treesType = Class.forName("com.sun.source.util.Trees");
            Object trees = treesType.getMethod("instance", ProcessingEnvironment.class)
                    .invoke(null, context.processingEnvironment);
            Object treePath = treesType.getMethod("getPath", Element.class).invoke(trees, element);
            if (treePath == null) return null;

            Class<?> pathType = Class.forName("com.sun.source.util.TreePath");
            Object unit = pathType.getMethod("getCompilationUnit").invoke(treePath);
            Class<?> unitType = Class.forName("com.sun.source.tree.CompilationUnitTree");
            JavaFileObject file = (JavaFileObject) unitType.getMethod("getSourceFile").invoke(unit);
            return file == null ? null : file.toUri();
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException ignored) {
            // Other compilers or processing wrappers may not expose the Tree API.
            return null;
        }
    }
}
