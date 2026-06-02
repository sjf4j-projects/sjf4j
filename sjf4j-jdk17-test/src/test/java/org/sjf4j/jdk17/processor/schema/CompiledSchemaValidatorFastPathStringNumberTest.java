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

    @Test
    public void validatesEnumCharJavaArrayAndNestedRecordInFastPath() {
        CompositeValidator validator = CompiledRegistry.of(CompositeValidator.class);

        assertTrue(validator.isValid(new Composite(State.ACTIVE, 'A', new String[]{"ok"}, new Address("Paris"))));
        assertTrue(validator.isValid(new Composite(State.PAUSED, 'Z', new String[]{"ok", "yo"}, new Address("Rome"))));

        assertFalse(validator.isValid(new Composite(State.DISABLED, 'A', new String[]{"ok"}, new Address("Paris"))));
        assertFalse(validator.isValid(new Composite(State.ACTIVE, 'A', new String[]{}, new Address("Paris"))));
        assertFalse(validator.isValid(new Composite(State.ACTIVE, 'A', new String[]{"x"}, new Address("Paris"))));
        assertFalse(validator.isValid(new Composite(State.ACTIVE, 'A', new String[]{"ok"}, new Address("X"))));
        assertFalse(validator.isValid(new Composite(State.ACTIVE, 'A', new String[]{"ok"}, null)));
    }

    @Test
    public void validatesFormatInFastPathWhenStrict() {
        UuidValidator validator = CompiledRegistry.of(UuidValidator.class);
        UuidSample good = new UuidSample("123e4567-e89b-12d3-a456-426614174000");
        UuidSample bad = new UuidSample("not-a-uuid");

        assertTrue(validator.strict(good));
        assertFalse(validator.strict(bad));
        assertTrue(validator.lenient(bad));
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

    public enum State { ACTIVE, PAUSED, DISABLED }

    @ValidJsonSchema("""
        {
          "type":"object",
          "required":["state","initial","tags","address"],
          "properties":{
            "state":{"enum":["ACTIVE","PAUSED"]},
            "initial":{"type":"string","minLength":1,"maxLength":1},
            "tags":{"type":"array","minItems":1,"items":{"type":"string","minLength":2}},
            "address":{
              "type":"object",
              "required":["city"],
              "properties":{"city":{"type":"string","minLength":2}},
              "additionalProperties":false
            }
          },
          "additionalProperties":false
        }
        """)
    public record Composite(State state, char initial, String[] tags, Address address) {}

    public record Address(String city) {}

    @CompiledSchemaValidator
    public interface CompositeValidator {
        @ValidatorOptions(fallback = false)
        boolean isValid(Composite sample);
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "properties":{"id":{"type":"string","format":"uuid"}}
        }
        """)
    public record UuidSample(String id) {}

    @CompiledSchemaValidator
    public interface UuidValidator {
        @ValidatorOptions(fallback = false)
        boolean strict(UuidSample sample);

        @ValidatorOptions(fallback = false, strictFormat = false)
        boolean lenient(UuidSample sample);
    }
}
