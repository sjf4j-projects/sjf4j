package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaResourceLimitsTest {
    private static SchemaRegistry registry(int maxSchemas, int maxDepth) {
        return new SchemaRegistry(PlanningOptions.builder()
                .maxSchemas(maxSchemas).maxSchemaDepth(maxDepth).build());
    }

    @Test
    void planningOptionsBelongToRegistry() {
        SchemaRegistry bounded = registry(1, 1);
        assertSame(bounded.getPlanningOptions(), SchemaRegistry.copyOf(bounded).getPlanningOptions());
        assertThrows(NullPointerException.class, () -> new SchemaRegistry((PlanningOptions) null));
        JsonSchema object = JsonSchema.fromJson("{\"type\":\"integer\"}");
        assertTrue(object.createPlan().isValid(1));
        assertTrue(object.createPlan(bounded).isValid(1));
        assertTrue(BooleanSchema.TRUE.createPlan(bounded).isValid(1));
        assertFalse(BooleanSchema.FALSE.createPlan(bounded).isValid(1));
    }

    @Test
    void planningCountsBooleanEmptyAndUnusedSchemas() {
        JsonSchema schema = JsonSchema.fromJson("{\"$defs\":{\"a\":true,\"b\":false,\"c\":{}}}");
        assertThrows(SchemaException.class, () -> schema.createPlan(registry(3, 0)));
        assertTrue(schema.createPlan(registry(4, 0)).isValid(1));
        assertThrows(SchemaException.class, () -> schema.createPlan(registry(0, 1)));
        assertTrue(schema.createPlan(registry(0, 2)).isValid(1));
    }

    @Test
    void differentRegistriesRecompileExternalResources() {
        SchemaRegistry source = new SchemaRegistry();
        URI uri = URI.create("https://example.test/external");
        SchemaPlan cached = source.register(uri, JsonSchema.fromJson("{\"allOf\":[true,{}]}"));
        JsonSchema root = JsonSchema.fromJson("{\"$ref\":\"https://example.test/external\"}");
        SchemaRegistry strict = registry(3, 0);
        strict.indexRegistry(source);
        assertThrows(SchemaException.class, () -> root.createPlan(strict));
        SchemaRegistry relaxed = registry(4, 0);
        relaxed.indexRegistry(source);
        assertTrue(root.createPlan(relaxed).isValid(1));
        assertNotSame(cached, relaxed.resolve(uri));
        assertSame(cached, source.resolve(uri));
        assertTrue(root.createPlan(relaxed).isValid(1));
    }

    @Test
    void boundedPlanningPreservesNestedResourceMappings() {
        SchemaRegistry source = new SchemaRegistry();
        URI rootUri = URI.create("https://example.test/root");
        URI childUri = URI.create("https://example.test/child");
        source.register(rootUri, JsonSchema.fromJson(
                "{\"$defs\":{\"child\":{\"$id\":\"https://example.test/child\",\"type\":\"integer\"}}}"));
        JsonSchema root = JsonSchema.fromJson("{\"$ref\":\"https://example.test/child\"}");
        SchemaRegistry bounded = registry(3, 0);
        bounded.indexRegistry(source);
        SchemaPlan plan = root.createPlan(bounded);
        assertTrue(plan.isValid(1));
        assertFalse(plan.isValid("bad"));
        SchemaRegistry strict = registry(2, 0);
        strict.indexRegistry(source);
        assertThrows(SchemaException.class, () -> root.createPlan(strict));
        assertTrue(source.resolve(childUri).isValid(1));
    }

    @Test
    void planningDepthSpansExternalResourcesAndLazyFragments() {
        URI a = URI.create("https://example.test/a");
        URI b = URI.create("https://example.test/b");
        SchemaRegistry source = new SchemaRegistry();
        source.index(a, JsonSchema.fromJson("{\"$ref\":\"https://example.test/b#/custom\"}"));
        source.index(b, JsonSchema.fromJson("{\"custom\":{\"not\":false}}"));
        JsonSchema root = JsonSchema.fromJson("{\"$ref\":\"https://example.test/a\"}");
        SchemaRegistry enough = registry(5, 4);
        enough.indexRegistry(source);
        assertTrue(root.createPlan(enough).isValid(1));
        SchemaRegistry shallow = registry(0, 3);
        shallow.indexRegistry(source);
        assertThrows(SchemaException.class, () -> root.createPlan(shallow));
        SchemaRegistry small = registry(4, 0);
        small.indexRegistry(source);
        assertThrows(SchemaException.class, () -> root.createPlan(small));
    }

    @Test
    void sharedPlanningContextBindsSiblingStaticAndDynamicRefs() {
        SchemaRegistry registry = registry(3, 0);
        registry.index(URI.create("https://example.test/a"), JsonSchema.fromJson(
                "{\"$ref\":\"https://example.test/b\"}"));
        registry.index(URI.create("https://example.test/b"), JsonSchema.fromJson(
                "{\"$dynamicAnchor\":\"node\",\"type\":\"integer\"}"));
        JsonSchema schema = JsonSchema.fromJson(
                "{\"$ref\":\"https://example.test/a\",\"$dynamicRef\":\"https://example.test/b#node\"}");
        SchemaPlan plan = schema.createPlan(registry);
        assertTrue(plan.isValid(1));
        assertFalse(plan.isValid("bad"));
    }

    @Test
    void planningCountsLazyPointerTargets() {
        JsonSchema schema = JsonSchema.fromJson("{\"$ref\":\"#/custom\",\"custom\":{\"not\":false}}");
        assertThrows(SchemaException.class, () -> schema.createPlan(registry(2, 0)));
        assertTrue(schema.createPlan(registry(3, 0)).isValid(1));
    }

    @Test
    void registryRegisterAndResolveSharePolicyAndDiscardFailedPlans() {
        SchemaRegistry registry = registry(2, 0);
        URI root = URI.create("https://example.test/root");
        URI external = URI.create("https://example.test/external");
        registry.index(external, JsonSchema.fromJson("{\"not\":false}"));
        JsonSchema schema = JsonSchema.fromJson("{\"$ref\":\"https://example.test/external\"}");
        assertThrows(SchemaException.class, () -> registry.register(root, schema));
        assertNull(registry.resolveBuilt(root));
        assertNull(registry.resolveBuilt(external));
        assertThrows(SchemaException.class, () -> registry.resolve(root));
        assertTrue(registry.resolve(external).isValid(1));
        assertTrue(registry.resolve(root).isValid(1));
    }

    @Test
    void failedLazyPointerDoesNotPolluteCachedRoot() {
        SchemaRegistry registry = registry(2, 0);
        URI root = URI.create("https://example.test/root");
        URI fragment = URI.create(root + "#/custom");
        registry.register(root, JsonSchema.fromJson(
                "{\"custom\":{\"$defs\":{\"a\":true,\"b\":false}}}"));
        SchemaPlan plan = registry.resolve(root);
        assertThrows(SchemaException.class, () -> registry.resolve(fragment));
        assertFalse(plan.byPathPlans.containsKey("/custom"));
        assertFalse(plan.byPathPlans.containsKey("/custom/$defs/a"));
        assertThrows(SchemaException.class, () -> registry.resolve(fragment));
    }

    @Test
    void stagedLazyFragmentsAreVisibleToRefsAndCommittedOnlyOnSuccess() {
        SchemaRegistry registry = registry(20, 0);
        URI root = URI.create("https://example.test/staged");
        registry.register(root, JsonSchema.fromJson("{\"first\":{\"$anchor\":\"first\",\"$ref\":\"#/second\"},"
                + "\"second\":{\"$anchor\":\"second\",\"type\":\"integer\",\"allOf\":[{\"$ref\":\"#first\"}]}}"));
        SchemaPlan original = registry.resolve(root);
        SchemaPlan first = registry.resolve(URI.create(root + "#/first"));
        assertSame(first, original.byAnchorPlans.get("first"));
        assertSame(first, original.byPathPlans.get("/first"));
        assertSame(first, registry.resolve(URI.create(root + "#first")));
        assertNotNull(original.byAnchorPlans.get("second"));
        assertNotNull(original.byPathPlans.get("/second"));
    }

    @Test
    void failedLazyRefsDoNotPublishAnchorsOrNestedResources() {
        SchemaRegistry registry = registry(20, 0);
        URI root = URI.create("https://example.test/staged");
        URI child = URI.create("https://example.test/child");
        registry.register(root, JsonSchema.fromJson("{\"custom\":{\"$dynamicAnchor\":\"custom\","
                + "\"$defs\":{\"child\":{\"$id\":\"https://example.test/child\",\"type\":\"integer\"}},"
                + "\"$ref\":\"https://example.test/missing\"}}"));
        SchemaPlan original = registry.resolve(root);
        assertThrows(SchemaException.class, () -> registry.resolve(URI.create(root + "#/custom")));
        assertNull(original.byAnchorPlans.get("custom"));
        assertNull(original.byDynamicAnchorPlans.get("custom"));
        assertNull(original.byPathPlans.get("/custom"));
        assertNull(registry.resolveBuilt(child));
        registry.register(URI.create("https://example.test/missing"), JsonSchema.fromJson("true"));
        SchemaPlan custom = registry.resolve(URI.create(root + "#/custom"));
        assertSame(custom, original.byDynamicAnchorPlans.get("custom"));
        assertNotNull(registry.resolveBuilt(child));
        assertTrue(custom.isValid(1));
    }

    @Test
    void boundedRegistryDoesNotBorrowGlobalPlans() {
        URI meta = URI.create("https://json-schema.org/draft/2020-12/schema");
        assertNotNull(SchemaRegistry.globalResolve(meta));
        SchemaRegistry bounded = registry(1, 0);
        assertThrows(SchemaException.class, () -> bounded.resolve(meta));
        assertNull(bounded.resolveBuilt(meta));
    }

    @Test
    void isValidOptionsAlwaysUseBooleanOnlySemantics() {
        ValidationOptions options = ValidationOptions.builder()
                .failFast(false).collectAnnotations(true).maxErrors(1)
                .maxEvaluations(1).maxEvaluationDepth(1).build();
        assertTrue(BooleanSchema.TRUE.createPlan().isValid(null, options));
        assertFalse(BooleanSchema.FALSE.createPlan().isValid(null, options));
        SchemaPlan plan = JsonSchema.fromJson("{\"type\":\"integer\"}").createPlan();
        assertFalse(plan.isValid(1, options));
        ValidationOptions enough = ValidationOptions.builder().maxEvaluations(2)
                .collectAnnotations(true).build();
        assertTrue(plan.isValid(1, enough));
        assertFalse(plan.isValid("bad", enough));
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
