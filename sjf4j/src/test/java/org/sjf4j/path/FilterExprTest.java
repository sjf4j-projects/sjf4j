package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterExprTest {

    @Test
    void testTruthAndComparisons() {
        assertFalse(FilterExpr.truth(null));
        assertTrue(FilterExpr.truth(true));
        assertFalse(FilterExpr.truth(false));
        assertFalse(FilterExpr.truth(0));
        assertTrue(FilterExpr.truth(1));
        assertFalse(FilterExpr.truth(""));
        assertTrue(FilterExpr.truth("x"));
        assertFalse(FilterExpr.truth(new JsonArray()));
        assertTrue(FilterExpr.truth(JsonArray.of(1)));
        assertTrue(FilterExpr.truth(JsonObject.of("a", 1)));

        assertTrue(FilterExpr.gt(2, 1));
        assertTrue(FilterExpr.ge(2, 2));
        assertTrue(FilterExpr.lt(1, 2));
        assertTrue(FilterExpr.le(2, 2));
        assertTrue(FilterExpr.gt("b", "a"));
        assertFalse(FilterExpr.gt("a", 1));
        assertFalse(FilterExpr.lt(true, false));
        assertTrue(FilterExpr.eq(1, 1L));
        assertFalse(FilterExpr.ge("a", 1));
        assertFalse(FilterExpr.le(true, false));
    }

    @Test
    void testMatch() {
        Pattern pattern = Pattern.compile("ha");
        assertTrue(FilterExpr.match("han", pattern));
        assertTrue(FilterExpr.match(JsonArray.of("no", "han"), pattern));
        assertFalse(FilterExpr.match(JsonArray.of(1, 2), pattern));
        assertFalse(FilterExpr.match(null, pattern));
        assertFalse(FilterExpr.match("han", "not-pattern"));
        assertFalse(FilterExpr.match(JsonObject.of("a", 1), pattern));
    }

    @Test
    void testExpressionImplementations() {
        FilterExpr.LiteralExpr nullExpr = new FilterExpr.LiteralExpr(null);
        FilterExpr.LiteralExpr stringExpr = new FilterExpr.LiteralExpr("han");
        FilterExpr.LiteralExpr numberExpr = new FilterExpr.LiteralExpr(12);
        FilterExpr.LiteralExpr boolExpr = new FilterExpr.LiteralExpr(true);
        FilterExpr.LiteralExpr otherExpr = new FilterExpr.LiteralExpr(JsonObject.of("a", 1));
        assertEquals("null", nullExpr.toString());
        assertEquals("\"han\"", stringExpr.toString());
        assertEquals("12", numberExpr.toString());
        assertEquals("true", boolExpr.toString());
        assertEquals("J{a=1}", otherExpr.toString());

        JsonObject root = JsonObject.of("name", "root", "child", JsonObject.of("name", "current"));
        FilterExpr.PathExpr rootPath = new FilterExpr.PathExpr("$.name");
        FilterExpr.PathExpr currentPath = new FilterExpr.PathExpr("@.name");
        assertEquals("root", rootPath.eval(root, root.getJsonObject("child")));
        assertEquals("current", currentPath.eval(root, root.getJsonObject("child")));
        assertEquals("$.name", rootPath.toString());

        FilterExpr.UnaryExpr truthy = new FilterExpr.UnaryExpr(true, currentPath);
        FilterExpr.UnaryExpr falsy = new FilterExpr.UnaryExpr(false, new FilterExpr.LiteralExpr(false));
        assertEquals(Boolean.TRUE, truthy.eval(root, root.getJsonObject("child")));
        assertEquals(Boolean.TRUE, falsy.eval(root, root.getJsonObject("child")));
        assertEquals("@.name", truthy.toString());
        assertEquals("!false", falsy.toString());

        FilterExpr left = new FilterExpr.LiteralExpr(2);
        FilterExpr right = new FilterExpr.LiteralExpr(1);
        assertEquals(Boolean.FALSE, new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.EQ).eval(root, root));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.NE).eval(root, root));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.GT).eval(root, root));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.GE).eval(root, root));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(right, left, FilterExpr.Op.LT).eval(root, root));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(right, left, FilterExpr.Op.LE).eval(root, root));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(new FilterExpr.LiteralExpr(true), new FilterExpr.LiteralExpr(true), FilterExpr.Op.AND).eval(root, root));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(new FilterExpr.LiteralExpr(false), new FilterExpr.LiteralExpr(true), FilterExpr.Op.OR).eval(root, root));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(new FilterExpr.LiteralExpr("han"), new FilterExpr.RegexExpr("/ha/", Pattern.compile("ha")), FilterExpr.Op.MATCH).eval(root, root));
        assertEquals(Boolean.FALSE, new FilterExpr.BinaryExpr(new FilterExpr.LiteralExpr("han"), new FilterExpr.LiteralExpr("ha"), FilterExpr.Op.MATCH).eval(root, root));
        assertEquals("(2 > 1)", new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.GT).toString());

        PathFunctionRegistry.register(new PathFunctionRegistry.FunctionDescriptor("joinCount", args -> ((List<?>) args[0]).size()));
        FilterExpr.FunctionExpr functionExpr = new FilterExpr.FunctionExpr("joinCount", List.of(new FilterExpr.LiteralExpr(List.of(1, 2, 3))));
        assertEquals(3, functionExpr.eval(root, root));

        FilterExpr.RegexExpr regexExpr = new FilterExpr.RegexExpr("/ha/", Pattern.compile("ha"));
        assertSame(regexExpr.eval(root, root), regexExpr.eval(root, root));
        assertEquals("/ha/", regexExpr.toString());
    }

    @Test
    void testEscapedStringParsingAndShortCircuit() {
        assertEquals("a'b", Paths.parseFilter("'a\\'b'").eval(null, null));
        assertEquals("a\"b\n", Paths.parseFilter("\"a\\\"b\\n\"").eval(null, null));
        assertEquals("\\'\"\b\f\n\r\tA", Paths.parseFilter("'\\\\\\'\\\"\\b\\f\\n\\r\\t\\u0041'").eval(null, null));
        assertThrows(JsonException.class, () -> Paths.parseFilter("'\\x'"));
        assertThrows(JsonException.class, () -> Paths.parseFilter("'\\u12'"));
        assertThrows(JsonException.class, () -> Paths.parseFilter("'\\uZZZZ'"));
        assertTrue(Paths.parseFilter("'HAN' =~ /ha/imsug").evalTruth(null, null));

        PathFunctionRegistry.register(new PathFunctionRegistry.FunctionDescriptor("explodeFilterExpr", args -> {
            throw new IllegalStateException("boom");
        }));

        assertFalse(Paths.parseFilter("false && explodeFilterExpr()").evalTruth(null, null));
        assertTrue(Paths.parseFilter("true || explodeFilterExpr()").evalTruth(null, null));
    }
}
