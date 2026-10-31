package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates schema references and dispatches supported JSON value kinds.
 * Concrete keyword compilation is delegated to type-focused compilers.
 */
final class SchemaCompiler {
    private final ProcessorContext context;
    private final boolean nullAsMissing;
    private final ObjectCompiler objects;
    private final ScalarCompiler scalars;
    private final ArrayCompiler arrays;
    private final OneOfCompiler oneOf;

    SchemaCompiler(ProcessorContext context, boolean nullAsMissing) {
        this.context = context;
        this.nullAsMissing = nullAsMissing;
        this.objects = new ObjectCompiler(context, this);
        this.scalars = new ScalarCompiler();
        this.arrays = new ArrayCompiler(context, this);
        this.oneOf = new OneOfCompiler(context, objects, this, nullAsMissing);
    }

    SchemaCheck.ObjectCheck compileObject(TypeMirror type,
                                          SchemaResourceResolver.Snapshot snapshot,
                                          boolean nullAsMissing) {
        return objects.compile(type, snapshot, nullAsMissing);
    }

    /** Compiles a non-object root, including a root oneOf or selected JSON Pointer fragment. */
    SchemaCheck compileRoot(TypeMirror type, SchemaResourceResolver.Snapshot snapshot) {
        Object root = org.sjf4j.Sjf4j.global().fromJson(snapshot.documents[0]);
        String uri = snapshot.uris[0];
        if (!snapshot.selectedUri.equals(uri)) {
            RefTarget selected = resolveRef(snapshot,
                    JsonObject.of("$ref", snapshot.selectedUri), uri);
            if (selected == null) return null;
            root = selected.value;
            uri = selected.uri;
        }
        return compileValue(snapshot, root, SchemaValue.of(context, type), uri, 0);
    }

    SchemaCheck compileValue(SchemaResourceResolver.Snapshot snapshot,
                             Object rawSchema, SchemaValue value,
                             String schemaUri, int depth) {
        if (depth > 8) return null;
        if (rawSchema instanceof Boolean) {
            return Boolean.TRUE.equals(rawSchema) ? new SchemaCheck.Pass() : new SchemaCheck.Reject();
        }
        if (JsonType.of(rawSchema) != JsonType.OBJECT) return null;
        JsonObject schema = Nodes.toJsonObject(rawSchema);
        if (schema.containsKey("$ref")) {
            RefTarget target = resolveRef(snapshot, schema, schemaUri);
            if (target == null) return null;
            return compileValue(snapshot, target.value, value, target.uri, depth + 1);
        }
        if (schema.containsKey("oneOf") || schema.containsKey("allOf")
                || schema.containsKey("anyOf") || schema.containsKey("not")
                || schema.containsKey("if") || schema.containsKey("then")
                || schema.containsKey("else")) {
            return compileApplicators(snapshot, schema, value, schemaUri, depth);
        }
        // Format is an assertion under the format-assertion vocabulary, even
        // when strictFormat=false. Only optimize known annotation-default dialects.
        if (schema.containsKey("format") && !canCompileFormat(snapshot)) return null;
        Object typeKeyword = schema.getNode("type");
        if (typeKeyword != null && JsonType.of(typeKeyword) == JsonType.ARRAY) {
            return compileUnionType(schema, value);
        }
        String kind = schema.getString("type");
        if ("boolean".equals(kind) || "null".equals(kind)) {
            return compileUnionType(schema, value);
        }
        if (kind == null) {
            if (schema.containsKey("properties") || schema.containsKey("required")
                    || schema.containsKey("additionalProperties")) {
                if (value.shape == SchemaValue.Shape.STRING
                        || value.shape == SchemaValue.Shape.NUMBER
                        || value.shape == SchemaValue.Shape.BOOLEAN
                        || value.isArrayLike()) {
                    // Object-specific keywords are ignored on non-objects.
                    for (String key : schema.keySet()) {
                        if (!key.equals("properties") && !key.equals("required")
                                && !key.equals("additionalProperties") && !isAnnotation(key))
                            return null;
                    }
                    return new SchemaCheck.Pass();
                }
                return objects.compileNested(value.type, snapshot, schema, schemaUri,
                        nullAsMissing, depth);
            }
            return scalars.compileLiteralsOnly(schema, value);
        }
        if ("string".equals(kind) || "number".equals(kind) || "integer".equals(kind)) {
            return scalars.compile(schema, value, kind);
        }
        if ("array".equals(kind)) {
            return arrays.compile(snapshot, schema, value, schemaUri, depth);
        }
        if ("object".equals(kind)) {
            return objects.compileNested(value.type, snapshot, schema, schemaUri,
                    nullAsMissing, depth);
        }
        return null;
    }

