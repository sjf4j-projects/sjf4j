package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.schema.SchemaResourceIndex;

/**
 * Processor adapter around the schema module's canonical resource index.
 * JSON Schema URI and anchor semantics are owned by SchemaResourceIndex.
 */
final class SchemaReferenceResolver {
    private SchemaReferenceResolver() {}

    static String canonicalLocation(SchemaResourceResolver.Snapshot snapshot, String uri) {
        return snapshot.referenceIndex().canonicalLocation(uri);
    }

    static SchemaCompiler.RefTarget resolveRef(SchemaResourceResolver.Snapshot snapshot,
                                               JsonObject schema, String sourceUri) {
        String literal = schema.getString("$ref");
        if (literal == null) return null;
        SchemaResourceIndex.Target target = snapshot.referenceIndex().resolve(sourceUri, literal);
        return target == null ? null : new SchemaCompiler.RefTarget(target.node, target.uri);
    }
}
