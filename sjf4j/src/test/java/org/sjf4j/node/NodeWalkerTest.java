package org.sjf4j.node;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.path.Paths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class NodeWalkerTest {

    @Test
    public void testWalkValues() {
        JsonObject jo = JsonObject.fromJson("{\"a\":1,\"b\":{\"c\":2,\"d\":[3,4]},\"e\":\"test\"}");
        
        List<String> paths = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        NodeWalker.walk(jo, NodeWalker.Target.VALUE, (ps, value) -> {
            paths.add(Paths.toPathExpr(ps));
            values.add(value);
            return NodeWalker.Control.CONTINUE;
        });
        
        log.info("Paths: {}", paths);
        log.info("Values: {}", values);

        assertFalse(paths.isEmpty());
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
        
        NodeWalker.walk(jo, NodeWalker.Target.CONTAINER, NodeWalker.Order.BOTTOM_UP,
                (ps, container) -> {
            containerPaths.add(Paths.toPathExpr(ps));
            assertNotNull(container);
            return NodeWalker.Control.CONTINUE;
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
        
        NodeWalker.walk(ja, NodeWalker.Target.VALUE, (ps, value) -> {
            paths.add(Paths.toPathExpr(ps));
            count.incrementAndGet();
            return NodeWalker.Control.CONTINUE;
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
        NodeWalker.walk(map, NodeWalker.Target.VALUE, (ps, value) -> {
            paths.add(Paths.toPathExpr(ps));
            return NodeWalker.Control.CONTINUE;
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
        NodeWalker.walk(list, NodeWalker.Target.VALUE, (ps, value) -> {
            paths.add(Paths.toPathExpr(ps));
            return NodeWalker.Control.CONTINUE;
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
        NodeWalker.walk(array, NodeWalker.Target.VALUE, (ps, value) -> {
            paths.add(Paths.toPathExpr(ps));
            return NodeWalker.Control.CONTINUE;
        });
        
        log.info("Array object paths: {}", paths);
        assertTrue(paths.contains("$[0]"));
        assertTrue(paths.contains("$[1]"));
        assertTrue(paths.contains("$[2]"));
    }

    @Test
    public void testWalkPrimitive() {
        AtomicInteger count = new AtomicInteger(0);
        
        NodeWalker.walk("test", NodeWalker.Target.VALUE, (ps, value) -> {
            count.incrementAndGet();
            assertEquals("test", value);
            return NodeWalker.Control.CONTINUE;
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
        NodeWalker.walk(jo, NodeWalker.Target.VALUE, (ps, value) -> {
            count.incrementAndGet();
            log.debug("PathSegment: {}, Value: {}", ps, value);
            return NodeWalker.Control.CONTINUE;
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

        List<String> values1 = new ArrayList<>();
        NodeWalker.walk(person, NodeWalker.Target.VALUE, (ps, node) -> {
            log.info("walk1 ps={}, node={}", ps, node);
            values1.add(Paths.toPathExpr(ps));
            return NodeWalker.Control.CONTINUE;
        });
        assertTrue(values1.contains("$.babies[1].name"));

        List<String> values2 = new ArrayList<>();
        NodeWalker.walk(person, NodeWalker.Target.ANY, NodeWalker.Order.BOTTOM_UP, (ps, node) -> {
            log.info("walk2 ps={}, node={}", ps, node);
            values2.add(Paths.toPathExpr(ps));
            return NodeWalker.Control.CONTINUE;
        });
        assertEquals(16, values2.size());
        assertEquals("$.name", values2.get(0));
        assertEquals("$", values2.get(15));
    }

}

