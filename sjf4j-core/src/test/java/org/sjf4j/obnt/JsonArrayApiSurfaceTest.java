package org.sjf4j.obnt;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.NodeException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonArrayApiSurfaceTest {

    static class TypedIntegerArray extends JsonArray {
        TypedIntegerArray() {}
        TypedIntegerArray(Object node) {
                addAll(node);
            }
        @Override
        public Class<?> elementClass() {
                return Integer.class;
            }
    }

    @Test
    void testJsonArraySurface() {
        JsonArray array = JsonArray.of(
                "12",
                34,
                56L,
                "7.5",
                8.25d,
                true,
                JsonObject.of("k", "v"),
                JsonArray.of("x", "y"),
                null,
                (short) 9,
                (byte) 4,
                new BigInteger("123"),
                new BigDecimal("4.5"),
                "false",
                JsonArray.of(1, 2, 3)
        );

        assertEquals("12", array.getString(0));
        assertEquals("fallback", array.getString(99, "fallback"));
        assertEquals("34", array.getAsString(1));
        assertEquals(34, array.getNumber(1).intValue());
        assertEquals(9, array.getNumber(99, 9).intValue());
        assertEquals(12, array.getAsNumber(0).intValue());
        assertEquals(56L, array.getLong(2));
        assertEquals(7L, array.getLong(99, 7L));
        assertEquals(12L, array.getAsLong(0));
        assertEquals(34, array.getInt(1));
        assertEquals(7, array.getInt(99, 7));
        assertEquals(12, array.getAsInt(0));
        assertEquals((short) 9, array.getShort(9));
        assertEquals((short) 7, array.getShort(99, (short) 7));
        assertEquals((short) 12, array.getAsShort(0));
        assertEquals((byte) 4, array.getByte(10));
        assertEquals((byte) 7, array.getByte(99, (byte) 7));
        assertEquals((byte) 12, array.getAsByte(0));
        assertEquals(8.25d, array.getDouble(4));
        assertEquals(7.0d, array.getDouble(99, 7.0d));
        assertEquals(7.5d, array.getAsDouble(3));
        assertEquals(8.25f, array.getFloat(4));
        assertEquals(7.0f, array.getFloat(99, 7.0f));
        assertEquals(7.5f, array.getAsFloat(3));
        assertEquals(new BigInteger("123"), array.getBigInteger(11));
        assertEquals(BigInteger.TEN, array.getBigInteger(99, BigInteger.TEN));
        assertEquals(new BigInteger("12"), array.getAsBigInteger(0));
        assertEquals(new BigDecimal("4.5"), array.getBigDecimal(12));
        assertEquals(BigDecimal.TEN, array.getBigDecimal(99, BigDecimal.TEN));
        assertEquals(new BigDecimal("7.5"), array.getAsBigDecimal(3));
        assertTrue(array.getBoolean(5));
        assertTrue(array.getBoolean(99, true));
        assertFalse(array.getAsBoolean(13));
        assertEquals("v", array.getJsonObject(6).getString("k"));
        assertEquals("v", array.getMap(6).get("k"));
        assertEquals("v", array.getMap(6, String.class).get("k"));
        assertEquals(JsonArray.of("x", "y"), array.getJsonArray(7));
        assertEquals(Arrays.asList("x", "y"), array.getList(7));
        assertEquals(Arrays.asList("x", "y"), array.getList(7, String.class));
        assertArrayEquals(new Object[]{"x", "y"}, array.getArray(7));
        assertArrayEquals(new String[]{"x", "y"}, array.getArray(7, String.class));
        assertEquals(Arrays.asList(1, 2, 3), array.getList(14, Integer.class));
        assertArrayEquals(new Integer[]{1, 2, 3}, array.getArray(14, Integer.class));
        assertEquals(34, array.get(1, Integer.class));
        assertEquals(12, array.getAs(0, Integer.class));
        assertEquals(34, array.<Integer>get(1));
        assertEquals(12, array.<Integer>getAs(0));
        assertThrows(NodeException.class, () -> array.get(0, "boom"));
        assertThrows(NodeException.class, () -> array.getAs(0, "boom"));

        JsonArray mutated = array.copy();
        assertEquals(array, mutated);
        mutated.addNonNull(null);
        mutated.add("tail");
        mutated.add(1, "inserted");
        assertEquals("inserted", mutated.getString(1));
        assertEquals("tail", mutated.remove(-1));
        assertTrue(mutated.containsIndex(-1));
        assertTrue(mutated.hasNonNull(0));
        assertFalse(mutated.hasNonNull(9));
        assertTrue(mutated.containsValue("inserted"));
        assertTrue(mutated.anyMatch((index, value) -> index == 1 && "inserted".equals(value)));
        assertEquals(Arrays.asList("12", "inserted", 34), Arrays.asList(mutated.iterator().next(), mutated.getNode(1), mutated.getNode(2)));

        JsonArray nullSlot = JsonArray.of("a", null, "c");
        assertEquals("a", nullSlot.setIfAbsent(0, "x"));
        assertNull(nullSlot.setIfAbsent(1, "b"));
        assertEquals("c", nullSlot.set(-1, "z"));
        assertEquals("z", nullSlot.getString(2));

        JsonArray appended = new JsonArray();
        appended.addAll(new short[]{1, 2});
        appended.addAll(new int[]{3, 4});
        appended.addAll(new long[]{5L});
        appended.addAll(new float[]{6.5f});
        appended.addAll(new double[]{7.5d});
        appended.addAll(new char[]{'a'});
        appended.addAll(new boolean[]{true});
        appended.addAll(Arrays.asList("x", "y"));
        appended.addAll(JsonArray.of("z"));
        assertEquals(12, appended.size());
        assertEquals(Collections.singleton("z"), JsonArray.of("z").toSet());
        assertEquals(Collections.singleton(1), JsonArray.of(1).toSet(Integer.class));
        assertEquals(Arrays.asList("a", "b"), JsonArray.of("a", "b").toList(String.class));
        assertArrayEquals(new String[]{"a", "b"}, JsonArray.of("a", "b").toArray(String.class));
        assertEquals(Arrays.asList("x", "y"), JsonArray.fromNode(Arrays.asList("x", "y")).toNode(List.class));
        JsonObject arrayNested = JsonObject.of("k", "v");
        List<?> boundList = JsonArray.of(arrayNested).bindNode(List.class);
        assertSame(arrayNested, boundList.get(0));
        List<?> copiedList = JsonArray.of(arrayNested).toNode(List.class);
        assertNotSame(arrayNested, copiedList.get(0));
        assertEquals(1, array.stream().count());

        JsonArray deepCopy = JsonArray.of(JsonObject.of("k", "v")).deepCopy();
        array.getJsonObject(6).put("k", "changed");
        assertEquals("v", deepCopy.getJsonObject(0).getString("k"));

        assertThrows(NodeException.class, () -> new TypedIntegerArray(Arrays.asList(1, "x")));
        TypedIntegerArray integers = new TypedIntegerArray();
        integers.add(1);
        assertThrows(NodeException.class, () -> integers.add("x"));
        mutated.clear();
        assertTrue(mutated.isEmpty());
        assertNotEquals(array, mutated);
    }
}
