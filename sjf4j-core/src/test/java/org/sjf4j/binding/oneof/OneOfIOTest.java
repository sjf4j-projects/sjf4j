package org.sjf4j.binding.oneof;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OneOfIOTest {

    static class Container {
        @OneOf(value = {
                @OneOf.Mapping(value = Dog.class, when = "dog"),
                @OneOf.Mapping(value = Cat.class, when = "cat")
        }, key = "kind")
        public Animal pet;
    }

    static class Animal { public String name; }
    static class Dog extends Animal { public boolean barks; }
    static class Cat extends Animal { public int lives; }

    @Test
    void mapsCurrentKeyDiscriminatorToMatchingSubtype() {
        Container container = (Container) new SimpleJsonBinder().readNode(
                "{\"pet\":{\"name\":\"Rex\",\"kind\":\"dog\",\"barks\":true}}", Container.class);

        Dog dog = assertInstanceOf(Dog.class, container.pet);
        assertEquals("Rex", dog.name);
        assertEquals(true, dog.barks);
    }
}
