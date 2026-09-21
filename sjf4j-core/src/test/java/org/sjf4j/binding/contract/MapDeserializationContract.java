package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.JsonBinder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Portable map cases from Jackson's MapDeserializationTest. */
public abstract class MapDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** Source: MapDeserializationTest#testExactStringIntMap. */
    @Test void testExactStringIntMap() {
        Map<String, Integer> value = cast(binding(StreamingContext.EMPTY).readNode("{\"foo\":13,\"bar\":-39,\"\":0}", new TypeReference<Map<String, Integer>>() {}.getType()));
        assertEquals(13, value.get("foo")); assertEquals(-39, value.get("bar")); assertEquals(0, value.get("")); assertNull(value.get("missing"));
    }
    /** Source: MapDeserializationTest#testGenericStringIntMap. */
    @Test void testGenericStringIntMap() {
            assertEquals(Map.of("a", 1, "b", 2, "c", -99), binding(StreamingContext.EMPTY).readNode("{\"a\":1,\"b\":2,\"c\":-99}", new TypeReference<Map<String, Integer>>() {}.getType()));
        }
    /** Source: MapDeserializationTest#testUntypedMap2 (structural: source requests HashMap). */
    @Test void testUntypedMap2() {
            assertEquals("x", ((Map<?, ?>) binding(StreamingContext.EMPTY).readNode("{\"a\":\"x\"}", Map.class)).get("a"));
        }
    /** Source: MapDeserializationTest#testUntypedMap3. */
    @Test void testUntypedMap3() {
        Map<?, ?> root = assertInstanceOf(Map.class, binding(StreamingContext.EMPTY).readNode("{\"a\":[{\"a\":\"b\"},\"value\"]}", Map.class));
        assertEquals("b", ((Map<?, ?>) ((java.util.List<?>) root.get("a")).get(0)).get("a"));
    }
    /** Source: MapDeserializationTest#testExactStringStringMap. */
    @Test void testExactTreeMap() {
            assertEquals(Map.of("a", "b"), binding(StreamingContext.EMPTY).readNode("{\"a\":\"b\"}", new TypeReference<TreeMap<String, String>>() {}.getType()));
        }
    /** Retained SJF4J concrete-map coverage; no Jackson source attribution. */
    @Test void testConcreteStringMaps() {
        assertEquals(Map.of("a", "b"), binding(StreamingContext.EMPTY).readNode("{\"a\":\"b\"}", new TypeReference<HashMap<String, String>>() {}.getType()));
        assertEquals(Map.of("a", "b"), binding(StreamingContext.EMPTY).readNode("{\"a\":\"b\"}", new TypeReference<LinkedHashMap<String, String>>() {}.getType()));
    }
    /** Source: MapDeserializationTest#testIntBooleanMap (structural: source requests HashMap). */
    @Disabled("TODO: Map non-String keys need cross-path design beyond StreamingIO; defer for later.")
    @Test void testTypedNonStringKeys() {
            assertEquals(Map.of(1, true, -1, false), binding(StreamingContext.EMPTY).readNode("{\"1\":true,\"-1\":false}", new TypeReference<Map<Integer, Boolean>>() {}.getType()));
        }
    @SuppressWarnings("unchecked") private static <T> T cast(Object value) {
            return (T) value;
        }
}
