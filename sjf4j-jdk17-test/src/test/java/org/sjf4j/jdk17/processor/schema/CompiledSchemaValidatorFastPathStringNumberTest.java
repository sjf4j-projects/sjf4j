package org.sjf4j.jdk17.processor.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.CompiledSchemaValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.annotation.schema.ValidatorOptions;
import org.sjf4j.compiled.CompiledRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledSchemaValidatorFastPathStringNumberTest {

    @Test
    public void validatesStringNumberAndArrayBoundariesInFastPath() {
        Validator validator = CompiledRegistry.of(Validator.class);

        assertTrue(validator.isValid(new Sample("AB12", 1, 1.5, List.of("ok"))));
        assertTrue(validator.isValid(new Sample("AB1234", 10, 9.5, List.of("ok", "yo", "up"))));

        assertFalse(validator.isValid(new Sample("AB1", 1, 1.5, List.of("ok"))));
        assertFalse(validator.isValid(new Sample("AB12345", 1, 1.5, List.of("ok"))));
        assertFalse(validator.isValid(new Sample("ab12", 1, 1.5, List.of("ok"))));
        assertFalse(validator.isValid(new Sample("AB12", 0, 1.5, List.of("ok"))));
        assertFalse(validator.isValid(new Sample("AB12", 11, 1.5, List.of("ok"))));
        assertFalse(validator.isValid(new Sample("AB12", 1, 1.0, List.of("ok"))));
        assertFalse(validator.isValid(new Sample("AB12", 1, 10.0, List.of("ok"))));
        assertFalse(validator.isValid(new Sample("AB12", 1, 1.5, List.of())));
        assertFalse(validator.isValid(new Sample("AB12", 1, 1.5, List.of("ok", "yo", "up", "no"))));
        assertFalse(validator.isValid(new Sample("AB12", 1, 1.5, List.of("x"))));
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "properties":{
            "code":{"type":"string","minLength":4,"maxLength":6,"pattern":"^[A-Z]+[0-9]+$"},
            "count":{"type":"integer","minimum":1,"maximum":10},
            "ratio":{"type":"number","exclusiveMinimum":1,"exclusiveMaximum":10},
            "tags":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"string","minLength":2}}
          }
        }
        """)
    public record Sample(String code, int count, double ratio, List<String> tags) {}

    @CompiledSchemaValidator
    public interface Validator {
        @ValidatorOptions(fallback = false)
        boolean isValid(Sample sample);
    }
}
