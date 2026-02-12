package org.sjf4j;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.node.NodeKind;
import org.sjf4j.supplier.MapSupplier;

import java.io.StringReader;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Slf4j
class JsonObjectTest {

    @TestFactory
    public Stream<DynamicTest> testWithJsonLib() {
        return Stream.of(
                DynamicTest.dynamicTest("Run with Simple JSON", () -> {
                    Sjf4jConfig.useSimpleJsonAsGlobal();
                    testAll();
                }),
                DynamicTest.dynamicTest("Run with Jackson", () -> {
                    Sjf4jConfig.useJacksonAsGlobal();
                    testAll();
                }),
                DynamicTest.dynamicTest("Run with Gson", () -> {
                    Sjf4jConfig.useGsonAsGlobal();
                    testAll();
                }),
                DynamicTest.dynamicTest("Run with Fastjson2", () -> {
                    Sjf4jConfig.useFastjson2AsGlobal();
                    testAll();
                })
        );
    }

    public void testAll() {
        testGetter1();
        testPutter1();
        testPutter2();
        testParse1();
        testParse2();
        testParse3();
        testMerge1();
        testByPath1();
        testByPath2();
        testByPath3();
        testBoolean1();
        testGeneric1();
        testDefaultValue1();
        testPutMap1();
        testOrder1();
        testNumber1();
        testNumber2();
        testYaml1();
        testPojo1();
        testHashCodeEquals();
        testClear();
        testPutNonNull();
        testComputeIfAbsent();
        testBuilder();
        testEntrySetKeySet();
        testRemoveByPath();
        testEdgeCases();
        testSupplier1();
        testNodeField1();
        testNodeField2();
        testSkipNode1();
    }

    final String JSON = "{\n" +
            "  \"id\": 1,\n" +
            "  \"name\": \"Alice\",\n" +
            "  \"active\": true,\n" +
            "  \"tags\": [\"java\", \"json\"],\n" +
            "  \"scores\": [95, 88.8, 0.5],\n" +
            "  \"user\": {\n" +
            "    \"role\": \"coder\",\n" +
            "    \"profile\": {\n" +
            "      \"level\": 7,\n" +
            "      \"values\": [1, \"two\", true, null, { \"x\": 3 }]\n" +
            "    }\n" +
            "  }\n" +
            "}";

    public void testGetter1() {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20],\"sex\":false}},\"sex\":true}";
        JsonObject jo = JsonObject.fromJson(json1);
        assertEquals(json1, jo.toJson());
        assertEquals(123, jo.getInteger("id"));
        assertEquals(123, jo.getBigInteger("id").intValue());
        assertEquals(Integer.class, jo.getNode("id").getClass());

        assertEquals("han", jo.getString("name"));
        assertEquals(123, jo.getNode("id"));
        assertEquals(123.0d, jo.getDouble("id"));

        assertEquals(175.3f, jo.getFloat("height"));
        assertEquals(175L, jo.getLong("height"));
        assertEquals((short) 175, jo.getShort("height"));

//        assertEquals(Double.class, jo.getObject("height").getClass()); // BigDecimal in Fastjson2
        assertInstanceOf(Number.class, jo.getNode("height"));

        assertThrows(JsonException.class, () -> jo.getString("height"));
        assertEquals("175.3", jo.getAsString("height"));

        assertEquals("good", jo.getJsonObject("friends").getString("jack"));
        assertEquals(false, jo.getJsonObject("friends").getJsonObject("rose").getBoolean("sex"));
        assertEquals(20, jo.getJsonObject("friends").getJsonObject("rose").getJsonArray("age").getInteger(1));

        assertNull(jo.getString("noexist1"));
        assertNull(jo.getJsonObject("noexist2"));

