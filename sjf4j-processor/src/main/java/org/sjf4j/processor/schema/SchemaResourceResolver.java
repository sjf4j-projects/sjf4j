package org.sjf4j.processor.schema;

import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.util.Strings;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.schema.SchemaRegistry;
import org.sjf4j.schema.SchemaLoader;
import org.sjf4j.schema.JsonSchema;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
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

/**
 * Resolves complete schema documents at annotation-processing time.
 * Schemas are analyzed for code generation, but runtime fallback loads the
 * original sources; the parsed document text is not emitted into generated code.
 */
final class SchemaResourceResolver {

    static final class Snapshot {
        final String[] uris;
        final String[] documents;
        final boolean[] fromRegistry;
        final String selectedUri;

        org.sjf4j.schema.SchemaRegistry registry;
        boolean customDialect;

        org.sjf4j.schema.SchemaPlan resolvePlan(String location) {
            if (registry == null) throw new IllegalStateException("schema registry not initialized");
            return registry.resolve(URI.create(location));
        }

        String canonicalLocation(String location) {
            try {
                org.sjf4j.schema.SchemaPlan plan = resolvePlan(location);
                return plan == null || plan.schemaUri() == null
                        ? location : plan.schemaUri().toString();
            } catch (IllegalArgumentException | org.sjf4j.schema.SchemaException missingSubschema) {
                // Some compiler keyword locations are synthetic (e.g. reduced
                // sibling assertion maps), not independent runtime subschemas.
                return location;
            }
        }

        Snapshot(Map<URI, String> resources, URI selectedUri, Set<URI> registryResources) {
            this.selectedUri = selectedUri.toString();
            uris = new String[resources.size()];
            documents = new String[resources.size()];
            fromRegistry = new boolean[resources.size()];
            int i = 0;
            for (Map.Entry<URI, String> entry : resources.entrySet()) {
                uris[i] = entry.getKey().toString();
                fromRegistry[i] = registryResources.contains(entry.getKey());
                documents[i++] = entry.getValue();
            }
        }
    }

    private final ProcessorContext context;
    private final String registryDir;
    private final String registryBaseUri;
    private final Map<String, SchemaRegistry> registryCache;
    private Map<String, IndexedSource> indexedSources;

    SchemaResourceResolver(ProcessorContext context, String registryDir, String registryBaseUri,
                           Map<String, SchemaRegistry> registryCache) {
        this.context = context;
        this.registryDir = registryDir.trim();
        this.registryBaseUri = registryBaseUri.trim();
        this.registryCache = registryCache;
    }

    /** Pre-register each directory/base combination once per processing run. */
    private SchemaRegistry registry() {
        String key = registryDir + "\u0000" + registryBaseUri;
        SchemaRegistry cached = registryCache.get(key);
        if (cached == null) {
            cached = new SchemaRegistry();
            if (!registryDir.isEmpty()) {
                cached.indexDir(URI.create(registryDir),
                        registryBaseUri.isEmpty() ? null : URI.create(registryBaseUri));
            } else if (!registryBaseUri.isEmpty()) {
                throw new IllegalArgumentException("registryBaseUri requires registryDir");
            }
            registryCache.put(key, cached);
        }
        return SchemaRegistry.copyOf(cached);
    }

