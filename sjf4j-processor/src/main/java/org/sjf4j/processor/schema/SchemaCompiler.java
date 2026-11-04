package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.schema.BooleanSchema;
import org.sjf4j.schema.ObjectSchema;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Coordinates schema references and dispatches supported JSON value kinds.
 * Concrete keyword compilation is delegated to type-focused compilers.
 */
final class SchemaCompiler {
    private final ProcessorContext context;
    private final ObjectCompiler objects;
    private final ScalarCompiler scalars;
    private final ArrayCompiler arrays;
    private final OneOfCompiler oneOf;
    private final Map<String, SchemaCheck.ReferenceCheck> boundReferences = new HashMap<String, SchemaCheck.ReferenceCheck>();
    private String fallbackReason;
    private int activeReferences;

    /** First semantic obstacle seen while compiling a schema tree. */
    String fallbackReason() {
        return fallbackReason == null ? "unsupported schema construct or Java value projection" : fallbackReason;
    }

    private void unsupported(String reason) {
        if (fallbackReason == null) fallbackReason = reason;
    }

    SchemaCompiler(ProcessorContext context) {
        this.context = context;
        this.objects = new ObjectCompiler(context, this);
        this.scalars = new ScalarCompiler();
        this.arrays = new ArrayCompiler(context, this);
        this.oneOf = new OneOfCompiler(context, objects, this);
    }

    /** Compiles a non-object root, including a root oneOf or selected JSON Pointer fragment. */
    SchemaCheck compileRoot(TypeMirror type, SchemaResourceResolver.Snapshot snapshot) {
        // A custom meta-schema can redefine vocabularies, including the
        // semantics of an otherwise empty or ref-only schema. Never emit
        // direct predicates under an unknown dialect.
        if (snapshot.customDialect) {
            unsupported("custom $schema / $vocabulary at " + snapshot.selectedUri
                    + " requires runtime vocabulary interpretation");
            return null;
        }
        Object root = org.sjf4j.Sjf4j.global().fromJson(snapshot.documents[0]);
        String uri = snapshot.uris[0];
        if (!snapshot.selectedUri.equals(uri)) {
            RefTarget selected = resolveRef(snapshot,
                    JsonObject.of("$ref", snapshot.selectedUri), uri);
            if (selected == null) return null;
            root = selected.value;
            uri = selected.uri;
        }
        SchemaCheck compiled = compileValue(snapshot, root, SchemaValue.of(context, type), uri, 0);
        if ((compiled == null || compiled.hasFallback()) && fallbackReason == null) {
            unsupported("schema at " + snapshot.selectedUri
                    + " requires runtime validation: no complete direct predicate is available");
        }
        return compiled;
    }

    SchemaCheck compileValue(SchemaResourceResolver.Snapshot snapshot,
                             Object rawSchema, SchemaValue value,
                             String schemaUri, int depth) {
        SchemaCheck compiled = compileSchema(snapshot, rawSchema, value, schemaUri, depth);
        if (compiled == null && fallbackReason == null) {
            unsupported(explainFailure(snapshot, rawSchema, schemaUri));
        }
        return compiled;
    }

