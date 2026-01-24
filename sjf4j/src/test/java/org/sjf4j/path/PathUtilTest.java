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

public class PathUtilTest {

    /// Pointer

    @Test
    void testRootOnly() {
        List<PathToken> tokens = PathUtil.tokenizePointer("");
        assertEquals(1, tokens.size());
        assertInstanceOf(PathToken.Root.class, tokens.get(0));

        tokens = PathUtil.tokenizePointer("/");
        assertEquals(1, tokens.size());
        assertInstanceOf(PathToken.Root.class, tokens.get(0));
    }

    @Test
    void testSimplePath() {
        List<PathToken> tokens = PathUtil.tokenizePointer("/users/name");
        assertEquals(3, tokens.size());
        assertInstanceOf(PathToken.Root.class, tokens.get(0));
        assertInstanceOf(PathToken.Name.class, tokens.get(1));
        assertEquals("users", ((PathToken.Name) tokens.get(1)).name);
        assertInstanceOf(PathToken.Name.class, tokens.get(2));
        assertEquals("name", ((PathToken.Name) tokens.get(2)).name);
    }

    @Test
    void testNumericIndex() {
        List<PathToken> tokens = PathUtil.tokenizePointer("/users/0/posts/12");
        assertEquals(5, tokens.size());
        assertInstanceOf(PathToken.Index.class, tokens.get(2));
        assertEquals(0, ((PathToken.Index) tokens.get(2)).index);
        assertInstanceOf(PathToken.Index.class, tokens.get(4));
        assertEquals(12, ((PathToken.Index) tokens.get(4)).index);
    }

    @Test
    void testAppendToken() {
        List<PathToken> tokens = PathUtil.tokenizePointer("/users/-/name");
        assertEquals(4, tokens.size());
        assertInstanceOf(PathToken.Append.class, tokens.get(2));
    }

    @Test
    void testEscapeSequences() {
        // "~0" -> "~", "~1" -> "/"
        List<PathToken> tokens = PathUtil.tokenizePointer("/a~1b/c~0d/~1~0");
        assertEquals(4, tokens.size());
        assertEquals("a/b", ((PathToken.Name) tokens.get(1)).name);
        assertEquals("c~d", ((PathToken.Name) tokens.get(2)).name);
        assertEquals("/~", ((PathToken.Name) tokens.get(3)).name);
    }

    @Test
    void testConsecutiveSlashes() {
        List<PathToken> tokens = PathUtil.tokenizePointer("/a//b///c");
        assertEquals(7, tokens.size());
        assertEquals("a", ((PathToken.Name) tokens.get(1)).name);
        assertEquals("b", ((PathToken.Name) tokens.get(3)).name);
        assertEquals("c", ((PathToken.Name) tokens.get(6)).name);
    }

    @Test
    void testNumericEdgeCases() {
        // leading zeros
        List<PathToken> tokens = PathUtil.tokenizePointer("/01/002");
        assertEquals(3, tokens.size());
        assertEquals(1, ((PathToken.Index) tokens.get(1)).index);
        assertEquals(2, ((PathToken.Index) tokens.get(2)).index);

        // large number
        tokens = PathUtil.tokenizePointer("/1234567890");
        assertEquals(2, tokens.size());
        assertEquals(1234567890, ((PathToken.Index) tokens.get(1)).index);
    }

