package org.sjf4j.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodesCoverageEdgeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    enum SwitchState { ON, OFF }

    static class Bean {
        private String name;
        private int count;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    static class GetterOnlyBean {
        private final String name = "locked";

        public String getName() {
            return name;
        }
    }

    static class DynamicBean extends JsonObject {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static class DynamicArray extends JsonArray {}

    static final class UnknownValue {
        private final String value;

        UnknownValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof UnknownValue)) {
                return false;
            }
            return value.equals(((UnknownValue) obj).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    @Test
    void testScalarConversionsCoverEnumCharBooleanAndFacadePaths() {
        assertNull(Nodes.toEnum(null, SwitchState.class));
        assertEquals(SwitchState.ON, Nodes.toEnum(SwitchState.ON, SwitchState.class));
        assertEquals(SwitchState.OFF, Nodes.toEnum("OFF", SwitchState.class));

        assertNull(Nodes.asEnum(null, SwitchState.class));
        assertEquals(SwitchState.ON, Nodes.asEnum(SwitchState.ON, SwitchState.class));
        assertEquals(SwitchState.OFF, Nodes.asEnum("OFF", SwitchState.class));
        assertNull(Nodes.asEnum("missing", SwitchState.class));

        assertEquals("ON", Nodes.toString(SwitchState.ON));
        assertEquals("1", Nodes.asString(MAPPER.getNodeFactory().numberNode(1)));

        assertNull(Nodes.toChar(null));
        assertEquals(Character.valueOf('z'), Nodes.toChar('z'));
        assertEquals(Character.valueOf('a'), Nodes.toChar("abc"));
        assertNull(Nodes.toChar(""));
        assertEquals(Character.valueOf('b'), Nodes.asChar("bar"));
        assertNull(Nodes.asChar(""));

        assertEquals(1, Nodes.asNumber(true));
        assertEquals(0, Nodes.asNumber(false));
        assertEquals(1, Nodes.asNumber(SwitchState.OFF));
        assertEquals(12, Nodes.asNumber(TextNode.valueOf("12")).intValue());
        assertNull(Nodes.asNumber(new Object()));

        assertTrue(Nodes.toBoolean(BooleanNode.TRUE));
        assertTrue(Nodes.asBoolean("yes"));
        assertTrue(Nodes.asBoolean("on"));
        assertTrue(Nodes.asBoolean("1"));
        assertFalse(Nodes.asBoolean("no"));
        assertFalse(Nodes.asBoolean("off"));
        assertFalse(Nodes.asBoolean("0"));
        assertTrue(Nodes.asBoolean(1));
        assertFalse(Nodes.asBoolean(0));
        assertTrue(Nodes.asBoolean(BooleanNode.TRUE));
        assertNull(Nodes.asBoolean(TextNode.valueOf("maybe")));
        assertNull(Nodes.asBoolean(2));
        assertNull(Nodes.asBoolean(new Object()));
    }

    @Test
    void testCollectionConversionsCoverPojoPrimitiveSetAndFacadeShapes() {
        Bean bean = new Bean();
        bean.setName("han");
        bean.setCount(2);

        ObjectNode objectNode = MAPPER.createObjectNode().put("name", "han").put("count", 2);
        ArrayNode arrayNode = MAPPER.createArrayNode().add("x").add(2);

        Map<String, Object> pojoMap = Nodes.toMap(bean);
        assertEquals("han", pojoMap.get("name"));
        assertEquals(2, pojoMap.get("count"));
        assertEquals(2, Nodes.toMap(objectNode).size());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "han");
        assertSame(map, Nodes.toMap(map, Map.class, Object.class));

        List<Object> list = new ArrayList<>();
        list.add("a");
        assertSame(list, Nodes.toList(list, List.class, Object.class));
        assertEquals(Arrays.asList(1, 2), Nodes.toList(new int[]{1, 2}));
        assertEquals(Arrays.asList("a", "b"), Nodes.toList(new LinkedHashSet<>(Arrays.asList("a", "b"))));
        List<Object> facadeList = Nodes.toList(arrayNode);
        assertEquals(2, facadeList.size());
        assertEquals("x", Nodes.asString(facadeList.get(0)));
        assertEquals(2, Nodes.toNumber(facadeList.get(1)).intValue());

        assertArrayEquals(new Object[]{1, 2}, Nodes.toArray(new int[]{1, 2}));
        assertArrayEquals(new Object[]{"a", "b"}, Nodes.toArray(new String[]{"a", "b"}));
        assertArrayEquals(new Object[]{"a", "b"}, Nodes.toArray(new LinkedHashSet<>(Arrays.asList("a", "b"))));
        Object[] facadeArray = Nodes.toArray(arrayNode);
        assertEquals(2, facadeArray.length);
        assertEquals("x", Nodes.asString(facadeArray[0]));
        assertEquals(2, Nodes.toNumber(facadeArray[1]).intValue());
        assertArrayEquals(new Object[]{1, 2}, Nodes.toArray(new int[]{1, 2}, Object.class));
        assertArrayEquals(new String[]{"a", "b"}, Nodes.toArray(Arrays.asList("a", "b"), String.class));

        assertEquals(new LinkedHashSet<>(Arrays.asList(1, 2)), Nodes.toSet(new int[]{1, 2}));
        assertEquals(new LinkedHashSet<>(Arrays.asList("a", "b")), Nodes.toSet(Arrays.asList("a", "b")));
        assertEquals(2, Nodes.toSet(arrayNode).size());

        Set<Object> set = new LinkedHashSet<>();
        set.add("x");
        assertSame(set, Nodes.toSet(set, Set.class, Object.class));
    }

    @Test
    void testObjectAndArrayHelpersCoverAccessMutationAndFacadePaths() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("name", "han");
        map.put("count", 2);

        JsonObject jsonObject = JsonObject.of("name", "han", "count", 2);

        Bean bean = new Bean();
        bean.setName("han");
        bean.setCount(2);

        GetterOnlyBean getterOnlyBean = new GetterOnlyBean();

        DynamicBean dynamicBean = new DynamicBean();
        dynamicBean.setName("han");
        dynamicBean.put("extra", true);

        ObjectNode facadeObject = MAPPER.createObjectNode().put("name", "han").put("count", 2);
        ArrayNode facadeArray = MAPPER.createArrayNode().add("x").add(2);

        List<String> objectKeys = new ArrayList<>();
        Nodes.forEachObject(bean, (key, value) -> objectKeys.add(key));
        assertEquals(Arrays.asList("name", "count"), objectKeys);
        assertTrue(Nodes.anyMatchObject(bean, (key, value) -> key.equals("name")));
        assertFalse(Nodes.anyMatchObject(bean, (key, value) -> key.equals("missing")));
        assertTrue(Nodes.replaceInObject(map, (key, value) -> key.equals("name") ? "jack" : value));
        assertTrue(Nodes.replaceInObject(bean, (key, value) -> key.equals("name") ? "bean" : value));
        assertFalse(Nodes.replaceInObject(getterOnlyBean, (key, value) -> "changed"));
        assertTrue(Nodes.replaceInObject(facadeObject, (key, value) -> key.equals("name") ? TextNode.valueOf("node") : value));

        List<Integer> visitedIndexes = new ArrayList<>();
        Nodes.forEachArray(new int[]{1, 2}, (idx, value) -> visitedIndexes.add(idx));
        assertEquals(Arrays.asList(0, 1), visitedIndexes);
        assertTrue(Nodes.anyMatchArray(new LinkedHashSet<>(Arrays.asList("a", "b")), (idx, value) -> idx == 1));
        assertFalse(Nodes.anyMatchArray(facadeArray, (idx, value) -> idx == 9));

        assertEquals(2, Nodes.sizeInObject(bean));
        assertEquals(2, Nodes.sizeInArray(new int[]{1, 2}));
        assertTrue(Nodes.keySetInObject(bean).contains("name"));
        assertEquals(2, Nodes.entrySetInObject(bean).size());
        assertTrue(Nodes.containsInObject(bean, "name"));
        assertTrue(Nodes.containsInArray(new int[]{1, 2}, -1));
        assertFalse(Nodes.containsInArray(new int[]{1, 2}, 5));
        assertEquals("bean", Nodes.getInObject(bean, "name"));
        assertEquals(Integer.valueOf(2), Nodes.getInObject(bean, "count", Integer.class));
        assertEquals(2, Nodes.getInArray(new int[]{1, 2}, -1));
        assertEquals(Integer.valueOf(2), Nodes.getInArray(new int[]{1, 2}, -1, Integer.class));
        assertNull(Nodes.getInArray(new int[]{1, 2}, 5));
        assertThrows(JsonException.class, () -> Nodes.getInArray(new LinkedHashSet<>(Arrays.asList("a", "b")), 0));

        Iterator<Object> iterator = Nodes.iteratorInArray(new int[]{1, 2});
        assertTrue(iterator.hasNext());
        assertEquals(1, iterator.next());
        assertEquals(2, iterator.next());
        assertThrows(NoSuchElementException.class, iterator::next);

        Nodes.Access access = new Nodes.Access();
        Nodes.accessInObject(bean, Bean.class, "name", access);
        assertEquals("bean", access.node);
        assertTrue(access.puttable);
        Nodes.accessInObject(dynamicBean, DynamicBean.class, "extra", access);
        assertEquals(true, access.node);
        assertTrue(access.puttable);
        Nodes.accessInObject(bean, Bean.class, "missing", access);
        assertNull(access.node);
        assertFalse(access.puttable);

        Nodes.accessInArray(Arrays.asList("x"), new TypeReference<List<String>>() {}.getType(), 1, access);
        assertNull(access.node);
        assertTrue(access.puttable);
        assertEquals(String.class, access.type);
        Nodes.accessInArray(Arrays.asList("x"), new TypeReference<List<String>>() {}.getType(), null, access);
        assertNull(access.node);
        assertTrue(access.puttable);
        assertEquals(String.class, access.type);
        Nodes.accessInArray(new int[]{1, 2}, int[].class, -1, access);
        assertEquals(2, access.node);
        assertTrue(access.puttable);
        Nodes.accessInArray(new int[]{1, 2}, int[].class, 2, access);
        assertNull(access.node);
        assertFalse(access.puttable);
        assertThrows(JsonException.class, () -> Nodes.accessInArray(new LinkedHashSet<>(Arrays.asList("a", "b")), null, 0, access));

        assertEquals("jack", Nodes.putInObject(map, "name", "map"));
        assertEquals("bean", Nodes.putInObject(bean, "name", "pojo"));
        assertNull(Nodes.putInObject(dynamicBean, "extra2", 3));
        assertThrows(JsonException.class, () -> Nodes.putInObject(bean, "missing", 1));

        List<Object> list = new ArrayList<>(Arrays.asList("a"));
        assertNull(Nodes.setInArray(list, 1, "b"));
        assertEquals("a", Nodes.setInArray(list, 0, "x"));

        JsonArray mutableJsonArray = JsonArray.of("a", "b");
        assertEquals("a", Nodes.setInArray(mutableJsonArray, 0, "x"));
        Nodes.addInArray(mutableJsonArray, "c");
        Nodes.addInArray(mutableJsonArray, 1, "y");
        assertEquals("b", Nodes.removeInArray(mutableJsonArray, 2));

        int[] numbers = {1, 2};
        assertEquals(2, Nodes.setInArray(numbers, -1, 7));
        assertEquals(7, numbers[1]);
        assertThrows(JsonException.class, () -> Nodes.setInArray(numbers, 2, 9));
        assertThrows(JsonException.class, () -> Nodes.setInArray(new LinkedHashSet<>(Arrays.asList("a", "b")), 0, "x"));

        Set<Object> set = new LinkedHashSet<>();
        Nodes.addInArray(set, "x");
        assertTrue(set.contains("x"));
        assertThrows(JsonException.class, () -> Nodes.addInArray(numbers, 9));
        assertThrows(JsonException.class, () -> Nodes.addInArray(set, 0, "y"));

        Map<String, Object> removableMap = new LinkedHashMap<>(map);
        assertEquals(2, Nodes.removeInObject(removableMap, "count"));
        assertEquals(2, Nodes.removeInObject(jsonObject, "count"));
        assertEquals("b", Nodes.removeInArray(new ArrayList<>(Arrays.asList("a", "b")), -1));
        assertThrows(JsonException.class, () -> Nodes.removeInArray(numbers, 0));
        assertThrows(JsonException.class, () -> Nodes.removeInArray(new LinkedHashSet<>(Arrays.asList("a", "b")), 0));
    }

    @Test
    void testFacadeNodesThroughNodesApisAndUnsupportedMutations() {
        ObjectNode objectNode = MAPPER.createObjectNode().put("name", "han").put("count", 2);
        ArrayNode arrayNode = MAPPER.createArrayNode().add("x").add(2);

        List<String> keys = new ArrayList<>();
        Nodes.forEachObject(objectNode, (key, value) -> keys.add(key));
        assertEquals(Arrays.asList("name", "count"), keys);
        assertTrue(Nodes.anyMatchObject(objectNode, (key, value) -> key.equals("count")));
        assertEquals(2, Nodes.sizeInObject(objectNode));
        assertTrue(Nodes.keySetInObject(objectNode).contains("name"));
        assertEquals(2, Nodes.entrySetInObject(objectNode).size());
        assertTrue(Nodes.containsInObject(objectNode, "name"));
        assertEquals("han", Nodes.asString(Nodes.getInObject(objectNode, "name")));
        assertEquals("han", Nodes.getInObject(objectNode, "name", String.class));

        Nodes.Access access = new Nodes.Access();
        Nodes.accessInObject(objectNode, null, "name", access);
        assertEquals("han", Nodes.asString(access.node));
        assertTrue(access.puttable);

        List<Integer> indexes = new ArrayList<>();
        Nodes.forEachArray(arrayNode, (idx, value) -> indexes.add(idx));
        assertEquals(Arrays.asList(0, 1), indexes);
        assertTrue(Nodes.anyMatchArray(arrayNode, (idx, value) -> idx == 1));
        assertEquals(2, Nodes.sizeInArray(arrayNode));
        assertEquals("x", Nodes.asString(Nodes.getInArray(arrayNode, 0)));
        assertEquals(Integer.valueOf(2), Nodes.getInArray(arrayNode, 1, Integer.class));

        Iterator<Object> iterator = Nodes.iteratorInArray(arrayNode);
        assertEquals("x", Nodes.asString(iterator.next()));
        assertEquals(2, Nodes.toNumber(iterator.next()).intValue());

        Nodes.accessInArray(arrayNode, null, 1, access);
        assertEquals(2, Nodes.toNumber(access.node).intValue());
        assertTrue(access.puttable);

        assertThrows(JsonException.class, () -> Nodes.copy(objectNode));
        assertEquals("han", Nodes.asString(Nodes.putInObject(objectNode, "name", MAPPER.valueToTree("jack"))));
        assertEquals("jack", objectNode.get("name").textValue());
        assertEquals("jack", Nodes.asString(Nodes.removeInObject(objectNode, "name")));
        assertFalse(objectNode.has("name"));
        assertEquals("x", Nodes.asString(Nodes.setInArray(arrayNode, 0, MAPPER.valueToTree("y"))));
        Nodes.addInArray(arrayNode, MAPPER.valueToTree(true));
        Nodes.addInArray(arrayNode, -1, MAPPER.valueToTree("mid"));
        assertEquals("mid", arrayNode.get(2).textValue());
        assertEquals(true, Nodes.toBoolean(Nodes.removeInArray(arrayNode, -1)));
    }

    @Test
    void testJojoJajoEqualityHashAndWalkCoverage() {
        assertThrows(JsonException.class, () -> Nodes.toJojo(JsonObject.of("name", "han"), JsonObject.class));
        assertNull(Nodes.toJajo(null, DynamicArray.class));
        DynamicArray dynamicArray = Nodes.toJajo(Arrays.asList(1, 2), DynamicArray.class);
        assertEquals(Arrays.asList(1, 2), dynamicArray.toList());
        assertThrows(JsonException.class, () -> Nodes.toJajo(Arrays.asList(1, 2), JsonArray.class));

        assertTrue(Nodes.equals(new UnknownValue("v"), new UnknownValue("v")));
        assertFalse(Nodes.equals("1", 1));
        assertEquals(Nodes.hash(Arrays.asList(1, 2)), Nodes.hash(new int[]{1, 2}));
        assertEquals("v".hashCode(), Nodes.hash(new UnknownValue("v")));
        assertTrue(Nodes.inspect(new UnknownValue("v")).startsWith("!"));

        JsonObject objectTree = JsonObject.of("user", JsonObject.of("name", "han"));
        JsonArray arrayTree = JsonArray.of(JsonObject.of("name", "han"), 2);
        List<String> visited = new ArrayList<>();

        Nodes.walk(objectTree, Nodes.WalkTarget.CONTAINER, Nodes.WalkOrder.TOP_DOWN,
                (path, node) -> {
                    visited.add(path.toString());
                    return false;
                });
        Nodes.walk(arrayTree, Nodes.WalkTarget.CONTAINER, Nodes.WalkOrder.TOP_DOWN,
                (path, node) -> {
                    visited.add(path.toString());
                    return false;
                });

        assertEquals(2, visited.size());
    }
}
