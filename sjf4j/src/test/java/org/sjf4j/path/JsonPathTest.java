package org.sjf4j.path;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.node.Nodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class JsonPathTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testParse1() {
        String s1 = "$.a.b[0].c";
        JsonPath path1 = JsonPath.parse(s1);
        log.info("path1: {}", path1);
        assertEquals("$.a.b[0].c", path1.toString());

        String s2 = "$.a[*].b['c'].d";
        JsonPath path2 = JsonPath.parse(s2);
        log.info("path2: {}", path2);
        assertEquals("$.a[*].b.c.d", path2.toExpr());

        String s3 = "$['x.y'].a['[['][-1].*[*]";
        JsonPath path3 = JsonPath.parse(s3);
        log.info("path3: {}", path3);
        assertEquals("$['x.y'].a['[['][-1].*[*]", path3.toString());

        String s4 = "$.a[''].b['\\''].c";
        JsonPath path4 = JsonPath.parse(s4);
        log.info("path4: {}", path4);
        assertEquals("$.a[''].b['\\''].c", path4.toString());
    }

    @Test
    public void testParse2() {
        String s1 = "/a/b/0/c";
        JsonPath path1 = JsonPath.parse(s1);
        log.info("path1: {}", path1);
        assertEquals("$.a.b[0].c", path1.toExpr());
        assertEquals("/a/b/0/c", path1.toPointerExpr());

        String s2 = "/a~0/0/b~1'/c~0/d e";
        JsonPath path2 = JsonPath.parse(s2);
        log.info("path2: {}", path2);
        assertEquals("$['a~'][0]['b/\\'']['c~']['d e']", path2.toExpr());

        assertThrows(JsonException.class, () -> JsonPath.parse("/a~2b"));
    }

    @Test
    public void testJsonPointerParent() {
        JsonPointer p = JsonPointer.parse("/a/b/0/c");
        assertEquals("/a/b/0", p.parent().toString());
        assertEquals("/a/b", p.parent().parent().toString());
        assertEquals("/a", p.parent().parent().parent().toString());
        assertEquals("", p.parent().parent().parent().parent().toString());
        assertNull(p.parent().parent().parent().parent().parent());

        JsonPointer root = JsonPointer.parse("");
        JsonPointer child = root.childIndex(2);
        assertEquals("/2", child.toString());
        assertTrue(JsonPointer.parse("/a/-").isAppend());
        assertFalse(JsonPointer.parse("/a/0").isAppend());
        assertEquals(JsonPointer.parse("/a/1"), JsonPointer.parse("/a/1"));
        assertEquals(JsonPointer.parse("/a/1").hashCode(), JsonPointer.parse("/a/1").hashCode());
        assertFalse(JsonPointer.parse("/a/1").equals(JsonPointer.parse("/a/2")));
        assertEquals("/01", JsonPointer.parse("/01").toString());
        assertNotEquals(JsonPointer.parse("/01"), JsonPointer.parse("/1"));
    }

    @Test
    public void testJsonPointerNumericObjectKeys() {
        JsonObject jo = JsonObject.fromJson("{\"0\":{\"01\":\"value\"},\"arr\":[\"a\",\"b\"]}");

        assertEquals("value", JsonPath.parse("/0/01").getString(jo));
        assertEquals("b", JsonPath.parse("/arr/1").getString(jo));

        JsonObject target = new JsonObject();
        JsonPath.parse("/0").put(target, "first");
        assertEquals("first", target.getString("0"));
        assertEquals("first", JsonPath.parse("/0").replace(target, "second"));
        assertEquals("second", target.getString("0"));
        assertEquals("second", JsonPath.parse("/0").removeIfPresent(target));
        assertFalse(target.containsKey("0"));
    }

    @Test
    public void testParse3() {
        String s1 = "$..a['b'].c";
        JsonPath p1 = JsonPath.parse(s1);
        log.info("s1={}, p1={}", s1, p1);
    }

    @Test
    public void testParse4() {
        String s1 = "a.b[0].c";
        JsonPath path1 = JsonPath.parse(s1);
        log.info("path1: {}", path1);
        assertEquals("a.b[0].c", path1.toString());
        assertEquals("$.a.b[0].c", path1.toExpr());
    }

    @Test
    public void testParseSimpleBracketFastPath() {
        JsonPath wildcard = JsonPath.parse("$.a[*].b");
        JsonPath index = JsonPath.parse("$.a[123].b");
        JsonPath append = JsonPath.parse("$.a[+]");

        assertEquals("$.a[*].b", wildcard.toExpr());
        assertEquals("$.a[123].b", index.toExpr());
        assertEquals("$.a[+]", append.toExpr());
        assertEquals("$.a[*].b", JsonPath.parse("$.a[ * ].b").toExpr());
        assertEquals("$.a[123].b", JsonPath.parse("$.a[ 123 ].b").toExpr());
        assertEquals("$.a[+]", JsonPath.parse("$.a[ + ]").toExpr());

        // Other bracket forms should still use the normal parser path.
        assertEquals("$.a[-123].b", JsonPath.parse("$.a[-123].b").toExpr());
        assertEquals("$.a.name.b", JsonPath.parse("$.a['name'].b").toExpr());
        assertEquals("$.a['na\\'me'].b", JsonPath.parse("$.a['na\\'me'].b").toString());
        assertEquals("$.a[''].b", JsonPath.parse("$.a[''].b").toString());
    }

    @Test
    public void testGetNode1() {
        String json1 = "{\n" +
                "  \"book\": [\n" +
                "    { \"title\": \"A\", \"price\": 10, \"tags\": [\"classic\"] },\n" +
                "    { \"title\": \"B\", \"price\": null, \"tags\": [] },\n" +
                "    { \"title\": \"C\", \"isbn.number\": \"123\", \"tags\": null }\n" +
                "  ],\n" +
                "  \"emptyArray\": [],\n" +
                "  \"emptyObject\": {},\n" +
                "  \"nullValue\": null,\n" +
                "  \"weird.keys\": { \"key with spaces\": \"v1\" }\n" +
                "}";
        JsonObject jo1 = JsonObject.fromJson(json1);

        assertEquals("B", JsonPath.parse("$.book[1]['title']").getNode(jo1));
        assertEquals("B", JsonPath.parse("/book/1/title").getNode(jo1));
        assertEquals(10, JsonPath.parse("$.book[0].price").getNode(jo1));
        assertEquals("classic", JsonPath.parse("$.book[0].tags[0]").getNode(jo1));
        assertEquals(Collections.emptyList(), JsonPath.parse("$.emptyArray").getNode(jo1));
        assertEquals(Collections.emptyMap(), JsonPath.parse("$['emptyObject']").getNode(jo1));
        assertNull(JsonPath.parse("$.nullValue").getNode(jo1));
        assertEquals("v1", JsonPath.parse("$['weird.keys']['key with spaces']").getNode(jo1));
        assertEquals("v1", JsonPath.parse("/weird.keys/key with spaces").getNode(jo1));

        log.info("$: {}", JsonPath.parse("$").getNode(jo1));
        assertEquals(JsonObject.class, JsonPath.parse("$").getNode(jo1).getClass());

        assertThrows(JsonException.class, () -> JsonPath.parse("$.book[*].price").getNode(jo1));

    }

    @Test
    public void testFind1() {
        String json1 = "{\n" +
                "  \"book\": [\n" +
                "    { \"title\": \"A\", \"price\": 10, \"tags\": [\"classic\"] },\n" +
                "    { \"title\": \"B\", \"price\": null, \"tags\": [] },\n" +
                "    { \"title\": \"C\", \"isbn.number\": \"123\", \"tags\": null }\n" +
                "  ],\n" +
                "  \"emptyArray\": [],\n" +
                "  \"emptyObject\": {},\n" +
                "  \"nullValue\": null,\n" +
                "  \"weird.keys\": { \"key with spaces\": \"v1\" }\n" +
                "}";
        JsonObject jo1 = JsonObject.fromJson(json1);

        List<Object> result1 = JsonPath.parse("$.book[*].title").find(jo1);
        log.info("result1: {}", result1);
        assertEquals(3, result1.size());
        assertEquals("B", result1.get(1));

        List<JsonArray> result2 = JsonPath.parse("$.book[*].tags").findAs(jo1, JsonArray.class);
        log.info("result2: {}", result2);
        assertEquals(3, result2.size());
        assertEquals("classic", result2.get(0).getString(0));
        assertNull(result2.get(2));

    }

    @Test
    public void testAutofillContainers1() {
        String json1 = "{\n" +
                "  \"book\": [\n" +
                "    { \"title\": \"A\", \"price\": 10, \"tags\": [\"classic\"] },\n" +
                "    { \"title\": \"B\", \"price\": null, \"tags\": [] },\n" +
                "    { \"title\": \"C\", \"isbn.number\": \"123\", \"tags\": null }\n" +
                "  ],\n" +
                "  \"emptyArray\": [],\n" +
                "  \"emptyObject\": {},\n" +
                "  \"nullValue\": null,\n" +
                "  \"weird.keys\": { \"key with spaces\": \"v1\" }\n" +
                "}";
        JsonObject jo1 = JsonObject.fromJson(json1);

        JsonPath.parse("$.book[0].box[+].gg").ensurePut(jo1, "mm");
        JsonObject container1 = JsonPath.parse("$.book[0].box[0]").getJsonObject(jo1);
        log.info("container1={} jo1={}", container1, jo1);
        assertEquals(JsonObject.class, container1.getClass());
        assertEquals(1, jo1.getJsonArrayByPath("$.book[0].box").size());

        JsonPath.parse("$.book[2].tags['gg mm'][+]").ensurePut(jo1, "mm");
        JsonArray container2 = JsonPath.parse("$.book[2].tags['gg mm']").getJsonArray(jo1);
        log.info("container2={} jo1={}", container2, jo1);
        assertEquals(JsonArray.class, container2.getClass());
        assertEquals(1, jo1.getJsonArrayByPath("$.book[2].tags['gg mm']").size());
    }

    @Test
    public void testMapList1() {
        JsonObject jo1 = JsonObject.of("names", new int[]{1, 2, 3});
        Map<String, List<JsonObject>> map = new HashMap<>();
        List<JsonObject> lis = new ArrayList<>();
        lis.add(JsonObject.of("kk", "ll"));
        map.put("lis", lis);
        jo1.put("map", map);

        log.info("jo1={}", jo1);
        assertEquals(2, JsonPath.parse("$.names[1]").getLong(jo1));
        assertEquals("ll", JsonPath.parse("$.map.lis[0].kk").getString(jo1));
        assertEquals(ArrayList.class, JsonPath.parse("$.map.lis").getNode(jo1).getClass());
    }

    @Test
    public void testEnsurePutAndRemove() {
        JsonObject jo = JsonObject.fromJson("{\"a\":{\"b\":123},\"array\":[1,2,3]}");

        JsonPath path1 = JsonPath.parse("$.a.c");
        path1.ensurePut(jo, "newValue");
        assertEquals("newValue", jo.getStringByPath("$.a.c"));

        JsonPath path2 = JsonPath.parse("$.array[1]");
        path2.ensurePut(jo, 999);
        assertEquals(999, jo.getIntByPath("$.array[1]"));

        JsonPath path3 = JsonPath.parse("$.a.d");
        path3.ensurePut(jo, "value");
        assertEquals("value", jo.getStringByPath("$.a.d"));
        assertEquals("value", jo.getStringByPath("$.a.d")); // should not be overwritten

        JsonPath path4 = JsonPath.parse("$.a.e");
        assertNull(path4.ensurePutIfAbsent(jo, "first"));
        assertEquals("first", jo.getStringByPath("$.a.e"));
        assertEquals("first", path4.ensurePutIfAbsent(jo, "second"));
        assertEquals("first", jo.getStringByPath("$.a.e")); // should not be overwritten

        JsonPath.parse("$.a.nullable").put(jo, null);
        assertNull(JsonPath.parse("$.a.nullable").ensurePutIfAbsent(jo, "filled"));
        assertEquals("filled", jo.getStringByPath("$.a.nullable"));

        assertEquals(999, JsonPath.parse("$.array[1]").ensurePutIfAbsent(jo, 111));
        assertNull(JsonPath.parse("$.array[3]").ensurePutIfAbsent(jo, 7));
        assertEquals(Arrays.asList(1, 999, 3, 7), JsonPath.parse("$.array[*]").find(jo));
        JsonPath.parse("$.array[2]").put(jo, null);
        assertNull(JsonPath.parse("$.array[2]").ensurePutIfAbsent(jo, 333));
        assertEquals(Arrays.asList(1, 999, 333, 7), JsonPath.parse("$.array[*]").find(jo));
        assertThrows(JsonException.class, () -> JsonPath.parse("$.array[5]").ensurePutIfAbsent(jo, 8));

        JsonPath path5 = JsonPath.parse("$.a.b");
        assertTrue(path5.hasNonNull(jo));
        path5.removeIfPresent(jo);
        assertFalse(path5.hasNonNull(jo));

        JsonPath path6 = JsonPath.parse("$.array[0]");
        path6.removeIfPresent(jo);
        assertEquals(3, jo.getJsonArray("array").size());
        assertEquals(999, jo.getJsonArray("array").getInt(0));
    }

    @Test
    public void testPutIfParentPresent() {
        JsonObject jo = JsonObject.of(
                "obj", JsonObject.of("present", 1),
                "arr", JsonArray.of("a", "b")
        );

        JsonPath present = JsonPath.parse("$.obj.present");
        assertEquals(1, present.putIfParentPresent(jo, 2));
        assertEquals(2, jo.getIntByPath("$.obj.present"));

        JsonPath missingKey = JsonPath.parse("$.obj.missing");
        assertNull(missingKey.putIfParentPresent(jo, 3));
        assertEquals(3, jo.getIntByPath("$.obj.missing"));

        JsonPath missingParent = JsonPath.parse("$.missing.value");
        assertNull(missingParent.putIfParentPresent(jo, 4));
        assertNull(JsonPath.parse("$.missing").getNode(jo));

        JsonPath arrayIndex = JsonPath.parse("$.arr[1]");
        assertEquals("b", arrayIndex.putIfParentPresent(jo, "bb"));
        assertEquals("bb", jo.getStringByPath("$.arr[1]"));

        JsonPath append = JsonPath.parse("$.arr[+]");
        assertNull(append.putIfParentPresent(jo, "c"));
        assertEquals(Arrays.asList("a", "bb", "c"), JsonPath.parse("$.arr[*]").find(jo));
        assertNull(JsonPath.parse("$.arr[3]").putIfParentPresent(jo, "d"));
        assertEquals(Arrays.asList("a", "bb", "c", "d"), JsonPath.parse("$.arr[*]").find(jo));

        JsonPath pointerObjectKey = JsonPath.parse("/0");
        JsonObject pointerTarget = new JsonObject();
        assertNull(pointerObjectKey.putIfParentPresent(pointerTarget, "zero"));
        assertEquals("zero", pointerTarget.getString("0"));
    }

    @Test
    public void testPutPojoReturnsNull() {
        Person person = Sjf4j.global().fromJson(JSON_DATA, Person.class);

        assertNull(JsonPath.parse("$.name").put(person, "Bob"));
        assertEquals("Bob", person.name);

        JsonObject object = JsonObject.of("name", "Alice");
        assertEquals("Alice", JsonPath.parse("$.name").put(object, "Bob"));
        assertEquals("Bob", object.getString("name"));
    }

    @Test
    public void testCompute() {
        JsonObject jo = JsonObject.fromJson("{\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}],\"groups\":[{\"members\":[{\"name\":\"A\"},{\"name\":\"B\"}]},{\"members\":[{\"name\":\"C\"}]}],\"meta\":{\"version\":1,\"nested\":{\"version\":2}}}");

        assertEquals(3, JsonPath.parse("$.babies[*].age").compute(jo, (parent, current) -> 9));
        assertEquals(Arrays.asList(9, 9, 9), JsonPath.parse("$.babies[*].age").find(jo));

        assertEquals(3, JsonPath.parse("$.babies[*].name").compute(jo, (parent, current) -> {
            return Nodes.toInt(Nodes.getInObject(parent, "age")) + ":" + current;
        }));
        assertEquals(Arrays.asList("9:Baby-0", "9:Baby-1", "9:Baby-2"), JsonPath.parse("$.babies[*].name").find(jo));

        assertEquals(3, JsonPath.parse("$.babies[?(@.age == 9)].name").compute(jo, (parent, current) -> "updated"));
        assertEquals(Arrays.asList("updated", "updated", "updated"), JsonPath.parse("$.babies[*].name").find(jo));

        assertEquals(2, JsonPath.parse("$..version").compute(jo, (parent, current) -> 7));
        assertEquals(Arrays.asList(7, 7), JsonPath.parse("$..version").find(jo));

        assertEquals(2, JsonPath.parse("$.groups[*].members[+]").compute(jo, (parent, current) -> JsonObject.of("name", "Z")));
        assertEquals(Arrays.asList("A", "B", "Z", "C", "Z"), JsonPath.parse("$.groups[*].members[*].name").find(jo));

        JsonArray babies = JsonArray.fromJson("[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]");
        JsonPath.parse("$[+]").put(babies, JsonObject.of("name", "Baby-3", "age", 4));
        assertEquals(Arrays.asList("Baby-0", "Baby-1", "Baby-2", "Baby-3"), JsonPath.parse("$[*].name").find(babies));
    }

    @Test
    public void testExceptionPaths() {
        JsonObject jo = JsonObject.fromJson("{\"a\":1}");

        assertNull(JsonPath.parse("$.nonexist").getNode(jo));
        assertEquals("default", JsonPath.parse("$.nonexist").getString(jo, "default"));

        assertThrows(JsonException.class, () -> {
            JsonPath.parse("$.a[*]").getNode(jo);
        });

        JsonPath relative = assertDoesNotThrow(() -> JsonPath.parse("no.invalid"));
        assertEquals("$.no.invalid", relative.toExpr());
        assertEquals(7, relative.getInt(JsonObject.of("no", JsonObject.of("invalid", 7))));

        JsonPath root = assertDoesNotThrow(() -> JsonPath.parse("")); // "" is valid in JSON Pointer
        assertEquals("", root.toPointerExpr());
        assertSame(jo, root.getNode(jo));
        assertTrue(root.contains(jo));
        assertThrows(JsonException.class, () -> JsonPath.parse("$.."));

        JsonArray ja = JsonArray.fromJson("[1,2,3]");
        assertNull(JsonPath.parse("$[10]").getNode(ja));

        JsonPath append = JsonPath.parse("$[+]");
        assertFalse(append.contains(ja));
        assertThrows(JsonException.class, () -> append.getNode(ja));
        assertThrows(JsonException.class, () -> append.replace(ja, 4));
        assertThrows(JsonException.class, () -> append.removeIfPresent(ja));
    }

    @Test
    public void testComplexPaths() {
        String json = "{\n" +
                "  \"store\": {\n" +
                "    \"book\": [\n" +
                "      { \"category\": \"reference\", \"author\": \"Nigel Rees\", \"title\": \"Sayings of the Century\", \"price\": 8.95 },\n" +
                "      { \"category\": \"fiction\", \"author\": \"Evelyn Waugh\", \"title\": \"Sword of Honour\", \"price\": 12.99 }\n" +
                "    ],\n" +
                "    \"bicycle\": { \"color\": \"red\", \"price\": 19.95 }\n" +
                "  }\n" +
                "}";
        JsonObject jo = JsonObject.fromJson(json);

        assertEquals("reference", JsonPath.parse("$.store.book[0].category").getNode(jo));
        assertEquals(12.99, JsonPath.parse("$.store.book[1].price").getDouble(jo));
        assertEquals("red", JsonPath.parse("$.store.bicycle.color").getNode(jo));

        List<Object> authors = JsonPath.parse("$.store.book[*].author").find(jo);
        assertEquals(2, authors.size());
        assertEquals("Nigel Rees", authors.get(0));
        assertEquals("Evelyn Waugh", authors.get(1));

        List<Object> prices = JsonPath.parse("$.store.book[*].price").find(jo);
        assertEquals(2, prices.size());
        assertEquals(8.95, Nodes.toDouble(prices.get(0)));
        assertEquals(12.99, Nodes.toDouble(prices.get(1)));
    }

    @Test
    public void testEdgeCases() {
        JsonObject jo = JsonObject.fromJson("{\"a\":1}");
        Object root = JsonPath.parse("$").getNode(jo);
        assertEquals(jo, root);

        JsonObject empty = new JsonObject();
        assertFalse(JsonPath.parse("$.a").hasNonNull(empty));
        assertNull(JsonPath.parse("$.a").getNode(empty));

        JsonArray emptyArray = new JsonArray();
        assertFalse(JsonPath.parse("$[0]").hasNonNull(emptyArray));
        assertNull(JsonPath.parse("$[0]").getNode(emptyArray));

        JsonObject withNull = JsonObject.fromJson("{\"a\":null}");
        assertFalse(JsonPath.parse("$.a").hasNonNull(withNull));
        assertNull(JsonPath.parse("$.a").getNode(withNull));

        JsonArray objectArray = JsonArray.of(
                JsonObject.of("a", null),
                JsonObject.of("b", 1),
                JsonObject.of("a", "x")
        );
        assertEquals(Arrays.asList((Object) null, "x"), JsonPath.parse("$[*].a").find(objectArray));

        JsonArray nestedArrays = JsonArray.of(
                JsonArray.of("a", (Object) null),
                JsonArray.of("b"),
                JsonArray.of("c", "d")
        );
        assertEquals(Arrays.asList((Object) null, "d"), JsonPath.parse("$[*][1]").find(nestedArrays));
    }

    @ToString
    public static class Person {
        public String name;
        public int age;
        public Info info;
        public List<Baby> babies;
    }

    @ToString
    public static class Info {
        public String email;
        public String city;
    }

    @ToString
    public static class Baby {
        public String name;
        public int age;
    }

    public static class PropertyPathRoot {
        public PropertyPathA a;
    }

    public static class PropertyPathA {
        public List<PropertyPathB> b;
    }

    public static class PropertyPathB {
        public String c;
    }

    public static class AutoContainerHolder {
        public Object objectField;
        public List<Object> listField;
        public JsonArray jsonArrayField;
        public AutoJsonArray autoJsonArrayField;
        public Baby[] babyArrayField;
        public Set<Object> setField;
        public Integer unsupportedObjectField;
        public Integer unsupportedArrayField;
    }

    public static class AutoJsonArray extends JsonArray {}

    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";

    @Test
    public void testPojo1() {
        Person person = Sjf4j.global().fromJson(JSON_DATA, Person.class);
        log.info("person={}", person);

        String name = JsonPath.parse("$.babies[1].name").getString(person);
        log.info("name={}", name);

    }

    @Test
    public void testEnsurePutCreatesTypedContainers() {
        AutoContainerHolder holder = new AutoContainerHolder();

        JsonPath.parse("$.objectField[+].name").ensurePut(holder, "object");
        assertTrue(holder.objectField instanceof ArrayList);
        assertEquals("object", JsonPath.parse("$.objectField[0].name").getString(holder));

        JsonPath.parse("$.listField[+].name").ensurePut(holder, "list");
        assertTrue(holder.listField instanceof ArrayList);
        assertEquals("list", JsonPath.parse("$.listField[0].name").getString(holder));

        JsonPath.parse("$.jsonArrayField[+].name").ensurePut(holder, "json-array");
        assertTrue(holder.jsonArrayField instanceof JsonArray);
        assertEquals("json-array", JsonPath.parse("$.jsonArrayField[0].name").getString(holder));

        JsonPath.parse("$.autoJsonArrayField[+].name").ensurePut(holder, "jajo");
        assertTrue(holder.autoJsonArrayField instanceof AutoJsonArray);
        assertEquals("jajo", JsonPath.parse("$.autoJsonArrayField[0].name").getString(holder));

        JsonException babyArrayFailure = assertThrows(JsonException.class,
                () -> JsonPath.parse("$.babyArrayField[2].name").ensurePut(holder, "baby"));
        assertTrue(babyArrayFailure.getMessage().contains("only List/JsonArray/JAJO/Set are supported"));
        assertNull(holder.babyArrayField);

        assertThrows(JsonException.class, () -> JsonPath.parse("$.setField[0].name").ensurePut(holder, "set"));
        assertTrue(holder.setField instanceof LinkedHashSet);

        assertThrows(JsonException.class, () -> JsonPath.parse("$.unsupportedObjectField.name").ensurePut(holder, "x"));
        assertThrows(JsonException.class, () -> JsonPath.parse("$.unsupportedArrayField[0]").ensurePut(holder, "x"));
    }

    @Test
    public void testEnsurePutReplacesNullIntermediateArrayElements() {
        JsonObject jo = JsonObject.of("items", JsonArray.of((Object) null));
        JsonPath.parse("$.items[0].name").ensurePut(jo, "Alice");
        assertEquals("Alice", JsonPath.parse("$.items[0].name").getString(jo));

        JsonObject appendJo = JsonObject.of("items", new JsonArray());
        JsonPath.parse("$.items[0].name").ensurePut(appendJo, "Zero");
        assertEquals("Zero", JsonPath.parse("$.items[0].name").getString(appendJo));

        AutoContainerHolder holder = new AutoContainerHolder();
        holder.listField = new ArrayList<>(Collections.singletonList(null));
        JsonPath.parse("$.listField[0].name").ensurePut(holder, "Bob");
        assertEquals("Bob", JsonPath.parse("$.listField[0].name").getString(holder));
    }

    @Test
    public void testEnsurePutIndexedIntermediateAppendsAtSizeUsingPropertyTypes() {
        PropertyPathRoot root = new PropertyPathRoot();

        JsonPath.parse("$.a.b[0].c").ensurePut(root, "b0");
        JsonPath.parse("$.a.b[1].c").ensurePut(root, "b1");

        assertEquals(2, root.a.b.size());
        assertEquals("b0", JsonPath.parse("$.a.b[0].c").getNode(root));
        assertEquals("b1", root.a.b.get(1).c);
    }

    @Test
    public void testSet1() {
        JsonObject jo1 = Sjf4j.global().fromJson(JSON_DATA, JsonObject.class);
        log.info("jo1={}", jo1);

        JsonPath.parse("$.babies[0].age").ensurePut(jo1, 33);
        log.info("jo1={}", jo1);
        assertEquals(33, jo1.getJsonArray("babies").getJsonObject(0).getInt("age"));

        JsonPath.parse("$.babies[1].name").ensurePut(jo1, "Grace");
        log.info("jo1={}", jo1);
        assertEquals("Grace", jo1.getJsonArray("babies").getJsonObject(1).getString("name"));

        JsonPath.parse("$.babies[+].name").ensurePut(jo1, "Zack");
        log.info("jo1={}", jo1);
        assertEquals("Zack", jo1.getJsonArray("babies").getJsonObject(3).getString("name"));

        assertThrows(JsonException.class, () -> JsonPath.parse("$.babies[9].name").ensurePut(jo1, "Error"));
    }

    @Test
    public void testSet2() {
        Person p1 = Sjf4j.global().fromJson(JSON_DATA, Person.class);
        log.info("p1={}", p1);

        JsonPath.parse("$.babies[0].age").ensurePut(p1, 33);
        log.info("p1={}", p1);
        assertEquals(33, p1.babies.get(0).age);

        JsonPath.parse("$.babies[1].name").ensurePut(p1, "Grace");
        log.info("p1={}", p1);
        assertEquals("Grace", p1.babies.get(1).name);

        JsonPath.parse("$.babies[+].name").ensurePut(p1, "Zack");
        log.info("p1={}", p1);
        assertEquals("Zack", p1.babies.get(3).name);

        assertThrows(JsonException.class, () -> JsonPath.parse("$.babies[9].name").ensurePut(p1, "Error"));
    }

    @Test
    public void testSliceOperations() {
        String json = "{\n" +
                "  \"numbers\": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],\n" +
                "  \"books\": [\n" +
                "    {\"title\": \"A\", \"price\": 10},\n" +
                "    {\"title\": \"B\", \"price\": 20},\n" +
                "    {\"title\": \"C\", \"price\": 30},\n" +
                "    {\"title\": \"D\", \"price\": 40},\n" +
                "    {\"title\": \"E\", \"price\": 50}\n" +
                "  ]\n" +
                "}";
        JsonObject jo = JsonObject.fromJson(json);

        assertEquals(2, JsonPath.parse("$.numbers[2]").getNode(jo));
        assertEquals(7, JsonPath.parse("$.numbers[-3]").getNode(jo)); // third from the end

        List<Object> slice1 = JsonPath.parse("$.numbers[1:5]").find(jo);
        assertEquals(4, slice1.size());
        assertEquals(Arrays.asList(1, 2, 3, 4), slice1);

        List<Object> slice2 = JsonPath.parse("$.numbers[::2]").find(jo); // step is 2
        assertEquals(5, slice2.size());
        assertEquals(Arrays.asList(0, 2, 4, 6, 8), slice2);

        List<Object> slice3 = JsonPath.parse("$.numbers[5:]").find(jo); // from 5 to end
        assertEquals(5, slice3.size());
        assertEquals(Arrays.asList(5, 6, 7, 8, 9), slice3);

        List<Object> slice4 = JsonPath.parse("$.numbers[:3]").find(jo); // first 3
        assertEquals(3, slice4.size());
        assertEquals(Arrays.asList(0, 1, 2), slice4);

        List<JsonObject> bookSlice = JsonPath.parse("$.books[1:4]").findAs(jo, JsonObject.class);
        assertEquals(3, bookSlice.size());
        assertEquals("B", bookSlice.get(0).getString("title"));
        assertEquals("C", bookSlice.get(1).getString("title"));
        assertEquals("D", bookSlice.get(2).getString("title"));

        List<Object> prices = JsonPath.parse("$.books[1:4].price").find(jo);
        assertEquals(3, prices.size());
        assertEquals(Arrays.asList(20, 30, 40), prices);
    }

    @Test
    public void testUnionOperations() {
        String json = "{\n" +
                "  \"numbers\": [0, 1, 2, 3, 4, 5],\n" +
                "  \"users\": [\n" +
                "    {\"name\": \"Alice\", \"age\": 25, \"role\": \"admin\"},\n" +
                "    {\"name\": \"Bob\", \"age\": 30, \"role\": \"user\"},\n" +
                "    {\"name\": \"Charlie\", \"age\": 35, \"role\": \"user\"},\n" +
                "    {\"name\": \"Diana\", \"age\": 28, \"role\": \"moderator\"}\n" +
                "  ],\n" +
                "  \"metadata\": {\n" +
                "    \"version\": \"1.0\",\n" +
                "    \"author\": \"test\",\n" +
                "    \"tags\": [\"json\", \"test\"]\n" +
                "  }\n" +
                "}";
        JsonObject jo = JsonObject.fromJson(json);

        assertThrows(JsonException.class, () -> JsonPath.parse("$.numbers[1,3,5]").getNode(jo));

        List<Object> multiIndex = JsonPath.parse("$.numbers[1,3,5]").find(jo);
        assertEquals(3, multiIndex.size());
        assertEquals(Arrays.asList(1, 3, 5), multiIndex);

        List<Object> multiName = JsonPath.parse("$.metadata['version','author']").find(jo);
        assertEquals(2, multiName.size());
        assertTrue(multiName.contains("1.0"));
        assertTrue(multiName.contains("test"));

        List<Object> usersUnion = JsonPath.parse("$.users[0,2]['name','age']").find(jo);
        assertEquals(4, usersUnion.size()); // [Alice, 25, Charlie, 35]
        assertTrue(usersUnion.contains("Alice"));
        assertTrue(usersUnion.contains("Charlie"));
        assertTrue(usersUnion.contains(25));
        assertTrue(usersUnion.contains(35));

        List<Object> mixedUnion = JsonPath.parse("$.numbers[0,2,3:6]").find(jo);
        assertEquals(5, mixedUnion.size()); // [0, 2, 3, 4, 5]
    }

    @Test
    public void testDescendantOperations() {
        String json = "{\n" +
                "  \"name\": \"root\",\n" +
                "  \"children\": [\n" +
                "    {\n" +
                "      \"name\": \"child1\",\n" +
                "      \"children\": [\n" +
                "        {\"name\": \"grandchild1\", \"value\": 100},\n" +
                "        {\"name\": \"grandchild2\", \"value\": 200}\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"child2\",\n" +
                "      \"value\": 50,\n" +
                "      \"children\": [\n" +
                "        {\"name\": \"grandchild3\", \"value\": 300, \"only\": 1}\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"tags\": [\"a\", \"b\"],\n" +
                "  \"nested\": {\n" +
                "    \"deep\": {\n" +
                "      \"name\": \"deepName\",\n" +
                "      \"values\": [10, 20, 30]\n" +
                "    }\n" +
                "  }\n" +
                "}";
        JsonObject jo = JsonObject.fromJson(json);

        List<Object> allNames = JsonPath.parse("$..name").find(jo);
        assertEquals(7, allNames.size());
        assertTrue(allNames.contains("root"));
        assertTrue(allNames.contains("child1"));
        assertTrue(allNames.contains("child2"));
        assertTrue(allNames.contains("grandchild1"));
        assertTrue(allNames.contains("grandchild2"));
        assertTrue(allNames.contains("grandchild3"));
        assertTrue(allNames.contains("deepName"));

        assertThrows(JsonException.class, () -> JsonPath.parse("$..name").getNode(jo));
        assertEquals(1, JsonPath.parse("$..only").getNode(jo));
        assertEquals(30, JsonPath.parse("$..nested..values[2]").getNode(jo));

        List<Object> allValues = JsonPath.parse("$..value").find(jo);
        assertEquals(4, allValues.size());
        assertTrue(allValues.contains(100));
        assertTrue(allValues.contains(200));
        assertTrue(allValues.contains(50));
        assertTrue(allValues.contains(300));

        List<Object> descendantArray = JsonPath.parse("$..values[1]").find(jo);
        assertEquals(1, descendantArray.size());
        assertEquals(20, descendantArray.get(0));

        List<Object> descendantChildren = JsonPath.parse("$..children[0].name").find(jo);
        log.info("descendantChildren={}", descendantChildren);
        assertEquals(3, descendantChildren.size());
        assertTrue(descendantChildren.contains("child1"));
        assertTrue(descendantChildren.contains("grandchild1"));
        assertTrue(descendantChildren.contains("grandchild3"));

        List<Object> deeps = JsonPath.parse("$..deep..*").find(jo);
        log.info("deeps={}", deeps);
        assertEquals(5, deeps.size());
    }

    @Test
    public void testComplexCombinations() {
        String json = "{\n" +
                "  \"departments\": [\n" +
                "    {\n" +
                "      \"name\": \"Engineering\",\n" +
                "      \"employees\": [\n" +
                "        {\"name\": \"Alice\", \"skills\": [\"Java\", \"Python\"], \"projects\": [\"A\", \"B\"]},\n" +
                "        {\"name\": \"Bob\", \"skills\": [\"JavaScript\", \"React\"], \"projects\": [\"C\"]}\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Marketing\",\n" +
                "      \"employees\": [\n" +
                "        {\"name\": \"Charlie\", \"skills\": [\"SEO\", \"Content\"], \"projects\": [\"D\", \"E\", \"F\"]}\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        JsonObject jo = JsonObject.fromJson(json);

        // recursion + slicing + property access
        List<Object> firstSkills = JsonPath.parse("$..employees[0].skills[0]").find(jo);
        assertEquals(2, firstSkills.size());
        assertTrue(firstSkills.contains("Java"));
        assertTrue(firstSkills.contains("SEO"));

        List<Object> selectedNames = JsonPath.parse("$..employees[0,1]['name','skills']").find(jo);
        // Contains: Alice, ["Java","Python"], Bob, ["JavaScript","React"], Charlie, ["SEO","Content"]
        assertTrue(selectedNames.size() >= 6);

        List<Object> slicedProjects = JsonPath.parse("$..projects[0:2]").find(jo);
        // Contains: ["A","B"], ["C"], ["D","E"]
        assertTrue(slicedProjects.size() >= 3);

        List<Object> complexPath = JsonPath.parse("$.departments[*].employees[0].skills[1]").find(jo);
        assertEquals(2, complexPath.size());
        assertTrue(complexPath.contains("Python"));
        assertTrue(complexPath.contains("Content"));
    }

    @Test
    public void testEdgeCasesWithNewFeatures() {
        String json = "{\n" +
                "  \"emptyArray\": [],\n" +
                "  \"emptyObject\": {},\n" +
                "  \"nullValue\": null,\n" +
                "  \"singleElement\": [42],\n" +
                "  \"nestedEmpty\": {\n" +
                "    \"empty\": [],\n" +
                "    \"deep\": {\n" +
                "      \"nothing\": null\n" +
                "    }\n" +
                "  }\n" +
                "}";
        JsonObject jo = JsonObject.fromJson(json);

        List<Object> emptySlice = JsonPath.parse("$.emptyArray[0:5]").find(jo);
        assertTrue(emptySlice.isEmpty());

        List<Object> emptyUnion = JsonPath.parse("$.emptyArray[0,1,2]").find(jo);
        assertTrue(emptyUnion.isEmpty());

        List<Object> descendantNulls = JsonPath.parse("$..nullValue").find(jo);
        assertEquals(1, descendantNulls.size());
        assertNull(descendantNulls.get(0));

        List<JsonArray> descendantEmptyArrays = JsonPath.parse("$..empty").findAs(jo, JsonArray.class);
        assertEquals(1, descendantEmptyArrays.size());
        assertTrue(descendantEmptyArrays.get(0).isEmpty());

        List<Object> singleSlice = JsonPath.parse("$.singleElement[0:1]").find(jo);
        assertEquals(1, singleSlice.size());
        assertEquals(42, singleSlice.get(0));
    }

    @Test
    public void testPojoWithNewFeatures() {
        Person person = Sjf4j.global().fromJson(JSON_DATA, Person.class);

        List<Object> allAges = JsonPath.parse("$..age").find(person);
        log.info("allAges={}", allAges);
        assertEquals(4, allAges.size()); // Alice(30) + 3 babies
        assertTrue(allAges.contains(30));
        assertTrue(allAges.contains(1));
        assertTrue(allAges.contains(2));
        assertTrue(allAges.contains(3));

        List<Object> babySlice = JsonPath.parse("$.babies[0:2]").find(person);
        log.info("babySlice={}", babySlice);
        assertEquals(2, babySlice.size());
        assertEquals("Baby-0", ((Baby) babySlice.get(0)).name);
        assertEquals("Baby-1", ((Baby) babySlice.get(1)).name);

        List<Object> selectedBabies = JsonPath.parse("$.babies[0,2]['name','age']").find(person);
        log.info("selectedBabies={}", selectedBabies);
        assertEquals(4, selectedBabies.size());
        assertTrue(selectedBabies.contains("Baby-0"));
        assertTrue(selectedBabies.contains("Baby-2"));
        assertTrue(selectedBabies.contains(1));
        assertTrue(selectedBabies.contains(3));

        List<Object> descendantNames = JsonPath.parse("$..name").find(person);
        assertEquals(4, descendantNames.size()); // Alice + 3 babies
        assertTrue(descendantNames.contains("Alice"));
        assertTrue(descendantNames.contains("Baby-0"));
        assertTrue(descendantNames.contains("Baby-1"));
        assertTrue(descendantNames.contains("Baby-2"));
    }

    @Test
    public void testPerformanceAndLargeData() {
        JsonObject largeData = new JsonObject();
        JsonArray largeArray = new JsonArray();
        for (int i = 0; i < 100; i++) {
            JsonObject item = new JsonObject();
            item.put("id", i);
            item.put("value", i * 10);
            item.put("nested", JsonObject.of("deepValue", i * 100));
            largeArray.add(item);
        }
        largeData.put("items", largeArray);
        largeData.put("metadata", JsonObject.of("count", 100));

        List<Object> slice = JsonPath.parse("$.items[10:20]").find(largeData);
        assertEquals(10, slice.size());

        List<Object> union = JsonPath.parse("$.items[5,15,25,35]['id','value']").find(largeData);
        assertEquals(8, union.size());

        List<Object> descendant = JsonPath.parse("$..deepValue").find(largeData);
        assertEquals(100, descendant.size());
    }


    @Test
    public void testEval1() {
        Person person = Sjf4j.global().fromJson(JSON_DATA, Person.class);
        JsonPath pathSum = JsonPath.parse("$..age.sum()");
        log.info("sum={}", pathSum.eval(person));

        JsonPath pathLength = JsonPath.parse("$..age.length()");
        log.info("length={}", pathLength.eval(person));

        JsonPath pathCount = JsonPath.parse("$..age.count()");
        log.info("count={}", pathCount.eval(person));

        JsonPath pathMatch = JsonPath.parse("$..email.match('alice..xample.com')");
        log.info("match={}", pathMatch.eval(person));

        JsonPath pathSearch = JsonPath.parse("$..email.search('alice')");
        log.info("search={}", pathSearch.eval(person));
        JsonPath pathSearch2 = JsonPath.parse("$..email.search('zack')");
        log.info("search={}", pathSearch2.eval(person, Boolean.class));
    }

    @Test
    public void testEval2() {
        Person p1 = Sjf4j.global().fromJson(JSON_DATA, Person.class);

        int age = JsonPath.parse("$.age").eval(p1, int.class);
        log.info("age={}", age);

        List babies = JsonPath.parse("$.babies").eval(p1, List.class);
        log.info("babies={}", babies);

        long cnt = JsonPath.parse("$.babies.count()").eval(p1, Long.class);
        log.info("cnt={}", cnt);
    }

    @Test
    public void testFilter1() {
        Person p1 = Sjf4j.global().fromJson(JSON_DATA, Person.class);
        String name = JsonPath.parse("$.babies[?@.age > 2].name").eval(p1, String.class);
        log.info("name={}", name);
        assertEquals("Baby-2", name);
    }


    public static String RFC9535_EXAMPLE1 = "{\n" +
            "  \"a\": [3, 5, 1, 2, 4, 6,\n" +
            "        {\"b\": \"j\"},\n" +
            "        {\"b\": \"k\"},\n" +
            "        {\"b\": {}},\n" +
            "        {\"b\": \"kilo\"}\n" +
            "       ],\n" +
            "  \"o\": {\"p\": 1, \"q\": 2, \"r\": 3, \"s\": 5, \"t\": {\"u\": 6}},\n" +
            "  \"e\": \"f\"\n" +
            "}";

    @Test
    public void testRfc9535_1() {
        JsonObject jo = JsonObject.fromJson(RFC9535_EXAMPLE1);
        Object rs1 = jo.evalByPath("$.a[?@.b == 'kilo']");
        log.info("rs1={}", rs1);
        Object rs2 = jo.evalByPath("$.a[?(@.b == 'kilo')]");
        log.info("rs2={}", rs2);
        Object rs3 = jo.evalByPath("$.a[?@>3.5]");
        log.info("rs3={}", rs3);
        Object rs4 = jo.evalByPath("$.a[?@.b]");
        log.info("rs4={}", rs4);
        Object rs5 = jo.evalByPath("$[?@.*]");
        log.info("rs5={}", rs5);
        Object rs6 = jo.evalByPath("$[?@[?@.b]]");
        log.info("rs6={}", rs6);
//        Object rs7 = jo.findAllNodes("$.o[?@<3, ?@<3]");
        Object rs8 = jo.evalByPath("$.a[?@<2 || @.b == \"k\"]");
        log.info("rs8={}", rs8);
        Object rs9 = jo.evalByPath("$.a[?match(@.b, \"[jk]\")]");
        log.info("rs9={}", rs9);
        Object rs10 = jo.evalByPath("$.a[?search(@.b, \"[jk]\")]");
        log.info("rs10={}", rs10);
        Object rs11 = jo.evalByPath("$.o[?@>1 && @<4]");
        log.info("rs11={}", rs11);
        Object rs12 = jo.evalByPath("$.o[?@>1 && @<4]");
        log.info("rs12={}", rs12);
        Object rs13 = jo.evalByPath("$.o[?@.u || @.x]");
        log.info("rs13={}", rs13);
        Object rs14 = jo.evalByPath("$.a[?@.b == $.x]");
        log.info("rs14={}", rs14);
        Object rs15 = jo.evalByPath("$.a[?@ == @]");
        log.info("rs15={}", rs15);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRfc9535_2() {
        JsonObject jo = JsonObject.fromJson(RFC9535_EXAMPLE1);
        Object rs1 = jo.evalByPath("$.a[?@.b == 'kilo'].b");
        log.info("rs1={}", rs1);
        assertEquals("kilo", rs1);

        Object rs3 = jo.evalByPath("$.a[?@>3.5].count()");
        log.info("rs3={}", rs3);
        assertEquals(3, rs3);

        Object rs10 = jo.evalByPath("$.a[?search(@.b, \"[jk]\")].b");
        log.info("rs10={}", rs10);
        assertEquals("k", ((List<Object>) rs10).get(1));

        Object v1 = jo.evalByPath("$..*[?(@.b == 'kilo')].b");
        log.info("v1={}", v1);
        assertEquals("kilo", v1);
    }

    @Test
    public void testFilterLiteralNullAndBoolean() {
        JsonObject jo = JsonObject.fromJson("{\"store\":{\"book\":[{" +
                "\"title\":\"A\",\"isbn\":\"111\",\"published\":true},{" +
                "\"title\":\"B\",\"published\":false},{" +
                "\"title\":\"C\",\"isbn\":null,\"published\":true}]}}");

        assertEquals(Arrays.asList("B", "C"), jo.findByPath("$.store.book[?@.isbn == null].title", String.class));
        assertEquals(Arrays.asList("A", "C"), jo.findByPath("$.store.book[?@.published == true].title", String.class));
        assertEquals(Collections.singletonList("B"), jo.findByPath("$.store.book[?@.published == false].title", String.class));
        assertEquals(Collections.singletonList("D"), JsonObject.fromJson("{\"store\":{\"book\":[{\"title\":\"D\",\"name\":\"a'b\"}]}}")
                .findByPath("$.store.book[?@.name == 'a\\'b'].title", String.class));
    }


    @Test
    public void testStringMatch() {
        JsonArray ja1 = new JsonArray();
        ja1.add(JsonObject.of("name", "Alice"));

        JsonObject jo1 = JsonPath.parse("$[?(@.name =~ /^A/)]").eval(ja1, JsonObject.class);
        assertNotNull(jo1);
        assertEquals("Alice", jo1.get("name"));

        Object jo2 = JsonPath.parse("$[?(@.name =~ /^B/)]").eval(ja1);
        assertNull(jo2);
    }

    @Test
    public void testRegexArrayElement() {
        JsonArray array = new JsonArray();
        array.add("Alice");
        array.add("Bob");
        array.add("Andrew");

        // Regex matches any string starting with 'A'
        List<Object> result = JsonPath.parse("$[?(@ =~ /^A/)]").find(array);
        assertEquals(2, result.size());
        assertTrue(result.contains("Alice"));
        assertTrue(result.contains("Andrew"));
    }

    @Test
    public void testComplexRegexWithGroups() {
        JsonArray array = new JsonArray();
        array.append(
                JsonObject.of("name", "Alice_01"),
                JsonObject.of("name", "Bob_99"),
                JsonObject.of("name", "ALIcE_02"),
                JsonObject.of("name", "ALICE_334"),
                JsonObject.of("name", "Alice-x")
        );

        List<JsonObject> result =
                JsonPath.parse("$[?@.name =~ /^(alice)_\\d{2}$/i]").find(array, JsonObject.class);

        assertEquals(2, result.size());
        assertEquals("Alice_01", result.get(0).getString("name"));
        assertEquals("ALIcE_02", result.get(1).getString("name"));
    }

    @Test
    public void testEnsurePutWithAppend1() {
        JsonObject jo = new JsonObject();
        JsonPath.parse("/bool/filter/-/term").ensurePut(jo, "haha");
        JsonPath.parse("/bool/filter/-/term").ensurePut(jo, "hoho");

        JsonObject bool = jo.getJsonObject("bool");
        assertNotNull(bool);
        JsonArray filter = bool.getJsonArray("filter");
        assertNotNull(filter);
        assertEquals(2, filter.size());
        assertEquals("haha", filter.getJsonObject(0).getString("term"));
        assertEquals("hoho", filter.getJsonObject(1).getString("term"));
    }

}
