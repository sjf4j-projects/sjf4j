package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Only isolated local coverage can compile without evaluated-location propagation. */
class CompiledValidatorStaticUnevaluatedTest {
    private static final String PROPERTIES = """
            {"properties":{"name":{"type":"string"}},"unevaluatedProperties":false}
            """;
    private static final String PATTERNS = """
            {"patternProperties":{"^x":true},"unevaluatedProperties":{"type":"integer"}}
            """;
    private static final String PREFIX = """
            {"prefixItems":[{"type":"integer"}],"unevaluatedItems":false}
            """;
    private static final String SUFFIX_SCHEMA = """
            {"prefixItems":[true],"unevaluatedItems":{"type":"string"}}
            """;

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(PROPERTIES) boolean properties(Object node);
        @ValidJsonSchema(PATTERNS) boolean patterns(Object node);
        @ValidJsonSchema(PREFIX) boolean prefix(Object node);
        @ValidJsonSchema(SUFFIX_SCHEMA) boolean suffix(Object node);
    }

    @Test
    void localPropertyCoverageMatchesRuntime() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan properties = JsonSchema.fromJson(PROPERTIES).createPlan();
        for (Object value : Arrays.asList(
                null, 1, "string",
                Map.of(), Map.of("name", "ok"), Map.of("name", 1),
                Map.of("other", 1), Map.of("name", "ok", "other", 1))) {
            assertEquals(properties.isValid(value), direct.properties(value), String.valueOf(value));
        }
        Map<String, Object> explicitNull = new LinkedHashMap<>();
        explicitNull.put("name", null);
        assertEquals(properties.isValid(explicitNull), direct.properties(explicitNull));
    }

    @Test
    void localPatternCoverageMatchesRuntime() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan patterns = JsonSchema.fromJson(PATTERNS).createPlan();
        for (Object value : List.of(
                Map.of(), Map.of("xName", "anything"), Map.of("other", 2),
                Map.of("other", "bad"), Map.of("xName", false, "other", 12))) {
            assertEquals(patterns.isValid(value), direct.patterns(value), String.valueOf(value));
        }
    }

    @Test
    void suffixCoverageMatchesRuntime() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan prefix = JsonSchema.fromJson(PREFIX).createPlan();
        SchemaPlan suffix = JsonSchema.fromJson(SUFFIX_SCHEMA).createPlan();
        for (Object value : Arrays.asList(
                null, "string", List.of(), List.of(1), List.of("bad"),
                List.of(1, 2), List.of(1, "ok"), List.of(1, "ok", "next"))) {
            assertEquals(prefix.isValid(value), direct.prefix(value), String.valueOf(value));
            assertEquals(suffix.isValid(value), direct.suffix(value), String.valueOf(value));
        }
    }
}
