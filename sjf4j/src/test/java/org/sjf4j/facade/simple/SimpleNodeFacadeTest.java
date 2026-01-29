package org.sjf4j.facade.simple;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonException;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.node.Nodes;
import org.sjf4j.patch.JsonPatch;
import org.sjf4j.patch.PatchOp;
import org.sjf4j.path.JsonPointer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
@Slf4j
public class SimpleNodeFacadeTest {

    private NodeFacade nodeFacade = new SimpleNodeFacade();

    @Test
    void objectClassPreservesShape() {
        Object[] samples = {
                1,
                "x",
                Arrays.asList(1,2),
                Collections.singletonMap("a",1),
                new JsonObject("x",1),
                new Student()
        };

        Object r0 = nodeFacade.readNode(samples[0], Object.class);
        assertEquals(Integer.class, r0.getClass());

        Object r1 = nodeFacade.readNode(samples[1], Object.class);
        assertEquals(String.class, r1.getClass());

        Object r2 = nodeFacade.readNode(samples[2], Object.class);
        assertInstanceOf(List.class, r2);

        Object r3 = nodeFacade.readNode(samples[3], Object.class);
        assertInstanceOf(Map.class, r3);

        Object r4 = nodeFacade.readNode(samples[4], Object.class);
        assertInstanceOf(JsonObject.class, r4);

        Object r5 = nodeFacade.readNode(samples[5], Object.class);
        assertInstanceOf(Student.class, r5);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Role {
        private String name;
        private float percentage;
    }

    @Data
    public static class User {
        private String name;
        private int age;
        private JsonObject info;
        private List<User> friends;
        private Map<String, Role> roles;
    }

    @Test
    public void testObject2Value1() {
        User baby = new User();
        baby.setName("Baby");
        baby.setAge(1);
        baby.setInfo(new JsonObject("cc", "dd"));

        User lily = new User();
        lily.setName("Lily");
        lily.setAge(25);
        lily.setInfo(new JsonObject("aa", "bb"));
        lily.setFriends(Collections.singletonList(baby));
        lily.setRoles(Collections.singletonMap("kk", new Role("Mom", 90.0f)));

        // object2Value
        Object value = nodeFacade.readNode(lily, JsonObject.class);
        log.info("value type={}, value={}", value.getClass(), value);
        assertEquals(JsonObject.class, value.getClass());
        assertEquals(25, ((JsonObject) value).getInteger("age"));
        assertEquals("Baby",
                ((JsonObject) value).getJsonArray("friends").getJsonObject(0).getString("name"));
        assertEquals(90f, ((JsonObject) value).getFloatByPath("$.roles.kk.percentage"));

        // value2Object
        Object object = nodeFacade.readNode(value, User.class);
        log.info("object type={}, object={}", object.getClass(), object);
    }

    @Test
    public void testValue2Object2() {
        Object o1 = nodeFacade.readNode(new JsonObject("percentage", 0), Role.class);
        log.info("o1 type={}, o1={}", o1.getClass(), o1);
        assertEquals(Role.class, o1.getClass());
    }

    @Test
    public void testValue2Object3() {
        Object o1 = nodeFacade.readNode(5.55, long.class);
        log.info("o1 type={}, o1={}", o1.getClass(), o1);
    }


    /// AI generated

    // ========== 基础类型 ==========
    public static class BasicTypes {
        public String name;
        public int age;
        public Boolean vip;
        public Double score;
    }

    @Test
    public void testBasicTypes() {
        JsonObject jo = new JsonObject();
        jo.put("name", "Alice");
        jo.put("age", 18);
        jo.put("vip", true);
        jo.put("score", 99.5);

        BasicTypes pojo = (BasicTypes) nodeFacade.readNode(jo, BasicTypes.class);
        assertEquals("Alice", pojo.name);
        assertEquals(18, pojo.age);
        assertTrue(pojo.vip);
        assertEquals(99.5, pojo.score);

        JsonObject back = (JsonObject) nodeFacade.readNode(pojo, JsonObject.class);
        assertEquals("Alice", back.get("name"));
    }

    // ========== 嵌套对象 ==========
    public static class Address {
        public String city;
        public String street;
    }

    public static class Person {
        public String name;
        public Address address;
    }

    @Test
    public void testNestedObject() {
        JsonObject jo = new JsonObject();
        JsonObject addr = new JsonObject();
        addr.put("city", "New York");
        addr.put("street", "5th Ave");
        jo.put("name", "Bob");
        jo.put("address", addr);

        Person p = (Person) nodeFacade.readNode(jo, Person.class);
        assertEquals("Bob", p.name);
        assertEquals("New York", p.address.city);
        assertEquals("5th Ave", p.address.street);

        Map<String, Object> back = (Map<String, Object>) nodeFacade.writeNode(p);
        assertEquals("Bob", back.get("name"));
    }

    // ========== List 和 数组 ==========
    public static class Team {
        public String teamName;
        public List<String> members;
        public int[] scores;
    }

    @Test
    public void testListAndArray() {
        JsonObject jo = new JsonObject();
        jo.put("teamName", "Rangers");
        jo.put("members", Arrays.asList("Tom", "Jerry"));
        jo.put("scores", new int[]{10, 20, 30});

        Team t = (Team) nodeFacade.readNode(jo, Team.class);
        assertEquals("Rangers", t.teamName);
        assertEquals(Arrays.asList("Tom", "Jerry"), t.members);
        assertArrayEquals(new int[]{10, 20, 30}, t.scores);

        Map<String, Object> back = (Map<String, Object>) nodeFacade.writeNode(t);
        assertEquals("Rangers", back.get("teamName"));
    }

    // ========== List<POJO> ==========
    public static class Student {
        public String name;
        public int age;
    }

    public static class ClassRoom {
        public List<Student> students;
    }

    @Test
    public void testListOfPojo() {
        JsonObject jo = new JsonObject();
        List<JsonObject> list = new ArrayList<>();
        list.add(new JsonObject("name", "Ann", "age", 10));
        list.add(new JsonObject("name", "Ben", "age", 12));
        jo.put("students", list);

        ClassRoom c = (ClassRoom) nodeFacade.readNode(jo, ClassRoom.class);
        log.info("c={}", c);
        assertEquals(2, c.students.size());
        assertEquals("Ann", c.students.get(0).name);

        JsonObject back = (JsonObject) nodeFacade.readNode(c, JsonObject.class);
        log.info("back={}", back);
        Assertions.assertNotNull(back);
        Object students = back.get("students");
        log.info("students type={}, json={}", students.getClass(), students);
        assertTrue(students instanceof List);
    }

    // ========== Set<POJO> ==========
    public static class ClassRoomSet {
        public Set<Student> students;
    }

    @Test
    public void testSetOfPojo() {
        JsonObject jo = new JsonObject();
        List<JsonObject> list = new ArrayList<>();
        list.add(new JsonObject("name", "Ann", "age", 10));
        list.add(new JsonObject("name", "Ben", "age", 12));
        jo.put("students", list);

        ClassRoomSet c = (ClassRoomSet) nodeFacade.readNode(jo, ClassRoomSet.class);
        assertEquals(2, c.students.size());

        JsonObject back = (JsonObject) nodeFacade.readNode(c, JsonObject.class);
        Object students = back.get("students");
        log.info("students type={}, json={}", students.getClass(), Nodes.inspect(students));
        assertTrue(students instanceof Set);
    }

    @Test
    public void testSetToSet() {
        Set<String> input = new LinkedHashSet<>(Arrays.asList("a", "b"));
        Set<String> output = (Set<String>) nodeFacade.readNode(input, Set.class);
        assertEquals(input, output);
    }

    // ========== Map ==========
    public static class DictHolder {
        public Map<String, Integer> map;
        public Map<String, Student> nested;
    }

    @Test
    public void testMap() {
        JsonObject jo = new JsonObject();
        JsonObject map = new JsonObject();
        map.put("a", 1);
        map.put("b", 2);

        JsonObject nested = new JsonObject();
        nested.put("s1", new JsonObject("name", "Alice", "age", 7));
        nested.put("s2", new JsonObject("name", "Bob", "age", 8));

        jo.put("map", map);
        jo.put("nested", nested);

        log.info("jo={}", jo);
        DictHolder holder = (DictHolder) nodeFacade.readNode(jo, DictHolder.class);
        log.info("holder={} map={}", holder, holder.map);
        assertEquals(1, holder.map.get("a"));
//        assertEquals("Alice", holder.nested.get("s1").name);

        JsonObject back = (JsonObject) nodeFacade.readNode(holder, JsonObject.class);
        assertEquals(2, (back.getJsonObject("map")).size());
    }

    // ========== Enum ==========
    public enum Status { OK, FAIL }

    public static class Msg {
        public Status status;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEnum() {
        JsonObject jo = new JsonObject();
        jo.put("status", "OK");

        Msg msg = (Msg) nodeFacade.readNode(jo, Msg.class);
        assertEquals(Status.OK, msg.status);

        Msg back = (Msg) nodeFacade.readNode(msg, null);
        assertEquals(Status.OK, back.status);
    }

    // ========== Boolean Getter ==========
    public static class Flag {
        public boolean active;
    }

    @Test
    public void testBooleanField() {
        JsonObject jo = new JsonObject();
        jo.put("active", true);
        Flag f = (Flag) nodeFacade.readNode(jo, Flag.class);
        assertTrue(f.active);
    }


    @Test
    public void testNullFields() {
        JsonObject jo = new JsonObject();
        jo.put("name", null);
        BasicTypes bt = (BasicTypes) nodeFacade.readNode(jo, BasicTypes.class);
        assertNull(bt.name);
        assertEquals(0, bt.age); // 默认 int=0

        jo.put("age", null);
        assertThrows(JsonException.class, () -> nodeFacade.readNode(jo, BasicTypes.class));
    }


    @Test
    public void testPrimitive1() {
        assertEquals(123, nodeFacade.readNode(123, int.class));
        assertEquals(123d, nodeFacade.readNode(123L, double.class));
        assertEquals('a', nodeFacade.readNode("abc", char.class));
        assertEquals('a', nodeFacade.readNode('a', char.class));
        assertEquals("a", nodeFacade.readNode('a', String.class));
        assertEquals(false, nodeFacade.readNode(false, boolean.class));
    }

    @Test
    public void testObjectArrayPreserveType() {
        int[] ints = new int[]{1, 2, 3};
        Object outInts = nodeFacade.readNode(ints, Object.class);
        assertTrue(outInts.getClass().isArray());
        assertEquals(int.class, outInts.getClass().getComponentType());
        assertArrayEquals(ints, (int[]) outInts);

        Integer[] boxed = new Integer[]{1, 2, 3};
        Object outBoxed = nodeFacade.readNode(boxed, Object.class);
        assertTrue(outBoxed instanceof Integer[]);
        assertArrayEquals(boxed, (Integer[]) outBoxed);
    }

    @Test
    public void testObjectJsonArrayPreserveType() {
        JsonArray input = new JsonArray(new Object[]{1, "a", true});
        Object out = nodeFacade.readNode(input, Object.class);
        assertEquals(JsonArray.class, out.getClass());
        JsonArray outJa = (JsonArray) out;
        assertEquals(3, outJa.size());
        assertEquals(1, outJa.getNode(0));
        assertEquals("a", outJa.getNode(1));
        assertEquals(true, outJa.getNode(2));
    }

    @Test
    public void testObjectJsonArraySubclassPreserveType() {
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOp("add", JsonPointer.compile("/a"), 1, null));

        Object out = nodeFacade.readNode(patch, Object.class);
        assertEquals(JsonPatch.class, out.getClass());
        JsonPatch outPatch = (JsonPatch) out;
        assertEquals(1, outPatch.size());
        PatchOp op = (PatchOp) outPatch.getNode(0);
        assertEquals("add", op.getOp());
    }

    @Test
    public void testObject1() {
        Map<String, Object> m = Collections.singletonMap("a", 1);
        Object o = nodeFacade.readNode(m, Object.class);
        assertInstanceOf(Map.class, o);
    }


}
