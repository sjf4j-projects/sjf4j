package org.sjf4j.schema;

import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;
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
 * directory. The framework does not write the input graph during validation.
 * Invoked getters, value bindings, and user extensions can have side effects.
 * Nested {@code @NodeValue} properties are encoded by their configured value
 * binding before evaluation.
 * Unlike {@link SchemaPlan#validate(Object)}, this facade uses fail-fast
 * validation and strict format assertions by default.
 */
public final class SchemaValidator {
    private static final String SCHEMA_FILE_SUFFIX_2 = ".json";

    private final SchemaLoader loader;
    private final SchemaRegistry registry;
    private final boolean strictFormat;
    private final Map<Class<?>, List<SchemaPlan>> pojoHierarchyPlansCache = new ConcurrentHashMap<>();

    /** Creates a validator using the default local schema directory and strict format checking. */
    public SchemaValidator() {
        this(new SchemaLoader.Local(SchemaRegistry.DEFAULT_JSON_SCHEMA_DIR), null, true);
    }

    /**
     * Creates a validator with its schema source, registry, and format mode.
     * The loader owns URI resolution and its base directory. The provided registry
     * is copied so lazy plan registration does not mutate caller-owned state.
     */
    public SchemaValidator(SchemaLoader loader, SchemaRegistry registry, boolean strictFormat) {
        this.loader = Asserts.notNull(loader, "loader");
        this.registry = SchemaRegistry.copyOf(registry);
        this.strictFormat = strictFormat;
    }

    /*
     * --------------------------------------------------------------
     * Validate
     * --------------------------------------------------------------
     */

    /**
     * Validates an annotated POJO and throws when its first resolved plan fails.
     * Null and a hierarchy with no {@link ValidJsonSchema} annotation are accepted.
     *
     * @throws ValidationException if an annotated schema rejects the instance
     * @throws SchemaException if a schema cannot be loaded or compiled
     */
    public void requireValid(Object pojo) {
        ValidationResult result = validate(pojo);
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    /**
     * Validates a POJO annotated with {@link ValidJsonSchema}.
     * <p>
     * Null and classes whose full superclass chain has no {@link ValidJsonSchema}
     * annotation are valid and skipped. Plans from annotated superclasses are
     * resolved and evaluated in parent-to-child order, including when the concrete
     * subclass is unannotated. Resolved plans are cached per concrete POJO class.
     * The framework does not write the POJO, but invoked getters, value bindings,
     * and user extensions can have side effects. Nested {@code @NodeValue}
     * properties are encoded before validation.
     *
     * @return a success result or the first failing plan's fail-fast result
     * @throws SchemaException if a schema cannot be loaded or compiled
     */
    public ValidationResult validate(Object pojo) {
        if (pojo == null) return ValidationResult.SUCCESS;
        Class<?> pojoClazz = pojo.getClass();

        List<SchemaPlan> plans = _resolvePlans(pojoClazz);
        if (plans.isEmpty()) return ValidationResult.SUCCESS;

        ValidationResult previous = null;
        for (SchemaPlan plan : plans) {
            ValidationResult result = plan.validate(pojo, true, strictFormat);
            result.mergePrevious(previous);
            if (!result.isValid()) return result;
            previous = result;
        }
        // Preserve successful annotation output from the resolved schema plans.
        return previous != null && !previous.getAnnotations().isEmpty()
                ? previous : ValidationResult.SUCCESS;
    }

    /**
     * Preloads and compiles schemas by references resolved by the loader.
     * <p>
     * References are resolved by the configured loader. Returns {@code null}
     * when no schema can be loaded from the resolved location. An unsupported
     * URI scheme or invalid schema raises {@link SchemaException}.
     */
    public SchemaPlan load(String ref) {
        Asserts.notNull(ref, "ref");
        URI refUri = loader.resolve(URI.create(ref));
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
            URI refUri = loader.resolve(URI.create(ref));
            SchemaPlan plan = _registerByRef(refUri);
            if (plan == null) throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                    "schema referenced by @ValidJsonSchema.ref on '" + clazz.getName() + "' was not found",
                    PathSegment.Root.INSTANCE, refUri));
            return plan;
        }

        // From convention
        URI simpleNameUri = loader.resolve(URI.create(clazz.getSimpleName() + SCHEMA_FILE_SUFFIX_2));
        SchemaPlan plan = _registerByRef(simpleNameUri);
        if (plan != null) return plan;

        String snakeName = Strings.toSnakeCase(clazz.getSimpleName());
        URI snakeNameUri = loader.resolve(URI.create(snakeName + SCHEMA_FILE_SUFFIX_2));
        plan = _registerByRef(snakeNameUri);
        if (plan != null) return plan;

        throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                "no schema found for @ValidJsonSchema on '" + clazz.getName() +
                        "'; expected annotation value/ref or file at '" + simpleNameUri +
                        "' or '" + snakeNameUri + "'",
                PathSegment.Root.INSTANCE, loader.resolve(URI.create(""))));
    }


    private SchemaPlan _registerByRef(URI uri) {
        SchemaPlan plan = uri.isAbsolute() ? registry.resolve(uri) : null;
        if (plan != null) return plan;

        JsonSchema schema = loader.load(uri);
        if (schema == null) return null;
        URI selectedUri = uri;
        if (!selectedUri.isAbsolute() && schema instanceof ObjectSchema) {
            URI loaded = ((ObjectSchema) schema).getRetrievalUri();
            if (loaded != null && loaded.isAbsolute()) {
                selectedUri = URI.create(loaded.toString() +
                        (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment()));
            }
        }
        if (selectedUri.isAbsolute()) {
            SchemaPlan existing = registry.resolve(selectedUri);
            if (existing != null) return existing;
        }
        SchemaPlan compiled = registry.register(SchemaLoader.documentUri(selectedUri), schema);
        SchemaPlan selected = selectedUri.isAbsolute() ? registry.resolve(selectedUri) : null;
        // Boolean documents are not URI-indexed by SchemaRegistry.
        return selected == null ? compiled : selected;
    }


}
