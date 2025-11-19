package org.sjf4j;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class JojoTest {

    @Getter @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Baby extends JsonObject {
        private String name;
        private int month;
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
        babies.add(new Baby("A", 6));
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
        babies.add(new Baby("A", 6));
        p1.setBabies(babies);
        p1.put("ex", "wang");

        JsonObject jo1 = JsonObject.fromPojo(p1);
        assertNotEquals(p1, jo1);
        assertEquals(p1.toJson(), jo1.toJson());
        assertEquals(JsonArray.class, jo1.getObject("babies").getClass());
    }

    @Getter @Setter
    public static class Wrapper<T> {
        public T value;
    }

    @Getter @Setter
    public static class WrapperTwo<T1, T2> {
        public T1 value1;
        public T2 value2;
    }


    @Test
    public void testGeneric1() {
        Wrapper<Person> wrapper = new Wrapper<>();
        Person person = new Person();
        person.setName("haha");
        wrapper.setValue(person);
        JsonObject jo = JsonObject.fromPojo(wrapper);
        log.info("jo={}", jo);
    }

    @Test
    public void testBasicFields() {
        Person p = new Person();
        p.setName("Alice");
        p.put("age", 18.99);

        assertEquals("Alice", p.getString("name"));
        assertEquals(18, p.getInteger("age"));

        p.put("name", "Bob");
        p.put("age", 25);
        assertEquals("Bob", p.getName());
        assertEquals(25, p.getAge());
    }

    @Test
    public void testNestedJsonObject() {
        Person p = new Person();
        JsonObject info = new JsonObject();
        info.put("gender", "male");
        info.put("city", "Beijing");

        p.setInfo(info);
        assertEquals("male", p.getInfo().getString("gender"));
        assertEquals("Beijing", p.getStringByPath("$.info.city"));
    }

    @Test
    public void testListField() {
        Baby b1 = new Baby();
        b1.setName("Tommy");
        b1.setMonth(6);

        Baby b2 = new Baby();
        b2.put("name", "Lucy");
        b2.put("month", 8);

        Person p = new Person();
        p.setBabies(Arrays.asList(b1, b2));

        assertEquals(2, p.getBabies().size());
        assertEquals("Lucy", p.getBabies().get(1).getName());
    }

    @Test
    public void testPojoAndJsonSync() {
        Person p = new Person();
        p.put("name", "Charlie");
        p.put("age", 20);

        assertEquals("Charlie", p.getName());
        assertEquals(20, p.getAge());

        p.setName("David");
        assertEquals("David", p.getString("name"));
    }

    @Test
    public void testEdgeCases() {
        Person p = new Person();
        p.setName(null);
        p.setInfo(null);
        p.setBabies(Collections.emptyList());

        assertNull(p.getName());
        assertNull(p.getInfo());
        assertTrue(p.getBabies().isEmpty());

        assertNull(p.get("unknown"));
        p.put("extra", "value");
        assertEquals("value", p.getString("extra"));
    }

    @Test
    public void testRecursiveStructure() {
        Person parent = new Person();
        parent.setName("Parent");

        Baby baby = new Baby();
        baby.setName("Child1");
        baby.setMonth(12);

        JsonObject info = new JsonObject();
        info.put("height", 175);
        info.put("weight", 70);
        parent.setInfo(info);
        parent.setBabies(Collections.singletonList(baby));

        assertEquals(175, parent.getInfo().getInteger("height"));
        assertEquals("Child1", parent.getBabies().get(0).getName());
    }

    @Test
    public void testSerializationRoundtrip() {
        Person p = new Person();
        p.setName("Eve");
        p.setAge(30);

        JsonObject info = new JsonObject();
        info.put("lang", "Java");
        p.setInfo(info);

        String json = p.toJson();
        Person parsed = Sjf4j.fromJson(new StringReader(json), Person.class);

        assertEquals(p.getName(), parsed.getName());
        assertEquals(p.getInfo().getString("lang"), parsed.getInfo().getString("lang"));
        assertEquals(p, parsed);
    }

    @Test
    public void testInvalidAssignments() {
        Person p = new Person();
        assertThrows(JsonException.class, () -> p.put("age", "not_a_number"));
    }

    @Test
    public void testLargeObject() {
        Person p = new Person();
        for (int i = 0; i < 1000; i++) {
            p.put("key" + i, i);
        }
        assertEquals(1000+4, p.size());
    }

    @Test
    public void testComplexScenario() {
        Person p = new Person();
        p.setName("John");
        p.put("nickname", "Johnny");
        p.setAge(42);

        JsonObject info = new JsonObject();
        info.put("married", true);
        p.setInfo(info);

        Baby b = new Baby();
        b.put("name", "BabyA");
        b.setMonth(10);

        p.setBabies(Collections.singletonList(b));

        assertEquals("Johnny", p.getString("nickname"));
        assertEquals(true, p.getInfo().getBoolean("married"));
        assertEquals(10, p.getBabies().get(0).getMonth());
    }

    @Test
    public void testFromJson2Pojo1() {
        Person p = new Person();
        p.setName("John");
        p.put("nickname", "Johnny");
        p.setAge(42);

        JsonObject info = new JsonObject();
        info.put("married", true);
        p.setInfo(info);

    }

}
