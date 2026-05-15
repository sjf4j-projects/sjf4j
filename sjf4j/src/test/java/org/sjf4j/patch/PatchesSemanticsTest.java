package org.sjf4j.patch;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchesSemanticsTest {

    static class Bean {
        private String name;
        private int count;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    @Test
    void testMergePatchCanReplaceRootWithScalar() {
        Object result = Patches.mergePatch(JsonObject.of("a", 1), 7);

        assertEquals(7, result);
    }

    @Test
    void testMergePatchCanReplaceRootWithNull() {
        Object result = Patches.mergePatch(JsonObject.of("a", 1), null);

        assertNull(result);
    }

    @Test
    void testMergePatchCanReplaceRootWithArray() {
        Object result = Patches.mergePatch(JsonObject.of("a", 1), JsonArray.of(1, 2, 3));

        assertTrue(result instanceof JsonArray);
        assertEquals(3, ((JsonArray) result).size());
    }

    @Test
    void testMergePatchTreatsNonObjectTargetAsEmptyObject() {
        Object result = Patches.mergePatch(1, JsonObject.of("a", 1, "b", JsonObject.of("c", 2)));

        assertTrue(result instanceof JsonObject);
        JsonObject object = (JsonObject) result;
        assertEquals(1, object.getInt("a"));
        assertEquals(2, object.getJsonObject("b").getInt("c"));
    }

    @Test
    void testMergePatchRemovesExplicitNullMemberEvenWhenTargetValueIsNull() {
        JsonObject target = JsonObject.of("a", null, "b", 1);

        Object result = Patches.mergePatch(target, JsonObject.of("a", null));

        assertSame(target, result);
        assertFalse(target.containsKey("a"));
        assertEquals(1, target.getInt("b"));
    }

    @Test
    void testMergePatchReplacesNonObjectMemberWithMergedObject() {
        JsonObject target = JsonObject.of("a", 1);

        Object result = Patches.mergePatch(target, JsonObject.of("a", JsonObject.of("b", 2)));

        assertSame(target, result);
        assertEquals(2, target.getJsonObject("a").getInt("b"));
    }

    @Test
    void testStaticMergePatchReturnsReplacedRoot() {
        JsonObject target = JsonObject.of("a", 1);

        Object result = Patches.mergePatch(target, JsonArray.of(1, 2, 3));
    
        assertTrue(result instanceof JsonArray);
        assertEquals(3, ((JsonArray) result).size());
    }

    @Test
    void testMergePatchFailsWhenRemovingPojoProperty() {
        Bean target = new Bean();
        target.setName("han");
        target.setCount(1);

        JsonException e = assertThrows(JsonException.class,
                () -> Patches.mergePatch(target, JsonObject.of("name", null)));

        assertTrue(e.getMessage().contains("cannot remove field 'name'"));
        assertEquals("han", target.getName());
        assertEquals(1, target.getCount());
    }
}
