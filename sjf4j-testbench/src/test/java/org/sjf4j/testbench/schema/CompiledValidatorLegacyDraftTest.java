package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.ValidationException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Draft-specific assertions must match the existing multi-draft runtime planner. */
class CompiledValidatorLegacyDraftTest {
    private static final String DRAFT_07 = "http://json-schema.org/draft-07/schema#";
    private static final String DRAFT_2019 = "https://json-schema.org/draft/2019-09/schema";

    private static final String TUPLE_07 = "{\"$schema\":\"" + DRAFT_07 + "\","
            + "\"items\":[{\"type\":\"integer\"},{\"type\":\"string\"}],\"additionalItems\":false}";
    private static final String TUPLE_2019 = "{\"$schema\":\"" + DRAFT_2019 + "\","
            + "\"items\":[{\"type\":\"integer\"}],\"additionalItems\":{\"type\":\"boolean\"}}";
    private static final String DEPENDENCIES_07 = "{\"$schema\":\"" + DRAFT_07 + "\","
            + "\"dependencies\":{\"a\":[\"b\"],\"b\":{\"properties\":{\"c\":{\"type\":\"integer\"}}}}}";
    private static final String DEPENDENCIES_2019 = "{\"$schema\":\"" + DRAFT_2019 + "\","
            + "\"dependencies\":{\"a\":[\"b\"],\"b\":{\"properties\":{\"c\":{\"type\":\"integer\"}}}},"
            + "\"dependentRequired\":{\"b\":[\"d\"]}}";
    private static final String TUPLE_UNEVALUATED_2019 = "{\"$schema\":\"" + DRAFT_2019 + "\","
            + "\"items\":[true],\"unevaluatedItems\":false}";
    private static final String TUPLE_ADDITIONAL_2019 = "{\"$schema\":\"" + DRAFT_2019 + "\","
            + "\"items\":[true],\"additionalItems\":true,\"unevaluatedItems\":false}";
    private static final String SCHEMA_DEPENDENCIES_UNEVALUATED_2019 =
            "{\"$schema\":\"" + DRAFT_2019 + "\","
            + "\"properties\":{\"a\":true},"
            + "\"dependencies\":{\"a\":{\"properties\":{\"b\":true}}},"
            + "\"unevaluatedProperties\":false}";
    private static final String ARRAY_DEPENDENCIES_UNEVALUATED_2019 =
            "{\"$schema\":\"" + DRAFT_2019 + "\","
            + "\"properties\":{\"a\":true},"
            + "\"dependencies\":{\"a\":[\"b\"]},"
            + "\"unevaluatedProperties\":false}";
    private static final String REF_07 = "{\"$schema\":\"" + DRAFT_07 + "\","
            + "\"definitions\":{\"target\":{\"type\":\"string\"}},"
            + "\"$ref\":\"#/definitions/target\",\"type\":\"integer\"}";
    private static final String REF_2019 = "{\"$schema\":\"" + DRAFT_2019 + "\","
            + "\"definitions\":{\"target\":{\"type\":\"string\"}},"
            + "\"$ref\":\"#/definitions/target\",\"minLength\":3}";

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(TUPLE_07) boolean tuple07(Object value);
        @ValidJsonSchema(TUPLE_2019) boolean tuple2019(Object value);
        @ValidJsonSchema(TUPLE_UNEVALUATED_2019) boolean tupleUnevaluated2019(Object value);
        @ValidJsonSchema(TUPLE_ADDITIONAL_2019) boolean tupleAdditional2019(Object value);
        @ValidJsonSchema(ARRAY_DEPENDENCIES_UNEVALUATED_2019)
        boolean arrayDependenciesUnevaluated2019(Object value);
        @ValidJsonSchema(DEPENDENCIES_07) boolean dependencies07(Object value);
        @ValidJsonSchema(DEPENDENCIES_2019) boolean dependencies2019(Object value);
        @ValidJsonSchema(REF_07) boolean ref07(Object value);
        @ValidJsonSchema(REF_2019) boolean ref2019(Object value);

        @ValidJsonSchema("{\"$schema\":\"" + DRAFT_07
                + "\",\"dependentRequired\":{\"a\":[\"b\"]},\"prefixItems\":[false]}")
        boolean ignoresNewKeywords07(Object value);

        @ValidJsonSchema("{\"$schema\":\"" + DRAFT_07
                + "\",\"contains\":{\"type\":\"integer\"},\"minContains\":0}")
        boolean contains07(Object value);

        @ValidJsonSchema("{\"$schema\":\"" + DRAFT_2019
                + "\",\"contains\":{\"type\":\"integer\"},\"minContains\":0}")
        boolean contains2019(Object value);

