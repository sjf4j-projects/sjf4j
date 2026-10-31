package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Errors and annotations share the same message type but remain independent lists. */
class ValidationResultTest {

    private static final PathSegment ROOT = PathSegment.Root.INSTANCE;

    @Test
    void errorCarriesLocationsAndDescriptionWithoutSeverity() {
        URI uri = URI.create("https://example.com/schema");
        PathSegment keyword = new PathSegment.Name(ROOT, "type");
        ValidationResult.Message error = new ValidationResult.Message("type", "expected integer", null, ROOT,
                keyword, uri);
        ValidationResult result = new ValidationResult(false, List.of(error));

        assertFalse(result.isValid());
        assertEquals(1, result.count());
        assertSame(error, result.getLastError());
        assertEquals("type", error.getKeyword());
        assertEquals("expected integer", error.getMessage());
        assertSame(ROOT, error.getInstancePs());
        assertSame(keyword, error.getKeywordPs());
        assertEquals(uri, error.getSchemaUri());
        assertEquals(uri.toString(), error.getSchemaUriText());
        assertTrue(error.toString().contains("type: expected integer"));
        assertFalse(error.toString().startsWith("ERROR"));
        assertNull(error.getValue());
        assertTrue(result.getAnnotations().isEmpty());
    }

    @Test
    void annotationPreservesOriginalJsonValueAndLocations() {
        PathSegment keyword = new PathSegment.Name(ROOT, "examples");
        Object values = List.of(Map.of("x", 1), Map.of("x", 2));
        ValidationResult.Message annotation = new ValidationResult.Message(
                "examples", "Example values", values, ROOT, keyword, null);
        ValidationResult result = new ValidationResult(true, null, List.of(annotation));

        assertTrue(result.isValid());
        assertEquals(0, result.count());
        assertTrue(result.getErrors().isEmpty());
        assertNull(result.getLastError());
        assertSame(values, annotation.getValue());
        assertEquals("examples", annotation.getKeyword());
        assertEquals("Example values", annotation.getMessage());
        assertFalse(annotation.toString().startsWith("ERROR"));
        assertTrue(annotation.toString().contains("examples: Example values"));
        assertSame(ROOT, annotation.getInstancePs());
        assertSame(keyword, annotation.getKeywordPs());
        assertNull(annotation.getSchemaUri());
        assertEquals(1, result.getAnnotations().size());
    }

    @Test
    void sharedMessageTypeKeepsErrorsAndAnnotationsSeparate() {
        ValidationResult.Message error = new ValidationResult.Message(
                "required", "missing property", null, ROOT, ROOT, null);
        Object rawValue = Map.of("sample", 42);
        ValidationResult.Message annotation = new ValidationResult.Message(
                "examples", "sample value", rawValue, ROOT, ROOT, null);
        ValidationResult result = new ValidationResult(
                false, List.of(error), List.of(annotation));

        assertFalse(result.isValid());
        assertEquals(1, result.count());
        assertSame(error, result.getLastError());
        assertEquals(List.of(error), result.getErrors());
        assertEquals(List.of(annotation), result.getAnnotations());
        assertNull(error.getValue());
        assertSame(rawValue, annotation.getValue());
        assertTrue(ValidationResult.SUCCESS.getErrors().isEmpty());
        assertTrue(ValidationResult.SUCCESS.getAnnotations().isEmpty());
    }

    @Test
    void validationPlanDoesNotInjectAnnotationsYet() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"title\":\"Metadata\",\"properties\":{\"x\":{\"description\":\"Property\"}}}"
        ).createPlan();
        ValidationResult result = plan.validate(Map.of("x", 1));

        assertSame(ValidationResult.SUCCESS, result);
        assertTrue(result.getAnnotations().isEmpty());
    }

    @Test
    void contextOwnsLiveErrorListWithoutCopyingIt() {
        SchemaPlan plan = JsonSchema.fromJson("{\"required\":[\"x\"]}").createPlan();
        ValidationContext ctx = new ValidationContext(false, false, false);
        assertFalse(plan.evaluate(Map.of(), ctx));

        ValidationResult result = ctx.toResult(false);
        assertEquals(1, result.getErrors().size());
        ctx.clearErrors();
        assertTrue(result.getErrors().isEmpty());
        assertEquals(0, result.count());
    }
}