        assertEquals(5, jo.size());
        assertFalse(jo.containsKey("noexist3"));
        assertFalse(jo.isEmpty());

    }

    @Test
    public void testGetter2() {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20],\"sex\":false}},\"sex\":true}";
        JsonObject jo = JsonObject.fromJson(json1);
        int id = jo.get("id");
        String height = jo.getAs("height");
        assertEquals(123, id);
        assertEquals("175.3", height);
    }

    public void testPutter1() {
        JsonObject jo = new JsonObject();
        assertEquals("{}", jo.toJson());

        jo.put("name", "higgs");
        assertEquals("{\"name\":\"higgs\"}", jo.toJson());

        jo.put("age", 18.8);
        jo.remove("name");
        assertEquals("{\"age\":18.8}", jo.toJson());

        JsonObject jo2 = new JsonObject("copy", "me", "yes?", true);
        jo2.putAll(jo);
        assertEquals("{\"copy\":\"me\",\"yes?\":true,\"age\":18.8}", jo2.toJson());

        System.out.println(jo2.toJson());
    }

    public void testPutter2() {
        // putByPathIfAbsent
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        JsonObject jo1 = JsonObject.fromJson(json1);

        assert(jo1.hasNonNullByPath("$.friends.jack"));
        jo1.ensurePutIfAbsentByPath("$.friends.jack", "bad");
        assertEquals(JsonObject.fromJson(json1), jo1);

        jo1.ensurePutIfAbsentByPath("$.friends.mark", "bad");
        String json2 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]},\"mark\":\"bad\"},\"sex\":true}";
        assertEquals(JsonObject.fromJson(json2), jo1);
    }

    public void testParse1() {
        JsonObject jo1 = new JsonObject();
        jo1.put("color", "red");

        JsonObject jo2 = JsonObject.fromJson("{\"color\":\"red\"}");

        JsonObject jo3 = new JsonObject();
        jo3.put("color", "red");

        assertEquals(jo1, jo2);
        assertEquals(jo1, jo3);
    }

    public void testParse2() {
        Map<String, Object> map1 = new HashMap<>();
        map1.put("number", 5);
        map1.put("duck", new JsonArray(new String[]{"gaga", "haha"}));
        JsonObject jo1 = new JsonObject(map1);

        JsonObject jo2 = JsonObject.fromJson("{\"number\":5,\"duck\":[\"gaga\",\"haha\"]}");
        assertEquals(jo1, jo2);
        assertTrue(jo1.nodeEquals(jo2));
        assertEquals(jo1.toJson(), jo2.toJson());

        assertEquals("[\"gaga\",\"haha\"]", jo1.getJsonArray("duck").toJson());

        System.out.println(jo1.getJsonArray("duck"));
        System.out.println(jo1);
    }

    public void testParse3() {
        JsonObject jo1 = JsonObject.fromJson("{\"number\":5,\"duck\":[\"gaga\",\"haha\"]}");
        System.out.println(jo1);
        assertEquals("{\"number\":5,\"duck\":[\"gaga\",\"haha\"]}", jo1.toJson());

        jo1 = JsonObject.fromJson("{\"numb😃er\":5,\"duck\":[\"gaga\",\"ha我的锅ha\"]}");
        System.out.println(jo1);
        assertEquals("{\"numb😃er\":5,\"duck\":[\"gaga\",\"ha我的锅ha\"]}", jo1.toJson());

        jo1 = JsonObject.fromJson("{\"number\":5,\"duck\":[\"gaga\",\"haha\"],\"45\":32}");
        System.out.println(jo1);
        assertNull(jo1.getNode("46"));

        assertThrows(Exception.class, () -> {
            JsonObject jo = JsonObject.fromJson("{\"number\":5,\"duck\":[\"gaga\",\"haha\"],45:32}");
            jo.getDouble("duck");
        });

        assertThrows(JsonException.class, () -> {
            JsonObject.fromJson("{\"number\":5,\"duck\":[\"gaga,\"haha\"],45:32}");
        });
    }

    /**
     * Test merge, copyOf, deepMerge
     */
    public void testMerge1() {
        JsonObject jo1 = JsonObject.fromJson("{\"num\":5,\"duck\":[\"gaga\",\"haha\"],\"attr\":{\"aa\":\"bb\",\"cc\":\"dd\",\"ee\":{\"ff\":\"gg\"}}}");
        JsonObject jo2 = JsonObject.fromJson("{\"num\":\"6\",\"yo\":77,\"duck\":[\"haha\"],\"attr\":{\"aa\":88,\"kk\":[1,2],\"ee\":{\"ff\":\"uu\"}}}");
        jo1.mergeWithCopy(jo2);
        assertEquals("{\"num\":\"6\",\"duck\":[\"haha\",\"haha\"],\"attr\":{\"aa\":88,\"cc\":\"dd\",\"ee\":{\"ff\":\"uu\"},\"kk\":[1,2]},\"yo\":77}", jo1.toJson());
//        System.out.println(jo1);

        JsonObject jo3 = JsonObject.fromJson("{\"num\":\"6\",\"duck\":[\"haha\",\"haha\"],\"attr\":{\"aa\":88,\"cc\":\"dd\",\"ee\":{\"ff\":\"uu\"},\"kk\":[1,2]},\"yo\":77}");
        assertEquals(jo3, jo1);

        JsonObject jo4 = JsonObject.fromJson("{\"num\":5,\"duck\":[\"gaga\",\"haha\"],\"attr\":{\"aa\":\"bb\",\"cc\":\"dd\"}}");
        JsonObject jo5 = jo4.deepCopy();
        jo4.getJsonObject("attr").put("aa", "jj");
//        System.out.println(jo5);
        assertEquals("jj", jo4.getJsonObject("attr").getString("aa"));
        assertEquals("bb", jo5.getJsonObject("attr").getString("aa"));

        JsonObject attr = jo5.getAs("attr");
        assertEquals("bb", attr.get("aa"));

        JsonObject jo6 = JsonObject.fromJson("{\"num\":5,\"duck\":[{\"j\":\"gaga\"}],\"x\":{\"y\":{\"z\":9}}}");
        JsonObject jo7 = JsonObject.fromJson("{\"duck\":[2,3],\"x\":{\"y\":{\"h\":10}}}");
        jo6.mergeWithCopy(jo7);
//        jo6.putByPath("$.x.y.h", 11);
//        assertEquals("{\"num\":5,\"duck\":[2,3],\"x\":{\"y\":{\"z\":9,\"h\":11}}}", jo6.toJson());
//        assertEquals(10, (Integer) jo7.getByPath("$.x.y.h"));
//        System.out.println(jo6);
//        System.out.println(jo7);
    }

    /**
     * ByPath
     */
    public void testByPath1() {
        JsonObject jo1 = JsonObject.fromJson("{\"num\":5,\"duck\":[\"gaga\",\"dodo\"],\"attr\":{\"aa\":\"bb\"}," +
                "\"nested\":[{},{},{\"yes\":[{},{\"no\":5}]}]}");
        assertEquals("bb", jo1.getNodeByPath("$.attr.aa"));
        jo1.ensurePutByPath("$.x.y.z", 555);
        assertEquals(555, (Integer) jo1.getNodeByPath("$.x.y.z"));

        assertThrows(JsonException.class, () -> {
            jo1.ensurePutByPath("$.duck.yes", "no");
        });

        jo1.ensurePutByPath("$.num", "6");
        assertEquals("6", jo1.getString("num"));
        System.out.println(jo1);

        // array in path
        assertEquals("dodo", jo1.getStringByPath("$.duck[1]"));

        jo1.ensurePutByPath("$.duck[1]", "xixi");
        assertEquals("xixi", jo1.getStringByPath("$.duck[1]"));
        System.out.println(jo1.toJson());

        assertEquals(5, jo1.getIntegerByPath("$.nested[2].yes[1].no"));

        assertNull(jo1.getStringByPath("$.duck[5]"));
        assertEquals("jiji", jo1.getStringByPath("$.duck[5]", "jiji"));
    }

    public void testByPath2() {
        JsonObject jo1 = JsonObject.fromJson("{\"isYes\":false,\"isNo\":true, \"ss.ss\":[1]}");
        assertNull(jo1.getIntegerByPath("$.ss\\.ss[0]"));
        assertEquals(1, jo1.getIntegerByPath("$['ss.ss'][0]"));

        jo1.ensurePutByPath("$.query['idea.fqmn']", "::bad::good");
        assertEquals("::bad::good", jo1.getStringByPath("$.query['idea.fqmn']"));
        assertEquals("::bad::good", jo1.getJsonObject("query").getString("idea.fqmn"));
        System.out.println("jo1: " + jo1);
    }

    public void testByPath3() {
        JsonObject jo1 = new JsonObject();
        assertThrows(JsonException.class, () -> jo1.ensurePutByPath("$.a.b[1].c", "444"));

        JsonObject jo2 = new JsonObject();
        jo2.ensurePutByPath("$.a.b", new JsonArray());
        assertThrows(JsonException.class, () -> jo2.ensurePutByPath("$.a.b[1].c", "444"));

        JsonObject jo3 = new JsonObject();
        jo3.ensurePutByPath("$.a.b", new JsonArray(new Object[]{0, new JsonObject("d", "99")}));
        jo3.ensurePutByPath("$.a.b[1].c", "444");
        assertEquals("444", jo3.getStringByPath("$.a.b[1].c"));
        //FIXME: this is a bug!
    }

    public void testByPath4() {
        JsonObject jo1 = JsonObject.fromJson(JSON);
        assertThrows(JsonException.class, () -> jo1.ensurePutByPath("$.a.b[1].c", "444"));

        JsonObject jo2 = new JsonObject();
        jo2.ensurePutByPath("$.a.b", new JsonArray());
        assertThrows(JsonException.class, () -> jo2.ensurePutByPath("$.a.b[1].c", "444"));

        JsonObject jo3 = new JsonObject();
        jo3.ensurePutByPath("$.a.b", new JsonArray(new Object[]{0, new JsonObject("d", "99")}));
        jo3.ensurePutByPath("$.a.b[1].c", "444");
        assertEquals("444", jo3.getStringByPath("$.a.b[1].c"));
        //FIXME: this is a bug!
    }

    public void testBoolean1() {
        // Boolean
        JsonObject jo1 = JsonObject.fromJson("{\"isYes\":false,\"isNo\":true, \"ss\":1}");
        assertFalse(jo1.getBoolean("isYes"));
        assertTrue(jo1.getBoolean("isNo"));
        assertTrue(jo1.getBoolean("notExist", true));
    }

    public static class MyObject extends JsonObject {
        public MyObject() {
            super();
        }
//        public MyObject(JsonObject target) {
//            super(target);
//        }
    }