    @Test
    void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> PathUtil.tokenizePointer(null));
        assertThrows(IllegalArgumentException.class, () -> PathUtil.tokenizePointer("users")); // missing '/'
    }

    @Test
    void testEmptySegments() {
        List<PathToken> tokens = PathUtil.tokenizePointer("/a//b");
        assertEquals(4, tokens.size());
        assertEquals("a", ((PathToken.Name) tokens.get(1)).name);
        assertEquals("", ((PathToken.Name) tokens.get(2)).name);
        assertEquals("b", ((PathToken.Name) tokens.get(3)).name);
    }

    @Test
    void testEmptyEnds() {
        List<PathToken> tokens = PathUtil.tokenizePointer("/a/b/");
        assertEquals(4, tokens.size());
        assertEquals("a", ((PathToken.Name) tokens.get(1)).name);
        assertEquals("b", ((PathToken.Name) tokens.get(2)).name);
        assertEquals("", ((PathToken.Name) tokens.get(3)).name);
    }

    @Test
    void testOnlyAppend() {
        List<PathToken> tokens = PathUtil.tokenizePointer("/-");
        assertEquals(2, tokens.size());
        assertInstanceOf(PathToken.Append.class, tokens.get(1));
    }


    /// Path

    @Test
    public void testBasicPaths() {
        // Root only
        testTokenizePath("$", 1, PathToken.Root.class);

        // Simple field access
        testTokenizePath("$.name", 2, PathToken.Root.class, PathToken.Name.class);
        testTokenizePath("$.user.name", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Name.class);
        testExpr("$.user.name", 2, ".name");

        // Array index
        testTokenizePath("$.array[0]", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Index.class);
        testTokenizePath("$.array[-1]", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Index.class);
        testExpr("$.array[-1]", 2, "[-1]");
    }

    @Test
    public void testBracketNotation() {
        // Quoted field names
        testTokenizePath("$['name']", 2, PathToken.Root.class, PathToken.Name.class);
        testTokenizePath("$[\"first name\"]", 2, PathToken.Root.class, PathToken.Name.class);
        testExpr("$[\"first name\"]", 1, "['first name']");

        // Unquoted field names in brackets
        testTokenizePathFailure("$[name]", "name");

        // Mixed dot and bracket
        testTokenizePath("$.user['name']", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Name.class);
    }

    @Test
    public void testWildcards() {
        // Object wildcard
        testTokenizePath("$.*", 2, PathToken.Root.class, PathToken.Wildcard.class);
        testTokenizePath("$.users.*", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Wildcard.class);

        // Array wildcard
        testTokenizePath("$.array[*]", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Wildcard.class);
        testTokenizePath("$[*]", 2, PathToken.Root.class, PathToken.Wildcard.class);
    }

    @Test
    public void testDescendantDescent() {
        testTokenizePath("$..name", 3, PathToken.Root.class, PathToken.Descendant.class, PathToken.Name.class);
        testTokenizePath("$..user..name", 5, PathToken.Root.class, PathToken.Descendant.class,
                PathToken.Name.class, PathToken.Descendant.class, PathToken.Name.class);
        testExpr("$..user..name", 3, "..");
        testTokenizePath("$..[0]", 3, PathToken.Root.class, PathToken.Descendant.class, PathToken.Index.class);

        testTokenizePathFailure("$....name", "name");
        testTokenizePathFailure("$...name", "name");
    }

    @Test
    public void testSlices() {
        // Basic slices
        testTokenizePath("$.array[1:5]", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Slice.class);
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
        testTokenizePath("$[1,3,5]", 2, PathToken.Root.class, PathToken.Union.class);
        testTokenizePath("$.array[0,2,-1]", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Union.class);
        testExpr("$.array[0,2,-1]", 2, "[0,2,-1]");

        // Multiple names
        testTokenizePath("$['name', 'age']", 2, PathToken.Root.class, PathToken.Union.class);
        testTokenizePath("$[\"first name\",\"last name\"]", 2, PathToken.Root.class, PathToken.Union.class);
        PathToken pt = PathUtil.tokenizePath("$[\"first name\",\"last name\"]").get(1);
        pt = ((PathToken.Union) pt).union.get(1);
        assertEquals("last name", ((PathToken.Name) pt).name);
        testTokenizePathFailure("$[name,age]", "name");

        // Mixed union
        testTokenizePath("$[1,'name',3]", 2, PathToken.Root.class, PathToken.Union.class);
    }

    @Test
    public void testComplexUnions() {
        // Union with slices
        testTokenizePath("$[1,3,5:10]", 2, PathToken.Root.class, PathToken.Union.class);
        testExpr("$.array[1:5,7,9]", 2, "[1:5,7,9]");

        // Union with wildcard
        testTokenizePathFailure("$[*,1,3]", "'*'");
    }

    @Test
    public void testEscapedCharacters() {
        // Escaped quotes in names
        testTokenizePath("$['name\\'with\\'quotes']", 2, PathToken.Root.class, PathToken.Name.class);

        // Names with special characters
        testTokenizePath("$['a[b]c']", 2, PathToken.Root.class, PathToken.Name.class);
        testTokenizePath("$['d,e:f']", 2, PathToken.Root.class, PathToken.Name.class);
        testTokenizePath("$['test]name']", 2, PathToken.Root.class, PathToken.Name.class);

        // Union with special characters
        testTokenizePath("$['a,b','c:d','e[f]g']", 2, PathToken.Root.class, PathToken.Union.class);
        PathToken pt = PathUtil.tokenizePath("$['a,b','c:d','e[f]g']").get(1);
        pt = ((PathToken.Union) pt).union.get(2);
        assertEquals("['e[f]g']", pt.toString());
    }

    @Test
    public void testComplexPaths() {
        // Deeply nested paths
        testTokenizePath("$.users[0]['address'].street", 5, PathToken.Root.class,
                PathToken.Name.class, PathToken.Index.class, PathToken.Name.class, PathToken.Name.class);

        // Complex with recursive and union
        testTokenizePath("$..users[0,1]['name','age']..value", 7,
                PathToken.Root.class, PathToken.Descendant.class, PathToken.Name.class, PathToken.Union.class,
                PathToken.Union.class, PathToken.Descendant.class, PathToken.Name.class);
    }

    @Test
    public void testEdgeCases() {
        // Empty bracket content should fail
        testTokenizePathFailure("$[]", "Empty");

        // Missing closing bracket
        testTokenizePathFailure("$[0", "Missing closing");

        // Unclosed quote
        testTokenizePathFailure("$['name", "Unclosed quote");

        // Invalid slice step
        testTokenizePathFailure("$[::0]", "Slice step cannot be 0");

        // Too many slice parts
        testTokenizePathFailure("$[1:2:3:4]", "Invalid slice syntax");
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
        testTokenizePath("$..max()", 3, PathToken.Root.class, PathToken.Descendant.class, PathToken.Function.class);
        testTokenizePath("$[*].length()", 3, PathToken.Root.class, PathToken.Wildcard.class, PathToken.Function.class);
    }

    @Test
    public void testFindMatchingParen() {
        int end;
        end = PathUtil.findMatchingParen("?(@.name == \"foo(bar)\")", 1);
        assertEquals(22, end);
        end = PathUtil.findMatchingParen("(?(@.a > 1 && @.b < 2))", 0);
        assertEquals(22, end);
        end = PathUtil.findMatchingParen("(\"(abc)\")", 0);
        assertEquals(8, end);
    }

    @Test
    public void testParseFunctionArgs() {
        List<String> args = PathUtil.parseFunctionArgs("10, 'abc', func2(1,2), \"hello(world)\"");
        System.out.println(new JsonArray(args));
        System.out.println(args.get(0));
        System.out.println(args.get(1));
        assertEquals(4, args.size());
        assertEquals("\"hello(world)\"", args.get(3));
    }

    @Test
    public void testFilter1() {
        FilterExpr fe = PathUtil.parseFilter("@.age > 30");
        System.out.println("fe=" + fe);
        assertEquals("(@.age > 30.0)", fe.toString());

        fe = PathUtil.parseFilter("(@.age > 30 || @..['bb'].count() < 2)");
        System.out.println("fe=" + fe);
        assertEquals("((@.age > 30.0) || (@..['bb'].count() < 2.0))", fe.toString());

        fe = PathUtil.parseFilter("@.tags && @.tags.contains('urgent')");
        System.out.println("fe=" + fe);
        assertEquals("(@.tags && @.tags.contains('urgent'))", fe.toString());

        JsonPath path = JsonPath.compile("$.babies[*][?@.age > 2].name");
        System.out.println("path=" + path);
    }

    @Test
    public void testFilter2() {
        FilterExpr fe = PathUtil.parseFilter("@.name == 'Bob'");
        System.out.println("fe=" + fe);
        assertEquals("(@.name == \"Bob\")", fe.toString());

        fe = PathUtil.parseFilter("@.active");
        System.out.println("fe=" + fe);
        assertEquals("@.active", fe.toString());

        fe = PathUtil.parseFilter("@.age >= 30 && !@.active");
        System.out.println("fe=" + fe);
        assertEquals("((@.age >= 30.0) && !@.active)", fe.toString());

        fe = PathUtil.parseFilter("@.members[?@.age > 30]");
        System.out.println("fe=" + fe);
        assertEquals("@.members[?@.age > 30]", fe.toString());
    }



    /// Private

    // Helper methods
    private void testTokenizePath(String path, int expectedTokenCount, Class<?>... expectedTokenTypes) {
        List<PathToken> tokens = PathUtil.tokenizePath(path);
        assertNotNull(tokens, "Tokens should not be null for path: " + path);
        assertEquals(expectedTokenCount, tokens.size(), "Token count mismatch for path: " + path);

        for (int i = 0; i < expectedTokenTypes.length; i++) {
            assertTrue(expectedTokenTypes[i].isInstance(tokens.get(i)),
                    "Token type mismatch at position " + i + " for path: " + path);
        }
    }

    private void testTokenizePathFailure(String path, String expectedError) {
        try {
            PathUtil.tokenizePath(path);
            fail("Expected JsonException for path: " + path);
        } catch (JsonException e) {
            assertTrue(e.getMessage().toLowerCase().contains(expectedError.toLowerCase()),
                    "Error message should contain: " + expectedError + ", but got: " + e.getMessage());
        }
    }

    private void testExpr(String path, int tokenIndex, String expectedExpr) {
        List<PathToken> tokens = PathUtil.tokenizePath(path);
        if (tokenIndex >= tokens.size()) {
            fail("tokenIndex '" + tokenIndex + "' >= tokens.size '" + tokens.size() + "'");
        }

        PathToken actual = tokens.get(tokenIndex);
        assertEquals(expectedExpr, actual.toString());
    }

    private void testRoundTrip(String originalPath) {
        List<PathToken> tokens = PathUtil.tokenizePath(originalPath);
        String rebuilt = PathUtil.toPathExpr(tokens);

        // Recompile the rebuilt path to ensure it's valid
        List<PathToken> roundTripTokens = PathUtil.tokenizePath(rebuilt);
        assertEquals(tokens.size(), roundTripTokens.size(),
                "Round-trip token count mismatch for: " + originalPath);

        // Compare token types
        for (int i = 0; i < tokens.size(); i++) {
            assertEquals(tokens.get(i).getClass(), roundTripTokens.get(i).getClass(),
                    "Token type mismatch at position " + i + " for: " + originalPath);
        }
    }

}