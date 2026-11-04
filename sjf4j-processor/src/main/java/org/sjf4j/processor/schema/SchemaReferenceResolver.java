package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;
import org.sjf4j.schema.SchemaUtil;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Compile-time JSON Schema resource index. URI resolution follows lexical $id
 * scopes; $anchor and JSON Pointer fragments address entries in those scopes.
 * Physical retrieval URIs are aliases for roots, not alternate resource bases.
 */
final class SchemaReferenceResolver {
    private final Map<String, Entry> locations = new HashMap<String, Entry>();
    private final Map<String, Entry> fragments = new HashMap<String, Entry>();

    SchemaReferenceResolver(SchemaResourceResolver.Snapshot snapshot) {
        for (int i = 0; i < snapshot.uris.length; i++) {
            URI retrieval = URI.create(snapshot.uris[i]);
            Object raw = Sjf4j.global().fromJson(snapshot.documents[i]);
            index(raw, retrieval, "", retrieval.toString(), "", true, true);
        }
        // A retrieval URI and the root's canonical $id name the same resource.
        // Its named fragments and JSON Pointer fragments must be accessible
        // through either URI. Embedded $id resources do not inherit this alias.
        Map<String, Entry> indexed = new HashMap<String, Entry>(fragments);
        Map<String, Entry> indexedLocations = new HashMap<String, Entry>(locations);
        for (String retrieval : snapshot.uris) {
            Entry root = locations.get(key(retrieval, ""));
            if (root == null) continue;
            String canonicalPrefix = root.resource + "#";
            String retrievalPrefix = retrieval + "#";
            // The enclosing root's pointer namespace also sees embedded $id
            // locations (SchemaPlanner adds their physical paths to byPathPlans).
            for (Map.Entry<String, Entry> location : indexedLocations.entrySet()) {
                if (!location.getKey().startsWith(retrievalPrefix)) continue;
                String suffix = location.getKey().substring(retrieval.length());
                fragments.putIfAbsent(retrieval + suffix, location.getValue());
                fragments.putIfAbsent(root.resource + suffix, location.getValue());
            }
            for (Map.Entry<String, Entry> fragment : indexed.entrySet()) {
                if (!fragment.getKey().startsWith(canonicalPrefix)) continue;
                String alias = retrieval + fragment.getKey().substring(root.resource.length());
                fragments.putIfAbsent(alias, fragment.getValue());
            }
        }
    }

    static String canonicalLocation(SchemaResourceResolver.Snapshot snapshot, String location) {
        SchemaReferenceResolver resolver = snapshot.referenceIndex();
        Entry entry = resolver.locations.get(location);
        if (entry == null) entry = resolver.locations.get(location + "#");
        return entry == null ? location : entry.location;
    }

    static boolean acceptsRefSiblings(SchemaResourceResolver.Snapshot snapshot, String location) {
        SchemaReferenceResolver resolver = snapshot.referenceIndex();
        Entry entry = resolver.locations.get(location);
        if (entry == null) entry = resolver.locations.get(location + "#");
        return entry == null || entry.modern;
    }

    static SchemaCompiler.RefTarget resolveRef(SchemaResourceResolver.Snapshot snapshot,
                                               JsonObject schema, String sourceUri) {
        for (String keyword : schema.keySet()) {
            if (!"$ref".equals(keyword) && !SchemaCompiler.isAnnotation(keyword)) return null;
        }
        String literal = schema.getString("$ref");
        if (literal == null) return null;
        return snapshot.referenceIndex().resolve(sourceUri, literal);
    }

    private SchemaCompiler.RefTarget resolve(String sourceUri, String reference) {
        Entry source = locations.get(sourceUri);
        if (source == null) {
            // Exact same-resource root references also occur without a '#'.
            source = locations.get(sourceUri + "#");
        }
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
        if (result == null) return null;
        return new SchemaCompiler.RefTarget(result.node, result.location);
    }

    private void index(Object node, URI parentResource, String resourcePointer,
                       String physicalDocument, String physicalPointer, boolean root, boolean inheritedModern) {
        JsonType kind = JsonType.of(node);
        if (kind != JsonType.OBJECT && kind != JsonType.BOOLEAN) return;
        URI resource = parentResource;
        String pointer = resourcePointer;
        boolean modern = inheritedModern;
        if (kind == JsonType.OBJECT) {
            Object dialect = Nodes.getInObject(node, "$schema");
            if (dialect instanceof String) {
                org.sjf4j.schema.SchemaDialect detected =
                        org.sjf4j.schema.SchemaDialect.detect((String) dialect);
                if (detected != null) {
                    modern = detected != org.sjf4j.schema.SchemaDialect.DRAFT_07;
                }
            }
            Object id = Nodes.getInObject(node, "$id");
            if (id instanceof String) {
                String stripped = SchemaUtil.stripFragment((String) id);
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

    private static String append(String base, String... tokens) {
        StringBuilder result = new StringBuilder(base);
        for (String token : tokens) {
            result.append('/').append(token.replace("~", "~0").replace("/", "~1"));
        }
        return result.toString();
    }

    private static String key(String resource, String fragment) {
        return resource + "#" + fragment;
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
