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

    private SchemaReferenceResolver(SchemaResourceResolver.Snapshot snapshot) {
        for (int i = 0; i < snapshot.uris.length; i++) {
            URI retrieval = URI.create(snapshot.uris[i]);
            Object raw = Sjf4j.global().fromJson(snapshot.documents[i]);
            index(raw, retrieval, "", retrieval.toString(), "", true);
        }
    }

    static SchemaCompiler.RefTarget resolveRef(SchemaResourceResolver.Snapshot snapshot,
                                               JsonObject schema, String sourceUri) {
        for (String keyword : schema.keySet()) {
            if (!"$ref".equals(keyword) && !SchemaCompiler.isAnnotation(keyword)) return null;
        }
        String literal = schema.getString("$ref");
        if (literal == null) return null;
        return new SchemaReferenceResolver(snapshot).resolve(sourceUri, literal);
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
                       String physicalDocument, String physicalPointer, boolean root) {
        JsonType kind = JsonType.of(node);
        if (kind != JsonType.OBJECT && kind != JsonType.BOOLEAN) return;
        URI resource = parentResource;
        String pointer = resourcePointer;
        if (kind == JsonType.OBJECT) {
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
        Entry entry = new Entry(node, resource.toString(), canonicalLocation);
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

        for (String keyword : new String[]{
                "$defs", "definitions", "properties", "patternProperties",
                "dependentSchemas", "dependencies"}) {
            Object mapping = Nodes.getInObject(node, keyword);
            if (JsonType.of(mapping) != JsonType.OBJECT) continue;
            for (Map.Entry<String, Object> child : Nodes.entrySetInObject(mapping)) {
                index(child.getValue(), resource, append(pointer, keyword, child.getKey()),
                        physicalDocument, append(physicalPointer, keyword, child.getKey()), false);
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
                            physicalDocument, append(physicalPointer, keyword, Integer.toString(i)), false);
                }
            } else if (child != null) {
                index(child, resource, append(pointer, keyword),
                        physicalDocument, append(physicalPointer, keyword), false);
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
        Entry(Object node, String resource, String location) {
            this.node = node;
            this.resource = resource;
            this.location = location;
        }
    }
}
