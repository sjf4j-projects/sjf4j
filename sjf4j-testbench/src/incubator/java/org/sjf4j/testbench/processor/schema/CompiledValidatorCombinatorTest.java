package org.sjf4j.testbench.processor.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.annotation.schema.ValidatingOptions;
import org.sjf4j.compiled.CompiledInstances;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledValidatorCombinatorTest {

    @Test
    public void validatesAnyOfNotAndConditionalInFastPath() {
        Validator validator = CompiledInstances.of(Validator.class);

        assertTrue(validator.isValid(new Rule(5, "A", "adult")));
        assertTrue(validator.isValid(new Rule(15, "B", "teen")));

        assertFalse(validator.isValid(new Rule(0, "A", "adult")));
        assertFalse(validator.isValid(new Rule(5, "X", "adult")));
        assertFalse(validator.isValid(new Rule(20, "A", "teen")));
        assertFalse(validator.isValid(new Rule(10, "A", "adult")));
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "properties":{
            "score":{
              "anyOf":[
                {"type":"integer","minimum":1,"maximum":5},
                {"type":"integer","minimum":10,"maximum":15}
              ],
              "not":{"const":10}
            },
            "kind":{"enum":["A","B"]}
          },
          "if":{"properties":{"kind":{"const":"A"}}},
          "then":{"properties":{"group":{"const":"adult"}}},
          "else":{"properties":{"group":{"const":"teen"}}}
        }
        """)
    public record Rule(int score, String kind, String group) {}

    @CompiledValidator
    public interface Validator {
        @ValidatingOptions(fallback = false)
        boolean isValid(Rule rule);
    }
}
