package org.sjf4j.processor.schema;

import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaDialect;
import org.sjf4j.schema.SchemaUtil;

import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Annotation-processing-only Schema document analysis.
 * Discovers referenced resources and unknown dialects before runtime
 * SchemaPlanner builds evaluation plans.
 */
final class SchemaSupport {
    private SchemaSupport() {}

    static final class Analysis {
        final Set<URI> referencedDocuments = new LinkedHashSet<>();
        boolean customDialect;
        boolean hasDynamicRef;
    }

    static Analysis analyze(URI retrievalUri, JsonSchema schema) {
        Analysis result = new Analysis();
        collectReferences(schema, retrievalUri, SchemaDialect.DRAFT_2020_12, result);
        return result;
    }

    private static void collectReferences(Object node, URI baseUri,
                                          SchemaDialect inheritedDialect, Analysis result) {
        if (JsonType.of(node) != JsonType.OBJECT) return;

        Object id = Nodes.getInObject(node, "$id");
        URI scopedUri = id instanceof String
                ? SchemaUtil.resolveUri(baseUri, URI.create(SchemaUtil.stripFragment((String) id)))
                : baseUri;

        Object declaredDialect = Nodes.getInObject(node, "$schema");
        SchemaDialect declared = declaredDialect instanceof String
                ? SchemaDialect.detect((String) declaredDialect) : null;
        if (declaredDialect != null && declared == null) result.customDialect = true;
        SchemaDialect dialect = declared == null ? inheritedDialect : declared;
        boolean refOnly = dialect == SchemaDialect.DRAFT_07
                && Nodes.getInObject(node, "$ref") instanceof String;

        for (String keyword : new String[]{"$ref", "$dynamicRef", "$recursiveRef"}) {
            if (!dialect.supportsKeyword(keyword) || (refOnly && !"$ref".equals(keyword))) continue;
            Object ref = Nodes.getInObject(node, keyword);
            if (!(ref instanceof String)) continue;
            if ("$dynamicRef".equals(keyword)) result.hasDynamicRef = true;
            URI target = SchemaUtil.resolveUri(scopedUri, URI.create((String) ref));
            result.referencedDocuments.add(URI.create(SchemaUtil.stripFragment(target.toString())));
        }

        // Definitions remain addressable by a Draft-07 $ref, even though
        // adjacent assertions are ignored. Discover their external references.
        for (String keyword : new String[]{"$defs", "definitions", "properties",
                "patternProperties", "dependentSchemas", "dependencies"}) {
            if (!dialect.supportsKeyword(keyword) || (refOnly && !"definitions".equals(keyword))) continue;
            Object schemas = Nodes.getInObject(node, keyword);
            if (JsonType.of(schemas) != JsonType.OBJECT) continue;
            for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(schemas)) {
                collectReferences(entry.getValue(), scopedUri, dialect, result);
            }
        }

        if (refOnly) return;

        for (String keyword : new String[]{"items", "additionalItems", "additionalProperties",
                "contains", "propertyNames", "not", "if", "then", "else",
                "unevaluatedItems", "unevaluatedProperties", "contentSchema",
                "allOf", "anyOf", "oneOf", "prefixItems"}) {
            if (!dialect.supportsKeyword(keyword)) continue;
            Object child = Nodes.getInObject(node, keyword);
            if (child == null) continue;
            if (JsonType.of(child) == JsonType.ARRAY) {
                for (Iterator<Object> it = Nodes.iteratorInArray(child); it.hasNext();) {
                    collectReferences(it.next(), scopedUri, dialect, result);
                }
            } else {
                collectReferences(child, scopedUri, dialect, result);
            }
        }
    }
}
