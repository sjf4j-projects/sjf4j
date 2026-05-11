package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaRegistryTest {

    @Test
    void loadSchemaFromMissingLocalUri_returnsNull() {
        assertNull(SchemaRegistry.loadSchemaFromLocalUri(URI.create("classpath:///json-schemas/missing-schema.json")));
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

}
