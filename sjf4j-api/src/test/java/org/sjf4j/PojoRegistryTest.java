package org.sjf4j;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.util.TypeReference;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Slf4j
public class PojoRegistryTest {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Role {
        private String name;
        private float percentage;
    }

    @Getter
    @Setter
    public static class Person {
        private String name;
        private int age;
        private JsonObject info;
        private List<JsonPojoTest.Person> friends;
    }

    @Test
    public void testRegister1() {
        PojoRegistry.PojoInfo pi = PojoRegistry.register(Person.class);
        log.info("pi={}", pi);
        assertNotNull(pi);
        assertEquals(4, pi.getFields().size());
        assertNotNull(pi.getFields().get("name").getGetter());
        assertNotNull(pi.getFields().get("name").getSetter());
        assertEquals(int.class, pi.getFields().get("age").getType());
        assertEquals(JsonObject.class, pi.getFields().get("info").getType());
        assertEquals(new TypeReference<List<JsonPojoTest.Person>>(){}.getType(),
                pi.getFields().get("friends").getType());
    }

    @Test
    public void testHasPojo1() {
        assertTrue(PojoRegistry.hasPojo(Role.class));
    }
}
