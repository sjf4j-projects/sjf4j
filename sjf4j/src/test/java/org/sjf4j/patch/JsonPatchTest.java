package org.sjf4j.patch;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.util.ContainerUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class JsonPatchTest {

    @Test
    public void testAddAppendToArray() {
        List<Integer> src = new ArrayList<>(Arrays.asList(1, 2, 3));

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(PatchOp.STD_ADD, JsonPointer.compile("/-"), 4, null));

        Object result = patch.apply(src);
        log.info("result: {}", result);
        assertEquals(Arrays.asList(1, 2, 3, 4), result);
    }

    @Test
    public void testReplaceArrayElement() {
        List<Integer> src = new ArrayList<>(Arrays.asList(1, 2, 3));

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(PatchOp.STD_REPLACE, JsonPointer.compile("/1"), 20, null));

        Object result = patch.apply(src);
        assertEquals(Arrays.asList(1, 20, 3), result);
    }

    @Test
    public void testRemoveNonExistingPathIgnored() {
        List<Integer> src = new ArrayList<>(Arrays.asList(1, 2, 3));

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(PatchOp.STD_REMOVE, JsonPointer.compile("/10"), null, null));

        Object result = patch.apply(src);
        assertEquals(Arrays.asList(1, 2, 3), result);
    }

    @Test
    public void testMoveInArray() {
        List<String> src = new ArrayList<>(Arrays.asList("a", "b", "c"));

        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp(PatchOp.STD_MOVE, JsonPointer.compile("/-"), null, JsonPointer.compile("/0")));

        Object result = patch.apply(src);
        assertEquals(Arrays.asList("b", "c", "a"), result);
    }

    @Test
    public void testDiffAndApply() {
        List<Integer> a = Arrays.asList(1, 2, 3);
        List<Integer> b = Arrays.asList(1, 20, 3, 4);

        JsonPatch patch = JsonPatch.diff(a, b);
        Object result = patch.apply(ContainerUtil.deepCopy(a));

        assertEquals(b, result);
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
