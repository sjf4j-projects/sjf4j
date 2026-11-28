package org.sjf4j.schema;

import org.sjf4j.util.Asserts;

import java.net.URI;

/**
 * A schema document with its retrieval and canonical resource URIs.
 * Resource metadata is immutable; callers must not mutate indexed schema models.
 */
final class SchemaResource {
    final JsonSchema schema;
    final URI retrievalUri;
    final URI canonicalUri;
    // For embedded resources, identify the original document and pointer within it.
    final SchemaResource document;
    final String documentPointer;

    SchemaResource(URI retrievalUri, JsonSchema schema) {
        this.schema = Asserts.notNull(schema, "schema");
        this.retrievalUri = retrievalUri == null ? null : SchemaUtil.documentUri(retrievalUri);
        String id = schema instanceof ObjectSchema ? ((ObjectSchema) schema).getId() : null;
        this.canonicalUri = SchemaUtil.resolveUri(this.retrievalUri, id == null ? null : URI.create(id));
        this.document = null;
        this.documentPointer = null;
    }

    private SchemaResource(JsonSchema schema, URI retrievalUri, URI canonicalUri,
                           SchemaResource document, String documentPointer) {
        this.schema = schema;
        this.retrievalUri = retrievalUri;
        this.canonicalUri = canonicalUri;
        this.document = document;
        this.documentPointer = documentPointer;
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
