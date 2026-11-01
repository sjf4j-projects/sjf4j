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
        void requireValid(Name value);
        void requireValid(Name value, boolean strictFormat);
    }

    @CompiledValidator(fallback = false)
    public interface ApplicatorValidator {
        @ValidJsonSchema("{\"oneOf\":[{\"type\":\"string\",\"minLength\":3},"
                + "{\"type\":\"string\",\"minLength\":5}]}")
        void requireOneOf(String value);

        @ValidJsonSchema("{\"anyOf\":[{\"type\":\"string\",\"minLength\":3},"
                + "{\"type\":\"number\",\"minimum\":10}]}")
        void requireAnyOf(Object value);

        @ValidJsonSchema("{\"not\":{\"type\":\"integer\"}}")
        void requireNot(Object value);
    }

    @Test
    void nativeRequireValidKeepsNestedPredicatesBoolean() {
        ApplicatorValidator validator = CompiledInstances.create(ApplicatorValidator.class);

        // Overlapping oneOf branches: exactly one match is required.
        assertDoesNotThrow(() -> validator.requireOneOf("abcd"));
        assertThrows(ValidationException.class, () -> validator.requireOneOf("ab"));
        assertThrows(ValidationException.class, () -> validator.requireOneOf("abcdef"));

        // anyOf accepts either branch; not inverts the nested predicate.
        assertDoesNotThrow(() -> validator.requireAnyOf("hello"));
        assertDoesNotThrow(() -> validator.requireAnyOf(11));
        assertThrows(ValidationException.class, () -> validator.requireAnyOf("x"));
        assertThrows(ValidationException.class, () -> validator.requireAnyOf(5));
        assertDoesNotThrow(() -> validator.requireNot("text"));
        assertThrows(ValidationException.class, () -> validator.requireNot(12));
    }

    @Test
    void compiledBooleanUsesStrictFormatOnlyWhenRequested() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        Person badEmail = new Person("invalid-email", "good");
        assertTrue(validator.isValid(badEmail));
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
        assertDoesNotThrow(() -> validator.requireValid(badEmail));
        assertThrows(ValidationException.class, () -> validator.requireValid(badEmail, true));
        assertDoesNotThrow(() -> validator.requireValid(badEmail, false));
        assertDoesNotThrow(() -> validator.requireValid(null));
    }

    @Test
    void validationResultAggregatesEveryErrorAndSupportsFormatFlag() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        Person invalid = new Person("invalid-email", "x");
        ValidationResult strict = validator.validate(invalid, true);
        assertFalse(strict.isValid());
        SchemaPlan reference = JsonSchema.fromJson(
                Person.class.getAnnotation(ValidJsonSchema.class).value()).createPlan();
        assertEquals(reference.validate(invalid, false, true).getErrors().size(),
                strict.getErrors().size());
        assertTrue(strict.getErrors().size() >= 2, () -> strict.toString());
        ValidationResult lenient = validator.validate(invalid);
        assertFalse(lenient.isValid());
        assertEquals(1, lenient.getErrors().size(), () -> lenient.toString());
        assertEquals(lenient.getErrors().size(), validator.validate(invalid, false).getErrors().size());
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

    @Test
    void nativeRequireValidPreservesFullFailureDiagnostics() {
        DirectValidator validator = CompiledInstances.create(DirectValidator.class);
        assertDoesNotThrow(() -> validator.requireValid(new Name("good")));
        assertDoesNotThrow(() -> validator.requireValid(new Name("good"), true));
        assertDoesNotThrow(() -> validator.requireValid(null));

        ValidationException failure = assertThrows(ValidationException.class,
                () -> validator.requireValid(new Name("x")));
        assertFalse(failure.getResult().isValid());
        SchemaPlan reference = JsonSchema.fromJson(
                Name.class.getAnnotation(ValidJsonSchema.class).value()).createPlan();
        ValidationException expected = assertThrows(ValidationException.class,
                () -> reference.requireValid(new Name("x"), false));
        assertEquals(expected.getResult().getErrors().size(),
                failure.getResult().getErrors().size());
        assertEquals(expected.getResult().getLastError().getKeyword(),
                failure.getResult().getLastError().getKeyword());
        assertEquals(expected.getResult().getLastError().getInstancePs().rootedPointerExpr(),
                failure.getResult().getLastError().getInstancePs().rootedPointerExpr());
        assertThrows(ValidationException.class,
                () -> validator.requireValid(new Name("x"), true));
    }
}
