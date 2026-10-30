package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.ValidationException;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.ValidationResult;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the three return contracts and optional per-call strict format flag. */
class CompiledValidatorContractTest {

    @ValidJsonSchema("{\"type\":\"object\",\"properties\":{"
            + "\"email\":{\"type\":\"string\",\"format\":\"email\"},"
            + "\"code\":{\"type\":\"string\",\"minLength\":3}}}")
    public static final class Person {
        private String email;
        private String code;
        public Person() { }
        public Person(String email, String code) {
            this.email = email;
            this.code = code;
        }
        public String getEmail() { return email; }
        public String getCode() { return code; }
    }

    @CompiledValidator
    public interface PersonValidator {
        boolean isValid(Person person);
        boolean isValid(Person person, boolean strictFormat);
        void requireValid(Person person);
        void requireValid(Person person, boolean strictFormat);
        ValidationResult validate(Person person);
        ValidationResult validate(Person person, boolean strictFormat);
    }

    @ValidJsonSchema("{\"type\":\"object\",\"properties\":{"
            + "\"name\":{\"type\":\"string\",\"minLength\":2}}}")
    public static final class Name {
        private final String name;
        public Name(String name) { this.name = name; }
        public String getName() { return name; }
    }

    @CompiledValidator(fallback = false)
    public interface DirectValidator {
        boolean isValid(Name value);
        boolean isValid(Name value, boolean strictFormat);
    }

    @Test
    void compiledBooleanUsesStrictFormatOnlyWhenRequested() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        Person badEmail = new Person("invalid-email", "good");
        assertFalse(validator.isValid(badEmail));
        assertFalse(validator.isValid(badEmail, true));
        assertTrue(validator.isValid(badEmail, false));
        SchemaPlan reference = JsonSchema.fromJson(
                Person.class.getAnnotation(ValidJsonSchema.class).value()).createPlan();
        assertEquals(reference.isValid(badEmail, false), validator.isValid(badEmail, false));
        assertEquals(reference.isValid(badEmail, true), validator.isValid(badEmail, true));
        assertTrue(validator.isValid(null));
        assertTrue(validator.isValid(null, false));
    }

    @Test
    void requireValidReturnsOrThrowsWithDiagnostics() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        Person badEmail = new Person("invalid-email", "good");
        assertThrows(ValidationException.class, () -> validator.requireValid(badEmail));
        assertThrows(ValidationException.class, () -> validator.requireValid(badEmail, true));
        assertDoesNotThrow(() -> validator.requireValid(badEmail, false));
        assertDoesNotThrow(() -> validator.requireValid(null));
    }

    @Test
    void validationResultAggregatesEveryErrorAndSupportsFormatFlag() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        Person invalid = new Person("invalid-email", "x");
        ValidationResult strict = validator.validate(invalid);
        assertFalse(strict.isValid());
        SchemaPlan reference = JsonSchema.fromJson(
                Person.class.getAnnotation(ValidJsonSchema.class).value()).createPlan();
        assertEquals(reference.validate(invalid, false, true).getErrors().size(),
                strict.getErrors().size());
        assertTrue(strict.getErrors().size() >= 2, () -> strict.toString());
        ValidationResult lenient = validator.validate(invalid, false);
        assertFalse(lenient.isValid());
        assertEquals(1, lenient.getErrors().size(), () -> lenient.toString());
        assertSame(ValidationResult.SUCCESS, validator.validate(null));
    }

    @Test
    void fallbackFalseRequiresDirectChecksForAllProperties() {
        DirectValidator validator = CompiledInstances.create(DirectValidator.class);
        assertTrue(validator.isValid(new Name("ok")));
        assertTrue(validator.isValid(new Name("ok"), false));
        assertFalse(validator.isValid(new Name("x")));
        assertFalse(validator.isValid(new Name("x"), true));
    }
}
