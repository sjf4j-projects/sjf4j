package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.TypeReference;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.NodeException;
import org.sjf4j.fixture.PojoPersonFixture.Person;
import org.sjf4j.path.PathSyntax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sjf4j.fixture.PojoPersonFixture.JSON_DATA;

class NodeWalkerTest {

    @Test
    public void walksObjectValues() {
        JsonObject jo = JsonObject.fromJson("{\"a\":1,\"b\":{\"c\":2,\"d\":[3,4]},\"e\":\"test\"}");
        
        List<String> paths = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        NodeWalker.walk(jo, NodeWalker.WalkTarget.VALUE, NodeWalker.WalkOrder.TOP_DOWN, -1, (ps, value) -> {
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
        
        NodeWalker.walk(jo, NodeWalker.WalkTarget.CONTAINER, NodeWalker.WalkOrder.BOTTOM_UP, -1,
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
        
        NodeWalker.walk(ja, NodeWalker.WalkTarget.VALUE, NodeWalker.WalkOrder.TOP_DOWN, -1,
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
        NodeWalker.walk(map, NodeWalker.WalkTarget.VALUE, NodeWalker.WalkOrder.TOP_DOWN, -1, (ps, value) -> {
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
        NodeWalker.walk(list, NodeWalker.WalkTarget.VALUE, NodeWalker.WalkOrder.TOP_DOWN, -1, (ps, value) -> {
            paths.add(PathSyntax.rootedPathExpr(ps));
            return true;
        });
        
        assertTrue(paths.contains("$[0]"));
        assertTrue(paths.contains("$[1]"));
        assertTrue(paths.contains("$[2][0]"));
    }

    @Test
    void linkedListVisitsIndexesAndShortCircuits() {
        List<Object> list = new LinkedList<Object>(Arrays.asList("first", null, "last")) {
            @Override
            public Object get(int index) {
                throw new AssertionError("indexed read on sequential list");
            }
        };
        List<String> entries = new ArrayList<>();
        NodeWalker.forEachArray(list, (index, value) -> entries.add(index + ":" + value));
        assertEquals(Arrays.asList("0:first", "1:null", "2:last"), entries);

        entries.clear();
        assertTrue(NodeWalker.anyMatchInArray(list, (index, value) -> {
            entries.add(index + ":" + value);
            return index == 1;
        }));
        assertEquals(Arrays.asList("0:first", "1:null"), entries);
        assertEquals(Arrays.asList("$[0]", "$[1]", "$[2]"),
                walkPaths(list, NodeWalker.WalkTarget.VALUE, -1));
    }

    @Test
    public void walksPrimitiveArrays() {
        int[] array = {1, 2, 3};
        
        List<String> paths = new ArrayList<>();
        NodeWalker.walk(array, (ps, value) -> {
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
        
        NodeWalker.walk("test", (ps, value) -> {
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
        NodeWalker.walk(jo, NodeWalker.WalkTarget.VALUE, NodeWalker.WalkOrder.TOP_DOWN, -1, (ps, value) -> {
            count.incrementAndGet();
            return true;
        });
        assertEquals(7, count.get());
    }


    @Test
    public void walksPojosInConfiguredOrder() {
        Person person = Sjf4j.global().fromJson(JSON_DATA, Person.class);

        List<String> values1 = new ArrayList<>();
        NodeWalker.walk(person, NodeWalker.WalkTarget.VALUE, NodeWalker.WalkOrder.TOP_DOWN, -1, (ps, node) -> {
            values1.add(ps.rootedPathExpr());
            return true;
        });
        assertTrue(values1.contains("$.babies[1].name"));

        List<String> values2 = new ArrayList<>();
        NodeWalker.walk(person, NodeWalker.WalkTarget.ANY, NodeWalker.WalkOrder.BOTTOM_UP, -1, (ps, node) -> {
            values2.add(PathSyntax.rootedPathExpr(ps));
            return true;
        });
        assertEquals(16, values2.size());
        assertEquals("$.name", values2.get(0));
        assertEquals("$", values2.get(15));
    }

    @Test
    void walkTargetsSelectOnlyMatchingShapes() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("text", "s");
        tree.put("number", 3);
        tree.put("boolean", true);
        tree.put("null", null);
        tree.put("unknown", new BoundValue("raw"));
        tree.put("array", Arrays.asList("nested"));

        assertEquals(Arrays.asList("$", "$.array"), walkPaths(tree, NodeWalker.WalkTarget.CONTAINER, -1));
        assertEquals(Arrays.asList("$"), walkPaths(tree, NodeWalker.WalkTarget.OBJECT, -1));
        assertEquals(Arrays.asList("$.array"), walkPaths(tree, NodeWalker.WalkTarget.ARRAY, -1));
        assertEquals(Arrays.asList("$.text", "$.array[0]"), walkPaths(tree, NodeWalker.WalkTarget.STRING, -1));
        assertEquals(Arrays.asList("$.number"), walkPaths(tree, NodeWalker.WalkTarget.NUMBER, -1));
        assertEquals(Arrays.asList("$.boolean"), walkPaths(tree, NodeWalker.WalkTarget.BOOLEAN, -1));
        assertEquals(Arrays.asList("$.null"), walkPaths(tree, NodeWalker.WalkTarget.NULL, -1));
        assertEquals(Arrays.asList("$.unknown"), walkPaths(tree, NodeWalker.WalkTarget.UNKNOWN, -1));
        assertEquals(Arrays.asList("$.text", "$.number", "$.boolean", "$.null", "$.unknown", "$.array[0]"),
                walkPaths(tree, NodeWalker.WalkTarget.VALUE, -1));
    }

    @Test
    void walkDepthAndTopDownPruningRespectBranches() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("skip", Arrays.asList(1, 2));
        tree.put("keep", JsonObject.of("child", 3));

        assertTrue(walkPaths(tree, NodeWalker.WalkTarget.ANY, 0).isEmpty());
        assertEquals(Arrays.asList("$"), walkPaths(tree, NodeWalker.WalkTarget.ANY, 1));
        assertEquals(Arrays.asList("$", "$.skip", "$.keep"),
                walkPaths(tree, NodeWalker.WalkTarget.ANY, 2));
        assertEquals(Arrays.asList("$", "$.skip", "$.skip[0]", "$.skip[1]", "$.keep", "$.keep.child"),
                walkPaths(tree, NodeWalker.WalkTarget.ANY, -1));

        List<String> visited = new ArrayList<>();
        NodeWalker.walk(tree, NodeWalker.WalkTarget.ANY, NodeWalker.WalkOrder.TOP_DOWN, -1,
                (path, node) -> {
                    String name = path.rootedPathExpr();
                    visited.add(name);
                    return !name.equals("$.skip");
                });
        assertEquals(Arrays.asList("$", "$.skip", "$.keep", "$.keep.child"), visited);

        visited.clear();
        NodeWalker.walk(tree, NodeWalker.WalkTarget.ANY, NodeWalker.WalkOrder.BOTTOM_UP, -1,
                (path, node) -> {
                    visited.add(path.rootedPathExpr());
                    return true;
                });
        assertEquals(Arrays.asList("$.skip[0]", "$.skip[1]", "$.skip", "$.keep.child", "$.keep", "$"),
                visited);
    }

    @Test
    void setIterationMatchingAndWalkUseIterationIndexes() {
        Set<Object> set = new LinkedHashSet<>(Arrays.asList("first", 7, false));
        List<String> entries = new ArrayList<>();
        NodeWalker.forEachArray(set, (index, value) -> entries.add(index + ":" + value));
        assertEquals(Arrays.asList("0:first", "1:7", "2:false"), entries);
        assertFalse(NodeWalker.anyMatchInArray(set, (index, value) -> false));
        assertTrue(NodeWalker.anyMatchInArray(set, (index, value) -> index == 1 && value.equals(7)));
        assertFalse(NodeWalker.anyMatchInArray(set, (index, value) -> index == 3));
        assertEquals(Arrays.asList("$[0]", "$[1]", "$[2]"),
                walkPaths(set, NodeWalker.WalkTarget.VALUE, -1));
        assertEquals(Arrays.asList("$"), walkPaths(set, NodeWalker.WalkTarget.ARRAY, -1));

        NodeWalker.Access access = new NodeWalker.Access();
        assertThrows(NodeException.class, () -> NodeWalker.getAccessInArray(set, 0, access));
        assertThrows(NodeException.class, () -> NodeWalker.putAccessInArray(set, null, 0, access));
        NodeWalker.putAccessInArray(set, null, null, access);
        assertTrue(access.puttable);
        assertNull(access.node);
    }

    @Test
    void objectAccessDistinguishesNullMissingAndPojoProperties() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("empty", null);
        NodeWalker.Access access = new NodeWalker.Access();
        NodeWalker.getAccessInObject(map, "empty", access);
        assertTrue(access.present);
        assertNull(access.node);
        NodeWalker.getAccessInObject(map, "missing", access);
        assertFalse(access.present);
        NodeWalker.putAccessInObject(map, new TypeReference<Map<String, Integer>>() {}.getType(),
                "empty", access);
        assertTrue(access.puttable);
        assertEquals(Integer.class, access.type);

        JsonObject json = JsonObject.of("empty", null);
        NodeWalker.getAccessInObject(json, "empty", access);
        assertTrue(access.present);
        NodeWalker.getAccessInObject(json, "missing", access);
        assertFalse(access.present);
        NodeWalker.putAccessInObject(json, null, "missing", access);
        assertTrue(access.puttable);
        assertEquals(Object.class, access.type);

        ReadWriteBean bean = new ReadWriteBean();
        bean.setWriteOnly("secret");
        NodeWalker.getAccessInObject(bean, "readOnly", access);
        assertTrue(access.present);
        assertEquals("visible", access.node);
        NodeWalker.putAccessInObject(bean, ReadWriteBean.class, "readOnly", access);
        assertFalse(access.puttable);
        assertEquals(String.class, access.type);
        NodeWalker.getAccessInObject(bean, "writeOnly", access);
        assertFalse(access.present);
        NodeWalker.putAccessInObject(bean, ReadWriteBean.class, "writeOnly", access);
        assertTrue(access.puttable);
        assertNull(access.node);
        assertEquals(String.class, access.type);
        NodeWalker.putAccessInObject(bean, ReadWriteBean.class, "missing", access);
        assertFalse(access.puttable);
        assertNull(access.node);

        DynamicObject dynamic = new DynamicObject();
        dynamic.put("extra", null);
        NodeWalker.getAccessInObject(dynamic, "extra", access);
        assertTrue(access.present);
        NodeWalker.putAccessInObject(dynamic, DynamicObject.class, "extra", access);
        assertTrue(access.puttable);
        assertEquals(Object.class, access.type);
    }

    @Test
    void arrayAccessNormalizesIndexesAndDistinguishesAppendFromMissing() {
        NodeWalker.Access access = new NodeWalker.Access();
        List<String> list = new ArrayList<>(Arrays.asList("one", null));
        NodeWalker.getAccessInArray(list, -1, access);
        assertTrue(access.present);
        assertNull(access.node);
        NodeWalker.getAccessInArray(list, -3, access);
        assertFalse(access.present);
        NodeWalker.putAccessInArray(list, new TypeReference<List<String>>() {}.getType(), 2, access);
        assertTrue(access.puttable);
        assertEquals(String.class, access.type);
        assertNull(access.node);
        NodeWalker.putAccessInArray(list, null, 3, access);
        assertFalse(access.puttable);
        NodeWalker.putAccessInArray(list, null, -2, access);
        assertTrue(access.puttable);
        assertEquals("one", access.node);

        JsonArray json = JsonArray.of("one", null);
        NodeWalker.getAccessInArray(json, -1, access);
        assertTrue(access.present);
        assertNull(access.node);
        NodeWalker.getAccessInArray(json, 2, access);
        assertFalse(access.present);
        NodeWalker.putAccessInArray(json, null, 2, access);
        assertTrue(access.puttable);
        NodeWalker.putAccessInArray(json, null, -3, access);
        assertFalse(access.puttable);

        int[] primitive = {4, 5};
        NodeWalker.getAccessInArray(primitive, -1, access);
        assertTrue(access.present);
        assertEquals(5, access.node);
        NodeWalker.putAccessInArray(primitive, int[].class, -1, access);
        assertEquals(int.class, access.type);
        assertEquals(5, access.node);
        assertTrue(access.puttable);
        NodeWalker.putAccessInArray(primitive, int[].class, null, access);
        assertFalse(access.puttable);
        NodeWalker.putAccessInArray(primitive, int[].class, 2, access);
        assertFalse(access.puttable);
    }

    @Test
    void structuralAccessRejectsWrongShapes() {
        NodeWalker.Access access = new NodeWalker.Access();
        assertThrows(NodeException.class, () -> NodeWalker.forEachObject(42, (key, value) -> {}));
        assertThrows(NodeException.class, () -> NodeWalker.anyMatchInObject(42, (key, value) -> true));
        assertThrows(NodeException.class, () -> NodeWalker.sizeInObject(42));
        assertThrows(NodeException.class, () -> NodeWalker.keySetInObject(42));
        assertThrows(NodeException.class, () -> NodeWalker.entrySetInObject(42));
        assertThrows(NodeException.class, () -> NodeWalker.getAccessInObject(42, "key", access));
        assertThrows(NodeException.class, () -> NodeWalker.putAccessInObject(42, null, "key", access));
        assertThrows(NodeException.class, () -> NodeWalker.forEachArray("scalar", (index, value) -> {}));
        assertThrows(NodeException.class, () -> NodeWalker.anyMatchInArray("scalar", (index, value) -> true));
        assertThrows(NodeException.class, () -> NodeWalker.sizeInArray("scalar"));
        assertThrows(NodeException.class, () -> NodeWalker.getAccessInArray("scalar", 0, access));
        assertThrows(NodeException.class, () -> NodeWalker.putAccessInArray("scalar", null, 0, access));
    }

    @Test
    void pojoAndContainerViewsExposeReadableMembersAndShortCircuit() {
        ReadWriteBean bean = new ReadWriteBean();
        bean.setWriteOnly("secret");
        List<String> keys = new ArrayList<>();
        NodeWalker.forEachObject(bean, (key, value) -> keys.add(key));
        assertEquals(Arrays.asList("readOnly"), keys);
        assertTrue(NodeWalker.anyMatchInObject(bean, (key, value) -> key.equals("readOnly")));
        assertFalse(NodeWalker.anyMatchInObject(bean, (key, value) -> key.equals("writeOnly")));
        assertEquals(1, NodeWalker.sizeInObject(bean));
        assertEquals("visible", NodeWalker.getInObject(bean, "readOnly"));
        assertNull(NodeWalker.getInObject(bean, "writeOnly"));
        assertTrue(NodeWalker.containsInObject(bean, "readOnly"));
        assertFalse(NodeWalker.containsInObject(bean, "writeOnly"));
        assertEquals(Arrays.asList("$.readOnly"), walkPaths(bean, NodeWalker.WalkTarget.VALUE, -1));

        List<Integer> numbers = Arrays.asList(1, 2, 3);
        AtomicInteger calls = new AtomicInteger();
        assertTrue(NodeWalker.anyMatchInArray(numbers, (index, value) -> {
            calls.incrementAndGet();
            return index == 1 && value.equals(2);
        }));
        assertEquals(2, calls.get());
        assertFalse(NodeWalker.anyMatchInArray(new int[]{1, 2}, (index, value) -> value.equals(3)));
        assertTrue(NodeWalker.anyMatchInArray(JsonArray.of(1, 2), (index, value) -> index == 1));
    }

    @Test
    void objectMutationRespectsMapJsonObjectAndPojoWriteRules() {
        Map<String, Object> map = new LinkedHashMap<>();
        assertNull(NodeWalker.putInObject(map, "key", 1));
        assertEquals(1, NodeWalker.putInObject(map, "key", 2));
        assertEquals(2, (int) NodeWalker.computeIfAbsentInObject(map, "key", key -> 3));
        assertEquals(2, NodeWalker.removeInObject(map, "key"));
        assertNull(NodeWalker.computeIfAbsentInObject(map, "missing", key -> null));
        assertFalse(map.containsKey("missing"));

        JsonObject json = JsonObject.of("key", 1);
        assertEquals(1, NodeWalker.putInObject(json, "key", 2));
        assertEquals(2, NodeWalker.removeInObject(json, "key"));
        assertEquals(3, (int) NodeWalker.computeIfAbsentInObject(json, "key", key -> 3));
        assertEquals(3, json.getNode("key"));

        ReadWriteBean bean = new ReadWriteBean();
        assertNull(NodeWalker.putInObject(bean, "writeOnly", "secret"));
        assertEquals("visible", NodeWalker.computeIfAbsentInObject(bean, "readOnly", key -> "new"));
        assertThrows(NodeException.class, () -> NodeWalker.putInObject(bean, "readOnly", "new"));
        assertThrows(NodeException.class, () -> NodeWalker.putInObject(bean, "missing", "new"));
        assertThrows(NodeException.class, () -> NodeWalker.removeInObject(bean, "readOnly"));
        assertThrows(NodeException.class, () -> NodeWalker.computeIfAbsentInObject(bean, "missing", key -> "new"));
    }

    @Test
    void arrayMutationRespectsAppendAndFixedSizeContainers() {
        List<Object> list = new ArrayList<>(Arrays.asList(1, 2));
        assertEquals(2, NodeWalker.setInArray(list, -1, 3));
        assertNull(NodeWalker.putInArray(list, 2, 4));
        NodeWalker.addInArray(list, 1, 5);
        assertEquals(Arrays.asList(1, 5, 3, 4), list);
        assertEquals(4, NodeWalker.removeInArray(list, -1));
        assertThrows(NodeException.class, () -> NodeWalker.setInArray(list, 3, 9));

        JsonArray json = JsonArray.of(1);
        assertNull(NodeWalker.putInArray(json, 1, 2));
        assertEquals(1, NodeWalker.setInArray(json, 0, 3));
        NodeWalker.addInArray(json, 4);
        assertEquals(2, NodeWalker.removeInArray(json, 1));
        assertEquals(Arrays.asList(3, 4), json.toList());

        int[] fixed = {1, 2};
        assertEquals(2, NodeWalker.putInArray(fixed, -1, 8));
        assertEquals(8, fixed[1]);
        assertThrows(NodeException.class, () -> NodeWalker.putInArray(fixed, 2, 9));
        assertThrows(NodeException.class, () -> NodeWalker.addInArray(fixed, 9));
        assertThrows(NodeException.class, () -> NodeWalker.removeInArray(fixed, 0));

        Set<Object> set = new LinkedHashSet<>();
        NodeWalker.addInArray(set, "item");
        assertTrue(set.contains("item"));
        assertThrows(NodeException.class, () -> NodeWalker.setInArray(set, 0, "new"));
        assertThrows(NodeException.class, () -> NodeWalker.addInArray(set, 0, "new"));
        assertThrows(NodeException.class, () -> NodeWalker.removeInArray(set, 0));
    }

    @Test
    void createMissingNodesUsesRequestedContainerShape() {
        assertTrue(NodeWalker.createObjectNode(null) instanceof Map);
        assertTrue(NodeWalker.createObjectNode(JsonObject.class) instanceof JsonObject);
        assertTrue(NodeWalker.createArrayNode(null) instanceof List);
        assertTrue(NodeWalker.createArrayNode(JsonArray.class) instanceof JsonArray);
        assertTrue(NodeWalker.createArrayNode(LinkedHashSet.class) instanceof LinkedHashSet);
        assertThrows(NodeException.class, () -> NodeWalker.createObjectNode(String.class));
        assertThrows(NodeException.class, () -> NodeWalker.createArrayNode(String.class));
    }

    private static List<String> walkPaths(Object root, NodeWalker.WalkTarget target, int depth) {
        List<String> paths = new ArrayList<>();
        NodeWalker.walk(root, target, NodeWalker.WalkOrder.TOP_DOWN, depth, (path, node) -> {
            paths.add(path.rootedPathExpr());
            return true;
        });
        return paths;
    }

    @NodeValue
    static class BoundValue {
        final String raw;

        BoundValue(String raw) {
            this.raw = raw;
        }

        @ValueToRaw
        String toRaw() {
            return raw;
        }

        @RawToValue
        static BoundValue fromRaw(String raw) {
            return new BoundValue(raw);
        }
    }

    static class ReadWriteBean {
        private final String readOnly = "visible";
        private String writeOnly;

        public String getReadOnly() {
            return readOnly;
        }

        public void setWriteOnly(String writeOnly) {
            this.writeOnly = writeOnly;
        }
    }

    static class DynamicObject extends JsonObject {}

}
