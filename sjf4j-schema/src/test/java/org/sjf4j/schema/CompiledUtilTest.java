package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Fail-fast compiled diagnostics must not require a runtime SchemaPlan. */
class CompiledUtilTest {

    @Test
    void compiledFailureCarriesKeywordAndInstanceLocation() {
        PathSegment item = new PathSegment.Index(
                new PathSegment.Name(PathSegment.Root.INSTANCE, "items"), 2);
        ValidationException exception = CompiledUtil.validationFailure(
                "oneOf", "sjf4j:/example", "/properties/items/items/oneOf", item);
        ValidationResult result = exception.getResult();
        assertFalse(result.isValid());
        assertEquals(1, result.count());
        ValidationResult.Message error = result.getLastError();
        assertEquals("oneOf", error.getKeyword());
        assertEquals("/items/2", error.getInstancePs().rootedPointerExpr());
        assertEquals("/properties/items/items/oneOf", error.getKeywordPs().rootedPointerExpr());
        assertEquals(URI.create("sjf4j:/example"), error.getSchemaUri());
    }

    @Test
    void rebaseNestedErrorAndAnnotationWithoutRevalidating() {
        PathSegment child = new PathSegment.Name(PathSegment.Root.INSTANCE, "xName");
        PathSegment keyword = new PathSegment.Name(PathSegment.Root.INSTANCE, "type");
        ValidationResult.Message error = new ValidationResult.Message(
                "type", "expected number", null, child, keyword, URI.create("sjf4j:/nested"));
        ValidationResult.Message annotation = new ValidationResult.Message(
                "title", "sample", null, PathSegment.Root.INSTANCE,
                PathSegment.Root.INSTANCE, URI.create("sjf4j:/nested"));
        ValidationException source = new ValidationException(
                new ValidationResult(false, List.of(error), List.of(annotation)));

        PathSegment parent = new PathSegment.Index(
                new PathSegment.Name(PathSegment.Root.INSTANCE, "items"), 3);
        ValidationException relocated = CompiledUtil.rebaseFailure(source, parent);
        assertNotSame(source, relocated);
        assertEquals("/items/3/xName",
                relocated.getResult().getLastError().getInstancePs().rootedPointerExpr());
        assertEquals("/items/3",
                relocated.getResult().getAnnotations().get(0).getInstancePs().rootedPointerExpr());
        assertEquals(keyword, relocated.getResult().getLastError().getKeywordPs());
        assertEquals(URI.create("sjf4j:/nested"), relocated.getResult().getLastError().getSchemaUri());
    }

    @Test
    void rootPrefixReturnsOriginalAndEmptyErrorsStayEmpty() {
        ValidationException source = CompiledUtil.validationFailure(
                "const", null, null, PathSegment.Root.INSTANCE);
        assertSame(source, CompiledUtil.rebaseFailure(source, null));
        assertSame(source, CompiledUtil.rebaseFailure(source, PathSegment.Root.INSTANCE));
        assertEquals("", source.getResult().getLastError().getKeywordPs().rootedPointerExpr());

        ValidationException noMessages = new ValidationException(
                new ValidationResult(false, Collections.emptyList()));
        ValidationException rebased = CompiledUtil.rebaseFailure(noMessages,
                new PathSegment.Name(PathSegment.Root.INSTANCE, "nested"));
        assertTrue(rebased.getResult().getErrors().isEmpty());
        assertTrue(rebased.getResult().getAnnotations().isEmpty());
    }
}
