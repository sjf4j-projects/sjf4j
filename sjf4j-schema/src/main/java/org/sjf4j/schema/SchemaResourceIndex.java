package org.sjf4j.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Compile-time JSON Schema resource index. URI resolution follows lexical $id
 * scopes; $anchor and JSON Pointer fragments address entries in those scopes.
 * Physical retrieval URIs are aliases for roots, not alternate resource bases.
 */
public final class SchemaResourceIndex {
    private final Map<String, Entry> locations = new HashMap<String, Entry>();
    private final boolean draft2020Only;
    private final Map<String, Entry> fragments = new HashMap<String, Entry>();

    public SchemaResourceIndex(Map<URI, ?> documents) {
        this(documents, false);
    }

    public SchemaResourceIndex(Map<URI, ?> documents, boolean draft2020Only) {
        this.draft2020Only = draft2020Only;
        for (Map.Entry<URI, ?> document : documents.entrySet()) {
            URI retrieval = document.getKey();
            Object raw = document.getValue() instanceof String
                    ? Sjf4j.global().fromJson((String) document.getValue()) : document.getValue();
            index(raw, retrieval, "", retrieval.toString(), "", true, true);
        }
        // A retrieval URI and the root's canonical $id name the same resource.
        // Its named fragments and JSON Pointer fragments must be accessible
        // through either URI. Embedded $id resources do not inherit this alias.
        Map<String, Entry> indexed = new HashMap<String, Entry>(fragments);
        Map<String, Entry> indexedLocations = new HashMap<String, Entry>(locations);
        for (URI sourceUri : documents.keySet()) {
            String retrieval = sourceUri.toString();
            Entry root = locations.get(key(retrieval, ""));
            if (root == null) continue;
            String canonicalPrefix = key(root.resource, "");
            String retrievalPrefix = key(retrieval, "");
            // The enclosing root's pointer namespace also sees embedded $id
            // locations (SchemaPlanner adds their physical paths to byPathPlans).
            for (Map.Entry<String, Entry> location : indexedLocations.entrySet()) {
                if (!location.getKey().startsWith(retrievalPrefix)) continue;
                String suffix = location.getKey().substring(retrievalPrefix.length());
                Entry entry = location.getValue();
                locations.putIfAbsent(key(root.resource, suffix), entry);
                fragments.putIfAbsent(key(retrieval, suffix), entry);
                fragments.putIfAbsent(key(root.resource, suffix), entry);
            }
            for (Map.Entry<String, Entry> fragment : indexed.entrySet()) {
                if (!fragment.getKey().startsWith(canonicalPrefix)) continue;
                String suffix = fragment.getKey().substring(canonicalPrefix.length());
                fragments.putIfAbsent(key(retrieval, suffix), fragment.getValue());
            }
        }
    }

    public String canonicalLocation(String location) {
        Entry entry = locations.get(locationKey(location));
        if (entry == null) entry = locations.get(location + "#");
        return entry == null ? location : entry.location;
    }

    public boolean acceptsRefSiblings(String location) {
        Entry entry = locations.get(locationKey(location));
        if (entry == null) entry = locations.get(location + "#");
        return entry == null || entry.modern;
    }

    /** Looks up an already absolute resource URI without requiring a source location. */
    public Target get(URI absoluteUri) {
        if (absoluteUri == null || !absoluteUri.isAbsolute()) return null;
        String resource = SchemaUtil.stripFragment(absoluteUri.toString());
        String fragment = absoluteUri.getFragment();
        Entry result = fragments.get(key(resource, fragment == null ? "" : fragment));
        return result == null ? null : new Target(result.node, result.location);
    }

    public Target resolve(String sourceUri, String reference) {
        Entry source = locations.get(locationKey(sourceUri));
        if (source == null) source = locations.get(sourceUri + "#");
        if (source == null) return null;
        final URI target;
        try {
            target = SchemaUtil.resolveUri(URI.create(source.resource), URI.create(reference));
        } catch (RuntimeException ex) {
            return null;
        }
        String resource = SchemaUtil.stripFragment(target.toString());
        String fragment = target.getFragment();
        Entry result = fragments.get(key(resource, fragment == null ? "" : fragment));
        return result == null ? null : new Target(result.node, result.location);
    }

    /** The schema node and canonical location selected by a reference. */
    public static final class Target {
        public final Object node;
        public final String uri;
        Target(Object node, String uri) {
            this.node = node;
            this.uri = uri;
        }
    }

