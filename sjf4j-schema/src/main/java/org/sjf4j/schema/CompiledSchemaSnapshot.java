package org.sjf4j.schema;

import java.net.URI;

/**
 * Restores a fixed compile-time schema resource graph for generated validators.
 *
 * <p>All referenced resources are indexed before the root is compiled.
 * No user schema file is reloaded at runtime; generated validators supply
 * the exact JSON text captured during annotation processing. Plans are
 * expected to be retained in a generated lazy holder.</p>
 */
public final class CompiledSchemaSnapshot {

    private CompiledSchemaSnapshot() {}

    /**
     * Builds the root plan from a frozen resource graph.
     *
     * @param retrievalUris absolute retrieval URIs, one per resource
     * @param documents complete JSON Schema documents in matching order
     * @param rootIndex index of the schema document being validated
     */
    public static SchemaPlan create(String[] retrievalUris, String[] documents, int rootIndex) {
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
                URI uri = URI.create(retrievalUris[i]);
                registry.index(uri, schema);
            }
        }
        return registry.register(URI.create(retrievalUris[rootIndex]), schemas[rootIndex]);
    }
}