    private SchemaCheck compileSchema(SchemaResourceResolver.Snapshot snapshot,
                                      Object rawSchema, SchemaValue value,
                                      String schemaUri, int depth) {
        if (rawSchema instanceof Boolean || rawSchema instanceof BooleanSchema) {
            boolean allowed = rawSchema instanceof BooleanSchema
                    ? ((BooleanSchema) rawSchema).booleanValue()
                    : Boolean.TRUE.equals(rawSchema);
            return allowed ? new SchemaCheck.Pass()
                    : new SchemaCheck.Reject(schemaDiagnostic(schemaUri, "false"));
        }
        if (JsonType.of(rawSchema) != JsonType.OBJECT) return null;
        JsonObject schema = schemaObject(rawSchema);
        // Canonical locations are indexed by lexical $id scope.
        schemaUri = snapshot.canonicalLocation(schemaUri);
        // Static local coverage is safe only for the selected root schema,
        // with no applicator/reference or dependent-schema annotation exchange.
        // Child schemas are compiled normally but cannot claim their parent's
        // evaluated locations.
        boolean unevaluatedObject = schema.containsKey("unevaluatedProperties");
        boolean unevaluatedArray = schema.containsKey("unevaluatedItems");
        if (unevaluatedObject || unevaluatedArray) {
            String keyword = unevaluatedObject ? "unevaluatedProperties" : "unevaluatedItems";
            String interfering = null;
            for (String key : new String[]{"allOf", "anyOf", "oneOf", "not", "if", "then",
                    "else", "$ref", "$dynamicRef", "dependentSchemas", "contains"}) {
                if (schema.containsKey(key)) {
                    interfering = key;
                    break;
                }
            }
            if (interfering == null && unevaluatedObject
                    && schema.containsKey("additionalProperties")) interfering = "additionalProperties";
            // In a single schema object, successful 'items' covers all remaining
            // indices after prefixItems; unevaluatedItems has no unvisited index.
            // Its assertion still runs normally, so failure cannot be hidden.
            if (interfering == null && unevaluatedObject && unevaluatedArray)
                interfering = "unevaluatedItems";
            if (depth != 0 || interfering != null) {
                String parent = interfering == null ? enclosingKeyword(schemaUri) : interfering;
                if (parent == null && activeReferences > 0) parent = "$ref";
                unsupported(parent == null
                        ? keyword + " in a nested schema requires runtime fallback: cross-schema evaluated-location tracking is not supported by CompiledValidator at "
                                + keywordUri(schemaUri, keyword)
                        : parent + " + " + keyword + " requires runtime fallback: cross-schema evaluated-location tracking is not supported by CompiledValidator at "
                                + keywordUri(schemaUri, keyword));
                return null;
            }
        }
        if (schema.containsKey("$ref")) {
            JsonObject referenceSchema = JsonObject.of("$ref", schema.getString("$ref"));
            RefTarget target = resolveRef(snapshot, referenceSchema, schemaUri);
            if (target == null) return null;
            String referenceKey = target.uri + "\\u0000" + (value.type == null ? "Object" : value.type.toString())
                    + "\\u0000" + value.shape;
            SchemaCheck.ReferenceCheck reference = boundReferences.get(referenceKey);
            SchemaCheck referenced;
            if (reference == null) {
                reference = new SchemaCheck.ReferenceCheck(target.uri, diagnostic(schemaUri, "$ref"));
                boundReferences.put(referenceKey, reference);
                try {
                    activeReferences++;
                    reference.target = compileValue(snapshot, target.value, value, target.uri, depth + 1);
                } finally {
                    activeReferences--;
                    reference.binding = false;
                }
                // The cache is an active reference chain, not global memoization.
                // Keeping completed nodes would lose the referring keyword's
                // diagnostic location and force needless helper calls.
                boundReferences.remove(referenceKey);
                if (reference.target == null) return null;
                referenced = reference.recursive ? reference : reference.target;
            } else {
                if (reference.binding) reference.recursive = true;
                referenced = reference;
            }
            if (referenced.hasFallback()) return null;
            // Draft 2020-12: adjacent assertions are independent of $ref.
            if (schema.size() == 1) return referenced;
            JsonObject adjacent = new JsonObject();
            for (String keyword : schema.keySet()) {
                if (!"$ref".equals(keyword)) adjacent.put(keyword, schema.getNode(keyword));
            }
            if (!adjacent.isEmpty()) {
                SchemaCheck siblings = compileValue(snapshot, adjacent, value, schemaUri, depth + 1);
                if (siblings == null || siblings.hasFallback()) return null;
                return new SchemaCheck.AllCheck(java.util.Arrays.asList(referenced, siblings));
            }
            return referenced;
        }
        // No keyword, including type, selects a compilation strategy for
        // other keywords. Applicators compile their child schemas recursively;
        // the sibling assertions are compiled by the same keyword dispatcher.
        if (schema.containsKey("oneOf") || schema.containsKey("allOf")
                || schema.containsKey("anyOf") || schema.containsKey("not")
                || schema.containsKey("if") || schema.containsKey("then")
                || schema.containsKey("else")) {
            return compileApplicators(snapshot, schema, value, schemaUri, depth);
        }
        return compileKeywords(snapshot, schema, value, schemaUri, depth);
    }

