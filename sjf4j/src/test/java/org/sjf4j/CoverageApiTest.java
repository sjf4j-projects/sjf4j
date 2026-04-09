package org.sjf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.simple.SimpleJsonFacade;
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

    private Sjf4j sjf4j;

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
        sjf4j = Sjf4j.builder().jsonFacade(new SimpleJsonFacade()).build();
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
        assertEquals(3, built.getIntByPath("$.nested.value"));
        assertEquals(5, built.getIntByPath("$.nested.other"));
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
        assertEquals(34, root.getIntByPath("$.num"));
        assertEquals(7, root.getIntByPath("$.missing", 7));
        assertEquals(12, root.getAsIntByPath("$.string"));
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
        assertEquals(2, root.computeByPath("$.items[*].id", (parent, current) ->
                Integer.parseInt(((JsonObject) parent).getString("idText")) * 10));
        root.addByPath("$.strings", "z");
        root.replaceByPath("$.obj.k", "vv");
        root.removeByPath("$.obj.added");
        assertEquals("vv", root.getStringByPath("$.obj.k"));
        assertEquals("x", root.getStringByPath("$.created.path"));
        assertEquals(Arrays.asList(10, 20), root.findByPath("$.items[*].id", Integer.class));
        assertEquals(Arrays.asList("1", "2"), root.findByPath("$.items[*].idText", String.class));
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

        root.apply(sjf4j.fromJson("[{\"op\":\"add\",\"path\":\"/patched\",\"value\":1}]", JsonPatch.class));
        assertEquals(1, root.getInt("patched"));
        root.merge(JsonObject.of("merged", JsonObject.of("value", 1)), true, false);
        root.merge(JsonObject.of("merged2", 2));
        root.mergeWithCopy(JsonObject.of("copied", JsonObject.of("x", 1)));
        root.mergeRfc7386(JsonObject.of("nullable", "set"));
        assertEquals(1, root.getIntByPath("$.merged.value"));
        assertEquals(2, root.getInt("merged2"));
        assertEquals("set", root.getString("nullable"));
        root.deepPruneNulls();
        assertFalse(root.getJsonObject("drop").containsKey("gone"));
    }

    @Test
    void testSjf4jApiSurface() {
        String json = "{\"name\":\"Alice\",\"age\":30,\"tags\":[\"x\",\"y\"]}";
        JsonObject fromString = sjf4j.fromJson(json, JsonObject.class);
        JsonObject fromReader = sjf4j.fromJson(new StringReader(json), JsonObject.class);
        JsonObject fromStream = sjf4j.fromJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), JsonObject.class);
        JsonObject fromBytes = sjf4j.fromJson(json.getBytes(StandardCharsets.UTF_8), JsonObject.class);
        assertEquals(fromString, fromReader);
        assertEquals(fromString, fromStream);
        assertEquals(fromString, fromBytes);
        assertTrue(JsonType.of(sjf4j.fromJson(json)).isObject());

        List<Integer> intsFromReader = sjf4j.fromJson(new StringReader("[1,2,3]"), new TypeReference<List<Integer>>() {});
        List<Integer> intsFromString = sjf4j.fromJson("[1,2,3]", new TypeReference<List<Integer>>() {});
        List<Integer> intsFromBytes = sjf4j.fromJson("[1,2,3]".getBytes(StandardCharsets.UTF_8), new TypeReference<List<Integer>>() {});
        List<Integer> intsFromStream = sjf4j.fromJson(new ByteArrayInputStream("[1,2,3]".getBytes(StandardCharsets.UTF_8)), new TypeReference<List<Integer>>() {});
        assertEquals(Arrays.asList(1, 2, 3), intsFromReader);
        assertEquals(intsFromReader, intsFromString);
        assertEquals(intsFromReader, intsFromBytes);
        assertEquals(intsFromReader, intsFromStream);

        StringWriter jsonWriter = new StringWriter();
        sjf4j.toJson(jsonWriter, fromString);
        ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
        sjf4j.toJson(jsonOutput, fromString);
        assertEquals(json, jsonWriter.toString());
        assertEquals(json, new String(jsonOutput.toByteArray(), StandardCharsets.UTF_8));
        assertEquals(json, sjf4j.toJsonString(fromString));
        assertEquals(json, new String(sjf4j.toJsonBytes(fromString), StandardCharsets.UTF_8));

        String yaml = "name: Alice\nage: 30\n";
        JsonObject yamlObject = sjf4j.fromYaml(yaml, JsonObject.class);
        assertEquals("Alice", yamlObject.getString("name"));
        assertEquals(yamlObject, sjf4j.fromYaml(new StringReader(yaml), JsonObject.class));
        assertTrue(JsonType.of(sjf4j.fromYaml(yaml)).isObject());
        Map<String, Object> yamlMap = sjf4j.fromYaml(yaml, new TypeReference<Map<String, Object>>() {});
        assertEquals("Alice", yamlMap.get("name"));
        assertEquals(yamlMap, sjf4j.fromYaml(new StringReader(yaml), new TypeReference<Map<String, Object>>() {}));
        StringWriter yamlWriter = new StringWriter();
        sjf4j.toYaml(yamlWriter, yamlObject);
        assertTrue(yamlWriter.toString().contains("name: Alice"));
        assertTrue(sjf4j.toYamlString(yamlObject).contains("age: 30"));
        assertTrue(new String(sjf4j.toYamlBytes(yamlObject), StandardCharsets.UTF_8).contains("name: Alice"));

        List<Integer> fromNode = sjf4j.fromNode(JsonArray.of(1, 2, 3), new TypeReference<List<Integer>>() {});
        assertEquals(Arrays.asList(1, 2, 3), fromNode);
        Person person = sjf4j.fromNode(JsonObject.of("name", "Alice", "age", 30), Person.class);
        assertEquals("Alice", person.name);
        assertEquals(30, person.age);
        JsonObject deepSource = JsonObject.of("nested", JsonObject.of("value", 1));
        JsonObject deepCopy = sjf4j.deepNode(deepSource);
        deepSource.getJsonObject("nested").put("value", 2);
        assertEquals(1, deepCopy.getIntByPath("$.nested.value"));
        assertInstanceOf(Map.class, sjf4j.toRaw(deepSource));

        Properties properties = sjf4j.toProperties(JsonObject.of("app", JsonObject.of("name", "sjf4j")));
        assertEquals("sjf4j", properties.getProperty("app.name"));
        assertEquals("sjf4j", ((JsonObject) sjf4j.fromProperties(properties)).getStringByPath("$.app.name"));
        assertEquals("sjf4j", sjf4j.fromProperties(properties, JsonObject.class).getStringByPath("$.app.name"));
        assertEquals("sjf4j", sjf4j.fromProperties(properties, new TypeReference<Map<String, Object>>() {}).get("app") instanceof Map
                ? ((Map<?, ?>) sjf4j.fromProperties(properties, new TypeReference<Map<String, Object>>() {}).get("app")).get("name")
                : null);

        assertThrows(NullPointerException.class, () -> sjf4j.fromJson((String) null, JsonObject.class));
        assertThrows(NullPointerException.class, () -> sjf4j.fromJson(json, (TypeReference<JsonObject>) null));
        assertThrows(NullPointerException.class, () -> sjf4j.fromYaml((String) null, JsonObject.class));
        assertThrows(NullPointerException.class, () -> sjf4j.fromNode(JsonObject.of(), (TypeReference<List<Integer>>) null));
        assertThrows(NullPointerException.class, () -> sjf4j.fromProperties(null));
    }
}
