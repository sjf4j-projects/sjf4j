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
 * <p>
 * The validator resolves one or more compiled plans for a class hierarchy and
 * reuses them from an internal cache. Resolution may come from inline schema
 * text, explicit refs, or filename conventions under the configured base
 * directory.
 */
public final class SchemaValidator {
    private static final String SCHEMA_FILE_SUFFIX_2 = ".json";

    private final URI baseDirUri;
    private final SchemaRegistry registry;
    private final ValidationOptions options;
    private final Map<Class<?>, List<SchemaPlan>> pojoHierarchyPlansCache = new ConcurrentHashMap<>();

    /**
     * Creates validator with default configuration.
     * <p>
     * Uses classpath schema base directory, {@link ValidationOptions#FAILFAST_STRICT},
     * and an empty copied registry.
     */
    public SchemaValidator() {
        this(null, null, null);
    }

    /**
     * Creates a validator with base directory, options, and registry.
     * <p>
     * The provided registry is copied so later lazy compilation does not mutate
     * caller-owned registry state.
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
     * Unannotated types are treated as valid and skipped. When a class extends
     * another annotated class, plans are resolved for the full superclass chain
     * and evaluated in parent-to-child order. Resolved plans are cached per
     * concrete POJO class.
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
     * References are resolved against validator base URI. Returns {@code null}
     * when no schema can be loaded from the resolved location.
     */
    public SchemaPlan load(String ref) {
        Objects.requireNonNull(ref, "ref");
        URI refUri = baseDirUri.resolve(ref);
        return _registerByRef(refUri);
    }


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
     * {@code <simple-name>.json}, then {@code <snake-name>.json}. Inline
     * schemas receive a synthetic retrieval URI so relative root {@code $id}
     * values still resolve consistently.
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
            if (plan == null) throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                    "failed to load schema by ref uri", null, refUri.toString()));
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

        throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                "no schema found for @ValidJsonSchema on '" + clazz.getName() +
                        "'; expected annotation value/ref or file at '" + simpleNameUri +
                        "' or '" + snakeNameUri + "'",
                null, (String) null));
    }


    private SchemaPlan _registerByRef(URI uri) {
        SchemaPlan plan = registry.resolve(uri);
        if (plan != null) return plan;

        ObjectSchema idSchema = SchemaUtil.loadSchemaFromLocalUri(uri);
        if (idSchema == null) return null;
        registry.register(idSchema);
        return registry.resolve(uri);
    }


}