    /**
     * Compile each assertion family independently, then conjunct the results.
     * A type keyword is just another independent assertion: it neither enables
     * nor disables string, numeric, array, or object keywords.
     *
     * A few object/array keywords share a semantic evaluation scope (e.g.
     * additionalProperties needs the local properties coverage). The family
     * compilers preserve that scope; they do not depend on a type assertion.
     */
    private SchemaCheck compileKeywords(SchemaResourceResolver.Snapshot snapshot,
                                        JsonObject schema, SchemaValue value,
                                        String uri, int depth) {
        JsonObject types = new JsonObject();
        JsonObject literals = new JsonObject();
        JsonObject strings = new JsonObject();
        JsonObject numbers = new JsonObject();
        JsonObject array = new JsonObject();
        JsonObject object = new JsonObject();

        for (String keyword : schema.keySet()) {
            if (isAnnotation(keyword)) continue;
            // In the standard Draft 2020-12 Content vocabulary these keywords
            // are annotations, not validation assertions. Never assume that
            // semantic role for an unverified custom dialect/vocabulary.
            if (keyword.equals("contentEncoding") || keyword.equals("contentMediaType")
                    || keyword.equals("contentSchema")) {
                if (!canCompileStandardDialect(snapshot)) return null;
                continue;
            }
            switch (keyword) {
                case "type":
                    types.put(keyword, schema.getNode(keyword));
                    break;
                case "const": case "enum":
                    literals.put(keyword, schema.getNode(keyword));
                    break;
                case "minLength": case "maxLength": case "pattern": case "format":
                    strings.put(keyword, schema.getNode(keyword));
                    break;
                case "minimum": case "maximum": case "exclusiveMinimum":
                case "exclusiveMaximum": case "multipleOf":
                    numbers.put(keyword, schema.getNode(keyword));
                    break;
                case "items": case "unevaluatedItems": case "prefixItems": case "uniqueItems":
                case "minItems": case "maxItems":
                case "contains": case "minContains": case "maxContains":
                    array.put(keyword, schema.getNode(keyword));
                    break;
                case "properties": case "unevaluatedProperties": case "patternProperties": case "propertyNames":
                case "required": case "additionalProperties": case "minProperties":
                case "maxProperties": case "dependentRequired": case "dependentSchemas":
                    object.put(keyword, schema.getNode(keyword));
                    break;
                default:
                    // Never omit an unsupported keyword while compiling others.
                    if ("$dynamicRef".equals(keyword)) {
                        unsupported("$dynamicRef at " + keywordUri(uri, keyword)
                                + " requires runtime dynamic-scope resolution");
                    } else if ("$vocabulary".equals(keyword)) {
                        unsupported(dialectFallbackReason(snapshot, uri));
                    } else {
                        unsupported("unsupported keyword '" + keyword + "' at " + keywordUri(uri, keyword)
                                + " requires runtime evaluation");
                    }
                    return null;
            }
        }
        if (types.isEmpty() && literals.isEmpty() && strings.isEmpty()
                && numbers.isEmpty() && array.isEmpty() && object.isEmpty()) {
            return new SchemaCheck.Pass();
        }

        // Custom vocabularies can redefine whether a keyword is an assertion.
        // A syntactically compilable keyword is not necessarily active.
        if (!canCompileStandardDialect(snapshot)) {
            unsupported(dialectFallbackReason(snapshot, uri));
            return null;
        }

        List<SchemaCheck> checks = new ArrayList<SchemaCheck>();
        if (!types.isEmpty()) {
            SchemaCheck typeCheck = compileUnionType(types, value, uri);
            if (typeCheck == null) return null;
            checks.add(typeCheck);
        }
        if (!literals.isEmpty()) {
            Object enumValue = literals.getNode("enum");
            if (literals.containsKey("enum") && JsonType.of(enumValue) == JsonType.ARRAY
                    && Nodes.sizeInArray(enumValue) == 0) {
                return new SchemaCheck.Reject(diagnostic(uri, "enum"));
            }
            SchemaCheck literal = scalars.compileLiteralsOnly(literals, value, uri);
            if (literal == null) return null;
            checks.add(literal);
        }
        if (!strings.isEmpty()) {
            SchemaValue stringValue = conditionalValue(value, "STRING");
            SchemaCheck check = scalars.compile(strings, stringValue, "string", uri);
            if (check == null) return null;
            checks.add(new SchemaCheck.ShapeGuard("STRING", stringValue, check));
        }
        if (!numbers.isEmpty()) {
            SchemaValue numberValue = conditionalValue(value, "NUMBER");
            SchemaCheck check = scalars.compile(numbers, numberValue, "number", uri);
            if (check == null) return null;
            checks.add(new SchemaCheck.ShapeGuard("NUMBER", numberValue, check));
        }
        if (!array.isEmpty()) {
            SchemaValue arrayValue = conditionalValue(value, "ARRAY");
            SchemaCheck check = arrays.compile(snapshot, array, arrayValue, uri, depth);
            if (check == null) return null;
            checks.add(new SchemaCheck.ShapeGuard("ARRAY", arrayValue, check));
        }
        if (!object.isEmpty()) {
            // Analyze the entire object-keyword scope with a representation-
            // neutral value first. A final POJO's missing or constant property
            // must not hide a schema feature that the compiler cannot handle.
            SchemaCheck semantic = objects.compileNested(null, snapshot, object, uri, depth);
            if (semantic == null) return null;
            SchemaCheck check = semantic;
            if (value.type != null && value.shape == SchemaValue.Shape.OBJECT) {
                // Only after schema coverage succeeds may we select faster
                // static POJO access. If it is unsafe, retain Nodes traversal.
                SchemaCheck optimized = objects.compileNested(value.type, snapshot, object, uri, depth);
                // A localized fallback is still a compiled semantic check.
                // Keep it when selecting direct POJO getters; never erase
                // unsupported child constraints because of representation.
                if (optimized != null && optimized.hasFallback() == semantic.hasFallback()) {
                    check = optimized;
                }
            }
            checks.add(new SchemaCheck.ShapeGuard("OBJECT", value, check));
        }
        if (checks.isEmpty()) return new SchemaCheck.Pass();
        return checks.size() == 1 ? checks.get(0) : new SchemaCheck.AllCheck(checks);
    }

