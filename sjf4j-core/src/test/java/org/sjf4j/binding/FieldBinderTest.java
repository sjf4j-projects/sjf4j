package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldBinderTest {
    static class MutablePerson {
        private String name;
        private int age;
        public void setName(String name) { this.name = name; }
        public void setAge(int age) { this.age = age; }
        public String getName() { return name; }
        public int getAge() { return age; }
    }

    @Test
    void bindsSetterProperties() {
        MutablePerson person = (MutablePerson) new SimpleJsonBinder().readNode(
                "{\"name\":\"Ada\",\"age\":37}", MutablePerson.class);

        assertEquals("Ada", person.getName());
        assertEquals(37, person.getAge());
    }
}
