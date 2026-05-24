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

    private static final Object[] NO_ARGS = new Object[0];

    @Test
    void testBuiltinsAndLookup() {
        assertTrue(FunctionRegistry.exists("length"));
        assertNotNull(FunctionRegistry.get("sum"));
        assertTrue(FunctionRegistry.getFunctionNames().contains("avg"));
        assertNull(FunctionRegistry.get("missing"));

        assertEquals(3, FunctionRegistry.invoke("length", "abc", NO_ARGS));
        assertEquals(2, FunctionRegistry.invoke("length", JsonObject.of("a", 1, "b", 2), NO_ARGS));
        assertEquals(3, FunctionRegistry.invoke("length", JsonArray.of(1, 2, 3), NO_ARGS));
        assertEquals(3, FunctionRegistry.invoke("count", JsonArray.of(1, 2, 3), NO_ARGS));
        assertNull(FunctionRegistry.invoke("length", true, NO_ARGS));
        assertEquals("x", FunctionRegistry.invoke("value", "x", NO_ARGS));
        assertEquals(6.0d, (Double) FunctionRegistry.invoke("sum", JsonArray.of(1, 2, 3), NO_ARGS));
        assertEquals(0.0d, (Double) FunctionRegistry.invoke("sum", JsonObject.of("a", 1), NO_ARGS));
        assertEquals(1.0d, (Double) FunctionRegistry.invoke("min", JsonArray.of(3, 1, 2), NO_ARGS));
        assertNull(FunctionRegistry.invoke("min", JsonArray.of(), NO_ARGS));
        assertEquals(3.0d, (Double) FunctionRegistry.invoke("max", JsonArray.of(3, 1, 2), NO_ARGS));
        assertNull(FunctionRegistry.invoke("max", JsonArray.of(), NO_ARGS));
        assertEquals(2.0d, (Double) FunctionRegistry.invoke("avg", JsonArray.of(1, 2, 3), NO_ARGS));
        assertNull(FunctionRegistry.invoke("avg", JsonArray.of(), NO_ARGS));
        assertEquals(Math.sqrt(2.0d / 3.0d), (Double) FunctionRegistry.invoke("stddev", JsonArray.of(1, 2, 3), NO_ARGS));
        assertNull(FunctionRegistry.invoke("stddev", JsonArray.of(), NO_ARGS));
        assertEquals("a", FunctionRegistry.invoke("first", JsonArray.of("a", "b"), NO_ARGS));
        assertNull(FunctionRegistry.invoke("first", JsonArray.of(), NO_ARGS));
        assertEquals("b", FunctionRegistry.invoke("last", JsonArray.of("a", "b"), NO_ARGS));
        assertNull(FunctionRegistry.invoke("last", JsonArray.of(), NO_ARGS));
        assertEquals("b", FunctionRegistry.invoke("index", JsonArray.of("a", "b"), new Object[]{1}));
        assertNull(FunctionRegistry.invoke("index", JsonObject.of("a", 1), new Object[]{0}));
        assertTrue((Boolean) FunctionRegistry.invoke("match", "alice@example.com", new Object[]{"alice@example.com"}));
        assertFalse((Boolean) FunctionRegistry.invoke("match", 123, new Object[]{"alice@example.com"}));
        assertTrue((Boolean) FunctionRegistry.invoke("search", "alice@example.com", new Object[]{"example"}));
        assertFalse((Boolean) FunctionRegistry.invoke("search", 123, new Object[]{"example"}));
        assertNull(FunctionRegistry.invoke("count", JsonObject.of("a", 1), NO_ARGS));
    }

    @Test
    void testBuiltinErrorsAndCustomRegistration() {
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("missing", null, NO_ARGS));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("length", "abc", new Object[]{1}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("count", JsonArray.of(), new Object[]{1}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("value", "x", new Object[]{1}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("sum", JsonArray.of(), new Object[]{1}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("min", JsonArray.of(), new Object[]{1}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("max", JsonArray.of(), new Object[]{1}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("avg", JsonArray.of(), new Object[]{1}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("stddev", JsonArray.of(), new Object[]{1}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("first", JsonArray.of(), new Object[]{1}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("last", JsonArray.of(), new Object[]{1}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("match", "abc", NO_ARGS));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("match", "abc", new Object[]{null}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("match", "abc", new Object[]{JsonObject.of("p", 1)}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("search", "abc", NO_ARGS));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("search", "abc", new Object[]{null}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("search", "abc", new Object[]{JsonObject.of("p", 1)}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("index", JsonArray.of("a"), new Object[]{"x"}));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("index", JsonArray.of("a"), NO_ARGS));
        assertThrows(JsonException.class, () -> new FunctionRegistry.FunctionDescriptor("", (target, args) -> null));
        assertThrows(NullPointerException.class, () -> new FunctionRegistry.FunctionDescriptor(null, (target, args) -> null));
        assertThrows(NullPointerException.class, () -> new FunctionRegistry.FunctionDescriptor("x", null));

        FunctionRegistry.FunctionDescriptor descriptor =
                new FunctionRegistry.FunctionDescriptor("triple", (target, args) -> ((Number) target).intValue() * 3);
        FunctionRegistry.register(descriptor);

        assertEquals("triple", descriptor.getName());
        assertEquals(12, FunctionRegistry.invoke("triple", 4, NO_ARGS));
        FunctionRegistry.register(new FunctionRegistry.FunctionDescriptor("boom", (target, args) -> {
            throw new IllegalStateException("boom");
        }));
        assertThrows(JsonException.class, () -> FunctionRegistry.invoke("boom", 1, NO_ARGS));
    }
}
