package org.sjf4j.facades.simple;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.ObjectFacade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class SimpleObjectFacadeTest {

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

    private ObjectFacade objectFacade = new SimpleObjectFacade();

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
        Object value = objectFacade.readNode(lily, null);
        log.info("value type={}, value={}", value.getClass(), value);
        assertEquals(JsonObject.class, value.getClass());
        assertEquals(25, ((JsonObject) value).getInteger("age"));
        assertEquals("Baby",
                ((JsonObject) value).getJsonArray("friends").getJsonObject(0).getString("name"));
        assertEquals(90f, ((JsonObject) value).getFloatByPath("$.roles.kk.percentage"));

        // value2Object
        Object object = objectFacade.readNode(value, User.class);
        log.info("object type={}, object={}", object.getClass(), object);
    }

    @Test
    public void testValue2Object2() {
        Object o1 = objectFacade.readNode(new JsonObject("percentage", 0), Role.class);
        log.info("o1 type={}, o1={}", o1.getClass(), o1);
        assertEquals(Role.class, o1.getClass());
    }

    @Test
    public void testValue2Object3() {
        Object o1 = objectFacade.readNode(5.55, long.class);
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

        BasicTypes pojo = (BasicTypes) objectFacade.readNode(jo, BasicTypes.class);
        assertEquals("Alice", pojo.name);
        assertEquals(18, pojo.age);
        assertTrue(pojo.vip);
        assertEquals(99.5, pojo.score);

        JsonObject back = (JsonObject) objectFacade.readNode(pojo, JsonObject.class);
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

        Person p = (Person) objectFacade.readNode(jo, Person.class);
        assertEquals("Bob", p.name);
        assertEquals("New York", p.address.city);
        assertEquals("5th Ave", p.address.street);

        JsonObject back = (JsonObject) objectFacade.readNode(p, null);
        assertEquals("Bob", back.getString("name"));
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

        Team t = (Team) objectFacade.readNode(jo, Team.class);
        assertEquals("Rangers", t.teamName);
        assertEquals(Arrays.asList("Tom", "Jerry"), t.members);
        assertArrayEquals(new int[]{10, 20, 30}, t.scores);

        JsonObject back = (JsonObject) objectFacade.readNode(t, Object.class);
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

        ClassRoom c = (ClassRoom) objectFacade.readNode(jo, ClassRoom.class);
        log.info("c={}", c);
        assertEquals(2, c.students.size());
        assertEquals("Ann", c.students.get(0).name);

        JsonObject back = (JsonObject) objectFacade.readNode(c, JsonObject.class);
        log.info("back={}", back);
        Assertions.assertNotNull(back);
        Object students = back.get("students");
        log.info("students type={}, json={}", students.getClass(), students);
        assertTrue(students instanceof JsonArray);
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
        DictHolder holder = (DictHolder) objectFacade.readNode(jo, DictHolder.class);
        log.info("holder={} map={}", holder, holder.map);
        assertEquals(1, holder.map.get("a"));
//        assertEquals("Alice", holder.nested.get("s1").name);

        JsonObject back = (JsonObject) objectFacade.readNode(holder, Object.class);
        assertEquals(2, (back.asJsonObject("map")).size());
    }

    // ========== Enum ==========
    public enum Status { OK, FAIL }

    public static class Msg {
        public Status status;
    }

    @Test
    public void testEnum() {
        JsonObject jo = new JsonObject();
        jo.put("status", "OK");

        Msg msg = (Msg) objectFacade.readNode(jo, Msg.class);
        assertEquals(Status.OK, msg.status);

        JsonObject back = (JsonObject) objectFacade.readNode(msg, null);
        assertEquals("OK", back.getString("status"));
    }

    // ========== Boolean Getter ==========
    public static class Flag {
        public boolean active;
    }

    @Test
    public void testBooleanField() {
        JsonObject jo = new JsonObject();
        jo.put("active", true);
        Flag f = (Flag) objectFacade.readNode(jo, Flag.class);
        assertTrue(f.active);
    }

    // ========== Null 字段 ==========
    @Test
    public void testNullFields() {
        JsonObject jo = new JsonObject();
        jo.put("name", null);
        BasicTypes bt = (BasicTypes) objectFacade.readNode(jo, BasicTypes.class);
        assertNull(bt.name);
        assertEquals(0, bt.age); // 默认 int=0

        jo.put("age", null);
        assertThrows(JsonException.class, () -> objectFacade.readNode(jo, BasicTypes.class));
    }


//    // ========== Record 类型 ==========
//    public record User(String name, int age) {}
//
//    @Test
//    public void testRecord() {
//        JsonObject jo = new JsonObject();
//        jo.put("name", "Tom");
//        jo.put("age", 20);
//
//        User u = (User) JsonConverter.value2Object(jo, User.class);
//        assertEquals("Tom", u.name());
//        assertEquals(20, u.age());
//    }

}