    /**
     * Instance applicability is a JSON-semantic test, not a Schema declaration.
     * Preserve known static shape fast paths; for Java scalar types that cannot
     * be the target shape, generate the generic expression behind the guard.
     */
    private SchemaValue conditionalValue(SchemaValue value, String jsonType) {
        boolean matching = ("STRING".equals(jsonType) && value.shape == SchemaValue.Shape.STRING)
                || ("NUMBER".equals(jsonType) && value.shape == SchemaValue.Shape.NUMBER)
                || ("ARRAY".equals(jsonType) && value.isArrayLike());
        return matching ? value : SchemaValue.of(context, null);
    }

    /** Compile only the type keyword; every other assertion is independent. */
    private SchemaCheck compileUnionType(JsonObject schema, SchemaValue value, String schemaUri) {
        if (schema.size() != 1 || !schema.containsKey("type")) return null;
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
        return new SchemaCheck.TypeCheck(types, diagnostic(schemaUri, "type"));
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
                checks.add(child.kind == SchemaCheck.Kind.PASS
                        ? new SchemaCheck.Reject(diagnostic(schemaUri, "not"))
                        : child.kind == SchemaCheck.Kind.REJECT ? new SchemaCheck.Pass()
                        : new SchemaCheck.ApplicatorCheck(kind, diagnostic(schemaUri, "not"), branches));
            } else if (kind == SchemaCheck.Kind.ALL_OF) {
                // Keep every assertion, including constant-false branches. The
                // boolean emitter still short-circuits at runtime, while the
                // diagnostic emitter must retain all failing allOf branches.
                branches.removeIf(branch -> branch.kind == SchemaCheck.Kind.PASS);
                checks.add(branches.isEmpty() ? new SchemaCheck.Pass()
                        : new SchemaCheck.ApplicatorCheck(kind, diagnostic(schemaUri, keyword), branches));
            } else {
                boolean passes = false;
                for (SchemaCheck branch : branches) {
                    if (branch.kind == SchemaCheck.Kind.PASS) passes = true;
                }
                if (passes) checks.add(new SchemaCheck.Pass());
                else {
                    branches.removeIf(branch -> branch.kind == SchemaCheck.Kind.REJECT);
                    checks.add(branches.isEmpty()
                            ? new SchemaCheck.Reject(diagnostic(schemaUri, keyword))
                            : new SchemaCheck.ApplicatorCheck(kind, diagnostic(schemaUri, keyword), branches));
                }
            }
        }
        if (checks.isEmpty()) return new SchemaCheck.Pass();
        checks.removeIf(check -> check.kind == SchemaCheck.Kind.PASS);
        if (checks.isEmpty()) return new SchemaCheck.Pass();
        return checks.size() == 1 ? checks.get(0) : new SchemaCheck.AllCheck(checks);
    }

    /**
     * Explain the first unsupported schema location, not simply the root where
     * compilation eventually returned null. Never invent a keyword pair: a
     * single dynamic reference or unsupported dialect is also a valid reason.
     */
    private static String explainFailure(SchemaResourceResolver.Snapshot snapshot,
                                         Object rawSchema, String uri) {
        if (JsonType.of(rawSchema) != JsonType.OBJECT)
            return "schema at " + uri + " has no supported direct representation";
        JsonObject schema = schemaObject(rawSchema);
        if (schema.containsKey("$dynamicRef")) {
            return "$dynamicRef at " + keywordUri(uri, "$dynamicRef")
                    + " requires runtime dynamic-scope resolution";
        }
        if (!canCompileStandardDialect(snapshot)) return dialectFallbackReason(snapshot, uri);
        String[] ordered = {"unevaluatedProperties", "unevaluatedItems", "$ref", "oneOf",
                "anyOf", "allOf", "not", "if", "then", "else", "contains",
                "dependentSchemas", "properties", "patternProperties", "additionalProperties",
                "prefixItems", "items", "const", "enum", "type", "format", "pattern",
                "multipleOf", "required", "dependentRequired", "propertyNames"};
        String first = null;
        for (String keyword : ordered) {
            if (!schema.containsKey(keyword)) continue;
            if (first == null) first = keyword;
            String enclosing = enclosingKeyword(uri);
            if (enclosing != null && ("unevaluatedProperties".equals(keyword)
                    || "unevaluatedItems".equals(keyword))) {
                return enclosing + " + " + keyword
                        + " requires runtime fallback: cross-schema evaluated-location tracking is not supported by CompiledValidator at " + keywordUri(uri, keyword);
            }
            if ("$ref".equals(keyword)) {
                return "$ref at " + keywordUri(uri, keyword)
                        + " cannot be fully resolved or directly compiled";
            }
        }
        if (first != null) {
            return "schema keyword '" + first + "' at " + keywordUri(uri, first)
                    + " cannot be fully compiled with this schema and Java value projection";
        }
        return "schema at " + uri + " cannot be fully compiled with this Java value projection";
    }

    private static String dialectFallbackReason(SchemaResourceResolver.Snapshot snapshot, String uri) {
        for (int i = 0; i < snapshot.documents.length; i++) {
            String document = snapshot.documents[i];
            if (document.contains("\"$vocabulary\"")) {
                return "$vocabulary at " + snapshot.uris[i]
                        + " requires runtime vocabulary interpretation";
            }
            if (document.contains("\"format-assertion\"")) {
                return "format-assertion vocabulary at " + snapshot.uris[i]
                        + " requires runtime dialect interpretation";
            }
        }
        return "custom $schema at " + uri + " requires runtime vocabulary interpretation";
    }

    /** Nearest enclosing applicator or structural keyword in a JSON Pointer. */
    private static String enclosingKeyword(String uri) {
        int hash = uri.indexOf('#');
        if (hash < 0) return null;
        String pointer = decodeUriFragment(uri.substring(hash + 1));
        String[] keywords = {"allOf", "anyOf", "oneOf", "not", "if", "then",
                "else", "contains", "dependentSchemas", "properties",
                "patternProperties", "items", "prefixItems"};
        String enclosing = null;
        int nearest = -1;
        for (String keyword : keywords) {
            int at = pointer.lastIndexOf("/" + keyword + "/");
            if (at > nearest) {
                nearest = at;
                enclosing = keyword;
            }
            if (pointer.endsWith("/" + keyword) && pointer.length() - keyword.length() - 1 > nearest) {
                nearest = pointer.length() - keyword.length() - 1;
                enclosing = keyword;
            }
        }
        return enclosing;
    }

    private static boolean isApplicator(String key) {
        return "oneOf".equals(key) || "allOf".equals(key)
                || "anyOf".equals(key) || "not".equals(key)
                || "if".equals(key) || "then".equals(key) || "else".equals(key);
    }

    /**
     * Applies a nested resource identifier before deriving keyword locations.
     * A changed $id starts a new schema resource, so its keyword pointer resets
     * to that resource's root just as it does in SchemaPlanner.
     */
    static String effectiveSchemaUri(String schemaUri, JsonObject schema) {
        Object rawId = schema.getNode("$id");
        if (!(rawId instanceof String)) return schemaUri;
        String id = org.sjf4j.schema.SchemaUtil.stripFragment((String) rawId);
        if (id.isEmpty()) return schemaUri;
        java.net.URI base = resourceUri(schemaUri);
        java.net.URI resolved = org.sjf4j.schema.SchemaUtil.resolveUri(
                base, java.net.URI.create(id));
        return resolved.equals(base) ? schemaUri : resolved.toString();
    }

    /**
     * Creates source metadata for a JSON Schema keyword at the current schema URI.
     * The URI may include a JSON Pointer fragment identifying a nested schema.
     */
    static SchemaCheck.Diagnostic diagnostic(String schemaUri, String keyword) {
        return createDiagnostic(schemaUri, keyword, true);
    }

    /** Creates source metadata for a boolean false schema, which has no keyword token. */
    static SchemaCheck.Diagnostic schemaDiagnostic(String schemaUri, String keyword) {
        return createDiagnostic(schemaUri, keyword, false);
    }

    private static SchemaCheck.Diagnostic createDiagnostic(String schemaUri, String keyword,
                                                            boolean appendKeyword) {
        int hash = schemaUri.indexOf('#');
        String fragment = hash < 0 ? null : schemaUri.substring(hash + 1);
        String pointer = fragment == null || fragment.isEmpty()
                ? "" : decodeUriFragment(fragment);
        if (!pointer.isEmpty() && !pointer.startsWith("/")) return null;
        if (appendKeyword) {
            pointer += "/" + keyword.replace("~", "~0").replace("/", "~1");
        }
        return new SchemaCheck.Diagnostic(keyword, resourceUri(schemaUri).toString(), pointer);
    }

    /** Returns the resource part without asking URI to parse a possibly raw JSON Pointer fragment. */
    private static java.net.URI resourceUri(String schemaUri) {
        int hash = schemaUri.indexOf('#');
        return java.net.URI.create(hash < 0 ? schemaUri : schemaUri.substring(0, hash));
    }

    /** Decodes percent-encoded UTF-8 bytes without treating '+' as a space. */
    private static String decodeUriFragment(String fragment) {
        StringBuilder decoded = new StringBuilder(fragment.length());
        for (int i = 0; i < fragment.length();) {
            if (fragment.charAt(i) != '%' || i + 2 >= fragment.length()
                    || Character.digit(fragment.charAt(i + 1), 16) < 0
                    || Character.digit(fragment.charAt(i + 2), 16) < 0) {
                decoded.append(fragment.charAt(i++));
                continue;
            }
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            while (i + 2 < fragment.length() && fragment.charAt(i) == '%'
                    && Character.digit(fragment.charAt(i + 1), 16) >= 0
                    && Character.digit(fragment.charAt(i + 2), 16) >= 0) {
                int value = Character.digit(fragment.charAt(i + 1), 16) * 16
                        + Character.digit(fragment.charAt(i + 2), 16);
                bytes.write(value);
                i += 3;
            }
            decoded.append(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return decoded.toString();
    }

    static String keywordUri(String base, String keyword) {
        return base + (base.contains("#") ? "/" : "#/") + keyword;
    }

    /** The supported Draft 2020-12 always defines dependentRequired. */
    static Boolean supportsDependentRequired(SchemaResourceResolver.Snapshot snapshot,
                                              String schemaUri, JsonObject schema) {
        return canCompileStandardDialect(snapshot) && !schema.containsKey("$vocabulary")
                ? Boolean.TRUE : null;
    }

    /** Unknown vocabularies can change assertion semantics, so remain fallback. */
    private static boolean canCompileStandardDialect(SchemaResourceResolver.Snapshot snapshot) {
        if (snapshot.customDialect) return false;
        for (String source : snapshot.documents) {
            if (source.contains("\"format-assertion\"") || source.contains("\"$vocabulary\""))
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
                || key.equals("writeOnly") || key.equals("definitions") || key.equals("$defs")
                || key.equals("$anchor") || key.equals("$dynamicAnchor")
                || key.equals("$recursiveAnchor");
    }

    /** JOJO metadata is not part of the JSON Schema keyword object. */
    static JsonObject schemaObject(Object rawSchema) {
        return rawSchema instanceof ObjectSchema
                ? new JsonObject(((ObjectSchema) rawSchema).dynamicMembers())
                : Nodes.toJsonObject(rawSchema);
    }

    static RefTarget resolveRef(SchemaResourceResolver.Snapshot snapshot,
                                JsonObject schema, String sourceUri) {
        String literal = schema.getString("$ref");
        if (literal == null) return null;
        try {
            java.net.URI absolute = org.sjf4j.schema.SchemaUtil.resolveUri(
                    java.net.URI.create(sourceUri), java.net.URI.create(literal));
            org.sjf4j.schema.SchemaPlan plan = snapshot.registry.resolve(absolute);
            return plan == null ? null : new RefTarget(plan.schema(),
                    plan.schemaUri().toString());
        } catch (RuntimeException unresolved) {
            return null;
        }
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
