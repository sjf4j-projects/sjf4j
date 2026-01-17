package org.sjf4j.path;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.util.NodeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class JsonPathTest {

    @Test
    public void testCompile1() {
        String s1 = "$.a.b[0].c";
        JsonPath path1 = JsonPath.compile(s1);
        log.info("path1: {}", path1);
        assertEquals("$.a.b[0].c", path1.toString());

        String s2 = "$.a[*].b['c'].d";
        JsonPath path2 = JsonPath.compile(s2);
        log.info("path2: {}", path2);
        assertEquals("$.a[*].b.c.d", path2.toExpr());

        String s3 = "$['x.y'].a['[['][-1].*[*]";
        JsonPath path3 = JsonPath.compile(s3);
        log.info("path3: {}", path3);
        assertEquals("$['x.y'].a['[['][-1].*[*]", path3.toString());

        String s4 = "$.a[''].b['\\''].c";
        JsonPath path4 = JsonPath.compile(s4);
        log.info("path4: {}", path4);
        assertEquals("$.a[''].b['\\''].c", path4.toString());
    }

    @Test
    public void testCompile2() {
        String s1 = "/a/b/0/c";
        JsonPath path1 = JsonPath.compile(s1);
        log.info("path1: {}", path1);
        assertEquals("$.a.b[0].c", path1.toExpr());
        assertEquals("/a/b/0/c", path1.toPointerExpr());

        String s2 = "/a~0/0/b~1'/c~/d e";
        JsonPath path2 = JsonPath.compile(s2);
        log.info("path2: {}", path2);
        assertEquals("$['a~'][0]['b/\\'']['c~']['d e']", path2.toExpr());
    }

    @Test
    public void testCompile3() {
        String s1 = "$..a['b'].c";
        JsonPath p1 = JsonPath.compile(s1);
        log.info("s1={}, p1={}", s1, p1);
    }

    @Test
    public void testCompile4() {
        String s1 = "a.b[0].c";
        JsonPath path1 = JsonPath.compile(s1);
        log.info("path1: {}", path1);
        assertEquals("a.b[0].c", path1.toString());
        assertEquals("$.a.b[0].c", path1.toExpr());
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

        assertEquals("B", JsonPath.compile("$.book[1]['title']").getNode(jo1));
        assertEquals("B", JsonPath.compile("/book/1/title").getNode(jo1));
        assertEquals(10, JsonPath.compile("$.book[0].price").getNode(jo1));
        assertEquals("classic", JsonPath.compile("$.book[0].tags[0]").getNode(jo1));
        assertEquals(Collections.emptyList(), JsonPath.compile("$.emptyArray").getNode(jo1));
        assertEquals(Collections.emptyMap(), JsonPath.compile("$['emptyObject']").getNode(jo1));
        assertNull(JsonPath.compile("$.nullValue").getNode(jo1));
        assertEquals("v1", JsonPath.compile("$['weird.keys']['key with spaces']").getNode(jo1));
        assertEquals("v1", JsonPath.compile("/weird.keys/key with spaces").getNode(jo1));

        log.info("$: {}", JsonPath.compile("$").getNode(jo1));
        assertEquals(JsonObject.class, JsonPath.compile("$").getNode(jo1).getClass());

        assertThrows(JsonException.class, () -> JsonPath.compile("$.book[*].price").getNode(jo1));

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

        List<Object> result1 = JsonPath.compile("$.book[*].title").findNodes(jo1);
        log.info("result1: {}", result1);
        assertEquals(3, result1.size());
        assertEquals("B", result1.get(1));

        List<JsonArray> result2 = JsonPath.compile("$.book[*].tags").findAs(jo1, JsonArray.class);
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

        JsonPath.compile("$.book[0].box[0].gg").ensurePut(jo1, "mm");
        JsonObject container1 = JsonPath.compile("$.book[0].box[0]").getJsonObject(jo1);
        log.info("container1={} jo1={}", container1, jo1);
        assertEquals(JsonObject.class, container1.getClass());
        assertEquals(1, jo1.getJsonArrayByPath("$.book[0].box").size());

        JsonPath.compile("$.book[2].tags['gg mm'][0]").ensurePut(jo1, "mm");
        JsonArray container2 = JsonPath.compile("$.book[2].tags['gg mm']").getJsonArray(jo1);
        log.info("container2={} jo1={}", container2, jo1);
        assertEquals(JsonArray.class, container2.getClass());
        assertEquals(1, jo1.getJsonArrayByPath("$.book[2].tags['gg mm']").size());
    }

    @Test
    public void testMapList1() {
        JsonObject jo1 = new JsonObject("names", new int[]{1,2,3});
        Map<String, List<JsonObject>> map = new HashMap<>();
        List<JsonObject> lis = new ArrayList<>();
        lis.add(new JsonObject("kk", "ll"));
        map.put("lis", lis);
        jo1.put("map", map);

        log.info("jo1={}", jo1);
        assertEquals(2, JsonPath.compile("$.names[1]").getLong(jo1));
        assertEquals("ll", JsonPath.compile("$.map.lis[0].kk").getString(jo1));
        assertEquals(ArrayList.class, JsonPath.compile("$.map.lis").getNode(jo1).getClass());
    }

    @Test
    public void testEnsurePutAndRemove() {
        JsonObject jo = JsonObject.fromJson("{\"a\":{\"b\":123},\"array\":[1,2,3]}");

        JsonPath path1 = JsonPath.compile("$.a.c");
        path1.ensurePut(jo, "newValue");
        assertEquals("newValue", jo.getStringByPath("$.a.c"));

        JsonPath path2 = JsonPath.compile("$.array[1]");
        path2.ensurePut(jo, 999);
        assertEquals(999, jo.getIntegerByPath("$.array[1]"));

        JsonPath path3 = JsonPath.compile("$.a.d");
        path3.ensurePutNonNull(jo, "value");
        assertEquals("value", jo.getStringByPath("$.a.d"));
        path3.ensurePutNonNull(jo, null);
        assertEquals("value", jo.getStringByPath("$.a.d")); // 不应该被覆盖

        JsonPath path4 = JsonPath.compile("$.a.e");
        path4.ensurePutIfAbsent(jo, "first");
        assertEquals("first", jo.getStringByPath("$.a.e"));
        path4.ensurePutIfAbsent(jo, "second");
        assertEquals("first", jo.getStringByPath("$.a.e")); // 不应该被覆盖

        JsonPath path5 = JsonPath.compile("$.a.b");
        assertTrue(path5.hasNonNull(jo));
        path5.remove(jo);
        assertFalse(path5.hasNonNull(jo));

        JsonPath path6 = JsonPath.compile("$.array[0]");
        path6.remove(jo);
        assertEquals(2, jo.getJsonArray("array").size());
        assertEquals(999, jo.getJsonArray("array").getInteger(0));
    }

    @Test
    public void testExceptionPaths() {
        JsonObject jo = JsonObject.fromJson("{\"a\":1}");

        assertNull(JsonPath.compile("$.nonexist").getNode(jo));
        assertEquals("default", JsonPath.compile("$.nonexist").getString(jo, "default"));

        assertThrows(JsonException.class, () -> {
            JsonPath.compile("$.a[*]").getNode(jo);
        });

        assertDoesNotThrow(() -> JsonPath.compile("no.invalid"));

        assertThrows(JsonException.class, () -> {
            JsonPath.compile("");
        });

        JsonArray ja = JsonArray.fromJson("[1,2,3]");
        assertNull(JsonPath.compile("$[10]").getNode(ja));
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

        assertEquals("reference", JsonPath.compile("$.store.book[0].category").getNode(jo));
        assertEquals(12.99, JsonPath.compile("$.store.book[1].price").getDouble(jo));
        assertEquals("red", JsonPath.compile("$.store.bicycle.color").getNode(jo));

        List<Object> authors = JsonPath.compile("$.store.book[*].author").findNodes(jo);
        assertEquals(2, authors.size());
        assertEquals("Nigel Rees", authors.get(0));
        assertEquals("Evelyn Waugh", authors.get(1));

        List<Object> prices = JsonPath.compile("$.store.book[*].price").findNodes(jo);
        assertEquals(2, prices.size());
        assertEquals(8.95, NodeUtil.toDouble(prices.get(0)));
        assertEquals(12.99, NodeUtil.toDouble(prices.get(1)));
    }

    @Test
    public void testEdgeCases() {
        JsonObject jo = JsonObject.fromJson("{\"a\":1}");
        Object root = JsonPath.compile("$").getNode(jo);
        assertEquals(jo, root);

        JsonObject empty = new JsonObject();
        assertFalse(JsonPath.compile("$.a").hasNonNull(empty));
        assertNull(JsonPath.compile("$.a").getNode(empty));

        JsonArray emptyArray = new JsonArray();
        assertFalse(JsonPath.compile("$[0]").hasNonNull(emptyArray));
        assertNull(JsonPath.compile("$[0]").getNode(emptyArray));

        JsonObject withNull = JsonObject.fromJson("{\"a\":null}");
        assertFalse(JsonPath.compile("$.a").hasNonNull(withNull));
        assertNull(JsonPath.compile("$.a").getNode(withNull));
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

    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";

    @Test
    public void testPojo1() {
        Person person = Sjf4j.fromJson(JSON_DATA, Person.class);
        log.info("person={}", person);

        String name = JsonPath.compile("$.babies[1].name").getString(person);
        log.info("name={}", name);

    }

    @Test
    public void testSet1() {
        JsonObject jo1 = Sjf4j.fromJson(JSON_DATA, JsonObject.class);
        log.info("jo1={}", jo1);

        JsonPath.compile("$.babies[0].age").ensurePut(jo1, 33);
        log.info("jo1={}", jo1);
        assertEquals(33, jo1.getJsonArray("babies").getJsonObject(0).getInteger("age"));

        JsonPath.compile("$.babies[1].name").ensurePut(jo1, "Grace");
        log.info("jo1={}", jo1);
        assertEquals("Grace", jo1.getJsonArray("babies").getJsonObject(1).getString("name"));

        JsonPath.compile("$.babies[3].name").ensurePut(jo1, "Zack");
        log.info("jo1={}", jo1);
        assertEquals("Zack", jo1.getJsonArray("babies").getJsonObject(3).getString("name"));

        assertThrows(JsonException.class, () -> JsonPath.compile("$.babies[9].name").ensurePut(jo1, "Error"));
    }

    @Test
    public void testSet2() {
        Person p1 = Sjf4j.fromJson(JSON_DATA, Person.class);
        log.info("p1={}", p1);

        JsonPath.compile("$.babies[0].age").ensurePut(p1, 33);
        log.info("p1={}", p1);
        assertEquals(33, p1.babies.get(0).age);

        JsonPath.compile("$.babies[1].name").ensurePut(p1, "Grace");
        log.info("p1={}", p1);
        assertEquals("Grace", p1.babies.get(1).name);

        JsonPath.compile("$.babies[3].name").ensurePut(p1, "Zack");
        log.info("p1={}", p1);
        assertEquals("Zack", p1.babies.get(3).name);

        assertThrows(JsonException.class, () -> JsonPath.compile("$.babies[9].name").ensurePut(p1, "Error"));
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

        assertEquals(2, JsonPath.compile("$.numbers[2]").getNode(jo));
        assertEquals(7, JsonPath.compile("$.numbers[-3]").getNode(jo)); // 倒数第三个

        List<Object> slice1 = JsonPath.compile("$.numbers[1:5]").findNodes(jo);
        assertEquals(4, slice1.size());
        assertEquals(Arrays.asList(1, 2, 3, 4), slice1);

        List<Object> slice2 = JsonPath.compile("$.numbers[::2]").findNodes(jo); // 步长为2
        assertEquals(5, slice2.size());
        assertEquals(Arrays.asList(0, 2, 4, 6, 8), slice2);

        List<Object> slice3 = JsonPath.compile("$.numbers[5:]").findNodes(jo); // 从5开始到结束
        assertEquals(5, slice3.size());
        assertEquals(Arrays.asList(5, 6, 7, 8, 9), slice3);

        List<Object> slice4 = JsonPath.compile("$.numbers[:3]").findNodes(jo); // 前3个
        assertEquals(3, slice4.size());
        assertEquals(Arrays.asList(0, 1, 2), slice4);

        List<JsonObject> bookSlice = JsonPath.compile("$.books[1:4]").findAs(jo, JsonObject.class);
        assertEquals(3, bookSlice.size());
        assertEquals("B", bookSlice.get(0).getString("title"));
        assertEquals("C", bookSlice.get(1).getString("title"));
        assertEquals("D", bookSlice.get(2).getString("title"));

        List<Object> prices = JsonPath.compile("$.books[1:4].price").findNodes(jo);
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

        assertThrows(JsonException.class, () -> JsonPath.compile("$.numbers[1,3,5]").getNode(jo));

        List<Object> multiIndex = JsonPath.compile("$.numbers[1,3,5]").findNodes(jo);
        assertEquals(3, multiIndex.size());
        assertEquals(Arrays.asList(1, 3, 5), multiIndex);

        List<Object> multiName = JsonPath.compile("$.metadata['version','author']").findNodes(jo);
        assertEquals(2, multiName.size());
        assertTrue(multiName.contains("1.0"));
        assertTrue(multiName.contains("test"));

        List<Object> usersUnion = JsonPath.compile("$.users[0,2]['name','age']").findNodes(jo);
        assertEquals(4, usersUnion.size()); // [Alice, 25, Charlie, 35]
        assertTrue(usersUnion.contains("Alice"));
        assertTrue(usersUnion.contains("Charlie"));
        assertTrue(usersUnion.contains(25));
        assertTrue(usersUnion.contains(35));

        List<Object> mixedUnion = JsonPath.compile("$.numbers[0,2,3:6]").findNodes(jo);
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

        List<Object> allNames = JsonPath.compile("$..name").findNodes(jo);
        assertEquals(7, allNames.size());
        assertTrue(allNames.contains("root"));
        assertTrue(allNames.contains("child1"));
        assertTrue(allNames.contains("child2"));
        assertTrue(allNames.contains("grandchild1"));
        assertTrue(allNames.contains("grandchild2"));
        assertTrue(allNames.contains("grandchild3"));
        assertTrue(allNames.contains("deepName"));

        assertThrows(JsonException.class, () -> JsonPath.compile("$..name").getNode(jo));
        assertEquals(1, JsonPath.compile("$..only").getNode(jo));
        assertEquals(30, JsonPath.compile("$..nested..values[2]").getNode(jo));

        List<Object> allValues = JsonPath.compile("$..value").findNodes(jo);
        assertEquals(4, allValues.size());
        assertTrue(allValues.contains(100));
        assertTrue(allValues.contains(200));
        assertTrue(allValues.contains(50));
        assertTrue(allValues.contains(300));

        List<Object> descendantArray = JsonPath.compile("$..values[1]").findNodes(jo);
        assertEquals(1, descendantArray.size());
        assertEquals(20, descendantArray.get(0));

        List<Object> descendantChildren = JsonPath.compile("$..children[0].name").findNodes(jo);
        log.info("descendantChildren={}", descendantChildren);
        assertEquals(3, descendantChildren.size());
        assertTrue(descendantChildren.contains("child1"));
        assertTrue(descendantChildren.contains("grandchild1"));
        assertTrue(descendantChildren.contains("grandchild3"));

        List<Object> deeps = JsonPath.compile("$..deep..*").findNodes(jo);
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

        // 递归 + 切片 + 属性访问
        List<Object> firstSkills = JsonPath.compile("$..employees[0].skills[0]").findNodes(jo);
        assertEquals(2, firstSkills.size());
        assertTrue(firstSkills.contains("Java"));
        assertTrue(firstSkills.contains("SEO"));

        List<Object> selectedNames = JsonPath.compile("$..employees[0,1]['name','skills']").findNodes(jo);
        // Contains: Alice, ["Java","Python"], Bob, ["JavaScript","React"], Charlie, ["SEO","Content"]
        assertTrue(selectedNames.size() >= 6);

        List<Object> slicedProjects = JsonPath.compile("$..projects[0:2]").findNodes(jo);
        // Contains: ["A","B"], ["C"], ["D","E"]
        assertTrue(slicedProjects.size() >= 3);

        List<Object> complexPath = JsonPath.compile("$.departments[*].employees[0].skills[1]").findNodes(jo);
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

        List<Object> emptySlice = JsonPath.compile("$.emptyArray[0:5]").findNodes(jo);
        assertTrue(emptySlice.isEmpty());

        List<Object> emptyUnion = JsonPath.compile("$.emptyArray[0,1,2]").findNodes(jo);
        assertTrue(emptyUnion.isEmpty());

        List<Object> descendantNulls = JsonPath.compile("$..nullValue").findNodes(jo);
        assertEquals(1, descendantNulls.size());
        assertNull(descendantNulls.get(0));

        List<JsonArray> descendantEmptyArrays = JsonPath.compile("$..empty").findAs(jo, JsonArray.class);
        assertEquals(1, descendantEmptyArrays.size());
        assertTrue(descendantEmptyArrays.get(0).isEmpty());

        List<Object> singleSlice = JsonPath.compile("$.singleElement[0:1]").findNodes(jo);
        assertEquals(1, singleSlice.size());
        assertEquals(42, singleSlice.get(0));
    }

    @Test
    public void testPojoWithNewFeatures() {
        Person person = Sjf4j.fromJson(JSON_DATA, Person.class);

        List<Object> allAges = JsonPath.compile("$..age").findNodes(person);
        log.info("allAges={}", allAges);
        assertEquals(4, allAges.size()); // Alice(30) + 3 babies
        assertTrue(allAges.contains(30));
        assertTrue(allAges.contains(1));
        assertTrue(allAges.contains(2));
        assertTrue(allAges.contains(3));

        List<Object> babySlice = JsonPath.compile("$.babies[0:2]").findNodes(person);
        log.info("babySlice={}", babySlice);
        assertEquals(2, babySlice.size());
        assertEquals("Baby-0", ((Baby) babySlice.get(0)).name);
        assertEquals("Baby-1", ((Baby) babySlice.get(1)).name);

        List<Object> selectedBabies = JsonPath.compile("$.babies[0,2]['name','age']").findNodes(person);
        log.info("selectedBabies={}", selectedBabies);
        assertEquals(4, selectedBabies.size());
        assertTrue(selectedBabies.contains("Baby-0"));
        assertTrue(selectedBabies.contains("Baby-2"));
        assertTrue(selectedBabies.contains(1));
        assertTrue(selectedBabies.contains(3));

        List<Object> descendantNames = JsonPath.compile("$..name").findNodes(person);
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
            item.put("nested", new JsonObject("deepValue", i * 100));
            largeArray.add(item);
        }
        largeData.put("items", largeArray);
        largeData.put("metadata", new JsonObject("count", 100));

        List<Object> slice = JsonPath.compile("$.items[10:20]").findNodes(largeData);
        assertEquals(10, slice.size());

        List<Object> union = JsonPath.compile("$.items[5,15,25,35]['id','value']").findNodes(largeData);
        assertEquals(8, union.size());

        List<Object> descendant = JsonPath.compile("$..deepValue").findNodes(largeData);
        assertEquals(100, descendant.size());
    }


    @Test
    public void testEval1() {
        Person person = Sjf4j.fromJson(JSON_DATA, Person.class);
        JsonPath pathSum = JsonPath.compile("$..age.sum()");
        log.info("sum={}", pathSum.eval(person));

        JsonPath pathLength = JsonPath.compile("$..age.length()");
        log.info("length={}", pathLength.eval(person));

        JsonPath pathCount = JsonPath.compile("$..age.count()");
        log.info("count={}", pathCount.eval(person));

        JsonPath pathMatch = JsonPath.compile("$..email.match('alice..xample.com')");
        log.info("match={}", pathMatch.eval(person));

        JsonPath pathSearch = JsonPath.compile("$..email.search('alice')");
        log.info("search={}", pathSearch.eval(person));
        JsonPath pathSearch2 = JsonPath.compile("$..email.search('zack')");
        log.info("search={}", pathSearch2.eval(person, Boolean.class));
    }

    @Test
    public void testEval2() {
        Person p1 = Sjf4j.fromJson(JSON_DATA, Person.class);

        int age = JsonPath.compile("$.age").eval(p1, int.class);
        log.info("age={}", age);

        List babies = JsonPath.compile("$.babies").eval(p1, List.class);
        log.info("babies={}", babies);

        long cnt = JsonPath.compile("$.babies.count()").eval(p1, Long.class);
        log.info("cnt={}", cnt);
    }

    @Test
    public void testFilter1() {
        Person p1 = Sjf4j.fromJson(JSON_DATA, Person.class);
        String name = JsonPath.compile("$.babies[?@.age > 2].name").eval(p1, String.class);
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
    public void testStringMatch() {
        JsonArray ja1 = new JsonArray();
        ja1.add(new JsonObject("name", "Alice"));

        JsonObject jo1 = JsonPath.compile("$[?(@.name =~ /^A/)]").eval(ja1, JsonObject.class);
        assertNotNull(jo1);
        assertEquals("Alice", jo1.get("name"));

        Object jo2 = JsonPath.compile("$[?(@.name =~ /^B/)]").eval(ja1);
        assertNull(jo2);
    }

    @Test
    public void testRegexArrayElement() {
        JsonArray array = new JsonArray();
        array.add("Alice");
        array.add("Bob");
        array.add("Andrew");

        // Regex matches any string starting with 'A'
        List<Object> result = JsonPath.compile("$[?(@ =~ /^A/)]").findNodes(array);
        assertEquals(2, result.size());
        assertTrue(result.contains("Alice"));
        assertTrue(result.contains("Andrew"));
    }

    @Test
    public void testComplexRegexWithGroups() {
        JsonArray array = new JsonArray();
        array.addAll(
                new JsonObject("name", "Alice_01"),
                new JsonObject("name", "Bob_99"),
                new JsonObject("name", "ALIcE_02"),
                new JsonObject("name", "ALICE_334"),
                new JsonObject("name", "Alice-x")
        );

        List<JsonObject> result =
                JsonPath.compile("$[?@.name =~ /^(alice)_\\d{2}$/i]").find(array, JsonObject.class);

        assertEquals(2, result.size());
        assertEquals("Alice_01", result.get(0).getString("name"));
        assertEquals("ALIcE_02", result.get(1).getString("name"));
    }

}
