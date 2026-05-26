package org.sjf4j.compiled;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;

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

    public static class Root {
        public Child a;
    }

    public static class Child {
        public int b;
    }

}
