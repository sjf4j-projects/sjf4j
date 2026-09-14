package org.sjf4j.testbench.processor.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.annotation.schema.ValidatingOptions;
import org.sjf4j.CompiledInstances;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledValidatorConstEnumTest {

    @Test
    public void validatesConstAndEnumInFastPath() {
        Validator validator = CompiledInstances.of(Validator.class);

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

    @CompiledValidator
    public interface Validator {
        @ValidatingOptions(fallback = false)
        boolean isValid(Choice choice);
    }
}