    private void index(Object node, URI parentResource, String resourcePointer,
                       String physicalDocument, String physicalPointer, boolean root, boolean inheritedModern) {
        JsonType kind = JsonType.of(node);
        if (kind != JsonType.OBJECT && kind != JsonType.BOOLEAN) return;
        URI resource = parentResource;
        String pointer = resourcePointer;
        boolean modern = inheritedModern;
        String legacyIdAnchor = null;
        if (kind == JsonType.OBJECT) {
            Object dialect = Nodes.getInObject(node, "$schema");
            if (draft2020Only && dialect != null &&
                    (!(dialect instanceof String) ||
                            SchemaDialect.detect((String) dialect) != SchemaDialect.DRAFT_2020_12)) {
                throw new IllegalArgumentException("CompiledValidator supports only JSON Schema Draft 2020-12; "
                        + "unsupported $schema: " + dialect + " at " + parentResource + "#" + resourcePointer);
            }
            if (dialect instanceof String) {
                org.sjf4j.schema.SchemaDialect detected =
                        org.sjf4j.schema.SchemaDialect.detect((String) dialect);
                if (detected != null) {
                    modern = detected != org.sjf4j.schema.SchemaDialect.DRAFT_07;
                }
            }
            Object id = Nodes.getInObject(node, "$id");
            if (id instanceof String) {
                try {
                    legacyIdAnchor = URI.create((String) id).getFragment();
                } catch (IllegalArgumentException malformedId) {
                    // SchemaRegistry reports malformed ids during plan validation.
                }
                // In Draft-07 a nested $ref suppresses its sibling $id scope.
                String stripped = !root && !modern && Nodes.getInObject(node, "$ref") instanceof String
                        ? "" : SchemaUtil.stripFragment((String) id);
                if (!stripped.isEmpty()) {
                    URI resolved = SchemaUtil.resolveUri(parentResource, URI.create(stripped));
                    if (!resolved.equals(parentResource)) {
                        resource = resolved;
                        pointer = "";
                    }
                }
            }
        }
        String physicalLocation = key(physicalDocument, physicalPointer);
        String canonicalLocation = key(resource.toString(), pointer);
        Entry entry = new Entry(node, resource.toString(), canonicalLocation, modern);
        locations.putIfAbsent(physicalLocation, entry);
        locations.putIfAbsent(canonicalLocation, entry);
        fragments.putIfAbsent(canonicalLocation, entry);
        // A root $id retains its physical retrieval alias, but its relative
        // references resolve against the canonical resource.
        if (root) fragments.putIfAbsent(physicalLocation, entry);

        if (kind != JsonType.OBJECT) return;
        Object anchor = Nodes.getInObject(node, "$anchor");
        if (!(anchor instanceof String) && legacyIdAnchor != null && !legacyIdAnchor.isEmpty()) {
            // Draft-07 location-independent identifiers also use $id fragments.
            fragments.putIfAbsent(key(resource.toString(), legacyIdAnchor), entry);
        }
        if (anchor instanceof String && !((String) anchor).isEmpty()) {
            fragments.putIfAbsent(key(resource.toString(), (String) anchor), entry);
        }
        Object dynamicAnchor = Nodes.getInObject(node, "$dynamicAnchor");
        if (dynamicAnchor instanceof String && !((String) dynamicAnchor).isEmpty()) {
            fragments.putIfAbsent(key(resource.toString(), (String) dynamicAnchor), entry);
        }

        for (String keyword : new String[]{
                "$defs", "definitions", "properties", "patternProperties",
                "dependentSchemas", "dependencies"}) {
            Object mapping = Nodes.getInObject(node, keyword);
            if (JsonType.of(mapping) != JsonType.OBJECT) continue;
            for (Map.Entry<String, Object> child : Nodes.entrySetInObject(mapping)) {
                index(child.getValue(), resource, append(pointer, keyword, child.getKey()),
                        physicalDocument, append(physicalPointer, keyword, child.getKey()), false, modern);
            }
        }
        for (String keyword : new String[]{
                "items", "additionalItems", "additionalProperties", "contains",
                "propertyNames", "not", "if", "then", "else", "unevaluatedItems",
                "unevaluatedProperties", "contentSchema", "allOf", "anyOf", "oneOf",
                "prefixItems"}) {
            Object child = Nodes.getInObject(node, keyword);
            if (JsonType.of(child) == JsonType.ARRAY) {
                for (int i = 0; i < Nodes.sizeInArray(child); i++) {
                    index(Nodes.getInArray(child, i), resource, append(pointer, keyword, Integer.toString(i)),
                            physicalDocument, append(physicalPointer, keyword, Integer.toString(i)), false, modern);
                }
            } else if (child != null) {
                index(child, resource, append(pointer, keyword),
                        physicalDocument, append(physicalPointer, keyword), false, modern);
            }
        }
    }

    /**
     * Compiler source locations may contain URI-escaped JSON Pointer segments.
     * The schema index stores decoded RFC 6901 pointers; decode URI escapes
     * (not ~0/~1) before matching the source location. Never decode '+' as space.
     */
    private static String locationKey(String location) {
        int hash = location.indexOf('#');
        if (hash < 0) return location;
        try {
            String fragment = URI.create(location).getFragment();
            return key(location.substring(0, hash), fragment == null ? "" : fragment);
        } catch (IllegalArgumentException invalidRawFragment) {
            // Locations produced from already decoded schema names can contain
            // quote or percent characters; those are already index keys.
            return location;
        }
    }

    private static String append(String base, String... tokens) {
        StringBuilder result = new StringBuilder(base);
        for (String token : tokens) {
            result.append('/').append(token.replace("~", "~0").replace("/", "~1"));
        }
        return result.toString();
    }

    private static String key(String resource, String fragment) {
        // Java URI resolution can change file:/// to file:/ while preserving
        // the same absolute resource; use the registry's canonical key.
        return SchemaUtil.normalizeUriKey(resource) + "#" + fragment;
    }

    private static final class Entry {
        final Object node;
        final String resource;
        final String location;
        final boolean modern;
        Entry(Object node, String resource, String location, boolean modern) {
            this.node = node;
            this.resource = resource;
            this.location = location;
            this.modern = modern;
        }
    }
}
