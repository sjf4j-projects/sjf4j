package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

}
