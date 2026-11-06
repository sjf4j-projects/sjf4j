package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.NodeException;

import java.net.URI;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaPlanTest {

    @Test
    void schemaUriMetadataAccessorsArePackagePrivate() throws Exception {
        assertFalse(java.lang.reflect.Modifier.isPublic(
                ObjectSchema.class.getDeclaredMethod("retrievalUri").getModifiers()));
        assertFalse(java.lang.reflect.Modifier.isPublic(
                ObjectSchema.class.getDeclaredMethod("retrievalUri", URI.class).getModifiers()));
        assertFalse(java.lang.reflect.Modifier.isPublic(
                ObjectSchema.class.getDeclaredMethod("canonicalUri").getModifiers()));
    }

    @Test
    void objectSchemaPlanSnapshotsMinimalRootRuntimeState() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{\"$id\":\"defs/user.json\",\"type\":\"string\"}");
        schema.retrievalUri(URI.create("file:///schemas/root.json"));

        SchemaPlan plan = schema.createPlan();

        assertEquals("file:///schemas/root.json", schema.retrievalUri().toString());
        assertEquals("defs/user.json", schema.canonicalUri().toString());
        assertTrue(plan.validate("hello").isValid());
        assertFalse(plan.validate(123).isValid());
    }

    @Test
    void anonymousSchemasWithCollidingContentHashesGetDistinctResourceUris() {
        ObjectSchema first = (ObjectSchema) JsonSchema.fromJson(
                "{\"title\":\"Aa\",\"type\":\"string\"}");
        ObjectSchema second = (ObjectSchema) JsonSchema.fromJson(
                "{\"title\":\"BB\",\"type\":\"string\"}");
        assertEquals(first.hashCode(), second.hashCode());

        SchemaRegistry registry = new SchemaRegistry();
        SchemaPlan firstPlan = SchemaPlanner.buildAndPutPlan(first, registry);
        SchemaPlan secondPlan = SchemaPlanner.buildAndPutPlan(second, registry);

        assertEquals("sjf4j", firstPlan.schemaUri().getScheme());
        assertTrue(firstPlan.schemaUri().toString().matches("sjf4j:/schema-\\d+/"));
        assertFalse(firstPlan.schemaUri().equals(secondPlan.schemaUri()));
        assertTrue(firstPlan.schema() == first);
        assertTrue(secondPlan.schema() == second);
        assertTrue(firstPlan.isValid("text"));
        assertTrue(secondPlan.isValid("text"));
    }

    @Test
    void anonymousBaseSupportsRelativeRootIdAndFragmentReferences() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"$id\":\"child\",\"$defs\":{\"value\":{\"type\":\"string\"}},"
                        + "\"$ref\":\"#/$defs/value\"}").createPlan();

        assertTrue(plan.schemaUri().toString().matches("sjf4j:/schema-\\d+/child"));
        assertTrue(plan.isValid("text"));
        assertFalse(plan.isValid(42));
    }

    @Test
    void absoluteRootIdIsPreservedWithoutAnonymousBase() {
        URI uri = URI.create("https://example.com/schema");
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"$id\":\"https://example.com/schema\",\"type\":\"string\"}").createPlan();

        assertEquals(uri, plan.schemaUri());
    }

    @Test
    void booleanSchemaPlanStaysMinimal() {
        SchemaPlan plan = BooleanSchema.FALSE.createPlan();

        assertFalse(plan.isValid("any"));
    }

    @Test
    void booleanFalseFallbackKeepsExplicitInstanceLocation() {
        SchemaPlan plan = BooleanSchema.FALSE.createPlan();
        org.sjf4j.path.PathSegment location =
                new org.sjf4j.path.PathSegment.Name(
                        org.sjf4j.path.PathSegment.Root.INSTANCE, "nested");
        ValidationContext ctx = new ValidationContext(true, false, false);

        assertFalse(plan.evaluate("value", location, ctx));
        ValidationResult result = ctx.toResult(false);
        assertEquals(1, result.getErrors().size());
        assertEquals("/nested", result.getErrors().get(0).getInstancePs().rootedPointerExpr());
        assertEquals("false", result.getErrors().get(0).getKeyword());

        assertTrue(BooleanSchema.TRUE.createPlan().evaluate("value", location,
                new ValidationContext(true, false, false)));
    }

    @Test
    void booleanResourcesCanBeIndexedResolvedAndReferencedByUri() {
        URI falseUri = URI.create("https://example.test/schemas/false.json");
        URI trueUri = URI.create("https://example.test/schemas/true.json");
        URI wrapperUri = URI.create("https://example.test/schemas/wrapper.json");
        SchemaRegistry registry = new SchemaRegistry();

        registry.index(falseUri, BooleanSchema.FALSE);
        SchemaPlan falsePlan = registry.resolve(falseUri);
        assertFalse(falsePlan.isValid("anything"));
        assertFalse(falsePlan.validate("anything").isValid());
        assertEquals(falseUri, falsePlan.schemaUri());

        SchemaPlan truePlan = registry.register(trueUri, BooleanSchema.TRUE);
        assertTrue(truePlan.isValid("anything"));
        assertEquals(truePlan, registry.resolve(trueUri));

        registry.index(wrapperUri, JsonSchema.fromJson("{\"$ref\":\"false.json\"}"));
        assertFalse(registry.resolve(wrapperUri).isValid("anything"));

        registry.indexIfAbsent(falseUri, BooleanSchema.TRUE);
        assertFalse(registry.resolve(falseUri).isValid("anything"));
        assertThrows(SchemaException.class, () -> registry.index(falseUri, BooleanSchema.TRUE));
        assertThrows(SchemaException.class, () -> registry.index(falseUri, JsonSchema.fromJson("{}")));
    }

    @Test
    void registerRejectsBooleanAndObjectUriCollisionsInBothOrders() {
        URI uri = URI.create("https://example.test/schemas/collision.json");

        SchemaRegistry booleanFirst = new SchemaRegistry();
        booleanFirst.index(uri, BooleanSchema.TRUE);
        assertThrows(SchemaException.class, () -> booleanFirst.register(
                uri, JsonSchema.fromJson("{\"type\":\"string\"}")));
        assertTrue(booleanFirst.resolve(uri).isValid(123));

        SchemaRegistry objectFirst = new SchemaRegistry();
        objectFirst.register(uri, JsonSchema.fromJson("{\"type\":\"string\"}"));
        assertThrows(SchemaException.class, () -> objectFirst.register(uri, BooleanSchema.TRUE));
        assertFalse(objectFirst.resolve(uri).isValid(123));
        assertTrue(objectFirst.resolve(uri).isValid("text"));
    }

    @Test
    void planStrictFormatDefaultCanBeOverriddenDirectly() {
        SchemaPlan plan = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"email\"}").createPlan();

        assertTrue(plan.validate("not-email").isValid());
        assertFalse(plan.validate("not-email", false, true).isValid());
    }

    @Test
    void planRefUsesPreResolvedTarget() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$defs\":{\"s\":{\"type\":\"string\"}}," +
                "\"$ref\":\"#/$defs/s\"" +
                "}");

        SchemaPlan plan = schema.createPlan();
        assertTrue(plan.isValid("ok"));
        assertFalse(plan.isValid(1));
    }

    @Test
    void planRefResolvesBooleanSubschemaByPointer() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$defs\":{\"bool\":true}," +
                "\"$ref\":\"#/$defs/bool\"" +
                "}");

        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid("ok"));
    }

    @Test
    void planRefResolvesEscapedPointerTokens() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$defs\":{\"percent%field\":{\"type\":\"string\"}}," +
                "\"$ref\":\"#/$defs/percent%25field\"" +
                "}");

        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid("ok"));
        assertFalse(plan.isValid(1));
    }

    @Test
    void planRefResolvesFileUriPointer() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"file:///folder/file.json\"," +
                "\"$defs\":{\"foo\":{\"type\":\"number\"}}," +
                "\"$ref\":\"#/$defs/foo\"" +
                "}");

        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(1));
        assertFalse(plan.isValid("a"));
    }

    @Test
    void rootRelativeIdUsesRetrievalUriAsRefBase() {
        ObjectSchema rootSchema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"defs/root.json\"," +
                "\"$ref\":\"leaf.json\"" +
                "}");
        rootSchema.retrievalUri(URI.create("file:///schemas/base.json"));

        ObjectSchema leafSchema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"file:///schemas/defs/leaf.json\"," +
                "\"type\":\"string\"," +
                "\"minLength\":1" +
                "}");

        SchemaRegistry registry = new SchemaRegistry();
        registry.index(leafSchema);

        SchemaPlan plan = rootSchema.createPlan(registry);

        assertTrue(plan.isValid("x"));
        assertFalse(plan.isValid(""));
    }

    @Test
    void dynamicRefFallsBackToInitialPlanAndUsesScopedOverride() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"type\":\"array\"," +
                "\"items\":{\"$dynamicRef\":\"#/$defs/number\"}," +
                "\"$defs\":{" +
                "\"number\":{\"$dynamicAnchor\":\"number\",\"type\":\"number\"}" +
                "}" +
                "}");

        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(new Object[]{1}));
        assertFalse(plan.isValid(new Object[]{"a"}));
    }

    @Test
    void failFastFailurePopsDynamicAnchorPlan() {
        SchemaPlan plan = JsonSchema.fromJson("{" +
                "\"$dynamicAnchor\":\"node\"," +
                "\"required\":[\"missing\"]," +
                "\"properties\":{\"present\":true}" +
                "}").createPlan();
        ValidationContext ctx = new ValidationContext(true, false, false);

        assertFalse(plan.evaluate(Instance.infer(Collections.emptyMap()), null, ctx));
        assertTrue(ctx.planStack().isEmpty());
    }

    @Test
    void failFastFailedAnyOfAndOneOfCandidatesLeaveNoEvaluatedFrames() {
        for (String keyword : new String[]{"anyOf", "oneOf"}) {
            SchemaPlan plan = JsonSchema.fromJson("{\"" + keyword + "\":[" +
                    "{\"required\":[\"missing\"],\"unevaluatedProperties\":true},true]," +
                    "\"unevaluatedProperties\":true}").createPlan();
            Instance instance = Instance.infer(Collections.singletonMap("present", 1));
            ValidationContext ctx = new ValidationContext(true, false, false);

            assertTrue(plan.evaluate(instance, null, ctx), keyword);
            assertNull(instance.peekEvaluated(), keyword);
        }
    }

    @Test
    void unevaluatedKeywordsApplyToTheirOwnTypesInBothModes() {
        SchemaPlan plan = JsonSchema.fromJson("{" +
                "\"unevaluatedProperties\":false,\"unevaluatedItems\":false" +
                "}").createPlan();
        for (Object instance : new Object[]{Collections.singletonMap("x", 1), Collections.singletonList(1)}) {
            assertFalse(plan.validate(instance, true, false).isValid());
            assertFalse(plan.validate(instance).isValid());
        }
    }

    @Test
    void notSeesUnevaluatedPropertiesFailureOnObject() {
        SchemaPlan plan = JsonSchema.fromJson("{" +
                "\"not\":{\"unevaluatedProperties\":false,\"unevaluatedItems\":false}" +
                "}").createPlan();
        Object instance = Collections.singletonMap("a", 1);

        assertTrue(plan.isValid(instance));
        assertTrue(plan.validate(instance).isValid());
    }

    @Test
    void inapplicableUnevaluatedPropertiesLeavesNoFrame() {
        SchemaPlan plan = JsonSchema.fromJson("{\"unevaluatedProperties\":false}").createPlan();
        for (boolean failFast : new boolean[]{true, false}) {
            Instance instance = Instance.infer(Collections.singletonList(1));
            ValidationContext ctx = new ValidationContext(failFast, false, false);

            assertTrue(plan.evaluate(instance, null, ctx));
            assertNull(instance.peekEvaluated());
        }
    }

    @Test
    void oneOfDoesNotCountInapplicableUnevaluatedKeywordAsMatch() {
        SchemaPlan plan = JsonSchema.fromJson("{" +
                "\"oneOf\":[{\"unevaluatedProperties\":false,\"unevaluatedItems\":false},true]" +
                "}").createPlan();
        for (Object instance : new Object[]{Collections.singletonMap("x", 1), Collections.singletonList(1)}) {
            assertTrue(plan.isValid(instance));
            assertTrue(plan.validate(instance).isValid());
        }
    }

    @Test
    void dynamicRefPointerFragmentBehavesLikePlainRef() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$ref\":\"list\"," +
                "\"$defs\":{" +
                "\"item\":{\"$dynamicAnchor\":\"item\",\"type\":\"string\"}," +
                "\"list\":{\"$id\":\"list\",\"type\":\"array\",\"items\":{\"$dynamicRef\":\"#/$defs/item\"},\"$defs\":{\"item\":{\"$dynamicAnchor\":\"item\",\"type\":\"number\"}}}" +
                "}" +
                "}");

        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(new Object[]{1}));
        assertFalse(plan.isValid(new Object[]{"a"}));
    }

    @Test
    void dynamicRefUsesOuterScopedOverride() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$ref\":\"list\"," +
                "\"$defs\":{" +
                "\"item\":{\"$dynamicAnchor\":\"item\",\"type\":\"string\"}," +
                "\"list\":{\"$id\":\"list\",\"type\":\"array\",\"items\":{\"$dynamicRef\":\"#item\"},\"$defs\":{\"bookend\":{\"$dynamicAnchor\":\"item\"}}}" +
                "}" +
                "}");

        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(new Object[]{"a"}));
        assertFalse(plan.isValid(new Object[]{1}));
    }

    @Test
    void dynamicRefUsesOuterScopedOverrideAcrossIntermediateResourceWithoutDynamicAnchors() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$ref\":\"wrapper\"," +
                "\"$defs\":{" +
                "\"item\":{\"$dynamicAnchor\":\"item\",\"type\":\"string\"}," +
                "\"wrapper\":{\"$id\":\"wrapper\",\"$ref\":\"list\"}," +
                "\"list\":{\"$id\":\"list\",\"type\":\"array\",\"items\":{\"$dynamicRef\":\"#item\"},\"$defs\":{\"base\":{\"$dynamicAnchor\":\"item\",\"type\":\"number\"}}}" +
                "}" +
                "}");

        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(new Object[]{"a"}));
        assertFalse(plan.isValid(new Object[]{1}));
    }

    @Test
    void duplicateAnchorInSameResourceIsRejected() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$defs\":{" +
                "\"a\":{\"$anchor\":\"dup\"}," +
                "\"b\":{\"$anchor\":\"dup\"}" +
                "}" +
                "}");

        assertThrows(SchemaException.class, schema::createPlan);
    }

    @Test
    void duplicateDynamicAnchorInSameResourceIsRejected() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$defs\":{" +
                "\"a\":{\"$dynamicAnchor\":\"dup\"}," +
                "\"b\":{\"$dynamicAnchor\":\"dup\"}" +
                "}" +
                "}");

        assertThrows(SchemaException.class, schema::createPlan);
    }

    @Test
    void anchorAndDynamicAnchorCollisionInSameResourceIsRejected() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$defs\":{" +
                "\"a\":{\"$anchor\":\"dup\"}," +
                "\"b\":{\"$dynamicAnchor\":\"dup\"}" +
                "}" +
                "}");

        assertThrows(SchemaException.class, schema::createPlan);
    }

    @Test
    void anchorAndDynamicAnchorCollisionOnSameSchemaIsRejected() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$defs\":{" +
                "\"a\":{\"$anchor\":\"dup\",\"$dynamicAnchor\":\"dup\"}" +
                "}" +
                "}");

        assertThrows(SchemaException.class, schema::createPlan);
    }

    @Test
    void planHandlesSelfRefWithoutPlaceholderBreakage() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{\"$ref\":\"#\"}");

        SchemaPlan plan = schema.createPlan();

        assertThrows(NodeException.class, () -> plan.validate("Cyclic schema reference"));
    }
}
