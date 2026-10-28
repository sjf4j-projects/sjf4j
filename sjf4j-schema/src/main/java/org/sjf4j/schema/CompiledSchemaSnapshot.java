package org.sjf4j.schema;

import java.net.URI;

/**
 * Frozen, compile-time schema resource graph for generated validators.
 *
 * <p>All referenced documents are indexed before the root is compiled.
 * The registry is retained so generated fallback holders may resolve any
 * child SchemaPlan against the same resource, anchor and reference graph.
 * Schema source files are never reloaded at runtime.</p>
 */
public final class CompiledSchemaSnapshot {

    private final SchemaRegistry registry;
    private final SchemaPlan root;
    private final URI rootUri;

    private CompiledSchemaSnapshot(SchemaRegistry registry, SchemaPlan root, URI rootUri) {
        this.registry = registry;
        this.root = root;
        this.rootUri = rootUri;
    }

    /** Returns the root SchemaPlan for the compiled validator method. */
    public SchemaPlan root() {
        return root;
    }

    /**
     * Resolves a fragment or referenced schema within the frozen resource graph.
     * Used by generated sub-schema fallbacks; relative references use the root
     * retrieval URI as base. Runtime resolution never fetches external documents.
     */
    public SchemaPlan resolve(String uri) {
        URI target = rootUri.resolve(URI.create(uri));
        if (target.equals(rootUri)) return root;
        SchemaPlan plan = registry.resolve(target);
        if (plan == null) {
            throw new SchemaException("unresolved compiled schema snapshot reference: " + target);
        }
        return plan;
    }

    /**
     * Restores a schema snapshot captured at annotation-processing time.
     *
     * @param retrievalUris absolute retrieval URIs for each document
     * @param documents corresponding JSON Schema documents
     * @param rootIndex index of the root document
     */
    public static CompiledSchemaSnapshot create(String[] retrievalUris,
                                                 String[] documents, int rootIndex) {
        if (retrievalUris == null || documents == null ||
                retrievalUris.length == 0 || retrievalUris.length != documents.length ||
                rootIndex < 0 || rootIndex >= documents.length) {
            throw new IllegalArgumentException("invalid compiled schema snapshot");
        }

        SchemaRegistry registry = new SchemaRegistry();
        JsonSchema[] schemas = new JsonSchema[documents.length];

        for (int i = 0; i < documents.length; i++) {
            JsonSchema schema = JsonSchema.fromJson(documents[i]);
            schemas[i] = schema;
            if (schema instanceof ObjectSchema) {
                registry.index(URI.create(retrievalUris[i]), schema);
            }
        }

        URI rootUri = URI.create(retrievalUris[rootIndex]);
        SchemaPlan root = registry.register(rootUri, schemas[rootIndex]);
        return new CompiledSchemaSnapshot(registry, root, rootUri);
    }
}
