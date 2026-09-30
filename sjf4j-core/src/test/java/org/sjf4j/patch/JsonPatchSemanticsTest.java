package org.sjf4j.patch;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.NodeException;
import org.sjf4j.path.JsonPointer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void testRootTestSucceedsAndFailsWithoutMutation() {
        JsonObject target = JsonObject.of("a", 1);
        PatchOperation succeeds = new PatchOperation(PatchOperation.STD_TEST,
                JsonPointer.parse(""), JsonObject.of("a", 1), null);
        PatchOperation fails = new PatchOperation(PatchOperation.STD_TEST,
                JsonPointer.parse(""), JsonObject.of("a", 2), null);

        assertSame(target, succeeds.apply(target));
        assertThrows(NodeException.class, () -> fails.apply(target));
        assertEquals(1, target.getInt("a"));
    }

    @Test
    void testRootCopyFromChildAndCopyErrors() {
        JsonObject target = JsonObject.of("a", JsonObject.of("x", 1));
        PatchOperation copy = new PatchOperation(PatchOperation.STD_COPY,
                JsonPointer.parse(""), null, JsonPointer.parse("/a"));

        Object result = copy.apply(target);

        assertTrue(result instanceof JsonObject);
        assertEquals(1, ((JsonObject) result).getInt("x"));
        assertThrows(NodeException.class, () -> new PatchOperation(PatchOperation.STD_COPY,
                JsonPointer.parse(""), null, null).apply(target));
        assertThrows(NodeException.class, () -> new PatchOperation(PatchOperation.STD_COPY,
                JsonPointer.parse(""), null, JsonPointer.parse("/missing")).apply(target));
    }

    @Test
    void testRootExistAcceptsNullTarget() {
        PatchOperation operation = new PatchOperation(PatchOperation.EXT_EXIST,
                JsonPointer.parse(""), null, null);

        assertNull(operation.apply(null));
    }

    @Test
    void testUnknownOperationAndNullNonRootTargetFail() {
        assertThrows(NodeException.class, () -> new PatchOperation("unknown",
                JsonPointer.parse(""), null, null).apply(new JsonObject()));
        assertThrows(NodeException.class, () -> new PatchOperation("unknown",
                JsonPointer.parse("/a"), null, null).apply(new JsonObject()));
        assertThrows(NodeException.class, () -> new PatchOperation(PatchOperation.STD_ADD,
                JsonPointer.parse("/a"), 1, null).apply(null));
        assertThrows(NodeException.class, () -> new PatchOperation(PatchOperation.STD_ADD,
                null, 1, null).apply(new JsonObject()));
    }

    @Test
    void testRootMoveFromRootToRootIsUnchanged() {
        JsonObject target = JsonObject.of("a", 1);
        PatchOperation operation = new PatchOperation(PatchOperation.STD_MOVE,
                JsonPointer.parse(""), null, JsonPointer.parse(""));

        assertSame(target, operation.apply(target));
        assertEquals(1, target.getInt("a"));
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

        assertThrows(NodeException.class, () -> patch.apply(target));
        assertEquals(before, target);
    }

    @Test
    void testMoveFailureDoesNotLoseSourceValue() {
        JsonObject target = JsonObject.of("a", 1);
        JsonObject before = target.copy();
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_MOVE,
                JsonPointer.parse("/b/c"), null, JsonPointer.parse("/a")));

        assertThrows(NodeException.class, () -> patch.apply(target));
        assertEquals(before, target);
    }

    @Test
    void testMoveArrayFailureRestoresSourceValue() {
        JsonArray target = JsonArray.of("a", "b", "c");
        JsonArray before = target.copy();
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_MOVE,
                JsonPointer.parse("/99"), null, JsonPointer.parse("/1")));

        assertThrows(NodeException.class, () -> patch.apply(target));
        assertEquals(before, target);
    }

    @Test
    void testTestOperationDoesNotTreatMissingNullAsEqual() {
        JsonObject target = new JsonObject();
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_TEST,
                JsonPointer.parse("/missing"), null, null));

        assertThrows(NodeException.class, () -> patch.apply(target));
    }

    @Test
    void testTestAndExistTreatExplicitNullAsExistingAndReadOnly() {
        JsonObject target = JsonObject.of("a", null);
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_TEST,
                JsonPointer.parse("/a"), null, null));
        patch.add(new PatchOperation(PatchOperation.EXT_EXIST,
                JsonPointer.parse("/a"), null, null));

        assertSame(target, patch.apply(target));
        assertTrue(target.containsKey("a"));
        assertNull(target.getNode("a"));
        assertThrows(NodeException.class, () -> new PatchOperation(PatchOperation.EXT_EXIST,
                JsonPointer.parse("/missing"), null, null).apply(target));
    }

    @Test
    void testOperationsFailForMissingParentsAndValues() {
        JsonObject target = new JsonObject();

        assertThrows(NodeException.class, () -> new PatchOperation(PatchOperation.STD_ADD,
                JsonPointer.parse("/missing/a"), 1, null).apply(target));
        assertThrows(NodeException.class, () -> new PatchOperation(PatchOperation.STD_REMOVE,
                JsonPointer.parse("/missing"), null, null).apply(target));
        assertThrows(NodeException.class, () -> new PatchOperation(PatchOperation.STD_COPY,
                JsonPointer.parse("/copy"), null, null).apply(target));
        assertThrows(NodeException.class, () -> new PatchOperation(PatchOperation.STD_COPY,
                JsonPointer.parse("/copy"), null, JsonPointer.parse("/missing")).apply(target));
    }

    @Test
    void testRemoveOutOfRangeArrayElementFails() {
        JsonArray target = JsonArray.of(1, 2);

        assertThrows(NodeException.class, () -> new PatchOperation(PatchOperation.STD_REMOVE,
                JsonPointer.parse("/2"), null, null).apply(target));
        assertEquals(JsonArray.of(1, 2), target);
    }

    @Test
    void testJsonPatchRejectsMalformedElementOnApply() {
        JsonPatch patch = new JsonPatch(java.util.Collections.singletonList(null));

        assertThrows(NodeException.class, () -> patch.apply(new JsonObject()));
    }

    @Test
    void testJsonPatchFromJsonRoundTrip() {
        JsonPatch patch = JsonPatch.fromJson("[{\"op\":\"add\",\"path\":\"/a\",\"value\":1}]");
        JsonPatch roundTrip = JsonPatch.fromJson(patch.toJson());

        assertEquals(1, roundTrip.size());
        PatchOperation operation = roundTrip.get(0, PatchOperation.class);
        assertEquals(PatchOperation.STD_ADD, operation.getOp());
        assertEquals("/a", operation.getPath().toString());
        JsonObject target = new JsonObject();
        roundTrip.apply(target);
        assertEquals(1, target.getInt("a"));
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
