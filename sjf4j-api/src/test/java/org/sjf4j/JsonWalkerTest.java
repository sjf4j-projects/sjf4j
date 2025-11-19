package org.sjf4j;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class JsonWalkerTest {

    @Test
    public void testWalkValues() {
        JsonObject jo = JsonObject.fromJson("{\"a\":1,\"b\":{\"c\":2,\"d\":[3,4]},\"e\":\"test\"}");
        
        List<String> paths = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        JsonWalker.walkValues(jo, (path, value) -> {
            paths.add(path.toString());
            values.add(value);
        });
        
        log.info("Paths: {}", paths);
        log.info("Values: {}", values);
        
        assertTrue(paths.size() > 0);
        assertFalse(paths.contains("$"));
        assertTrue(paths.contains("$.a"));
        assertFalse(paths.contains("$.b"));
        assertTrue(paths.contains("$.b.c"));
        assertTrue(paths.contains("$.b.d[0]"));
        assertTrue(paths.contains("$.b.d[1]"));
        assertTrue(paths.contains("$.e"));
        
        assertEquals(paths.size(), values.size());
    }

    @Test
    public void testWalkContainersBottomUp() {
        JsonObject jo = JsonObject.fromJson("{\"a\":1,\"b\":{\"c\":2}}");
        
        List<String> containerPaths = new ArrayList<>();
        
        JsonWalker.walkContainersBottomUp(jo, (path, container) -> {
            containerPaths.add(path.toString());
            assertNotNull(container);
        });
        
        log.info("Container paths: {}", containerPaths);
        
        // 应该包含所有容器，按自底向上顺序
        assertTrue(containerPaths.contains("$"));
        assertTrue(containerPaths.contains("$.b"));
    }

    @Test
    public void testWalkArray() {
        JsonArray ja = JsonArray.fromJson("[1,2,[3,4],{\"a\":5}]");
        
        AtomicInteger count = new AtomicInteger(0);
        List<String> paths = new ArrayList<>();
        
        JsonWalker.walkValues(ja, (path, value) -> {
            paths.add(path.toString());
            count.incrementAndGet();
        });
        
        log.info("Array paths: {}", paths);
        assertTrue(count.get() > 0);
        assertFalse(paths.contains("$"));
        assertTrue(paths.contains("$[0]"));
        assertTrue(paths.contains("$[1]"));
        assertFalse(paths.contains("$[2]"));
        assertTrue(paths.contains("$[2][0]"));
        assertTrue(paths.contains("$[2][1]"));
        assertTrue(paths.contains("$[3].a"));
    }

    @Test
    public void testWalkMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("a", 1);
        Map<String, Object> nested = new HashMap<>();
        nested.put("b", 2);
        map.put("nested", nested);
        
        List<String> paths = new ArrayList<>();
        JsonWalker.walkValues(map, (path, value) -> {
            paths.add(path.toString());
        });
        
        log.info("Map paths: {}", paths);
        assertTrue(paths.contains("$.a"));
        assertTrue(paths.contains("$.nested.b"));
    }

    @Test
    public void testWalkList() {
        List<Object> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        List<Object> nested = new ArrayList<>();
        nested.add(3);
        list.add(nested);
        
        List<String> paths = new ArrayList<>();
        JsonWalker.walkValues(list, (path, value) -> {
            paths.add(path.toString());
        });
        
        log.info("List paths: {}", paths);
        assertTrue(paths.contains("$[0]"));
        assertTrue(paths.contains("$[1]"));
        assertTrue(paths.contains("$[2][0]"));
    }

    @Test
    public void testWalkArrayObject() {
        int[] array = {1, 2, 3};
        
        List<String> paths = new ArrayList<>();
        JsonWalker.walkValues(array, (path, value) -> {
            paths.add(path.toString());
        });
        
        log.info("Array object paths: {}", paths);
        assertTrue(paths.contains("$[0]"));
        assertTrue(paths.contains("$[1]"));
        assertTrue(paths.contains("$[2]"));
    }

    @Test
    public void testWalkPrimitive() {
        AtomicInteger count = new AtomicInteger(0);
        
        JsonWalker.walkValues("test", (path, value) -> {
            count.incrementAndGet();
            assertEquals("test", value);
        });
        
        assertEquals(1, count.get());
    }

    @Test
    public void testWalkNestedStructure() {
        JsonObject jo = JsonObject.fromJson("{\n" +
                "  \"users\": [\n" +
                "    {\"name\": \"Alice\", \"age\": 25},\n" +
                "    {\"name\": \"Bob\", \"age\": 30}\n" +
                "  ],\n" +
                "  \"metadata\": {\n" +
                "    \"count\": 2,\n" +
                "    \"tags\": [\"active\", \"verified\"]\n" +
                "  }\n" +
                "}");
        
        AtomicInteger count = new AtomicInteger(0);
        JsonWalker.walkValues(jo, (path, value) -> {
            count.incrementAndGet();
            log.debug("Path: {}, Value: {}", path, value);
        });
        
        log.info("Total values walked: {}", count.get());
        assertEquals(7, count.get());
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
    public void testWalkPojo1() {
        Person person = Sjf4j.fromJson(JSON_DATA, Person.class);
        log.info("person={}", person);

        List<String> values = new ArrayList<>();
        JsonWalker.walkValues(person, (path, node) -> {
            log.info("walk path={}, node={}", path, node);
            values.add(path.toString());
        });
        assertTrue(values.contains("$.babies[1].name"));
    }

}

