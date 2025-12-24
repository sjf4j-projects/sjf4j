package org.sjf4j;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
class JsonArrayTest {

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
        testRemove1();
        testParse1();
        testParse2();
        testParse3();
        testArray1();
        testArray2();
        testToList1();
        testCopy();
        testByPath1();
        testByPath2();
        testYaml1();
        testHashCodeEquals();
        testNegativeIndex();
        testSetAndAdd();
        testContainsIndex();
        testForEach();
        testEmptyArray();
        testMerge();
        testPrimitiveArrays();
        testEdgeCases();
    }

    public void testGetter1() {
        String json1 = "[12,34,[56,78],9,{\"a\":0}]";
        JsonArray ja = JsonArray.fromJson(json1);
        assertEquals(json1, ja.toJson());

        assertEquals(5, ja.size());
        assertEquals((short)12, ja.getShort(0));
        assertEquals(78f, ja.asJsonArray(2).getFloat(1));
        assertEquals(0, ja.asJsonObject(4).getFloat("a"));

        System.out.println(ja.toJson());
    }

    public void testRemove1() {
        JsonArray ja = new JsonArray();
        assertEquals("[]", ja.toJson());

        ja.add("higgs");
        assertEquals("[\"higgs\"]", ja.toJson());
        assertEquals("J[higgs]", ja.inspect());

        ja.add(18.8);
        ja.add(0, "jackson");
        ja.remove(2);
        assertEquals("[\"jackson\",\"higgs\"]", ja.toJson());

        JsonArray ja2 = new JsonArray();
        ja2.addAll("copy", "me", "yes?");
        ja.addAll(ja2);
        assertEquals("[\"jackson\",\"higgs\",\"copy\",\"me\",\"yes?\"]", ja.toJson());
    }

    public void testParse1() {
        JsonArray ja1 = new JsonArray();
        ja1.addAll("color", "red");

        JsonArray ja2 = JsonArray.fromJson("[\"color\",\"red\"]");

        JsonArray ja3 = new JsonArray();
        ja3.addAll("color", "red");

        assertEquals(ja1, ja2);
        assertEquals(ja1, ja3);
    }

    public void testParse2() {
//        JsonArray ja1 = new JsonArray("gaga", "haha", new String[]{"jiji", "kaka"});
        JsonArray ja1 = new JsonArray(
                new Object[]{"gaga", "haha", new JsonArray(new String[]{"jiji", "kaka"})});

        JsonArray ja2 = JsonArray.fromJson("[\"gaga\",\"haha\",[\"jiji\",\"kaka\"]]");
        assertEquals(ja1, ja2);
        assertEquals(ja1.toJson(), ja2.toJson());

        assertEquals("[\"jiji\",\"kaka\"]", ja1.getJsonArray(2).toJson());

        System.out.println(ja1.getJsonArray(2));
        System.out.println(ja1);
    }

    public void testParse3() {
        JsonArray ja1 = JsonArray.fromJson("[\"number\",5,\"duck\",{\"yell\":\"haha\",\"777\":888}]");
        System.out.println(ja1);
        assertEquals("[\"number\",5,\"duck\",{\"yell\":\"haha\",\"777\":888}]", ja1.toJson());

        ja1 = JsonArray.fromJson("[\"numb😃er\",5,\"duck\",[\"gaga\",\"ha我的锅ha\"]]");
        System.out.println(ja1);
        assertEquals("[\"numb😃er\",5,\"duck\",[\"gaga\",\"ha我的锅ha\"]]", ja1.toJson());

        ja1 = JsonArray.fromJson("[\"number\",5,null,[\"gaga\",\"haha\"],45,32]");
        System.out.println(ja1);
        assertNull(ja1.getNode(2));

        assertThrows(JsonException.class, () -> {
            JsonArray ja = JsonArray.fromJson("[\"number,5,null,[\"gaga\",\"haha\"],45,32]");
        });

        assertThrows(JsonException.class, () -> {
            JsonArray ja = JsonArray.fromJson("[\"number\",5,null,[\"gaga\",\"haha\"],45,32]");
            ja.getLong(0);
        });
    }

    @Test
    public void testArray1() {
        JsonArray a1 = JsonArray.fromJson("[2,3,4]");
//        List<Object> list = a1.toList();
        List<Integer> list1 = a1.toList(Integer.class);
        log.info("list1.type={}, list1={}", list1.getClass(), list1);
        assertEquals(3, list1.size());
        assertEquals(3, list1.get(1));

        List<Float> list2 = a1.toList(Float.class);
        log.info("list2.type={}, list2={}", list2.getClass(), list2);
        assertEquals(Float.class, list2.get(1).getClass());
        assertEquals(3.0f, list2.get(1));

        Float[] fs = a1.toArray(Float.class);
        assertEquals(3, fs.length);
        assertEquals(3.0f, fs[1]);
    }

    public void testArray2() {
        int[] ii = {1,2,3};
        JsonArray ja1 = new JsonArray(ii);
        System.out.println("ja1: " + ja1);
        assertEquals(3, ja1.size());
        assertEquals(3, ja1.getInteger(2));

        JsonArray ja2 = new JsonArray();
        ja2.addAll(new char[]{'s', '1', '2'});
        System.out.println("ja2: " + ja2);
        assertEquals(3, ja2.size());
        assertEquals("2", ja2.getString(2));
    }

    public void testCopy() {
        JsonArray a1 = JsonArray.fromJson("[2,3,[4,[5,6]]]");
        JsonArray a2 = a1.deepCopy();

        assertEquals(6, a2.asJsonArray(2).asJsonArray(1).getInteger(1));
        a1.asJsonArray(2).asJsonArray(1).set(1, 7);
        assertEquals(7, a1.asJsonArray(2).asJsonArray(1).getInteger(1));
        assertEquals(6, a2.asJsonArray(2).asJsonArray(1).getInteger(1));
    }

    public void testByPath1() {
        JsonArray ja1 = JsonArray.fromJson("[2,3,{}]");
        assertEquals(JsonArray.class, ja1.getNodeByPath("$").getClass());
        assertEquals((byte) 3, ja1.getByteByPath("$[1]"));

        ja1.ensurePutIfAbsentByPath("$[1]", 9);
        ja1.ensurePutByPath("$[2].a.b", "yes");
        log.info("ja1: {}", ja1);
        assertEquals("[2,3,{\"a\":{\"b\":\"yes\"}}]", ja1.toJson());
    }

    public void testByPath2() {
        JsonArray ja1 = JsonArray.fromJson("[2,3,{}]");
        ja1.ensurePutByPath("$[2]", new JsonArray());
        ja1.ensurePutByPath("$[2][0].a.b", "yes");
        ja1.ensurePutNonNullByPath("$[2][1]", null);
        log.info("ja1={}", ja1);
        assertEquals("[2,3,[{\"a\":{\"b\":\"yes\"}}]]", ja1.toJson());
    }

    @Test
    public void testYaml1() {
        String json1 = "[\"number\",5,null,[\"gaga\",\"haha\"],45,{\"aa\":\"bb\"}]";
        JsonArray ja1 = JsonArray.fromJson(json1);
        String ya1 = ja1.toYaml();
        log.info("ya1: \n{}", ya1);

        JsonArray ja2 = JsonArray.fromYaml(ya1);
        assertEquals(ja1, ja2);
    }

    // ========== 补充测试用例 ==========

    public void testHashCodeEquals() {
        JsonArray ja1 = JsonArray.fromJson("[1,2,\"test\"]");
        JsonArray ja2 = JsonArray.fromJson("[1,2,\"test\"]");
        JsonArray ja3 = JsonArray.fromJson("[1,2,\"test2\"]");
        
        assertEquals(ja1.hashCode(), ja2.hashCode());
        assertEquals(ja1, ja2);
        assertNotEquals(ja1, ja3);
        assertNotEquals(null, ja1);
        assertEquals(ja1, ja1); // 自反性
    }

    public void testNegativeIndex() {
        JsonArray ja = new JsonArray(new String[]{"a", "b", "c", "d"});
        
        // 负数索引：从末尾开始
        assertEquals("d", ja.getString(-1));
        assertEquals("c", ja.getString(-2));
        assertEquals("b", ja.getString(-3));
        assertEquals("a", ja.getString(-4));
        
        // 边界测试
        assertNull(ja.getString(-5)); // 超出范围
        assertNull(ja.getString(4));   // 超出范围
        
        // 使用负数索引设置
        ja.set(-1, "z");
        assertEquals("z", ja.getString(3));
        
        // 使用负数索引获取不同类型
        JsonArray ja2 = new JsonArray(new Object[]{1, 2, 3, 4});
        assertEquals(4, ja2.getInteger(-1));
        assertEquals(1.0, ja2.getDouble(-4));
    }

    public void testSetAndAdd() {
        JsonArray ja = new JsonArray(new Object[]{"a", "b", "c"});
        
        // 测试set
        ja.set(1, "x");
        assertEquals("x", ja.getString(1));
        
        // 测试add在末尾
        ja.add("d");
        assertEquals(4, ja.size());
        assertEquals("d", ja.getString(3));
        
        // 测试add在指定位置
        ja.add(1, "y");
        assertEquals(5, ja.size());
        assertEquals("y", ja.getString(1));
        assertEquals("x", ja.getString(2));
        
        // 测试边界情况
        assertThrows(JsonException.class, () -> ja.set(-10, "error"));
        assertThrows(JsonException.class, () -> ja.set(10, "error"));
        assertThrows(JsonException.class, () -> ja.add(-10, "error"));
        assertThrows(JsonException.class, () -> ja.add(10, "error"));
        
        // 测试add在末尾（索引等于size）
        ja.add(ja.size(), "end");
        assertEquals(6, ja.size());
        assertEquals("end", ja.getString(5));
    }

    public void testContainsIndex() {
        JsonArray ja = new JsonArray(new Object[]{"a", "b", "c"});
        
        assertTrue(ja.containsIndex(0));
        assertTrue(ja.containsIndex(1));
        assertTrue(ja.containsIndex(2));
        assertFalse(ja.containsIndex(3));
        
        // 负数索引
        assertTrue(ja.containsIndex(-1));
        assertTrue(ja.containsIndex(-2));
        assertTrue(ja.containsIndex(-3));
        assertFalse(ja.containsIndex(-4));
    }

    public void testForEach() {
        JsonArray ja = new JsonArray(new Object[]{"a", "b", "c"});
        List<Object> collected = new java.util.ArrayList<>();
        
        ja.forEach(e -> collected.add(e));
        assertEquals(3, collected.size());
        assertEquals("a", collected.get(0));
        assertEquals("b", collected.get(1));
        assertEquals("c", collected.get(2));
        
        // 测试BiConsumer版本
        List<String> indices = new java.util.ArrayList<>();
        ja.forEach((idx, val) -> {
            indices.add(idx + ":" + val);
        });
        assertEquals(3, indices.size());
        assertEquals("0:a", indices.get(0));
        assertEquals("1:b", indices.get(1));
        assertEquals("2:c", indices.get(2));
    }

    public void testEmptyArray() {
        JsonArray empty = new JsonArray();
        
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());
        assertNull(empty.getNode(0));
        assertNull(empty.getString(0));
        
        // 空数组的迭代
        empty.forEach(val -> fail("Should not iterate"));
        
        // 空数组的toList
        assertTrue(empty.isEmpty());
    }

    @Test
    public void testMerge() {
        JsonArray ja1 = JsonArray.fromJson("[1,2,{\"a\":\"b\"}]");
        JsonArray ja2 = JsonArray.fromJson("[3,4,{\"a\":\"c\",\"d\":\"e\"}]");
        
        ja1.merge(ja2);
        assertEquals(3, ja1.size());
        assertEquals(3, ja1.getInteger(0));
        assertEquals(4, ja1.getInteger(1));
        assertEquals("c", ja1.asJsonObject(2).getString("a"));
        assertEquals("e", ja1.asJsonObject(2).getString("d"));
        
        // 测试merge with copy
        JsonArray ja3 = JsonArray.fromJson("[{\"x\":1}]");
        JsonArray ja4 = JsonArray.fromJson("[{\"y\":2}]");
        ja3.mergeWithCopy(ja4);
        assertEquals(1, ja3.size());
        log.info("ja3={}", ja3.inspect());
        assertEquals(1, ja3.asJsonObject(0).getInteger("x"));
        assertEquals(2, ja3.asJsonObject(0).getInteger("y"));
    }

    public void testToList1() {
        JsonArray ja = new JsonArray(new Object[]{1, 2, "test", true});
        List<Object> list = ja.toList();
        
        assertEquals(4, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals("test", list.get(2));
        assertEquals(true, list.get(3));
    }

    public void testPrimitiveArrays() {
        // 测试boolean数组
        JsonArray ja1 = new JsonArray(new Object[]{true, false, true});
        assertEquals(3, ja1.size());
        assertTrue(ja1.getBoolean(0));
        assertFalse(ja1.getBoolean(1));
        
        // 测试byte数组
        JsonArray ja2 = new JsonArray(new Object[]{(byte)1, (byte)2, (byte)3});
        assertEquals(3, ja2.size());
        assertEquals((byte)1, ja2.getByte(0));
        
        // 测试short数组
        JsonArray ja3 = new JsonArray(new Object[]{(short)10, (short)20});
        assertEquals(2, ja3.size());
        assertEquals((short)10, ja3.getShort(0));
        
        // 测试char数组
        JsonArray ja4 = new JsonArray(new Object[]{'a', 'b', 'c'});
        assertEquals(3, ja4.size());
        assertEquals("a", ja4.getString(0));
        
        // 测试float数组
        JsonArray ja5 = new JsonArray(new Object[]{1.1f, 2.2f});
        assertEquals(2, ja5.size());
        assertEquals(1.1f, ja5.getFloat(0), 0.001f);
    }

    public void testEdgeCases() {
        // 测试null值
        JsonArray ja = new JsonArray();
        ja.add(null);
        ja.add("value");
        assertNull(ja.getNode(0));
        assertEquals("value", ja.getString(1));
        
        // 测试混合类型
        JsonArray ja2 = new JsonArray();
        ja2.add(1);
        ja2.add("string");
        ja2.add(true);
        ja2.add(new JsonObject("key", "value"));
        ja2.add(new JsonArray(new Object[]{1, 2}));
        assertEquals(5, ja2.size());
        
        // 测试非常大的数组
        JsonArray ja3 = new JsonArray();
        for (int i = 0; i < 1000; i++) {
            ja3.add(i);
        }
        assertEquals(1000, ja3.size());
        assertEquals(999, ja3.getInteger(999));
        
        // 测试getString的默认值
        JsonArray ja4 = new JsonArray(new Object[]{"a", null, "c"});
        assertEquals("default", ja4.getString(1, "default"));
        assertEquals("a", ja4.getString(0, "default"));
    }

    public static class MyArray1 extends JsonArray {
        private int a = 33;
        @Override
        public Class<?> elementType() {
            return int.class;
        }
    }

    public static class MyArray2 extends JsonArray {
        private int a = 33;
    }

    @Test
    public void testExtend1() {
        String json1 = "[1,2,{\"a\":\"b\"}]";
        assertThrows(Exception.class, () -> Sjf4j.fromJson(json1, MyArray1.class));
        MyArray2 my2 = Sjf4j.fromJson(json1, MyArray2.class);
        log.info("my2={}", my2);
        assertEquals(json1, my2.toJson());
    }

}