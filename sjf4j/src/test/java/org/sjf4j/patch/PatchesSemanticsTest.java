package org.sjf4j.patch;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchesSemanticsTest {

    @Test
    void testMergeRfc7386CanReplaceRootWithScalar() {
        Object result = Patches.mergeRfc7386(JsonObject.of("a", 1), 7);

        assertEquals(7, result);
    }

    @Test
    void testMergeRfc7386CanReplaceRootWithNull() {
        Object result = Patches.mergeRfc7386(JsonObject.of("a", 1), null);

        assertNull(result);
    }

    @Test
    void testMergeRfc7386CanReplaceRootWithArray() {
        Object result = Patches.mergeRfc7386(JsonObject.of("a", 1), JsonArray.of(1, 2, 3));

        assertTrue(result instanceof JsonArray);
        assertEquals(3, ((JsonArray) result).size());
    }

    @Test
    void testMergeRfc7386TreatsNonObjectTargetAsEmptyObject() {
        Object result = Patches.mergeRfc7386(1, JsonObject.of("a", 1, "b", JsonObject.of("c", 2)));

        assertTrue(result instanceof JsonObject);
        JsonObject object = (JsonObject) result;
        assertEquals(1, object.getInt("a"));
        assertEquals(2, object.getJsonObject("b").getInt("c"));
    }

    @Test
    void testMergeRfc7386RemovesExplicitNullMemberEvenWhenTargetValueIsNull() {
        JsonObject target = JsonObject.of("a", null, "b", 1);

        Object result = Patches.mergeRfc7386(target, JsonObject.of("a", null));

        assertSame(target, result);
        assertFalse(target.containsKey("a"));
        assertEquals(1, target.getInt("b"));
    }

    @Test
    void testMergeRfc7386ReplacesNonObjectMemberWithMergedObject() {
        JsonObject target = JsonObject.of("a", 1);

        Object result = Patches.mergeRfc7386(target, JsonObject.of("a", JsonObject.of("b", 2)));

        assertSame(target, result);
        assertEquals(2, target.getJsonObject("a").getInt("b"));
    }

    @Test
    void testJsonContainerMergeRfc7386ReturnsReplacedRoot() {
        JsonObject target = JsonObject.of("a", 1);

        Object result = target.mergeRfc7386(JsonArray.of(1, 2, 3));

        assertTrue(result instanceof JsonArray);
        assertEquals(3, ((JsonArray) result).size());
    }
}
