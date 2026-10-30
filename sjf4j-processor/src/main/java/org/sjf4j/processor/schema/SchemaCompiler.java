package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.type.TypeMirror;

/**
 * Coordinates schema references and dispatches supported JSON value kinds.
 * Concrete keyword compilation is delegated to type-focused compilers.
 */
final class SchemaCompiler {
    private final ObjectCompiler objects;
    private final ScalarCompiler scalars;
    private final ArrayCompiler arrays;
    private final OneOfCompiler oneOf;

    SchemaCompiler(ProcessorContext context) {
        this.objects = new ObjectCompiler(context, this);
        this.scalars = new ScalarCompiler();
        this.arrays = new ArrayCompiler(context, this);
        this.oneOf = new OneOfCompiler(context, objects);
    }

    SchemaCheck.ObjectCheck compileObject(TypeMirror type,
                                          SchemaResourceResolver.Snapshot snapshot,
                                          boolean nullAsMissing) {
        return objects.compile(type, snapshot, nullAsMissing);
    }

    SchemaCheck compileValue(SchemaResourceResolver.Snapshot snapshot,
                             Object rawSchema, SchemaValue value,
                             String schemaUri, int depth) {
        if (depth > 8) return null;
        if (rawSchema instanceof Boolean) {
            return Boolean.TRUE.equals(rawSchema) ? new SchemaCheck.Pass() : null;
        }
        if (JsonType.of(rawSchema) != JsonType.OBJECT) return null;
        JsonObject schema = Nodes.toJsonObject(rawSchema);
        if (schema.containsKey("$ref")) {
            RefTarget target = resolveRef(snapshot, schema, schemaUri);
            if (target == null) return null;
            return compileValue(snapshot, target.value, value, target.uri, depth + 1);
        }
        if (schema.containsKey("oneOf")) {
            return oneOf.compile(snapshot, schema, value, schemaUri, depth);
        }
        // Format is an assertion under the format-assertion vocabulary, even
        // when strictFormat=false. Only optimize known annotation-default dialects.
        if (schema.containsKey("format") && !canCompileFormat(snapshot)) return null;
        String kind = schema.getString("type");
        if (kind == null) return scalars.compileLiteralsOnly(schema, value);
        if ("string".equals(kind) || "number".equals(kind) || "integer".equals(kind)) {
            return scalars.compile(schema, value, kind);
        }
        if ("array".equals(kind)) {
            return arrays.compile(snapshot, schema, value, schemaUri, depth);
        }
        return null;
    }

    private static boolean canCompileFormat(SchemaResourceResolver.Snapshot snapshot) {
        for (String source : snapshot.documents) {
            if (source.contains("\"format-assertion\"") || source.contains("\"$vocabulary\""))
                return false;
            Object raw = org.sjf4j.Sjf4j.global().fromJson(source);
            if (JsonType.of(raw) != JsonType.OBJECT) continue;
            String dialect = Nodes.toJsonObject(raw).getString("$schema");
            if (dialect != null
                    && !dialect.startsWith("https://json-schema.org/draft/2020-12/schema")
                    && !dialect.startsWith("https://json-schema.org/draft/2019-09/schema")
                    && !dialect.startsWith("http://json-schema.org/draft-07/schema")
                    && !dialect.startsWith("https://json-schema.org/draft-07/schema"))
                return false;
        }
        return true;
    }

    /** Only JSON Schema annotation / definition keywords may be skipped. */
    static boolean isAnnotation(String key) {
        return key.equals("$schema") || key.equals("$id") || key.equals("title")
                || key.equals("description") || key.equals("default") || key.equals("examples")
                || key.equals("$comment") || key.equals("deprecated") || key.equals("readOnly")
                || key.equals("writeOnly") || key.equals("definitions") || key.equals("$defs");
    }

    /** A compile-time resolution only; unsupported anchors or sibling keywords fall back. */
    static RefTarget resolveRef(SchemaResourceResolver.Snapshot snapshot,
                                JsonObject schema, String sourceUri) {
        for (String keyword : schema.keySet()) {
            if (!keyword.equals("$ref") && !isAnnotation(keyword)) return null;
        }
        String literal = schema.getString("$ref");
        if (literal == null || hasScopedId(snapshot, sourceUri) || schema.containsKey("$id")) return null;
        java.net.URI target;
        try {
            target = java.net.URI.create(sourceUri).resolve(literal);
        } catch (IllegalArgumentException e) {
            return null;
        }
        // A nested $id establishes a new resource base. Do not compile a
        // pointer under that scope using the retrieval URI: the runtime
        // SchemaRegistry resolves canonical resources correctly.
        if (hasScopedId(snapshot, target.toString())) return null;
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
        if (fragment != null && !fragment.isEmpty()) {
            if (!fragment.startsWith("/")) return null;
            try {
                node = JsonPointer.parse(fragment).getNode(node);
            } catch (RuntimeException ex) {
                return null;
            }
            if (node == null) return null;
        }
        return new RefTarget(node, target.toString());
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
            Object root = node;
            try {
                for (PathSegment segment : JsonPointer.parse(fragment).segments()) {
                    if (segment == PathSegment.Root.INSTANCE) continue;
                    node = JsonPointer.fromLast(segment).getNode(root);
                    if (node == null || hasId(node)) return true;
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

    static final class RefTarget {
        final Object value;
        final String uri;
        RefTarget(Object value, String uri) {
            this.value = value;
            this.uri = uri;
        }
    }
}
