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
        assertThrows(IllegalArgumentException.class, () -> ExternalSchemas.parse("[]"));
        assertThrows(IllegalArgumentException.class,
                () -> ExternalSchemas.parse("{\"relative/path\":true}"));
        assertThrows(IllegalArgumentException.class,
                () -> ExternalSchemas.parse("{\"https://example.test/resource#/$defs/a\":true}"));
        assertThrows(IllegalArgumentException.class,
                () -> ExternalSchemas.parse("{\"https://example.test/resource\":null}"));
        assertEquals(2, ExternalSchemas.parse(
                "{\"https://example.test/a\":true,\"https://example.test/b\":false}").size());
    }
}
