package org.sjf4j.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

}
