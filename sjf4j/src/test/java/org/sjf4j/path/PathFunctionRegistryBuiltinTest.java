package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathFunctionRegistryBuiltinTest {

    @Test
    void testBuiltinsAndLookup() {
        assertTrue(PathFunctionRegistry.exists("length"));
        assertNotNull(PathFunctionRegistry.get("sum"));
        assertTrue(PathFunctionRegistry.getFunctionNames().contains("avg"));

        assertEquals(3, PathFunctionRegistry.invoke("length", new Object[]{"abc"}));
        assertEquals(2, PathFunctionRegistry.invoke("length", new Object[]{JsonObject.of("a", 1, "b", 2)}));
        assertEquals(3, PathFunctionRegistry.invoke("count", new Object[]{JsonArray.of(1, 2, 3)}));
        assertEquals("x", PathFunctionRegistry.invoke("value", new Object[]{"x"}));
        assertEquals(6.0d, (Double) PathFunctionRegistry.invoke("sum", new Object[]{JsonArray.of(1, 2, 3)}));
        assertEquals(1.0d, (Double) PathFunctionRegistry.invoke("min", new Object[]{JsonArray.of(3, 1, 2)}));
        assertEquals(3.0d, (Double) PathFunctionRegistry.invoke("max", new Object[]{JsonArray.of(3, 1, 2)}));
        assertEquals(2.0d, (Double) PathFunctionRegistry.invoke("avg", new Object[]{JsonArray.of(1, 2, 3)}));
        assertEquals(2.0d / 3.0d, (Double) PathFunctionRegistry.invoke("stddev", new Object[]{JsonArray.of(1, 2, 3)}));
        assertEquals("a", PathFunctionRegistry.invoke("first", new Object[]{JsonArray.of("a", "b")}));
        assertEquals("b", PathFunctionRegistry.invoke("last", new Object[]{JsonArray.of("a", "b")}));
        assertEquals("b", PathFunctionRegistry.invoke("index", new Object[]{JsonArray.of("a", "b"), 1}));
        assertTrue((Boolean) PathFunctionRegistry.invoke("match", new Object[]{"alice@example.com", "alice@example.com"}));
        assertTrue((Boolean) PathFunctionRegistry.invoke("search", new Object[]{"alice@example.com", "example"}));
        assertFalse((Boolean) PathFunctionRegistry.invoke("search", new Object[]{123, "example"}));
        assertNull(PathFunctionRegistry.invoke("count", new Object[]{JsonObject.of("a", 1)}));
    }

    @Test
    void testBuiltinErrorsAndCustomRegistration() {
        assertThrows(JsonException.class, () -> PathFunctionRegistry.invoke("missing", new Object[0]));
        assertThrows(JsonException.class, () -> PathFunctionRegistry.invoke("length", new Object[0]));
        assertThrows(JsonException.class, () -> PathFunctionRegistry.invoke("match", new Object[]{"abc", null}));
        assertThrows(JsonException.class, () -> PathFunctionRegistry.invoke("index", new Object[]{JsonArray.of("a"), "x"}));
        assertThrows(JsonException.class, () -> new PathFunctionRegistry.FunctionDescriptor("", args -> null));
        assertThrows(NullPointerException.class, () -> new PathFunctionRegistry.FunctionDescriptor(null, args -> null));
        assertThrows(NullPointerException.class, () -> new PathFunctionRegistry.FunctionDescriptor("x", null));

        PathFunctionRegistry.FunctionDescriptor descriptor =
                new PathFunctionRegistry.FunctionDescriptor("triple", args -> ((Number) args[0]).intValue() * 3);
        PathFunctionRegistry.register(descriptor);

        assertEquals("triple", descriptor.getName());
        assertEquals(12, PathFunctionRegistry.invoke("triple", new Object[]{4}));
        PathFunctionRegistry.register(new PathFunctionRegistry.FunctionDescriptor("boom", args -> {
            throw new IllegalStateException("boom");
        }));
        assertThrows(JsonException.class, () -> PathFunctionRegistry.invoke("boom", new Object[]{1}));
    }
}
