package org.sjf4j;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
class JsonArrayTest {

    @TestFactory
    public Stream<DynamicTest> testWithJsonLib() {
        return Stream.of(
                DynamicTest.dynamicTest("Run with Jackson", () -> {
                    FacadeFactory.usingJacksonAsDefault();
                    testAll();
                }),
                DynamicTest.dynamicTest("Run with Gson", () -> {
                    FacadeFactory.usingGsonAsDefault();
                    testAll();
                }),
                DynamicTest.dynamicTest("Run with Fastjson2", () -> {
                    FacadeFactory.usingFastjson2AsDefault();
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
        testCopy();
        testByPath1();
        testByPath2();
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
        assertEquals("[]", ja.toString());

        ja.add("higgs");
        assertEquals("[\"higgs\"]", ja.toString());

        ja.add(18.8);
        ja.add(0, "jackson");
        ja.remove(2);
        assertEquals("[\"jackson\",\"higgs\"]", ja.toString());

        JsonArray ja2 = new JsonArray();
        ja2.addAll("copy", "me", "yes?");
        ja.addAll(ja2);
        assertEquals("[\"jackson\",\"higgs\",\"copy\",\"me\",\"yes?\"]", ja.toString());
        System.out.println(ja.toJson());
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
        JsonArray ja1 = new JsonArray("gaga", "haha", new JsonArray("jiji", "kaka"));

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
        assertNull(ja1.getObject(2));

        assertThrows(JsonException.class, () -> {
            JsonArray ja = JsonArray.fromJson("[\"number,5,null,[\"gaga\",\"haha\"],45,32]");
        });

        assertThrows(JsonException.class, () -> {
            JsonArray ja = JsonArray.fromJson("[\"number\",5,null,[\"gaga\",\"haha\"],45,32]");
            ja.getLong(0);
        });
    }

    public void testArray1() {
        JsonArray a1 = JsonArray.fromJson("[2,3,4]");
        List<Object> list = a1.toList();
        System.out.println("arr: " + list);
        assertEquals(3, list.size());
        assertEquals(3, list.get(1));
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

        assertEquals(6, a2.getJsonArray(2).getJsonArray(1).getInteger(1));
        a1.getJsonArray(2).getJsonArray(1).set(1, 7);
        assertEquals(7, a1.getJsonArray(2).getJsonArray(1).getInteger(1));
        assertEquals(6, a2.getJsonArray(2).getJsonArray(1).getInteger(1));
    }

    public void testByPath1() {
        JsonArray ja1 = JsonArray.fromJson("[2,3,{}]");
        assertEquals("[2,3,{}]", ja1.getObjectByPath("$").toString());
        assertEquals((byte) 3, ja1.getByteByPath("$[1]"));

        ja1.putByPathIfAbsent("$[1]", 9);
        ja1.putByPath("$[2].a.b", "yes");
        log.info("ja1: {}", ja1);
        assertEquals("[2,3,{\"a\":{\"b\":\"yes\"}}]", ja1.toJson());
    }

    public void testByPath2() {
        JsonArray ja1 = JsonArray.fromJson("[2,3,{}]");
        ja1.putByPath("$[2]", new JsonArray());
        ja1.putByPath("$[2][0].a.b", "yes");
        ja1.putByPathIfNonNull("$[2][1]", null);
        log.info("ja1={}", ja1);
        assertEquals("[2,3,[{\"a\":{\"b\":\"yes\"}}]]", ja1.toJson());
    }

}