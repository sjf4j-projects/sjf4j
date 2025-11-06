package org.sjf4j;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Slf4j
public class JsonPojoTest {

    @Getter @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Baby extends JsonObject {
        private String name;
    }

    @Getter @Setter
    public static class Person extends JsonObject {
        private String name;
        private int age;
        private JsonObject info;
        private List<Baby> babies;
    }

    @Test
    public void testToPojo1() {
        Person p1 = new Person();
        p1.setName("Lily");
        p1.setAge(22);
        List<Baby> babies = new ArrayList<>();
        babies.add(new Baby("A"));
        p1.setBabies(babies);
        p1.put("ex", "wang");

        p1.put("age", 25);
        assertEquals(25, p1.getAge());

        p1.putByPath("$.babies[0].name", "B");
        assertEquals("B", p1.getBabies().get(0).getName());

        String json1 = p1.toJson();
        log.info("json1={}", json1);

        Person p2 = JsonObject.fromJson(json1).toPojo(Person.class);
        log.info("p2={}", p2);
        assertEquals(p1, p2);
    }

    @Test
    public void testFromPojo1() {
        Person p1 = new Person();
        p1.setName("Lily");
        p1.setAge(22);
        List<Baby> babies = new ArrayList<>();
        babies.add(new Baby("A"));
        p1.setBabies(babies);
        p1.put("ex", "wang");

        JsonObject jo1 = JsonObject.fromPojo(p1);
        assertNotEquals(p1, jo1);
        assertEquals(p1.toJson(), jo1.toJson());
        assertEquals(JsonArray.class, jo1.getObject("babies").getClass());
    }




}
