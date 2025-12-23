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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    public void testFindNode1() {
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

        assertEquals("B", JsonPath.compile("$.book[1]['title']").findNode(jo1));
        assertEquals("B", JsonPath.compile("/book/1/title").findNode(jo1));
        assertEquals(10, JsonPath.compile("$.book[0].price").findNode(jo1));
        assertEquals("classic", JsonPath.compile("$.book[0].tags[0]").findNode(jo1));
        assertEquals(new JsonArray(), JsonPath.compile("$.emptyArray").findNode(jo1));
        assertEquals(new JsonObject(), JsonPath.compile("$['emptyObject']").findNode(jo1));
        assertNull(JsonPath.compile("$.nullValue").findNode(jo1));
        assertEquals("v1", JsonPath.compile("$['weird.keys']['key with spaces']").findNode(jo1));
        assertEquals("v1", JsonPath.compile("/weird.keys/key with spaces").findNode(jo1));

        log.info("$: {}", JsonPath.compile("$").findNode(jo1));
        assertEquals(JsonObject.class, JsonPath.compile("$").findNode(jo1).getClass());

        assertThrows(JsonException.class, () -> JsonPath.compile("$.book[*].price").findNode(jo1));

    }

    @Test
    public void testFindAll1() {
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

        List<Object> result1 = JsonPath.compile("$.book[*].title").findAllNodes(jo1);
        log.info("result1: {}", result1);
        assertEquals(3, result1.size());
        assertEquals("B", result1.get(1));

        List<JsonArray> result2 = JsonPath.compile("$.book[*].tags").findAllAs(jo1, JsonArray.class);
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
        JsonObject container1 = JsonPath.compile("$.book[0].box[0]").findAsJsonObject(jo1);
        log.info("container1={} jo1={}", container1, jo1);
        assertEquals(JsonObject.class, container1.getClass());
        assertEquals(1, jo1.asJsonArrayByPath("$.book[0].box").size());

        JsonPath.compile("$.book[2].tags['gg mm'][0]").ensurePut(jo1, "mm");
        JsonArray container2 = JsonPath.compile("$.book[2].tags['gg mm']").findAsJsonArray(jo1);
        log.info("container2={} jo1={}", container2, jo1);
        assertEquals(JsonArray.class, container2.getClass());
        assertEquals(1, jo1.asJsonArrayByPath("$.book[2].tags['gg mm']").size());
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
        assertEquals(2, JsonPath.compile("$.names[1]").findLong(jo1));
        assertEquals("ll", JsonPath.compile("$.map.lis[0].kk").findString(jo1));
        assertEquals(ArrayList.class, JsonPath.compile("$.map.lis").findNode(jo1).getClass());
    }

    public void testEnsurePutAndRemove() {
        JsonObject jo = JsonObject.fromJson("{\"a\":{\"b\":123},\"array\":[1,2,3]}");
        
        // 测试put
        JsonPath path1 = JsonPath.compile("$.a.c");
        path1.ensurePut(jo, "newValue");
        assertEquals("newValue", jo.getStringByPath("$.a.c"));
        
        // 测试put数组索引
        JsonPath path2 = JsonPath.compile("$.array[1]");
        path2.ensurePut(jo, 999);
        assertEquals(999, jo.getIntegerByPath("$.array[1]"));
        
        // 测试putNonNull
        JsonPath path3 = JsonPath.compile("$.a.d");
        path3.ensurePutNonNull(jo, "value");
        assertEquals("value", jo.getStringByPath("$.a.d"));
        path3.ensurePutNonNull(jo, null);
        assertEquals("value", jo.getStringByPath("$.a.d")); // 不应该被覆盖
        
        // 测试putIfAbsent
        JsonPath path4 = JsonPath.compile("$.a.e");
        path4.ensurePutIfAbsent(jo, "first");
        assertEquals("first", jo.getStringByPath("$.a.e"));
        path4.ensurePutIfAbsent(jo, "second");
        assertEquals("first", jo.getStringByPath("$.a.e")); // 不应该被覆盖
        
        // 测试remove
        JsonPath path5 = JsonPath.compile("$.a.b");
        assertTrue(path5.hasNonNull(jo));
        path5.remove(jo);
        assertFalse(path5.hasNonNull(jo));
        
        // 测试remove数组元素
        JsonPath path6 = JsonPath.compile("$.array[0]");
        path6.remove(jo);
        assertEquals(2, jo.getJsonArray("array").size());
        assertEquals(999, jo.getJsonArray("array").getInteger(0));
    }

    @Test
    public void testExceptionPaths() {
        JsonObject jo = JsonObject.fromJson("{\"a\":1}");
        
        // 测试不存在的路径
        assertNull(JsonPath.compile("$.nonexist").findNode(jo));
        assertEquals("default", JsonPath.compile("$.nonexist").findString(jo, "default"));
        
        // 测试通配符在findOne中
        assertThrows(JsonException.class, () -> {
            JsonPath.compile("$.a[*]").findNode(jo);
        });
        
        // 测试无效的路径表达式
        assertThrows(JsonException.class, () -> {
            JsonPath.compile("invalid");
        });
        
        // 测试空路径
        assertThrows(JsonException.class, () -> {
            JsonPath.compile("");
        });
        
        // 测试数组越界
        JsonArray ja = JsonArray.fromJson("[1,2,3]");
        assertNull(JsonPath.compile("$[10]").findNode(ja));
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
        
        // 复杂路径查找
        assertEquals("reference", JsonPath.compile("$.store.book[0].category").findNode(jo));
        assertEquals(12.99, JsonPath.compile("$.store.book[1].price").findDouble(jo));
        assertEquals("red", JsonPath.compile("$.store.bicycle.color").findNode(jo));
        
        // 使用通配符查找所有
        List<Object> authors = JsonPath.compile("$.store.book[*].author").findAllNodes(jo);
        assertEquals(2, authors.size());
        assertEquals("Nigel Rees", authors.get(0));
        assertEquals("Evelyn Waugh", authors.get(1));
        
        // 使用通配符查找所有价格
        List<Object> prices = JsonPath.compile("$.store.book[*].price").findAllNodes(jo);
        assertEquals(2, prices.size());
        assertEquals(8.95, NodeUtil.toDouble(prices.get(0)));
        assertEquals(12.99, NodeUtil.toDouble(prices.get(1)));
    }

    @Test
    public void testEdgeCases() {
        // 测试根路径
        JsonObject jo = JsonObject.fromJson("{\"a\":1}");
        Object root = JsonPath.compile("$").findNode(jo);
        assertEquals(jo, root);
        
        // 测试空对象
        JsonObject empty = new JsonObject();
        assertFalse(JsonPath.compile("$.a").hasNonNull(empty));
        assertNull(JsonPath.compile("$.a").findNode(empty));
        
        // 测试空数组
        JsonArray emptyArray = new JsonArray();
        assertFalse(JsonPath.compile("$[0]").hasNonNull(emptyArray));
        assertNull(JsonPath.compile("$[0]").findNode(emptyArray));
        
        // 测试null值
        JsonObject withNull = JsonObject.fromJson("{\"a\":null}");
        assertFalse(JsonPath.compile("$.a").hasNonNull(withNull));
        assertNull(JsonPath.compile("$.a").findNode(withNull));
    }


    // --------- 模拟的 POJO ------------
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

        String name = JsonPath.compile("$.babies[1].name").findString(person);
        log.info("name={}", name);

    }

    @Test
    public void testSet1() {
        JsonObject jo1 = Sjf4j.fromJson(JSON_DATA, JsonObject.class);
        log.info("jo1={}", jo1);

        JsonPath.compile("$.babies[0].age").ensurePut(jo1, 33);
        log.info("jo1={}", jo1);
        assertEquals(33, jo1.asJsonArray("babies").asJsonObject(0).getInteger("age"));

        JsonPath.compile("$.babies[1].name").ensurePut(jo1, "Grace");
        log.info("jo1={}", jo1);
        assertEquals("Grace", jo1.asJsonArray("babies").asJsonObject(1).getString("name"));

        JsonPath.compile("$.babies[3].name").ensurePut(jo1, "Zack");
        log.info("jo1={}", jo1);
        assertEquals("Zack", jo1.asJsonArray("babies").asJsonObject(3).getString("name"));

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

        // 基础切片测试 - readObject
        assertEquals(2, JsonPath.compile("$.numbers[2]").findNode(jo));
        assertEquals(7, JsonPath.compile("$.numbers[-3]").findNode(jo)); // 倒数第三个

        // 切片测试 - readAll
        List<Object> slice1 = JsonPath.compile("$.numbers[1:5]").findAllNodes(jo);
        assertEquals(4, slice1.size());
        assertEquals(Arrays.asList(1, 2, 3, 4), slice1);

        List<Object> slice2 = JsonPath.compile("$.numbers[::2]").findAllNodes(jo); // 步长为2
        assertEquals(5, slice2.size());
        assertEquals(Arrays.asList(0, 2, 4, 6, 8), slice2);

        List<Object> slice3 = JsonPath.compile("$.numbers[5:]").findAllNodes(jo); // 从5开始到结束
        assertEquals(5, slice3.size());
        assertEquals(Arrays.asList(5, 6, 7, 8, 9), slice3);

        List<Object> slice4 = JsonPath.compile("$.numbers[:3]").findAllNodes(jo); // 前3个
        assertEquals(3, slice4.size());
        assertEquals(Arrays.asList(0, 1, 2), slice4);

        // 对象数组的切片
        List<JsonObject> bookSlice = JsonPath.compile("$.books[1:4]").findAllAs(jo, JsonObject.class);
        assertEquals(3, bookSlice.size());
        assertEquals("B", bookSlice.get(0).getString("title"));
        assertEquals("C", bookSlice.get(1).getString("title"));
        assertEquals("D", bookSlice.get(2).getString("title"));

        // 切片中的属性访问
        List<Object> prices = JsonPath.compile("$.books[1:4].price").findAllNodes(jo);
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

        // 多索引联合 - readObject (返回第一个)
        assertThrows(JsonException.class, () -> JsonPath.compile("$.numbers[1,3,5]").findNode(jo));

        // 多索引联合 - readAll
        List<Object> multiIndex = JsonPath.compile("$.numbers[1,3,5]").findAllNodes(jo);
        assertEquals(3, multiIndex.size());
        assertEquals(Arrays.asList(1, 3, 5), multiIndex);

        // 多名称联合
        List<Object> multiName = JsonPath.compile("$.metadata['version','author']").findAllNodes(jo);
        assertEquals(2, multiName.size());
        assertTrue(multiName.contains("1.0"));
        assertTrue(multiName.contains("test"));

        // 混合联合 (索引和名称)
        List<Object> usersUnion = JsonPath.compile("$.users[0,2]['name','age']").findAllNodes(jo);
        assertEquals(4, usersUnion.size()); // [Alice, 25, Charlie, 35]
        assertTrue(usersUnion.contains("Alice"));
        assertTrue(usersUnion.contains("Charlie"));
        assertTrue(usersUnion.contains(25));
        assertTrue(usersUnion.contains(35));

        // 带切片的联合
        List<Object> mixedUnion = JsonPath.compile("$.numbers[0,2,3:6]").findAllNodes(jo);
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

        // 递归查找所有name - readAll
        List<Object> allNames = JsonPath.compile("$..name").findAllNodes(jo);
        assertEquals(7, allNames.size());
        assertTrue(allNames.contains("root"));
        assertTrue(allNames.contains("child1"));
        assertTrue(allNames.contains("child2"));
        assertTrue(allNames.contains("grandchild1"));
        assertTrue(allNames.contains("grandchild2"));
        assertTrue(allNames.contains("grandchild3"));
        assertTrue(allNames.contains("deepName"));

        // 递归查找第一个name - readObject
        assertThrows(JsonException.class, () -> JsonPath.compile("$..name").findNode(jo));
        assertEquals(1, JsonPath.compile("$..only").findNode(jo));
        assertEquals(30, JsonPath.compile("$..nested..values[2]").findNode(jo));

        // 递归查找特定路径
        List<Object> allValues = JsonPath.compile("$..value").findAllNodes(jo);
        assertEquals(4, allValues.size());
        assertTrue(allValues.contains(100));
        assertTrue(allValues.contains(200));
        assertTrue(allValues.contains(50));
        assertTrue(allValues.contains(300));

        // 递归数组访问
        List<Object> descendantArray = JsonPath.compile("$..values[1]").findAllNodes(jo);
        assertEquals(1, descendantArray.size());
        assertEquals(20, descendantArray.get(0));

        // 组合递归和其他操作
        List<Object> descendantChildren = JsonPath.compile("$..children[0].name").findAllNodes(jo);
        log.info("descendantChildren={}", descendantChildren);
        assertEquals(3, descendantChildren.size());
        assertTrue(descendantChildren.contains("child1"));
        assertTrue(descendantChildren.contains("grandchild1"));
        assertTrue(descendantChildren.contains("grandchild3"));

        List<Object> deeps = JsonPath.compile("$..deep..*").findAllNodes(jo);
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
        List<Object> firstSkills = JsonPath.compile("$..employees[0].skills[0]").findAllNodes(jo);
        assertEquals(2, firstSkills.size());
        assertTrue(firstSkills.contains("Java"));
        assertTrue(firstSkills.contains("SEO"));

        // 联合 + 递归
        List<Object> selectedNames = JsonPath.compile("$..employees[0,1]['name','skills']").findAllNodes(jo);
        // 应该包含: Alice, ["Java","Python"], Bob, ["JavaScript","React"], Charlie, ["SEO","Content"]
        assertTrue(selectedNames.size() >= 6);

        // 切片 + 递归
        List<Object> slicedProjects = JsonPath.compile("$..projects[0:2]").findAllNodes(jo);
        // 应该包含: ["A","B"], ["C"], ["D","E"]
        assertTrue(slicedProjects.size() >= 3);

        // 复杂路径: 所有部门的第一个员工的第二个技能
        List<Object> complexPath = JsonPath.compile("$.departments[*].employees[0].skills[1]").findAllNodes(jo);
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

        // 空数组的切片
        List<Object> emptySlice = JsonPath.compile("$.emptyArray[0:5]").findAllNodes(jo);
        assertTrue(emptySlice.isEmpty());

        // 空数组的联合
        List<Object> emptyUnion = JsonPath.compile("$.emptyArray[0,1,2]").findAllNodes(jo);
        assertTrue(emptyUnion.isEmpty());

        // 递归查找空值
        List<Object> descendantNulls = JsonPath.compile("$..nullValue").findAllNodes(jo);
        assertEquals(1, descendantNulls.size());
        assertNull(descendantNulls.get(0));

        // 递归查找空数组
        List<JsonArray> descendantEmptyArrays = JsonPath.compile("$..empty").findAllAs(jo, JsonArray.class);
        assertEquals(1, descendantEmptyArrays.size());
        assertTrue(descendantEmptyArrays.get(0).isEmpty());

        // 单元素数组的切片
        List<Object> singleSlice = JsonPath.compile("$.singleElement[0:1]").findAllNodes(jo);
        assertEquals(1, singleSlice.size());
        assertEquals(42, singleSlice.get(0));
    }

    @Test
    public void testPojoWithNewFeatures() {
        Person person = Sjf4j.fromJson(JSON_DATA, Person.class);

        // 递归查找POJO中的属性
        List<Object> allAges = JsonPath.compile("$..age").findAllNodes(person);
        log.info("allAges={}", allAges);
        assertEquals(4, allAges.size()); // Alice(30) + 3 babies
        assertTrue(allAges.contains(30));
        assertTrue(allAges.contains(1));
        assertTrue(allAges.contains(2));
        assertTrue(allAges.contains(3));

        // 切片POJO数组
        List<Object> babySlice = JsonPath.compile("$.babies[0:2]").findAllNodes(person);
        log.info("babySlice={}", babySlice);
        assertEquals(2, babySlice.size());
        assertEquals("Baby-0", ((Baby) babySlice.get(0)).name);
        assertEquals("Baby-1", ((Baby) babySlice.get(1)).name);

        // 联合POJO属性
        List<Object> selectedBabies = JsonPath.compile("$.babies[0,2]['name','age']").findAllNodes(person);
        log.info("selectedBabies={}", selectedBabies);
        assertEquals(4, selectedBabies.size());
        assertTrue(selectedBabies.contains("Baby-0"));
        assertTrue(selectedBabies.contains("Baby-2"));
        assertTrue(selectedBabies.contains(1));
        assertTrue(selectedBabies.contains(3));

        // 递归 + POJO
        List<Object> descendantNames = JsonPath.compile("$..name").findAllNodes(person);
        assertEquals(4, descendantNames.size()); // Alice + 3 babies
        assertTrue(descendantNames.contains("Alice"));
        assertTrue(descendantNames.contains("Baby-0"));
        assertTrue(descendantNames.contains("Baby-1"));
        assertTrue(descendantNames.contains("Baby-2"));
    }

    @Test
    public void testPerformanceAndLargeData() {
        // 构建一个较大的测试数据
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

        // 测试切片性能
        List<Object> slice = JsonPath.compile("$.items[10:20]").findAllNodes(largeData);
        assertEquals(10, slice.size());

        // 测试联合性能
        List<Object> union = JsonPath.compile("$.items[5,15,25,35]['id','value']").findAllNodes(largeData);
        assertEquals(8, union.size());

        // 测试递归性能
        List<Object> descendant = JsonPath.compile("$..deepValue").findAllNodes(largeData);
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




}
