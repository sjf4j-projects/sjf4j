package org.sjf4j.compiled;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.JsonPath;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledPathTest {


    @Test
    public void testAsm1() {
        JsonException ex = assertThrows(JsonException.class,
                () -> CompiledPath.compile("$.a.b", Root.class, Integer.class));
        assertTrue(ex.getMessage().contains("sjf4j-bytecode"));
    }

    @Test
    public void testFallbackPutIfParentPresent() {
        FallbackCompiledPath<JsonObject, Integer> path = new FallbackCompiledPath<>(
                JsonPath.parse("$.nested.value"), JsonObject.class, Integer.class);

        JsonObject root = JsonObject.of();
        assertNull(path.putIfParentPresent(root, 2));
        assertNull(root.getNode("nested"));

        JsonObject nested = JsonObject.of("value", 1);
        root.put("nested", nested);
        assertEquals(Integer.valueOf(1), path.putIfParentPresent(root, 2));
        assertEquals(Integer.valueOf(2), nested.getNode("value"));
    }

    @Test
    public void testFallbackCompute() {
        FallbackCompiledPath<JsonObject, Integer> path = new FallbackCompiledPath<>(
                JsonPath.parse("$.nested.value"), JsonObject.class, Integer.class);

        JsonObject root = JsonObject.of("nested", JsonObject.of("value", 1));
        assertEquals(1, path.compute(root, (parent, current) -> ((Integer) current) + 1));
        assertEquals(Integer.valueOf(2), ((JsonObject) root.getNode("nested")).getNode("value"));

        JsonObject missing = JsonObject.of();
        AtomicBoolean called = new AtomicBoolean(false);
        assertEquals(0, path.compute(missing, (parent, current) -> {
            called.set(true);
            return 3;
        }));
        assertEquals(false, called.get());

        FallbackCompiledPath<JsonObject, Object> append = new FallbackCompiledPath<>(
                JsonPath.parse("$.items[+]"), JsonObject.class, Object.class);
        JsonArray items = new JsonArray();
        JsonObject appendRoot = JsonObject.of("items", items);
        assertEquals(1, append.compute(appendRoot, (parent, current) -> {
            assertNull(current);
            return "x";
        }));
        assertEquals("x", items.get(0));
    }

    @Test
    public void testFallbackEnsurePutIfAbsent() {
        FallbackCompiledPath<JsonObject, Integer> path = new FallbackCompiledPath<>(
                JsonPath.parse("$.nested.value"), JsonObject.class, Integer.class);

        JsonObject root = JsonObject.of();
        assertNull(path.ensurePutIfAbsent(root, 1));
        assertEquals(Integer.valueOf(1), Nodes.getInObject(root.getNode("nested"), "value"));

        assertEquals(Integer.valueOf(1), path.ensurePutIfAbsent(root, 2));
        assertEquals(Integer.valueOf(1), Nodes.getInObject(root.getNode("nested"), "value"));

        Nodes.putInObject(root.getNode("nested"), "value", null);
        assertNull(path.ensurePutIfAbsent(root, 3));
        assertEquals(Integer.valueOf(3), Nodes.getInObject(root.getNode("nested"), "value"));

        FallbackCompiledPath<JsonObject, Object> append = new FallbackCompiledPath<>(
                JsonPath.parse("$.items[+]"), JsonObject.class, Object.class);
        JsonArray items = new JsonArray();
        root.put("items", items);
        assertNull(append.ensurePutIfAbsent(root, "x"));
        assertEquals("x", items.get(0));

        FallbackCompiledPath<JsonObject, Object> intermediateAppend = new FallbackCompiledPath<>(
                JsonPath.parse("$.items[+].value"), JsonObject.class, Object.class);
        assertThrows(JsonException.class, () -> intermediateAppend.ensurePutIfAbsent(root, "y"));
    }

    public static class Root {
        public Child a;
    }

    public static class Child {
        public int b;
    }

}
