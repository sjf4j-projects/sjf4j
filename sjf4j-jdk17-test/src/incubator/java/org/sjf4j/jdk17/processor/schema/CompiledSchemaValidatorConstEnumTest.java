package org.sjf4j.jdk17.processor.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.CompiledSchemaValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.annotation.schema.ValidatorOptions;
import org.sjf4j.compiled.CompiledNodes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledSchemaValidatorConstEnumTest {

    @Test
    public void validatesConstAndEnumInFastPath() {
        Validator validator = CompiledNodes.of(Validator.class);

        assertTrue(validator.isValid(new Choice("fixed", "red")));
        assertTrue(validator.isValid(new Choice("fixed", "blue")));
        assertFalse(validator.isValid(new Choice("other", "red")));
        assertFalse(validator.isValid(new Choice("fixed", "green")));
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "properties":{
            "kind":{"const":"fixed"},
            "color":{"enum":["red","blue"]}
          }
        }
        """)
    public record Choice(String kind, String color) {}

    @CompiledSchemaValidator
    public interface Validator {
        @ValidatorOptions(fallback = false)
        boolean isValid(Choice choice);
    }
}
