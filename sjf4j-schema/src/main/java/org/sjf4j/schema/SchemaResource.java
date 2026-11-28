package org.sjf4j.schema;

import org.sjf4j.util.Asserts;

import java.net.URI;

/**
 * A schema document with its retrieval and canonical resource URIs.
 * Resource metadata is immutable; callers must not mutate indexed schema models.
 */
public final class SchemaResource {
    private final JsonSchema schema;
    private final URI retrievalUri;
    private final URI canonicalUri;
    // For embedded resources, identify the original document and pointer within it.
    private final SchemaResource document;
    private final String documentPointer;

    public SchemaResource(URI retrievalUri, JsonSchema schema) {
        this(Asserts.notNull(schema, "schema"),
                retrievalUri == null ? null : SchemaUtil.documentUri(retrievalUri),
                SchemaUtil.resolveUri(retrievalUri == null ? null : SchemaUtil.documentUri(retrievalUri),
                        schema instanceof ObjectSchema && ((ObjectSchema) schema).getId() != null
                                ? URI.create(((ObjectSchema) schema).getId()) : null),
                null, null);
    }

    private SchemaResource(JsonSchema schema, URI retrievalUri, URI canonicalUri,
                           SchemaResource document, String documentPointer) {
        this.schema = schema;
        this.retrievalUri = retrievalUri;
        this.canonicalUri = canonicalUri;
        this.document = document;
        this.documentPointer = documentPointer;
    }

    public JsonSchema schema() {
        return schema;
    }

    public URI retrievalUri() {
        return retrievalUri;
    }

    public URI canonicalUri() {
        return canonicalUri;
    }

    public SchemaPlan createPlan(SchemaRegistry registry) {
        if (schema instanceof BooleanSchema) {
            return SchemaPlan.of(canonicalUri, org.sjf4j.path.PathSegment.Root.INSTANCE, (BooleanSchema) schema);
        }
        return SchemaPlanner.buildPlan(this, registry);
    }

    public SchemaPlan createPlan() {
        return createPlan(null);
    }

    SchemaResource withCanonicalUri(URI uri) {
        return new SchemaResource(schema, retrievalUri, uri, document, documentPointer);
    }

    SchemaResource nested(ObjectSchema child, URI uri, String pointer) {
        return new SchemaResource(child, null, uri, document == null ? this : document,
                documentPointer == null ? pointer : documentPointer + pointer);
    }

    /** Distinguishes a separately indexed root from an embedded resource. */
    boolean isNested() {
        return document != null;
    }

    /**
     * Root identity is the original model and canonical resource URI. Embedded
     * identity is its owning document and full document pointer, not the
     * transient ObjectSchema produced by conversion during planning.
     */
    boolean sameResource(SchemaResource other) {
        if (this == other) return true;
        if (other == null || isNested() != other.isNested()) return false;
        if (!SchemaUtil.normalizeUriKey(canonicalUri).equals(SchemaUtil.normalizeUriKey(other.canonicalUri))) {
            return false;
        }
        if (document == null) return schema == other.schema;
        return document.sameResource(other.document) && documentPointer.equals(other.documentPointer);
    }
}
