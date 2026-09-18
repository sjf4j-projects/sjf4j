package org.sjf4j.binding.oneof;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OneOfIOTest {

    static class Container {
        @OneOf(value = {
                @OneOf.Mapping(value = Dog.class, when = "dog"),
                @OneOf.Mapping(value = Cat.class, when = "cat")
        }, key = "kind")
        public Animal pet;
    }

    @OneOf(value = {@OneOf.Mapping(value = Dog.class, when = "dog"), @OneOf.Mapping(value = Cat.class, when = "cat")}, key = "kind")
    static class Animal extends JsonObject { public String name; }
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

    /** Retained SJF4J semantics: mappings without a discriminator select by JSON type. */
    @Test
    void mapsJsonTypesToTheirConfiguredTargets() {
        JsonTypeContainer strings = (JsonTypeContainer) new SimpleJsonBinder().readNode(
                "{\"value\":\"text\"}", JsonTypeContainer.class);
        JsonTypeContainer numbers = (JsonTypeContainer) new SimpleJsonBinder().readNode(
                "{\"value\":12}", JsonTypeContainer.class);

        assertEquals("text", strings.value);
        assertEquals(12, numbers.value);
    }

    /** Retained SJF4J semantics: a PARENT discriminator can precede its polymorphic property. */
    @Test
    void mapsParentScopeDiscriminatorBeforeTheProperty() {
        Parent container = (Parent) new SimpleJsonBinder().readNode(
                "{\"kind\":\"cat\",\"pet\":{\"name\":\"Mog\",\"lives\":9}}", Parent.class);

        Cat cat = assertInstanceOf(Cat.class, container.pet);
        assertEquals("Mog", cat.name);
        assertEquals(9, cat.lives);
    }

    /** Retained SJF4J semantics: a PARENT discriminator after the property is deferred and then applied. */
    @Test
    void defersParentScopePropertyUntilItsFollowingDiscriminator() {
        Parent container = (Parent) new SimpleJsonBinder().readNode(
                "{\"pet\":{\"name\":\"Rex\",\"barks\":true},\"kind\":\"dog\"}", Parent.class);

        Dog dog = assertInstanceOf(Dog.class, container.pet);
        assertEquals(true, dog.barks);
    }

    /** Retained SJF4J semantics: FAILBACK_NULL consumes an unmapped object without losing following fields. */
    @Test
    void fallsBackToNullForAnUnmappedDiscriminatorAndContinues() {
        FallbackContainer container = (FallbackContainer) new SimpleJsonBinder().readNode(
                "{\"pet\":{\"kind\":\"lizard\",\"name\":\"Liz\"},\"after\":7}", FallbackContainer.class);

        assertNull(container.pet);
        assertEquals(7, container.after);
    }

    /** Retained SJF4J semantics: FAIL reports an unmapped discriminator. */
    @Test
    void rejectsAnUnmappedDiscriminatorByDefault() {
        assertThrows(RuntimeException.class, () -> new SimpleJsonBinder().readNode(
                "{\"pet\":{\"kind\":\"lizard\"}}", Container.class));
    }

    /** Retained SJF4J semantics: pending fields, unknown dynamic fields, and arrays of a type-level OneOf all bind normally. */
    @Test
    void preservesUnknownSubtypeFieldsAndBindsOneOfContainerElements() {
        Container container = (Container) new SimpleJsonBinder().readNode(
                "{\"pet\":{\"extra\":3,\"name\":\"Rex\",\"kind\":\"dog\",\"barks\":true}}", Container.class);
        AnimalList list = (AnimalList) new SimpleJsonBinder().readNode(
                "{\"pets\":[{\"kind\":\"cat\",\"name\":\"Mog\",\"lives\":9}]}", AnimalList.class);

        assertEquals(3, container.pet.getInt("extra"));
        assertInstanceOf(Cat.class, list.pets.get(0));
    }

    static class JsonTypeContainer {
        @OneOf({@OneOf.Mapping(String.class), @OneOf.Mapping(Integer.class)}) public Object value;
    }
    static class Parent {
        public String kind;
        @OneOf(value = {@OneOf.Mapping(value = Dog.class, when = "dog"), @OneOf.Mapping(value = Cat.class, when = "cat")},
                key = "kind", scope = OneOf.Scope.PARENT) public Animal pet;
    }
    static class FallbackContainer {
        @OneOf(value = {@OneOf.Mapping(value = Dog.class, when = "dog"), @OneOf.Mapping(value = Cat.class, when = "cat")},
                key = "kind", onNoMatch = OneOf.OnNoMatch.FAILBACK_NULL) public Animal pet;
        public int after;
    }
    static class AnimalList { public java.util.List<Animal> pets; }
}
