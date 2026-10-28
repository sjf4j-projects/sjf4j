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

        @ValidJsonSchema(ref = "compiled-validator-root.json")
        boolean fromResource(Payload payload);
    }
}
