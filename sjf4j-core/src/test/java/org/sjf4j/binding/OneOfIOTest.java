package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.exception.BindingException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

class OneOfIOTest {

    static class Container {
        @OneOf(mappings = {
                @OneOfMapping(type = Dog.class, when = "dog"),
                @OneOfMapping(type = Cat.class, when = "cat")
        }, key = "kind")
        public Animal pet;
    }

    @OneOf(mappings = {@OneOfMapping(type = Dog.class, when = "dog"), @OneOfMapping(type = Cat.class, when = "cat")}, key = "kind")
    static class Animal extends JsonObject { public String name; }
    static class Dog extends Animal { public boolean barks; }
    static class Cat extends Animal { public int lives; }

    @Test
    void mapsCurrentKeyDiscriminatorToMatchingSubtype() {
        Container container = (Container) new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"pet\":{\"name\":\"Rex\",\"kind\":\"dog\",\"barks\":true}}", Container.class);

        Dog dog = assertInstanceOf(Dog.class, container.pet);
        assertEquals("Rex", dog.name);
        assertEquals(true, dog.barks);
    }

    /** Retained SJF4J semantics: mappings without a discriminator select by JSON type. */
    @Test
    void mapsJsonTypesToTheirConfiguredTargets() {
        JsonTypeContainer strings = (JsonTypeContainer) new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"value\":\"text\"}", JsonTypeContainer.class);
        JsonTypeContainer numbers = (JsonTypeContainer) new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"value\":12}", JsonTypeContainer.class);

        assertEquals("text", strings.value);
        assertEquals(12, numbers.value);
    }

    /** Retained SJF4J semantics: a PARENT discriminator can precede its polymorphic property. */
    @Test
    void mapsParentScopeDiscriminatorBeforeTheProperty() {
        Parent container = (Parent) new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"kind\":\"cat\",\"pet\":{\"name\":\"Mog\",\"lives\":9}}", Parent.class);

        Cat cat = assertInstanceOf(Cat.class, container.pet);
        assertEquals("Mog", cat.name);
        assertEquals(9, cat.lives);
    }

    /** Retained SJF4J semantics: a PARENT discriminator after the property is deferred and then applied. */
    @Test
    void defersParentScopePropertyUntilItsFollowingDiscriminator() {
        Parent container = (Parent) new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"pet\":{\"name\":\"Rex\",\"barks\":true},\"kind\":\"dog\"}", Parent.class);

        Dog dog = assertInstanceOf(Dog.class, container.pet);
        assertEquals(true, dog.barks);
    }

    /** Retained SJF4J semantics: FAILBACK_NULL consumes an unmapped object without losing following fields. */
    @Test
    void fallsBackToNullForAnUnmappedDiscriminatorAndContinues() {
        FallbackContainer container = (FallbackContainer) new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"pet\":{\"kind\":\"lizard\",\"name\":\"Liz\"},\"after\":7}", FallbackContainer.class);

        assertNull(container.pet);
        assertEquals(7, container.after);
    }

    /** Retained SJF4J semantics: FAIL reports an unmapped discriminator. */
    @Test
    void rejectsAnUnmappedDiscriminatorByDefault() {
        assertThrows(BindingException.class, () -> new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"pet\":{\"kind\":\"lizard\"}}", Container.class));
    }

    @Test
    void mapsCurrentPathDiscriminatorToMatchingSubtype() {
        PathContainer container = (PathContainer) new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"pet\":{\"meta\":{\"kind\":\"cat\"},\"name\":\"Mog\",\"lives\":9}}",
                PathContainer.class);

        Cat cat = assertInstanceOf(Cat.class, container.pet);
        assertEquals("Mog", cat.name);
        assertEquals(9, cat.lives);
    }

    @Test
    void handlesMissingAndUnmatchedCurrentPathDiscriminators() {
        SimpleJsonBinder binder = new SimpleJsonBinder(RuntimeContext.EMPTY);
        assertThrows(BindingException.class,
                () -> binder.readNode("{\"pet\":{\"name\":\"Mog\"}}", PathContainer.class));
        assertThrows(BindingException.class,
                () -> binder.readNode("{\"pet\":{\"meta\":{\"kind\":\"lizard\"}}}", PathContainer.class));

        PathFallbackContainer missing = (PathFallbackContainer) binder.readNode(
                "{\"pet\":{\"name\":\"Mog\"},\"after\":1}", PathFallbackContainer.class);
        PathFallbackContainer unmatched = (PathFallbackContainer) binder.readNode(
                "{\"pet\":{\"meta\":{\"kind\":\"lizard\"}},\"after\":2}", PathFallbackContainer.class);
        assertNull(missing.pet);
        assertNull(unmatched.pet);
        assertEquals(1, missing.after);
        assertEquals(2, unmatched.after);
    }

    @Test
    void expandsPendingKeyFieldsAndRejectsDuplicateDiscriminators() {
        Container container = (Container) new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"pet\":{\"one\":1,\"two\":2,\"three\":3,\"four\":4,\"five\":5,"
                        + "\"kind\":\"dog\",\"name\":\"Rex\",\"barks\":true}}", Container.class);

        Dog dog = assertInstanceOf(Dog.class, container.pet);
        assertEquals("Rex", dog.name);
        assertEquals(5, dog.getInt("five"));

        assertThrows(BindingException.class, () -> new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"pet\":{\"kind\":\"dog\",\"kind\":\"cat\"}}", Container.class));
    }

    @Test
    void appliesPropertyCodecsToFieldsBufferedBeforeTheDiscriminator() {
        CodecContainer container = (CodecContainer) new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"pet\":{\"createdAt\":1704067200000,\"kind\":\"dog\"}}", CodecContainer.class);

        assertEquals(Instant.ofEpochMilli(1704067200000L), container.pet.createdAt);
    }

    @Test
    void decodesNullValueFieldsBufferedBeforeTheDiscriminator() {
        CodecContainer container = (CodecContainer) new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"pet\":{\"marker\":null,\"kind\":\"dog\"}}", CodecContainer.class);

        assertEquals("<null>", container.pet.marker.value);
    }

    /** Retained SJF4J semantics: pending fields, unknown dynamic fields, and arrays of a type-level OneOf all bind normally. */
    @Test
    void preservesUnknownSubtypeFieldsAndBindsOneOfContainerElements() {
        Container container = (Container) new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"pet\":{\"extra\":3,\"name\":\"Rex\",\"kind\":\"dog\",\"barks\":true}}", Container.class);
        AnimalList list = (AnimalList) new SimpleJsonBinder(RuntimeContext.EMPTY).readNode(
                "{\"pets\":[{\"kind\":\"cat\",\"name\":\"Mog\",\"lives\":9}]}", AnimalList.class);

        assertEquals(3, container.pet.getInt("extra"));
        assertInstanceOf(Cat.class, list.pets.get(0));
    }

    @Test
    void treatsNullAsNullRegardlessOfNoMatchPolicy() {
        SimpleJsonBinder binder = new SimpleJsonBinder(RuntimeContext.EMPTY);
        assertNull(binder.readNode("null", Animal.class));
        Container container = (Container) binder.readNode("{\"pet\":null}", Container.class);
        assertNull(container.pet);
        JsonTypeContainer shape = (JsonTypeContainer) binder.readNode("{\"value\":null}", JsonTypeContainer.class);
        assertNull(shape.value);
        PathContainer path = (PathContainer) binder.readNode("{\"pet\":null,\"after\":4}", PathContainer.class);
        assertNull(path.pet);
        assertEquals(4, path.after);
    }

    @Test
    void rejectsNestedOneOfBeforeReadingTheSubtype() {
        SimpleJsonBinder binder = new SimpleJsonBinder(RuntimeContext.EMPTY);
        BindingException nested = assertThrows(BindingException.class,
                () -> binder.readNode("{\"kind\":\"nested\"}", NestedBase.class));
        assertEquals(true, nested.getMessage().contains("nested @OneOf is not supported"));

        BindingException parent = assertThrows(BindingException.class,
                () -> binder.readNode("{\"kind\":\"nested\"}", ParentNestedBase.class));
        assertEquals(true, parent.getMessage().contains("nested @OneOf is not supported"));
    }

    @Test
    void rejectsOneOfOnCreatorParametersOrCreatorBoundFields() {
        SimpleJsonBinder binder = new SimpleJsonBinder(RuntimeContext.EMPTY);
        BindingException field = assertThrows(BindingException.class,
                () -> binder.readNode("{\"pet\":{\"species\":\"cat\"}}", CreatorMemberOneOf.class));
        assertEquals(true, field.getMessage().contains("creator-bound property"));

        BindingException parameter = assertThrows(BindingException.class,
                () -> binder.readNode("{\"pet\":{\"species\":\"cat\"}}", CreatorParameterOneOf.class));
        assertEquals(true, parameter.getMessage().contains("creator parameter"));
    }

    @Test
    void currentKeySupportsObjectContainerSubtype() {
        SimpleJsonBinder binder = new SimpleJsonBinder(RuntimeContext.EMPTY);
        for (String input : new String[]{
                "{\"payload\":{\"kind\":\"map\",\"value\":7},\"after\":5}",
                "{\"payload\":{\"value\":7,\"kind\":\"map\"},\"after\":5}"
        }) {
            MapHolder holder = (MapHolder) binder.readNode(input, MapHolder.class);
            assertInstanceOf(java.util.LinkedHashMap.class, holder.payload);
            assertEquals(7, ((Number) holder.payload.get("value")).intValue());
            assertEquals(5, holder.after);
        }
    }

    @OneOf(key = "kind", mappings = {@OneOfMapping(type = NestedDog.class, when = "nested")})
    static class NestedBase {
        @OneOf(key = "species", mappings = {
                @OneOfMapping(type = Dog.class, when = "dog"),
                @OneOfMapping(type = Cat.class, when = "cat")
        })
        public Animal pet;
    }
    static class NestedDog extends NestedBase { }
    @OneOf(key = "kind", mappings = {@OneOfMapping(type = ParentNestedDog.class, when = "nested")})
    static class ParentNestedBase { }
    static class ParentNestedDog extends ParentNestedBase {
        public String species;
        @OneOf(scope = OneOf.Scope.PARENT, key = "species",
                mappings = {@OneOfMapping(type = Dog.class, when = "dog"),
                            @OneOfMapping(type = Cat.class, when = "cat")})
        public Animal pet;
    }

    static class CreatorMemberOneOf {
        @OneOf(key = "species", mappings = {@OneOfMapping(type = Dog.class, when = "dog"),
                @OneOfMapping(type = Cat.class, when = "cat")})
        private final Animal pet;

        @NodeCreator
        CreatorMemberOneOf(@NodeProperty("pet") Animal pet) {
            this.pet = pet;
        }
    }

    static class CreatorParameterOneOf {
        private final Animal pet;

        @NodeCreator
        CreatorParameterOneOf(@NodeProperty("pet") @OneOf(key = "species",
                mappings = {@OneOfMapping(type = Dog.class, when = "dog"),
                            @OneOfMapping(type = Cat.class, when = "cat")}) Animal pet) {
            this.pet = pet;
        }
    }

    static class MapHolder {
        @OneOf(key = "kind", mappings = {@OneOfMapping(type = java.util.LinkedHashMap.class, when = "map")})
        public java.util.Map<String, Object> payload;
        public int after;
    }

    static class JsonTypeContainer {
        @OneOf(mappings = {@OneOfMapping(type = String.class), @OneOfMapping(type = Integer.class)}) public Object value;
    }
    static class Parent {
        public String kind;
        @OneOf(mappings = {@OneOfMapping(type = Dog.class, when = "dog"), @OneOfMapping(type = Cat.class, when = "cat")},
                key = "kind", scope = OneOf.Scope.PARENT) public Animal pet;
    }
    static class FallbackContainer {
        @OneOf(mappings = {@OneOfMapping(type = Dog.class, when = "dog"), @OneOfMapping(type = Cat.class, when = "cat")},
                key = "kind", onNoMatch = OneOf.OnNoMatch.FAILBACK_NULL) public Animal pet;
        public int after;
    }
    static class PathContainer {
        @OneOf(mappings = {@OneOfMapping(type = Dog.class, when = "dog"), @OneOfMapping(type = Cat.class, when = "cat")},
                path = "$.meta.kind") public Animal pet;
        public int after;
    }
    static class PathFallbackContainer {
        @OneOf(mappings = {@OneOfMapping(type = Dog.class, when = "dog"), @OneOfMapping(type = Cat.class, when = "cat")},
                path = "$.meta.kind", onNoMatch = OneOf.OnNoMatch.FAILBACK_NULL) public Animal pet;
        public int after;
    }
    static class AnimalList { public java.util.List<Animal> pets; }
    static class CodecContainer {
        @OneOf(mappings = {@OneOfMapping(type = CodecDog.class, when = "dog")}, key = "kind")
        public CodecAnimal pet;
    }
    @OneOf(mappings = {@OneOfMapping(type = CodecDog.class, when = "dog")}, key = "kind")
    static class CodecAnimal extends JsonObject {
        @NodeProperty(codecName = "epochMillis") public Instant createdAt;
        @NodeProperty(codecName = "") public NullCode marker;
    }
    static class CodecDog extends CodecAnimal {}
    @NodeValue
    static class NullCode {
        final String value;

        NullCode(String value) {
            this.value = value;
        }

        @ValueToRaw String encode() {
            return value;
        }

        @RawToValue static NullCode decode(String raw) {
            return new NullCode(raw == null ? "<null>" : raw);
        }
    }
}
