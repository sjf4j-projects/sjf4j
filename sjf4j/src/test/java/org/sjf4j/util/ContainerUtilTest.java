package org.sjf4j.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Slf4j
public class ContainerUtilTest {

    @Test
    public void testCopy1() {
        JsonObject jo1 = JsonObject.fromJson("{\"num\":\"6\",\"duck\":[\"haha\",\"haha\"],\"attr\":{\"aa\":88,\"cc\":\"dd\",\"ee\":{\"ff\":\"uu\"},\"kk\":[1,2]},\"yo\":77}");
        JsonObject jo2 = ContainerUtil.copy(jo1);
        JsonObject jo3 = ContainerUtil.deepCopy(jo1);
        assertEquals(jo1, jo2);
        assertEquals(jo1, jo3);

        jo1.put("num", "7");
        assertEquals(jo1, jo2);
        assertNotEquals(jo1, jo3);
    }


    public static class Address extends JsonObject {
        public String city;
        public String street;
    }
    public static class Person extends JsonObject {
        public String name;
        public Address address;
    }

    @Test
    public void testCopy2() {
        JsonObject jo = new JsonObject(
                "name", "Bob",
                "address", new JsonObject(
                        "city", "New York",
                        "street", "5th Ave"));
        Person p1 = jo.toPojo(Person.class);
        Person p2 = ContainerUtil.copy(p1);
        Person p3 = ContainerUtil.deepCopy(p1);
        assertEquals(p1, p2);
        assertEquals(p1, p3);

        p1.address.city = "Beijing";
        assertEquals(p1, p2);
        assertNotEquals(p1, p3);

        p1.name = "Tom";
        assertNotEquals(p1, p2);
    }


    public static class Baby {
        public String name;
        public List<String> friends;
    }

    @Test
    public void testCopy3() {
        JsonObject jo = new JsonObject(
                "name", "Bob",
                "friends", new String[]{"Tom", "Jay"});
        Baby b1 = jo.toPojo(Baby.class);
        Baby b2 = ContainerUtil.copy(b1);
        Baby b3 = ContainerUtil.deepCopy(b1);
        assertNotEquals(b1, b2);
        assertEquals(Sjf4j.toJson(b1), Sjf4j.toJson(b2));
        assertEquals(Sjf4j.toJson(b1), Sjf4j.toJson(b3));

        b1.friends.set(0, "Jim");
        assertEquals(Sjf4j.toJson(b1), Sjf4j.toJson(b2));
        assertNotEquals(Sjf4j.toJson(b1), Sjf4j.toJson(b3));

        b1.name = "Bro";
        assertNotEquals(Sjf4j.toJson(b1), Sjf4j.toJson(b2));
    }

    @Test
    public void testEquals() {
        JsonObject jo = new JsonObject(
                "name", "Bob",
                "address", new JsonObject(
                        "city", "New York",
                        "street", "5th Ave"));
        Person p1 = jo.toPojo(Person.class);
        JsonObject jo1 = new JsonObject(p1);
        assertNotEquals(p1, jo1);
        assertNotEquals(jo1, p1);

        Map<String, Object> map1 = jo1.toMap();
        assertEquals(jo1, map1);
        assertNotEquals(map1, jo1);
    }


    @SuppressWarnings("unchecked")
    @Test
    public void testMergeOverwriteAndDeepCopy() {
        Map<String, Object> target = new HashMap<>();
        target.put("a", 1);
        target.put("b", new HashMap<>(Collections.singletonMap("x", 10)));

        Map<String, Object> patch = new HashMap<>();
        patch.put("a", 2);
        patch.put("b", new HashMap<>(Collections.singletonMap("y", 20)));
        patch.put("c", 3);

        // merge with overwrite, no deep copy
        ContainerUtil.merge(target, patch, true, false);

        assertEquals(2, target.get("a"));
        Map<String, Object> b = (Map<String, Object>) target.get("b");
        assertEquals(10, b.get("x"));
        assertEquals(20, b.get("y"));
        assertEquals(3, target.get("c"));
    }

    @Test
    public void testMergeWithoutOverwrite() {
        Map<String, Object> target = new HashMap<>();
        target.put("a", 1);

        Map<String, Object> patch = new HashMap<>();
        patch.put("a", 2);  // should not overwrite
        patch.put("b", 3);

        ContainerUtil.merge(target, patch, false, false);

        assertEquals(1, target.get("a"));
        assertEquals(3, target.get("b"));
    }

    @Test
    public void testMergeArray() {
        List<Object> target = new ArrayList<>(Arrays.asList(1, 2, 3));
        List<Object> patch = new ArrayList<>(Arrays.asList(10, 20, 30));

        ContainerUtil.merge(target, patch, true, false);

        assertEquals(Arrays.asList(10, 20, 30), target);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMergeNestedArrayAndObject() {
        Map<String, Object> target = new HashMap<>();
        target.put("arr", new ArrayList<>(Arrays.asList(
                new HashMap<>(Collections.singletonMap("x", 1)),
                new HashMap<>(Collections.singletonMap("y", 2))
        )));

        Map<String, Object> patch = new HashMap<>();
        patch.put("arr", new ArrayList<>(Arrays.asList(
                new HashMap<>(Collections.singletonMap("y", 10)),
                new HashMap<>(Collections.singletonMap("z", 20))
        )));

        ContainerUtil.merge(target, patch, true, false);

        List<Map<String, Object>> arr = (List<Map<String, Object>>) target.get("arr");
        assertEquals(2, arr.size());
        assertEquals(10, arr.get(0).get("y"));
        assertEquals(20, arr.get(1).get("z"));
    }

    @Test
    public void testMergeRfc7386DeletesAndArrayReplace() {
        Map<String, Object> target = new HashMap<>();
        target.put("a", 1);
        target.put("b", null);
        target.put("c", new ArrayList<>(Arrays.asList(1, 2, 3)));

        Map<String, Object> patch = new HashMap<>();
        patch.put("a", null);  // delete key
        patch.put("c", new ArrayList<>(Arrays.asList(10, 20)));  // replace array
        patch.put("d", 5);

        ContainerUtil.mergeRfc7386(target, patch);

        assertFalse(target.containsKey("a"));  // deleted
        assertNull(target.get("b"));  // unchanged, null is valid target value
        assertEquals(Arrays.asList(10, 20), target.get("c"));  // replaced
        assertEquals(5, target.get("d"));
    }


    @Test
    public void testMergeRfc7386Nested() {
        Map<String, Object> target = new HashMap<>();
        target.put("obj", new JsonObject("x", 1, "y", 2));

        Map<String, Object> patch = new HashMap<>();
        patch.put("obj", new JsonObject("x", 10, "z", 3));

        ContainerUtil.mergeRfc7386(target, patch);

        JsonObject obj = (JsonObject) target.get("obj");
        assertEquals(10, obj.getInteger("x"));  // merged
        assertEquals(2, obj.getInteger("y"));   // preserved
        assertEquals(3, obj.getInteger("z"));   // added
    }

}
