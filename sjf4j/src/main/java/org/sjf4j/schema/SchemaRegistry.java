package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;

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
 */
public class SchemaRegistry {

    private final Map<String, SchemaPlan> byIdPlans;
    private final Map<String, ObjectSchema> byIdSchemas;
    private final SchemaDialect defaultDialect;



    /**
     * Creates an empty schema registry.
     */
    public SchemaRegistry(SchemaDialect defaultDialect) {
        this.byIdPlans = new ConcurrentHashMap<>();
        this.byIdSchemas = new ConcurrentHashMap<>();
        this.defaultDialect = defaultDialect;
    }

    public SchemaRegistry() {
        this(SchemaDialect.DRAFT_2020_12);
    }


    public SchemaDialect getDefaultDialect() {
        return defaultDialect;
    }

    /**
     * Indexes a root schema by its declared canonical URI or retrieval URI.
     * <p>
     * For object schemas this may reuse or populate the schema's root
     * retrieval-URI metadata before the schema is compiled.
     */
    public SchemaRegistry index(JsonSchema schema) {
        return index(null, schema);
    }

    /**
     * Indexes a root schema with an explicit retrieval URI.
     * <p>
     * The registry stores schema models only for root resources. This supports
     * delayed compilation and order-independent loading of referenced schemas.
     * The provided retrieval URI is written back to the root {@link ObjectSchema}
     * so later compilation can resolve a relative root {@code $id}.
     */
    public SchemaRegistry index(URI retrievalUri, JsonSchema schema) {
        Objects.requireNonNull(schema, "schema");
        if (schema instanceof BooleanSchema) {
            return this;
        }

        ObjectSchema os = (ObjectSchema) schema;
        if (retrievalUri != null) os.setRetrievalUri(retrievalUri);
        retrievalUri = os.getRetrievalUri();
        URI canonicalUri = retrievalUri != null
                ? SchemaUtil.resolveUri(retrievalUri, os.getCanonicalUri())
                : os.getCanonicalUri();
        if (canonicalUri == null) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_URI,
                    "missing root schema uri: no $id or retrievalUri", null, (String) null));
        }
        _putSchema(canonicalUri, os);

        if (retrievalUri != null && !SchemaUtil.normalizeUriKey(retrievalUri)
                .equals(SchemaUtil.normalizeUriKey(canonicalUri))) {
            _putSchema(retrievalUri, os);
        }
        return this;
    }

    /**
     * Returns the compiled root plan for a schema, indexing it first if needed.
     */
    public SchemaPlan register(JsonSchema schema) {
        return register(null, schema);
    }

    /**
     * Indexes a root schema if needed and returns its compiled root plan.
     */
    public SchemaPlan register(URI retrievalUri, JsonSchema schema) {
        Objects.requireNonNull(schema, "schema");
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
                    "missing root schema uri: no $id or retrievalUri", null, (String) null));
        }
        index(os);
        return SchemaPlanner.buildPlan(os, this);
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
                    "duplicate schema uri '" + id + "'", null, id));
        }
    }

    private void _putSchema(URI uri, ObjectSchema schema) {
        _checkUri(uri);
        String id = SchemaUtil.normalizeUriKey(uri);
        ObjectSchema old = byIdSchemas.putIfAbsent(id, schema);
        if (old != null && old != schema) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                    "duplicate schema uri '" + id + "'", null, id));
        }
    }

    private void _checkUri(URI uri) {
        Objects.requireNonNull(uri, "uri");
        if (uri.toString().isEmpty()) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_URI,
                    "schema uri must not be empty", null, (String) null));
        }
        if (!uri.isAbsolute()) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_URI,
                    "schema uri must be absolute", null, uri.toString()));
        }
    }

    /**
     * Imports all schema mappings from another registry.
     * <p>
     * Existing mappings must resolve to the same canonical schema resource.
     */
    public static SchemaRegistry copyOf(SchemaRegistry other) {
        if (other == null) return new SchemaRegistry();

        SchemaRegistry registry = new SchemaRegistry(other.defaultDialect);
        for (Map.Entry<String, SchemaPlan> entry : other.byIdPlans.entrySet()) {
            SchemaPlan plan = entry.getValue();
            SchemaPlan old = registry.byIdPlans.putIfAbsent(entry.getKey(), plan);
            if (old != null && old != plan) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                        "duplicate schema uri '" + entry.getKey() + "'", null, entry.getKey()));
            }
        }
        for (Map.Entry<String, ObjectSchema> entry : other.byIdSchemas.entrySet()) {
            ObjectSchema schema = entry.getValue();
            ObjectSchema old = registry.byIdSchemas.putIfAbsent(entry.getKey(), schema);
            if (old != null && old != schema) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                        "duplicate schema uri '" + entry.getKey() + "'", null, entry.getKey()));
            }
        }
        return registry;
    }

    /**
     * Resolves schema by absolute URI (canonical or alias).
     * <p>
     * The URI fragment, when present, is resolved within the compiled target
     * resource rather than as a separate registry key.
     */
    public SchemaPlan resolve(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String id = uri.toString();
        String fragment = uri.getFragment();
        if (fragment != null) id = SchemaUtil.stripFragment(id);
        return _resolvePlan(id, fragment, true);
    }

    /**
     * Resolves a resource by normalized absolute URI plus optional fragment.
     * <p>
     * If no compiled plan exists yet but a root schema model is indexed for the
     * same resource URI, compilation is triggered lazily at this point.
     */
    public SchemaPlan resolve(String id, String fragment) {
        Objects.requireNonNull(id, "id");
        return _resolvePlan(id, fragment, true);
    }

    SchemaPlan resolveBuilt(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String id = uri.toString();
        String fragment = uri.getFragment();
        if (fragment != null) id = SchemaUtil.stripFragment(id);
        return _resolvePlan(id, fragment, false);
    }

    private SchemaPlan _resolvePlan(String id, String fragment, boolean allowBuild) {
        id = SchemaUtil.normalizeUriKey(id);
        SchemaPlan plan = byIdPlans.get(id);
        if (plan != null) return _resolveByFragment(plan, fragment);

        if (allowBuild) {
            ObjectSchema schema = byIdSchemas.get(id);
            if (schema != null) {
                plan = SchemaPlanner.buildPlan(schema, this);
                return _resolveByFragment(plan, fragment);
            }
        }

        if (this != GLOBAL_SCHEMA_REGISTRY) {
            return GLOBAL_SCHEMA_REGISTRY._resolvePlan(id, fragment, allowBuild);
        }
        return null;
    }

    private SchemaPlan _resolveByFragment(SchemaPlan rootPlan, String fragment) {
        if (fragment == null || fragment.isEmpty()) return rootPlan;
        SchemaPlan plan = rootPlan.getByFragment(fragment);
        if (plan == null) {
            plan = SchemaPlanner.lazyBuildPlanByPath(rootPlan, fragment, this);
        }
        if (plan == null) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                    "cannot resolve schema fragment '#" + fragment + "'",
                    null, rootPlan.schemaUri));
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
        Objects.requireNonNull(uri, "uri");
        String id = SchemaUtil.normalizeUriKey(uri);
        ObjectSchema schema = byIdSchemas.get(id);
        if (schema != null) return schema;

        if (this != GLOBAL_SCHEMA_REGISTRY) {
            return GLOBAL_SCHEMA_REGISTRY.resolveSchema(uri);
        }
        return null;
    }


    public int size() {
        return idSet().size();
    }


    public boolean contains(String id) {
        id = SchemaUtil.normalizeUriKey(id);
        return byIdPlans.containsKey(id) || byIdSchemas.containsKey(id) ||
                (this != GLOBAL_SCHEMA_REGISTRY && GLOBAL_SCHEMA_REGISTRY.contains(id));
    }

    /**
     * Returns true if the absolute resource URI exists in this registry.
     * <p>
     * Fragments are ignored because fragment lookup happens inside the compiled
     * resource plan. The global built-in registry is consulted as a fallback.
     */
    public boolean contains(URI uri) {
        Objects.requireNonNull(uri, "uri");
        return contains(SchemaUtil.normalizeUriKey(uri));
    }

    /**
     * Returns all registered absolute resource URIs visible from this registry.
     */
    public Set<String> idSet() {
        Set<String> idSet = new HashSet<>(byIdPlans.keySet());
        idSet.addAll(byIdSchemas.keySet());
        if (this != GLOBAL_SCHEMA_REGISTRY) {
            idSet.addAll(GLOBAL_SCHEMA_REGISTRY.idSet());
        }
        return idSet;
    }

    /// Global
    public static final SchemaRegistry GLOBAL_SCHEMA_REGISTRY = new SchemaRegistry();
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
                "global schema not found", null, uri.toString()));
        GLOBAL_SCHEMA_REGISTRY.index(schema);
        if (alias != null && !alias.isEmpty()) {
            GLOBAL_SCHEMA_REGISTRY._putSchema(URI.create(alias), schema);
        }
    }

}
