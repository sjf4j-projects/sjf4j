package org.sjf4j.schema;

import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.path.JsonPointer;

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
        this.baseUri = resolveBaseUri(baseDir);
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

        List<JsonSchema> schemaChain = getOrLoadSchemaChain(pojoClazz);
        if (schemaChain.isEmpty()) return ValidationResult.VALID;

        for (JsonSchema schema : schemaChain) {
            ValidationResult result = schema.validate(pojo, options);
            if (!result.isValid()) return result;
        }
        return ValidationResult.VALID;
    }

    private List<JsonSchema> getOrLoadSchemaChain(Class<?> pojoClazz) {
        List<JsonSchema> chain = pojoSchemaChainMapping.get(pojoClazz);
        if (chain != null) return chain;
        List<JsonSchema> loaded = loadSchemaChain(pojoClazz);
        pojoSchemaChainMapping.put(pojoClazz, loaded);
        return loaded;
    }

    private List<JsonSchema> loadSchemaChain(Class<?> pojoClazz) {
        List<JsonSchema> chain = new ArrayList<>();

        Class<?> parent = pojoClazz.getSuperclass();
        if (parent != null && parent != Object.class) {
            chain.addAll(getOrLoadSchemaChain(parent));
        }

        ValidJsonSchema anno = pojoClazz.getDeclaredAnnotation(ValidJsonSchema.class);
        if (anno != null) {
            chain.add(loadPojoSchema(pojoClazz, anno));
        }

        return chain;
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
            compileAndRegister(schema);
        }
        return this;
    }

    /**
     * Preloads and compiles all schema files in a directory.
     */
    public SchemaValidator preloadDirectory(String dir) {
        URI dirUri = resolveBaseUri(dir);
        String scheme = dirUri.getScheme();
        if (scheme == null) {
            preloadDirectory(Paths.get(dir));
            return this;
        }
        if ("file".equalsIgnoreCase(scheme)) {
            preloadDirectory(Paths.get(dirUri));
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
            preloadDirectory(Paths.get(url.getPath()));
            return this;
        }
        throw new SchemaException("Unsupported preload directory uri: " + dirUri);
    }

    /**
     * Resolves user base directory against default schema base URI.
     */
    private URI resolveBaseUri(String baseDir) {
        if (baseDir == null) return DEFAULT_BASE_URI;
        if (!baseDir.endsWith("/")) baseDir += "/";
        return DEFAULT_BASE_URI.resolve(baseDir);
    }

    /**
     * Loads schema for a POJO from annotation value/ref or naming convention.
     * <p>
     * Resolution order: inline schema text, explicit ref, then
     * {@code <full-class-name>.schema.json}, then {@code <simple-name>.schema.json}.
     */
    private JsonSchema loadPojoSchema(Class<?> clazz, ValidJsonSchema anno) {
        // From value
        String inline = anno.value();
        if (!inline.isEmpty()) {
            JsonSchema schema = JsonSchema.fromJson(inline);
            return compileAndRegister(schema);
        }

        // From ref
        String ref = anno.ref().trim();
        if (!ref.isEmpty()) {
            URI uri = baseUri.resolve(ref);
            JsonSchema schema = SchemaStore.loadSchemaFromLocalUri(uri);
            compileAndRegister(schema);

            String refFragment = uri.getFragment();
            if (refFragment == null || refFragment.isEmpty()) {
                return schema;
            } else {
                return findInSchema(schema, refFragment);
            }
        }

        // From convention
        URI fullNameUri = baseUri.resolve(clazz.getName() + SCHEMA_FILE_SUFFIX);
        try {
            ObjectSchema schema = SchemaStore.loadSchemaFromLocalUri(fullNameUri);
            return compileAndRegister(schema);
        } catch (Exception e) {
            URI simpleNameUri = baseUri.resolve(clazz.getSimpleName() + SCHEMA_FILE_SUFFIX);
            try {
                ObjectSchema schema = SchemaStore.loadSchemaFromLocalUri(simpleNameUri);
                return compileAndRegister(schema);
            } catch (Exception e2) {
                throw new SchemaException("No 'value' or 'ref' specified for @ValidJsonSchema on " + clazz.getName() +
                        ", and neither '" + fullNameUri + "' nor '" + simpleNameUri + "' was found");
            }
        }
    }

    /**
     * Compiles a schema and registers it in the store.
     */
    private JsonSchema compileAndRegister(JsonSchema schema) {
        schema.compile(schemaStore);
        schemaStore.register(schema);
        return schema;
    }

    /**
     * Recursively preloads schema files from a local directory path.
     */
    private void preloadDirectory(Path dir) {
        try {
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                throw new SchemaException("Schema directory not found: " + dir);
            }
            Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(SCHEMA_FILE_SUFFIX))
                    .forEach(p -> {
                        JsonSchema schema = SchemaStore.loadSchemaFromLocalUri(p.toUri());
                        compileAndRegister(schema);
                    });
        } catch (IOException e) {
            throw new SchemaException("Failed to preload schema directory: " + dir, e);
        }
    }

    /**
     * Resolves an optional fragment to an anchor, dynamic anchor, or pointer.
     */
    private JsonSchema findInSchema(JsonSchema schema, String fragment) {
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
                    idSchema.getResolvedUri() + "'");
        }
        return found;
    }

}
