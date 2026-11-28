package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;

import java.net.URI;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for schema resources and compiled plans indexed by absolute URI.
 * <p>
 * Raw {@link SchemaResource} entries are kept so referenced root schemas can be
 * compiled lazily and independent of load order. {@link SchemaPlan} entries are
 * cached by absolute resource URI after compilation. Fragment lookup is limited
 * to subschemas recognized during planning; published plan graphs are unchanged
 * and are reused by subsequent planning operations in this registry.
 * Concurrent compilation against a fixed catalog is supported; callers must
 * not mutate indexed schema models or concurrently change resource definitions.
 * Multi-resource registration is not a transaction.
 * Lookup first checks local mappings and then the built-in global meta-schemas;
 * it does not fetch arbitrary remote resources.
 */
public class SchemaRegistry {

    private final Map<String, SchemaPlan> byIdPlans;
    private final Map<String, SchemaResource> byIdResources;
    private final SchemaDialect defaultDialect;
    private final PlanningOptions planningOptions;


    /**
     * Creates a registry with the supplied default dialect. Built-in global
     * meta-schemas remain visible as fallback resources.
     */
    public SchemaRegistry(SchemaDialect defaultDialect) {
        this(defaultDialect, PlanningOptions.DEFAULT);
    }

    /** Creates a registry with immutable planning options and default dialect. */
    public SchemaRegistry(PlanningOptions planningOptions) {
        this(SchemaDialect.DRAFT_2020_12, planningOptions);
    }

    /** Creates a registry with a fixed compilation environment. */
    public SchemaRegistry(SchemaDialect defaultDialect, PlanningOptions planningOptions) {
        this.byIdPlans = new ConcurrentHashMap<>();
        this.byIdResources = new ConcurrentHashMap<>();
        this.defaultDialect = Asserts.notNull(defaultDialect, "defaultDialect");
        this.planningOptions = Asserts.notNull(planningOptions, "planningOptions");
    }