//    public void testCast1() {
//        // generics
//        JsonObject jo1 = JsonObject.fromJson("{\"num\":5,\"attr\":{\"aa\":\"bb\"}}");
//
//        MyObject obj = jo1.getJsonObject("attr").cast(MyObject.class);
//        System.out.println("MyObject: " + obj);
//        assertInstanceOf(JsonObject.class, obj);
//        assertEquals("bb", obj.getString("aa"));
//    }

    public void testGeneric1() {
        // T get(key, T... reified)
        JsonObject jo1 = JsonObject.fromJson("{\"num\":5,\"attr\":{\"aa\":\"bb\"}}");

        int num = jo1.get("num");
        System.out.println("num: " + num);
        assertEquals(5, num);

        JsonObject attr = jo1.getAs("attr");
        System.out.println("attr: " + attr);
        assertNotNull(attr);
        assertEquals(1, attr.size());

//        float f = jo1.getFloat("num");
        float f = jo1.get("num");
        System.out.println("f: " + f);
        assertEquals(5.0, f);

        Object obj = jo1.get("attr");
        System.out.println("obj: " + obj);
//        assertInstanceOf(JsonObject.class, obj);
        assertTrue(JsonType.of(obj).isObject());

        Object obj2 = jo1.get("attr2");
        System.out.println("obj2: " + obj2);
        assertNull(obj2);

        BigInteger big = new BigInteger(jo1.getAsString("num"));
        System.out.println("big: " + big);
        assertEquals(BigInteger.valueOf(5), big);
    }

    public void testDefaultValue1() {
        JsonObject jo1 = JsonObject.fromJson("{\"num\":5,\"attr\":{\"aa\":\"bb\"}}");
        assertEquals(5, jo1.getInteger("num", 6));
        assertEquals(6, jo1.getInteger("num2", 6));
        assertEquals("5", jo1.getAsString("num", "6"));
        assertEquals("6", jo1.getAsString("num2", "6"));

//        assertEquals("bb", jo1.getStringByPath("$.attr.aa", "cc"));
//        assertEquals("cc", jo1.getStringByPath("$.attr.aa2", "cc"));

        assertEquals(new JsonObject("aa", "bb"), jo1.getJsonObject("attr", new JsonObject()));
        assertEquals(new JsonObject(), jo1.getJsonObject("attr2", new JsonObject()));
    }


    public void testPutMap1() {
        Map<String, Object> map1 = new HashMap<>();
        map1.put("s1", "yesyes");
        map1.put("bool2", false);
        map1.put("int3", 99);
        map1.put("double4", 99.999);

        List<String> list5 = new java.util.ArrayList<>();
        list5.add("l1");
        list5.add("l2");
        list5.add("l3");
        map1.put("list5", list5);

        Map<String, String> map6 = new HashMap<>();
        map6.put("k1", "v1");
        map6.put("k2", "v2");
        map1.put("map6", map6);

        map1.put("jo7", new JsonObject("man", "bad"));

        JsonObject jo1 = new JsonObject(map1);
        System.out.println("jo1: " + jo1.toJson());
//        assertEquals("l2", jo1.getStringByPath("$.list5[1]"));
//        assertEquals("v2", jo1.getStringByPath("$.map6.k2"));
    }

    public void testOrder1() {
        String json1 = "{\"s1\":\"haha\",\"i2\":123,\"f3\":99.9,\"b4\":true,\"s5\":\"00\"}";
        JsonObject jo1 = JsonObject.fromJson(json1);
        System.out.println("jo1.keys: " + jo1.keySet());
        assertEquals("[s1, i2, f3, b4, s5]", jo1.keySet().toString());
    }

    public void testNumber1() {
        String json1 = "{\"big\":9999999}";
        JsonObject jo1 = JsonObject.fromJson(json1);
        System.out.println("jo1: " + jo1);
        assertEquals(Integer.class, jo1.getNode("big").getClass());
        assertEquals(json1, jo1.toJson());

        String json2 = "{\"big\":9999999999999999}";
        JsonObject jo2 = JsonObject.fromJson(json2);
        System.out.println("jo2: " + jo2);
        assertEquals(Long.class, jo2.getNode("big").getClass());
        assertEquals(json2, jo2.toJson());

        String json3 = "{\"big\":999999999999999999999999999999999999999999999}";
        JsonObject jo3 = JsonObject.fromJson(json3);
        System.out.println("jo3: " + jo3);
        assertEquals(BigInteger.class, jo3.getNode("big").getClass());
        assertEquals(json3, jo3.toJson());

        /// Only gson
//        String big = new String(new char[101]).replace('\0', '9');
//        String json4 = "{\"big\":" + big + "}";
//        assertThrows(JsonException.class, () -> JsonObject.fromJson(json4));
    }

    public void testNumber2() {
        String json1 = "{\"big\":1e200}";
        JsonObject jo1 = JsonObject.fromJson(json1);
        System.out.println("jo1: " + jo1);
        assertEquals(1e200, jo1.getDouble("big"));
    }

    @Test
    public void testYaml1() {

        String json1 = "{\"s1\":\"haha\",\"i2\":null,\"f3\":99.9,\"b4\":true,\"s\\\"5\":\"00\"}";
        JsonObject jo1 = (JsonObject) new Fastjson2JsonFacade().readNode(
                new StringReader(json1), JsonObject.class);
//        JsonObject jo1 = JsonObject.fromJson(json1);
        String ya1 = jo1.toYaml();
        log.info("ya1: \n{}", ya1);

        JsonObject jo2 = JsonObject.fromYaml(ya1);
        log.info("jo1={}", jo1.inspect());
        log.info("jo2={}", jo2.inspect());
        assertEquals(jo1, jo2);
    }

    public static class Address {
        public String city;
        public String street;
    }

    public static class Person {
        public String name;
        public Address address;
    }

    @Test
    public void testPojo1() {
        JsonObject jo = new JsonObject(
                "name", "Bob",
                "address", new JsonObject(
                        "city", "New York",
                        "street", "5th Ave"));
        Person p1 = jo.toPojo(Person.class);
        assertEquals("Bob", p1.name);
        assertEquals("New York", p1.address.city);
        assertEquals("5th Ave", p1.address.street);

        JsonObject back = JsonObject.fromNode(p1);
        assertEquals("Bob", back.getString("name"));
    }

    // ========== test cases ==========

    public void testHashCodeEquals() {
        JsonObject jo1 = JsonObject.fromJson("{\"a\":1,\"b\":\"test\"}");
        JsonObject jo2 = JsonObject.fromJson("{\"a\":1,\"b\":\"test\"}");
        JsonObject jo3 = JsonObject.fromJson("{\"a\":1,\"b\":\"test2\"}");
        
        assertEquals(jo1.hashCode(), jo2.hashCode());
        assertEquals(jo1, jo2);
        assertNotEquals(jo1, jo3);
        assertNotEquals(jo1, null);
        assertEquals(jo1, jo1); // 自反性
    }

    public void testClear() {
        JsonObject jo = new JsonObject("a", 1, "b", "test");
        assertFalse(jo.isEmpty());
        assertEquals(2, jo.size());
        
        jo.clear();
        assertTrue(jo.isEmpty());
        assertEquals(0, jo.size());
        assertFalse(jo.containsKey("a"));
    }

    public void testPutNonNull() {
        JsonObject jo = new JsonObject();
        
        jo.putNonNull("a", "value");
        assertEquals("value", jo.getString("a"));
        
        jo.putNonNull("b", null);
        assertFalse(jo.containsKey("b"));
        
        jo.putNonNull("c", 0);
        assertEquals(0, jo.getInteger("c"));
    }

    public void testComputeIfAbsent() {
        JsonObject jo = new JsonObject();
        
        JsonObject nested = jo.computeIfAbsent("nested", k -> new JsonObject());
        assertNotNull(nested);
        assertEquals(nested, jo.getJsonObject("nested"));
        
        JsonObject nested2 = jo.computeIfAbsent("nested", k -> new JsonObject());
        assertEquals(nested, nested2); // 应该返回同一个对象
        
        JsonArray array = jo.computeIfAbsent("array", k -> new JsonArray());
        assertNotNull(array);
        assertEquals(array, jo.getJsonArray("array"));
        
        JsonArray array2 = jo.computeIfAbsent("array", k -> new JsonArray());
        assertEquals(array, array2);
    }

    public void testBuilder() {
        JsonObject jo = JsonObject.builder()
                .put("name", "Alice")
                .put("age", 25)
                .putNonNull("email", "alice@example.com")
                .putIfAbsent("name", "Bob") // 已存在，不会覆盖
                .put("status", true)
                .build();
        
        assertEquals("Alice", jo.getString("name")); // 没有被覆盖
        assertEquals(25, jo.getInteger("age"));
        assertEquals("alice@example.com", jo.getString("email"));
        assertTrue(jo.getBoolean("status"));
        
        // 测试路径操作
        JsonObject jo2 = JsonObject.builder()
                .putByPath("$.user.name", "Bob")
                .putNonNullByPath("$.user.age", 30)
                .putIfAbsentByPath("$.user.email", "bob@example.com")
                .build();
        
        assertEquals("Bob", jo2.getStringByPath("$.user.name"));
        assertEquals(30, jo2.getIntegerByPath("$.user.age"));
        assertEquals("bob@example.com", jo2.getStringByPath("$.user.email"));
    }

    public void testEntrySetKeySet() {
        JsonObject jo = new JsonObject("a", 1, "b", "test", "c", true);
        
        assertEquals(3, jo.keySet().size());
        assertTrue(jo.keySet().contains("a"));
        assertTrue(jo.keySet().contains("b"));
        assertTrue(jo.keySet().contains("c"));
        
        assertEquals(3, jo.entrySet().size());
        jo.entrySet().forEach(entry -> {
            assertTrue(jo.containsKey(entry.getKey()));
            assertNotNull(entry.getValue());
        });
        
        // 测试keySet的顺序
        List<String> keys = new java.util.ArrayList<>(jo.keySet());
        assertEquals("a", keys.get(0));
        assertEquals("b", keys.get(1));
        assertEquals("c", keys.get(2));
    }

    public void testRemoveByPath() {
        JsonObject jo = JsonObject.fromJson("{\"a\":{\"b\":{\"c\":123}},\"array\":[1,2,3]}");
        
        assertTrue(jo.hasNonNullByPath("$.a.b.c"));
        jo.removeByPath("$.a.b.c");
        assertFalse(jo.hasNonNullByPath("$.a.b.c"));
        assertTrue(jo.hasNonNullByPath("$.a.b")); // 父对象还在
        
        assertTrue(jo.hasNonNullByPath("$.array[1]"));
        jo.removeByPath("$.array[1]");
        assertEquals(2, jo.getJsonArray("array").size());
        assertEquals(1, jo.getJsonArray("array").getInteger(0));
        assertEquals(3, jo.getJsonArray("array").getInteger(1));
    }

    public void testEdgeCases() {
        // 测试空对象
        JsonObject empty = new JsonObject();
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());
        assertNull(empty.getNode("nonexist"));
        
        // 测试null值
        JsonObject jo = new JsonObject();
        jo.put("nullKey", null);
        assertTrue(jo.containsKey("nullKey"));
        assertNull(jo.getNode("nullKey"));
        
        // 测试特殊字符键
        jo.put("key.with.dots", "value1");
        jo.put("key-with-dashes", "value2");
        jo.put("key_with_underscores", "value3");
        assertEquals("value1", jo.getString("key.with.dots"));
        assertEquals("value2", jo.getString("key-with-dashes"));
        assertEquals("value3", jo.getString("key_with_underscores"));
        
        // 测试空字符串键
        jo.put("", "emptyKey");
        assertEquals("emptyKey", jo.getString(""));
        
        // 测试非常大的数字
        jo.put("bigInt", new BigInteger("999999999999999999999999999"));
        assertInstanceOf(BigInteger.class, jo.getNode("bigInt"));
        
        // 测试嵌套深度
        JsonObject nested = new JsonObject();
        for (int i = 0; i < 10; i++) {
            JsonObject next = new JsonObject();
            nested.put("level" + i, next);
            nested = next;
        }
        nested.put("final", "value");
        assertNotNull(nested);
    }

    public void testSupplier1() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global()).mapSupplier(MapSupplier.TreeMapSupplier).build());
        JsonObject jo1 = new JsonObject("c", "cc", "b", "bb", "a", "aa");
        log.info("jo1={}", jo1);
        assertEquals("{\"a\":\"aa\",\"b\":\"bb\",\"c\":\"cc\"}", jo1.toJson());

        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global()).mapSupplier(MapSupplier.LinkedHashMapSupplier).build());
        JsonObject jo2 = new JsonObject("c", "cc", "b", "bb", "a", "aa");
        log.info("jo2={}", jo2);
        assertEquals("{\"c\":\"cc\",\"b\":\"bb\",\"a\":\"aa\"}", jo2.toJson());
        assertEquals(jo1, jo2);
    }


    public static class BookField extends JsonObject {
        @NodeProperty("no")
        private String yes;
        private double height;
        private transient int transientHeight;
    }

    public void testNodeField1() {
        String json1 = "{\"no\":\"good\",\"height\":175.5,\"transientHeight\":189.9}";
        BookField jo1 = Sjf4j.fromJson(json1, BookField.class);
        log.info("jo1={}", jo1.inspect());
        assertEquals("good", jo1.yes);
        assertEquals("good", jo1.getString("no"));
        assertNull(jo1.getString("yes"));
        assertEquals(175.5, jo1.height);
        assertEquals(0, jo1.transientHeight);

        String json2 = Sjf4j.toJsonString(jo1);
        log.info("json2={}", json2);
        assertEquals(json1, json2);
    }

    public static class Note {
        @NodeProperty("no")
        private String yes;
        public String getYes() {return yes;}
    }

    public void testNodeField2() {
        Note note1 = new Note();
        note1.yes = "gaga";
        String json1 = Sjf4j.toJsonString(note1);
        log.info("json1={}", json1);
    }

    public void testSkipNode1() {
        String json =
                "{\n" +
                "  \"id\": 7,\n" +
                "  \"skipObj\": {\n" +
                "    \"x\": [true, false, null, {\"deep\": \"v, }\"}],\n" +
                "    \"y\": 2\n" +
                "  },\n" +
                "  \"skipArr\": [1,2,{\"a\":[3,4]}],\n" +
                "  \"skipStr\": \"wa,w[]{}a\",\n" +
                "  \"skipNumber\": -334455,\n" +
                "  \"skipBoolean\": false,\n" +
                "  \"skipNull\": null,\n" +
                "  \"name\": \"Jack\"\n" +
                "}";
        Person pojo = Sjf4j.fromJson(json, Person.class);
        log.info("pojo={}", pojo);
        assertEquals("Jack", pojo.name);
    }

}