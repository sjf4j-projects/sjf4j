package org.sjf4j.schema;

import org.sjf4j.Sjf4j;
import org.sjf4j.exception.SchemaException;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
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

    private final Map<URI, ObjectSchema> mixedUriSchemas = new ConcurrentHashMap<>();

    /**
     * Creates an empty schema registry.
     */
    public SchemaRegistry() {}

    /**
     * Creates a registry by importing another registry.
     */
    public SchemaRegistry(SchemaRegistry other) {
        importFrom(other);
    }

    /**
     * Creates a registry and registers initial schemas.
     */
    public SchemaRegistry(JsonSchema... initialSchemas) {
        for (JsonSchema schema : initialSchemas) {
            register(schema);
        }
    }

    /**
     * Registers a schema using its canonical resource URI.
     *
     * @return true when schema was registrable (object schema with non-empty URI)
     */
    public boolean register(JsonSchema schema) {
        if (!(schema instanceof ObjectSchema)) return false;
        ObjectSchema os = (ObjectSchema) schema;
        URI uri = os.getCanonicalUri();
        if (uri != null && !uri.toString().isEmpty()) {
            _register(uri, os);
            return true;
        }
        return false;
    }

    /**
     * Registers a schema with explicit URI alias.
     * <p>
     * If alias differs from canonical URI, both mappings are inserted.
     */
    public boolean register(URI uri, JsonSchema schema) {
        if (!(schema instanceof ObjectSchema)) return false;
        ObjectSchema os = (ObjectSchema) schema;
        URI canonicalUri = os.getCanonicalUri();
        if (uri != null) {
            if (canonicalUri != null && !uri.equals(canonicalUri)) {
                _register(uri, os);
                _register(canonicalUri, os);
            } else {
                _register(uri, os);
            }
            return true;
        }
        if (canonicalUri != null) {
            _register(canonicalUri, os);
            return true;
        }
        return false;
    }

    /**
     * Registers one URI mapping after validation checks.
     *
     * @throws SchemaException on invalid URI shape or duplicate/conflicting map
     */
    private void _register(URI uri, JsonSchema schema) {
        Objects.requireNonNull(uri);
        Objects.requireNonNull(schema);
        if (uri.toString().isEmpty())
            throw new SchemaException("Invalid schema: uri should not be empty");
        if (!uri.isAbsolute())
            throw new SchemaException("Invalid schema: uri must be absolute (not relative): " + uri);
        if (uri.getFragment() != null)
            throw new SchemaException("Invalid schema: uri should not have fragment: " + uri);
        if (!(schema instanceof ObjectSchema))
            throw new SchemaException("Invalid schema: schema must be object (not true or false)");
        ObjectSchema os = (ObjectSchema) schema;
        URI canonicalUri = os.getCanonicalUri();
        ObjectSchema oldOs = mixedUriSchemas.putIfAbsent(uri, os);
        if (oldOs != null) {
            if (uri.equals(canonicalUri)) {
                throw new SchemaException("Duplicate schema uri: " + canonicalUri);
            } else {
                throw new SchemaException("Alias conflict, schema uri: " + canonicalUri + ", alias uri: " + uri);
            }
        }
    }

    /**
     * Imports all schema mappings from another registry.
     * <p>
     * Existing mappings must resolve to the same canonical schema resource.
     */
    public void importFrom(SchemaRegistry other) {
        if (other != null) {
            for (Map.Entry<URI, ObjectSchema> entry : other.mixedUriSchemas.entrySet()) {
                ObjectSchema os = entry.getValue();
                ObjectSchema oldOs = mixedUriSchemas.putIfAbsent(entry.getKey(), os);
                if (oldOs != null && !oldOs.getCanonicalUri().equals(os.getCanonicalUri())) {
                    throw new SchemaException("Duplicate schema uri: " + os.getCanonicalUri());
                }
            }
        }
    }

    /**
     * Resolves schema by absolute URI (canonical or alias).
     */
    public ObjectSchema resolve(URI uri) {
        return mixedUriSchemas.get(uri);
    }

    /**
     * Returns true if the URI exists in this registry.
     */
    public boolean contains(URI uri) {
        return mixedUriSchemas.containsKey(uri);
    }

    /**
     * Returns all registered URIs.
     */
    public Set<URI> uris() {
        return mixedUriSchemas.keySet();
    }

    private static final SchemaRegistry GLOBAL_REGISTRY = new SchemaRegistry();

    /**
     * Resolves a schema from the global built-in registry.
     */
    public static ObjectSchema globalResolve(URI uri) {
        return GLOBAL_REGISTRY.resolve(uri);
    }

    private static final String JSON_SCHEMAS_DIR = "json-schemas/";
    static {
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/core.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/applicator.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/validation.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/meta-data.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/format-annotation.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/unevaluated.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/content.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/schema.json");
    }

    /**
     * Loads and registers one built-in global schema resource.
     */
    private static void registerGlobalSchema(String resourcePath) {
        ObjectSchema schema = loadSchemaFromResource(resourcePath);
        if (schema == null) throw new SchemaException("Not found global schema: " + resourcePath);
        if (!GLOBAL_REGISTRY.register(schema)) {
            throw new SchemaException("Failed to register global schema: " + resourcePath);
        }
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
            schema = loadSchemaFromFile(uri.getPath());
        } else if ("classpath".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = uri.getSchemeSpecificPart();
            }
            schema = loadSchemaFromResource(path);
        } else {
            throw new SchemaException("Unsupported local schema uri: " + uri);
        }
        if (schema == null) return null;
        schema.setRetrievalUri(CompileUtil.withoutFragment(uri));
        return schema;
    }

    /**
     * Loads a schema from a file path. Returns {@code null} when the file does
     * not exist.
     */
    public static ObjectSchema loadSchemaFromFile(String filePath) {
        try (InputStream in = Files.newInputStream(Paths.get(filePath))) {
            return Sjf4j.global().fromJson(in, ObjectSchema.class);
        } catch (NoSuchFileException e) {
            return null;
        } catch (Exception e) {
            throw new SchemaException("Failed to load schema file: " + filePath, e);
        }
    }

    /**
     * Loads a schema from a classpath resource path. Returns {@code null} when
     * the resource does not exist.
     */
    public static ObjectSchema loadSchemaFromResource(String resourcePath) {
        if (resourcePath.startsWith("/")) resourcePath = resourcePath.substring(1);
        try (InputStream in = SchemaRegistry.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            return Sjf4j.global().fromJson(in, ObjectSchema.class);
        } catch (Exception e) {
            throw new SchemaException("Failed to load schema resource: " + resourcePath, e);
        }
    }
}
