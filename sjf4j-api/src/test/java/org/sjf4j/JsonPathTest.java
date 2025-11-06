package org.sjf4j;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
        assertEquals("$.a[*].b.c.d", path2.toString());

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
        assertEquals("$.a.b[0].c", path1.toString());

        String s2 = "/a~0/0/b~1'/c~/d e";
        JsonPath path2 = JsonPath.compile(s2);
        log.info("path2: {}", path2);
        assertEquals("$['a~'][0]['b/\\'']['c~']['d e']", path2.toString());
    }

    @Test
    public void testFindOne1() {
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

        assertEquals("B", JsonPath.compile("$.book[1]['title']").findOne(jo1));
        assertEquals("B", JsonPath.compile("/book/1/title").findOne(jo1));
        assertEquals(10, JsonPath.compile("$.book[0].price").findOne(jo1));
        assertEquals("classic", JsonPath.compile("$.book[0].tags[0]").findOne(jo1));
        assertEquals(new JsonArray(), JsonPath.compile("$.emptyArray").findOne(jo1));
        assertEquals(new JsonObject(), JsonPath.compile("$['emptyObject']").findOne(jo1));
        assertNull(JsonPath.compile("$.nullValue").findOne(jo1));
        assertEquals("v1", JsonPath.compile("$['weird.keys']['key with spaces']").findOne(jo1));
        assertEquals("v1", JsonPath.compile("/weird.keys/key with spaces").findOne(jo1));

        log.info("$: {}", JsonPath.compile("$").findOne(jo1));
        assertEquals(JsonObject.class, JsonPath.compile("$").findOne(jo1).getClass());

        assertThrows(JsonException.class, () -> JsonPath.compile("$.book[*].price").findOne(jo1));

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

        JsonArray result1 = JsonPath.compile("$.book[*].title").findAll(jo1);
        log.info("result1: {}", result1);
        assertEquals(3, result1.size());
        assertEquals("B", result1.getString(1));

        JsonArray result2 = JsonPath.compile("$.book[*].tags").findAll(jo1);
        log.info("result2: {}", result2);
        assertEquals(3, result2.size());
        assertEquals("classic", result2.getJsonArray(0).getString(0));
        assertEquals(new JsonArray(), result2.getJsonArray(1));
        assertNull(result2.getJsonArray(2));

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

        Object container1 = JsonPath.compile("$.book[0].box[0].gg")._autoCreateContainers(jo1);
        log.info("container1={} jo1={}", container1, jo1);
        assertEquals(JsonObject.class, container1.getClass());
        assertEquals(1, jo1.getJsonArrayByPath("$.book[0].box").size());

        Object container2 = JsonPath.compile("$.book[2].tags['gg mm'][0]")._autoCreateContainers(jo1);
        log.info("container2={} jo1={}", container2, jo1);
        assertEquals(JsonArray.class, container2.getClass());
        assertEquals(0, jo1.getJsonArrayByPath("$.book[2].tags['gg mm']").size());
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
        assertEquals(2, new JsonPath("$.names[1]").getLong(jo1));
        assertEquals("ll", new JsonPath("$.map.lis[0].kk").getString(jo1));
        assertEquals(ArrayList.class, new JsonPath("$.map.lis").getObject(jo1).getClass());
    }

    public void testPutAndRemove() {
        JsonObject jo = JsonObject.fromJson("{\"a\":{\"b\":123},\"array\":[1,2,3]}");
        
        // 测试put
        JsonPath path1 = JsonPath.compile("$.a.c");
        path1.put(jo, "newValue");
        assertEquals("newValue", jo.getStringByPath("$.a.c"));
        
        // 测试put数组索引
        JsonPath path2 = JsonPath.compile("$.array[1]");
        path2.put(jo, 999);
        assertEquals(999, jo.getIntegerByPath("$.array[1]"));
        
        // 测试putNonNull
        JsonPath path3 = JsonPath.compile("$.a.d");
        path3.putNonNull(jo, "value");
        assertEquals("value", jo.getStringByPath("$.a.d"));
        path3.putNonNull(jo, null);
        assertEquals("value", jo.getStringByPath("$.a.d")); // 不应该被覆盖
        
        // 测试putIfAbsent
        JsonPath path4 = JsonPath.compile("$.a.e");
        path4.putIfAbsentOrNull(jo, "first");
        assertEquals("first", jo.getStringByPath("$.a.e"));
        path4.putIfAbsentOrNull(jo, "second");
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
        assertNull(JsonPath.compile("$.nonexist").getObject(jo));
        assertEquals("default", JsonPath.compile("$.nonexist").getString(jo, "default"));
        
        // 测试通配符在findOne中
        assertThrows(JsonException.class, () -> {
            JsonPath.compile("$.a[*]").findOne(jo);
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
        assertNull(JsonPath.compile("$[10]").getObject(ja));
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
        assertEquals("reference", JsonPath.compile("$.store.book[0].category").findOne(jo));
        assertEquals(12.99, JsonPath.compile("$.store.book[1].price").findOne(jo));
        assertEquals("red", JsonPath.compile("$.store.bicycle.color").findOne(jo));
        
        // 使用通配符查找所有
        JsonArray authors = JsonPath.compile("$.store.book[*].author").findAll(jo);
        assertEquals(2, authors.size());
        assertEquals("Nigel Rees", authors.getString(0));
        assertEquals("Evelyn Waugh", authors.getString(1));
        
        // 使用通配符查找所有价格
        JsonArray prices = JsonPath.compile("$.store.book[*].price").findAll(jo);
        assertEquals(2, prices.size());
        assertEquals(8.95, prices.getDouble(0));
        assertEquals(12.99, prices.getDouble(1));
    }

    @Test
    public void testEdgeCases() {
        // 测试根路径
        JsonObject jo = JsonObject.fromJson("{\"a\":1}");
        Object root = JsonPath.compile("$").findOne(jo);
        assertEquals(jo, root);
        
        // 测试空对象
        JsonObject empty = new JsonObject();
        assertFalse(JsonPath.compile("$.a").hasNonNull(empty));
        assertNull(JsonPath.compile("$.a").findOne(empty));
        
        // 测试空数组
        JsonArray emptyArray = new JsonArray();
        assertFalse(JsonPath.compile("$[0]").hasNonNull(emptyArray));
        assertNull(JsonPath.compile("$[0]").findOne(emptyArray));
        
        // 测试null值
        JsonObject withNull = JsonObject.fromJson("{\"a\":null}");
        assertFalse(JsonPath.compile("$.a").hasNonNull(withNull));
        assertNull(JsonPath.compile("$.a").findOne(withNull));
    }


}
