package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompiledValidatorDependentRequiredTest {
    private static final String SCHEMA = """
            {"dependentRequired":{"a":["b","c"],"b":["d"]}}
            """;

    @CompiledValidator
    public interface Validator {
        @ValidJsonSchema(SCHEMA)
        boolean valid(Map<String, Object> value);
    }

    public static final class Model {
        public String getA() { return null; }
        public String getB() { return null; }
        public String getC() { return null; }
    }

    @CompiledValidator
    public interface PojoValidator {
        @ValidJsonSchema("{\"type\":\"object\",\"dependentRequired\":{\"a\":[\"b\",\"c\"]}}")
        boolean valid(Model value);
    }

    @Test
    void matchesRuntimeForNullValuesAndMultipleDependencies() {
        Validator compiled = CompiledInstances.create(Validator.class);
        SchemaPlan runtime = JsonSchema.fromJson(SCHEMA).createPlan();
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : new String[]{"a", "b", "c", "d"}) {
            assertEquals(runtime.isValid(map), compiled.valid(map), map.toString());
            map.put(key, null);
        }
        assertEquals(runtime.isValid(map), compiled.valid(map));
        map.remove("d");
        assertEquals(runtime.isValid(map), compiled.valid(map));
        map.remove("a");
        assertEquals(runtime.isValid(map), compiled.valid(map));
    }

    @Test
    void knownPojoPropertiesArePresentEvenWhenNull() {
        PojoValidator compiled = CompiledInstances.create(PojoValidator.class);
        Model model = new Model();
        String schema = "{\"type\":\"object\",\"dependentRequired\":{\"a\":[\"b\",\"c\"]}}";
        assertEquals(JsonSchema.fromJson(schema).createPlan().isValid(model),
                compiled.valid(model));
    }

    @Test
    void honorsDraft07KeywordInapplicability() {
        String draft07 = "{\"$schema\":\"http://json-schema.org/draft-07/schema#\","
                + "\"dependentRequired\":{\"a\":[\"b\"]}}";
        String latest = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
                + "\"dependentRequired\":{\"a\":[\"b\"]}}";
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("a", 1);
        assertEquals(true, JsonSchema.fromJson(draft07).createPlan().isValid(input));
        assertEquals(false, JsonSchema.fromJson(latest).createPlan().isValid(input));
    }
}
