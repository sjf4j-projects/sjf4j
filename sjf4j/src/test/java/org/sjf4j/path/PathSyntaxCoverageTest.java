package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathSyntaxCoverageTest {


    @Test
    void testPointerFormattingErrors() {
        assertThrows(JsonException.class, () -> PathSyntax.toPointerExpr(new PathSegment[]{new PathSegment.Name(PathSegment.Root.INSTANCE, "a"), PathSegment.Root.INSTANCE}));
        assertThrows(JsonException.class, () -> PathSyntax.toPointerExpr(new PathSegment[]{PathSegment.Root.INSTANCE, new PathSegment.Append(PathSegment.Root.INSTANCE), new PathSegment.Name(PathSegment.Root.INSTANCE, "a")}));
        assertThrows(JsonException.class, () -> PathSyntax.toPointerExpr(new PathSegment[]{PathSegment.Root.INSTANCE, new PathSegment.Descendant(PathSegment.Root.INSTANCE)}));
    }

    @Test
    void testAdditionalPathParsingEdges() {
        assertEquals("$.name", PathSyntax.toPathExpr(PathSyntax.parsePath("name")));
        assertEquals("@.name", PathSyntax.toPathExpr(PathSyntax.parsePath("@.name")));
        assertEquals("$.sum(1, 2)", PathSyntax.toPathExpr(PathSyntax.parsePath("$.sum(1, 2)")));
        assertEquals("$.items[?(@.age > 1.0)]", PathSyntax.toPathExpr(PathSyntax.parsePath("$.items[?@.age > 1]")));
        assertEquals("$.items[?(@.isbn == null)]", PathSyntax.toPathExpr(PathSyntax.parsePath("$.items[?@.isbn == null]")));
        assertEquals("$.items[?(@.enabled == true)]", PathSyntax.toPathExpr(PathSyntax.parsePath("$.items[?@.enabled == true]")));
        assertEquals("$.items[?(@.enabled == false)]", PathSyntax.toPathExpr(PathSyntax.parsePath("$.items[?@.enabled == false]")));

        assertThrows(JsonException.class, () -> PathSyntax.parsePath("$."));
        assertThrows(JsonException.class, () -> PathSyntax.parsePath("$.."));
        assertThrows(JsonException.class, () -> PathSyntax.parsePath("$.name("));
        assertThrows(JsonException.class, () -> PathSyntax.parsePath("$#"));
    }

    @Test
    void testFunctionArgAndParenHelpers() {
        assertThrows(JsonException.class, () -> PathSyntax._findMatchingParen("(abc", 0));
        assertEquals(4, PathSyntax._findMatchingParen("(')')x", 0));
        assertEquals(Collections.singletonList(""), PathSyntax._parseFunctionArgs("   "));
        assertEquals(Arrays.asList("1", "nested(2,3)", "'x,y'", "/a(b)/"), PathSyntax._parseFunctionArgs("1, nested(2,3), 'x,y', /a(b)/"));
    }

    static class SamplePojo {
        public String value;
    }
}
