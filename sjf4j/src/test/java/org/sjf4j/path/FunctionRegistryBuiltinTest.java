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

class FunctionRegistryBuiltinTest {

    @Test
    void testBuiltinsAndLookup() {
        assertTrue(FunctionRegistry.exists("length"));
        assertNotNull(FunctionRegistry.get("sum"));
        assertTrue(FunctionRegistry.getFunctionNames().contains("avg"));
        assertNull(FunctionRegistry.get("missing"));

        assertEquals(3, FunctionRegistry.invoke("length", new Object[]{"abc"}));
        assertEquals(2, FunctionRegistry.invoke("length", new Object[]{JsonObject.of("a", 1, "b", 2)}));
        assertEquals(3, FunctionRegistry.invoke("length", new Object[]{JsonArray.of(1, 2, 3)}));
        assertEquals(3, FunctionRegistry.invoke("count", new Object[]{JsonArray.of(1, 2, 3)}));
        assertNull(FunctionRegistry.invoke("length", new Object[]{true}));
        assertEquals("x", FunctionRegistry.invoke("value", new Object[]{"x"}));
        assertEquals(6.0d, (Double) FunctionRegistry.invoke("sum", new Object[]{JsonArray.of(1, 2, 3)}));
        assertEquals(0.0d, (Double) FunctionRegistry.invoke("sum", new Object[]{JsonObject.of("a", 1)}));
        assertEquals(1.0d, (Double) FunctionRegistry.invoke("min", new Object[]{JsonArray.of(3, 1, 2)}));
        assertNull(FunctionRegistry.invoke("min", new Object[]{JsonArray.of()}));
        assertEquals(3.0d, (Double) FunctionRegistry.invoke("max", new Object[]{JsonArray.of(3, 1, 2)}));
        assertNull(FunctionRegistry.invoke("max", new Object[]{JsonArray.of()}));
        assertEquals(2.0d, (Double) FunctionRegistry.invoke("avg", new Object[]{JsonArray.of(1, 2, 3)}));
        assertNull(FunctionRegistry.invoke("avg", new Object[]{JsonArray.of()}));
        assertEquals(Math.sqrt(2.0d / 3.0d), (Double) FunctionRegistry.invoke("stddev", new Object[]{JsonArray.of(1, 2, 3)}));
        assertNull(FunctionRegistry.invoke("stddev", new Object[]{JsonArray.of()}));
        assertEquals("a", FunctionRegistry.invoke("first", new Object[]{JsonArray.of("a", "b")}));
        assertNull(FunctionRegistry.invoke("first", new Object[]{JsonArray.of()}));
        assertEquals("b", FunctionRegistry.invoke("last", new Object[]{JsonArray.of("a", "b")}));
        assertNull(FunctionRegistry.invoke("last", new Object[]{JsonArray.of()}));
        assertEquals("b", FunctionRegistry.invoke("index", new Object[]{JsonArray.of("a", "b"), 1}));
        assertNull(FunctionRegistry.invoke("index", new Object[]{JsonObject.of("a", 1), 0}));
        assertTrue((Boolean) FunctionRegistry.invoke("match", new Object[]{"alice@example.com", "alice@example.com"}));
        assertFalse((Boolean) FunctionRegistry.invoke("match", new Object[]{123, "alice@example.com"}));
        assertTrue((Boolean) FunctionRegistry.invoke("search", new Object[]{"alice@example.com", "example"}));
        assertFalse((Boolean) FunctionRegistry.invoke("search", new Object[]{123, "example"}));
        assertNull(FunctionRegistry.invoke("count", new Object[]{JsonObject.of("a", 1)}));
    }

    @Test
    void testBuiltinErrorsAndCustomRegistration() {
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("missing", new Object[0]));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("length", new Object[0]));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("count", new Object[0]));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("value", new Object[0]));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("sum", new Object[0]));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("min", new Object[0]));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("max", new Object[0]));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("avg", new Object[0]));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("stddev", new Object[0]));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("first", new Object[0]));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("last", new Object[0]));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("match", new Object[]{"abc"}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("match", new Object[]{"abc", null}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("match", new Object[]{"abc", JsonObject.of("p", 1)}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("search", new Object[]{"abc"}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("search", new Object[]{"abc", null}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("search", new Object[]{"abc", JsonObject.of("p", 1)}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("index", new Object[]{JsonArray.of("a"), "x"}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("index", new Object[]{JsonArray.of("a")}));
        assertThrows(JsonException.class, () -> new FunctionRegistry.FunctionDescriptor("", args -> null));
        assertThrows(NullPointerException.class, () -> new FunctionRegistry.FunctionDescriptor(null, args -> null));
        assertThrows(NullPointerException.class, () -> new FunctionRegistry.FunctionDescriptor("x", null));

        FunctionRegistry.FunctionDescriptor descriptor =
                new FunctionRegistry.FunctionDescriptor("triple", args -> ((Number) args[0]).intValue() * 3);
        FunctionRegistry.register(descriptor);

        assertEquals("triple", descriptor.getName());
        assertEquals(12, FunctionRegistry.invoke("triple", new Object[]{4}));
        FunctionRegistry.register(new FunctionRegistry.FunctionDescriptor("boom", args -> {
            throw new IllegalStateException("boom");
        }));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("boom", new Object[]{1}));
    }
}
