package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompiledValidatorDynamicBaseTest {
    private static final String SCHEMA = """
            {"oneOf":[
              {"type":"object","required":["name"],"properties":{"name":{"type":"string","minLength":2}}},
              {"type":"object","required":["age"],"properties":{"age":{"type":"integer","minimum":18}}}
            ]}
            """;

    public interface Base { }

    public static final class Named implements Base {
        public String getName() { return "Alice"; }
    }

    public static final class Aged implements Base {
        public int getAge() { return 20; }
    }

    public static final class Both implements Base {
        public String getName() { return "Alice"; }
        public int getAge() { return 20; }
    }

    public static class OpenBase {
        public String getName() { return "Alice"; }
    }

    public static final class Extended extends OpenBase {
        public int getAge() { return 20; }
    }

    @CompiledValidator
    public interface Validator {
        @ValidJsonSchema(SCHEMA)
        boolean valid(Base value);
        @ValidJsonSchema(SCHEMA)
        boolean valid(OpenBase value);
    }

    @Test
    void genericOneOfWorksWithoutDeclaredPropertiesOrOneOfAnnotation() {
        Validator compiled = CompiledInstances.create(Validator.class);
        SchemaPlan runtime = JsonSchema.fromJson(SCHEMA).createPlan();
        for (Base value : new Base[]{new Named(), new Aged(), new Both()}) {
            assertEquals(runtime.isValid(value), compiled.valid(value));
        }
    }

    @Test
    void nonFinalBaseDoesNotIgnoreRuntimeSubclassMembers() {
        Validator compiled = CompiledInstances.create(Validator.class);
        Extended value = new Extended();
        assertEquals(JsonSchema.fromJson(SCHEMA).createPlan().isValid(value), compiled.valid(value));
    }
}
