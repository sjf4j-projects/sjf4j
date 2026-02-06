package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PathsTest {

    /// Pointer

    @Test
    void testRootOnly() {
        PathSegment[] segments = Paths.parsePointer("");
        assertEquals(1, segments.length);
        assertInstanceOf(PathSegment.Root.class, segments[0]);

        segments = Paths.parsePointer("/");
        assertEquals(1, segments.length);
        assertInstanceOf(PathSegment.Root.class, segments[0]);
    }

    @Test
    void testSimplePath() {
        PathSegment[] segments = Paths.parsePointer("/users/name");
        assertEquals(3, segments.length);
        assertInstanceOf(PathSegment.Root.class, segments[0]);
        assertInstanceOf(PathSegment.Name.class, segments[1]);
        assertEquals("users", ((PathSegment.Name) segments[1]).name);
        assertInstanceOf(PathSegment.Name.class, segments[2]);
        assertEquals("name", ((PathSegment.Name) segments[2]).name);
    }

    @Test
    void testNumericIndex() {
        PathSegment[] segments = Paths.parsePointer("/users/0/posts/12");
        assertEquals(5, segments.length);
        assertInstanceOf(PathSegment.Index.class, segments[2]);
        assertEquals(0, ((PathSegment.Index) segments[2]).index);
        assertInstanceOf(PathSegment.Index.class, segments[4]);
        assertEquals(12, ((PathSegment.Index) segments[4]).index);
    }

    @Test
    void testAppendToken() {
        PathSegment[] segments = Paths.parsePointer("/users/-/name");
        assertEquals(4, segments.length);
        assertInstanceOf(PathSegment.Append.class, segments[2]);
    }

    @Test
    void testEscapeSequences() {
        // "~0" -> "~", "~1" -> "/"
        PathSegment[] segments = Paths.parsePointer("/a~1b/c~0d/~1~0");
        assertEquals(4, segments.length);
        assertEquals("a/b", ((PathSegment.Name) segments[1]).name);
        assertEquals("c~d", ((PathSegment.Name) segments[2]).name);
        assertEquals("/~", ((PathSegment.Name) segments[3]).name);
    }

    @Test
    void testConsecutiveSlashes() {
        PathSegment[] segments = Paths.parsePointer("/a//b///c");
        assertEquals(7, segments.length);
        assertEquals("a", ((PathSegment.Name) segments[1]).name);
        assertEquals("b", ((PathSegment.Name) segments[3]).name);
        assertEquals("c", ((PathSegment.Name) segments[6]).name);
    }

    @Test
    void testNumericEdgeCases() {
        // leading zeros
        PathSegment[] segments = Paths.parsePointer("/01/002");
        assertEquals(3, segments.length);
        assertEquals(1, ((PathSegment.Index) segments[1]).index);
        assertEquals(2, ((PathSegment.Index) segments[2]).index);

        // large number
        segments = Paths.parsePointer("/1234567890");
        assertEquals(2, segments.length);
        assertEquals(1234567890, ((PathSegment.Index) segments[1]).index);
    }

    @Test
    void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> Paths.parsePointer("users")); // missing '/'
    }

    @Test
    void testEmptySegments() {
        PathSegment[] segments = Paths.parsePointer("/a//b");
        assertEquals(4, segments.length);
        assertEquals("a", ((PathSegment.Name) segments[1]).name);
        assertEquals("", ((PathSegment.Name) segments[2]).name);
        assertEquals("b", ((PathSegment.Name) segments[3]).name);
    }

    @Test
    void testEmptyEnds() {
        PathSegment[] segments = Paths.parsePointer("/a/b/");
        assertEquals(4, segments.length);
        assertEquals("a", ((PathSegment.Name) segments[1]).name);
        assertEquals("b", ((PathSegment.Name) segments[2]).name);
        assertEquals("", ((PathSegment.Name) segments[3]).name);
    }

    @Test
    void testOnlyAppend() {
        PathSegment[] segments = Paths.parsePointer("/-");
        assertEquals(2, segments.length);
        assertInstanceOf(PathSegment.Append.class, segments[1]);
    }


    /// Path

    @Test
    public void testBasicPaths() {
        // Root only
        testParsePath("$", 1, PathSegment.Root.class);

        // Simple field access
        testParsePath("$.name", 2, PathSegment.Root.class, PathSegment.Name.class);
        testParsePath("$.user.name", 3, PathSegment.Root.class, PathSegment.Name.class, PathSegment.Name.class);
        testExpr("$.user.name", 2, ".name");

        // Array index
        testParsePath("$.array[0]", 3, PathSegment.Root.class, PathSegment.Name.class, PathSegment.Index.class);
        testParsePath("$.array[-1]", 3, PathSegment.Root.class, PathSegment.Name.class, PathSegment.Index.class);
        testExpr("$.array[-1]", 2, "[-1]");
    }

    @Test
    public void testBracketNotation() {
        // Quoted field names
        testParsePath("$['name']", 2, PathSegment.Root.class, PathSegment.Name.class);
        testParsePath("$[\"first name\"]", 2, PathSegment.Root.class, PathSegment.Name.class);
        testExpr("$[\"first name\"]", 1, "['first name']");

        // Unquoted field names in brackets
        testParsePathFailure("$[name]", "name");

        // Mixed dot and bracket
        testParsePath("$.user['name']", 3, PathSegment.Root.class, PathSegment.Name.class, PathSegment.Name.class);
    }

    @Test
    public void testWildcards() {
        // Object wildcard
        testParsePath("$.*", 2, PathSegment.Root.class, PathSegment.Wildcard.class);
        testParsePath("$.users.*", 3, PathSegment.Root.class, PathSegment.Name.class, PathSegment.Wildcard.class);

        // Array wildcard
        testParsePath("$.array[*]", 3, PathSegment.Root.class, PathSegment.Name.class, PathSegment.Wildcard.class);
        testParsePath("$[*]", 2, PathSegment.Root.class, PathSegment.Wildcard.class);
    }

    @Test
    public void testDescendantDescent() {
        testParsePath("$..name", 3, PathSegment.Root.class, PathSegment.Descendant.class, PathSegment.Name.class);
        testParsePath("$..user..name", 5, PathSegment.Root.class, PathSegment.Descendant.class,
                PathSegment.Name.class, PathSegment.Descendant.class, PathSegment.Name.class);
        testExpr("$..user..name", 3, "..");
        testParsePath("$..[0]", 3, PathSegment.Root.class, PathSegment.Descendant.class, PathSegment.Index.class);

        testParsePathFailure("$....name", "name");
        testParsePathFailure("$...name", "name");
    }

    @Test
    public void testSlices() {
        // Basic slices
        testParsePath("$.array[1:5]", 3, PathSegment.Root.class, PathSegment.Name.class, PathSegment.Slice.class);
        testExpr("$.array[1:]", 2, "[1:]");
        testExpr("$.array[:5]", 2, "[:5]");
        testExpr("$.array[::2]", 2, "[::2]");

        // Full slice syntax
        testExpr("$.array[1:10:2]", 2, "[1:10:2]");
        testExpr("$.array[-5:-1]", 2, "[-5:-1]");

        // With descendant
        testExpr("$..array[1:5]", 3, "[1:5]");
    }

    @Test
    public void testUnions() {
        // Multiple indices
        testParsePath("$[1,3,5]", 2, PathSegment.Root.class, PathSegment.Union.class);
        testParsePath("$.array[0,2,-1]", 3, PathSegment.Root.class, PathSegment.Name.class, PathSegment.Union.class);
        testExpr("$.array[0,2,-1]", 2, "[0,2,-1]");

        // Multiple names
        testParsePath("$['name', 'age']", 2, PathSegment.Root.class, PathSegment.Union.class);
        testParsePath("$[\"first name\",\"last name\"]", 2, PathSegment.Root.class, PathSegment.Union.class);
        PathSegment pt = Paths.parsePath("$[\"first name\",\"last name\"]")[1];
        pt = ((PathSegment.Union) pt).union[1];
        assertEquals("last name", ((PathSegment.Name) pt).name);
        testParsePathFailure("$[name,age]", "name");

        // Mixed union
        testParsePath("$[1,'name',3]", 2, PathSegment.Root.class, PathSegment.Union.class);
    }

    @Test
    public void testComplexUnions() {
        // Union with slices
        testParsePath("$[1,3,5:10]", 2, PathSegment.Root.class, PathSegment.Union.class);
        testExpr("$.array[1:5,7,9]", 2, "[1:5,7,9]");

        // Union with wildcard
        testParsePathFailure("$[*,1,3]", "'*'");
    }

    @Test
    public void testEscapedCharacters() {
        // Escaped quotes in names
        testParsePath("$['name\\'with\\'quotes']", 2, PathSegment.Root.class, PathSegment.Name.class);

        // Names with special characters
        testParsePath("$['a[b]c']", 2, PathSegment.Root.class, PathSegment.Name.class);
        testParsePath("$['d,e:f']", 2, PathSegment.Root.class, PathSegment.Name.class);
        testParsePath("$['test]name']", 2, PathSegment.Root.class, PathSegment.Name.class);

        // Union with special characters
        testParsePath("$['a,b','c:d','e[f]g']", 2, PathSegment.Root.class, PathSegment.Union.class);
        PathSegment pt = Paths.parsePath("$['a,b','c:d','e[f]g']")[1];
        pt = ((PathSegment.Union) pt).union[2];
        assertEquals("['e[f]g']", pt.toString());
    }

    @Test
    public void testComplexPaths() {
        // Deeply nested paths
        testParsePath("$.users[0]['address'].street", 5, PathSegment.Root.class,
                PathSegment.Name.class, PathSegment.Index.class, PathSegment.Name.class, PathSegment.Name.class);

        // Complex with recursive and union
        testParsePath("$..users[0,1]['name','age']..value", 7,
                PathSegment.Root.class, PathSegment.Descendant.class, PathSegment.Name.class, PathSegment.Union.class,
                PathSegment.Union.class, PathSegment.Descendant.class, PathSegment.Name.class);
    }

    @Test
    public void testEdgeCases() {
        // Empty bracket content should fail
        testParsePathFailure("$[]", "Empty");

        // Missing closing bracket
        testParsePathFailure("$[0", "Missing closing");

        // Unclosed quote
        testParsePathFailure("$['name", "Unclosed quote");

        // Invalid slice step
        testParsePathFailure("$[::0]", "Slice step cannot be 0");

        // Too many slice parts
        testParsePathFailure("$[1:2:3:4]", "Invalid slice syntax");
    }

    @Test
    public void testToStringRoundTrip() {
        // Test that toString() produces valid paths that can be recompiled
        testRoundTrip("$.name");
        testRoundTrip("$.users[0].name");
        testRoundTrip("$['first name']");
        testRoundTrip("$[1,3,5]");
        testRoundTrip("$[1:5:2]");
        testRoundTrip("$..name");
        testRoundTrip("$.*");
        testRoundTrip("$[*]");
        testRoundTrip("$['a,b','c:d']");
        testRoundTrip("$.length()");
        testRoundTrip("$..values.sum()");
        testRoundTrip("$[*].length()");
    }

    @Test
    public void testFunction() {
        // Object wildcard
        testParsePath("$..max()", 3, PathSegment.Root.class, PathSegment.Descendant.class, PathSegment.Function.class);
        testParsePath("$[*].length()", 3, PathSegment.Root.class, PathSegment.Wildcard.class, PathSegment.Function.class);
    }

    @Test
    public void testFindMatchingParen() {
        int end;
        end = Paths.findMatchingParen("?(@.name == \"foo(bar)\")", 1);
        assertEquals(22, end);
        end = Paths.findMatchingParen("(?(@.a > 1 && @.b < 2))", 0);
        assertEquals(22, end);
        end = Paths.findMatchingParen("(\"(abc)\")", 0);
        assertEquals(8, end);
    }

    @Test
    public void testParseFunctionArgs() {
        List<String> args = Paths.parseFunctionArgs("10, 'abc', func2(1,2), \"hello(world)\"");
        System.out.println(new JsonArray(args));
        System.out.println(args.get(0));
        System.out.println(args.get(1));
        assertEquals(4, args.size());
        assertEquals("\"hello(world)\"", args.get(3));
    }

    @Test
    public void testFilter1() {
        FilterExpr fe = Paths.parseFilter("@.age > 30");
        System.out.println("fe=" + fe);
        assertEquals("(@.age > 30.0)", fe.toString());

        fe = Paths.parseFilter("(@.age > 30 || @..['bb'].count() < 2)");
        System.out.println("fe=" + fe);
        assertEquals("((@.age > 30.0) || (@..['bb'].count() < 2.0))", fe.toString());

        fe = Paths.parseFilter("@.tags && @.tags.contains('urgent')");
        System.out.println("fe=" + fe);
        assertEquals("(@.tags && @.tags.contains('urgent'))", fe.toString());

        JsonPath path = JsonPath.compile("$.babies[*][?@.age > 2].name");
        System.out.println("path=" + path);
    }

    @Test
    public void testFilter2() {
        FilterExpr fe = Paths.parseFilter("@.name == 'Bob'");
        System.out.println("fe=" + fe);
        assertEquals("(@.name == \"Bob\")", fe.toString());

        fe = Paths.parseFilter("@.active");
        System.out.println("fe=" + fe);
        assertEquals("@.active", fe.toString());

        fe = Paths.parseFilter("@.age >= 30 && !@.active");
        System.out.println("fe=" + fe);
        assertEquals("((@.age >= 30.0) && !@.active)", fe.toString());

        fe = Paths.parseFilter("@.members[?@.age > 30]");
        System.out.println("fe=" + fe);
        assertEquals("@.members[?@.age > 30]", fe.toString());
    }



    /// Private

    // Helper methods
    private void testParsePath(String path, int expectedTokenCount, Class<?>... expectedTokenTypes) {
        PathSegment[] segments = Paths.parsePath(path);
        assertNotNull(segments, "Tokens should not be null for path: " + path);
        assertEquals(expectedTokenCount, segments.length, "Token count mismatch for path: " + path);

        for (int i = 0; i < expectedTokenTypes.length; i++) {
            assertTrue(expectedTokenTypes[i].isInstance(segments[i]),
                    "Token type mismatch at position " + i + " for path: " + path);
        }
    }

    private void testParsePathFailure(String path, String expectedError) {
        try {
            Paths.parsePath(path);
            fail("Expected JsonException for path: " + path);
        } catch (JsonException e) {
            assertTrue(e.getMessage().toLowerCase().contains(expectedError.toLowerCase()),
                    "Error message should contain: " + expectedError + ", but got: " + e.getMessage());
        }
    }

    private void testExpr(String path, int tokenIndex, String expectedExpr) {
        PathSegment[] segments = Paths.parsePath(path);
        if (tokenIndex >= segments.length) {
            fail("tokenIndex '" + tokenIndex + "' >= segments.size '" + segments.length + "'");
        }

        PathSegment actual = segments[tokenIndex];
        assertEquals(expectedExpr, actual.toString());
    }

    private void testRoundTrip(String originalPath) {
        PathSegment[] segments = Paths.parsePath(originalPath);
        String rebuilt = Paths.toPathExpr(segments);

        // Recompile the rebuilt path to ensure it's valid
        PathSegment[] roundTripTokens = Paths.parsePath(rebuilt);
        assertEquals(segments.length, roundTripTokens.length,
                "Round-trip token count mismatch for: " + originalPath);

        // Compare token types
        for (int i = 0; i < segments.length; i++) {
            assertEquals(segments[i].getClass(), roundTripTokens[i].getClass(),
                    "Token type mismatch at position " + i + " for: " + originalPath);
        }
    }

}