    /** Single boolean/null types and type unions with optional scalar literals. */
    private SchemaCheck compileUnionType(JsonObject schema, SchemaValue value) {
        for (String key : schema.keySet()) {
            if (!key.equals("type") && !key.equals("const") && !key.equals("enum")
                    && !isAnnotation(key)) return null;
        }
        Object raw = schema.getNode("type");
        List<String> types = new ArrayList<String>();
        if (raw instanceof String) {
            types.add((String) raw);
        } else if (JsonType.of(raw) == JsonType.ARRAY) {
            for (int i = 0; i < Nodes.sizeInArray(raw); i++) {
                Object item = Nodes.getInArray(raw, i);
                if (!(item instanceof String)) return null;
                types.add((String) item);
            }
        } else return null;
        if (types.isEmpty()) return null;
        for (String type : types) {
            try {
                JsonType.ofSchema(type);
            } catch (RuntimeException invalidType) {
                return null;
            }
        }
        SchemaCheck check = new SchemaCheck.TypeCheck(types);
        if (!schema.containsKey("const") && !schema.containsKey("enum")) return check;
        JsonObject literals = new JsonObject();
        if (schema.containsKey("const")) literals.put("const", schema.getNode("const"));
        if (schema.containsKey("enum")) literals.put("enum", schema.getNode("enum"));
        SchemaCheck literal = scalars.compileLiteralsOnly(literals, value);
        if (literal == null) return null;
        List<SchemaCheck> all = new ArrayList<SchemaCheck>();
        all.add(check);
        all.add(literal);
        return new SchemaCheck.AllCheck(all);
    }

