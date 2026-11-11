package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeWalker;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class JsonPathFilterPresenceAndNothingTest {

    @Test
    void singularQueryTestsPresenceNotValueTruthiness() {
        JsonArray rows = JsonArray.of(
                JsonObject.of("value", false),
                JsonObject.of("value", 0),
                JsonObject.of("value", null),
                JsonObject.of("value", ""),
                JsonObject.of("value", new JsonArray()),
                JsonObject.of());

        assertEquals(Arrays.asList(rows.getNode(0), rows.getNode(1), rows.getNode(2),
                        rows.getNode(3), rows.getNode(4)),
                JsonPath.parse("$[?@.value]").find(rows));
        assertEquals(Arrays.asList(rows.getNode(5)),
                JsonPath.parse("$[?!@.value]").find(rows));
    }

    @Test
    void logicalOperatorsTestOperandQueryPresence() {
        JsonArray rows = JsonArray.of(
                JsonObject.of("a", false),
                JsonObject.of("a", 0, "b", null),
                JsonObject.of("b", false),
                JsonObject.of());

        assertEquals(Arrays.asList(rows.getNode(1)),
                JsonPath.parse("$[?(@.a && @.b)]").find(rows));
        assertEquals(Arrays.asList(rows.getNode(0), rows.getNode(1), rows.getNode(2)),
                JsonPath.parse("$[?(@.a || @.b)]").find(rows));
        assertEquals(Arrays.asList(rows.getNode(0)),
                JsonPath.parse("$[?(@.a && !@.b)]").find(rows));
        assertEquals(Arrays.asList(rows.getNode(3)),
                JsonPath.parse("$[?(!@.a && !@.b)]").find(rows));
    }

    @Test
    void nonSingularQueryTestsNodeListNonEmptiness() {
        JsonArray rows = JsonArray.of(
                JsonObject.of("items", JsonArray.of(false)),
                JsonObject.of("items", new JsonArray()),
                JsonObject.of("items", JsonArray.of((Object) null)),
                JsonObject.of("items", false));

        assertEquals(Arrays.asList(rows.getNode(0), rows.getNode(2)),
                JsonPath.parse("$[?@.items[*]]").find(rows));
    }

    @Test
    void missingPathComparisonsFollowRfc9535() {
        JsonObject empty = JsonObject.of();
        FilterExpr.PathExpr a = new FilterExpr.PathExpr("@.a");
        FilterExpr.PathExpr b = new FilterExpr.PathExpr("@.b");
        FilterExpr.LiteralExpr zero = new FilterExpr.LiteralExpr(0);

        assertTrue(new FilterExpr.BinaryExpr(a, b, FilterExpr.Op.EQ).evalTruth(empty, empty, new NodeWalker.Access()));
        assertFalse(new FilterExpr.BinaryExpr(a, b, FilterExpr.Op.NE).evalTruth(empty, empty, new NodeWalker.Access()));
        assertTrue(new FilterExpr.BinaryExpr(a, b, FilterExpr.Op.LE).evalTruth(empty, empty, new NodeWalker.Access()));
        assertTrue(new FilterExpr.BinaryExpr(a, b, FilterExpr.Op.GE).evalTruth(empty, empty, new NodeWalker.Access()));
        assertFalse(new FilterExpr.BinaryExpr(a, b, FilterExpr.Op.LT).evalTruth(empty, empty, new NodeWalker.Access()));
        assertFalse(new FilterExpr.BinaryExpr(a, b, FilterExpr.Op.GT).evalTruth(empty, empty, new NodeWalker.Access()));

        assertFalse(new FilterExpr.BinaryExpr(a, zero, FilterExpr.Op.EQ).evalTruth(empty, empty, new NodeWalker.Access()));
        assertTrue(new FilterExpr.BinaryExpr(a, zero, FilterExpr.Op.NE).evalTruth(empty, empty, new NodeWalker.Access()));
        assertFalse(new FilterExpr.BinaryExpr(a, zero, FilterExpr.Op.LE).evalTruth(empty, empty, new NodeWalker.Access()));
        assertFalse(new FilterExpr.BinaryExpr(a, zero, FilterExpr.Op.GE).evalTruth(empty, empty, new NodeWalker.Access()));

        JsonArray rows = JsonArray.of(JsonObject.of(), JsonObject.of("a", null), JsonObject.of("a", 0));
        assertEquals(Arrays.asList(rows.getNode(0)),
                JsonPath.parse("$[?(@.a == @.b)]").find(rows));
        assertEquals(Arrays.asList(rows.getNode(1), rows.getNode(2)),
                JsonPath.parse("$[?(@.a != @.b)]").find(rows));
    }

    @Test
    void currentNodeExistenceIncludesFalseZeroAndNull() {
        JsonArray values = JsonArray.of(false, 0, null, "");
        assertEquals(values.toList(), JsonPath.parse("$[?@]").find(values));
    }
}
