package org.sjf4j.obnt;

import lombok.ToString;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;
import org.sjf4j.path.PathSyntax;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NodesWalkTest {

    @Test
    public void walksObjectValues() {
        JsonObject jo = JsonObject.fromJson("{\"a\":1,\"b\":{\"c\":2,\"d\":[3,4]},\"e\":\"test\"}");
        
        List<String> paths = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        Nodes.walk(jo, Nodes.WalkTarget.VALUE, Nodes.WalkOrder.TOP_DOWN, -1, (ps, value) -> {
            paths.add(PathSyntax.rootedPathExpr(ps));
            values.add(value);
            return true;
        });
        
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
    public void walksContainersBottomUp() {
        JsonObject jo = JsonObject.fromJson("{\"a\":1,\"b\":{\"c\":2}}");
        
        List<String> containerPaths = new ArrayList<>();
        
        Nodes.walk(jo, Nodes.WalkTarget.CONTAINER, Nodes.WalkOrder.BOTTOM_UP, -1,
                (ps, container) -> {
            containerPaths.add(ps.rootedPathExpr());
            assertNotNull(container);
            return true;
        });
        
        assertTrue(containerPaths.contains("$"));
        assertTrue(containerPaths.contains("$.b"));
    }

    @Test
    public void walksNestedArrays() {
        JsonArray ja = JsonArray.fromJson("[1,2,[3,4],{\"a\":5}]");
        
        AtomicInteger count = new AtomicInteger(0);
        List<String> paths = new ArrayList<>();
        
        Nodes.walk(ja, Nodes.WalkTarget.VALUE, Nodes.WalkOrder.TOP_DOWN, -1,
                (ps, value) -> {
            paths.add(PathSyntax.rootedPathExpr(ps));
            count.incrementAndGet();
            return true;
        });
        
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
    public void walksMaps() {
        Map<String, Object> map = new HashMap<>();
        map.put("a", 1);
        Map<String, Object> nested = new HashMap<>();
        nested.put("b", 2);
        map.put("nested", nested);
        
        List<String> paths = new ArrayList<>();
        Nodes.walk(map, Nodes.WalkTarget.VALUE, Nodes.WalkOrder.TOP_DOWN, -1, (ps, value) -> {
            paths.add(PathSyntax.rootedPathExpr(ps));
            return true;
        });
        
        assertTrue(paths.contains("$.a"));
        assertTrue(paths.contains("$.nested.b"));
    }

    @Test
    public void walksLists() {
        List<Object> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        List<Object> nested = new ArrayList<>();
        nested.add(3);
        list.add(nested);
        
        List<String> paths = new ArrayList<>();
        Nodes.walk(list, Nodes.WalkTarget.VALUE, Nodes.WalkOrder.TOP_DOWN, -1, (ps, value) -> {
            paths.add(PathSyntax.rootedPathExpr(ps));
            return true;
        });
        
        assertTrue(paths.contains("$[0]"));
        assertTrue(paths.contains("$[1]"));
        assertTrue(paths.contains("$[2][0]"));
    }

    @Test
    public void walksPrimitiveArrays() {
        int[] array = {1, 2, 3};
        
        List<String> paths = new ArrayList<>();
        Nodes.walk(array, (ps, value) -> {
            paths.add(PathSyntax.rootedPathExpr(ps));
            return true;
        });
        
        assertTrue(paths.contains("$[0]"));
        assertTrue(paths.contains("$[1]"));
        assertTrue(paths.contains("$[2]"));
    }

    @Test
    public void walksPrimitiveValues() {
        AtomicInteger count = new AtomicInteger(0);
        
        Nodes.walk("test", (ps, value) -> {
            count.incrementAndGet();
            assertEquals("test", value);
            return true;
        });
        
        assertEquals(1, count.get());
    }

    @Test
    public void walksNestedStructures() {
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
        Nodes.walk(jo, Nodes.WalkTarget.VALUE, Nodes.WalkOrder.TOP_DOWN, -1, (ps, value) -> {
            count.incrementAndGet();
            return true;
        });
        assertEquals(7, count.get());
    }


    // --------- Sample POJO ------------
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
    public void walksPojosInConfiguredOrder() {
        Person person = Sjf4j.global().fromJson(JSON_DATA, Person.class);

        List<String> values1 = new ArrayList<>();
        Nodes.walk(person, Nodes.WalkTarget.VALUE, Nodes.WalkOrder.TOP_DOWN, -1, (ps, node) -> {
            values1.add(ps.rootedPathExpr());
            return true;
        });
        assertTrue(values1.contains("$.babies[1].name"));

        List<String> values2 = new ArrayList<>();
        Nodes.walk(person, Nodes.WalkTarget.ANY, Nodes.WalkOrder.BOTTOM_UP, -1, (ps, node) -> {
            values2.add(PathSyntax.rootedPathExpr(ps));
            return true;
        });
        assertEquals(16, values2.size());
        assertEquals("$.name", values2.get(0));
        assertEquals("$", values2.get(15));
    }

}
