package org.sjf4j.jdk17.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.SchemaValidator;
import org.sjf4j.schema.ValidationResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidJsonSchemaTest {

    @ValidJsonSchema("""
        {
            "type":"object",
            "required":["id"],
            "properties":{
                "id":{
                    "type":"integer",
                    "minimum":1
                }
            }
        }
    """)
    public static class Order {
        public int id;
    }

    @Test
    public void testInlineSchemaOnPojo() {
        SchemaValidator validator = new SchemaValidator();
        Order ok = new Order();
        ok.id = 2;
        ValidationResult result = validator.validate(ok);
        assertTrue(result.isValid());

        Order bad = new Order();
        bad.id = 0;
        assertFalse(validator.validate(bad).isValid());
    }

}
