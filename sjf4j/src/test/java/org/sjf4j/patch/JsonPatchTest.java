package org.sjf4j.patch;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4j;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.path.JsonPointer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class JsonPatchTest {

    @Test
    void testBuiltinPatchOpsRegistered() {
        assertTrue(OperationRegistry.exists(PatchOperation.STD_ADD));
        assertTrue(OperationRegistry.exists(PatchOperation.STD_REMOVE));
        assertTrue(OperationRegistry.exists(PatchOperation.STD_REPLACE));
        assertTrue(OperationRegistry.exists(PatchOperation.STD_COPY));
        assertTrue(OperationRegistry.exists(PatchOperation.STD_MOVE));
        assertTrue(OperationRegistry.exists(PatchOperation.STD_TEST));

        assertTrue(OperationRegistry.exists(PatchOperation.EXT_EXIST));
        assertTrue(OperationRegistry.exists(PatchOperation.EXT_ENSURE_PUT));
    }

    @Test
    void testAddOperation() {
        JsonObject target = new JsonObject();
        JsonPatch patch = new JsonPatch();

        patch.add(new PatchOperation(
                PatchOperation.STD_ADD,
                JsonPointer.compile("/a"),
                1,
                null
        ));

        patch.apply(target);
        assertEquals(1, target.getInt("a"));
    }

    @Test
    void testRemoveOperation() {
        JsonObject target = new JsonObject();
        target.put("a", 1);

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(
                PatchOperation.STD_REMOVE,
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
        patch.add(new PatchOperation(
                PatchOperation.STD_REPLACE,
                JsonPointer.compile("/a"),
                2,
                null
        ));

        patch.apply(target);
        assertEquals(2, target.getInt("a"));
    }

    @Test
    void testReplaceNonExistentPathFails() {
        JsonObject target = new JsonObject();

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(
                PatchOperation.STD_REPLACE,
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
        JsonObject before = target.copy();

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(
                PatchOperation.STD_TEST,
                JsonPointer.compile("/a"),
                1,
                null
        ));
        assertSame(target, assertDoesNotThrow(() -> patch.apply(target)));
        assertEquals(before, target);

        JsonPatch patch2 = new JsonPatch();
        patch2.add(new PatchOperation(
                PatchOperation.STD_TEST,
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
        patch.add(new PatchOperation(
                PatchOperation.STD_COPY,
                JsonPointer.compile("/b"),
                null,
                JsonPointer.compile("/a")
        ));

        patch.apply(target);
        assertEquals(1, target.getInt("b"));
    }

    @Test
    void testMoveOperation() {
        JsonObject target = new JsonObject();
        target.put("a", 1);

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(
                PatchOperation.STD_MOVE,
                JsonPointer.compile("/b"),
                null,
                JsonPointer.compile("/a")
        ));

        patch.apply(target);

        assertEquals(1, target.getInt("b"));
        assertFalse(target.containsKey("a"));
    }

    @Test
    void testExistOperation() {
        JsonObject target = new JsonObject();
        target.put("a", 1);
        target.put("nested", JsonArray.of(1, 2, 3));
        JsonObject before = target.copy();

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(
                PatchOperation.EXT_EXIST,
                JsonPointer.compile("/a"),
                null,
                null
        ));

        assertSame(target, assertDoesNotThrow(() -> patch.apply(target)));
        assertEquals(before, target);
    }

    @Test
    void testEnsurePutOperation() {
        JsonObject target = new JsonObject();

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(
                PatchOperation.EXT_ENSURE_PUT,
                JsonPointer.compile("/a/b/c"),
                1,
                null
        ));

        patch.apply(target);
        assertEquals(1, target.getIntByPath("/a/b/c"));
    }


    @Test
    public void testAddAppendToArray() {
        List<Integer> src = new ArrayList<>(Arrays.asList(1, 2, 3));

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_ADD, JsonPointer.compile("/-"), 4, null));

        patch.apply(src);
        log.info("src: {}", src);
        assertEquals(Arrays.asList(1, 2, 3, 4), src);
    }

    @Test
    public void testReplaceArrayElement() {
        List<Integer> src = new ArrayList<>(Arrays.asList(1, 2, 3));

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_REPLACE, JsonPointer.compile("/1"), 20, null));

        patch.apply(src);
        assertEquals(Arrays.asList(1, 20, 3), src);
    }

    @Test
    public void testRemoveNonExistingPathIgnored() {
        List<Integer> src = new ArrayList<>(Arrays.asList(1, 2, 3));

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_REMOVE, JsonPointer.compile("/10"), null, null));

        assertThrows(JsonException.class, () -> patch.apply(src));
        assertEquals(Arrays.asList(1, 2, 3), src);
    }

    @Test
    public void testMoveInArray() {
        List<String> src = new ArrayList<>(Arrays.asList("a", "b", "c"));

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation(PatchOperation.STD_MOVE, JsonPointer.compile("/-"), null, JsonPointer.compile("/0")));

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
        List<Integer> b = Arrays.asList(1, 5, 3, 4);

        JsonPatch patch = JsonPatch.diff(a, b);
        System.out.println("patch=" + patch.toJson());

        List<Integer> c = Sjf4j.global().deepNode(a);
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

        Sjf4j sjf4j = Sjf4j.builder().jsonFacadeProvider(Fastjson2JsonFacade.provider()).build();
        JsonObject jo1 = sjf4j.fromJson(json1, JsonObject.class);
        log.info("jo1={}", jo1);
        JsonPatch patch = sjf4j.fromJson(jsonPatch, JsonPatch.class);
        log.info("patch={}", patch);
        patch.apply(jo1);
        log.info("jo1={}", jo1);

        JsonObject jo2 = sjf4j.fromJson(json2, JsonObject.class);
        assertEquals(jo2, jo1);
    }
}
