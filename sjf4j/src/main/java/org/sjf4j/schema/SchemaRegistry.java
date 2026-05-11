package org.sjf4j.schema;

import org.sjf4j.Sjf4j;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.path.PathSegment;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for compiled schema resources indexed by absolute URI.
 * <p>
 * Supports canonical URI and alias mappings pointing to the same ObjectSchema.
 */
public class SchemaRegistry {

    private final Map<String, SchemaPlan> byIdPlans;
    private final Map<String, ObjectSchema> byIdSchemas;

    /**
     * Creates an empty schema registry.
     */
    public SchemaRegistry() {
        byIdPlans = new ConcurrentHashMap<>();
        byIdSchemas = new ConcurrentHashMap<>();
    }

    public SchemaRegistry index(JsonSchema schema) {
        return index(null, schema);
    }

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
            throw new SchemaException("Missing uri: no $id or retrievalUri");
        }
        _putSchema(canonicalUri, os);

        if (retrievalUri != null && !SchemaUtil.normalizeUriKey(retrievalUri)
                .equals(SchemaUtil.normalizeUriKey(canonicalUri))) {
            _putSchema(retrievalUri, os);
        }
        return this;
    }

    public SchemaPlan register(JsonSchema schema) {
        return register(null, schema);
    }

    public SchemaPlan register(URI retrievalUri, JsonSchema schema) {
        Objects.requireNonNull(schema, "schema");
        if (schema instanceof BooleanSchema) {
            return SchemaPlan.of(PathSegment.Root.INSTANCE, (BooleanSchema) schema);
        }

        ObjectSchema os = (ObjectSchema) schema;
        if (retrievalUri != null) os.setRetrievalUri(retrievalUri);
        URI canonicalUri = os.getRetrievalUri() != null
                ? SchemaUtil.resolveUri(os.getRetrievalUri(), os.getCanonicalUri())
                : os.getCanonicalUri();
        if (canonicalUri == null) {
            throw new SchemaException("Missing uri: no $id or retrievalUri");
        }
        index(os);
        return SchemaPlanner.createPlan(os, this);
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
            throw new SchemaException("Duplicate schema uri: '" + id + "'");
        }
    }

    private void _putSchema(URI uri, ObjectSchema schema) {
        _checkUri(uri);
        String id = SchemaUtil.normalizeUriKey(uri);
        ObjectSchema old = byIdSchemas.putIfAbsent(id, schema);
        if (old != null && old != schema) {
            throw new SchemaException("Duplicate schema uri: '" + id + "'");
        }
    }

    private void _checkUri(URI uri) {
        if (uri.toString().isEmpty())
            throw new SchemaException("Strict URI check failed: uri should not be empty");
        if (!uri.isAbsolute())
            throw new SchemaException("Strict URI check failed: uri must be absolute (not relative): " + uri);
//        if (uri.getFragment() != null)
//            throw new SchemaException("Strict URI check failed: uri should not have fragment: " + uri);
    }

    /**
     * Imports all schema mappings from another registry.
     * <p>
     * Existing mappings must resolve to the same canonical schema resource.
     */
    public SchemaRegistry copyFrom(SchemaRegistry other) {
        if (other != null) {
            for (Map.Entry<String, SchemaPlan> entry : other.byIdPlans.entrySet()) {
                SchemaPlan plan = entry.getValue();
                SchemaPlan old = byIdPlans.putIfAbsent(entry.getKey(), plan);
                if (old != null && old != plan) {
                    throw new SchemaException("Duplicate schema uri: " + entry.getKey());
                }
            }
            for (Map.Entry<String, ObjectSchema> entry : other.byIdSchemas.entrySet()) {
                ObjectSchema schema = entry.getValue();
                ObjectSchema old = byIdSchemas.putIfAbsent(entry.getKey(), schema);
                if (old != null && old != schema) {
                    throw new SchemaException("Duplicate schema uri: " + entry.getKey());
                }
            }
        }
        return this;
    }

    /**
     * Resolves schema by absolute URI (canonical or alias).
     */
    public SchemaPlan resolve(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String mixed = uri.toString();
        String fragment = uri.getFragment();
        if (fragment == null) return resolve(mixed, null);
        return resolve(SchemaUtil.stripFragment(mixed), fragment);
    }


    public SchemaPlan resolve(String id, String fragment) {
        id = SchemaUtil.normalizeUriKey(id);
        SchemaPlan plan = _resolvePlan(id, fragment);
        if (plan != null) return plan;

        ObjectSchema schema = byIdSchemas.get(id);
        if (schema != null) {
            plan = SchemaPlanner.createPlan(schema, this);
            if (fragment == null || fragment.isEmpty()) return plan;
            return plan.getByFragment(fragment);
        }

        return null;
    }

    SchemaPlan resolvePlan(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String mixed = uri.toString();
        String fragment = uri.getFragment();
        if (fragment == null) return _resolvePlan(mixed, null);
        return _resolvePlan(SchemaUtil.stripFragment(mixed), fragment);
    }

    ObjectSchema resolveSchema(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String id = SchemaUtil.normalizeUriKey(uri);
        ObjectSchema schema = byIdSchemas.get(id);
        if (schema != null) return schema;

        if (this != GLOBAL_SCHEMA_REGISTRY) {
            _loadGlobalSchemaByPath();
            return GLOBAL_SCHEMA_REGISTRY.resolveSchema(uri);
        }
        return null;
    }

    private SchemaPlan _resolvePlan(String id, String fragment) {
        Objects.requireNonNull(id, "id");
        id = SchemaUtil.normalizeUriKey(id);
        SchemaPlan plan = byIdPlans.get(id);
        if (plan != null) {
            if (fragment == null || fragment.isEmpty()) return plan;
            return plan.getByFragment(fragment);
        }

        if (this != GLOBAL_SCHEMA_REGISTRY) {
            _loadGlobalSchemaByPath();
            return GLOBAL_SCHEMA_REGISTRY._resolvePlan(id, fragment);
        }
        return null;
    }

    public int size() {
        return idSet().size();
    }

    /**
     * Returns true if the URI exists in this registry.
     */
    public boolean contains(String uri) {
        String id = SchemaUtil.normalizeUriKey(uri);
        return byIdPlans.containsKey(id) || byIdSchemas.containsKey(id) ||
                (this != GLOBAL_SCHEMA_REGISTRY && GLOBAL_SCHEMA_REGISTRY.contains(id));
    }

    /**
     * Returns all registered URIs.
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
    public static final URI DEFAULT_JSON_SCHEMA_DIR = URI.create("classpath:///json-schemas/");

    /**
     * Resolves a schema from the global built-in registry.
     */
    public static SchemaPlan globalResolve(URI uri) {
        return GLOBAL_SCHEMA_REGISTRY.resolve(uri);
    }

    public static SchemaPlan globalResolve(String id, String fragment) {
        return GLOBAL_SCHEMA_REGISTRY.resolve(id, fragment);
    }

    private static void _loadGlobalSchemaByPath() {
        if (GLOBAL_SCHEMA_REGISTRY.size() == 0) {
            _registerGlobalSchemaByPath("draft2020-12/meta/core.json");
            _registerGlobalSchemaByPath("draft2020-12/meta/applicator.json");
            _registerGlobalSchemaByPath("draft2020-12/meta/validation.json");
            _registerGlobalSchemaByPath("draft2020-12/meta/meta-data.json");
            _registerGlobalSchemaByPath("draft2020-12/meta/format-annotation.json");
            _registerGlobalSchemaByPath("draft2020-12/meta/format-assertion.json");
            _registerGlobalSchemaByPath("draft2020-12/meta/unevaluated.json");
            _registerGlobalSchemaByPath("draft2020-12/meta/content.json");
            _registerGlobalSchemaByPath("draft2020-12/schema.json");
        }
    }

    private static void _registerGlobalSchemaByPath(String filePath) {
        URI uri = DEFAULT_JSON_SCHEMA_DIR.resolve(filePath);
        ObjectSchema schema = loadSchemaFromLocalUri(uri);
        if (schema == null) throw new SchemaException("Not found global schema: " + uri);
        GLOBAL_SCHEMA_REGISTRY.register(schema);
    }

    /**
     * Loads a schema from local URI.
     * <p>
     * Supported schemes: {@code file}, {@code classpath}. Returns {@code null}
     * when the target does not exist.
     */
    public static ObjectSchema loadSchemaFromLocalUri(URI uri) {
        Objects.requireNonNull(uri, "uri");
        ObjectSchema schema;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            schema = _loadSchemaFromFile(uri.getPath());
        } else if ("classpath".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = uri.getSchemeSpecificPart();
            }
            schema = _loadSchemaFromResource(path);
        } else {
            throw new SchemaException("Unsupported local schema uri: " + uri);
        }
        if (schema == null) return null;
        schema.setRetrievalUri(uri);
        return schema;
    }

    /**
     * Loads a schema from a file path. Returns {@code null} when the file does
     * not exist.
     */
    private static ObjectSchema _loadSchemaFromFile(String path) {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            return Sjf4j.global().fromJson(in, ObjectSchema.class);
        } catch (NoSuchFileException e) {
            return null;
        } catch (Exception e) {
            throw new SchemaException("Failed to load schema from file: " + path, e);
        }
    }

    /**
     * Loads a schema from a classpath resource path. Returns {@code null} when
     * the resource does not exist.
     */
    private static ObjectSchema _loadSchemaFromResource(String path) {
        if (path.startsWith("/")) path = path.substring(1);
        try (InputStream in = SchemaRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            return Sjf4j.global().fromJson(in, ObjectSchema.class);
        } catch (Exception e) {
            throw new SchemaException("Failed to load schema from resource: " + path, e);
        }
    }

}
