package org.sjf4j.processor.schema;

import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.path.JsonPointer;
import java.util.ArrayList;
import java.util.List;

/**
 * Compile-time schema reference resolution, shared by the root validator,
 * nested keyword compiler, and oneOf compilation.
 *
 * <p>Only statically identifiable targets are expanded. References whose
 * canonical resource scope depends on an embedded $id remain runtime fallback.
 * This is intentionally conservative: incorrect direct validation is worse
 * than an unsupported direct compilation.</p>
 */
final class SchemaReferenceResolver {
    private SchemaReferenceResolver() {}

    /** Compile-time resolution for static pointers and named anchors. */
    static SchemaCompiler.RefTarget resolveRef(SchemaResourceResolver.Snapshot snapshot,
                                JsonObject schema, String sourceUri) {
        for (String keyword : schema.keySet()) {
            if (!keyword.equals("$ref") && !SchemaCompiler.isAnnotation(keyword)) return null;
        }
        String literal = schema.getString("$ref");
        if (literal == null || hasScopedId(snapshot, sourceUri) || schema.containsKey("$id")) return null;
        java.net.URI target;
        try {
            target = java.net.URI.create(sourceUri).resolve(literal);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String document = org.sjf4j.schema.SchemaLoader.documentUri(target).toString();
        Object node = null;
        for (int i = 0; i < snapshot.uris.length; i++) {
            if (snapshot.uris[i].equals(document)) {
                node = org.sjf4j.Sjf4j.global().fromJson(snapshot.documents[i]);
                break;
            }
        }
        if (node == null) return null;
        String fragment = target.getFragment();
        if (fragment != null && !fragment.isEmpty() && !fragment.startsWith("/")) {
            // Named anchors are found only at schema locations in the same resource.
            // Embedded $id resource scopes remain delegated to SchemaRegistry.
            String pointer = findAnchorPointer(node, fragment);
            if (pointer == null) return null;
            target = java.net.URI.create(document + (pointer.isEmpty() ? "" : "#" + pointer));
            fragment = pointer;
        }
        if (hasScopedId(snapshot, target.toString())) return null;
        if (fragment != null && !fragment.isEmpty()) {
            try {
                node = JsonPointer.parse(fragment).getNode(node);
            } catch (RuntimeException ex) {
                return null;
            }
            if (node == null) return null;
        }
        return new SchemaCompiler.RefTarget(node, target.toString());
    }

    /** Finds a unique anchor within the current resource (never in instance data). */
    private static String findAnchorPointer(Object root, String anchor) {
        List<String> matches = new ArrayList<String>();
        collectAnchors(root, anchor, "", true, matches);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static void collectAnchors(Object node, String anchor, String pointer,
                                       boolean root, List<String> matches) {
        if (JsonType.of(node) != JsonType.OBJECT) return;
        if (!root && Nodes.getInObject(node, "$id") != null) return;
        if (anchor.equals(Nodes.getInObject(node, "$anchor"))) matches.add(pointer);
        for (String keyword : new String[]{
                "$defs", "definitions", "properties", "patternProperties",
                "dependentSchemas", "dependencies"}) {
            Object mapping = Nodes.getInObject(node, keyword);
            if (JsonType.of(mapping) != JsonType.OBJECT) continue;
            for (java.util.Map.Entry<String, Object> entry : Nodes.entrySetInObject(mapping)) {
                collectAnchors(entry.getValue(), anchor,
                        pointer + "/" + keyword + "/" + pointerToken(entry.getKey()),
                        false, matches);
            }
        }
        for (String keyword : new String[]{
                "items", "additionalItems", "additionalProperties", "contains",
                "propertyNames", "not", "if", "then", "else",
                "unevaluatedItems", "unevaluatedProperties", "contentSchema",
                "allOf", "anyOf", "oneOf", "prefixItems"}) {
            Object child = Nodes.getInObject(node, keyword);
            if (JsonType.of(child) == JsonType.ARRAY) {
                for (int i = 0; i < Nodes.sizeInArray(child); i++) {
                    collectAnchors(Nodes.getInArray(child, i), anchor,
                            pointer + "/" + keyword + "/" + i, false, matches);
                }
            } else if (child != null) {
                collectAnchors(child, anchor, pointer + "/" + keyword, false, matches);
            }
        }
    }

    private static String pointerToken(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    /**
     * Detect resource scopes that cannot safely be represented by our simple
     * retrieval-URI + JSON Pointer expansion. Such references are delegated to
     * SchemaPlan rather than risk compiling the wrong schema branch.
     */
    private static boolean hasScopedId(SchemaResourceResolver.Snapshot snapshot, String reference) {
        java.net.URI uri;
        try {
            uri = java.net.URI.create(reference);
        } catch (IllegalArgumentException ex) {
            return true;
        }
        String document = org.sjf4j.schema.SchemaLoader.documentUri(uri).toString();
        for (int i = 0; i < snapshot.uris.length; i++) {
            if (!snapshot.uris[i].equals(document)) continue;
            Object node = org.sjf4j.Sjf4j.global().fromJson(snapshot.documents[i]);
            if (hasId(node)) return true;
            String fragment = uri.getFragment();
            if (fragment == null || fragment.isEmpty()) return false;
            if (!fragment.startsWith("/")) return true;
            try {
                // Inspect every ancestor, not isolated segments against the
                // document root. Nested $defs/property pointers must resolve.
                int offset = 1;
                while (offset <= fragment.length()) {
                    int slash = fragment.indexOf('/', offset);
                    int end = slash < 0 ? fragment.length() : slash;
                    String prefix = fragment.substring(0, end);
                    Object scoped = JsonPointer.parse(prefix).getNode(node);
                    if (scoped == null || hasId(scoped)) return true;
                    if (slash < 0) break;
                    offset = slash + 1;
                }
            } catch (RuntimeException ex) {
                return true;
            }
            return false;
        }
        // A canonical URI of an embedded $id cannot be mapped safely through
        // the physical document snapshot; leave it to SchemaRegistry.
        return true;
    }

    private static boolean hasId(Object node) {
        return JsonType.of(node) == JsonType.OBJECT && Nodes.getInObject(node, "$id") != null;
    }


}
