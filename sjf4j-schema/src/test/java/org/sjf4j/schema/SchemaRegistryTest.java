package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaRegistryTest {

    @Test
    void loadSchemaFromMissingLocalUri_returnsNull() {
        assertNull(SchemaUtil.loadSchemaFromLocalUri(URI.create("classpath:///json-schemas/missing-schema.json")));
    }

    @Test
    void index_allowsOrderIndependentDeferredCompilation() {
        ObjectSchema rootSchema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"http://example.com/root\"," +
                "\"$ref\":\"leaf\"" +
                "}");
        ObjectSchema leafSchema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"http://example.com/leaf\"," +
                "\"type\":\"string\"," +
                "\"minLength\":1" +
                "}");

        SchemaRegistry registry = new SchemaRegistry();
        registry.index(rootSchema).index(leafSchema);

        SchemaPlan plan = rootSchema.createPlan(registry);
        assertTrue(plan.validate("x").isValid());
        assertFalse(plan.validate("").isValid());
    }

    @Test
    void index_resolvesRelativeRootIdAgainstRetrievalUri() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"defs/user.json\"," +
                "\"type\":\"string\"" +
                "}");

        SchemaRegistry registry = new SchemaRegistry();
        registry.index(URI.create("file:///schemas/root.json"), schema);

        assertTrue(registry.contains("file:///schemas/defs/user.json"));
        assertTrue(registry.contains("file:///schemas/root.json"));
        assertTrue(registry.contains(URI.create("file:///schemas/defs/user.json")));
        assertTrue(registry.contains(URI.create("file:///schemas/root.json")));
    }

    @Test
    void resolve_fallsBackToGlobalIndexedSchemas() {
        SchemaPlan plan = new SchemaRegistry().resolve(URI.create("https://json-schema.org/draft/2020-12/schema"));

        assertNotNull(plan);
    }

    @Test
    void resolve_fallsBackToGlobalIndexedDraft2019AndDraft7Schemas() {
        assertNotNull(new SchemaRegistry().resolve(URI.create("https://json-schema.org/draft/2019-09/schema")));
        assertNotNull(new SchemaRegistry().resolve(URI.create("https://json-schema.org/draft/2019-09/meta/core")));
        assertNotNull(new SchemaRegistry().resolve(URI.create("http://json-schema.org/draft-07/schema#")));
        assertNotNull(new SchemaRegistry().resolve(URI.create("https://json-schema.org/draft-07/schema")));
    }

    @Test
    void resolveBuilt_doesNotLazyCompileIndexedSchema() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"https://example.com/user\"," +
                "\"type\":\"string\"" +
                "}");
        URI uri = URI.create("https://example.com/user");

        SchemaRegistry registry = new SchemaRegistry();
        registry.index(schema);

        assertNull(registry.resolveBuilt(uri));
        assertNotNull(registry.resolve(uri));
        assertNotNull(registry.resolveBuilt(uri));
    }

    @Test
    void resolve_missingFragment_reportsConsistentSchemaResolveMessage() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"https://example.com/user\"," +
                "\"type\":\"string\"" +
                "}");

        SchemaRegistry registry = new SchemaRegistry();
        registry.register(schema);

        SchemaException ex = assertThrows(SchemaException.class,
                () -> registry.resolve("https://example.com/user", "missing"));
        assertTrue(ex.getMessage().contains("SCHEMA schema.resolve: cannot resolve schema fragment '#missing'"));
        assertTrue(ex.getMessage().contains("schema=https://example.com/user"));
    }

    @Test
    void resolve_missingPointerFragment_reportsConsistentSchemaResolveMessage() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"https://example.com/user\"," +
                "\"properties\":{\"name\":{\"type\":\"string\"}}" +
                "}");

        SchemaRegistry registry = new SchemaRegistry();
        registry.register(schema);

        SchemaException ex = assertThrows(SchemaException.class,
                () -> registry.resolve("https://example.com/user", "/properties/age"));
        assertTrue(ex.getMessage().contains("SCHEMA schema.resolve: cannot resolve schema fragment '#/properties/age'"));
        assertTrue(ex.getMessage().contains("schema=https://example.com/user"));
    }

    @Test
    void index_duplicateSchemaUri_reportsConsistentConflictMessage() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.index(JsonSchema.fromJson("{" +
                "\"$id\":\"https://example.com/user\"," +
                "\"type\":\"string\"" +
                "}"));

        SchemaException ex = assertThrows(SchemaException.class,
                () -> registry.index(JsonSchema.fromJson("{" +
                        "\"$id\":\"https://example.com/user\"," +
                        "\"type\":\"integer\"" +
                        "}")));
        assertTrue(ex.getMessage().contains("SCHEMA schema.conflict: duplicate schema uri 'https://example.com/user'"));
        assertTrue(ex.getMessage().contains("schema=https://example.com/user"));
    }

}
