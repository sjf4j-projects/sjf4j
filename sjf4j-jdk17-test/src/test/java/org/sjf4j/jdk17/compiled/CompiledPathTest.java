package org.sjf4j.jdk17.compiled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sjf4j.compiled.CompiledPath;
import org.sjf4j.exception.JsonException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledPathTest {

    @Test
    public void testAsm1() {
        CompiledPath<Root, Integer> path = CompiledPath.compile("$.a", Root.class, Integer.class);
        Assertions.assertInstanceOf(CompiledPath.class, path);
        assertTrue(path.getClass().getName().startsWith("org.sjf4j.bytecode.generated.CompiledPath_"));

        Root root = new Root();
        root.a = 5;
        assertEquals(Integer.valueOf(5), path.get(root));
        assertNull(path.put(root, 7));
        assertEquals(Integer.valueOf(7), root.a);
    }

    @Test
    public void testObjectRootIsRejectedByBytecodeCompiler() {
        JsonException ex = assertThrows(JsonException.class,
                () -> CompiledPath.compile("$.a", Object.class, Integer.class));
        assertTrue(ex.getMessage().contains("Object"));
        assertTrue(ex.getMessage().contains("FallbackCompiledPath"));
    }

    @Test
    public void testPutIfParentPresent() {
        CompiledPath<RootWithMap, Integer> path = CompiledPath.compile("$.map.a", RootWithMap.class, Integer.class);

        RootWithMap root = new RootWithMap();
        assertNull(path.putIfParentPresent(root, 2));
        assertNull(root.map);

        root.map = new HashMap<>();
        root.map.put("a", 1);
        assertEquals(Integer.valueOf(1), path.putIfParentPresent(root, 2));
        assertEquals(Integer.valueOf(2), root.map.get("a"));

        CompiledPath<RootWithMap, Integer> missingLeaf = CompiledPath.compile("$.map.b", RootWithMap.class, Integer.class);
        assertNull(missingLeaf.putIfParentPresent(root, 3));
        assertEquals(Integer.valueOf(3), root.map.get("b"));

    }

    @Test
    public void testCompute() {
        CompiledPath<Root, Integer> pojoPath = CompiledPath.compile("$.a", Root.class, Integer.class);
        Root root = new Root();
        root.a = 5;
        assertEquals(1, pojoPath.compute(root, (parent, current) -> ((Integer) current) + 2));
        assertEquals(Integer.valueOf(7), root.a);

        CompiledPath<RootWithMap, Integer> mapPath = CompiledPath.compile("$.map.a", RootWithMap.class, Integer.class);
        RootWithMap mapRoot = new RootWithMap();
        mapRoot.map = new HashMap<>();
        mapRoot.map.put("a", 1);
        assertEquals(1, mapPath.compute(mapRoot, (parent, current) -> ((Integer) current) + 1));
        assertEquals(Integer.valueOf(2), mapRoot.map.get("a"));

        RootWithMap missing = new RootWithMap();
        AtomicBoolean called = new AtomicBoolean(false);
        assertEquals(0, mapPath.compute(missing, (parent, current) -> {
            called.set(true);
            return 1;
        }));
        assertEquals(false, called.get());

        CompiledPath<RootWithList, Integer> listPath = CompiledPath.compile("$.items[0]", RootWithList.class, Integer.class);
        RootWithList listRoot = new RootWithList();
        listRoot.items = new ArrayList<>();
        listRoot.items.add(3);
        assertEquals(1, listPath.compute(listRoot, (parent, current) -> ((Integer) current) + 4));
        assertEquals(Integer.valueOf(7), listRoot.items.get(0));

        CompiledPath<RootWithList, Integer> listAppendByIndex = CompiledPath.compile("$.items[1]", RootWithList.class, Integer.class);
        assertEquals(1, listAppendByIndex.compute(listRoot, (parent, current) -> {
            assertNull(current);
            return 4;
        }));
        assertEquals(Integer.valueOf(4), listRoot.items.get(1));

        CompiledPath<RootWithList, Object> appendPath = CompiledPath.compile("$.items[+]", RootWithList.class, Object.class);
        assertEquals(1, appendPath.compute(listRoot, (parent, current) -> {
            assertNull(current);
            return 9;
        }));
        assertEquals(Integer.valueOf(9), listRoot.items.get(2));

        CompiledPath<RootWithArray, Integer> arrayAppendByIndex = CompiledPath.compile("$.items[1]", RootWithArray.class, Integer.class);
        RootWithArray arrayRoot = new RootWithArray();
        arrayRoot.items = new int[]{1};
        AtomicBoolean arrayCallbackCalled = new AtomicBoolean(false);
        assertThrows(JsonException.class, () -> arrayAppendByIndex.compute(arrayRoot, (parent, current) -> {
            arrayCallbackCalled.set(true);
            assertNull(current);
            return 4;
        }));
        assertTrue(arrayCallbackCalled.get());
    }

    public static class Root {
        public Integer a;
    }

    public static class RootWithMap {
        public Map<String, Integer> map;
    }

    public static class RootWithList {
        public List<Integer> items;
    }

    public static class RootWithArray {
        public int[] items;
    }

}
