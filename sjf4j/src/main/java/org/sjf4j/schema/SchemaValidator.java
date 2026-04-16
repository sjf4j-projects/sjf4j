package org.sjf4j.schema;

import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.util.Strings;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Schema validation entry point for POJOs annotated with {@link ValidJsonSchema}.
 */
public final class SchemaValidator {
    private static final URI DEFAULT_BASE_URI = URI.create("classpath:///json-schemas/");
    private static final String SCHEMA_FILE_SUFFIX = ".json";

//    private final String baseDir;
    private final URI baseUri;
    private final SchemaStore schemaStore;
    private final ValidationOptions options;
    private final Map<Class<?>, List<JsonSchema>> pojoSchemaChainMapping = new ConcurrentHashMap<>();

    /**
     * Creates validator with default configuration.
     */
    public SchemaValidator() {
        this(null, null, null);
    }

    public SchemaValidator(String baseDir) {
        this(baseDir, null, null);
    }

    /**
     * Creates a validator with base directory, options, and store.
     */
    public SchemaValidator(String baseDir, ValidationOptions options, SchemaStore store) {
        this.baseUri = _resolveBaseUri(baseDir);
        this.options = options == null ? ValidationOptions.FAILFAST_STRICT : options;
        this.schemaStore = store == null ? new SchemaStore() : store;
    }

    /**
     * Validates a POJO annotated with {@link ValidJsonSchema}.
     * <p>
     * Unannotated types are treated as valid and skipped. Resolved schemas are
     * cached per POJO class.
     */
    public ValidationResult validate(Object pojo) {
        if (pojo == null) return ValidationResult.VALID;
        Class<?> pojoClazz = pojo.getClass();

        List<JsonSchema> schemaChain = _getOrLoadSchemaChain(pojoClazz);
        if (schemaChain.isEmpty()) return ValidationResult.VALID;

        for (JsonSchema schema : schemaChain) {
            ValidationResult result = schema.validate(pojo, options);
            if (!result.isValid()) return result;
        }
        return ValidationResult.VALID;
    }

