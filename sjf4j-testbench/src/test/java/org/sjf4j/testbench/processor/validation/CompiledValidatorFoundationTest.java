package org.sjf4j.testbench.processor.validation;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.ValidationException;
import org.sjf4j.schema.ValidationResult;

import static org.junit.jupiter.api.Assertions.*;

class CompiledValidatorFoundationTest {

    @Test
    void typeAndInheritedSchemasAreValidatedInOrder() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        Employee good = new Employee("Alice", 3);
        Employee shortName = new Employee("A", 3);
        Employee badLevel = new Employee("Alice", -1);

        assertTrue(validator.isValid(good), () -> "compiled=" + validator.validate(good) + " runtime=" + new org.sjf4j.schema.SchemaValidator().validate(good));
        assertFalse(validator.isValid(shortName));
        assertFalse(validator.isValid(badLevel));
        assertFalse(validator.isValid(null));

        assertFalse(validator.validate(shortName).isValid());
        assertFalse(validator.validate(badLevel).isValid());
        assertTrue(validator.validate(good).isValid());
        assertThrows(ValidationException.class, () -> validator.requireValid(badLevel));
        validator.requireValid(good);
    }

    @Test
    void methodAndParameterBindingsOverrideTypeBinding() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        Employee invalidType = new Employee("Joe", -7);

        assertFalse(validator.isValid(invalidType));
        assertTrue(validator.methodSchema(invalidType), () -> "method binding should override types; validation=" + validator.validate(invalidType));
        assertTrue(validator.parameterSchema(invalidType));
        assertFalse(validator.methodSchema(new Employee("", -7)));
        assertFalse(validator.parameterSchema(new Employee("J", -7)));
    }

    @Test
    void methodBindingCanValidateUnannotatedTypes() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        assertTrue(validator.text("abcd"));
        assertFalse(validator.text("a"));
        assertFalse(validator.text(null));
    }

    @Test
    void primitiveParameterUsesRuntimeFallbackWithoutNullCheck() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        assertTrue(validator.positive(3));
        assertFalse(validator.positive(0));
    }

    @Test
    void referencedSchemaAndItsExternalRefsResolveFromResources() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);

        assertTrue(validator.fromResource(new Payload("valid")));
        assertFalse(validator.fromResource(new Payload("x")));
        assertFalse(validator.fromResource(new Payload(null)));
    }

    @Test
    void annotationRefSelectsExactSubschema() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        assertFalse(validator.fromFragment("x"));
        assertFalse(validator.fromFragment("abc"));
        assertTrue(validator.fromFragment("abcd"));
        assertFalse(validator.fromFragment(null)); // null is not a JSON string
        assertTrue(validator.fromPayloadFragment(new Payload("valid")));
        assertFalse(validator.fromPayloadFragment(new Payload("x")));
    }

    @Test
    void supportsBooleanSchemaResourcesAndDirectBooleanSchema() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        assertFalse(validator.fromBooleanRef("hello"));
        assertFalse(validator.fromBooleanRef(new Payload("valid")));
        assertFalse(validator.fromBooleanRoot("hello"));
        assertTrue(validator.fromBooleanTrue("hello"));
    }

    @Test
    void booleanSchemaFallbackReturnsDiagnosticsForInlineAndReferencedResources() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);

        assertFalse(validator.validateInlineFalse("value").isValid());
        assertEquals("false", validator.validateInlineFalse("value").getLastError().getKeyword());
        assertTrue(validator.validateInlineTrue("value").isValid());
        assertFalse(validator.validateBooleanFalseResource("value").isValid());
        assertTrue(validator.validateBooleanTrueResource("value").isValid());
        assertFalse(validator.validateBooleanRef("value").isValid());

        assertFalse(validator.validateInlineFalse(null).isValid());
        assertFalse(validator.validateBooleanFalseResource(null).isValid());
        assertFalse(validator.validateBooleanRef(null).isValid());
        assertTrue(validator.validateInlineTrue(null).isValid());
        assertTrue(validator.validateBooleanTrueResource(null).isValid());
        assertFalse(validator.fromBooleanRoot(null));
        assertFalse(validator.fromBooleanRef(null));
        assertTrue(validator.fromBooleanTrue(null));
        assertThrows(ValidationException.class, () -> validator.requireBooleanFalse("value"));
        assertThrows(ValidationException.class, () -> validator.requireBooleanFalse(null));
        validator.requireBooleanTrue("value");
        validator.requireBooleanTrue(null);
    }

    @Test
    void usesRuntimeAcronymSnakeCaseConvention() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        assertTrue(validator.convention(new HTTPRequest("abcd")));
        assertFalse(validator.convention(new HTTPRequest("x")));
    }


    @Test
    void directPojoGetterMatchesRuntimeAndDynamicValuesFallback() {
        FastPojoValidator compiled = CompiledInstances.create(FastPojoValidator.class);
        org.sjf4j.schema.SchemaPlan runtime =
                new org.sjf4j.schema.SchemaRegistry().register(
                        java.net.URI.create("sjf4j:/test/fast-pojo.json"),
                        org.sjf4j.schema.JsonSchema.fromJson(FAST_SCHEMA));

        FastPojo good = new FastPojo("Alice", 3);
        FastPojo shortName = new FastPojo("A", 3);
        FastPojo badLevel = new FastPojo("Alice", -1);
        assertTrue(runtime.isValid(good, true));
        assertTrue(compiled.test(good));
        assertEquals(runtime.isValid(good, true), compiled.test(good));
        assertEquals(runtime.isValid(shortName, true), compiled.test(shortName));
        assertEquals(runtime.isValid(badLevel, true), compiled.test(badLevel));
        assertFalse(compiled.test(null));
        assertEquals(runtime.isValid(null, true), compiled.test(null));
    }

    private static final String FAST_SCHEMA = "{\"type\":\"object\",\"required\":[\"name\"],"
            + "\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":2},"
            + "\"level\":{\"type\":\"integer\",\"minimum\":0}},"
            + "\"additionalProperties\":false}";

    public static final class FastPojo {
        private final String name;
        private final int level;
        public FastPojo() { this(null, 0); }
        public FastPojo(String name, int level) { this.name = name; this.level = level; }
        public String getName() { return name; }
        public int getLevel() { return level; }
    }

    @CompiledValidator(fallback=true)
    public interface FastPojoValidator {
        @ValidJsonSchema(FAST_SCHEMA)
        boolean test(FastPojo input);
    }


    // The generated _Impl.java should use the property name for its local: name = input.getName();
    // followed by a direct codePointCount/minLength/maxLength check.
    @Test
    void directStringValidationMatchesRuntime() {
        DirectValidator validator = CompiledInstances.create(DirectValidator.class);
        assertTrue(validator.valid(new DirectBean("Alice")));
        assertFalse(validator.valid(new DirectBean("x")));
        assertFalse(validator.valid(new DirectBean(null)));
        assertFalse(validator.valid(null));
    }

    public static final class DirectBean {
        private String name;
        public DirectBean() {}
        public DirectBean(String name) { this.name = name; }
        public String getName() { return name; }
    }

    @CompiledValidator(fallback=true)
    public interface DirectValidator {
        @ValidJsonSchema("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":2,\"maxLength\":10}}}")
        boolean valid(DirectBean input);
    }

    // The generated _Impl.java should use direct getters and the shared
    // uniqueItems predicate, without creating a runtime SchemaPlan.
    @Test
    void mixesDirectGetterChecksWithCompiledUniqueItems() {
        MixedValidator compiled = CompiledInstances.create(MixedValidator.class);
        assertTrue(compiled.test(new MixedBean("Alice", java.util.Arrays.asList(1, 2))));
        assertFalse(compiled.test(new MixedBean("A", java.util.Arrays.asList(1, 2))));
        assertFalse(compiled.test(new MixedBean("Alice", java.util.Arrays.asList(1, 1))));
    }

    public static final class MixedBean {
        private String name;
        private java.util.List<Integer> tags;
        public MixedBean() {}
        public MixedBean(String name, java.util.List<Integer> tags) {
            this.name = name;
            this.tags = tags;
        }
        public String getName() { return name; }
        public java.util.List<Integer> getTags() { return tags; }
    }

    @CompiledValidator(fallback=true)
    public interface MixedValidator {
        @ValidJsonSchema("{\"type\":\"object\",\"properties\":{"
                + "\"name\":{\"type\":\"string\",\"minLength\":2},"
                + "\"tags\":{\"type\":\"array\",\"uniqueItems\":true}}}")
        boolean test(MixedBean value);
    }

    @Test
    void generatedSourceContainsDirectGettersAndCompiledUniqueItems() throws Exception {
        java.nio.file.Path root = java.nio.file.Paths.get(
                "build/generated/sources/annotationProcessor/java/test/",
                "org/sjf4j/testbench/processor/validation/");
        String mixed = java.nio.file.Files.readString(root.resolve(
                "CompiledValidatorFoundationTest$MixedValidator_Impl.java"));
        assertTrue(mixed.contains("value.getName()"), "direct getter must be emitted");
        assertTrue(mixed.contains("CompiledUtil.hasDuplicateItems("),
                "uniqueItems must use shared JSON equality directly");
        assertFalse(mixed.contains("SchemaFallbacks.SchemaPlan.SUBSCHEMA"),
                "direct uniqueItems must not create a cached SchemaPlan");
        assertFalse(mixed.contains("new String[]{"), "JSON Schema documents must not be duplicated");
    }

    @ValidJsonSchema
    public static final class HTTPRequest {
        private String url;
        public HTTPRequest() {}
        public HTTPRequest(String url) { this.url = url; }
        public String getUrl() { return url; }
    }

    @ValidJsonSchema("""
        {"type":"object","required":["name"],
         "properties":{"name":{"type":"string","minLength":2}}}
        """)
    public static class Person {
        private String name;
        public Person() {}
        public Person(String name) { this.name = name; }
        public String getName() { return name; }
    }

    @ValidJsonSchema("""
        {"type":"object","properties":{"level":{"type":"integer","minimum":0}}}
        """)
    public static final class Employee extends Person {
        private int level;
        public Employee() {}
        public Employee(String name, int level) {
            super(name);
            this.level = level;
        }
        public int getLevel() { return level; }
    }

    public record Payload(String value) {}

    @CompiledValidator(fallback=true)
    public interface PersonValidator {
        boolean isValid(Employee employee);
        ValidationResult validate(Employee employee);
        void requireValid(Employee employee);

        @ValidJsonSchema("""
            {"type":"object","properties":{"name":{"minLength":1}}}
            """)
        boolean methodSchema(Employee employee);

        boolean parameterSchema(
                @ValidJsonSchema("""
                    {"type":"object","properties":{"name":{"minLength":2}}}
                    """) Employee employee);

        @ValidJsonSchema("""
            {"type":"string","minLength":4}
            """)
        boolean text(String text);

        @ValidJsonSchema("""
            {"type":"integer","minimum":1}
            """)
        boolean positive(int value);

        @ValidJsonSchema(ref = "compiled-validator-defs.json#/$defs/text")
        boolean fromFragment(String text);

        @ValidJsonSchema(ref = "compiled-validator-defs.json#/$defs/valid")
        boolean fromPayloadFragment(Payload payload);

        @ValidJsonSchema("false")
        ValidationResult validateInlineFalse(Object value);

        @ValidJsonSchema("true")
        ValidationResult validateInlineTrue(Object value);

        @ValidJsonSchema(ref = "compiled-validator-false.json")
        ValidationResult validateBooleanFalseResource(Object value);

        @ValidJsonSchema(ref = "compiled-validator-true.json")
        ValidationResult validateBooleanTrueResource(Object value);

        @ValidJsonSchema(ref = "compiled-validator-boolean-root.json")
        ValidationResult validateBooleanRef(Object value);

        @ValidJsonSchema(ref = "compiled-validator-false.json")
        void requireBooleanFalse(Object value);

        @ValidJsonSchema(ref = "compiled-validator-true.json")
        void requireBooleanTrue(Object value);

        @ValidJsonSchema(ref = "compiled-validator-boolean-root.json")
        boolean fromBooleanRef(Object value);

        @ValidJsonSchema(ref = "compiled-validator-false.json")
        boolean fromBooleanRoot(Object value);

        @ValidJsonSchema(ref = "compiled-validator-true.json")
        boolean fromBooleanTrue(Object value);

        boolean convention(HTTPRequest request);

        @ValidJsonSchema(ref = "compiled-validator-root.json")
        boolean fromResource(Payload payload);
    }
}
