package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for schema resources and compiled plans indexed by absolute URI.
 * <p>
 * Raw {@link ObjectSchema} entries are kept so referenced root schemas can be
 * compiled lazily and independent of load order. {@link SchemaPlan} entries are
 * cached by absolute resource URI after compilation. Fragment resolution stays
 * inside each compiled plan. The implementation is safe for concurrent access
 * at the map level, but root schema objects stored in the registry still carry
 * mutable retrieval-URI metadata.
 * Lookup first checks local mappings and then the built-in global meta-schemas;
 * it does not fetch arbitrary remote resources.
 */
public class SchemaRegistry {

    private final Map<String, SchemaPlan> byIdPlans;
    private final Map<String, ObjectSchema> byIdSchemas;
    private final SchemaDialect defaultDialect;
    private final SchemaLoader loader;



    /**
     * Creates a registry with the supplied default dialect. Built-in global
     * meta-schemas remain visible as fallback resources.
     */
    public SchemaRegistry(SchemaDialect defaultDialect) {
        this(defaultDialect, SchemaLoader.LOCAL);
    }

    /** Creates a registry with a caller-supplied schema loader. */
    public SchemaRegistry(SchemaLoader loader) {
        this(SchemaDialect.DRAFT_2020_12, loader);
    }

    public SchemaRegistry(SchemaDialect defaultDialect, SchemaLoader loader) {
        this.byIdPlans = new ConcurrentHashMap<>();
        this.byIdSchemas = new ConcurrentHashMap<>();
        this.defaultDialect = defaultDialect;
        this.loader = Asserts.notNull(loader, "loader");
    }

    /** Creates a registry defaulting to draft 2020-12. */
    public SchemaRegistry() {
        this(SchemaDialect.DRAFT_2020_12);
    }


    /** Returns the dialect used when a schema does not declare a known {@code $schema}. */
    public SchemaDialect getDefaultDialect() {
        return defaultDialect;
    }

    /**
     * Indexes a root schema by its declared canonical URI or retrieval URI.
     * <p>
     * For object schemas this may reuse or populate the schema's root
     * retrieval-URI metadata before the schema is compiled. A boolean schema
     * has no resource URI and is not indexed.
     */
    public void index(JsonSchema schema) {
        index(null, schema);
    }

