package org.sjf4j.patch;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.util.NodeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class JsonPatchTest {

    @Test
    void testBuiltinPatchOpsRegistered() {
        assertTrue(PatchOpRegistry.exists(PatchOp.STD_ADD));
        assertTrue(PatchOpRegistry.exists(PatchOp.STD_REMOVE));
        assertTrue(PatchOpRegistry.exists(PatchOp.STD_REPLACE));
        assertTrue(PatchOpRegistry.exists(PatchOp.STD_COPY));
        assertTrue(PatchOpRegistry.exists(PatchOp.STD_MOVE));
        assertTrue(PatchOpRegistry.exists(PatchOp.STD_TEST));

        assertTrue(PatchOpRegistry.exists(PatchOp.EXT_EXIST));
        assertTrue(PatchOpRegistry.exists(PatchOp.EXT_ENSURE_PUT));
    }

    @Test
    void testAddOperation() {
        JsonObject target = new JsonObject();
        JsonPatch patch = new JsonPatch();

        patch.add(new PatchOp(
                PatchOp.STD_ADD,
                JsonPointer.compile("/a"),
                1,
                null
        ));

        patch.apply(target);
        assertEquals(1, target.getInteger("a"));
    }

    @Test
    void testRemoveOperation() {
        JsonObject target = new JsonObject();
        target.put("a", 1);

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(
                PatchOp.STD_REMOVE,
                JsonPointer.compile("/a"),
                null,
                null
        ));

        patch.apply(target);
        assertFalse(target.containsKey("a"));
    }

    @Test
    void testReplaceOperation() {
        JsonObject target = new JsonObject();
        target.put("a", 1);

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(
                PatchOp.STD_REPLACE,
                JsonPointer.compile("/a"),
                2,
                null
        ));

        patch.apply(target);
        assertEquals(2, target.getInteger("a"));
    }

    @Test
    void testReplaceNonExistentPathFails() {
        JsonObject target = new JsonObject();

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(
                PatchOp.STD_REPLACE,
                JsonPointer.compile("/a"),
                1,
                null
        ));

        assertThrows(JsonException.class, () -> patch.apply(target));
    }

    @Test
    void testTestOperationSuccess() {
        JsonObject target = new JsonObject();
        target.put("a", 1);

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(
                PatchOp.STD_TEST,
                JsonPointer.compile("/a"),
                1,
                null
        ));
        assertDoesNotThrow(() -> patch.apply(target));

        JsonPatch patch2 = new JsonPatch();
        patch2.add(new PatchOp(
                PatchOp.STD_TEST,
                JsonPointer.compile("/a"),
                2,
                null
        ));
        assertThrows(JsonException.class, () -> patch2.apply(target));
    }

    @Test
    void testCopyOperation() {
        JsonObject target = new JsonObject();
        target.put("a", 1);

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(
                PatchOp.STD_COPY,
                JsonPointer.compile("/b"),
                null,
                JsonPointer.compile("/a")
        ));

        patch.apply(target);
        assertEquals(1, target.getInteger("b"));
    }

    @Test
    void testMoveOperation() {
        JsonObject target = new JsonObject();
        target.put("a", 1);

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(
                PatchOp.STD_MOVE,
                JsonPointer.compile("/b"),
                null,
                JsonPointer.compile("/a")
        ));

        patch.apply(target);

        assertEquals(1, target.getInteger("b"));
        assertFalse(target.containsKey("a"));
    }

    @Test
    void testExistOperation() {
        JsonObject target = new JsonObject();
        target.put("a", 1);

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(
                PatchOp.EXT_EXIST,
                JsonPointer.compile("/a"),
                null,
                null
        ));

        assertDoesNotThrow(() -> patch.apply(target));
    }

    @Test
    void testEnsurePutOperation() {
        JsonObject target = new JsonObject();

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(
                PatchOp.EXT_ENSURE_PUT,
                JsonPointer.compile("/a/b/c"),
                1,
                null
        ));

        patch.apply(target);
        assertEquals(1, target.getIntegerByPath("/a/b/c"));
    }


    @Test
    public void testAddAppendToArray() {
        List<Integer> src = new ArrayList<>(Arrays.asList(1, 2, 3));

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(PatchOp.STD_ADD, JsonPointer.compile("/-"), 4, null));

        patch.apply(src);
        log.info("src: {}", src);
        assertEquals(Arrays.asList(1, 2, 3, 4), src);
    }

    @Test
    public void testReplaceArrayElement() {
        List<Integer> src = new ArrayList<>(Arrays.asList(1, 2, 3));

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(PatchOp.STD_REPLACE, JsonPointer.compile("/1"), 20, null));

        patch.apply(src);
        assertEquals(Arrays.asList(1, 20, 3), src);
    }

    @Test
    public void testRemoveNonExistingPathIgnored() {
        List<Integer> src = new ArrayList<>(Arrays.asList(1, 2, 3));

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(PatchOp.STD_REMOVE, JsonPointer.compile("/10"), null, null));

        patch.apply(src);
        assertEquals(Arrays.asList(1, 2, 3), src);
    }

    @Test
    public void testMoveInArray() {
        List<String> src = new ArrayList<>(Arrays.asList("a", "b", "c"));

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(PatchOp.STD_MOVE, JsonPointer.compile("/-"), null, JsonPointer.compile("/0")));

        patch.apply(src);
        assertEquals(Arrays.asList("b", "c", "a"), src);
    }

    @Test
    void testDiffAndApply1() {
        JsonObject source = new JsonObject();
        source.put("a", 1);

        JsonObject target = new JsonObject();
        target.put("a", 2);
        target.put("b", 3);

        JsonPatch patch = JsonPatch.diff(source, target);
        patch.apply(source);

        assertEquals(target, source);
    }

    @Test
    public void testDiffAndApply2() {
        List<Integer> a = Arrays.asList(1, 2, 3);
        List<Integer> b = Arrays.asList(1, 20, 3, 4);

        JsonPatch patch = JsonPatch.diff(a, b);
        List<Integer> c = NodeUtil.deepCopy(a);
        patch.apply(c);

        assertEquals(b, c);
    }

    @Test
    public void testComplex1() {
        String json1 = "{\n" +
                "  \"users\": [\n" +
                "    { \"id\": 1, \"name\": \"Alice\", \"roles\": [\"admin\", \"user\"] },\n" +
                "    { \"id\": 2, \"name\": \"Bob\", \"roles\": [\"user\"] }\n" +
                "  ],\n" +
                "  \"meta\": {\n" +
                "    \"count\": 2,\n" +
                "    \"active\": true\n" +
                "  }\n" +
                "}\n";

        String json2 = "{\n" +
                "  \"users\": [\n" +
                "    { \"id\": 1, \"name\": \"Alice Smith\", \"roles\": [\"admin\", null] },\n" +
                "    { \"id\": 3, \"name\": \"Charlie\", \"roles\": [\"user\", \"editor\"] },\n" +
                "    { \"id\": 2, \"name\": \"Bob\", \"roles\": [\"user\"] }\n" +
                "  ],\n" +
                "  \"meta\": {\n" +
                "    \"count\": 3,\n" +
                "    \"active\": false\n" +
                "  }\n" +
                "}\n";

        String jsonPatch = "[\n" +
                "  { \"op\": \"replace\", \"path\": \"/users/0/name\", \"value\": \"Alice Smith\" },\n" +
                "  { \"op\": \"replace\", \"path\": \"/users/0/roles/1\", \"value\": null },\n" +
                "  { \"op\": \"add\", \"path\": \"/users/1\", \"value\": { \"id\": 3, \"name\": \"Charlie\", \"roles\": [\"user\",\"editor\"] } },\n" +
                "  { \"op\": \"replace\", \"path\": \"/meta/count\", \"value\": 3 },\n" +
                "  { \"op\": \"replace\", \"path\": \"/meta/active\", \"value\": false }\n" +
                "]";

        JsonObject jo1 = JsonObject.fromJson(json1);
        JsonPatch patch = JsonPatch.fromJson(jsonPatch);
        log.info("jo1={}", jo1);
        patch.apply(jo1);
        log.info("jo1={}", jo1);

        JsonObject jo2 = JsonObject.fromJson(json2);
        assertEquals(jo2, jo1);
    }
}