    /**
     * Applicator checks share a value but must not share local variables or
     * evaluated-location state. Unsupported branches fall back as a complete
     * schema subtree; compiling only some branches is not equivalent.
     */
    private SchemaCheck compileApplicators(SchemaResourceResolver.Snapshot snapshot,
                                           JsonObject schema, SchemaValue value,
                                           String schemaUri, int depth) {
        List<SchemaCheck> checks = new ArrayList<SchemaCheck>();
        JsonObject siblings = new JsonObject();
        for (String key : schema.keySet()) {
            if (!isApplicator(key)) siblings.put(key, schema.getNode(key));
        }
        if (!siblings.isEmpty()) {
            boolean annotationsOnly = true;
            for (String key : siblings.keySet()) {
                if (!isAnnotation(key)) {
                    annotationsOnly = false;
                    break;
                }
            }
            if (!annotationsOnly) {
                SchemaCheck sibling = compileValue(snapshot, siblings, value, schemaUri, depth + 1);
                if (sibling == null || sibling.hasFallback()) return null;
                checks.add(sibling);
            }
        }
        if (schema.containsKey("oneOf")) {
            JsonObject isolated = new JsonObject();
            isolated.put("oneOf", schema.getNode("oneOf"));
            SchemaCheck compiled = oneOf.compile(snapshot, isolated, value, schemaUri, depth);
            if (compiled == null || compiled.hasFallback()) {
                // Preserve the specialized subtype fast path for oneOf alone.
                if (checks.isEmpty() && !schema.containsKey("allOf")
                        && !schema.containsKey("anyOf") && !schema.containsKey("not")
                        && !(schema.containsKey("if")
                             && (schema.containsKey("then") || schema.containsKey("else")))) {
                    return compiled;
                }
                return null;
            }
            checks.add(compiled);
        }
        if (schema.containsKey("if")
                && (schema.containsKey("then") || schema.containsKey("else"))) {
            SchemaCheck condition = compileValue(snapshot, schema.getNode("if"), value,
                    keywordUri(schemaUri, "if"), depth + 1);
            if (condition == null || condition.hasFallback()) return null;
            // Constant conditions select only one branch, even when the other
            // branch has unsupported keywords.
            String selected = condition.kind == SchemaCheck.Kind.PASS ? "then"
                    : condition.kind == SchemaCheck.Kind.REJECT ? "else" : null;
            if (selected != null) {
                if (schema.containsKey(selected)) {
                    SchemaCheck chosen = compileValue(snapshot, schema.getNode(selected), value,
                            keywordUri(schemaUri, selected), depth + 1);
                    if (chosen == null || chosen.hasFallback()) return null;
                    checks.add(chosen);
                }
            } else {
                SchemaCheck thenCheck = null;
                SchemaCheck elseCheck = null;
                if (schema.containsKey("then")) {
                    thenCheck = compileValue(snapshot, schema.getNode("then"), value,
                            keywordUri(schemaUri, "then"), depth + 1);
                    if (thenCheck == null || thenCheck.hasFallback()) return null;
                }
                if (schema.containsKey("else")) {
                    elseCheck = compileValue(snapshot, schema.getNode("else"), value,
                            keywordUri(schemaUri, "else"), depth + 1);
                    if (elseCheck == null || elseCheck.hasFallback()) return null;
                }
                checks.add(new SchemaCheck.ConditionalCheck(condition, thenCheck, elseCheck));
            }
        }
        // Missing if means then/else have no effect. A lone if also has
        // no validation effect and does not need to be compiled.
        for (String keyword : new String[]{"allOf", "anyOf", "not"}) {
            if (!schema.containsKey(keyword)) continue;
            boolean negation = "not".equals(keyword);
            Object raw = schema.getNode(keyword);
            if (!negation && JsonType.of(raw) != JsonType.ARRAY) return null;
            int count = negation ? 1 : Nodes.sizeInArray(raw);
            if (count == 0) return null;
            List<SchemaCheck> branches = new ArrayList<SchemaCheck>(count);
            for (int i = 0; i < count; i++) {
                String childUri = keywordUri(schemaUri, keyword)
                        + (negation ? "" : "/" + i);
                SchemaCheck child = compileValue(snapshot,
                        negation ? raw : Nodes.getInArray(raw, i),
                        value, childUri, depth + 1);
                if (child == null || child.hasFallback()) return null;
                branches.add(child);
            }
            SchemaCheck.Kind kind = negation ? SchemaCheck.Kind.NOT
                    : "allOf".equals(keyword) ? SchemaCheck.Kind.ALL_OF
                    : SchemaCheck.Kind.ANY_OF;
            if (kind == SchemaCheck.Kind.NOT) {
                SchemaCheck child = branches.get(0);
                checks.add(child.kind == SchemaCheck.Kind.PASS ? new SchemaCheck.Reject()
                        : child.kind == SchemaCheck.Kind.REJECT ? new SchemaCheck.Pass()
                        : new SchemaCheck.ApplicatorCheck(kind, branches));
            } else if (kind == SchemaCheck.Kind.ALL_OF) {
                boolean rejects = false;
                for (SchemaCheck branch : branches) {
                    if (branch.kind == SchemaCheck.Kind.REJECT) rejects = true;
                }
                if (rejects) checks.add(new SchemaCheck.Reject());
                else {
                    branches.removeIf(branch -> branch.kind == SchemaCheck.Kind.PASS);
                    checks.add(branches.isEmpty() ? new SchemaCheck.Pass()
                            : new SchemaCheck.ApplicatorCheck(kind, branches));
                }
            } else {
                boolean passes = false;
                for (SchemaCheck branch : branches) {
                    if (branch.kind == SchemaCheck.Kind.PASS) passes = true;
                }
                if (passes) checks.add(new SchemaCheck.Pass());
                else {
                    branches.removeIf(branch -> branch.kind == SchemaCheck.Kind.REJECT);
                    checks.add(branches.isEmpty() ? new SchemaCheck.Reject()
                            : new SchemaCheck.ApplicatorCheck(kind, branches));
                }
            }
        }
        if (checks.isEmpty()) return new SchemaCheck.Pass();
        for (SchemaCheck check : checks) {
            if (check.kind == SchemaCheck.Kind.REJECT) return check;
        }
        checks.removeIf(check -> check.kind == SchemaCheck.Kind.PASS);
        if (checks.isEmpty()) return new SchemaCheck.Pass();
        return checks.size() == 1 ? checks.get(0) : new SchemaCheck.AllCheck(checks);
    }

    private static boolean isApplicator(String key) {
        return "oneOf".equals(key) || "allOf".equals(key)
                || "anyOf".equals(key) || "not".equals(key)
                || "if".equals(key) || "then".equals(key) || "else".equals(key);
    }

    static String keywordUri(String base, String keyword) {
        return base + (base.contains("#") ? "/" : "#/") + keyword;
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

    static String childUri(String base, String keyword, int index) {
        return base + (base.contains("#") ? "/" : "#/") + keyword + "/" + index;
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
