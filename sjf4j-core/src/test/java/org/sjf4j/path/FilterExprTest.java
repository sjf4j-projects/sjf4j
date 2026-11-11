package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.NodeException;
import org.sjf4j.node.NodeWalker;

import java.util.Arrays;
import java.util.Collections;
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
        assertFalse(FilterExpr.truth(FilterExpr.PathExpr.NOTHING));
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
        assertFalse(FilterExpr.eq(FilterExpr.PathExpr.NOTHING, null));
        assertFalse(FilterExpr.in(FilterExpr.PathExpr.NOTHING, Collections.emptyList()));
    }

    @Test
    void singularCurrentPathPreservesMissingAndNull() {
        JsonObject root = JsonObject.of("items", JsonArray.of(
                JsonObject.of("age", 2), JsonObject.of(), JsonObject.of("age", null)));
        FilterExpr.PathExpr age = new FilterExpr.PathExpr("@.age");

        assertEquals(2, age.eval(root, ((JsonArray) root.get("items")).get(0), new NodeWalker.Access()));
        assertSame(FilterExpr.PathExpr.NOTHING, age.eval(root, ((JsonArray) root.get("items")).get(1), new NodeWalker.Access()));
        assertEquals(null, age.eval(root, ((JsonArray) root.get("items")).get(2), new NodeWalker.Access()));
        assertEquals(Collections.singletonList(JsonObject.of("age", null)),
                JsonPath.parse("$.items[?(@.age == null)]").find(root));
    }

    @Test
    void queryLocalAccessIsReusableAcrossNestedPredicatesAndMissingValues() {
        JsonObject root = JsonObject.fromJson("{\"threshold\":10,\"items\":["
                + "{\"price\":12,\"active\":true},{\"price\":7,\"active\":true},"
                + "{\"price\":14},{\"price\":null},{},"
                + "{\"price\":20,\"active\":false}]}");
        FilterExpr predicate = PathSyntax.parseFilter(
                "(@.price > $.threshold && @.active) || @.price == null");
        JsonArray items = root.getJsonArray("items");
        NodeWalker.Access scratch = new NodeWalker.Access();

        // Reusing one Access must not retain a preceding item's present flag.
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            assertEquals(predicate.evalTruth(root, item, new NodeWalker.Access()),
                    predicate.evalTruth(root, item, scratch), "item " + i);
        }
        // A custom FilterExpr overriding evalTruth retains its semantics.
        FilterExpr custom = new FilterExpr() {
            @Override
            public Object eval(Object rootNode, Object currentNode, NodeWalker.Access access) {
                return false;
            }

            @Override
            public boolean evalTruth(Object rootNode, Object currentNode, NodeWalker.Access access) {
                return true;
            }
        };
        assertTrue(custom.evalTruth(root, items.get(0), scratch));

        FilterExpr existence = PathSyntax.parseFilter("@.price");
        assertTrue(existence.evalTruth(root, items.get(3), scratch));
        assertFalse(existence.evalTruth(root, items.get(4), scratch));
        assertTrue(existence.evalTruth(root, items.get(0), scratch));
    }

    @Test
    void nonSingularPathsAreAllowedForExistenceButNotComparisons() {
        JsonObject root = JsonObject.fromJson("{\"users\":[{\"age\":1},{\"age\":3}]}");
        assertEquals(Collections.singletonList(root), JsonPath.parse("$[?@.users[?(@.age > 2)]]").find(JsonArray.of(root)));
        NodeException error = assertThrows(NodeException.class,
                () -> JsonPath.parse("$[?(@.users[*].age > 2)]"));
        assertTrue(error.getMessage().contains("@.users[*].age"));
    }

    @Test
    void nonSingularTailFunctionMustReturnScalarForComparison() {
        FunctionRegistry.register(new FunctionRegistry.FunctionDescriptor("arrayForComparison", (target, args) -> JsonArray.of(1)));
        NodeException error = assertThrows(NodeException.class,
                () -> JsonPath.parse("$[?(@..value.arrayForComparison() == 1)]").find(JsonArray.of(JsonObject.of("value", 1))));
        assertTrue(error.getMessage().contains("@..value.arrayForComparison()"));
        assertTrue(error.getMessage().contains("ARRAY"));
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
    void testIn() {
        assertTrue(FilterExpr.in("S", JsonArray.of("XS", "S", "M")));
        assertTrue(FilterExpr.in(1, Arrays.asList(1L, 2L)));
        assertTrue(FilterExpr.in(null, Collections.singletonList(null)));
        assertFalse(FilterExpr.in(true, false));
        assertFalse(FilterExpr.in("XL", JsonArray.of("XS", "S", "M")));
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
        assertEquals("root", rootPath.eval(root, root.getJsonObject("child"), new NodeWalker.Access()));
        assertEquals("current", currentPath.eval(root, root.getJsonObject("child"), new NodeWalker.Access()));
        assertEquals("$.name", rootPath.toString());

        FilterExpr.UnaryExpr truthy = new FilterExpr.UnaryExpr(true, currentPath);
        FilterExpr.UnaryExpr falsy = new FilterExpr.UnaryExpr(false, new FilterExpr.LiteralExpr(false));
        assertEquals(Boolean.TRUE, truthy.eval(root, root.getJsonObject("child"), new NodeWalker.Access()));
        assertEquals(Boolean.TRUE, falsy.eval(root, root.getJsonObject("child"), new NodeWalker.Access()));
        assertEquals("@.name", truthy.toString());
        assertEquals("!false", falsy.toString());

        FilterExpr left = new FilterExpr.LiteralExpr(2);
        FilterExpr right = new FilterExpr.LiteralExpr(1);
        assertEquals(Boolean.FALSE, new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.EQ).eval(root, root, new NodeWalker.Access()));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.NE).eval(root, root, new NodeWalker.Access()));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.GT).eval(root, root, new NodeWalker.Access()));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.GE).eval(root, root, new NodeWalker.Access()));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(right, left, FilterExpr.Op.LT).eval(root, root, new NodeWalker.Access()));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(right, left, FilterExpr.Op.LE).eval(root, root, new NodeWalker.Access()));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(new FilterExpr.LiteralExpr(true), new FilterExpr.LiteralExpr(true), FilterExpr.Op.AND).eval(root, root, new NodeWalker.Access()));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(new FilterExpr.LiteralExpr(false), new FilterExpr.LiteralExpr(true), FilterExpr.Op.OR).eval(root, root, new NodeWalker.Access()));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(new FilterExpr.LiteralExpr("han"), new FilterExpr.RegexExpr("/ha/", Pattern.compile("ha")), FilterExpr.Op.MATCH).eval(root, root, new NodeWalker.Access()));
        assertEquals(Boolean.FALSE, new FilterExpr.BinaryExpr(new FilterExpr.LiteralExpr("han"), new FilterExpr.LiteralExpr("ha"), FilterExpr.Op.MATCH).eval(root, root, new NodeWalker.Access()));
        assertEquals("(2 > 1)", new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.GT).toString());

        FunctionRegistry.register(new FunctionRegistry.FunctionDescriptor("joinCount", (target, args) -> ((List<?>) target).size()));
        FilterExpr.FunctionExpr functionExpr = new FilterExpr.FunctionExpr("joinCount", Arrays.asList(new FilterExpr.LiteralExpr(Arrays.asList(1, 2, 3))));
        assertEquals(3, functionExpr.eval(root, root, new NodeWalker.Access()));

        FilterExpr.RegexExpr regexExpr = new FilterExpr.RegexExpr("/ha/", Pattern.compile("ha"));
        assertSame(regexExpr.eval(root, root, new NodeWalker.Access()), regexExpr.eval(root, root, new NodeWalker.Access()));
        assertEquals("/ha/", regexExpr.toString());

        FilterExpr.ArrayExpr arrayExpr = new FilterExpr.ArrayExpr(Arrays.asList(
                new FilterExpr.LiteralExpr("han"),
                new FilterExpr.LiteralExpr(12),
                new FilterExpr.LiteralExpr(true),
                new FilterExpr.LiteralExpr(null)
        ));
        assertEquals(Arrays.asList("han", 12, true, null), arrayExpr.eval(root, root, new NodeWalker.Access()));
        assertEquals("[\"han\", 12, true, null]", arrayExpr.toString());

        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(
                new FilterExpr.LiteralExpr("han"),
                new FilterExpr.ArrayExpr(Arrays.asList(new FilterExpr.LiteralExpr("ha"), new FilterExpr.LiteralExpr("han"))),
                FilterExpr.Op.IN).eval(root, root, new NodeWalker.Access()));
        assertEquals(Boolean.TRUE, new FilterExpr.BinaryExpr(
                new FilterExpr.LiteralExpr("han"),
                new FilterExpr.ArrayExpr(Arrays.asList(new FilterExpr.LiteralExpr("ha"), new FilterExpr.LiteralExpr("ho"))),
                FilterExpr.Op.NIN).eval(root, root, new NodeWalker.Access()));
    }

    @Test
    void testEscapedStringParsingAndShortCircuit() {
        assertEquals("a'b", PathSyntax.parseFilter("'a\\'b'").eval(null, null, new NodeWalker.Access()));
        assertEquals("a\"b\n", PathSyntax.parseFilter("\"a\\\"b\\n\"").eval(null, null, new NodeWalker.Access()));
        assertEquals("\\'\"\b\f\n\r\tA", PathSyntax.parseFilter("'\\\\\\'\\\"\\b\\f\\n\\r\\t\\u0041'").eval(null, null, new NodeWalker.Access()));
        assertThrows(NodeException.class, () -> PathSyntax.parseFilter("'\\x'"));
        assertThrows(NodeException.class, () -> PathSyntax.parseFilter("'\\u12'"));
        assertThrows(NodeException.class, () -> PathSyntax.parseFilter("'\\uZZZZ'"));
        assertTrue(PathSyntax.parseFilter("'HAN' =~ /ha/imsug").evalTruth(null, null, new NodeWalker.Access()));

        FunctionRegistry.register(new FunctionRegistry.FunctionDescriptor("explodeFilterExpr", (target, args) -> {
            throw new IllegalStateException("boom");
        }));

        assertFalse(PathSyntax.parseFilter("false && explodeFilterExpr()").evalTruth(null, null, new NodeWalker.Access()));
        assertTrue(PathSyntax.parseFilter("true || explodeFilterExpr()").evalTruth(null, null, new NodeWalker.Access()));
    }
}
