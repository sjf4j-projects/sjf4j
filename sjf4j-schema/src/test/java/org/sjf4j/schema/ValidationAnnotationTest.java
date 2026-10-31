package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** JSON Schema annotation values and their lifetime are separate from errors. */
class ValidationAnnotationTest {

    @Test
    void exposesRootAndNestedMetadataWithOriginalValuesAndLocations() {
        SchemaPlan plan = JsonSchema.fromJson("{"
                + "\"title\":\"Root\",\"type\":\"object\","
                + "\"properties\":{\"name\":{\"type\":\"string\","
                + "\"description\":\"Display name\",\"default\":\"anonymous\","
                + "\"examples\":[\"Ada\",\"Lin\"]}}}"
        ).createPlan();

        ValidationResult result = plan.validate(Map.of("name", "Ada"));
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        assertEquals(4, result.getAnnotations().size());
        assertEquals("title", result.getAnnotations().get(3).getKeyword());
        assertEquals("Root", result.getAnnotations().get(3).getValue());
        assertEquals("", result.getAnnotations().get(3).getInstancePs().rootedPointerExpr());

        ValidationAnnotation description = result.getAnnotations().stream()
                .filter(a -> a.getKeyword().equals("description")).findFirst().orElseThrow();
        assertEquals("Display name", description.getValue());
        assertEquals("/name", description.getInstancePs().rootedPointerExpr());
        assertEquals("/properties/name/description", description.getKeywordPs().rootedPointerExpr());

        ValidationAnnotation examples = result.getAnnotations().stream()
                .filter(a -> a.getKeyword().equals("examples")).findFirst().orElseThrow();
        assertNotNull(examples.getValue());
    }

    @Test
    void failedSchemaAndItsSubschemasDoNotPublishAnnotations() {
        SchemaPlan plan = JsonSchema.fromJson("{"
                + "\"title\":\"Root\",\"properties\":{\"name\":{"
                + "\"description\":\"Only when valid\",\"type\":\"string\"}}}"
        ).createPlan();

        ValidationResult result = plan.validate(Map.of("name", 42));
        assertFalse(result.isValid());
        assertTrue(result.getAnnotations().isEmpty());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void validAnyOfBranchContributesAnnotationsButFailedBranchDoesNot() {
        SchemaPlan plan = JsonSchema.fromJson("{\"anyOf\":["
                + "{\"title\":\"string branch\",\"type\":\"string\"},"
                + "{\"title\":\"number branch\",\"type\":\"number\"}]}"
        ).createPlan();

        ValidationResult result = plan.validate(5);
        assertTrue(result.isValid());
        assertEquals(1, result.getAnnotations().size());
        assertEquals("number branch", result.getAnnotations().get(0).getValue());
        assertEquals("/anyOf/1/title", result.getAnnotations().get(0).getKeywordPs().rootedPointerExpr());
    }

    @Test
    void anyOfCollectsAllSuccessfulBranchesWhenAnnotationsAreEnabled() {
        SchemaPlan plan = JsonSchema.fromJson("{\"anyOf\":["
                + "{\"title\":\"first\",\"type\":\"integer\"},"
                + "{\"title\":\"second\",\"minimum\":0}]}"
        ).createPlan();
        ValidationResult result = plan.validate(5);
        assertTrue(result.isValid());
        assertEquals(2, result.getAnnotations().size());
    }

    @Test
    void containsCollectsAnnotationsForAllMatchingItems() {
        SchemaPlan plan = JsonSchema.fromJson("{\"contains\":{"
                + "\"description\":\"number\",\"type\":\"integer\"}}").createPlan();
        ValidationResult result = plan.validate(List.of(1, "wrong", 2));
        assertTrue(result.isValid());
        assertEquals(2, result.getAnnotations().size());
        assertEquals("/0", result.getAnnotations().get(0).getInstancePs().rootedPointerExpr());
        assertEquals("/2", result.getAnnotations().get(1).getInstancePs().rootedPointerExpr());
    }

    @Test
    void failedOneOfAndNotBranchesDoNotExposeTentativeAnnotations() {
        SchemaPlan overlapping = JsonSchema.fromJson("{\"oneOf\":["
                + "{\"description\":\"first\",\"type\":\"number\"},"
                + "{\"description\":\"second\",\"type\":\"integer\"}]}"
        ).createPlan();
        assertTrue(overlapping.validate("not a number").getAnnotations().isEmpty());
        assertTrue(overlapping.validate(1).getAnnotations().isEmpty());

        SchemaPlan not = JsonSchema.fromJson("{\"title\":\"outer\","
                + "\"not\":{\"title\":\"probe\",\"type\":\"string\"}}").createPlan();
        ValidationResult valid = not.validate(1);
        assertTrue(valid.isValid());
        assertEquals(1, valid.getAnnotations().size());
        assertEquals("outer", valid.getAnnotations().get(0).getValue());
        assertTrue(not.validate("x").getAnnotations().isEmpty());
    }

    @Test
    void reusableContextAccumulatesAnnotationsAndErrorsIndependently() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"title\":\"success\",\"type\":\"integer\"}").createPlan();
        ValidationContext ctx = new ValidationContext(true, false, false);
        assertTrue(plan.evaluate(42, ctx));
        assertFalse(plan.evaluate("wrong", ctx));
        assertTrue(plan.evaluate(1, ctx));
        ValidationResult result = ctx.toResult(false);
        assertEquals(2, result.getAnnotations().size());
        assertEquals(1, result.getErrors().size());

        ctx.clearAnnotations();
        assertEquals(0, result.getAnnotations().size());
        assertEquals(1, result.getErrors().size());
        ctx.clearMessages();
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void booleanOnlyModeDoesNotCollectAnnotations() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"description\":\"metadata\",\"type\":\"integer\"}").createPlan();
        ValidationContext ctx = new ValidationContext(true, false, true);
        assertTrue(plan.evaluate(2, ctx));
        assertFalse(plan.evaluate("wrong", ctx));
        assertTrue(ctx.getAnnotations().isEmpty());
        assertTrue(ctx.toResult(false).getAnnotations().isEmpty());
    }

    @ValidJsonSchema("{\"title\":\"Parent\"}")
    public static class AnnotatedParent {
    }

    @ValidJsonSchema("{\"description\":\"Child\"}")
    public static class AnnotatedChild extends AnnotatedParent {
    }

    @Test
    void schemaValidatorRetainsSuccessfulAnnotationsAcrossClassHierarchy() {
        ValidationResult result = new SchemaValidator().validate(new AnnotatedChild());
        assertTrue(result.isValid());
        assertEquals(2, result.getAnnotations().size());
        assertEquals("Parent", result.getAnnotations().get(0).getValue());
        assertEquals("Child", result.getAnnotations().get(1).getValue());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void annotationValuesMayBeNull() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"default\":null,\"type\":\"integer\"}").createPlan();
        ValidationResult result = plan.validate(5);
        assertTrue(result.isValid());
        assertEquals(1, result.getAnnotations().size());
        assertEquals("default", result.getAnnotations().get(0).getKeyword());
    }
}
