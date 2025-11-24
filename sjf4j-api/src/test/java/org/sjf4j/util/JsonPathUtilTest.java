package org.sjf4j.util;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonException;
import org.sjf4j.PathToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class JsonPathUtilTest {

    @Test
    public void testBasicPaths() {
        // Root only
        testCompile("$", 1, PathToken.Root.class);

        // Simple field access
        testCompile("$.name", 2, PathToken.Root.class, PathToken.Name.class);
        testCompile("$.user.name", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Name.class);
        testExpr("$.user.name", 2, ".name");

        // Array index
        testCompile("$.array[0]", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Index.class);
        testCompile("$.array[-1]", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Index.class);
        testExpr("$.array[-1]", 2, "[-1]");
    }

    @Test
    public void testBracketNotation() {
        // Quoted field names
        testCompile("$['name']", 2, PathToken.Root.class, PathToken.Name.class);
        testCompile("$[\"first name\"]", 2, PathToken.Root.class, PathToken.Name.class);
        testExpr("$[\"first name\"]", 1, "['first name']");

        // Unquoted field names in brackets
        testCompileFailure("$[name]", "name");

        // Mixed dot and bracket
        testCompile("$.user['name']", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Name.class);
    }

    @Test
    public void testWildcards() {
        // Object wildcard
        testCompile("$.*", 2, PathToken.Root.class, PathToken.Wildcard.class);
        testCompile("$.users.*", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Wildcard.class);

        // Array wildcard
        testCompile("$.array[*]", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Wildcard.class);
        testCompile("$[*]", 2, PathToken.Root.class, PathToken.Wildcard.class);
    }

    @Test
    public void testDescendantDescent() {
        testCompile("$..name", 3, PathToken.Root.class, PathToken.Descendant.class, PathToken.Name.class);
        testCompile("$..user..name", 5, PathToken.Root.class, PathToken.Descendant.class,
                PathToken.Name.class, PathToken.Descendant.class, PathToken.Name.class);
        testExpr("$..user..name", 3, "..");
        testCompile("$..[0]", 3, PathToken.Root.class, PathToken.Descendant.class, PathToken.Index.class);

        testCompileFailure("$....name", "name");
        testCompileFailure("$...name", "name");
    }

    @Test
    public void testSlices() {
        // Basic slices
        testCompile("$.array[1:5]", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Slice.class);
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
        testCompile("$[1,3,5]", 2, PathToken.Root.class, PathToken.Union.class);
        testCompile("$.array[0,2,-1]", 3, PathToken.Root.class, PathToken.Name.class, PathToken.Union.class);
        testExpr("$.array[0,2,-1]", 2, "[0,2,-1]");

        // Multiple names
        testCompile("$['name','age']", 2, PathToken.Root.class, PathToken.Union.class);
        testCompile("$[\"first name\",\"last name\"]", 2, PathToken.Root.class, PathToken.Union.class);
        PathToken pt = JsonPathUtil.compile("$[\"first name\",\"last name\"]").get(1);
        pt = ((PathToken.Union) pt).union.get(1);
        assertEquals("last name", ((PathToken.Name) pt).name);
        testCompileFailure("$[name,age]", "name");

        // Mixed union
        testCompile("$[1,'name',3]", 2, PathToken.Root.class, PathToken.Union.class);
    }

    @Test
    public void testComplexUnions() {
        // Union with slices
        testCompile("$[1,3,5:10]", 2, PathToken.Root.class, PathToken.Union.class);
        testExpr("$.array[1:5,7,9]", 2, "[1:5,7,9]");

        // Union with wildcard
        testCompileFailure("$[*,1,3]", "'*'");
    }

    @Test
    public void testEscapedCharacters() {
        // Escaped quotes in names
        testCompile("$['name\\'with\\'quotes']", 2, PathToken.Root.class, PathToken.Name.class);

        // Names with special characters
        testCompile("$['a[b]c']", 2, PathToken.Root.class, PathToken.Name.class);
        testCompile("$['d,e:f']", 2, PathToken.Root.class, PathToken.Name.class);
        testCompile("$['test]name']", 2, PathToken.Root.class, PathToken.Name.class);

        // Union with special characters
        testCompile("$['a,b','c:d','e[f]g']", 2, PathToken.Root.class, PathToken.Union.class);
        PathToken pt = JsonPathUtil.compile("$['a,b','c:d','e[f]g']").get(1);
        pt = ((PathToken.Union) pt).union.get(2);
        assertEquals("['e[f]g']", pt.toString());
    }

    @Test
    public void testComplexPaths() {
        // Deeply nested paths
        testCompile("$.users[0]['address'].street", 5, PathToken.Root.class,
                PathToken.Name.class, PathToken.Index.class, PathToken.Name.class, PathToken.Name.class);

        // Complex with recursive and union
        testCompile("$..users[0,1]['name','age']..value", 7,
                PathToken.Root.class, PathToken.Descendant.class, PathToken.Name.class, PathToken.Union.class,
                PathToken.Union.class, PathToken.Descendant.class, PathToken.Name.class);
    }

    @Test
    public void testEdgeCases() {
        // Empty bracket content should fail
        testCompileFailure("$[]", "Empty");

        // Missing closing bracket
        testCompileFailure("$[0", "Missing closing");

        // Unclosed quote
        testCompileFailure("$['name", "Unclosed quote");

        // Invalid slice step
        testCompileFailure("$[::0]", "Slice step cannot be 0");

        // Too many slice parts
        testCompileFailure("$[1:2:3:4]", "Invalid slice syntax");
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
        testCompile("$..max()", 3, PathToken.Root.class, PathToken.Descendant.class, PathToken.Function.class);
        testCompile("$[*].length()", 3, PathToken.Root.class, PathToken.Wildcard.class, PathToken.Function.class);
    }


    /// Private

    // Helper methods
    private void testCompile(String path, int expectedTokenCount, Class<?>... expectedTokenTypes) {
        List<PathToken> tokens = JsonPathUtil.compile(path);
        assertNotNull(tokens, "Tokens should not be null for path: " + path);
        assertEquals(expectedTokenCount, tokens.size(), "Token count mismatch for path: " + path);

        for (int i = 0; i < expectedTokenTypes.length; i++) {
            assertTrue(expectedTokenTypes[i].isInstance(tokens.get(i)),
                    "Token type mismatch at position " + i + " for path: " + path);
        }
    }

    private void testCompileFailure(String path, String expectedError) {
        try {
            JsonPathUtil.compile(path);
            fail("Expected JsonException for path: " + path);
        } catch (JsonException e) {
            assertTrue(e.getMessage().toLowerCase().contains(expectedError.toLowerCase()),
                    "Error message should contain: " + expectedError + ", but got: " + e.getMessage());
        }
    }

    private void testExpr(String path, int tokenIndex, String expectedExpr) {
        List<PathToken> tokens = JsonPathUtil.compile(path);
        if (tokenIndex >= tokens.size()) {
            fail("tokenIndex '" + tokenIndex + "' >= tokens.size '" + tokens.size() + "'");
        }

        PathToken actual = tokens.get(tokenIndex);
        assertEquals(expectedExpr, actual.toString());
    }

    private void testRoundTrip(String originalPath) {
        List<PathToken> tokens = JsonPathUtil.compile(originalPath);
        String rebuilt = JsonPathUtil.genExpr(tokens);

        // Recompile the rebuilt path to ensure it's valid
        List<PathToken> roundTripTokens = JsonPathUtil.compile(rebuilt);
        assertEquals(tokens.size(), roundTripTokens.size(),
                "Round-trip token count mismatch for: " + originalPath);

        // Compare token types
        for (int i = 0; i < tokens.size(); i++) {
            assertEquals(tokens.get(i).getClass(), roundTripTokens.get(i).getClass(),
                    "Token type mismatch at position " + i + " for: " + originalPath);
        }
    }
}