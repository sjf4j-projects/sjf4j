package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
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

    SchemaCompiler(ProcessorContext context) {
        this.objects = new ObjectCompiler(context, this);
        this.scalars = new ScalarCompiler();
        this.arrays = new ArrayCompiler(context, this);
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
        String kind = schema.getString("type");
        if (kind == null) return null;
        if ("string".equals(kind) || "number".equals(kind) || "integer".equals(kind)) {
            return scalars.compile(schema, value, kind);
        }
        if ("array".equals(kind)) {
            return arrays.compile(snapshot, schema, value, schemaUri, depth);
        }
        return null;
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
        if (literal == null) return null;
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
        if (fragment != null && !fragment.isEmpty()) {
            if (!fragment.startsWith("/")) return null; // Named anchors need planner resolution.
            String[] segments = fragment.substring(1).split("/", -1);
            for (String part : segments) {
                String name = part.replace("~1", "/").replace("~0", "~");
                if (org.sjf4j.JsonType.of(node) == org.sjf4j.JsonType.OBJECT) {
                    node = org.sjf4j.Nodes.getInObject(node, name);
                } else if (org.sjf4j.JsonType.of(node) == org.sjf4j.JsonType.ARRAY) {
                    try {
                        node = org.sjf4j.Nodes.getInArray(node, Integer.parseInt(name));
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                } else return null;
                if (node == null) return null;
            }
        }
        return new RefTarget(node, target.toString());
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
