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
    void testRootedHelpersAndInspect() {
        assertEquals("", Paths.rootedInspect(null));
        assertEquals("", Paths.rootedPathExpr(null));
        assertEquals("", Paths.rootedPointerExpr(null));

        PathSegment root = PathSegment.Root.INSTANCE;
        PathSegment mapName = new PathSegment.Name(root, Map.class, "field");
        PathSegment joName = new PathSegment.Name(root, JsonObject.class, "field");
        PathSegment pojoName = new PathSegment.Name(root, SamplePojo.class, "value");
        PathSegment listIndex = new PathSegment.Index(root, List.class, 1);
        PathSegment jaIndex = new PathSegment.Index(root, JsonArray.class, 2);
        PathSegment arrayIndex = new PathSegment.Index(root, String[].class, 3);
        PathSegment setIndex = new PathSegment.Index(root, Set.class, 4);
        PathSegment otherIndex = new PathSegment.Index(root, SamplePojo.class, 5);

        assertEquals("/{field", Paths.inspect(new PathSegment[]{root, mapName}));
        assertEquals("/J{field", Paths.inspect(new PathSegment[]{root, joName}));
        assertTrue(Paths.inspect(new PathSegment[]{root, pojoName}).startsWith("/@SamplePojo{"));
        assertEquals("/[1", Paths.inspect(new PathSegment[]{root, listIndex}));
        assertEquals("/J[2", Paths.inspect(new PathSegment[]{root, jaIndex}));
        assertEquals("/A[3", Paths.inspect(new PathSegment[]{root, arrayIndex}));
        assertEquals("/S[4", Paths.inspect(new PathSegment[]{root, setIndex}));
        assertEquals("/@SamplePojo[5", Paths.inspect(new PathSegment[]{root, otherIndex}));
        assertEquals("$.field", Paths.rootedPathExpr(mapName));
        assertEquals("/field", Paths.rootedPointerExpr(mapName));
        assertEquals("/{field", Paths.rootedInspect(mapName));
    }

    @Test
    void testPointerFormattingErrors() {
        assertThrows(JsonException.class, () -> Paths.toPointerExpr(new PathSegment[]{new PathSegment.Name(PathSegment.Root.INSTANCE, null, "a"), PathSegment.Root.INSTANCE}));
        assertThrows(JsonException.class, () -> Paths.toPointerExpr(new PathSegment[]{PathSegment.Root.INSTANCE, new PathSegment.Append(PathSegment.Root.INSTANCE, null), new PathSegment.Name(PathSegment.Root.INSTANCE, null, "a")}));
        assertThrows(JsonException.class, () -> Paths.toPointerExpr(new PathSegment[]{PathSegment.Root.INSTANCE, new PathSegment.Descendant(PathSegment.Root.INSTANCE, null)}));
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
