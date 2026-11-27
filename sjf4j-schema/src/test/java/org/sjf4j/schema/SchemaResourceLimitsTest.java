package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaResourceLimitsTest {
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
        assertThrows(ValidationException.class, () -> plan.isValid(data,
                ValidationOptions.builder().maxEvaluations(6).build()));
        assertThrows(ValidationException.class, () -> plan.validate(data,
                ValidationOptions.builder().maxEvaluations(6).build()));
        SchemaPlan empty = JsonSchema.fromJson("{\"items\":true}").createPlan();
        assertThrows(ValidationException.class, () -> empty.isValid(data,
                ValidationOptions.builder().maxEvaluations(3).build()));
        assertTrue(empty.isValid(data, ValidationOptions.builder().maxEvaluations(4).build()));
    }

    @Test
    void evaluationDepthIncludesReferencesAndResetsForSiblings() {
        SchemaPlan refs = JsonSchema.fromJson("{\"$ref\":\"#/$defs/a\",\"$defs\":{\"a\":{\"$ref\":\"#/$defs/b\"},\"b\":true}}").createPlan();
        assertTrue(refs.isValid(1, ValidationOptions.builder().maxEvaluationDepth(3).build()));
        assertThrows(ValidationException.class, () -> refs.isValid(1,
                ValidationOptions.builder().maxEvaluationDepth(2).build()));
        SchemaPlan items = JsonSchema.fromJson("{\"items\":true}").createPlan();
        assertTrue(items.isValid(Arrays.asList(1, 2, 3), ValidationOptions.builder().maxEvaluationDepth(2).build()));
        SchemaPlan recursive = JsonSchema.fromJson("{\"properties\":{\"next\":{\"$ref\":\"#\"}}}").createPlan();
        Map<String, Object> cycle = new HashMap<>();
        cycle.put("next", cycle);
        assertThrows(ValidationException.class, () -> recursive.isValid(cycle,
                ValidationOptions.builder().maxEvaluationDepth(8).build()));
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
        ValidationException e = assertThrows(ValidationException.class, () -> plan.validate("text", options));
        assertFalse(e.getResult().isValid());
        assertEquals(1, e.getResult().count());
        assertEquals("maxEvaluationDepth", e.getResult().getErrors().get(0).getKeyword());
        ValidationException bool = assertThrows(ValidationException.class, () -> plan.isValid("text", options));
        assertEquals("maxEvaluationDepth", bool.getResult().getErrors().get(0).getKeyword());

        SchemaPlan any = JsonSchema.fromJson("{\"anyOf\":[false,true]}").createPlan();
        ValidationException steps = assertThrows(ValidationException.class, () -> any.validate(1,
                ValidationOptions.builder().maxEvaluations(3).build()));
        assertEquals("maxEvaluations", steps.getResult().getErrors().get(0).getKeyword());
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
