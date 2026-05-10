package org.sjf4j.schema;

import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.util.Strings;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schema validation entry point for POJOs annotated with {@link ValidJsonSchema}.
 */
public final class SchemaValidator {
    private static final String SCHEMA_FILE_SUFFIX_1 = ".schema.json";
    private static final String SCHEMA_FILE_SUFFIX_2 = ".json";

    private final URI baseDirUri;
    private final SchemaRegistry registry;
    private final ValidationOptions options;
    private final Map<Class<?>, List<SchemaPlan>> pojoHierarchyPlansCache = new ConcurrentHashMap<>();

    /**
     * Creates validator with default configuration.
     */
    public SchemaValidator() {
        this(null, null, null);
    }

    /**
     * Creates a validator with base directory, options, and registry.
     */
    public SchemaValidator(String baseDir, ValidationOptions options, SchemaRegistry registry) {
        this.baseDirUri = _resolveBaseDir(baseDir);
        this.options = options == null ? ValidationOptions.FAILFAST_STRICT : options;
        this.registry = new SchemaRegistry().copyFrom(registry);
    }

    private URI _resolveBaseDir(String baseDir) {
        if (baseDir == null) return SchemaRegistry.DEFAULT_JSON_SCHEMA_DIR;
        if (!baseDir.endsWith("/")) baseDir += "/";
        return SchemaRegistry.DEFAULT_JSON_SCHEMA_DIR.resolve(baseDir);
    }

    /// Validate

    public void requireValid(Object pojo) {
        ValidationResult result = validate(pojo);
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    /**
     * Validates a POJO annotated with {@link ValidJsonSchema}.
     * <p>
     * Unannotated types are treated as valid and skipped. Resolved schemas are
     * cached per POJO class.
     */
    public ValidationResult validate(Object pojo) {
        if (pojo == null) return ValidationResult.SUCCESS;
        Class<?> pojoClazz = pojo.getClass();

        List<SchemaPlan> plans = _resolvePlans(pojoClazz);
        if (plans.isEmpty()) return ValidationResult.SUCCESS;

        ValidationResult previous = null;
        for (SchemaPlan plan : plans) {
            ValidationResult result = plan.validate(pojo, options);
            result.mergePrevious(previous);
            if (!result.isValid()) return result;
            previous = result;
        }
        return ValidationResult.SUCCESS;
    }

    /**
     * Preloads and compiles schemas by relative references.
     * <p>
     * References are resolved against validator base URI.
     */
    public SchemaPlan load(String ref) {
        Objects.requireNonNull(ref, "ref");
        URI refUri = baseDirUri.resolve(ref);
        return _registerByRef(refUri);
    }

//    /**
//     * Preloads and compiles all schema files in a directory.
//     */
//    public SchemaValidator preloadDirectory(String dir) {
//        URI dirUri = _resolveBaseUri(dir);
//        String scheme = dirUri.getScheme();
//        if (scheme == null) {
//            _preloadDir(Paths.get(dir));
//            return this;
//        }
//        if ("file".equalsIgnoreCase(scheme)) {
//            _preloadDir(Paths.get(dirUri));
//            return this;
//        }
//        if ("classpath".equalsIgnoreCase(scheme)) {
//            String path = dirUri.getPath();
//            if (path == null || path.isEmpty()) {
//                path = dirUri.getSchemeSpecificPart();
//            }
//            if (path != null && path.startsWith("/")) {
//                path = path.substring(1);
//            }
//            URL url = SchemaValidator.class.getClassLoader().getResource(path);
//            if (url == null) {
//                throw new SchemaException("Classpath directory not found: " + dirUri);
//            }
//            if (!"file".equalsIgnoreCase(url.getProtocol())) {
//                throw new SchemaException("Classpath directory scan not supported: " + url);
//            }
//            _preloadDir(Paths.get(url.getPath()));
//            return this;
//        }
//        throw new SchemaException("Unsupported preload directory uri: " + dirUri);
//    }
//
//    /**
//     * Recursively preloads schema files from a local directory path.
//     */
//    private void _preloadDir(Path dir) {
//        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
//            throw new SchemaException("Schema directory not found: " + dir);
//        }
//        try (Stream<Path> paths = Files.walk(dir)) {
//            paths.filter(Files::isRegularFile)
//                    .filter(p -> p.toString().endsWith(SCHEMA_FILE_SUFFIX_2))
//                    .forEach(p -> {
//                        JsonSchema schema = SchemaRegistry.loadSchemaFromLocalUri(p.toUri());
//                        _compileAndRegister(schema);
//                    });
//        } catch (IOException e) {
//            throw new SchemaException("Failed to preload schema directory: " + dir, e);
//        }
//    }


    private List<SchemaPlan> _resolvePlans(Class<?> pojoClazz) {
        List<SchemaPlan> plans = pojoHierarchyPlansCache.get(pojoClazz);
        if (plans != null) return plans;
        plans = new ArrayList<>();

        Class<?> parent = pojoClazz.getSuperclass();
        if (parent != null && parent != Object.class) {
            List<SchemaPlan> parentPlans = _resolvePlans(parent);
            plans.addAll(parentPlans);
        }

        ValidJsonSchema anno = pojoClazz.getDeclaredAnnotation(ValidJsonSchema.class);
        if (anno != null) {
            SchemaPlan plan = _resolvePlanFromAnno(pojoClazz, anno);
            plans.add(plan);
        }

        pojoHierarchyPlansCache.put(pojoClazz, plans);
        return plans;
    }


    /**
     * Loads schema for a POJO from annotation value/ref or naming convention.
     * <p>
     * Resolution order: inline schema text, explicit ref, then
     * {@code <simple-name>.json}, then {@code <snake-name>.json}.
     */
    private SchemaPlan _resolvePlanFromAnno(Class<?> clazz, ValidJsonSchema anno) {
        // From value
        String inline = anno.value();
        if (!inline.isEmpty()) {
            JsonSchema schema = JsonSchema.fromJson(inline);
            if (schema instanceof ObjectSchema) {
                ((ObjectSchema) schema).setRetrievalUri(URI.create("sjf4j:/" + clazz.getName() + "/"));
            }
            return registry.register(schema);
        }

        // From ref
        String ref = anno.ref().trim();
        if (!ref.isEmpty()) {
            URI refUri = baseDirUri.resolve(ref);
            SchemaPlan plan = _registerByRef(refUri);
            if (plan == null) throw new SchemaException("Failed to load schema by ref uri: " + refUri);
            return plan;
        }

        // From convention
        URI simpleNameUri = baseDirUri.resolve(clazz.getSimpleName() + SCHEMA_FILE_SUFFIX_2);
        SchemaPlan plan = _registerByRef(simpleNameUri);
        if (plan != null) return plan;

        String snakeName = Strings.toSnakeCase(clazz.getSimpleName());
        URI snakeNameUri = baseDirUri.resolve(snakeName + SCHEMA_FILE_SUFFIX_2);
        plan = _registerByRef(snakeNameUri);
        if (plan != null) return plan;

        throw new SchemaException("No schema found for @ValidJsonSchema on " + clazz.getName() +
                ": neither 'value' nor 'ref' is specified, and no schema file exists at '" +
                simpleNameUri + "' or '" + snakeNameUri + "'.");
    }


    private SchemaPlan _registerByRef(URI uri) {
        SchemaPlan plan = registry.resolve(uri);
        if (plan != null) return plan;

        ObjectSchema idSchema = SchemaRegistry.loadSchemaFromLocalUri(uri);
        if (idSchema == null) return null;
        registry.register(idSchema);
        return registry.resolve(uri);
    }


}
