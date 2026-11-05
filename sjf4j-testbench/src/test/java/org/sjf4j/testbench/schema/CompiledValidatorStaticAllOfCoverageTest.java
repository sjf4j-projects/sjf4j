package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Only compile-time-constant coverage is projected across a simple allOf. */
class CompiledValidatorStaticAllOfCoverageTest {
    private static final String PROPERTIES = """
            {"properties":{"foo":{"type":"string"}},
             "allOf":[{"properties":{"bar":{"type":"string"}}}],
             "unevaluatedProperties":false}
            """;
    private static final String PATTERNS = """
            {"properties":{"foo":{"type":"string"}},
             "allOf":[{"patternProperties":{"^bar":{"type":"string"}}}],
             "unevaluatedProperties":false}
            """;
    private static final String ALL_PROPERTIES = """
            {"properties":{"foo":{"type":"string"}},
             "allOf":[{"additionalProperties":true}],
             "unevaluatedProperties":false}
            """;
    private static final String PREFIX = """
            {"prefixItems":[{"type":"string"}],
             "allOf":[{"prefixItems":[true,{"type":"number"}]}],
             "unevaluatedItems":false}
            """;
    private static final String ALL_ITEMS = """
            {"allOf":[{"prefixItems":[{"type":"string"}],"items":true}],
             "unevaluatedItems":false}
            """;

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(PROPERTIES) boolean properties(Object node);
        @ValidJsonSchema(PATTERNS) boolean patterns(Object node);
        @ValidJsonSchema(ALL_PROPERTIES) boolean allProperties(Object node);
        @ValidJsonSchema(PREFIX) boolean prefix(Object node);
        @ValidJsonSchema(ALL_ITEMS) boolean allItems(Object node);
    }

    @Test
    void staticPropertyAndPatternCoverageMatchesRuntime() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan props = JsonSchema.fromJson(PROPERTIES).createPlan();
        SchemaPlan patterns = JsonSchema.fromJson(PATTERNS).createPlan();
        SchemaPlan all = JsonSchema.fromJson(ALL_PROPERTIES).createPlan();
        for (Object node : Arrays.asList(null, true, 42, List.of("not an object"),
                Map.of(), Map.of("foo", "ok"), Map.of("bar", "ok"),
                Map.of("bar", 3), Map.of("foobar", "ok"),
                Map.of("foo", "ok", "bar", "ok"),
                Map.of("foo", "ok", "bar1", "ok"),
                Map.of("foo", "ok", "bar1", 4),
                Map.of("foo", "ok", "extra", true),
                Map.of("foo", 3, "bar", "ok"))) {
            assertEquals(props.isValid(node), direct.properties(node), String.valueOf(node));
            assertEquals(patterns.isValid(node), direct.patterns(node), String.valueOf(node));
            assertEquals(all.isValid(node), direct.allProperties(node), String.valueOf(node));
        }
    }

    @Test
    void staticIndexCoverageMatchesRuntime() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan prefix = JsonSchema.fromJson(PREFIX).createPlan();
        SchemaPlan allItems = JsonSchema.fromJson(ALL_ITEMS).createPlan();
        for (Object node : Arrays.asList(null, 1, Map.of(), List.of(), List.of("foo"),
                List.of(2), List.of("foo", 2), List.of("foo", 2.5),
                List.of("foo", "bad"), List.of("foo", 2, "extra"),
                List.of("foo", 2, 3), List.of("foo", 2, 3, 4),
                List.of("bad", 1))) {
            assertEquals(prefix.isValid(node), direct.prefix(node), String.valueOf(node));
            assertEquals(allItems.isValid(node), direct.allItems(node), String.valueOf(node));
        }
    }
}
