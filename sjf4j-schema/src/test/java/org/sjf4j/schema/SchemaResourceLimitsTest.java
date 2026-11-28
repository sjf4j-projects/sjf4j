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
        assertEquals(1, bounded.getPlanningOptions().getMaxSchemas());
        assertThrows(NullPointerException.class, () -> new SchemaRegistry((PlanningOptions) null));
        JsonSchema object = JsonSchema.fromJson("{\"type\":\"integer\"}");
        assertTrue(object.formulate().isValid(1));
        assertTrue(object.formulate(bounded).isValid(1));
        assertTrue(BooleanSchema.TRUE.formulate(bounded).isValid(1));
        assertFalse(BooleanSchema.FALSE.formulate(bounded).isValid(1));
    }

    @Test
    void createPlanDoesNotRegisterOrMutatePublishedResources() {
        SchemaRegistry registry = new SchemaRegistry();
        URI resource = URI.create("https://example.test/resource");
        SchemaPlan cached = registry.register(resource, JsonSchema.fromJson(
                "{\"$defs\":{\"value\":{\"$dynamicAnchor\":\"node\",\"type\":\"integer\"}}}"));
        JsonSchema schema = JsonSchema.fromJson("{\"$id\":\"https://example.test/temporary\","
                + "\"$ref\":\"https://example.test/resource#/$defs/value\"}");
        assertTrue(schema.formulate(registry).isValid(1));
        assertFalse(schema.formulate(registry).isValid("bad"));
        assertFalse(registry.contains("https://example.test/temporary"));
        assertSame(cached, registry.resolve(resource));
        assertSame(cached.byPathPlans.get("/$defs/value"), registry.resolve(URI.create(resource + "#node")));

        URI uncached = URI.create("https://example.test/uncached");
        registry.index(uncached, JsonSchema.fromJson("{\"type\":\"integer\"}"));
        assertTrue(JsonSchema.fromJson("{\"$ref\":\"https://example.test/uncached\"}")
                .formulate(registry).isValid(1));
        assertNull(registry.resolveBuilt(uncached));
    }

    @Test
    void planningCountsBooleanEmptyAndUnusedSchemas() {
        JsonSchema schema = JsonSchema.fromJson("{\"$defs\":{\"a\":true,\"b\":false,\"c\":{}}}");
        assertThrows(SchemaException.class, () -> schema.formulate(registry(3, 0)));
        assertTrue(schema.formulate(registry(4, 0)).isValid(1));
        assertThrows(SchemaException.class, () -> schema.formulate(registry(0, 1)));
        assertTrue(schema.formulate(registry(0, 2)).isValid(1));
    }

    @Test
    void differentRegistriesRecompileExternalResources() {
        SchemaRegistry source = new SchemaRegistry();
        URI uri = URI.create("https://example.test/external");
        SchemaPlan cached = source.register(uri, JsonSchema.fromJson("{\"allOf\":[true,{}]}"));
        JsonSchema root = JsonSchema.fromJson("{\"$ref\":\"https://example.test/external\"}");
        SchemaRegistry strict = registry(3, 0);
        strict.indexRegistry(source);
        assertThrows(SchemaException.class, () -> root.formulate(strict));
        SchemaRegistry relaxed = registry(4, 0);
        relaxed.indexRegistry(source);
        assertTrue(root.formulate(relaxed).isValid(1));
        assertNotSame(cached, relaxed.resolve(uri));
        assertSame(cached, source.resolve(uri));
        assertTrue(root.formulate(relaxed).isValid(1));
        SchemaPlan local = relaxed.resolve(uri);
        assertSame(local, ((Evaluator.RefEvaluator) root.formulate(relaxed).evaluators[0]).plan);
    }

    @Test
    void nestedResourcesAreReusedAfterTheirOwningDocumentIsRegistered() {
        SchemaRegistry source = new SchemaRegistry();
        URI rootUri = URI.create("https://example.test/root");
        URI childUri = URI.create("https://example.test/child");
        source.register(rootUri, JsonSchema.fromJson(
                "{\"$defs\":{\"child\":{\"$id\":\"https://example.test/child\",\"type\":\"integer\"}}}"));
        JsonSchema root = JsonSchema.fromJson("{\"$ref\":\"https://example.test/child\"}");
        SchemaRegistry bounded = registry(2, 0);
        bounded.indexRegistry(source);
        assertNull(bounded.resolve(childUri));
        assertThrows(SchemaException.class, () -> root.formulate(bounded));
        SchemaPlan owner = bounded.resolve(rootUri);
        SchemaPlan child = bounded.resolve(childUri);
        assertSame(owner.byPathPlans.get("/$defs/child"), child);
        assertNotSame(source.resolve(childUri), child);
        SchemaPlan plan = root.formulate(bounded);
        assertTrue(plan.isValid(1));
        assertFalse(plan.isValid("bad"));
        assertSame(child, ((Evaluator.RefEvaluator) plan.evaluators[0]).plan);
    }

    @Test
    void planningDepthSpansExternalResourcesAndSubschemas() {
        URI a = URI.create("https://example.test/a");
        URI b = URI.create("https://example.test/b");
        SchemaRegistry source = new SchemaRegistry();
        source.index(a, JsonSchema.fromJson("{\"$ref\":\"https://example.test/b#/$defs/target\"}"));
        source.index(b, JsonSchema.fromJson("{\"$defs\":{\"target\":{\"not\":false}}}"));
        JsonSchema root = JsonSchema.fromJson("{\"$ref\":\"https://example.test/a\"}");
        SchemaRegistry enough = registry(5, 5);
        enough.indexRegistry(source);
        assertTrue(root.formulate(enough).isValid(1));
        SchemaRegistry shallow = registry(0, 4);
        shallow.indexRegistry(source);
        assertThrows(SchemaException.class, () -> root.formulate(shallow));
        SchemaRegistry small = registry(4, 0);
        small.indexRegistry(source);
        assertThrows(SchemaException.class, () -> root.formulate(small));
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
        SchemaPlan plan = schema.formulate(registry);
        assertTrue(plan.isValid(1));
        assertFalse(plan.isValid("bad"));
    }

    @Test
    void referencesToUnknownKeywordValuesAreRejected() {
        for (String keyword : new String[]{"$ref", "$dynamicRef"}) {
            JsonSchema schema = JsonSchema.fromJson("{\"" + keyword + "\":\"#/custom\","
                    + "\"custom\":{\"not\":false}}");
            SchemaException failure = assertThrows(SchemaException.class, schema::formulate);
            assertTrue(failure.getMessage().contains("cannot resolve schema fragment '#/custom'"));
        }
        JsonSchema known = JsonSchema.fromJson("{\"$ref\":\"#/$defs/target\","
                + "\"$defs\":{\"target\":{\"not\":false}}}");
        assertThrows(SchemaException.class, () -> known.formulate(registry(2, 0)));
        assertTrue(known.formulate(registry(3, 0)).isValid(1));
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
        SchemaPlan cached = registry.resolve(external);
        assertTrue(cached.isValid(1));
        SchemaPlan resolved = registry.resolve(root);
        assertTrue(resolved.isValid(1));
        assertSame(cached, ((Evaluator.RefEvaluator) resolved.evaluators[0]).plan);
    }

    @Test
    void registryRejectsUnknownFragmentsBeforeAndAfterPublication() {
        SchemaRegistry registry = new SchemaRegistry();
        URI root = URI.create("https://example.test/root");
        URI fragment = URI.create(root + "#/custom");
        URI child = URI.create("https://example.test/child");
        registry.index(root, JsonSchema.fromJson(
                "{\"custom\":{\"$id\":\"https://example.test/child\",\"$anchor\":\"hidden\","
                        + "\"type\":\"integer\"},\"$defs\":{\"known\":{\"type\":\"string\"}}}"));
        SchemaException first = assertThrows(SchemaException.class, () -> registry.resolve(fragment));
        assertTrue(first.getMessage().contains("cannot resolve schema fragment '#/custom'"));
        assertNull(registry.resolveBuilt(root));

        SchemaPlan plan = registry.resolve(root);
        assertThrows(SchemaException.class, () -> registry.resolve(fragment));
        assertNull(registry.resolveBuilt(fragment));
        assertThrows(SchemaException.class, () -> registry.resolve(URI.create(root + "#hidden")));
        assertNull(registry.resolve(child));
        assertFalse(plan.byPathPlans.containsKey("/custom"));
        assertSame(plan, registry.resolve(root));
        assertTrue(registry.resolve(URI.create(root + "#/$defs/known")).isValid("text"));
    }

    @Test
    void globalAliasIsCachedInTheResolvingRegistry() {
        SchemaRegistry registry = new SchemaRegistry();
        URI alias = URI.create("https://json-schema.org/draft-07/schema");
        SchemaPlan plan = registry.resolve(alias);
        assertNotNull(plan);
        assertSame(plan, registry.resolveBuilt(alias));
        assertSame(plan, registry.resolve(alias));
    }

    @Test
    void indexedRootCannotReplacePreviouslyRegisteredNestedResource() {
        SchemaRegistry registry = new SchemaRegistry();
        URI root = URI.create("https://example.test/root");
        URI child = URI.create("https://example.test/child");
        registry.register(root, JsonSchema.fromJson(
                "{\"$defs\":{\"child\":{\"$id\":\"https://example.test/child\",\"type\":\"integer\"}}}"));
        SchemaPlan plan = registry.resolve(child);
        assertThrows(SchemaException.class, () -> registry.index(child, JsonSchema.fromJson("{\"type\":\"string\"}")));
        assertThrows(SchemaException.class, () -> registry.index(child, BooleanSchema.TRUE));
        assertSame(plan, registry.resolve(child));
    }

    @Test
    void nestedIdConflictsBetweenDifferentDocumentsAreRejected() {
        SchemaRegistry registry = new SchemaRegistry();
        URI first = URI.create("https://example.test/first");
        URI second = URI.create("https://example.test/second");
        URI child = URI.create("https://example.test/child");
        String schema = "{\"$defs\":{\"child\":{\"$id\":\"https://example.test/child\",\"type\":\"integer\"}}}";
        registry.register(first, JsonSchema.fromJson(schema));
        SchemaPlan existing = registry.resolve(child);
        assertThrows(SchemaException.class, () -> registry.register(second, JsonSchema.fromJson(schema)));
        assertSame(existing, registry.resolve(child));
        assertNull(registry.resolveBuilt(second));
    }

    @Test
    void cachedReferencesDoNotConsumePlanningDepthOrSchemaBudget() {
        SchemaRegistry registry = registry(2, 2);
        URI dependency = URI.create("https://example.test/dependency");
        SchemaPlan cached = registry.register(dependency, JsonSchema.fromJson("{\"not\":false}"));
        SchemaPlan root = JsonSchema.fromJson("{\"$ref\":\"https://example.test/dependency\"}")
                .formulate(registry);
        assertSame(cached, ((Evaluator.RefEvaluator) root.evaluators[0]).plan);
        assertTrue(root.isValid(1));
    }

    @Test
    void boundedRegistryDoesNotBorrowGlobalPlans() {
        URI meta = URI.create("https://json-schema.org/draft/2020-12/schema");
        SchemaPlan global = new SchemaRegistry().resolve(meta);
        assertNotNull(global);
        assertNotSame(global, new SchemaRegistry().resolve(meta));
        SchemaRegistry bounded = registry(1, 0);
        assertThrows(SchemaException.class, () -> bounded.resolve(meta));
        assertNull(bounded.resolveBuilt(meta));
    }

    @Test
    void booleanSchemaFastPathsPreserveValidationResults() {
        ValidationOptions options = ValidationOptions.builder()
                .collectAnnotations(true).maxEvaluations(1)
                .maxEvaluationDepth(1).maxErrors(1).build();
        SchemaPlan accepted = BooleanSchema.TRUE.formulate();
        SchemaPlan rejected = BooleanSchema.FALSE.formulate();
        assertSame(ValidationResult.SUCCESS, accepted.validate(null, options));
        assertDoesNotThrow(() -> accepted.requireValid(null, options));
        ValidationResult result = rejected.validate(null, options);
        assertFalse(result.isValid());
        assertEquals("false", result.getLastError().getKeyword());
        assertTrue(result.getAnnotations().isEmpty());
        ValidationException failure = assertThrows(ValidationException.class,
                () -> rejected.requireValid(null, options));
        assertEquals("false", failure.getResult().getLastError().getKeyword());
    }

    @Test
    void booleanOnlyDoesNotOverrideFailFast() {
        for (boolean failFast : new boolean[]{false, true}) {
            ValidationOptions options = ValidationOptions.builder().failFast(failFast).build();
            for (boolean booleanOnly : new boolean[]{false, true}) {
                ValidationContext ctx = new ValidationContext(options, booleanOnly);
                assertEquals(failFast, ctx.isFailFast());
                assertEquals(booleanOnly, ctx.isBooleanOnly());
            }
        }
    }

    @Test
    void isValidOptionsAlwaysUseBooleanOnlySemantics() {
        ValidationOptions options = ValidationOptions.builder()
                .failFast(false).collectAnnotations(true).maxErrors(1)
                .maxEvaluations(1).maxEvaluationDepth(1).build();
        assertTrue(BooleanSchema.TRUE.formulate().isValid(null, options));
        assertFalse(BooleanSchema.FALSE.formulate().isValid(null, options));
        SchemaPlan plan = JsonSchema.fromJson("{\"type\":\"integer\"}").formulate();
        assertFalse(plan.isValid(1, options));
        ValidationOptions enough = ValidationOptions.builder().maxEvaluations(2)
                .collectAnnotations(true).build();
        assertTrue(plan.isValid(1, enough));
        assertFalse(plan.isValid("bad", enough));
    }

    @Test
    void evaluationCountsSchemaAndEvaluatorCalls() {
        SchemaPlan plan = JsonSchema.fromJson("{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}").formulate();
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
        SchemaPlan empty = JsonSchema.fromJson("{\"items\":true}").formulate();
        assertFalse(empty.isValid(data, ValidationOptions.builder().maxEvaluations(3).build()));
        assertTrue(empty.isValid(data, ValidationOptions.builder().maxEvaluations(4).build()));
    }

    @Test
    void evaluationDepthIncludesReferencesAndResetsForSiblings() {
        SchemaPlan refs = JsonSchema.fromJson("{\"$ref\":\"#/$defs/a\",\"$defs\":{\"a\":{\"$ref\":\"#/$defs/b\"},\"b\":true}}").formulate();
        assertTrue(refs.isValid(1, ValidationOptions.builder().maxEvaluationDepth(3).build()));
        assertFalse(refs.isValid(1, ValidationOptions.builder().maxEvaluationDepth(2).build()));
        SchemaPlan items = JsonSchema.fromJson("{\"items\":true}").formulate();
        assertTrue(items.isValid(Arrays.asList(1, 2, 3), ValidationOptions.builder().maxEvaluationDepth(2).build()));
        SchemaPlan recursive = JsonSchema.fromJson("{\"properties\":{\"next\":{\"$ref\":\"#\"}}}").formulate();
        Map<String, Object> cycle = new HashMap<>();
        cycle.put("next", cycle);
        assertFalse(recursive.isValid(cycle, ValidationOptions.builder().maxEvaluationDepth(8).build()));
    }

    @Test
    void maxErrorsStopsValidationWithoutCountingProbeFailures() {
        SchemaPlan plan = JsonSchema.fromJson("{\"items\":{\"type\":\"integer\"}}").formulate();
        ValidationOptions options = ValidationOptions.builder().maxErrors(2).build();
        ValidationResult invalid = plan.validate(Arrays.asList("a", "b", "c"), options);
        assertFalse(invalid.isValid());
        assertEquals(2, invalid.getErrors().size());
        assertTrue(plan.validate(Arrays.asList(1, 2, 3), options).isValid());
        SchemaPlan probe = JsonSchema.fromJson("{\"anyOf\":[false,{\"type\":\"integer\"}]}").formulate();
        options = ValidationOptions.builder().maxErrors(1).build();
        assertTrue(probe.validate(1, options).isValid());
        assertTrue(probe.validate(1, options).getErrors().isEmpty());
        assertFalse(probe.validate("bad", options).isValid());
    }

    @Test
    void limitErrorsAbortEvenIgnoredBranches() {
        SchemaPlan plan = JsonSchema.fromJson("{\"not\":{\"type\":\"integer\"}}").formulate();
        ValidationOptions options = ValidationOptions.builder().maxEvaluationDepth(1).build();
        ValidationResult result = plan.validate("text", options);
        assertFalse(result.isValid());
        assertEquals(1, result.count());
        assertEquals("maxEvaluationDepth", result.getLastError().getKeyword());
        assertFalse(plan.isValid("text", options));

        SchemaPlan any = JsonSchema.fromJson("{\"anyOf\":[false,true]}").formulate();
        ValidationOptions limited = ValidationOptions.builder().maxEvaluations(3).build();
        ValidationResult steps = any.validate(1, limited);
        assertFalse(steps.isValid());
        assertEquals("maxEvaluations", steps.getLastError().getKeyword());
        assertFalse(any.isValid(1, limited));
    }

    @Test
    void maxErrorsAbortsBeforeLaterExecutionLimit() {
        SchemaPlan plan = JsonSchema.fromJson("{\"items\":{\"type\":\"integer\"}}").formulate();
        ValidationOptions options = ValidationOptions.builder().maxErrors(1).maxEvaluations(4).build();
        ValidationResult result = plan.validate(Arrays.asList("bad", "also bad"), options);
        assertFalse(result.isValid());
        assertEquals(1, result.count());
        assertEquals("type", result.getLastError().getKeyword());
        ValidationException required = assertThrows(ValidationException.class,
                () -> plan.requireValid(Arrays.asList("bad", "also bad"), options));
        assertEquals("type", required.getResult().getLastError().getKeyword());

        // If the error cap has not been reached, the next execution limit still aborts.
        ValidationOptions stepsFirst = ValidationOptions.builder().maxErrors(2).maxEvaluations(4).build();
        ValidationResult exhausted = plan.validate(Arrays.asList("bad", "also bad"), stepsFirst);
        assertEquals(2, exhausted.count());
        assertEquals("maxEvaluations", exhausted.getLastError().getKeyword());
    }

    @Test
    void maxErrorsStopsInsideSingleEvaluator() {
        SchemaPlan plan = JsonSchema.fromJson("{\"required\":[\"a\",\"b\",\"c\"]}").formulate();
        ValidationResult result = plan.validate(java.util.Collections.emptyMap(),
                ValidationOptions.builder().maxErrors(2).build());
        assertFalse(result.isValid());
        assertEquals(2, result.count());
        assertEquals("required", result.getLastError().getKeyword());
        assertTrue(result.getLastError().getMessage().contains("'b'"));
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
