package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.SchemaException;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaPlanTest {

    @Test
    void objectSchemaPlanSnapshotsMinimalRootRuntimeState() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{\"$id\":\"defs/user.json\",\"type\":\"string\"}");
        schema.setRetrievalUri(URI.create("file:///schemas/root.json"));

        SchemaPlan plan = schema.createPlan();

        assertEquals("file:///schemas/root.json", schema.getRetrievalUri().toString());
        assertEquals("defs/user.json", schema.getCanonicalUri().toString());
        assertTrue(plan.validate("hello").isValid());
        assertFalse(plan.validate(123).isValid());
    }

    @Test
    void booleanSchemaPlanStaysMinimal() {
        SchemaPlan plan = BooleanSchema.FALSE.createPlan();

        assertFalse(plan.isValid("any"));
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
        rootSchema.setRetrievalUri(URI.create("file:///schemas/base.json"));

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

        assertThrows(SchemaException.class, () -> plan.validate("Cyclic schema reference"));
    }
}