    public void requireValid(Object pojo) {
        ValidationResult result = validate(pojo);
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    /**
     * Preloads and compiles schemas by relative references.
     * <p>
     * References are resolved against validator base URI.
     */
    public SchemaValidator preload(String... refs) {
        if (refs == null) return this;
        for (String ref : refs) {
            if (ref == null || ref.trim().isEmpty()) continue;
            URI uri = baseUri.resolve(ref);
            JsonSchema schema = SchemaStore.loadSchemaFromLocalUri(uri);
            if (schema == null) {
                throw new SchemaException("Schema preload target not found: " + uri);
            }
            _compileAndRegister(schema);
        }
        return this;
    }

    /**
     * Preloads and compiles all schema files in a directory.
     */
    public SchemaValidator preloadDirectory(String dir) {
        URI dirUri = _resolveBaseUri(dir);
        String scheme = dirUri.getScheme();
        if (scheme == null) {
            _preloadDir(Paths.get(dir));
            return this;
        }
        if ("file".equalsIgnoreCase(scheme)) {
            _preloadDir(Paths.get(dirUri));
            return this;
        }
        if ("classpath".equalsIgnoreCase(scheme)) {
            String path = dirUri.getPath();
            if (path == null || path.isEmpty()) {
                path = dirUri.getSchemeSpecificPart();
            }
            if (path != null && path.startsWith("/")) {
                path = path.substring(1);
            }
            URL url = SchemaValidator.class.getClassLoader().getResource(path);
            if (url == null) {
                throw new SchemaException("Classpath directory not found: " + dirUri);
            }
            if (!"file".equalsIgnoreCase(url.getProtocol())) {
                throw new SchemaException("Classpath directory scan not supported: " + url);
            }
            _preloadDir(Paths.get(url.getPath()));
            return this;
        }
        throw new SchemaException("Unsupported preload directory uri: " + dirUri);
    }

    /**
     * Resolves user base directory against default schema base URI.
     */
    private URI _resolveBaseUri(String baseDir) {
        if (baseDir == null) return DEFAULT_BASE_URI;
        if (!baseDir.endsWith("/")) baseDir += "/";
        return DEFAULT_BASE_URI.resolve(baseDir);
    }

    /**
     * Loads schema for a POJO from annotation value/ref or naming convention.
     * <p>
     * Resolution order: inline schema text, explicit ref, then
     * {@code <simple-name>.json}, then {@code <snake-name>.json}.
     */
    private JsonSchema _loadPojoSchema(Class<?> clazz, ValidJsonSchema anno) {
        // From value
        String inline = anno.value();
        if (!inline.isEmpty()) {
            JsonSchema schema = JsonSchema.fromJson(inline);
            return _compileAndRegister(schema);
        }

        // From ref
        String ref = anno.ref().trim();
        if (!ref.isEmpty()) {
            URI uri = baseUri.resolve(ref);
            JsonSchema schema = SchemaStore.loadSchemaFromLocalUri(uri);
            if (schema == null) {
                throw new SchemaException("Schema ref not found: " + uri);
            }
            _compileAndRegister(schema);

            String refFragment = uri.getFragment();
            if (refFragment == null || refFragment.isEmpty()) {
                return schema;
            } else {
                return _findInSchema(schema, refFragment);
            }
        }

        // From convention
        URI simpleNameUri = baseUri.resolve(clazz.getSimpleName() + SCHEMA_FILE_SUFFIX);
        String snakeName = Strings.toSnakeCase(clazz.getSimpleName());
        URI snakeNameUri = baseUri.resolve(snakeName + SCHEMA_FILE_SUFFIX);
        ObjectSchema schema = SchemaStore.loadSchemaFromLocalUri(simpleNameUri);
        if (schema != null) {
            return _compileAndRegister(schema);
        }

        if (snakeNameUri.equals(simpleNameUri)) {
            throw new SchemaException("No schema found for @ValidJsonSchema on " + clazz.getName() +
                    ": neither 'value' nor 'ref' is specified, and no schema file exists at '" +
                    simpleNameUri + "'.");
        }

        schema = SchemaStore.loadSchemaFromLocalUri(snakeNameUri);
        if (schema != null) {
            return _compileAndRegister(schema);
        }
        throw new SchemaException("No schema found for @ValidJsonSchema on " + clazz.getName() +
                ": neither 'value' nor 'ref' is specified, and no schema file exists at '" +
                simpleNameUri + "' or '" + snakeNameUri + "'.");
    }

    /**
     * Compiles a schema and registers it in the store.
     */
    private JsonSchema _compileAndRegister(JsonSchema schema) {
        schema.compile(schemaStore);
        if (schema instanceof ObjectSchema) {
            ObjectSchema objectSchema = (ObjectSchema) schema;
            URI resolvedUri = objectSchema.getCanonicalUri();
            if (resolvedUri != null && !resolvedUri.toString().isEmpty() && schemaStore.contains(resolvedUri)) {
                return schemaStore.resolve(resolvedUri);
            }
        }
        schemaStore.register(schema);
        return schema;
    }

    private List<JsonSchema> _getOrLoadSchemaChain(Class<?> pojoClazz) {
        List<JsonSchema> chain = pojoSchemaChainMapping.get(pojoClazz);
        if (chain != null) return chain;
        List<JsonSchema> loaded = _loadSchemaChain(pojoClazz);
        pojoSchemaChainMapping.put(pojoClazz, loaded);
        return loaded;
    }

    private List<JsonSchema> _loadSchemaChain(Class<?> pojoClazz) {
        List<JsonSchema> chain = new ArrayList<>();

        Class<?> parent = pojoClazz.getSuperclass();
        if (parent != null && parent != Object.class) {
            chain.addAll(_getOrLoadSchemaChain(parent));
        }

        ValidJsonSchema anno = pojoClazz.getDeclaredAnnotation(ValidJsonSchema.class);
        if (anno != null) {
            chain.add(_loadPojoSchema(pojoClazz, anno));
        }

        return chain;
    }

    /**
     * Recursively preloads schema files from a local directory path.
     */
    private void _preloadDir(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new SchemaException("Schema directory not found: " + dir);
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(SCHEMA_FILE_SUFFIX))
                .forEach(p -> {
                    JsonSchema schema = SchemaStore.loadSchemaFromLocalUri(p.toUri());
                    _compileAndRegister(schema);
                });
        } catch (IOException e) {
            throw new SchemaException("Failed to preload schema directory: " + dir, e);
        }
    }

    /**
     * Resolves an optional fragment to an anchor, dynamic anchor, or pointer.
     */
    private JsonSchema _findInSchema(JsonSchema schema, String fragment) {
        if (fragment == null || fragment.isEmpty()) return schema;
        if (!(schema instanceof ObjectSchema)) {
            throw new SchemaException("Invalid schema fragment lookup target: required ObjectSchema, but was " +
                    schema.getClass().getName());
        }
        ObjectSchema idSchema = (ObjectSchema) schema;
        JsonSchema found = idSchema.getSchemaByAnchor(fragment);
        if (found == null) {
            found = idSchema.getSchemaByDynamicAnchor(fragment);
        }
        if (found == null && fragment.startsWith("/")) {
            found = idSchema.getSchemaByPath(JsonPointer.compile(fragment));
        }
        if (found == null) {
            throw new SchemaException("No sub-schema found for fragment '" + fragment + "' in schema '" +
                    idSchema.getCanonicalUri() + "'");
        }
        return found;
    }

}
