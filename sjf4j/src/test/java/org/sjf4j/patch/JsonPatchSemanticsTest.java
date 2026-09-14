package org.sjf4j.patch;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPointer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPatchSemanticsTest {

    @Test
    void testDiffTreatsObjectExplicitNullAsValue() {
        JsonObject source = JsonObject.of("a", 1);
        JsonObject target = JsonObject.of("a", null);

        JsonPatch patch = JsonPatch.diff(source, target);
        PatchOperation op = (PatchOperation) patch.get(0, Object.class);

        assertEquals(1, patch.size());
        assertEquals(PatchOperation.STD_REPLACE, op.getOp());
        assertEquals("/a", op.getPath().toString());
        patch.apply(source);
        assertTrue(source.containsKey("a"));
        assertNull(source.getNode("a"));

        JsonPatch reverse = JsonPatch.diff(target, JsonObject.of("a", 1));
        op = (PatchOperation) reverse.get(0, Object.class);
        assertEquals(1, reverse.size());
        assertEquals(PatchOperation.STD_REPLACE, op.getOp());
        reverse.apply(target);
        assertEquals(1, target.getInt("a"));
    }

    @Test
    void testDiffAddsAbsentExplicitNullObjectMember() {
        JsonObject source = new JsonObject();
        JsonObject target = JsonObject.of("a", null);

        JsonPatch patch = JsonPatch.diff(source, target);
        PatchOperation op = (PatchOperation) patch.get(0, Object.class);

        assertEquals(1, patch.size());
        assertEquals(PatchOperation.STD_ADD, op.getOp());
        assertEquals("/a", op.getPath().toString());
        patch.apply(source);
        assertTrue(source.containsKey("a"));
        assertNull(source.getNode("a"));
    }

    @Test
    void testDiffTreatsArrayExplicitNullAsValue() {
        JsonArray source = JsonArray.of(null, 2);
        JsonArray target = JsonArray.of(1, 2);

        JsonPatch patch = JsonPatch.diff(source, target);
        PatchOperation op = (PatchOperation) patch.get(0, Object.class);

        assertEquals(1, patch.size());
        assertEquals(PatchOperation.STD_REPLACE, op.getOp());
        assertEquals("/0", op.getPath().toString());
        patch.apply(source);
        assertEquals(target, source);
    }

    @Test
    void testDiffRootNullTransitions() {
        JsonObject object = JsonObject.of("a", 1);
        JsonPatch add = JsonPatch.diff(null, object);
        JsonPatch remove = JsonPatch.diff(object, null);

        assertEquals(PatchOperation.STD_ADD,
                ((PatchOperation) add.get(0, Object.class)).getOp());
        assertEquals(object, add.apply((Object) null));
        assertEquals(PatchOperation.STD_REMOVE,
                ((PatchOperation) remove.get(0, Object.class)).getOp());
        assertNull(remove.apply(object));
        assertEquals(0, JsonPatch.diff(null, null).size());
    }

    @Test
    void testDiffAndApplyCanReplaceRootDocument() {
        JsonPatch patch = JsonPatch.diff(1, JsonObject.of("a", 1));

        Object result = patch.apply(1);

        assertTrue(result instanceof JsonObject);
        assertEquals(1, ((JsonObject) result).getInt("a"));
    }

    @Test
    void testDiffAndApplyCanRemoveRootDocument() {
        JsonObject source = JsonObject.of("a", 1);
        JsonPatch patch = JsonPatch.diff(source, null);

        Object result = patch.apply(source);

        assertNull(result);
    }

    @Test
    void testRootReplacementFeedsLaterOperations() {
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_REPLACE,
                JsonPointer.parse(""), JsonObject.of("a", 1), null));
        patch.add(new PatchOperation(PatchOperation.STD_ADD,
                JsonPointer.parse("/b"), 2, null));

        Object result = patch.apply(1);

        assertTrue(result instanceof JsonObject);
        assertEquals(1, ((JsonObject) result).getInt("a"));
        assertEquals(2, ((JsonObject) result).getInt("b"));
    }

    @Test
    void testCopyPreservesExplicitNull() {
        JsonObject target = JsonObject.of("a", null);
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_COPY,
                JsonPointer.parse("/b"), null, JsonPointer.parse("/a")));

        patch.apply(target);

        assertTrue(target.containsKey("b"));
        assertNull(target.getNode("b"));
    }

    @Test
    void testMovePreservesExplicitNull() {
        JsonObject target = JsonObject.of("a", null);
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_MOVE,
                JsonPointer.parse("/b"), null, JsonPointer.parse("/a")));

        patch.apply(target);

        assertFalse(target.containsKey("a"));
        assertTrue(target.containsKey("b"));
        assertNull(target.getNode("b"));
    }

    @Test
    void testCopyDeepCopiesContainers() {
        JsonObject target = JsonObject.of("a", JsonObject.of("x", 1));
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_COPY,
                JsonPointer.parse("/b"), null, JsonPointer.parse("/a")));

        patch.apply(target);
        JsonObject copied = target.getJsonObject("b");
        assertNotNull(copied);

        copied.put("x", 2);

        assertEquals(1, target.getJsonObject("a").getInt("x"));
        assertEquals(2, copied.getInt("x"));
    }

    @Test
    void testCopyRootToDescendantUsesDetachedSnapshot() {
        JsonObject target = JsonObject.of("a", JsonObject.of("x", 1));
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_COPY,
                JsonPointer.parse("/snapshot"), null, JsonPointer.parse("")));

        patch.apply(target);

        JsonObject snapshot = target.getJsonObject("snapshot");
        assertNotNull(snapshot);
        snapshot.getJsonObject("a").put("x", 2);

        assertEquals(1, target.getJsonObject("a").getInt("x"));
        assertEquals(2, snapshot.getJsonObject("a").getInt("x"));
    }

    @Test
    void testMoveIntoDescendantFailsAtomically() {
        JsonObject target = JsonObject.of("a", JsonObject.of("x", 1));
        JsonObject before = target.copy();
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_MOVE,
                JsonPointer.parse("/a/b"), null, JsonPointer.parse("/a")));

        assertThrows(JsonException.class, () -> patch.apply(target));
        assertEquals(before, target);
    }

    @Test
    void testMoveFailureDoesNotLoseSourceValue() {
        JsonObject target = JsonObject.of("a", 1);
        JsonObject before = target.copy();
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_MOVE,
                JsonPointer.parse("/b/c"), null, JsonPointer.parse("/a")));

        assertThrows(JsonException.class, () -> patch.apply(target));
        assertEquals(before, target);
    }

    @Test
    void testMoveArrayFailureRestoresSourceValue() {
        JsonArray target = JsonArray.of("a", "b", "c");
        JsonArray before = target.copy();
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_MOVE,
                JsonPointer.parse("/99"), null, JsonPointer.parse("/1")));

        assertThrows(JsonException.class, () -> patch.apply(target));
        assertEquals(before, target);
    }

    @Test
    void testTestOperationDoesNotTreatMissingNullAsEqual() {
        JsonObject target = new JsonObject();
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_TEST,
                JsonPointer.parse("/missing"), null, null));

        assertThrows(JsonException.class, () -> patch.apply(target));
    }

    @Test
    void testDiffUsesNodeEqualityForNumericLeaves() {
        JsonPatch patch = JsonPatch.diff(JsonObject.of("n", 1), JsonObject.of("n", 1L));

        assertEquals(0, patch.size());
    }

    @Test
    void testJsonContainerApplyReturnsReplacedRoot() {
        JsonObject target = JsonObject.of("a", 1);
        JsonPatch patch = JsonPatch.diff(target, 7);

        Object result = target.apply(patch);

        assertEquals(7, result);
    }
}
