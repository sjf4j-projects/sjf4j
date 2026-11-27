package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaResourceLimitsTest {
    @Test
    void planningEntryPointsDispatchToSchemaImplementations() {
        PlanningOptions unlimited = PlanningOptions.builder().build();
        PlanningOptions bounded = PlanningOptions.builder().maxSchemas(1).maxSchemaDepth(1).build();
        JsonSchema object = JsonSchema.fromJson("{\"type\":\"integer\"}");
        assertTrue(object.createPlan().isValid(1));
        assertTrue(object.createPlan(null, unlimited).isValid(1));
        assertTrue(object.createPlan(null, bounded).isValid(1));
        assertThrows(NullPointerException.class, () -> object.createPlan(null, null));

        JsonSchema alwaysTrue = BooleanSchema.TRUE;
        JsonSchema alwaysFalse = BooleanSchema.FALSE;
        assertTrue(alwaysTrue.createPlan().isValid(1));
        assertTrue(alwaysTrue.createPlan(null, bounded).isValid(1));
        assertFalse(alwaysFalse.createPlan(null, bounded).isValid(1));
        assertThrows(NullPointerException.class, () -> alwaysTrue.createPlan(null, null));
    }

    @Test
    void planningCountsBooleanEmptyAndUnusedSchemas() {
        JsonSchema schema = JsonSchema.fromJson("{\"$defs\":{\"a\":true,\"b\":false,\"c\":{}}}");
        assertThrows(SchemaException.class, () -> schema.createPlan(null,
                PlanningOptions.builder().maxSchemas(3).build()));
        assertTrue(schema.createPlan(null, PlanningOptions.builder().maxSchemas(4).build()).isValid(1));
        assertThrows(SchemaException.class, () -> schema.createPlan(null,
                PlanningOptions.builder().maxSchemaDepth(1).build()));
        assertTrue(schema.createPlan(null, PlanningOptions.builder().maxSchemaDepth(2).build()).isValid(1));
    }

    @Test
    void planningSharesLimitsAcrossCachedExternalResources() {
        SchemaRegistry registry = new SchemaRegistry();
        URI uri = URI.create("https://example.test/external");
        registry.register(uri, JsonSchema.fromJson("{\"allOf\":[true,{}]}"));
        JsonSchema root = JsonSchema.fromJson("{\"$ref\":\"https://example.test/external\"}");
        assertThrows(SchemaException.class, () -> root.createPlan(registry,
                PlanningOptions.builder().maxSchemas(3).build()));
        assertTrue(root.createPlan(registry,
                PlanningOptions.builder().maxSchemas(4).build()).isValid(1));
        assertTrue(registry.resolve(uri).isValid(1));
    }

    @Test
    void boundedPlanningPreservesNestedResourceMappings() {
        SchemaRegistry registry = new SchemaRegistry();
        URI rootUri = URI.create("https://example.test/root");
        URI childUri = URI.create("https://example.test/child");
        registry.register(rootUri, JsonSchema.fromJson(
                "{\"$defs\":{\"child\":{\"$id\":\"https://example.test/child\",\"type\":\"integer\"}}}"));
        JsonSchema root = JsonSchema.fromJson("{\"$ref\":\"https://example.test/child\"}");
        SchemaPlan plan = root.createPlan(registry, PlanningOptions.builder().maxSchemas(3).build());
        assertTrue(plan.isValid(1));
        assertFalse(plan.isValid("bad"));
        assertThrows(SchemaException.class, () -> root.createPlan(registry,
                PlanningOptions.builder().maxSchemas(2).build()));
        assertTrue(registry.resolve(childUri).isValid(1));
    }

    @Test
    void planningDepthSpansExternalResourcesAndLazyFragments() {
        SchemaRegistry registry = new SchemaRegistry();
        URI a = URI.create("https://example.test/a");
        URI b = URI.create("https://example.test/b");
        registry.index(a, JsonSchema.fromJson("{\"$ref\":\"https://example.test/b#/custom\"}"));
        registry.index(b, JsonSchema.fromJson("{\"custom\":{\"not\":false}}"));
        JsonSchema root = JsonSchema.fromJson("{\"$ref\":\"https://example.test/a\"}");
        // Root, a, b, lazy fragment and its child: five plans in total.
        // b's root is finished before the fragment is built.
        assertTrue(root.createPlan(registry, PlanningOptions.builder()
                .maxSchemas(5).maxSchemaDepth(4).build()).isValid(1));
        assertThrows(SchemaException.class, () -> root.createPlan(registry,
                PlanningOptions.builder().maxSchemaDepth(3).build()));
        assertThrows(SchemaException.class, () -> root.createPlan(registry,
                PlanningOptions.builder().maxSchemas(4).build()));
    }

    @Test
    void sharedPlanningContextBindsSiblingStaticAndDynamicRefs() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.index(URI.create("https://example.test/a"), JsonSchema.fromJson(
                "{\"$ref\":\"https://example.test/b\"}"));
        registry.index(URI.create("https://example.test/b"), JsonSchema.fromJson(
                "{\"$dynamicAnchor\":\"node\",\"type\":\"integer\"}"));
        JsonSchema schema = JsonSchema.fromJson(
                "{\"$ref\":\"https://example.test/a\",\"$dynamicRef\":\"https://example.test/b#node\"}");
        SchemaPlan plan = schema.createPlan(registry, PlanningOptions.builder().maxSchemas(3).build());
        assertTrue(plan.isValid(1));
        assertFalse(plan.isValid("bad"));
    }

    @Test
    void planningCountsLazyPointerTargets() {
        JsonSchema schema = JsonSchema.fromJson("{\"$ref\":\"#/custom\",\"custom\":{\"not\":false}}");
        assertThrows(SchemaException.class, () -> schema.createPlan(null,
                PlanningOptions.builder().maxSchemas(2).build()));
        assertTrue(schema.createPlan(null, PlanningOptions.builder().maxSchemas(3).build()).isValid(1));
    }

    @Test
    void evaluationCountsSchemaAndEvaluatorCalls() {
        SchemaPlan plan = JsonSchema.fromJson("{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}").createPlan();
        Object data = Arrays.asList(1, 2);
        assertTrue(plan.isValid(data, ValidationOptions.builder().maxEvaluations(7).build()));
        ValidationOptions exhausted = ValidationOptions.builder().maxEvaluations(6).build();
        assertFalse(plan.isValid(data, exhausted));
        ValidationResult result = plan.validate(data, exhausted);
        assertFalse(result.isValid());
        assertEquals("maxEvaluations", result.getLastError().getKeyword());
        ValidationException required = assertThrows(ValidationException.class,
                () -> plan.requireValid(data, exhausted));
        assertEquals("maxEvaluations", required.getResult().getLastError().getKeyword());
        SchemaPlan empty = JsonSchema.fromJson("{\"items\":true}").createPlan();
        assertFalse(empty.isValid(data, ValidationOptions.builder().maxEvaluations(3).build()));
        assertTrue(empty.isValid(data, ValidationOptions.builder().maxEvaluations(4).build()));
    }

    @Test
    void evaluationDepthIncludesReferencesAndResetsForSiblings() {
        SchemaPlan refs = JsonSchema.fromJson("{\"$ref\":\"#/$defs/a\",\"$defs\":{\"a\":{\"$ref\":\"#/$defs/b\"},\"b\":true}}").createPlan();
        assertTrue(refs.isValid(1, ValidationOptions.builder().maxEvaluationDepth(3).build()));
        assertFalse(refs.isValid(1, ValidationOptions.builder().maxEvaluationDepth(2).build()));
        SchemaPlan items = JsonSchema.fromJson("{\"items\":true}").createPlan();
        assertTrue(items.isValid(Arrays.asList(1, 2, 3), ValidationOptions.builder().maxEvaluationDepth(2).build()));
        SchemaPlan recursive = JsonSchema.fromJson("{\"properties\":{\"next\":{\"$ref\":\"#\"}}}").createPlan();
        Map<String, Object> cycle = new HashMap<>();
        cycle.put("next", cycle);
        assertFalse(recursive.isValid(cycle, ValidationOptions.builder().maxEvaluationDepth(8).build()));
    }

    @Test
    void maxErrorsCapsOutputWithoutChangingValidityOrBranchSemantics() {
        SchemaPlan plan = JsonSchema.fromJson("{\"items\":{\"type\":\"integer\"}}").createPlan();
        ValidationOptions options = ValidationOptions.builder().maxErrors(2).build();
        ValidationResult invalid = plan.validate(Arrays.asList("a", "b", "c"), options);
        assertFalse(invalid.isValid());
        assertEquals(2, invalid.getErrors().size());
        assertTrue(plan.validate(Arrays.asList(1, 2, 3), options).isValid());
        SchemaPlan probe = JsonSchema.fromJson("{\"anyOf\":[false,{\"type\":\"integer\"}]}").createPlan();
        assertTrue(probe.validate(1, options).isValid());
        assertTrue(probe.validate(1, options).getErrors().isEmpty());
        assertFalse(probe.validate("bad", options).isValid());
    }

    @Test
    void limitErrorsAbortEvenIgnoredBranches() {
        SchemaPlan plan = JsonSchema.fromJson("{\"not\":{\"type\":\"integer\"}}").createPlan();
        ValidationOptions options = ValidationOptions.builder().maxEvaluationDepth(1).build();
        ValidationResult result = plan.validate("text", options);
        assertFalse(result.isValid());
        assertEquals(1, result.count());
        assertEquals("maxEvaluationDepth", result.getLastError().getKeyword());
        assertFalse(plan.isValid("text", options));

        SchemaPlan any = JsonSchema.fromJson("{\"anyOf\":[false,true]}").createPlan();
        ValidationOptions limited = ValidationOptions.builder().maxEvaluations(3).build();
        ValidationResult steps = any.validate(1, limited);
        assertFalse(steps.isValid());
        assertEquals("maxEvaluations", steps.getLastError().getKeyword());
        assertFalse(any.isValid(1, limited));
    }

    @Test
    void terminalErrorTakesPriorityWithinErrorCap() {
        SchemaPlan plan = JsonSchema.fromJson("{\"items\":{\"type\":\"integer\"}}").createPlan();
        ValidationOptions options = ValidationOptions.builder().maxErrors(1).maxEvaluations(4).build();
        ValidationResult result = plan.validate(Arrays.asList("bad", "also bad"), options);
        assertFalse(result.isValid());
        assertEquals(1, result.count());
        assertEquals("maxEvaluations", result.getLastError().getKeyword());
    }

    @Test
    void negativeLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PlanningOptions.builder().maxSchemas(-1));
        assertThrows(IllegalArgumentException.class, () -> PlanningOptions.builder().maxSchemaDepth(-1));
        assertThrows(IllegalArgumentException.class, () -> ValidationOptions.builder().maxEvaluations(-1));
        assertThrows(IllegalArgumentException.class, () -> ValidationOptions.builder().maxEvaluationDepth(-1));
        assertThrows(IllegalArgumentException.class, () -> ValidationOptions.builder().maxErrors(-1));
    }
}
