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

    @CompiledValidator(fallback=true)
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
        assertFalse(validator.isValid(null));
        assertFalse(validator.isValid(null, false));
    }

    @Test
    void requireValidReturnsOrThrowsWithDiagnostics() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        Person badEmail = new Person("invalid-email", "good");
        assertDoesNotThrow(() -> validator.requireValid(badEmail));
        assertThrows(ValidationException.class, () -> validator.requireValid(badEmail, true));
        assertDoesNotThrow(() -> validator.requireValid(badEmail, false));
        assertThrows(ValidationException.class, () -> validator.requireValid(null));
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
        assertFalse(validator.validate(null).isValid());
        assertEquals("type", validator.validate(null).getLastError().getKeyword());
    }

    @Test
    void fallbackFalseRequiresDirectChecksForAllProperties() {
        DirectValidator validator = CompiledInstances.create(DirectValidator.class);
        assertTrue(validator.isValid(new Name("ok")));
        assertTrue(validator.isValid(new Name("ok"), false));
        assertFalse(validator.isValid(new Name("x")));
        assertFalse(validator.isValid(new Name("x"), true));
    }

    @CompiledValidator(fallback = false)
    public interface NullSchemaValidator {
        @ValidJsonSchema("false")
        boolean falseSchema(Object value);

        @ValidJsonSchema("true")
        boolean trueSchema(Object value);

        @ValidJsonSchema("{\"type\":\"null\"}")
        boolean nullType(Object value);

        @ValidJsonSchema("{\"type\":\"string\"}")
        boolean stringType(Object value);

        @ValidJsonSchema("{\"const\":null}")
        boolean nullConst(Object value);

        @ValidJsonSchema("{\"enum\":[null,\"ok\"]}")
        boolean nullEnum(Object value);

        @ValidJsonSchema("{\"not\":{\"type\":\"null\"}}")
        boolean notNull(Object value);

        @ValidJsonSchema("{\"required\":[\"name\"]}")
        boolean objectOnlyRequired(Object value);

        @ValidJsonSchema("{\"type\":\"object\",\"required\":[\"name\"]}")
        boolean typedRequired(Object value);

        @ValidJsonSchema("{\"type\":\"object\"}")
        boolean staticObjectType(Name value);

        @ValidJsonSchema("{\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":2}}}")
        boolean staticObjectProperties(Name value);

        @ValidJsonSchema("{\"required\":[\"missing\"]}")
        boolean staticMissingRequired(Name value);

        @ValidJsonSchema("{\"type\":\"object\",\"required\":[\"missing\"]}")
        boolean staticTypedMissingRequired(Name value);

        @ValidJsonSchema("{\"properties\":{},\"additionalProperties\":false}")
        boolean staticAdditionalProperties(Name value);

        @ValidJsonSchema("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}")
        boolean staticTypedAdditionalProperties(Name value);

        @ValidJsonSchema("false")
        void requireFalse(Object value);

        @ValidJsonSchema("true")
        void requireTrue(Object value);

        @ValidJsonSchema("{\"type\":\"object\"}")
        void requireObject(Name value);
    }

    @Test
    void nullIsValidatedAgainstCompiledSchemaRatherThanSkipped() {
        NullSchemaValidator validator = CompiledInstances.create(NullSchemaValidator.class);

        assertFalse(validator.falseSchema(null));
        assertTrue(validator.trueSchema(null));
        assertTrue(validator.nullType(null));
        assertFalse(validator.nullType("text"));
        assertFalse(validator.stringType(null));
        assertTrue(validator.nullConst(null));
        assertFalse(validator.nullConst("text"));
        assertTrue(validator.nullEnum(null));
        assertFalse(validator.notNull(null));
        assertTrue(validator.notNull("text"));

        // Object-only keywords are ignored by a null instance unless an
        // explicit object type is required.
        assertTrue(validator.objectOnlyRequired(null));
        assertFalse(validator.typedRequired(null));
        assertTrue(validator.typedRequired(java.util.Map.of("name", "ok")));

        // The static POJO fast path has the same null/type applicability rules.
        assertFalse(validator.staticObjectType(null));
        assertTrue(validator.staticObjectProperties(null));
        assertTrue(validator.staticMissingRequired(null));
        assertFalse(validator.staticMissingRequired(new Name("ok")));
        assertFalse(validator.staticTypedMissingRequired(null));
        assertTrue(validator.staticAdditionalProperties(null));
        assertFalse(validator.staticAdditionalProperties(new Name("ok")));
        assertFalse(validator.staticTypedAdditionalProperties(null));

        assertThrows(ValidationException.class, () -> validator.requireFalse(null));
        assertDoesNotThrow(() -> validator.requireTrue(null));
        assertThrows(ValidationException.class, () -> validator.requireObject(null));
    }

    public static final class ArrayHolder {
        private final java.util.List<String> items;
        public ArrayHolder(java.util.List<String> items) { this.items = items; }
        public java.util.List<String> getItems() { return items; }
    }

    @CompiledValidator(fallback = false)
    public interface NestedValidator {
        @ValidJsonSchema("{\"type\":\"object\",\"properties\":{\"items\":{\"type\":\"array\","
                + "\"items\":{\"oneOf\":[{\"type\":\"string\",\"minLength\":1},"
                + "{\"type\":\"string\",\"minLength\":2}]}}}}")
        void requireValid(ArrayHolder value);
    }

    @CompiledValidator(fallback = false)
    public interface ConditionalValidator {
        @ValidJsonSchema("{\"if\":{\"const\":\"x\"},\"then\":{\"const\":\"y\"},"
                + "\"else\":{\"const\":\"z\"}}")
        void requireValid(String value);
    }

    @Test
    void conditionalFailureReportsSelectedBranch() {
        ConditionalValidator validator = CompiledInstances.create(ConditionalValidator.class);
        ValidationException ifTrue = assertThrows(ValidationException.class,
                () -> validator.requireValid("x"));
        ValidationException ifFalse = assertThrows(ValidationException.class,
                () -> validator.requireValid("a"));
        assertEquals("const", ifTrue.getResult().getLastError().getKeyword());
        assertEquals("/then/const", ifTrue.getResult().getLastError().getKeywordPs().rootedPointerExpr());
        assertEquals("/else/const", ifFalse.getResult().getLastError().getKeywordPs().rootedPointerExpr());
    }

    @CompiledValidator(fallback = true)
    public interface FallbackValidator {
        @ValidJsonSchema("{\"type\":\"object\",\"properties\":{\"nested\":{\"type\":\"object\","
                + "\"patternProperties\":{\"^x\":{\"type\":\"number\"}}}}}")
        void requireValid(java.util.Map<String, Object> value);
    }

    @Test
    void trueFallbackPreservesParentInstancePath() {
        FallbackValidator validator = CompiledInstances.create(FallbackValidator.class);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.requireValid(java.util.Map.of("nested",
                        java.util.Map.of("xName", "not a number"))));
        assertEquals("/nested/xName", ex.getResult().getLastError().getInstancePs().rootedPointerExpr());
    }

    @Test
    void nestedOneOfReportsArrayElementInsteadOfRevalidatingRoot() {
        NestedValidator validator = CompiledInstances.create(NestedValidator.class);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.requireValid(new ArrayHolder(java.util.List.of("ok"))));
        assertEquals("oneOf", ex.getResult().getLastError().getKeyword());
        assertEquals("/items/0", ex.getResult().getLastError().getInstancePs().rootedPointerExpr());
        assertEquals("/properties/items/items/oneOf",
                ex.getResult().getLastError().getKeywordPs().rootedPointerExpr());
    }

    @Test
    void directApplicatorFailureKeepsKeywordAndPosition() {
        ApplicatorValidator validator = CompiledInstances.create(ApplicatorValidator.class);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.requireOneOf("abcdef"));
        assertEquals("oneOf", ex.getResult().getLastError().getKeyword());
        assertEquals("", ex.getResult().getLastError().getInstancePs().rootedPointerExpr());
        assertEquals("/oneOf", ex.getResult().getLastError().getKeywordPs().rootedPointerExpr());
    }

    @Test
    void nativeRequireValidPreservesFullFailureDiagnostics() {
        DirectValidator validator = CompiledInstances.create(DirectValidator.class);
        assertDoesNotThrow(() -> validator.requireValid(new Name("good")));
        assertDoesNotThrow(() -> validator.requireValid(new Name("good"), true));
        assertThrows(ValidationException.class, () -> validator.requireValid(null));

        ValidationException failure = assertThrows(ValidationException.class,
                () -> validator.requireValid(new Name("x")));
        assertFalse(failure.getResult().isValid());
        // Compiled POJO access can identify the nested assertion precisely.
        // Re-running the root runtime plan would instead report UNKNOWN type
        // for an unregistered raw Java POJO and hide the compiled error.
        assertEquals(1, failure.getResult().getErrors().size());
        assertEquals("minLength", failure.getResult().getLastError().getKeyword());
        assertEquals("/name", failure.getResult().getLastError().getInstancePs().rootedPointerExpr());
        assertEquals("/properties/name/minLength",
                failure.getResult().getLastError().getKeywordPs().rootedPointerExpr());
        assertThrows(ValidationException.class,
                () -> validator.requireValid(new Name("x"), true));
    }
}
