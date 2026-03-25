package org.sjf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.TypeReference;
import org.sjf4j.patch.JsonPatch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageApiTest {

    static class TypedIntegerArray extends JsonArray {
        TypedIntegerArray() {
        }

        TypedIntegerArray(Object node) {
            super(node);
        }

        @Override
        public Class<?> elementType() {
            return Integer.class;
        }
    }

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

    @BeforeEach
    void setUp() {
        Sjf4jConfig.useSimpleJsonAsGlobal();
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
        assertEquals(34, array.getInteger(1));
        assertEquals(7, array.getInteger(99, 7));
        assertEquals(12, array.getAsInteger(0));
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
        assertThrows(JsonException.class, () -> array.get(0, "boom"));
        assertThrows(JsonException.class, () -> array.getAs(0, "boom"));

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
        assertEquals(1, array.stream().count());

        JsonArray deepCopy = JsonArray.of(JsonObject.of("k", "v")).deepCopy();
        array.getJsonObject(6).put("k", "changed");
        assertEquals("v", deepCopy.getJsonObject(0).getString("k"));

        assertThrows(JsonException.class, () -> new TypedIntegerArray(Arrays.asList(1, "x")));
        TypedIntegerArray integers = new TypedIntegerArray();
        integers.add(1);
        assertThrows(JsonException.class, () -> integers.add("x"));
        mutated.clear();
        assertTrue(mutated.isEmpty());
        assertNotEquals(array, mutated);
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
        assertEquals(34, object.getInteger("number"));
        assertEquals(7, object.getInteger("missing", 7));
        assertEquals(12, object.getAsInteger("string"));
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
        assertInstanceOf(Map.class, object.toRaw());

        JsonObject dynamic = new JsonObject();
        dynamic.put("present", "value");
        assertEquals("value", dynamic.putNonNull("present", "next"));
        assertEquals("next", dynamic.getString("present"));
        assertEquals("next", dynamic.putIfAbsent("present", "skip"));
        assertNull(dynamic.putIfAbsent("newKey", "created"));
        assertEquals("created", dynamic.getString("newKey"));
        assertEquals("created", dynamic.replace("newKey", "replaced"));
        assertNull(dynamic.replace("missing", "nope"));
        assertEquals("computed", dynamic.computeIfAbsent("computed", key -> key));
        assertEquals("computed", dynamic.computeIfAbsent("computed", key -> "ignored"));
        dynamic.putAll(Collections.singletonMap("mapKey", (Object) 1));
        dynamic.putAll(JsonObject.of("jsonKey", 2));
        dynamic.putAll(new ExtraPojo());
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
        assertTrue(fieldBacked.transform((key, value) -> "name".equals(key) ? "HAN" : value));
        assertEquals("HAN", fieldBacked.name);
        assertThrows(JsonException.class, () -> fieldBacked.remove("name"));
        fieldBacked.clear();
        assertEquals(2, fieldBacked.size());
        fieldBacked.put("city", "SG");
        JsonObject wrapped = new JsonObject(fieldBacked);
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
                .putNonNull("b", null)
                .putIfAbsent("a", 2)
                .put("nested", new JsonObject())
                .putByPath("$.nested.value", 3)
                .ensurePutIfAbsentByPath("$.nested.value", 4)
                .ensurePutByPath("$.nested.other", 5)
                .build();
        assertEquals(3, built.getIntegerByPath("$.nested.value"));
        assertEquals(5, built.getIntegerByPath("$.nested.other"));
        assertEquals(1, built.stream().count());

        Person pojo = JsonObject.of("name", "Alice", "age", 30).toPojo(Person.class);
        assertEquals("Alice", pojo.name);
        JsonObject pojoNode = JsonObject.fromNode(new ExtraPojo());
        assertEquals("ok", pojoNode.getString("code"));
        assertTrue(object.copy().nodeEquals(object));

        JsonObject deepCopy = JsonObject.of("nested", JsonObject.of("k", "v")).deepCopy();
        deepCopy.getJsonObject("nested").put("k", "changed");
        assertEquals("changed", deepCopy.getJsonObject("nested").getString("k"));
    }

    @Test
    void testJsonContainerPathSurface() {
        JsonObject root = JsonObject.of(
                "string", "12",
                "num", 34,
                "doubleString", "7.5",
                "double", 8.25d,
                "bool", true,
                "boolString", "false",
                "obj", JsonObject.of("k", "v"),
                "strings", JsonArray.of("x", "y"),
                "numbers", JsonArray.of(1, 2, 3),
                "nullable", null,
                "short", (short) 9,
                "byte", (byte) 4,
                "bigInt", new BigInteger("123"),
                "bigDec", new BigDecimal("4.5"),
                "items", JsonArray.of(
                        JsonObject.of("id", 1, "idText", "1"),
                        JsonObject.of("id", 2, "idText", "2")),
                "drop", JsonObject.of("keep", 1, "gone", null)
        );

        assertTrue(root.containsByPath("$.nullable"));
        assertFalse(root.hasNonNullByPath("$.nullable"));
        assertEquals(34, root.getNodeByPath("$.num"));
        assertEquals("fallback", root.getNodeByPath("$.missing", "fallback"));
        assertEquals("12", root.getStringByPath("$.string"));
        assertEquals("fallback", root.getStringByPath("$.missing", "fallback"));
        assertEquals("34", root.getAsStringByPath("$.num"));
        assertEquals(34, root.getNumberByPath("$.num").intValue());
        assertEquals(7, root.getNumberByPath("$.missing", 7).intValue());
        assertEquals(12, root.getAsNumberByPath("$.string").intValue());
        assertEquals(34L, root.getLongByPath("$.num"));
        assertEquals(7L, root.getLongByPath("$.missing", 7L));
        assertEquals(12L, root.getAsLongByPath("$.string"));
        assertEquals(34, root.getIntegerByPath("$.num"));
        assertEquals(7, root.getIntegerByPath("$.missing", 7));
        assertEquals(12, root.getAsIntegerByPath("$.string"));
        assertEquals((short) 9, root.getShortByPath("$.short"));
        assertEquals((short) 7, root.getShortByPath("$.missing", (short) 7));
        assertEquals((short) 12, root.getAsShortByPath("$.string"));
        assertEquals((byte) 4, root.getByteByPath("$.byte"));
        assertEquals((byte) 7, root.getByteByPath("$.missing", (byte) 7));
        assertEquals((byte) 12, root.getAsByteByPath("$.string"));
        assertEquals(8.25d, root.getDoubleByPath("$.double"));
        assertEquals(7.0d, root.getDoubleByPath("$.missing", 7.0d));
        assertEquals(7.5d, root.getAsDoubleByPath("$.doubleString"));
        assertEquals(8.25f, root.getFloatByPath("$.double"));
        assertEquals(7.0f, root.getFloatByPath("$.missing", 7.0f));
        assertEquals(7.5f, root.getAsFloatByPath("$.doubleString"));
        assertEquals(new BigInteger("123"), root.getBigIntegerByPath("$.bigInt"));
        assertEquals(BigInteger.TEN, root.getBigIntegerByPath("$.missing", BigInteger.TEN));
        assertEquals(new BigInteger("12"), root.getAsBigIntegerByPath("$.string"));
        assertEquals(new BigDecimal("4.5"), root.getBigDecimalByPath("$.bigDec"));
        assertEquals(BigDecimal.TEN, root.getBigDecimalByPath("$.missing", BigDecimal.TEN));
        assertEquals(new BigDecimal("7.5"), root.getAsBigDecimalByPath("$.doubleString"));
        assertTrue(root.getBooleanByPath("$.bool"));
        assertTrue(root.getBooleanByPath("$.missing", true));
        assertFalse(root.getAsBooleanByPath("$.boolString"));
        assertEquals("v", root.getMapByPath("$.obj").get("k"));
        assertEquals("v", root.asMapByPath("$.obj", String.class).get("k"));
        assertEquals("v", root.getJsonObjectByPath("$.obj").getString("k"));
        assertEquals(JsonArray.of("x", "y"), root.getJsonArrayByPath("$.strings"));
        assertEquals(Arrays.asList("x", "y"), root.getListByPath("$.strings"));
        assertEquals(Arrays.asList("x", "y"), root.getListByPath("$.strings", String.class));
        assertArrayEquals(new Object[]{"x", "y"}, root.getArrayByPath("$.strings"));
        assertArrayEquals(new Integer[]{1, 2, 3}, root.getArrayByPath("$.numbers", Integer.class));
        assertEquals(Collections.singleton(1), JsonObject.of("set", JsonArray.of(1)).getSetByPath("$.set", Integer.class));
        assertEquals(34, root.getByPath("$.num", Integer.class));
        assertEquals(34, root.<Integer>getByPath("$.num"));
        assertEquals(12, root.getAsByPath("$.string", Integer.class));
        assertEquals(12, root.<Integer>getAsByPath("$.string"));
        assertThrows(JsonException.class, () -> root.getByPath("$.num", 1));
        assertThrows(JsonException.class, () -> root.getAsByPath("$.string", 1));

        root.putByPath("$.obj.added", 1);
        root.ensurePutByPath("$.created.path", "x");
        root.ensurePutIfAbsentByPath("$.created.path", "y");
        root.addByPath("$.strings", "z");
        root.replaceByPath("$.obj.k", "vv");
        root.removeByPath("$.obj.added");
        assertEquals("vv", root.getStringByPath("$.obj.k"));
        assertEquals("x", root.getStringByPath("$.created.path"));
        assertEquals(Arrays.asList(1, 2), root.findByPath("$.items[*].id", Integer.class));
        assertEquals(Arrays.asList(1, 2), root.findAsByPath("$.items[*].idText", Integer.class));
        assertEquals(2, root.evalByPath("$.items.length()", Number.class).intValue());
        assertEquals(12, root.evalAsByPath("$.string", Integer.class));

        AtomicInteger visits = new AtomicInteger();
        root.walk((path, node) -> {
            visits.incrementAndGet();
            return true;
        });
        root.walk(org.sjf4j.node.Nodes.WalkTarget.VALUE, (path, node) -> true);
        root.walk(org.sjf4j.node.Nodes.WalkTarget.CONTAINER, org.sjf4j.node.Nodes.WalkOrder.TOP_DOWN, (path, node) -> true);
        root.walk(org.sjf4j.node.Nodes.WalkTarget.VALUE, org.sjf4j.node.Nodes.WalkOrder.BOTTOM_UP, 3, (path, node) -> true);
        assertTrue(visits.get() > 0);

        root.apply(JsonPatch.fromJson("[{\"op\":\"add\",\"path\":\"/patched\",\"value\":1}]"));
        assertEquals(1, root.getInteger("patched"));
        root.merge(JsonObject.of("merged", JsonObject.of("value", 1)), true, false);
        root.merge(JsonObject.of("merged2", 2));
        root.mergeWithCopy(JsonObject.of("copied", JsonObject.of("x", 1)));
        root.mergeRfc7386(JsonObject.of("nullable", "set"));
        assertEquals(1, root.getIntegerByPath("$.merged.value"));
        assertEquals(2, root.getInteger("merged2"));
        assertEquals("set", root.getString("nullable"));
        root.deepPruneNulls();
        assertFalse(root.getJsonObject("drop").containsKey("gone"));
    }

    @Test
    void testSjf4jApiSurface() {
        String json = "{\"name\":\"Alice\",\"age\":30,\"tags\":[\"x\",\"y\"]}";
        JsonObject fromString = Sjf4j.fromJson(json, JsonObject.class);
        JsonObject fromReader = Sjf4j.fromJson(new StringReader(json), JsonObject.class);
        JsonObject fromStream = Sjf4j.fromJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), JsonObject.class);
        JsonObject fromBytes = Sjf4j.fromJson(json.getBytes(StandardCharsets.UTF_8), JsonObject.class);
        assertEquals(fromString, fromReader);
        assertEquals(fromString, fromStream);
        assertEquals(fromString, fromBytes);
        assertTrue(JsonType.of(Sjf4j.fromJson(json)).isObject());

        List<Integer> intsFromReader = Sjf4j.fromJson(new StringReader("[1,2,3]"), new TypeReference<List<Integer>>() {});
        List<Integer> intsFromString = Sjf4j.fromJson("[1,2,3]", new TypeReference<List<Integer>>() {});
        List<Integer> intsFromBytes = Sjf4j.fromJson("[1,2,3]".getBytes(StandardCharsets.UTF_8), new TypeReference<List<Integer>>() {});
        List<Integer> intsFromStream = Sjf4j.fromJson(new ByteArrayInputStream("[1,2,3]".getBytes(StandardCharsets.UTF_8)), new TypeReference<List<Integer>>() {});
        assertEquals(Arrays.asList(1, 2, 3), intsFromReader);
        assertEquals(intsFromReader, intsFromString);
        assertEquals(intsFromReader, intsFromBytes);
        assertEquals(intsFromReader, intsFromStream);

        StringWriter jsonWriter = new StringWriter();
        Sjf4j.toJson(jsonWriter, fromString);
        ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
        Sjf4j.toJson(jsonOutput, fromString);
        assertEquals(json, jsonWriter.toString());
        assertEquals(json, new String(jsonOutput.toByteArray(), StandardCharsets.UTF_8));
        assertEquals(json, Sjf4j.toJsonString(fromString));
        assertEquals(json, new String(Sjf4j.toJsonBytes(fromString), StandardCharsets.UTF_8));

        String yaml = "name: Alice\nage: 30\n";
        JsonObject yamlObject = Sjf4j.fromYaml(yaml, JsonObject.class);
        assertEquals("Alice", yamlObject.getString("name"));
        assertEquals(yamlObject, Sjf4j.fromYaml(new StringReader(yaml), JsonObject.class));
        assertTrue(JsonType.of(Sjf4j.fromYaml(yaml)).isObject());
        Map<String, Object> yamlMap = Sjf4j.fromYaml(yaml, new TypeReference<Map<String, Object>>() {});
        assertEquals("Alice", yamlMap.get("name"));
        assertEquals(yamlMap, Sjf4j.fromYaml(new StringReader(yaml), new TypeReference<Map<String, Object>>() {}));
        StringWriter yamlWriter = new StringWriter();
        Sjf4j.toYaml(yamlWriter, yamlObject);
        assertTrue(yamlWriter.toString().contains("name: Alice"));
        assertTrue(Sjf4j.toYamlString(yamlObject).contains("age: 30"));
        assertTrue(new String(Sjf4j.toYamlBytes(yamlObject), StandardCharsets.UTF_8).contains("name: Alice"));

        List<Integer> fromNode = Sjf4j.fromNode(JsonArray.of(1, 2, 3), new TypeReference<List<Integer>>() {});
        assertEquals(Arrays.asList(1, 2, 3), fromNode);
        Person person = Sjf4j.fromNode(JsonObject.of("name", "Alice", "age", 30), Person.class);
        assertEquals("Alice", person.name);
        assertEquals(30, person.age);
        JsonObject deepSource = JsonObject.of("nested", JsonObject.of("value", 1));
        JsonObject deepCopy = Sjf4j.deepNode(deepSource);
        deepSource.getJsonObject("nested").put("value", 2);
        assertEquals(1, deepCopy.getIntegerByPath("$.nested.value"));
        assertInstanceOf(Map.class, Sjf4j.toRaw(deepSource));

        Properties properties = Sjf4j.toProperties(JsonObject.of("app", JsonObject.of("name", "sjf4j")));
        assertEquals("sjf4j", properties.getProperty("app.name"));
        assertEquals("sjf4j", ((JsonObject) Sjf4j.fromProperties(properties)).getStringByPath("$.app.name"));
        assertEquals("sjf4j", Sjf4j.fromProperties(properties, JsonObject.class).getStringByPath("$.app.name"));
        assertEquals("sjf4j", Sjf4j.fromProperties(properties, new TypeReference<Map<String, Object>>() {}).get("app") instanceof Map
                ? ((Map<?, ?>) Sjf4j.fromProperties(properties, new TypeReference<Map<String, Object>>() {}).get("app")).get("name")
                : null);

        assertThrows(NullPointerException.class, () -> Sjf4j.fromJson((String) null, JsonObject.class));
        assertThrows(NullPointerException.class, () -> Sjf4j.fromJson(json, (TypeReference<JsonObject>) null));
        assertThrows(NullPointerException.class, () -> Sjf4j.fromYaml((String) null, JsonObject.class));
        assertThrows(NullPointerException.class, () -> Sjf4j.fromNode(JsonObject.of(), (TypeReference<List<Integer>>) null));
        assertThrows(NullPointerException.class, () -> Sjf4j.fromProperties(null));
    }
}
