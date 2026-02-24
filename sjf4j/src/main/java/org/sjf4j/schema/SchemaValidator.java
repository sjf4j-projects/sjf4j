package org.sjf4j.schema;

import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.path.JsonPointer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class SchemaValidator {
    private static final URI DEFAULT_BASE_URI = URI.create("classpath:/json-schemas/");
    private static final String SCHEMA_FILE_SUFFIX = ".schema.json";

//    private final String baseDir;
    private final URI baseUri;
    private final SchemaStore schemaStore;
    private final ValidationOptions defaultOptions;
    private final Map<Class<?>, JsonSchema> pojoSchemaMapping = new ConcurrentHashMap<>();

    public SchemaValidator() {
        this(null, null, null);
    }

    public SchemaValidator(String baseDir, ValidationOptions options, SchemaStore store) {
        this.baseUri = baseDir == null ? DEFAULT_BASE_URI : DEFAULT_BASE_URI.resolve(baseDir);
        this.defaultOptions = options == null ? ValidationOptions.DEFAULT : options;
        this.schemaStore = store == null ? new SchemaStore() : store;
    }

    public ValidationResult validate(Object pojo) {
        if (pojo == null) return ValidationResult.VALID;
        Class<?> pojoClazz = pojo.getClass();

        ValidJsonSchema anno = pojoClazz.getAnnotation(ValidJsonSchema.class);
        if (anno == null) return ValidationResult.VALID;

        JsonSchema schema = pojoSchemaMapping.computeIfAbsent(pojoClazz,
                (k) -> loadPojoSchema(k, anno));

        ValidationOptions options = optionsFrom(anno);
        return schema.validate(pojo, options);
    }

    private ValidationOptions optionsFrom(ValidJsonSchema anno) {
        boolean failFast = anno.failFast() || defaultOptions.isFailFast();
        boolean strictFormat = anno.strictFormat() || defaultOptions.isStrictFormat();
        if (failFast == defaultOptions.isFailFast() && strictFormat == defaultOptions.isStrictFormat()) {
            return defaultOptions;
        }
        return new ValidationOptions.Builder()
                .failFast(failFast)
                .strictFormats(strictFormat)
                .build();
    }

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

    private JsonSchema compileAndRegister(JsonSchema schema) {
        schema.compile(schemaStore);
        schemaStore.register(schema);
        return schema;
    }

    private JsonSchema findInSchema(JsonSchema schema, String fragment) {
        if (fragment == null || fragment.isEmpty()) return schema;
        if (!(schema instanceof ObjectSchema)) {
            throw new IllegalArgumentException("Required ObjectSchema, but was " + schema.getClass());
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