        @ValidJsonSchema(DEPENDENCIES_07) void requireDependencies07(Object value);
        @ValidJsonSchema(TUPLE_07) void requireTuple07(Object value);
    }

    @CompiledValidator(fallback = true)
    public interface Fallback {
        @ValidJsonSchema(SCHEMA_DEPENDENCIES_UNEVALUATED_2019)
        boolean schemaDependenciesUnevaluated2019(Object value);
    }

    @Test
    void directValidationMatchesRuntimeForBothLegacyDialects() {
        Direct compiled = CompiledInstances.create(Direct.class);
        assertParity(TUPLE_07, compiled::tuple07,
                List.of(), List.of(1), List.of(1, "two"), List.of(1, "two", 3),
                List.of("bad"), 12, null);
        assertParity(TUPLE_2019, compiled::tuple2019,
                List.of(), List.of(1), List.of(1, true), List.of(1, false, true),
                List.of(1, "wrong"), List.of("wrong"), null);
        assertParity(TUPLE_UNEVALUATED_2019, compiled::tupleUnevaluated2019,
                List.of(), List.of(1), List.of(1, 2), List.of(1, 2, 3), null);
        assertParity(TUPLE_ADDITIONAL_2019, compiled::tupleAdditional2019,
                List.of(), List.of(1), List.of(1, 2), List.of(1, 2, 3), null);
        assertParity(ARRAY_DEPENDENCIES_UNEVALUATED_2019,
                compiled::arrayDependenciesUnevaluated2019,
                Map.of(), Map.of("a", 1), Map.of("a", 1, "b", 2), null);
        assertParity(DEPENDENCIES_07, compiled::dependencies07,
                Map.of(), Map.of("a", 1), Map.of("a", 1, "b", 2),
                Map.of("b", 1, "c", "wrong"), Map.of("a", 1, "b", 2, "c", 5),
                "not an object", null);
        assertParity(DEPENDENCIES_2019, compiled::dependencies2019,
                Map.of(), Map.of("a", 1), Map.of("b", 1), Map.of("b", 1, "d", 2),
                Map.of("b", 1, "c", "wrong", "d", 3),
                Map.of("a", 1, "b", 2, "c", 4, "d", 5), null);
        assertParity(REF_07, compiled::ref07, "x", "hello", 123, null);
        assertParity(REF_2019, compiled::ref2019, "x", "hello", 123, null);
    }

    @Test
    void draftSpecificKeywordApplicabilityAndReferenceSiblings() {
        Direct compiled = CompiledInstances.create(Direct.class);
        assertTrue(compiled.ignoresNewKeywords07(Map.of("a", 1)));
        assertTrue(compiled.ignoresNewKeywords07(List.of(1)));
        assertFalse(compiled.contains07(List.of()));
        assertTrue(compiled.contains2019(List.of()));
        assertTrue(compiled.ref07("x"));
        assertFalse(compiled.ref07(1));
        assertFalse(compiled.ref2019("x"));
        assertTrue(compiled.ref2019("hello"));
    }

    @Test
    void reviewRegressionForLegacyUnevaluatedCoverage() {
        Direct direct = CompiledInstances.create(Direct.class);
        assertTrue(direct.tupleUnevaluated2019(List.of(1)));
        assertFalse(direct.tupleUnevaluated2019(List.of(1, 2)));
        assertTrue(direct.tupleAdditional2019(List.of(1, 2)));
        Fallback fallback = CompiledInstances.create(Fallback.class);
        assertParity(SCHEMA_DEPENDENCIES_UNEVALUATED_2019,
                fallback::schemaDependenciesUnevaluated2019,
                Map.of(), Map.of("a", 1), Map.of("a", 1, "b", 2),
                Map.of("a", 1, "b", 2, "c", 3), null);
        assertTrue(fallback.schemaDependenciesUnevaluated2019(Map.of("a", 1, "b", 2)));
    }

    @Test
    void failurePathsUseOriginalDraftKeywords() {
        Direct compiled = CompiledInstances.create(Direct.class);
        ValidationException dependency = assertThrows(ValidationException.class,
                () -> compiled.requireDependencies07(Map.of("a", 1)));
        assertEquals("/dependencies", dependency.getResult().getLastError()
                .getKeywordPs().rootedPointerExpr());
        ValidationException tuple = assertThrows(ValidationException.class,
                () -> compiled.requireTuple07(List.of("wrong")));
        assertEquals("/items/0/type", tuple.getResult().getLastError()
                .getKeywordPs().rootedPointerExpr());
    }

    private static void assertParity(String schema, java.util.function.Predicate<Object> compiled,
                                     Object... values) {
        var runtime = JsonSchema.fromJson(schema).createPlan();
        for (Object value : values) {
            assertEquals(runtime.isValid(value), compiled.test(value),
                    schema + " instance: " + value);
        }
    }
}
