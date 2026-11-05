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

    private static final String ITEMS_AND_UNEVALUATED = """
            {"items":{"type":"integer"},"unevaluatedItems":false}
            """;
    private static final String PREFIX_ITEMS_AND_UNEVALUATED = """
            {"prefixItems":[{"type":"string"}],"items":{"type":"integer"},
             "unevaluatedItems":false}
            """;

    private static final String ADDITIONAL_FALSE = """
            {"properties":{"name":{"type":"string"}},
             "additionalProperties":false,"unevaluatedProperties":false}
            """;
    private static final String ADDITIONAL_TRUE = """
            {"properties":{"name":true},
             "additionalProperties":true,"unevaluatedProperties":false}
            """;
    private static final String ADDITIONAL_SCHEMA = """
            {"patternProperties":{"^x":true},
             "additionalProperties":{"type":"integer"},"unevaluatedProperties":false}
            """;

    private static final String TRUE_ALLOF_ITEMS = """
            {"allOf":[true],"unevaluatedItems":false}
            """;
    private static final String TRUE_ALLOF_PROPERTIES = """
            {"properties":{"foo":{"type":"string"}},
             "allOf":[true],"unevaluatedProperties":false}
            """;
    private static final String NOT_ITEMS = """
            {"prefixItems":[{"const":"foo"}],
             "not":{"not":{"prefixItems":[true,{"const":"bar"}]}},
             "unevaluatedItems":false}
            """;
    private static final String NOT_PROPERTIES = """
            {"properties":{"foo":{"type":"string"}},
             "not":{"not":{"properties":{"bar":{"const":"bar"}},"required":["bar"]}},
             "unevaluatedProperties":false}
            """;

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(PROPERTIES) boolean properties(Object node);
        @ValidJsonSchema(PATTERNS) boolean patterns(Object node);
        @ValidJsonSchema(PREFIX) boolean prefix(Object node);
        @ValidJsonSchema(SUFFIX_SCHEMA) boolean suffix(Object node);
        @ValidJsonSchema(ITEMS_AND_UNEVALUATED) boolean items(Object node);
        @ValidJsonSchema(PREFIX_ITEMS_AND_UNEVALUATED) boolean prefixItems(Object node);
        @ValidJsonSchema(ADDITIONAL_FALSE) boolean additionalFalse(Object node);
        @ValidJsonSchema(ADDITIONAL_TRUE) boolean additionalTrue(Object node);
        @ValidJsonSchema(ADDITIONAL_SCHEMA) boolean additionalSchema(Object node);
        @ValidJsonSchema(TRUE_ALLOF_ITEMS) boolean allTrueItems(Object node);
        @ValidJsonSchema(TRUE_ALLOF_PROPERTIES) boolean allTrueProperties(Object node);
        @ValidJsonSchema(NOT_ITEMS) boolean notItems(Object node);
        @ValidJsonSchema(NOT_PROPERTIES) boolean notProperties(Object node);
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
        SchemaPlan items = JsonSchema.fromJson(ITEMS_AND_UNEVALUATED).createPlan();
        SchemaPlan prefixItems = JsonSchema.fromJson(PREFIX_ITEMS_AND_UNEVALUATED).createPlan();
        for (Object value : Arrays.asList(
                null, "string", List.of(), List.of(1), List.of("bad"),
                List.of(1, 2), List.of("ok", 2), List.of("ok", 2, 3),
                List.of("ok", "bad"))) {
            assertEquals(items.isValid(value), direct.items(value), String.valueOf(value));
            assertEquals(prefixItems.isValid(value), direct.prefixItems(value), String.valueOf(value));
        }
    }
    @Test
    void adjacentAdditionalPropertiesMakesUnevaluatedRedundant() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan falsePlan = JsonSchema.fromJson(ADDITIONAL_FALSE).createPlan();
        SchemaPlan truePlan = JsonSchema.fromJson(ADDITIONAL_TRUE).createPlan();
        SchemaPlan schemaPlan = JsonSchema.fromJson(ADDITIONAL_SCHEMA).createPlan();
        for (Object value : Arrays.asList(null, "string", List.of(1),
                Map.of(), Map.of("name", "ok"), Map.of("name", 42),
                Map.of("xKey", "anything"), Map.of("other", 12),
                Map.of("name", "ok", "other", 12), Map.of("other", "bad"),
                Map.of("xKey", true, "other", 99))) {
            assertEquals(falsePlan.isValid(value), direct.additionalFalse(value), String.valueOf(value));
            assertEquals(truePlan.isValid(value), direct.additionalTrue(value), String.valueOf(value));
            assertEquals(schemaPlan.isValid(value), direct.additionalSchema(value), String.valueOf(value));
        }
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("name", null);
        withNull.put("other", null);
        assertEquals(falsePlan.isValid(withNull), direct.additionalFalse(withNull));
        assertEquals(truePlan.isValid(withNull), direct.additionalTrue(withNull));
        assertEquals(schemaPlan.isValid(withNull), direct.additionalSchema(withNull));
    }

    @Test
    void coverageNeutralApplicatorsMatchRuntime() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan allItems = JsonSchema.fromJson(TRUE_ALLOF_ITEMS).createPlan();
        SchemaPlan allProperties = JsonSchema.fromJson(TRUE_ALLOF_PROPERTIES).createPlan();
        SchemaPlan notItems = JsonSchema.fromJson(NOT_ITEMS).createPlan();
        SchemaPlan notProperties = JsonSchema.fromJson(NOT_PROPERTIES).createPlan();
        for (Object value : Arrays.asList(null, 1, List.of(), List.of("foo"),
                List.of("foo", "bar"), List.of("foo", "bad"), List.of("bad"),
                Map.of(), Map.of("foo", "ok"), Map.of("foo", 42),
                Map.of("foo", "ok", "bar", "bar"), Map.of("bar", "bar"))) {
            assertEquals(allItems.isValid(value), direct.allTrueItems(value), String.valueOf(value));
            assertEquals(allProperties.isValid(value), direct.allTrueProperties(value), String.valueOf(value));
            assertEquals(notItems.isValid(value), direct.notItems(value), String.valueOf(value));
            assertEquals(notProperties.isValid(value), direct.notProperties(value), String.valueOf(value));
        }
    }

}
