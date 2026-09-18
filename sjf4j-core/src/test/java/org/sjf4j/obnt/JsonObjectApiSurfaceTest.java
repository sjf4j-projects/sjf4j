package org.sjf4j.obnt;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonObjectApiSurfaceTest {

    static class FieldBackedObject extends JsonObject {
        public String name;
        public int age;
    }

    static class Person {
        public String name;
        public int age;
    }

    static class ExtraPojo {
        public String code = "ok";
        public int count = 2;
    }

    @Test
    void testJsonObjectSurface() {
        JsonObject object = JsonObject.of(
                "string", "12",
                "number", 34,
                "long", 56L,
                "doubleString", "7.5",
                "double", 8.25d,
                "bool", true,
                "boolString", "false",
                "obj", JsonObject.of("k", "v"),
                "arr", JsonArray.of("x", "y"),
                "nullValue", null,
                "short", (short) 9,
                "byte", (byte) 4,
                "bigInt", new BigInteger("123"),
                "bigDec", new BigDecimal("4.5")
        );

        assertEquals("12", object.getString("string"));
        assertEquals("fallback", object.getString("missing", "fallback"));
        assertEquals("34", object.getAsString("number"));
        assertEquals(34, object.getNumber("number").intValue());
        assertEquals(7, object.getNumber("missing", 7).intValue());
        assertEquals(12, object.getAsNumber("string").intValue());
        assertEquals(56L, object.getLong("long"));
        assertEquals(7L, object.getLong("missing", 7L));
        assertEquals(12L, object.getAsLong("string"));
        assertEquals(34, object.getInt("number"));
        assertEquals(7, object.getInt("missing", 7));
        assertEquals(12, object.getAsInt("string"));
        assertEquals((short) 9, object.getShort("short"));
        assertEquals((short) 7, object.getShort("missing", (short) 7));
        assertEquals((short) 12, object.getAsShort("string"));
        assertEquals((byte) 4, object.getByte("byte"));
        assertEquals((byte) 7, object.getByte("missing", (byte) 7));
        assertEquals((byte) 12, object.getAsByte("string"));
        assertEquals(8.25d, object.getDouble("double"));
        assertEquals(7.0d, object.getDouble("missing", 7.0d));
        assertEquals(7.5d, object.getAsDouble("doubleString"));
        assertEquals(8.25f, object.getFloat("double"));
        assertEquals(7.0f, object.getFloat("missing", 7.0f));
        assertEquals(7.5f, object.getAsFloat("doubleString"));
        assertEquals(new BigInteger("123"), object.getBigInteger("bigInt"));
        assertEquals(BigInteger.TEN, object.getBigInteger("missing", BigInteger.TEN));
        assertEquals(new BigInteger("12"), object.getAsBigInteger("string"));
        assertEquals(new BigDecimal("4.5"), object.getBigDecimal("bigDec"));
        assertEquals(BigDecimal.TEN, object.getBigDecimal("missing", BigDecimal.TEN));
        assertEquals(new BigDecimal("7.5"), object.getAsBigDecimal("doubleString"));
        assertTrue(object.getBoolean("bool"));
        assertTrue(object.getBoolean("missing", true));
        assertFalse(object.getAsBoolean("boolString"));
        assertEquals("v", object.getJsonObject("obj").getString("k"));
        assertEquals("v", object.getMap("obj").get("k"));
        assertEquals("v", object.getMap("obj", String.class).get("k"));
        assertEquals(JsonArray.of("x", "y"), object.getJsonArray("arr"));
        assertEquals(Arrays.asList("x", "y"), object.getList("arr"));
        assertEquals(Arrays.asList("x", "y"), object.getList("arr", String.class));
        assertArrayEquals(new Object[]{"x", "y"}, object.getArray("arr"));
        assertArrayEquals(new String[]{"x", "y"}, object.getArray("arr", String.class));
        assertEquals(34, object.get("number", Integer.class));
        assertEquals(12, object.getAs("string", Integer.class));
        assertEquals(34, object.<Integer>get("number"));
        assertEquals(12, object.<Integer>getAs("string"));
        assertThrows(JsonException.class, () -> object.get("number", 1));
        assertThrows(JsonException.class, () -> object.getAs("string", 1));
        assertEquals(object.toMap(), object.toNode(Map.class));
        JsonObject nested = JsonObject.of("k", "v");
        Map<?, ?> boundMap = JsonObject.of("nested", nested).bindNode(Map.class);
        assertSame(nested, boundMap.get("nested"));
        Map<?, ?> copiedMap = JsonObject.of("nested", nested).toNode(Map.class);
        assertNotSame(nested, copiedMap.get("nested"));
        assertInstanceOf(Map.class, object.toRaw());

        JsonObject dynamic = new JsonObject();
        dynamic.put("present", "value");
        assertEquals("computed", dynamic.computeIfAbsent("computed", key -> key));
        assertEquals("computed", dynamic.computeIfAbsent("computed", key -> "ignored"));
        dynamic.put("code", "ok");
        dynamic.putAll(Collections.singletonMap("mapKey", (Object) 1));
        dynamic.putAll(JsonObject.of("jsonKey", 2));

        assertEquals("ok", dynamic.getString("code"));
        assertTrue(dynamic.removeIf(entry -> "mapKey".equals(entry.getKey())));
        assertEquals(2, dynamic.remove("jsonKey"));
        assertFalse(dynamic.containsKey("jsonKey"));

        Properties properties = dynamic.toProperties();
        assertEquals("ok", JsonObject.fromProperties(properties).getString("code"));

        FieldBackedObject fieldBacked = new FieldBackedObject();
        fieldBacked.name = "han";
        fieldBacked.age = 18;
        fieldBacked.put("city", "SG");
        assertTrue(fieldBacked.containsKey("name"));
        assertTrue(fieldBacked.hasNonNull("name"));
        assertEquals(3, fieldBacked.size());
        assertTrue(fieldBacked.keySet().contains("name"));
        assertTrue(fieldBacked.entrySet().stream().anyMatch(entry -> "city".equals(entry.getKey())));
        assertTrue(fieldBacked.anyMatch((key, value) -> "city".equals(key)));
        assertTrue(fieldBacked.replaceAll((key, value) -> "name".equals(key) ? "HAN" : value));
        assertEquals("HAN", fieldBacked.name);
        assertThrows(JsonException.class, () -> fieldBacked.remove("name"));
        fieldBacked.clear();
        assertEquals(2, fieldBacked.size());
        fieldBacked.put("city", "SG");
        JsonObject wrapped = new JsonObject();
        wrapped.putAll(fieldBacked);
        assertEquals("HAN", wrapped.getString("name"));
        fieldBacked.prune();
        assertFalse(fieldBacked.containsKey("city"));

        FieldBackedObject same = new FieldBackedObject();
        same.name = "HAN";
        same.age = 18;
        assertEquals(fieldBacked, same);
        assertEquals(fieldBacked.hashCode(), same.hashCode());

        JsonObject built = JsonObject.builder()
                .put("a", 1)
                .put("nested", new JsonObject())
                .putByPath("$.nested.value", 3)
                .putIfParentPresentByPath("$.nested.value", 33)
                .putIfParentPresentByPath("$.nested.missing", 44)
                .ensurePutIfAbsentByPath("$.nested.value", 4)
                .ensurePutByPath("$.nested.other", 5)
                .build();
        assertEquals(33, built.getIntByPath("$.nested.value"));
        assertEquals(44, built.getIntByPath("$.nested.missing"));
        assertEquals(5, built.getIntByPath("$.nested.other"));
        assertEquals(1, built.stream().count());

        Person pojo = JsonObject.of("name", "Alice", "age", 30).bindNode(Person.class);
        assertEquals("Alice", pojo.name);
        JsonObject pojoNode = JsonObject.fromNode(new ExtraPojo());
        assertEquals("ok", pojoNode.getString("code"));
        assertTrue(object.copy().nodeEquals(object));

        JsonObject deepCopy = JsonObject.of("nested", JsonObject.of("k", "v")).deepCopy();
        deepCopy.getJsonObject("nested").put("k", "changed");
        assertEquals("changed", deepCopy.getJsonObject("nested").getString("k"));
    }
}
