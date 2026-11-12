package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class ExternalSchemasAnnotationTest {
    private static final String ROOT = "{\"properties\":{\"value\":{\"$ref\":\"https://example.test/shared\"}}}";

    @ValidJsonSchema(
            value = ROOT,
            externalSchemas = "{\"https://example.test/shared\":{\"type\":\"string\"}}")
    public static class StringBean {
        public Object value;
        StringBean(Object value) { this.value = value; }
    }

    @ValidJsonSchema(
            value = ROOT,
            externalSchemas = "{\"https://example.test/shared\":{\"type\":\"integer\"}}")
    public static class IntegerBean {
        public Object value;
        IntegerBean(Object value) { this.value = value; }
    }

    @ValidJsonSchema(
            value = ROOT,
            externalSchemasRef = "classpath:/schema-external/mappings.json")
    public static class FileBean {
        public Object value;
        FileBean(Object value) { this.value = value; }
    }

    @ValidJsonSchema(
            value = ROOT,
            externalSchemas = "{\"https://example.test/shared\":{\"type\":\"string\"}}",
            externalSchemasRef = "classpath:/schema-external/mappings.json")
    public static class PriorityBean {
        public Object value;
        PriorityBean(Object value) { this.value = value; }
    }

    @ValidJsonSchema(
            value = "{\"$ref\":\"https://example.test/base\"}",
            externalSchemas = "{\"https://example.test/shared\":{\"type\":\"string\"}}")
    public static class PrecompiledBaseBean {
        public Object value;
        PrecompiledBaseBean(Object value) { this.value = value; }
    }

    @Test
    void customRegistryPlansAreRecompiledWithBindingSpecificReferences() {
        SchemaRegistry catalog = new SchemaRegistry();
        URI shared = URI.create("https://example.test/shared");
        URI base = URI.create("https://example.test/base");
        catalog.index(shared, JsonSchema.fromJson("{\"type\":\"integer\"}"));
        catalog.index(base, JsonSchema.fromJson(
                "{\"properties\":{\"value\":{\"$ref\":\"https://example.test/shared\"}}}"));

        // Compile the original plan before the annotated binding is resolved.
        SchemaPlan oldPlan = catalog.resolve(base);
        assertTrue(oldPlan.isValid(new PrecompiledBaseBean(123)));
        assertFalse(oldPlan.isValid(new PrecompiledBaseBean("ok")));

        SchemaValidator validator = new SchemaValidator(
                new SchemaLoader.Local(SchemaRegistry.DEFAULT_JSON_SCHEMA_DIR), catalog, true);
        assertTrue(validator.validate(new PrecompiledBaseBean("ok")).isValid());
        assertFalse(validator.validate(new PrecompiledBaseBean(123)).isValid());

        // The original catalog's cached plan remains valid and unchanged.
        assertTrue(oldPlan.isValid(new PrecompiledBaseBean(123)));
    }

    @Test
    void registryIndexStillRejectsConflicts() {
        URI key = URI.create("https://example.test/only");
        SchemaRegistry registry = new SchemaRegistry();
        registry.index(key, JsonSchema.fromJson("{\"type\":\"integer\"}"));
        assertThrows(SchemaException.class, () -> registry.index(
                key, JsonSchema.fromJson("{\"type\":\"string\"}")));
        registry.indexIfAbsent(key, JsonSchema.fromJson("{\"type\":\"string\"}"));
        assertTrue(registry.resolve(key).isValid(3));
        assertFalse(registry.resolve(key).isValid("three"));
    }

    @Test
    void sameRetrievalUriIsScopedPerAnnotatedBinding() {
        SchemaValidator validator = new SchemaValidator();
        assertTrue(validator.validate(new StringBean("hello")).isValid());
        assertFalse(validator.validate(new StringBean(123)).isValid());
        assertTrue(validator.validate(new IntegerBean(123)).isValid());
        assertFalse(validator.validate(new IntegerBean("hello")).isValid());
        // Ensure a second pass uses the same per-type plan without contamination.
        assertTrue(validator.validate(new StringBean("world")).isValid());
    }

    @Test
    void readsExternalSchemasRefAndPrefersInlineMapping() {
        SchemaValidator validator = new SchemaValidator();
        assertTrue(validator.validate(new FileBean(123)).isValid());
        assertFalse(validator.validate(new FileBean("hello")).isValid());
        assertTrue(validator.validate(new PriorityBean("hello")).isValid());
        assertFalse(validator.validate(new PriorityBean(123)).isValid());
    }

    @Test
    void validatesMappingShapeAndUris() {
        assertThrows(IllegalArgumentException.class, () -> SchemaLoader.parseExternalSchemas("[]"));
        assertThrows(IllegalArgumentException.class,
                () -> SchemaLoader.parseExternalSchemas("{\"relative/path\":true}"));
        assertThrows(IllegalArgumentException.class,
                () -> SchemaLoader.parseExternalSchemas("{\"https://example.test/resource#/$defs/a\":true}"));
        assertThrows(IllegalArgumentException.class,
                () -> SchemaLoader.parseExternalSchemas("{\"https://example.test/resource\":null}"));
        assertEquals(2, SchemaLoader.parseExternalSchemas(
                "{\"https://example.test/a\":true,\"https://example.test/b\":false}").size());
    }
}
