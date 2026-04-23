package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathsCoverageTest {


    @Test
    void testPointerFormattingErrors() {
        assertThrows(JsonException.class, () -> Paths.toPointerExpr(new PathSegment[]{new PathSegment.Name(PathSegment.Root.INSTANCE, "a"), PathSegment.Root.INSTANCE}));
        assertThrows(JsonException.class, () -> Paths.toPointerExpr(new PathSegment[]{PathSegment.Root.INSTANCE, new PathSegment.Append(PathSegment.Root.INSTANCE), new PathSegment.Name(PathSegment.Root.INSTANCE, "a")}));
        assertThrows(JsonException.class, () -> Paths.toPointerExpr(new PathSegment[]{PathSegment.Root.INSTANCE, new PathSegment.Descendant(PathSegment.Root.INSTANCE)}));
    }

    @Test
    void testAdditionalPathParsingEdges() {
        assertEquals("$.name", Paths.toPathExpr(Paths.parsePath("name")));
        assertEquals("@.name", Paths.toPathExpr(Paths.parsePath("@.name")));
        assertEquals("$.sum(1, 2)", Paths.toPathExpr(Paths.parsePath("$.sum(1, 2)")));
        assertEquals("$.items[?(@.age > 1.0)]", Paths.toPathExpr(Paths.parsePath("$.items[?@.age > 1]")));
        assertEquals("$.items[?(@.isbn == null)]", Paths.toPathExpr(Paths.parsePath("$.items[?@.isbn == null]")));
        assertEquals("$.items[?(@.enabled == true)]", Paths.toPathExpr(Paths.parsePath("$.items[?@.enabled == true]")));
        assertEquals("$.items[?(@.enabled == false)]", Paths.toPathExpr(Paths.parsePath("$.items[?@.enabled == false]")));

        assertThrows(JsonException.class, () -> Paths.parsePath("$."));
        assertThrows(JsonException.class, () -> Paths.parsePath("$.."));
        assertThrows(JsonException.class, () -> Paths.parsePath("$.name("));
        assertThrows(JsonException.class, () -> Paths.parsePath("$#"));
    }

    @Test
    void testFunctionArgAndParenHelpers() {
        assertThrows(JsonException.class, () -> Paths._findMatchingParen("(abc", 0));
        assertEquals(4, Paths._findMatchingParen("(')')x", 0));
        assertEquals(Collections.singletonList(""), Paths._parseFunctionArgs("   "));
        assertEquals(Arrays.asList("1", "nested(2,3)", "'x,y'", "/a(b)/"), Paths._parseFunctionArgs("1, nested(2,3), 'x,y', /a(b)/"));
    }

    static class SamplePojo {
        public String value;
    }
}
