package org.sjf4j;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.sjf4j.exception.JsonException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
        return Stream.of(DynamicTest.dynamicTest("Run with default global", this::testAll));
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
        testOfFactory();
        testWrapSemantics();
        testEmptyArray();
        testMerge();
        testPrimitiveArrays();
        testEdgeCases();
        testSupplier1();
    }

    public void testGetter1() {
        String json1 = "[12,34,[56,78],9,{\"a\":0}]";
        JsonArray ja = JsonArray.fromJson(json1);
        assertEquals(json1, ja.toJson());

        assertEquals(5, ja.size());
        assertEquals((short)12, ja.getShort(0));
        assertEquals(78f, ja.getJsonArray(2).getFloat(1));
        assertEquals(0, ja.getJsonObject(4).getFloat("a"));

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
        JsonArray ja1 = JsonArray.of(
                "gaga", "haha", JsonArray.of("jiji", "kaka"));

        JsonArray ja2 = JsonArray.fromJson("[\"gaga\",\"haha\",[\"jiji\",\"kaka\"]]");
        assertNotEquals(ja1, ja2);
        assertTrue(ja1.nodeEquals(ja2));
        assertEquals(ja1.toJson(), ja2.toJson());

        assertEquals("[\"jiji\",\"kaka\"]", ja1.getJsonArray(2).toJson());

        System.out.println(ja1.getJsonArray(2));
        System.out.println(ja1);
    }

    public void testParse3() {
        JsonArray ja1 = JsonArray.fromJson("[\"number\",5,\"duck\",{\"yell\":\"haha\",\"777\":888}]");
        System.out.println(ja1);
        assertEquals("[\"number\",5,\"duck\",{\"yell\":\"haha\",\"777\":888}]", ja1.toJson());

        ja1 = JsonArray.fromJson("[\"numb😃er\",5,\"duck\",[\"gaga\",\"haMyWokha\"]]");
        System.out.println(ja1);
        assertEquals("[\"numb😃er\",5,\"duck\",[\"gaga\",\"haMyWokha\"]]", ja1.toJson());

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

    public void testOfFactory() {
        JsonArray ja = JsonArray.of("gaga", "haha", JsonArray.of("jiji", "kaka"));
        assertEquals("[\"gaga\",\"haha\",[\"jiji\",\"kaka\"]]", ja.toJson());

        assertEquals(JsonArray.of(1, 2, 3), new JsonArray(new int[]{1, 2, 3}));
    }

    public void testWrapSemantics() {
        JsonArray src = JsonArray.of(1, 2);
        JsonArray wrapped = new JsonArray(src);
        src.add(3);
        assertEquals(3, wrapped.size());

        int[] ints = new int[]{1, 2};
        JsonArray copied = new JsonArray(ints);
        ints[0] = 9;
        assertEquals(1, copied.getInt(0));
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
        assertEquals(3, ja1.getInt(2));

        JsonArray ja2 = new JsonArray();
        ja2.addAll(new char[]{'s', '1', '2'});
        System.out.println("ja2: " + ja2);
        assertEquals(3, ja2.size());
        assertEquals("2", ja2.getString(2));
    }

    public void testCopy() {
        JsonArray a1 = JsonArray.fromJson("[2,3,[4,[5,6]]]");
        JsonArray a2 = a1.deepCopy();

        assertEquals(6, a2.getJsonArray(2).getJsonArray(1).getInt(1));
        a1.getJsonArray(2).getJsonArray(1).set(1, 7);
        assertEquals(7, a1.getJsonArray(2).getJsonArray(1).getInt(1));
        assertEquals(6, a2.getJsonArray(2).getJsonArray(1).getInt(1));
    }

    public void testSupplier1() {
        JsonArray ja = JsonArray.of("c", "b", "a");
        assertInstanceOf(ArrayList.class, ja.nodeList);
        assertInstanceOf(ArrayList.class, ja.toList());
        assertInstanceOf(LinkedHashSet.class, ja.toSet());
        assertEquals("[\"c\",\"b\",\"a\"]", ja.toJson());
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
        log.info("ja1: {}", ja1);
        log.info("ja2: {}", ja2);

        assertEquals(ja1, ja2);
    }

    // ========== test case by ai ==========

    public void testHashCodeEquals() {
        JsonArray ja1 = JsonArray.fromJson("[1,2,\"test\"]");
        JsonArray ja2 = JsonArray.fromJson("[1,2,\"test\"]");
        JsonArray ja3 = JsonArray.fromJson("[1,2,\"test2\"]");
        
        assertEquals(ja1.hashCode(), ja2.hashCode());
        assertEquals(ja1, ja2);
        assertNotEquals(ja1, ja3);
        assertNotEquals(null, ja1);
        assertEquals(ja1, ja1); // reflexive
    }

    public void testNegativeIndex() {
        JsonArray ja = JsonArray.of("a", "b", "c", "d");
        
        // negative index: from the end
        assertEquals("d", ja.getString(-1));
        assertEquals("c", ja.getString(-2));
        assertEquals("b", ja.getString(-3));
        assertEquals("a", ja.getString(-4));
        
        // boundary test
        assertNull(ja.getString(-5)); // out of range
        assertNull(ja.getString(4));   // out of range
        
        // set using negative index
        ja.set(-1, "z");
        assertEquals("z", ja.getString(3));
        
        // get different types using negative index
        JsonArray ja2 = JsonArray.of(1, 2, 3, 4);
        assertEquals(4, ja2.getInt(-1));
        assertEquals(1.0, ja2.getDouble(-4));
    }

    public void testSetAndAdd() {
        JsonArray ja = JsonArray.of("a", "b", "c");
        
        // test set
        ja.set(1, "x");
        assertEquals("x", ja.getString(1));
        
        // test add at end
        ja.add("d");
        assertEquals(4, ja.size());
        assertEquals("d", ja.getString(3));
        
        // test add at specific position
        ja.add(1, "y");
        assertEquals(5, ja.size());
        assertEquals("y", ja.getString(1));
        assertEquals("x", ja.getString(2));
        
        // test boundary cases
        assertThrows(JsonException.class, () -> ja.set(-10, "error"));
        assertThrows(JsonException.class, () -> ja.set(10, "error"));
        assertThrows(JsonException.class, () -> ja.add(-10, "error"));
        assertThrows(JsonException.class, () -> ja.add(10, "error"));
        
        // test add at end (index == size)
        ja.add(ja.size(), "end");
        assertEquals(6, ja.size());
        assertEquals("end", ja.getString(5));
    }

    public void testContainsIndex() {
        JsonArray ja = JsonArray.of("a", "b", "c");
        
        assertTrue(ja.containsIndex(0));
        assertTrue(ja.containsIndex(1));
        assertTrue(ja.containsIndex(2));
        assertFalse(ja.containsIndex(3));
        
        // negative index
        assertTrue(ja.containsIndex(-1));
        assertTrue(ja.containsIndex(-2));
        assertTrue(ja.containsIndex(-3));
        assertFalse(ja.containsIndex(-4));
    }

    public void testForEach() {
        JsonArray ja = JsonArray.of("a", "b", "c");
        List<Object> collected = new java.util.ArrayList<>();
        
        ja.forEach(e -> collected.add(e));
        assertEquals(3, collected.size());
        assertEquals("a", collected.get(0));
        assertEquals("b", collected.get(1));
        assertEquals("c", collected.get(2));
        
        // test BiConsumer variant
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
        
        // iterate empty array
        empty.forEach(val -> fail("Should not iterate"));
        
        // toList on empty array
        assertTrue(empty.isEmpty());
    }

    @Test
    public void testMerge() {
        JsonArray ja1 = JsonArray.fromJson("[1,2,{\"a\":\"b\"}]");
        JsonArray ja2 = JsonArray.fromJson("[3,4,{\"a\":\"c\",\"d\":\"e\"}]");
        
        ja1.merge(ja2);
        assertEquals(3, ja1.size());
        assertEquals(3, ja1.getInt(0));
        assertEquals(4, ja1.getInt(1));
        assertEquals("c", ja1.getJsonObject(2).getString("a"));
        assertEquals("e", ja1.getJsonObject(2).getString("d"));
        
        // test merge with copy
        JsonArray ja3 = JsonArray.fromJson("[{\"x\":1}]");
        JsonArray ja4 = JsonArray.fromJson("[{\"y\":2}]");
        ja3.mergeWithCopy(ja4);
        assertEquals(1, ja3.size());
        log.info("ja3={}", ja3.inspect());
        assertEquals(1, ja3.getJsonObject(0).getInt("x"));
        assertEquals(2, ja3.getJsonObject(0).getInt("y"));
    }

    public void testToList1() {
        JsonArray ja = JsonArray.of(1, 2, "test", true);
        List<Object> list = ja.toList();
        
        assertEquals(4, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals("test", list.get(2));
        assertEquals(true, list.get(3));
    }

    public void testPrimitiveArrays() {
        // test boolean array
        JsonArray ja1 = JsonArray.of(true, false, true);
        assertEquals(3, ja1.size());
        assertTrue(ja1.getBoolean(0));
        assertFalse(ja1.getBoolean(1));
        
        // test byte array
        JsonArray ja2 = JsonArray.of((byte) 1, (byte) 2, (byte) 3);
        assertEquals(3, ja2.size());
        assertEquals((byte)1, ja2.getByte(0));
        
        // test short array
        JsonArray ja3 = JsonArray.of((short) 10, (short) 20);
        assertEquals(2, ja3.size());
        assertEquals((short)10, ja3.getShort(0));
        
        // test char array
        JsonArray ja4 = JsonArray.of('a', 'b', 'c');
        assertEquals(3, ja4.size());
        assertEquals("a", ja4.getString(0));
        
        // test float array
        JsonArray ja5 = JsonArray.of(1.1f, 2.2f);
        assertEquals(2, ja5.size());
        assertEquals(1.1f, ja5.getFloat(0), 0.001f);
    }

    public void testEdgeCases() {
        // test null value
        JsonArray ja = new JsonArray();
        ja.add(null);
        ja.add("value");
        assertNull(ja.getNode(0));
        assertEquals("value", ja.getString(1));
        
        // test mixed types
        JsonArray ja2 = new JsonArray();
        ja2.add(1);
        ja2.add("string");
        ja2.add(true);
        ja2.add(JsonObject.of("key", "value"));
        ja2.add(JsonArray.of(1, 2));
        assertEquals(5, ja2.size());
        
        // test very large array
        JsonArray ja3 = new JsonArray();
        for (int i = 0; i < 1000; i++) {
            ja3.add(i);
        }
        assertEquals(1000, ja3.size());
        assertEquals(999, ja3.getInt(999));
        
        // test default value of getString
        JsonArray ja4 = JsonArray.of("a", null, "c");
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
        assertThrows(Exception.class, () -> Sjf4j.global().fromJson(json1, MyArray1.class));
        MyArray2 my2 = Sjf4j.global().fromJson(json1, MyArray2.class);
        log.info("my2={}", my2);
        assertEquals(json1, my2.toJson());
    }

}
