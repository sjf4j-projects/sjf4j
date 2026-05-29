package org.sjf4j.jdk17.compiled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sjf4j.bytecode.BytecodePath;
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

public class BytecodePathTest {

    @Test
    public void testAsm1() {
        BytecodePath<Root, Integer> path = BytecodePath.compile("$.a", Root.class, Integer.class);
        Assertions.assertInstanceOf(BytecodePath.class, path);
        assertTrue(path.getClass().getName().startsWith("org.sjf4j.bytecode.generated.BytecodePath_"));

        Root root = new Root();
        root.a = 5;
        assertEquals(Integer.valueOf(5), path.get(root));
        assertNull(path.put(root, 7));
        assertEquals(Integer.valueOf(7), root.a);
    }

    @Test
    public void testObjectRootIsRejectedByBytecodeCompiler() {
        JsonException ex = assertThrows(JsonException.class,
                () -> BytecodePath.compile("$.a", Object.class, Integer.class));
        assertTrue(ex.getMessage().contains("Object"));
        assertTrue(ex.getMessage().contains("FallbackCompiledPath"));
    }

    @Test
    public void testPutIfParentPresent() {
        BytecodePath<RootWithMap, Integer> path = BytecodePath.compile("$.map.a", RootWithMap.class, Integer.class);

        RootWithMap root = new RootWithMap();
        assertNull(path.putIfParentPresent(root, 2));
        assertNull(root.map);

        root.map = new HashMap<>();
        root.map.put("a", 1);
        assertEquals(Integer.valueOf(1), path.putIfParentPresent(root, 2));
        assertEquals(Integer.valueOf(2), root.map.get("a"));

        BytecodePath<RootWithMap, Integer> missingLeaf = BytecodePath.compile("$.map.b", RootWithMap.class, Integer.class);
        assertNull(missingLeaf.putIfParentPresent(root, 3));
        assertEquals(Integer.valueOf(3), root.map.get("b"));

    }

    @Test
    public void testCompute() {
        BytecodePath<Root, Integer> pojoPath = BytecodePath.compile("$.a", Root.class, Integer.class);
        Root root = new Root();
        root.a = 5;
        assertEquals(1, pojoPath.compute(root, (parent, current) -> ((Integer) current) + 2));
        assertEquals(Integer.valueOf(7), root.a);

        BytecodePath<RootWithMap, Integer> mapPath = BytecodePath.compile("$.map.a", RootWithMap.class, Integer.class);
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

        BytecodePath<RootWithList, Integer> listPath = BytecodePath.compile("$.items[0]", RootWithList.class, Integer.class);
        RootWithList listRoot = new RootWithList();
        listRoot.items = new ArrayList<>();
        listRoot.items.add(3);
        assertEquals(1, listPath.compute(listRoot, (parent, current) -> ((Integer) current) + 4));
        assertEquals(Integer.valueOf(7), listRoot.items.get(0));

        BytecodePath<RootWithList, Integer> listAppendByIndex = BytecodePath.compile("$.items[1]", RootWithList.class, Integer.class);
        assertEquals(1, listAppendByIndex.compute(listRoot, (parent, current) -> {
            assertNull(current);
            return 4;
        }));
        assertEquals(Integer.valueOf(4), listRoot.items.get(1));

        BytecodePath<RootWithList, Object> appendPath = BytecodePath.compile("$.items[+]", RootWithList.class, Object.class);
        assertEquals(1, appendPath.compute(listRoot, (parent, current) -> {
            assertNull(current);
            return 9;
        }));
        assertEquals(Integer.valueOf(9), listRoot.items.get(2));

        BytecodePath<RootWithArray, Integer> arrayAppendByIndex = BytecodePath.compile("$.items[1]", RootWithArray.class, Integer.class);
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

    @Test
    public void testEnsurePutIfAbsent() {
        BytecodePath<RootWithMap, Integer> mapPath = BytecodePath.compile("$.map.a", RootWithMap.class, Integer.class);
        RootWithMap mapRoot = new RootWithMap();
        assertNull(mapPath.ensurePutIfAbsent(mapRoot, 1));
        assertEquals(Integer.valueOf(1), mapRoot.map.get("a"));

        assertEquals(Integer.valueOf(1), mapPath.ensurePutIfAbsent(mapRoot, 2));
        assertEquals(Integer.valueOf(1), mapRoot.map.get("a"));

        mapRoot.map.put("a", null);
        assertNull(mapPath.ensurePutIfAbsent(mapRoot, 3));
        assertEquals(Integer.valueOf(3), mapRoot.map.get("a"));

        BytecodePath<RootWithList, Integer> listPath = BytecodePath.compile("$.items[1]", RootWithList.class, Integer.class);
        RootWithList listRoot = new RootWithList();
        listRoot.items = new ArrayList<>();
        listRoot.items.add(4);
        assertNull(listPath.ensurePutIfAbsent(listRoot, 5));
        assertEquals(Integer.valueOf(5), listRoot.items.get(1));
        assertEquals(Integer.valueOf(5), listPath.ensurePutIfAbsent(listRoot, 6));
        assertEquals(Integer.valueOf(5), listRoot.items.get(1));

        BytecodePath<RootWithList, Object> appendPath = BytecodePath.compile("$.items[+]", RootWithList.class, Object.class);
        assertNull(appendPath.ensurePutIfAbsent(listRoot, 7));
        assertEquals(Integer.valueOf(7), listRoot.items.get(2));

        BytecodePath<RootWithObjectList, Object> intermediateAppend =
                BytecodePath.compile("$.items[+].value", RootWithObjectList.class, Object.class);
        RootWithObjectList objectListRoot = new RootWithObjectList();
        objectListRoot.items = new ArrayList<>();
        JsonException ex = assertThrows(JsonException.class,
                () -> intermediateAppend.ensurePutIfAbsent(objectListRoot, 8));
        assertTrue(ex.getMessage().contains("ensurePutIfAbsent"));

        BytecodePath<RootWithBookList, Integer> defaultLeafPath =
                BytecodePath.compile("$.items[0].value", RootWithBookList.class, Integer.class);
        RootWithBookList defaultLeafRoot = new RootWithBookList();
        defaultLeafRoot.items = new ArrayList<>();
        assertEquals(Integer.valueOf(11), defaultLeafPath.ensurePutIfAbsent(defaultLeafRoot, 12));
        assertEquals(Integer.valueOf(11), defaultLeafRoot.items.get(0).value);
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

    public static class RootWithObjectList {
        public List<Object> items;
    }

    public static class RootWithBookList {
        public List<Book> items;
    }

    public static class Book {
        public Integer value = 11;
    }

    public static class RootWithArray {
        public int[] items;
    }

}
