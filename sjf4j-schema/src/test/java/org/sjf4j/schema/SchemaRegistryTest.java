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
        registry.index(rootSchema);
        registry.index(leafSchema);

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
    void indexIfAbsent_skipsExistingRetrievalUriWithoutConflict() {
        URI retrievalUri = URI.create("http://localhost:1234/schema.json");
        ObjectSchema stringSchema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"http://localhost:1234/string-schema.json\"," +
                "\"type\":\"string\"" +
                "}");
        ObjectSchema numberSchema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"http://localhost:1234/number-schema.json\"," +
                "\"type\":\"number\"" +
                "}");

        SchemaRegistry registry = new SchemaRegistry();
        registry.index(retrievalUri, stringSchema);
        registry.indexIfAbsent(retrievalUri, numberSchema);

        assertTrue(registry.resolve(retrievalUri).validate("x").isValid());
        assertFalse(registry.resolve(retrievalUri).validate(1).isValid());
        assertFalse(registry.contains(URI.create("http://localhost:1234/number-schema.json")));
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
    void createPlan_missingRefResource_reportsResourceAndKeyword() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"," +
                "\"$ref\":\"http://localhost:1234/draft2020-12/detached-ref.json#/$defs/foo\"" +
                "}");

        SchemaException ex = assertThrows(SchemaException.class,
                () -> schema.createPlan(new SchemaRegistry()));
        assertTrue(ex.getMessage().contains("cannot resolve schema resource 'http://localhost:1234/draft2020-12/detached-ref.json'"));
        assertTrue(ex.getMessage().contains("preload or register the referenced schema"));
        assertTrue(ex.getMessage().contains("keyword=/$ref"));
    }

    @Test
    void createPlan_missingRefFragment_reportsFragmentResourceAndKeyword() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.index(JsonSchema.fromJson("{" +
                "\"$id\":\"http://localhost:1234/draft2020-12/detached-ref.json\"," +
                "\"$defs\":{}" +
                "}"));
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"," +
                "\"$ref\":\"http://localhost:1234/draft2020-12/detached-ref.json#/$defs/foo\"" +
                "}");

        SchemaException ex = assertThrows(SchemaException.class,
                () -> schema.createPlan(registry));
        assertTrue(ex.getMessage().contains("cannot resolve schema fragment '#/$defs/foo'"));
        assertTrue(ex.getMessage().contains("resource 'http://localhost:1234/draft2020-12/detached-ref.json'"));
        assertTrue(ex.getMessage().contains("keyword=/$ref"));
    }

    @Test
    void createPlan_missingCustomMetaschema_reportsSchemaKeyword() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"https://schema/using/no/validation\"," +
                "\"$schema\":\"http://localhost:1234/draft2020-12/metaschema-no-validation.json\"," +
                "\"properties\":{\"badProperty\":false}" +
                "}");

        SchemaException ex = assertThrows(SchemaException.class,
                () -> schema.createPlan(new SchemaRegistry()));
        assertTrue(ex.getMessage().contains("cannot resolve $schema 'http://localhost:1234/draft2020-12/metaschema-no-validation.json'"));
        assertTrue(ex.getMessage().contains("preload or register the metaschema"));
        assertTrue(ex.getMessage().contains("keyword=/$schema"));
        assertTrue(ex.getMessage().contains("schema=https://schema/using/no/validation"));
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