    public PlanningOptions getPlanningOptions() {
        return planningOptions;
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
     * Resource metadata is independent of the schema model. Boolean schemas
     * with an explicit retrieval URI are indexed as ready-to-use plans.
     */
    public void index(JsonSchema schema) {
        index(null, schema);
    }

    /**
     * Indexes a root schema with an explicit retrieval URI.
     * <p>
     * The registry stores schema models only for root resources. This supports
     * delayed compilation and order-independent loading of referenced schemas.
     * The retrieval URI determines the base for a relative root {@code $id}.
     * Boolean resources are stored directly as plans when a retrieval URI is supplied.
     *
     * @throws SchemaException if no URI is available, the URI is not absolute,
     *                         or it conflicts with another resource
     */
    public void index(URI retrievalUri, JsonSchema schema) {
        index(new SchemaResource(retrievalUri, schema));
    }

    /**
     * Indexes a root schema with an explicit retrieval URI unless its canonical
     * URI or retrieval URI is already present in this registry.
     * <p>
     * Unlike {@link #index(URI, JsonSchema)}, duplicate target mappings are a
     * no-op instead of a conflict. The check is local to this registry; global
     * built-in schemas remain fallback-only and do not block local indexing.
     * Boolean resources with a retrieval URI are indexed as plans.
     */
    public void indexIfAbsent(URI retrievalUri, JsonSchema schema) {
        _index(new SchemaResource(retrievalUri, schema), true);
    }

    /** Indexes a loaded resource without modifying its schema model. */
    public void index(SchemaResource resource) {
        _index(resource, false);
    }

    /** Indexes a resource and returns its canonical URI, if any. */
    private URI _index(SchemaResource resource, boolean ifAbsent) {
        Asserts.notNull(resource, "resource");
        URI retrievalUri = resource.retrievalUri();
        if (resource.schema() instanceof BooleanSchema) {
            _indexBoolean(retrievalUri, (BooleanSchema) resource.schema(), ifAbsent);
            return retrievalUri;
        }
        URI canonicalUri = resource.canonicalUri();
        if (canonicalUri == null) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_URI,
                    "missing root schema uri: specify $id or a retrieval URI",
                    PathSegment.Root.INSTANCE, null));
        }
        if (ifAbsent && (_containsLocalResource(canonicalUri)
                || (retrievalUri != null && _containsLocalResource(retrievalUri)))) {
            return canonicalUri;
        }
        resource = _putResource(canonicalUri, resource);
        if (retrievalUri != null && !SchemaUtil.normalizeUriKey(retrievalUri)
                .equals(SchemaUtil.normalizeUriKey(canonicalUri))) {
            _putResource(retrievalUri, resource);
        }
        return canonicalUri;
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
        return register(new SchemaResource(retrievalUri, schema));
    }

    /** Indexes and compiles a loaded resource. */
    public SchemaPlan register(SchemaResource resource) {
        URI canonicalUri = _index(resource, false);
        if (canonicalUri == null) {
            // A boolean document without a URI is not registered.
            return SchemaPlan.of(null, PathSegment.Root.INSTANCE, (BooleanSchema) resource.schema());
        }
        // Resolve the retrieval alias when present so it is also cached, even
        // if the canonical resource was previously planned. Register a root,
        // not the fragment of a legacy $id.
        URI alias = resource.retrievalUri();
        return resolve((alias == null ? canonicalUri : alias).toString(), null);
    }

    /** Indexes an explicitly located boolean resource for direct and $ref resolution. */
    private void _indexBoolean(URI retrievalUri, BooleanSchema schema, boolean ifAbsent) {
        // An inline boolean schema with no retrieval URI remains an independent plan.
        if (retrievalUri == null) return;
        _checkUri(retrievalUri);
        String id = SchemaUtil.normalizeUriKey(retrievalUri);
        if (ifAbsent && _containsLocalResource(retrievalUri)) return;
        if (byIdResources.containsKey(id)) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                    "duplicate schema uri '" + id + "'", PathSegment.Root.INSTANCE, retrievalUri));
        }
        SchemaPlan plan = SchemaPlan.of(retrievalUri, PathSegment.Root.INSTANCE, schema);
        SchemaPlan existing = byIdPlans.putIfAbsent(id, plan);
        if (existing != null && (!existing.booleanSchema || existing.booleanValue != schema.booleanValue())) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                    "duplicate schema uri '" + id + "'", PathSegment.Root.INSTANCE, retrievalUri));
        }
    }

    /**
     * Validates and normalizes a resource URI for planning.
     *
     * @throws SchemaException on invalid URI shape
     */
    String checkPlanUri(URI uri) {
        _checkUri(uri);
        return SchemaUtil.normalizeUriKey(uri);
    }

    /**
     * Publishes complete graphs without overwriting an existing resource.
     * Independent compilations may race; publication is atomic per key, not a batch transaction.
     */
    void registerPlans(Map<String, SchemaPlan> plans) {
        for (Map.Entry<String, SchemaPlan> entry : plans.entrySet()) {
            _checkPlanConflict(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, SchemaPlan> entry : plans.entrySet()) {
            String id = entry.getKey();
            SchemaPlan plan = entry.getValue();
            SchemaPlan old = byIdPlans.putIfAbsent(id, plan);
            if (old != null && old != plan) {
                _checkPlanConflict(id, plan);
            }
        }
    }

    private void _checkPlanConflict(String id, SchemaPlan plan) {
        SchemaResource model = byIdResources.get(id);
        SchemaPlan old = byIdPlans.get(id);
        if (model != null && !model.sameResource(plan.resource)) {
            throw _planConflict(id);
        }
        if (old == null || old == plan) return;
        if (old.booleanSchema != plan.booleanSchema
                || (old.booleanSchema ? old.booleanValue != plan.booleanValue
                : !old.resource.sameResource(plan.resource))) {
            throw _planConflict(id);
        }
    }

    private SchemaException _planConflict(String id) {
        return new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                "duplicate schema uri '" + id + "'", PathSegment.Root.INSTANCE, URI.create(id)));
    }

    private SchemaResource _putResource(URI uri, SchemaResource resource) {
        _checkUri(uri);
        String id = SchemaUtil.normalizeUriKey(uri);
        SchemaPlan indexed = byIdPlans.get(id);
        if (indexed != null && (indexed.booleanSchema || !resource.sameResource(indexed.resource))) {
            throw _planConflict(id);
        }
        SchemaResource old = byIdResources.putIfAbsent(id, resource);
        if (old != null && !old.sameResource(resource)) {
            throw _planConflict(id);
        }
        return old == null ? resource : old;
    }

    private boolean _containsLocalResource(URI uri) {
        String id = SchemaUtil.normalizeUriKey(uri);
        return byIdResources.containsKey(id) || byIdPlans.containsKey(id);
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
     * Indexes missing resources from another registry, retaining all aliases
     * of an object resource as a group. Referenced object plans must be
     * recompiled in this registry so existing bindings cannot leak across
     * different resource contexts. Boolean plans are reference-independent.
     * Nested resource IDs become visible once their owning document is planned
     * and registered in this registry.
     *
     * <p>This is the registry-to-registry counterpart of
     * {@link #indexIfAbsent(URI, JsonSchema)}. Neither method overwrites a
     * locally registered resource.
     */
    void indexRegistry(SchemaRegistry source) {
        if (source == null || source == this) return;
        java.util.IdentityHashMap<SchemaResource, Set<String>> groups =
                new java.util.IdentityHashMap<SchemaResource, Set<String>>();
        for (Map.Entry<String, SchemaResource> entry : source.byIdResources.entrySet()) {
            Set<String> aliases = groups.get(entry.getValue());
            if (aliases == null) {
                aliases = new HashSet<String>();
                groups.put(entry.getValue(), aliases);
            }
            aliases.add(entry.getKey());
        }
        for (Map.Entry<SchemaResource, Set<String>> group : groups.entrySet()) {
            boolean conflicting = false;
            for (String key : group.getValue()) {
                if (byIdResources.containsKey(key) || byIdPlans.containsKey(key)) {
                    conflicting = true;
                    break;
                }
            }
            if (conflicting) continue;
            for (String key : group.getValue()) {
                byIdResources.putIfAbsent(key, group.getKey());
            }
        }
        for (Map.Entry<String, SchemaPlan> entry : source.byIdPlans.entrySet()) {
            if (entry.getValue().booleanSchema
                    && !byIdResources.containsKey(entry.getKey())) {
                byIdPlans.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
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
        return resolve(SchemaUtil.documentUri(uri).toString(), uri.getFragment());
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
        id = SchemaUtil.normalizeUriKey(id);
        SchemaPlan root = byIdPlans.get(id);
        if (root != null) return resolveFragmentOrThrow(root, fragment);
        return SchemaPlanner.resolve(this, id, fragment);
    }

    /**
     * Resolves a Draft 2020-12 dynamic reference for a known evaluation scope.
     * The reference URI must already be absolute; scope entries are ordered
     * from the outermost schema evaluation to the innermost. An empty scope
     * resolves to the initial (static) target. This method is also useful to
     * annotation processors which specialize schema references ahead of time.
     *
     * @return the resolved plan, or null if its resource is not registered
     * @throws SchemaException if the resource exists but its fragment is missing
     */
    public SchemaPlan resolveDynamicRef(URI refUri, List<SchemaPlan> scope) {
        Asserts.notNull(refUri, "refUri");
        Asserts.notNull(scope, "scope");
        SchemaPlan initial = resolve(refUri);
        if (initial == null) return null;
        String fragment = refUri.getFragment();
        String dynamicAnchor = fragment != null && !fragment.startsWith("/")
                && fragment.equals(initial.dynamicAnchor) ? fragment : null;
        return SchemaPlan.findDynamicTarget(initial, dynamicAnchor, scope.iterator());
    }

    /** Resolves a cached resource without compiling an indexed root. */
    SchemaPlan resolveBuilt(URI uri) {
        Asserts.notNull(uri, "uri");
        SchemaPlan root = byIdPlans.get(SchemaUtil.normalizeUriKey(uri));
        return root == null ? null : root.findFragment(uri.getFragment());
    }

    /** Looks up a normalized resource key locally, without compiling anything. */
    SchemaPlan findLocalPlan(String id) {
        return byIdPlans.get(id);
    }

    /** Looks up a normalized resource key locally, without a global fallback. */
    SchemaResource findLocalResource(String id) {
        return byIdResources.get(id);
    }

    /** Resolves an already planned fragment; never starts planning. */
    static SchemaPlan resolveFragmentOrThrow(SchemaPlan root, String fragment) {
        if (root == null) return null;
        SchemaPlan plan = root.findFragment(fragment);
        if (plan == null) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                    "cannot resolve schema fragment '#" + fragment + "'",
                    PathSegment.Root.INSTANCE, root.resourceUri));
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
        SchemaResource resource = byIdResources.get(id);
        if (resource != null) return (ObjectSchema) resource.schema();

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
        return byIdPlans.containsKey(id) || byIdResources.containsKey(id) ||
                (this != GLOBAL_SCHEMA_REGISTRY && GLOBAL_SCHEMA_REGISTRY.contains(id));
    }

    /**
     * Returns a new set of local and built-in resource URI keys. Canonical and
     * retrieval aliases may each contribute a key for the same schema model.
     */
    public Set<String> idSet() {
        Set<String> idSet = new HashSet<>(byIdPlans.keySet());
        idSet.addAll(byIdResources.keySet());
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

    /** Fixed local resource loader for the built-in meta-schema catalog. */
    private static final SchemaLoader.Local BUILTIN_SCHEMA_LOADER =
            new SchemaLoader.Local(SchemaLoader.DEFAULT_JSON_SCHEMA_DIR);

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
        URI uri = SchemaLoader.DEFAULT_JSON_SCHEMA_DIR.resolve(filePath);
        SchemaResource loaded = BUILTIN_SCHEMA_LOADER.load(URI.create(filePath));
        if (loaded == null) throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                "global schema not found", PathSegment.Root.INSTANCE, uri));
        if (!(loaded.schema() instanceof ObjectSchema)) throw new SchemaException(SchemaUtil.formatSchemaLine(
                SchemaUtil.Code.SCHEMA_LOAD, "global schema must be object-shaped",
                PathSegment.Root.INSTANCE, uri));
        GLOBAL_SCHEMA_REGISTRY.index(loaded);
        if (alias != null && !alias.isEmpty()) {
            GLOBAL_SCHEMA_REGISTRY._putResource(URI.create(alias), loaded);
        }
    }

}
