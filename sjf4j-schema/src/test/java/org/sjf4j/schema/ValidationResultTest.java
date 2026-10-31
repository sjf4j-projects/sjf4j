package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Error and Annotation are independent data types within ValidationResult. */
class ValidationResultTest {

    private static final PathSegment ROOT = PathSegment.Root.INSTANCE;

    @Test
    void errorCarriesLocationsAndDescriptionWithoutSeverity() {
        URI uri = URI.create("https://example.com/schema");
        PathSegment keyword = new PathSegment.Name(ROOT, "type");
        ValidationResult.Error error = new ValidationResult.Error("type", "expected integer", ROOT,
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
        assertTrue(error.toString().contains("ERROR"));
        assertTrue(result.getAnnotations().isEmpty());
    }

    @Test
    void annotationPreservesOriginalJsonValueAndLocations() {
        PathSegment keyword = new PathSegment.Name(ROOT, "examples");
        Object values = List.of(Map.of("x", 1), Map.of("x", 2));
        ValidationResult.Annotation annotation = new ValidationResult.Annotation(
                "examples", values, ROOT, keyword, null);
        ValidationResult result = new ValidationResult(true, null, List.of(annotation));

        assertTrue(result.isValid());
        assertEquals(0, result.count());
        assertTrue(result.getErrors().isEmpty());
        assertNull(result.getLastError());
        assertSame(values, annotation.getValue());
        assertEquals("examples", annotation.getKeyword());
        assertSame(ROOT, annotation.getInstancePs());
        assertSame(keyword, annotation.getKeywordPs());
        assertNull(annotation.getSchemaUri());
        assertEquals(1, result.getAnnotations().size());
    }

    @Test
    void mergeIsNonMutatingAndKeepsParentFirst() {
        ValidationResult.Error parentError = new ValidationResult.Error(
                "required", "missing property", ROOT, ROOT, null);
        ValidationResult.Error childError = new ValidationResult.Error(
                "type", "wrong type", ROOT, ROOT, null);
        ValidationResult.Annotation parentAnnotation = new ValidationResult.Annotation(
                "title", "Parent", ROOT, ROOT, null);
        ValidationResult.Annotation childAnnotation = new ValidationResult.Annotation(
                "title", "Child", ROOT, ROOT, null);

        ValidationResult parent = new ValidationResult(true, null, List.of(parentAnnotation));
        ValidationResult child = new ValidationResult(false, List.of(childError), List.of(childAnnotation));
        ValidationResult combined = child.mergePrevious(parent);
        assertFalse(combined.isValid());
        assertEquals(List.of(parentAnnotation, childAnnotation), combined.getAnnotations());
        assertEquals(List.of(childError), combined.getErrors());
        assertEquals(1, combined.count());
        assertEquals(1, parent.getAnnotations().size());
        assertEquals(1, child.getAnnotations().size());

        ValidationResult invalidParent = new ValidationResult(false, List.of(parentError));
        ValidationResult mergedErrors = child.mergePrevious(invalidParent);
        assertEquals(List.of(parentError, childError), mergedErrors.getErrors());
        assertEquals(List.of(childError), child.getErrors());

        assertSame(ValidationResult.SUCCESS, ValidationResult.SUCCESS.mergePrevious(null));
        ValidationResult successWithParent = ValidationResult.SUCCESS.mergePrevious(parent);
        assertEquals(1, successWithParent.getAnnotations().size());
        assertTrue(ValidationResult.SUCCESS.getAnnotations().isEmpty());
        assertTrue(ValidationResult.SUCCESS.getErrors().isEmpty());
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
