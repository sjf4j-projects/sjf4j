package org.sjf4j;

import org.junit.jupiter.api.Test;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.ValidationResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SchemaExampleTest {

    @Test
    public void testJsonSchema1() {
        JsonSchema schema = JsonSchema.fromJson("{ \"type\": \"number\" }");
        SchemaPlan plan = schema.createPlan();
        // Prepares the schema for validation

        assertTrue(plan.isValid(1));                              // Passes validation

        ValidationResult result = plan.validate("a");
        assertFalse(result.isValid());                                  // Fails validation
        assertEquals("type", result.getLastMessage().getKeyword()); // Fails validation

    }
}
