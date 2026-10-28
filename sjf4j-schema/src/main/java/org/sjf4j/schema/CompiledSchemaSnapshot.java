package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;

import java.net.URI;

/**
 * Frozen, compile-time schema resource graph for generated validators.
 *
 * <p>All referenced documents, including boolean schemas, are indexed before
 * the selected schema is resolved. Nothing is loaded from user resource files
 * at runtime.</p>
 */
public final class CompiledSchemaSnapshot {

    private final SchemaRegistry registry;
    private final SchemaPlan root;
    private final SchemaPlan selected;
    private final URI rootUri;

    private CompiledSchemaSnapshot(SchemaRegistry registry, SchemaPlan root,
                                   SchemaPlan selected, URI rootUri) {
        this.registry = registry;
        this.root = root;
        this.selected = selected;
        this.rootUri = rootUri;
    }

    /** Returns the document's root plan, regardless of annotation fragment selection. */
    public SchemaPlan root() {
        return root;
    }

    /** Returns the exact plan selected by the annotation's ref, including its fragment. */
    public SchemaPlan selected() {
        return selected;
    }

    /** Resolves a child or external plan within the same frozen resource graph. */
    public SchemaPlan resolve(String uri) {
        URI target = rootUri.resolve(URI.create(uri));
        if (target.equals(rootUri)) return root;
        SchemaPlan plan = registry.resolve(target);
        if (plan == null) {
            throw new SchemaException("unresolved compiled schema snapshot reference: " + target);
        }
        return plan;
    }

    /** Builds a snapshot selecting the document's root schema. */
    public static CompiledSchemaSnapshot create(String[] retrievalUris,
                                                 String[] documents, int rootIndex) {
        if (retrievalUris == null || rootIndex < 0 || rootIndex >= retrievalUris.length) {
            throw new IllegalArgumentException("invalid compiled schema snapshot");
        }
        return create(retrievalUris, documents, rootIndex, retrievalUris[rootIndex]);
    }

    /**
     * Builds a snapshot selecting the full annotation reference (including fragment).
     *
     * @param retrievalUris resource URIs without fragments
     * @param documents complete JSON Schema documents matching those URIs
     * @param rootIndex index of the annotation's retrieval document
     * @param selectedUri absolute annotation URI, possibly containing a fragment
     */
    public static CompiledSchemaSnapshot create(String[] retrievalUris, String[] documents,
                                                 int rootIndex, String selectedUri) {
        if (retrievalUris == null || documents == null ||
                retrievalUris.length == 0 || retrievalUris.length != documents.length ||
                rootIndex < 0 || rootIndex >= documents.length || selectedUri == null) {
            throw new IllegalArgumentException("invalid compiled schema snapshot");
        }

        SchemaRegistry registry = new SchemaRegistry();
        JsonSchema[] schemas = new JsonSchema[documents.length];

        for (int i = 0; i < documents.length; i++) {
            URI retrievalUri = URI.create(retrievalUris[i]);
            JsonSchema schema = JsonSchema.fromJson(documents[i]);
            schemas[i] = schema;

            if (schema instanceof ObjectSchema) {
                registry.index(retrievalUri, schema);
            } else {
                // Boolean schema resources must be visible to $ref resolution.
                registry.putPlan(retrievalUri, SchemaPlan.of(
                        retrievalUri, PathSegment.Root.INSTANCE, (BooleanSchema) schema));
            }
        }

        URI rootUri = URI.create(retrievalUris[rootIndex]);
        SchemaPlan root = schemas[rootIndex] instanceof BooleanSchema
                ? registry.resolve(rootUri)
                : registry.register(rootUri, schemas[rootIndex]);

        URI target = rootUri.resolve(URI.create(selectedUri));
        SchemaPlan selected = target.equals(rootUri) ? root : registry.resolve(target);
        if (selected == null) {
            throw new SchemaException("unresolved compiled schema selection: " + target);
        }
        return new CompiledSchemaSnapshot(registry, root, selected, rootUri);
    }
}
