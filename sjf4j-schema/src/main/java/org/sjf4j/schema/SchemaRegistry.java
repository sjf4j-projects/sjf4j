package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;

import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import java.util.HashSet;
import java.util.Map;
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
    private final PlanningOptions planningOptions;
    // Imported nested resources retain their owning document and pointer, not foreign plans.
    private final Map<String, URI> byIdNestedResources = new ConcurrentHashMap<>();


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
        this.byIdSchemas = new ConcurrentHashMap<>();
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
     * For object schemas this may reuse or populate the schema's root
     * retrieval-URI metadata before the schema is compiled. Boolean schemas
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
     * The provided retrieval URI is written back to the root {@link ObjectSchema}
     * so later compilation can resolve a relative root {@code $id}.
     * Boolean resources are stored directly as plans when a retrieval URI is supplied.
     *
     * @throws SchemaException if no URI is available, the URI is not absolute,
     *                         or it conflicts with another resource
     */
    public void index(URI retrievalUri, JsonSchema schema) {
        Asserts.notNull(schema, "schema");
        if (schema instanceof BooleanSchema) {
            _indexBoolean(retrievalUri, (BooleanSchema) schema, false);
            return;
        }

        ObjectSchema os = (ObjectSchema) schema;
        if (retrievalUri != null) os.retrievalUri(retrievalUri);
        retrievalUri = os.retrievalUri();
        URI canonicalUri = retrievalUri != null
                ? SchemaUtil.resolveUri(retrievalUri, os.canonicalUri())
                : os.canonicalUri();
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
     * Boolean resources with a retrieval URI are indexed as plans.
     */
    public void indexIfAbsent(URI retrievalUri, JsonSchema schema) {
        Asserts.notNull(schema, "schema");
        if (schema instanceof BooleanSchema) {
            _indexBoolean(retrievalUri, (BooleanSchema) schema, true);
            return;
        }

        ObjectSchema os = (ObjectSchema) schema;
        if (retrievalUri != null) os.retrievalUri(retrievalUri);
        retrievalUri = os.retrievalUri();
        URI canonicalUri = retrievalUri != null
                ? SchemaUtil.resolveUri(retrievalUri, os.canonicalUri())
                : os.canonicalUri();
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
     * Recursively indexes JSON Schemas in a file-system directory, using file: retrieval
     * URIs for schemas without $id. Plans remain lazy until resolve() is invoked.
     *
     * @param directory a file: URI or working-directory-relative directory URI ending in '/'
     */
    public void indexDir(URI directory) {
        indexDir(directory, null);
    }

    /**
     * Indexes a local directory under a logical base URI.
     * For example, directory/file.json is indexed as baseUri.resolve("file.json").
     * The schema's own $id, when present, is still its canonical URI. This is
     * useful for preloaded remote resources without $id; no network I/O occurs.
     *
     * @param directory a local directory containing .json schemas
     * @param baseUri absolute directory URI ending in '/', or null to use local file: URIs
     */
    public void indexDir(URI directory, URI baseUri) {
        SchemaLoader.Local loader = new SchemaLoader.Local(directory);
        URI directoryUri = loader.baseUri();
        URI effectiveBaseUri = baseUri == null ? directoryUri : baseUri;
        if (!effectiveBaseUri.isAbsolute() || !effectiveBaseUri.toString().endsWith("/")
                || effectiveBaseUri.getRawQuery() != null || effectiveBaseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("schema registry base URI must be an absolute directory URI: "
                    + effectiveBaseUri);
        }
        if (!"file".equalsIgnoreCase(directoryUri.getScheme())) {
            throw new IllegalArgumentException("schema registry directory must use a file: URI: " + directoryUri);
        }
        Path root = Paths.get(directoryUri);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("schema registry directory not found: " + root);
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        URI uri = path.toUri();
                        JsonSchema schema = loader.load(uri);
                        if (schema == null) {
                            throw new IllegalArgumentException("schema registry resource not found: " + uri);
                        }
                        URI relative = root.toUri().relativize(uri);
                        indexIfAbsent(effectiveBaseUri.resolve(relative), schema);
                    });
        } catch (IOException e) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                    "failed to index schema registry directory", PathSegment.Root.INSTANCE, directoryUri), e);
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
            if (retrievalUri == null) {
                return SchemaPlan.of(null, PathSegment.Root.INSTANCE, (BooleanSchema) schema);
            }
            _indexBoolean(retrievalUri, (BooleanSchema) schema, false);
            return byIdPlans.get(SchemaUtil.normalizeUriKey(retrievalUri));
        }

        ObjectSchema os = (ObjectSchema) schema;
        if (retrievalUri != null) os.retrievalUri(retrievalUri);
        URI canonicalUri = os.retrievalUri() != null
                ? SchemaUtil.resolveUri(os.retrievalUri(), os.canonicalUri())
                : os.canonicalUri();
        if (canonicalUri == null) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_URI,
                    "missing root schema uri: specify $id or a retrieval URI",
                    PathSegment.Root.INSTANCE, null));
        }
        index(os);
        return SchemaPlanner.buildAndPutPlan(os, this);
    }


    /** Indexes an explicitly located boolean resource for direct and $ref resolution. */
    private void _indexBoolean(URI retrievalUri, BooleanSchema schema, boolean ifAbsent) {
        // An inline boolean schema with no retrieval URI remains an independent plan.
        if (retrievalUri == null) return;
        _checkUri(retrievalUri);
        String id = SchemaUtil.normalizeUriKey(retrievalUri);
        if (ifAbsent && (byIdPlans.containsKey(id) || byIdSchemas.containsKey(id))) return;
        if (byIdSchemas.containsKey(id)) {
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
     * Registers one URI mapping after validation checks.
     *
     * @throws SchemaException on invalid URI shape or duplicate/conflicting map
     */
    boolean putPlan(URI uri, SchemaPlan plan) {
        _checkUri(uri);
        String id = SchemaUtil.normalizeUriKey(uri);
        SchemaPlan old = byIdPlans.putIfAbsent(id, plan);
        if (old != null && old != plan) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                    "duplicate schema uri '" + id + "'", PathSegment.Root.INSTANCE, uri));
        }
        return old == null;
    }

    /** Removes only the plan published by a failed planning operation. */
    void removePlan(String id, SchemaPlan plan) {
        byIdPlans.remove(id, plan);
    }

    private void _putSchema(URI uri, ObjectSchema schema) {
        _checkUri(uri);
        String id = SchemaUtil.normalizeUriKey(uri);
        SchemaPlan indexed = byIdPlans.get(id);
        if (indexed != null && indexed.booleanSchema) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                    "duplicate schema uri '" + id + "'", PathSegment.Root.INSTANCE, uri));
        }
        ObjectSchema old = byIdSchemas.putIfAbsent(id, schema);
        if (old != null && old != schema) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                    "duplicate schema uri '" + id + "'", PathSegment.Root.INSTANCE, uri));
        }
    }

    private boolean _containsLocalResource(URI uri) {
        String id = SchemaUtil.normalizeUriKey(uri);
        return byIdSchemas.containsKey(id) || byIdPlans.containsKey(id) || byIdNestedResources.containsKey(id);
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
     *
     * <p>This is the registry-to-registry counterpart of
     * {@link #indexIfAbsent(URI, JsonSchema)}. Neither method overwrites a
     * locally registered resource.
     */
    void indexRegistry(SchemaRegistry source) {
        if (source == null || source == this) return;
        java.util.IdentityHashMap<ObjectSchema, Set<String>> groups =
                new java.util.IdentityHashMap<ObjectSchema, Set<String>>();
        for (Map.Entry<String, ObjectSchema> entry : source.byIdSchemas.entrySet()) {
            Set<String> aliases = groups.get(entry.getValue());
            if (aliases == null) {
                aliases = new HashSet<String>();
                groups.put(entry.getValue(), aliases);
            }
            aliases.add(entry.getKey());
        }
        for (Map.Entry<ObjectSchema, Set<String>> group : groups.entrySet()) {
            boolean conflicting = false;
            for (String key : group.getValue()) {
                if (byIdSchemas.containsKey(key) || byIdPlans.containsKey(key) || byIdNestedResources.containsKey(key)) {
                    conflicting = true;
                    break;
                }
            }
            if (conflicting) continue;
            for (String key : group.getValue()) {
                byIdSchemas.putIfAbsent(key, group.getKey());
            }
        }
        for (Map.Entry<String, SchemaPlan> entry : source.byIdPlans.entrySet()) {
            if (entry.getValue().booleanSchema
                    && !byIdSchemas.containsKey(entry.getKey()) && !byIdNestedResources.containsKey(entry.getKey())) {
                byIdPlans.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        // Retain resource locations, not plans, when importing nested $id mappings.
        for (Map.Entry<String, URI> entry : source.byIdNestedResources.entrySet()) {
            String rootId = SchemaUtil.normalizeUriKey(entry.getValue());
            if (byIdSchemas.get(rootId) != null
                    && byIdSchemas.get(rootId) == source.byIdSchemas.get(rootId)) {
                _putNestedResource(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, SchemaPlan> entry : source.byIdPlans.entrySet()) {
            String id = entry.getKey();
            SchemaPlan target = entry.getValue();
            if (target.booleanSchema || source.byIdSchemas.containsKey(id)) continue;
            SchemaPlan root = source.findOwningRoot(target);
            if (root == null || byIdSchemas.get(SchemaUtil.normalizeUriKey(root.resourceUri)) != root.schema) continue;
            for (Map.Entry<String, SchemaPlan> fragment : root.byPathPlans.entrySet()) {
                if (fragment.getValue() != target) continue;
                try {
                    URI location = URI.create(SchemaUtil.documentUri(root.resourceUri).toString()
                            + new URI(null, null, fragment.getKey()).toASCIIString());
                    _putNestedResource(id, location);
                } catch (URISyntaxException e) {
                    throw new SchemaException("Invalid nested resource pointer: " + fragment.getKey(), e);
                }
                break;
            }
        }
    }

    private void _putNestedResource(String id, URI location) {
        if (!byIdSchemas.containsKey(id) && !byIdPlans.containsKey(id)) {
            byIdNestedResources.putIfAbsent(id, location);
        }
    }

    /**
     * Imports all schema mappings from another registry.
     * The returned registry has independent maps but shares indexed schema
     * models and compiled plans under the same planning policy. Null creates a default registry.
     */
    public static SchemaRegistry copyOf(SchemaRegistry other) {
        if (other == null) return new SchemaRegistry();

        SchemaRegistry registry = new SchemaRegistry(other.defaultDialect, other.planningOptions);
        registry.byIdNestedResources.putAll(other.byIdNestedResources);
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
        if (fragment != null) id = SchemaUtil.documentUri(uri).toString();
        return SchemaPlanner.resolve(this, id, fragment);
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
        return SchemaPlanner.resolveBuilt(this, uri);
    }

    /** Looks up a normalized resource key locally, without compiling anything. */
    SchemaPlan findLocalPlan(String id) {
        return byIdPlans.get(id);
    }

    /** Looks up a normalized resource key locally, without a global fallback. */
    ObjectSchema findLocalSchema(String id) {
        return byIdSchemas.get(id);
    }

    /** Looks up the owning resource pointer of an imported nested schema. */
    URI findNestedResource(String id) {
        return byIdNestedResources.get(id);
    }

    /** Finds the indexed root owning a previously compiled nested resource. */
    private SchemaPlan findOwningRoot(SchemaPlan target) {
        for (String id : byIdSchemas.keySet()) {
            SchemaPlan root = byIdPlans.get(id);
            if (root != null && root.byPathPlans != null) {
                for (SchemaPlan fragment : root.byPathPlans.values()) {
                    if (fragment == target) return root;
                }
            }
        }
        return null;
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
        return byIdPlans.containsKey(id) || byIdSchemas.containsKey(id) || byIdNestedResources.containsKey(id) ||
                (this != GLOBAL_SCHEMA_REGISTRY && GLOBAL_SCHEMA_REGISTRY.contains(id));
    }

    /**
     * Returns a new set of local and built-in resource URI keys. Canonical and
     * retrieval aliases may each contribute a key for the same schema model.
     */
    public Set<String> idSet() {
        Set<String> idSet = new HashSet<>(byIdPlans.keySet());
        idSet.addAll(byIdSchemas.keySet());
        idSet.addAll(byIdNestedResources.keySet());
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

    /** Fixed local resource loader for the built-in meta-schema catalog. */
    private static final SchemaLoader.Local BUILTIN_SCHEMA_LOADER =
            new SchemaLoader.Local(DEFAULT_JSON_SCHEMA_DIR);

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
        JsonSchema loaded = BUILTIN_SCHEMA_LOADER.load(URI.create(filePath));
        if (loaded == null) throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                "global schema not found", PathSegment.Root.INSTANCE, uri));
        if (!(loaded instanceof ObjectSchema)) throw new SchemaException(SchemaUtil.formatSchemaLine(
                SchemaUtil.Code.SCHEMA_LOAD, "global schema must be object-shaped",
                PathSegment.Root.INSTANCE, uri));
        ObjectSchema schema = (ObjectSchema) loaded;
        GLOBAL_SCHEMA_REGISTRY.index(schema);
        if (alias != null && !alias.isEmpty()) {
            GLOBAL_SCHEMA_REGISTRY._putSchema(URI.create(alias), schema);
        }
    }

}
