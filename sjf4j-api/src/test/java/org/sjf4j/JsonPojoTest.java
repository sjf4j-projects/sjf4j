package org.sjf4j;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
public class JsonPojoTest {

    @Getter @Setter
    public static class Person extends JsonObject {
        private String name;
        private int age;
        private JsonObject info;
        private List<Person> friends;
    }

    @Test
    public void testOne() {

    }


}