    /**
     * Indexes a root schema with an explicit retrieval URI.
     * <p>
     * The registry stores schema models only for root resources. This supports
     * delayed compilation and order-independent loading of referenced schemas.
     * The provided retrieval URI is written back to the root {@link ObjectSchema}
     * so later compilation can resolve a relative root {@code $id}.
     *
     * @throws SchemaException if no URI is available, the URI is not absolute,
     *                         or it conflicts with another resource
     */
    public void index(URI retrievalUri, JsonSchema schema) {
        Asserts.notNull(schema, "schema");
        if (schema instanceof BooleanSchema) {
            return;
        }

        ObjectSchema os = (ObjectSchema) schema;
        if (retrievalUri != null) os.setRetrievalUri(retrievalUri);
        retrievalUri = os.getRetrievalUri();
        URI canonicalUri = retrievalUri != null
                ? SchemaUtil.resolveUri(retrievalUri, os.getCanonicalUri())
                : os.getCanonicalUri();
        if (canonicalUri == null) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_URI,
                    "missing root schema uri: specify $id or a retrieval URI",
                    PathSegment.Root.INSTANCE, null));
        }
        _putSchema(canonicalUri, os);

        if (retrievalUri != null && !SchemaUtil.normalizeUriKey(retrievalUri)
                .equals(SchemaUtil.normalizeUriKey(canonicalUri))) {
            _putSchema(retrievalUri, os);
        }
    }

    /**
     * Indexes a root schema with an explicit retrieval URI unless its canonical
     * URI or retrieval URI is already present in this registry.
     * <p>
     * Unlike {@link #index(URI, JsonSchema)}, duplicate target mappings are a
     * no-op instead of a conflict. The check is local to this registry; global
     * built-in schemas remain fallback-only and do not block local indexing.
     * A boolean schema is not indexed.
     */
    public void indexIfAbsent(URI retrievalUri, JsonSchema schema) {
        Asserts.notNull(schema, "schema");
        if (schema instanceof BooleanSchema) {
            return;
        }

        ObjectSchema os = (ObjectSchema) schema;
        if (retrievalUri != null) os.setRetrievalUri(retrievalUri);
        retrievalUri = os.getRetrievalUri();
        URI canonicalUri = retrievalUri != null
                ? SchemaUtil.resolveUri(retrievalUri, os.getCanonicalUri())
                : os.getCanonicalUri();
        if (canonicalUri == null) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_URI,
                    "missing root schema uri: specify $id or a retrieval URI",
                    PathSegment.Root.INSTANCE, null));
        }

        if (_containsLocalResource(canonicalUri) || (retrievalUri != null && _containsLocalResource(retrievalUri))) {
            return;
        }

        _putSchema(canonicalUri, os);
        if (retrievalUri != null && !SchemaUtil.normalizeUriKey(retrievalUri)
                .equals(SchemaUtil.normalizeUriKey(canonicalUri))) {
            _putSchema(retrievalUri, os);
        }
    }

    /**
     * Returns the compiled root plan for a schema, indexing it first if needed.
     * A boolean schema produces an independent plan without an indexed URI.
     */
    public SchemaPlan register(JsonSchema schema) {
        return register(null, schema);
    }

    /**
     * Indexes a root schema if needed and returns its compiled root plan.
     * A missing or relative root URI without a retrieval base is rejected.
     */
    public SchemaPlan register(URI retrievalUri, JsonSchema schema) {
        Asserts.notNull(schema, "schema");
        if (schema instanceof BooleanSchema) {
            return SchemaPlan.of(null, PathSegment.Root.INSTANCE, (BooleanSchema) schema);
        }

        ObjectSchema os = (ObjectSchema) schema;
        if (retrievalUri != null) os.setRetrievalUri(retrievalUri);
        URI canonicalUri = os.getRetrievalUri() != null
                ? SchemaUtil.resolveUri(os.getRetrievalUri(), os.getCanonicalUri())
                : os.getCanonicalUri();
        if (canonicalUri == null) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_URI,
                    "missing root schema uri: specify $id or a retrieval URI",
                    PathSegment.Root.INSTANCE, null));
        }
        index(os);
        return SchemaPlanner.buildAndPutPlan(os, this);
    }


    /**
     * Registers one URI mapping after validation checks.
     *
     * @throws SchemaException on invalid URI shape or duplicate/conflicting map
     */
    void putPlan(URI uri, SchemaPlan plan) {
        _checkUri(uri);
        String id = SchemaUtil.normalizeUriKey(uri);
        SchemaPlan old = byIdPlans.putIfAbsent(id, plan);
        if (old != null && old != plan) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                    "duplicate schema uri '" + id + "'", PathSegment.Root.INSTANCE, uri));
        }
    }

    private void _putSchema(URI uri, ObjectSchema schema) {
        _checkUri(uri);
        String id = SchemaUtil.normalizeUriKey(uri);
        ObjectSchema old = byIdSchemas.putIfAbsent(id, schema);
        if (old != null && old != schema) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                    "duplicate schema uri '" + id + "'", PathSegment.Root.INSTANCE, uri));
        }
    }

    private boolean _containsLocalResource(URI uri) {
        String id = SchemaUtil.normalizeUriKey(uri);
        return byIdSchemas.containsKey(id) || byIdPlans.containsKey(id);
    }

    private void _checkUri(URI uri) {
        Asserts.notNull(uri, "uri");
        if (uri.toString().isEmpty()) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_URI,
                    "schema uri must not be empty", PathSegment.Root.INSTANCE, uri));
        }
        if (!uri.isAbsolute()) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_URI,
                    "schema uri must be absolute", PathSegment.Root.INSTANCE, uri));
        }
    }

    /**
     * Imports all schema mappings from another registry.
     * <p>
     * The returned registry has independent lookup maps but shares the indexed
     * schema models and compiled plans with {@code other}. A null source creates
     * a registry with the default dialect.
     */
    public static SchemaRegistry copyOf(SchemaRegistry other) {
        if (other == null) return new SchemaRegistry();

        SchemaRegistry registry = new SchemaRegistry(other.defaultDialect, other.loader);
        for (Map.Entry<String, SchemaPlan> entry : other.byIdPlans.entrySet()) {
            SchemaPlan plan = entry.getValue();
            SchemaPlan old = registry.byIdPlans.putIfAbsent(entry.getKey(), plan);
            if (old != null && old != plan) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                        "duplicate schema uri '" + entry.getKey() + "'", PathSegment.Root.INSTANCE,
                        URI.create(entry.getKey())));
            }
        }
        for (Map.Entry<String, ObjectSchema> entry : other.byIdSchemas.entrySet()) {
            ObjectSchema schema = entry.getValue();
            ObjectSchema old = registry.byIdSchemas.putIfAbsent(entry.getKey(), schema);
            if (old != null && old != schema) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                        "duplicate schema uri '" + entry.getKey() + "'", PathSegment.Root.INSTANCE,
                        URI.create(entry.getKey())));
            }
        }
        return registry;
    }

    /**
     * Explicitly loads a document through the configured SchemaLoader if it is
     * not already registered, then resolves its URI (including the fragment).
     * Unlike resolve(URI), this method may perform I/O.
     */
    public SchemaPlan load(URI uri) {
        Asserts.notNull(uri, "uri");
        SchemaPlan plan = resolve(uri);
        if (plan != null) return plan;
        URI document = SchemaLoader.documentUri(uri);
        JsonSchema schema = loader.load(document);
        if (schema == null) return null;
        SchemaPlan root = register(document, schema);
        if (schema instanceof BooleanSchema) {
            if (uri.getFragment() != null && !uri.getFragment().isEmpty()) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                        "boolean schema does not define a fragment",
                        PathSegment.Root.INSTANCE, uri));
            }
            return root;
        }
        return resolve(uri);
    }

    /**
     * Resolves schema by absolute URI (canonical or alias).
     * <p>
     * The URI fragment, when present, is resolved within the compiled target
     * resource rather than as a separate registry key. Missing resources return
     * {@code null}; a missing fragment in a known resource throws
     * {@link SchemaException}.
     */
    public SchemaPlan resolve(URI uri) {
        Asserts.notNull(uri, "uri");
        String id = uri.toString();
        String fragment = uri.getFragment();
        if (fragment != null) id = SchemaUtil.stripFragment(id);
        SchemaPlan plan = _resolveResource(id, true);
        return _resolveFragmentOrThrow(plan, fragment);
    }

    /**
     * Resolves a resource by normalized absolute URI plus optional fragment.
     * <p>
     * If no compiled plan exists yet but a root schema model is indexed for the
     * same resource URI, compilation is triggered lazily at this point.
     * Missing resources return {@code null}; unresolved fragments throw
     * {@link SchemaException}.
     */
    public SchemaPlan resolve(String id, String fragment) {
        Asserts.notNull(id, "id");
        SchemaPlan plan = _resolveResource(id, true);
        return _resolveFragmentOrThrow(plan, fragment);
    }

    SchemaPlan resolveBuilt(URI uri) {
        Asserts.notNull(uri, "uri");
        String id = uri.toString();
        String fragment = uri.getFragment();
        if (fragment != null) id = SchemaUtil.stripFragment(id);
        SchemaPlan plan = _resolveResource(id, false);
        return _resolveFragmentOrThrow(plan, fragment);
    }

    /**
     * Resolves only the schema resource part of a URI.
     * <p>
     * Fragments are intentionally ignored here so callers that need better
     * diagnostics can distinguish a missing resource from a missing fragment.
     * Missing resources return {@code null}; indexed resources may still be
     * compiled lazily.
     */
    SchemaPlan resolveResource(URI uri) {
        Asserts.notNull(uri, "uri");
        String id = uri.getFragment() == null ? uri.toString() : SchemaUtil.stripFragment(uri.toString());
        return _resolveResource(id, true);
    }

    /**
     * Resolves a fragment within an already resolved schema resource.
     * <p>
     * This method does not throw for missing fragments. The public resolve API
     * keeps the historical exception behavior, while internal callers can use
     * {@code null} to produce context-specific messages for {@code $ref} and
     * {@code $dynamicRef}.
     */
    SchemaPlan resolveFragment(SchemaPlan rootPlan, String fragment) {
        if (rootPlan == null || fragment == null || fragment.isEmpty()) return rootPlan;
        if (rootPlan.byAnchorPlans == null || rootPlan.byPathPlans == null) return null;
        SchemaPlan plan = rootPlan.byAnchorPlans.get(fragment);
        if (plan == null) {
            plan = rootPlan.byPathPlans.get(fragment);
        }
        if (plan == null) {
            plan = SchemaPlanner.lazyBuildPlanByPath(rootPlan, fragment, this);
        }
        return plan;
    }

    /**
     * Resolves a resource key without fragment handling.
     * <p>
     * Local registry entries win; the global built-in registry is consulted only
     * as fallback. This preserves custom registry overrides and avoids remote or
     * filesystem loading beyond schemas explicitly indexed in the registry.
     */
    private SchemaPlan _resolveResource(String id, boolean allowBuild) {
        id = SchemaUtil.normalizeUriKey(id);
        SchemaPlan plan = byIdPlans.get(id);
        if (plan != null) return plan;

        if (allowBuild) {
            ObjectSchema schema = byIdSchemas.get(id);
            if (schema != null) {
                return SchemaPlanner.buildAndPutPlan(schema, this);
            }
        }

        if (this != GLOBAL_SCHEMA_REGISTRY) {
            return GLOBAL_SCHEMA_REGISTRY._resolveResource(id, allowBuild);
        }
        return null;
    }

    /**
     * Adapter used by public resolve methods to preserve their existing
     * fragment-missing exception behavior.
     */
    private SchemaPlan _resolveFragmentOrThrow(SchemaPlan rootPlan, String fragment) {
        if (rootPlan == null) return null;
        SchemaPlan plan = resolveFragment(rootPlan, fragment);
        if (plan == null) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                    "cannot resolve schema fragment '#" + fragment + "'",
                    PathSegment.Root.INSTANCE, rootPlan.schemaUri));
        }

        return plan;
    }

    /**
     * Resolves a root schema model by absolute resource URI.
     * <p>
     * This is primarily used internally for lazy compilation and vocabulary
     * lookup, not for fragment resolution.
     */
    ObjectSchema resolveSchema(URI uri) {
        Asserts.notNull(uri, "uri");
        String id = SchemaUtil.normalizeUriKey(uri);
        ObjectSchema schema = byIdSchemas.get(id);
        if (schema != null) return schema;

        if (this != GLOBAL_SCHEMA_REGISTRY) {
            return GLOBAL_SCHEMA_REGISTRY.resolveSchema(uri);
        }
        return null;
    }


    /** Returns the count of distinct visible resource URI keys, including built-ins. */
    public int size() {
        return idSet().size();
    }


    /** Returns whether an absolute resource URI key is visible locally or globally. */
    public boolean contains(String id) {
        return _contains(SchemaUtil.normalizeUriKey(id));
    }

    /**
     * Returns true if the absolute resource URI exists in this registry.
     * <p>
     * Fragments are ignored because fragment lookup happens inside the compiled
     * resource plan. The global built-in registry is consulted as a fallback.
     */
    public boolean contains(URI uri) {
        return _contains(SchemaUtil.normalizeUriKey(uri));
    }

    private boolean _contains(String id) {
        return byIdPlans.containsKey(id) || byIdSchemas.containsKey(id) ||
                (this != GLOBAL_SCHEMA_REGISTRY && GLOBAL_SCHEMA_REGISTRY.contains(id));
    }

    /**
     * Returns a new set of local and built-in resource URI keys. Canonical and
     * retrieval aliases may each contribute a key for the same schema model.
     */
    public Set<String> idSet() {
        Set<String> idSet = new HashSet<>(byIdPlans.keySet());
        idSet.addAll(byIdSchemas.keySet());
        if (this != GLOBAL_SCHEMA_REGISTRY) {
            idSet.addAll(GLOBAL_SCHEMA_REGISTRY.idSet());
        }
        return idSet;
    }

    /*
     * --------------------------------------------------------------
     * Global
     * --------------------------------------------------------------
     */
    /** Global registry of built-in meta-schema resources. */
    public static final SchemaRegistry GLOBAL_SCHEMA_REGISTRY = new SchemaRegistry();
    /** Default classpath directory for annotation-based schema discovery. */
    public static final URI DEFAULT_JSON_SCHEMA_DIR = URI.create("classpath:/json-schemas/");

    /**
     * Resolves a schema from the global built-in registry.
     * <p>
     * Built-in schema models are indexed at class initialization time, while
     * compiled plans are still created lazily on first resolution.
     */
    public static SchemaPlan globalResolve(URI uri) {
        return GLOBAL_SCHEMA_REGISTRY.resolve(uri);
    }

    /** Resolves a built-in resource URI and optional fragment, or returns null if absent. */
    public static SchemaPlan globalResolve(String id, String fragment) {
        return GLOBAL_SCHEMA_REGISTRY.resolve(id, fragment);
    }

    static {
        _indexGlobalSchemaByPath("draft07/schema.json", "https://json-schema.org/draft-07/schema");

        _indexGlobalSchemaByPath("draft2019-09/meta/core.json", null);
        _indexGlobalSchemaByPath("draft2019-09/meta/applicator.json", null);
        _indexGlobalSchemaByPath("draft2019-09/meta/validation.json", null);
        _indexGlobalSchemaByPath("draft2019-09/meta/meta-data.json", null);
        _indexGlobalSchemaByPath("draft2019-09/meta/format.json", null);
        _indexGlobalSchemaByPath("draft2019-09/meta/content.json", null);
        _indexGlobalSchemaByPath("draft2019-09/schema.json", null);

        _indexGlobalSchemaByPath("draft2020-12/meta/core.json", null);
        _indexGlobalSchemaByPath("draft2020-12/meta/applicator.json", null);
        _indexGlobalSchemaByPath("draft2020-12/meta/validation.json", null);
        _indexGlobalSchemaByPath("draft2020-12/meta/meta-data.json", null);
        _indexGlobalSchemaByPath("draft2020-12/meta/format-annotation.json", null);
        _indexGlobalSchemaByPath("draft2020-12/meta/format-assertion.json", null);
        _indexGlobalSchemaByPath("draft2020-12/meta/unevaluated.json", null);
        _indexGlobalSchemaByPath("draft2020-12/meta/content.json", null);
        _indexGlobalSchemaByPath("draft2020-12/schema.json", null);
    }

    private static void _indexGlobalSchemaByPath(String filePath, String alias) {
        URI uri = DEFAULT_JSON_SCHEMA_DIR.resolve(filePath);
        ObjectSchema schema = SchemaUtil.loadSchemaFromLocalUri(uri);
        if (schema == null) throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                "global schema not found", PathSegment.Root.INSTANCE, uri));
        GLOBAL_SCHEMA_REGISTRY.index(schema);
        if (alias != null && !alias.isEmpty()) {
            GLOBAL_SCHEMA_REGISTRY._putSchema(URI.create(alias), schema);
        }
    }

}
