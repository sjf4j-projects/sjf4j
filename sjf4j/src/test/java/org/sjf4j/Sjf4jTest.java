package org.sjf4j;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        JsonObject jo1 = JsonObject.deepNode(p1);
        log.info("jo1={}", jo1);

        JsonObject jo2 = Sjf4j.fromJson(JSON_DATA, JsonObject.class);
        log.info("jo2={}", jo2);

        jo1.deepPruneNulls();
        assertEquals(jo1, jo2);
    }


}