    Snapshot resolve(ValidJsonSchema annotation, String ownerName, String originName,
                     Element sourceElement) {
        Path resourceDirectory = findResourceDirectory(sourceElement);
        String inline = annotation.value();
        String ref = annotation.ref().trim();
        URI documentUri;
        URI selectedUri;
        String rootDocument;
        boolean rootFromRegistry = false;

        if (!inline.isEmpty()) {
            documentUri = URI.create("sjf4j:/" + originName + "/");
            selectedUri = documentUri;
            rootDocument = inline;
        } else {
            URI baseDir = SchemaRegistry.DEFAULT_JSON_SCHEMA_DIR;
            if (!ref.isEmpty()) {
                selectedUri = baseDir.resolve(ref);
                if (!selectedUri.isAbsolute()) throw new IllegalArgumentException("non-absolute schema URI: " + ref);
                // Fragments select a schema within the resource; they are not
                // part of the document retrieval URI.
                documentUri = URI.create(selectedUri.toString().split("#", 2)[0]);
                if ("classpath".equals(documentUri.getScheme()) || "file".equals(documentUri.getScheme())) {
                    rootDocument = read(documentUri, resourceDirectory);
                } else {
                    IndexedSource indexed = indexedSource(documentUri);
                    rootDocument = indexed == null ? null : indexed.document;
                    if (indexed != null) {
                        documentUri = indexed.retrieval;
                        rootFromRegistry = true;
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
        Set<URI> registryResources = new HashSet<URI>();
        resources.put(documentUri, rootDocument);
        if (rootFromRegistry) registryResources.add(documentUri);

        boolean customDialect = false;
        for (List<URI> queue = new ArrayList<URI>(resources.keySet()); !queue.isEmpty();) {
            URI uri = queue.remove(0);
            CompiledSupport.Analysis analysis = CompiledSupport.analyze(
                    uri, JsonSchema.fromJson(resources.get(uri)));
            customDialect |= analysis.customDialect;
            for (URI document : analysis.referencedDocuments) {
                if (resources.containsKey(document)) continue;
                String contents;
                URI retrieval = document;
                if ("classpath".equals(document.getScheme()) || "file".equals(document.getScheme())) {
                    contents = read(document, resourceDirectory);
                } else {
                    IndexedSource indexed = indexedSource(document);
                    if (indexed == null) continue;
                    retrieval = indexed.retrieval;
                    contents = indexed.document;
                    registryResources.add(retrieval);
                }
                if (contents == null || resources.containsKey(retrieval)) continue;
                resources.put(retrieval, contents);
                queue.add(retrieval);
            }
        }

        Snapshot snapshot = new Snapshot(resources, selectedUri, registryResources);
        // Directory resources are indexed once, prior to root resolution.
        // Each method gets a registry copy so roots cannot leak between methods.
        SchemaRegistry registry = registry();
        for (int i = 0; i < snapshot.uris.length; i++) {
            // indexDir already owns the registry-backed source object.
            // Reindexing a separately parsed copy would be a duplicate URI.
            if (snapshot.fromRegistry[i]) continue;
            registry.index(URI.create(snapshot.uris[i]), JsonSchema.fromJson(snapshot.documents[i]));
        }
        // Boolean resources are now indexed alongside object resources, so the
        // selected URI must always resolve during annotation processing.
        if (registry.resolve(URI.create(snapshot.selectedUri)) == null) {
            throw new IllegalArgumentException("unresolved selected schema: " + snapshot.selectedUri);
        }
        snapshot.registry = registry;
        // This checks every nested schema node compiled by the Planner, not
        // arbitrary instance objects under const/default/enum.
        snapshot.customDialect = customDialect;
        return snapshot;
    }

    private static final class IndexedSource {
        final URI retrieval;
        final String document;
        IndexedSource(URI retrieval, String document) {
            this.retrieval = retrieval;
            this.document = document;
        }
    }

    /**
     * The runtime registry already indexes these files. At compilation time
     * they must also be visible as source documents for direct $ref codegen.
     * Only a referenced document is included in the snapshot, not the whole
     * registry directory.
     */
    private IndexedSource indexedSource(URI target) {
        if (registryDir.isEmpty()) return null;
        if (indexedSources == null) {
            indexedSources = new LinkedHashMap<String, IndexedSource>();
            Path directory;
            try {
                URI location = URI.create(registryDir);
                directory = "file".equals(location.getScheme())
                        ? Paths.get(location) : Paths.get(registryDir);
                directory = directory.toAbsolutePath().normalize();
            } catch (RuntimeException invalid) {
                return null; // The registry reports configuration errors later.
            }
            if (!Files.isDirectory(directory)) return null;
            final Path root = directory;
            URI base = registryBaseUri.isEmpty() ? directory.toUri() : URI.create(registryBaseUri);
            try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                java.util.Iterator<Path> iterator = paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json")).iterator();
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    URI retrieval = base.resolve(relative);
                    String document = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    IndexedSource indexed = new IndexedSource(retrieval, document);
                    indexedSources.putIfAbsent(retrieval.toString(), indexed);
                    Object parsed = org.sjf4j.Sjf4j.global().fromJson(document);
                    if (JsonType.of(parsed) == JsonType.OBJECT) {
                        Object id = Nodes.getInObject(parsed, "$id");
                        if (id instanceof String) {
                            String stripped = org.sjf4j.schema.SchemaUtil.stripFragment((String) id);
                            if (!stripped.isEmpty()) {
                                URI canonical = org.sjf4j.schema.SchemaUtil.resolveUri(retrieval, URI.create(stripped));
                                indexedSources.putIfAbsent(canonical.toString(), indexed);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("cannot index schema registry directory: " + root, e);
            }
        }
        return indexedSources.get(target.toString());
    }

    private String read(URI uri, Path resourceDirectory) {
        URI document = SchemaLoader.documentUri(uri);
        try (java.io.Reader reader = openResource(document, resourceDirectory)) {
            if (reader == null) return null;
            StringBuilder text = new StringBuilder();
            char[] buf = new char[4096];
            for (int n; (n = reader.read(buf)) != -1;) text.append(buf, 0, n);
            return text.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot load schema: " + document, e);
        }
    }

    private java.io.Reader openResource(URI uri, Path resourceDirectory) throws IOException {
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
                        if (src != null && src.getFileName() != null
                                && "src".equals(src.getFileName().toString())) {
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
