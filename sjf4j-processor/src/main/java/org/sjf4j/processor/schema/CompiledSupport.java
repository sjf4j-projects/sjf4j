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
 * Discovers referenced resources and enforces the compiled dialect contract
 * before runtime SchemaPlanner builds evaluation plans.
 */
final class CompiledSupport {
    private CompiledSupport() {}

    static final class Analysis {
        final Set<URI> referencedDocuments = new LinkedHashSet<URI>();
        boolean customDialect;
        boolean hasDynamicRef;
    }

    static Analysis analyze(URI retrievalUri, JsonSchema schema) {
        Analysis result = new Analysis();
        collectReferences(schema, retrievalUri, result);
        return result;
    }

    private static void collectReferences(Object node, URI baseUri, Analysis result) {
        if (JsonType.of(node) != JsonType.OBJECT) return;

        Object id = Nodes.getInObject(node, "$id");
        URI scopedUri = id instanceof String
                ? SchemaUtil.resolveUri(baseUri, URI.create(SchemaUtil.stripFragment((String) id)))
                : baseUri;

        Object declaredDialect = Nodes.getInObject(node, "$schema");
        if (declaredDialect != null) {
            SchemaDialect dialect = declaredDialect instanceof String
                    ? SchemaDialect.detect((String) declaredDialect) : null;
            if (dialect == SchemaDialect.DRAFT_07 || dialect == SchemaDialect.DRAFT_2019_09) {
                throw new IllegalArgumentException("CompiledValidator supports only JSON Schema Draft 2020-12; "
                        + "unsupported $schema: " + declaredDialect + " at " + scopedUri);
            }
            if (dialect == null) result.customDialect = true;
        }

        for (String keyword : new String[]{"$ref", "$dynamicRef", "$recursiveRef"}) {
            Object ref = Nodes.getInObject(node, keyword);
            if (!(ref instanceof String)) continue;
            if ("$dynamicRef".equals(keyword)) result.hasDynamicRef = true;
            URI target = SchemaUtil.resolveUri(scopedUri, URI.create((String) ref));
            result.referencedDocuments.add(URI.create(SchemaUtil.stripFragment(target.toString())));
        }

        for (String keyword : new String[]{"$defs", "definitions", "properties",
                "patternProperties", "dependentSchemas", "dependencies"}) {
            Object schemas = Nodes.getInObject(node, keyword);
            if (JsonType.of(schemas) != JsonType.OBJECT) continue;
            for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(schemas)) {
                collectReferences(entry.getValue(), scopedUri, result);
            }
        }

        for (String keyword : new String[]{"items", "additionalItems", "additionalProperties",
                "contains", "propertyNames", "not", "if", "then", "else",
                "unevaluatedItems", "unevaluatedProperties", "contentSchema",
                "allOf", "anyOf", "oneOf", "prefixItems"}) {
            Object child = Nodes.getInObject(node, keyword);
            if (child == null) continue;
            if (JsonType.of(child) == JsonType.ARRAY) {
                for (Iterator<Object> it = Nodes.iteratorInArray(child); it.hasNext();) {
                    collectReferences(it.next(), scopedUri, result);
                }
            } else {
                collectReferences(child, scopedUri, result);
            }
        }
    }
}
