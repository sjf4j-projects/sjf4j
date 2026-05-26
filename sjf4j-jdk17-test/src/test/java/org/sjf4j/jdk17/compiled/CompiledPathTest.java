package org.sjf4j.jdk17.compiled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sjf4j.compiled.CompiledPath;
import org.sjf4j.exception.JsonException;

import java.util.HashMap;
import java.util.Map;

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

    public static class Root {
        public Integer a;
    }

    public static class RootWithMap {
        public Map<String, Integer> map;
    }

}
