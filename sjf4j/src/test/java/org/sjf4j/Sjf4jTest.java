package org.sjf4j;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.node.Nodes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Sjf4jTest {

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

    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"}}";

    @Test
    public void testJson2Pojo1() {
        Person p1 = Sjf4j.fromJson(JSON_DATA, Person.class);
        log.info("p1={}", p1);

        JsonObject jo1 = JsonObject.fromNode(p1);
        log.info("jo1={}", jo1);

        JsonObject jo2 = JsonObject.fromNode(p1);
        log.info("jo2={}", jo2);
        assertEquals(jo1, jo2);

        JsonObject jo3 = Sjf4j.fromJson(JSON_DATA, JsonObject.class);
        log.info("jo3={}", jo3);

        Person p2 = Sjf4j.fromNode(jo3, Person.class);
        log.info("p2={}", p2);
        assertNotEquals(p1, p2);

        assertEquals(Sjf4j.toRaw(p1), Sjf4j.toRaw(p2));
    }

    @Test
    public void testJson2Pojo2() {
        Person p1 = Sjf4j.fromJson(JSON_DATA, Person.class);
        log.info("p1={}", p1);

        JsonObject jo2 = JsonObject.fromNode(p1);
        log.info("jo2={}", jo2);

        Object n3 = Sjf4j.toRaw(p1);
        log.info("n3={}", n3);

        assertTrue(Nodes.equals(p1, n3));
        assertTrue(Nodes.equals(jo2, n3));
    }


}
