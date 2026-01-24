package org.sjf4j.patch;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Slf4j
public class PatchUtilTest {


    @SuppressWarnings("unchecked")
    @Test
    public void testMergeOverwriteAndDeepCopy() {
        Map<String, Object> target = new HashMap<>();
        target.put("a", 1);
        target.put("b", new HashMap<>(Collections.singletonMap("x", 10)));

        Map<String, Object> patch = new HashMap<>();
        patch.put("a", 2);
        patch.put("b", new HashMap<>(Collections.singletonMap("y", 20)));
        patch.put("c", 3);

        // merge with overwrite, no deep copy
        PatchUtil.merge(target, patch, true, false);

        assertEquals(2, target.get("a"));
        Map<String, Object> b = (Map<String, Object>) target.get("b");
        assertEquals(10, b.get("x"));
        assertEquals(20, b.get("y"));
        assertEquals(3, target.get("c"));
    }

    @Test
    public void testMergeWithoutOverwrite() {
        Map<String, Object> target = new HashMap<>();
        target.put("a", 1);

        Map<String, Object> patch = new HashMap<>();
        patch.put("a", 2);  // should not overwrite
        patch.put("b", 3);

        PatchUtil.merge(target, patch, false, false);

        assertEquals(1, target.get("a"));
        assertEquals(3, target.get("b"));
    }

    @Test
    public void testMergeArray() {
        List<Object> target = new ArrayList<>(Arrays.asList(1, 2, 3));
        List<Object> patch = new ArrayList<>(Arrays.asList(10, 20, 30));

        PatchUtil.merge(target, patch, true, false);

        assertEquals(Arrays.asList(10, 20, 30), target);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMergeNestedArrayAndObject() {
        Map<String, Object> target = new HashMap<>();
        target.put("arr", new ArrayList<>(Arrays.asList(
                new HashMap<>(Collections.singletonMap("x", 1)),
                new HashMap<>(Collections.singletonMap("y", 2))
        )));

        Map<String, Object> patch = new HashMap<>();
        patch.put("arr", new ArrayList<>(Arrays.asList(
                new HashMap<>(Collections.singletonMap("y", 10)),
                new HashMap<>(Collections.singletonMap("z", 20))
        )));

        PatchUtil.merge(target, patch, true, false);

        List<Map<String, Object>> arr = (List<Map<String, Object>>) target.get("arr");
        assertEquals(2, arr.size());
        assertEquals(10, arr.get(0).get("y"));
        assertEquals(20, arr.get(1).get("z"));
    }

    @Test
    public void testMergeRfc7386DeletesAndArrayReplace() {
        Map<String, Object> target = new HashMap<>();
        target.put("a", 1);
        target.put("b", null);
        target.put("c", new ArrayList<>(Arrays.asList(1, 2, 3)));

        Map<String, Object> patch = new HashMap<>();
        patch.put("a", null);  // delete key
        patch.put("c", new ArrayList<>(Arrays.asList(10, 20)));  // replace array
        patch.put("d", 5);

        PatchUtil.mergeRfc7386(target, patch);

        assertFalse(target.containsKey("a"));  // deleted
        assertNull(target.get("b"));  // unchanged, null is valid target value
        assertEquals(Arrays.asList(10, 20), target.get("c"));  // replaced
        assertEquals(5, target.get("d"));
    }


    @Test
    public void testMergeRfc7386Nested() {
        Map<String, Object> target = new HashMap<>();
        target.put("obj", new JsonObject("x", 1, "y", 2));

        Map<String, Object> patch = new HashMap<>();
        patch.put("obj", new JsonObject("x", 10, "z", 3));

        PatchUtil.mergeRfc7386(target, patch);

        JsonObject obj = (JsonObject) target.get("obj");
        assertEquals(10, obj.getInteger("x"));  // merged
        assertEquals(2, obj.getInteger("y"));   // preserved
        assertEquals(3, obj.getInteger("z"));   // added
    }

}
