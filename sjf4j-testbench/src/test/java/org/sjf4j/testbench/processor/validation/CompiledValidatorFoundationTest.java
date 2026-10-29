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
        assertTrue(validator.isValid(null));

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
        assertTrue(validator.text(null));
    }

    @Test
    void primitiveParameterUsesRuntimeFallbackWithoutNullCheck() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        assertTrue(validator.positive(3));
        assertFalse(validator.positive(0));
    }

    @Test
    void referencedSchemaAndItsExternalRefsAreFrozenAtCompileTime() {
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
        assertTrue(validator.fromFragment(null)); // SchemaValidator null handling
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
    void usesRuntimeAcronymSnakeCaseConvention() {
        PersonValidator validator = CompiledInstances.create(PersonValidator.class);
        assertTrue(validator.convention(new HTTPRequest("abcd")));
        assertFalse(validator.convention(new HTTPRequest("x")));
    }


    @Test
    void generatedValidatorActuallyInstallsCompiledPropertiesEvaluator() throws Exception {
        Class<?> implementation = Class.forName(FastPojoValidator.class.getName() + "_Impl");
        Class<?> fallback = Class.forName(implementation.getName() + "$Fallback");
        java.lang.reflect.Field planField = fallback.getDeclaredField("PLAN_0");
        planField.setAccessible(true);
        Object plan = planField.get(null);

        java.lang.reflect.Field evaluators = org.sjf4j.schema.SchemaPlan.class.getDeclaredField("evaluators");
        evaluators.setAccessible(true);
        Object[] chain = (Object[]) evaluators.get(plan);
        assertTrue(java.util.Arrays.stream(chain).anyMatch(
                evaluator -> evaluator.getClass().getSimpleName().equals("CompiledPropertiesEvaluator")),
                "generated validator must install the direct-POJO traversal path");
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
        assertTrue(compiled.test(null));
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

    @CompiledValidator
    public interface FastPojoValidator {
        @ValidJsonSchema(FAST_SCHEMA)
        boolean test(FastPojo input);
    }


    // The generated _Impl.java should contain: String _property0 = input.getName();
    // followed by a direct codePointCount/minLength/maxLength check.
    @Test
    void directStringValidationMatchesRuntime() {
        DirectValidator validator = CompiledInstances.create(DirectValidator.class);
        assertTrue(validator.valid(new DirectBean("Alice")));
        assertFalse(validator.valid(new DirectBean("x")));
        assertFalse(validator.valid(new DirectBean(null)));
        assertTrue(validator.valid(null));
    }

    public static final class DirectBean {
        private String name;
        public DirectBean() {}
        public DirectBean(String name) { this.name = name; }
        public String getName() { return name; }
    }

    @CompiledValidator
    public interface DirectValidator {
        @ValidJsonSchema("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":2,\"maxLength\":10}}}")
        boolean valid(DirectBean input);
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

    @CompiledValidator
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
