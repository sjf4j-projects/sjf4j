package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.NodeException;
import org.sjf4j.regex.RegexEngine;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathSyntaxValidationTest {


    @Test
    void rejectsInvalidPointerSegments() {
        assertThrows(NodeException.class, () -> PathSyntax.toPointerExpr(new PathSegment[]{new PathSegment.Name(PathSegment.Root.INSTANCE, "a"), PathSegment.Root.INSTANCE}));
        assertThrows(NodeException.class, () -> PathSyntax.toPointerExpr(new PathSegment[]{PathSegment.Root.INSTANCE, new PathSegment.Append(PathSegment.Root.INSTANCE), new PathSegment.Name(PathSegment.Root.INSTANCE, "a")}));
        assertThrows(NodeException.class, () -> PathSyntax.toPointerExpr(new PathSegment[]{PathSegment.Root.INSTANCE, new PathSegment.Descendant(PathSegment.Root.INSTANCE)}));
    }

    @Test
    void parsesAndRejectsBoundaryPathExpressions() {
        assertEquals("$.name", PathSyntax.toPathExpr(PathSyntax.parsePath("name", RegexEngine.JDK_ENGINE)));
        assertEquals("@.name", PathSyntax.toPathExpr(PathSyntax.parsePath("@.name", RegexEngine.JDK_ENGINE)));
        assertEquals("$.sum(1, 2)", PathSyntax.toPathExpr(PathSyntax.parsePath("$.sum(1, 2)", RegexEngine.JDK_ENGINE)));
        assertEquals("$.items[?(@.age > 1.0)]", PathSyntax.toPathExpr(PathSyntax.parsePath("$.items[?@.age > 1]", RegexEngine.JDK_ENGINE)));
        assertEquals("$.items[?(@.isbn == null)]", PathSyntax.toPathExpr(PathSyntax.parsePath("$.items[?@.isbn == null]", RegexEngine.JDK_ENGINE)));
        assertEquals("$.items[?(@.enabled == true)]", PathSyntax.toPathExpr(PathSyntax.parsePath("$.items[?@.enabled == true]", RegexEngine.JDK_ENGINE)));
        assertEquals("$.items[?(@.enabled == false)]", PathSyntax.toPathExpr(PathSyntax.parsePath("$.items[?@.enabled == false]", RegexEngine.JDK_ENGINE)));

        assertThrows(NodeException.class, () -> PathSyntax.parsePath("$.", RegexEngine.JDK_ENGINE));
        assertThrows(NodeException.class, () -> PathSyntax.parsePath("$..", RegexEngine.JDK_ENGINE));
        assertThrows(NodeException.class, () -> PathSyntax.parsePath("$.name(", RegexEngine.JDK_ENGINE));
        assertThrows(NodeException.class, () -> PathSyntax.parsePath("$#", RegexEngine.JDK_ENGINE));
    }

    @Test
    void parsesNestedFunctionArgumentsAndParentheses() {
        assertThrows(NodeException.class, () -> PathSyntax._findMatchingParen("(abc", 0));
        assertEquals(4, PathSyntax._findMatchingParen("(')')x", 0));
        assertEquals(Collections.singletonList(""), PathSyntax._parseFunctionArgs("   "));
        assertEquals(Arrays.asList("1", "nested(2,3)", "'x,y'", "/a(b)/"), PathSyntax._parseFunctionArgs("1, nested(2,3), 'x,y', /a(b)/"));
    }

    static class SamplePojo {
        public String value;
    }
}
