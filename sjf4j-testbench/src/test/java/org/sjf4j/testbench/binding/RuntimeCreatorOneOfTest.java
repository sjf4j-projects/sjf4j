package org.sjf4j.testbench.binding;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.NodeMapper;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeCreatorOneOfTest {
    interface Animal {}
    public record Dog(String kind, String name, int age) implements Animal {}
    public record Cat(String kind, String name, int lives) implements Animal {}

    public record Zoo(@OneOf(discriminatorKey = "kind", mappings = {
            @OneOfMapping(type = Dog.class, when = "dog"),
            @OneOfMapping(type = Cat.class, when = "cat")
    }) Animal pet, int after) {}

    public static class ConstructorZoo {
        public final Animal pet;
        public final int after;

        @NodeCreator
        public ConstructorZoo(@NodeProperty("pet") @OneOf(discriminatorKey = "kind", mappings = {
                @OneOfMapping(type = Dog.class, when = "dog"),
                @OneOfMapping(type = Cat.class, when = "cat")
        }) Animal pet, @NodeProperty("after") int after) {
            this.pet = pet;
            this.after = after;
        }
    }

    public static class FieldZoo {
        @OneOf(discriminatorKey = "kind", mappings = {
                @OneOfMapping(type = Dog.class, when = "dog"),
                @OneOfMapping(type = Cat.class, when = "cat")
        })
        public final Animal pet;
        public final int after;

        @NodeCreator
        public FieldZoo(@NodeProperty("pet") Animal pet, @NodeProperty("after") int after) {
            this.pet = pet;
            this.after = after;
        }
    }

    public record PathZoo(@OneOf(discriminatorPath = "$.meta.kind", mappings = {
            @OneOfMapping(type = Dog.class, when = "dog"),
            @OneOfMapping(type = Cat.class, when = "cat")
    }) Animal pet) {}

    public record NullableZoo(@OneOf(discriminatorKey = "kind",
            onNoMatch = OneOf.OnNoMatch.FAILBACK_NULL,
            mappings = @OneOfMapping(type = Dog.class, when = "dog")) Animal pet) {}

    public static class InheritedBase {
        @NodeProperty("animal")
        @OneOf(discriminatorKey = "kind", mappings = {
                @OneOfMapping(type = Dog.class, when = "dog"),
                @OneOfMapping(type = Cat.class, when = "cat")
        })
        protected final Animal pet;
        protected InheritedBase(Animal pet) { this.pet = pet; }
    }

    public static class InheritedZoo extends InheritedBase {
        @NodeCreator
        public InheritedZoo(@NodeProperty("animal") Animal pet) { super(pet); }
    }

    public static class InheritedConflictZoo extends InheritedBase {
        @NodeCreator
        public InheritedConflictZoo(@NodeProperty("animal")
                @OneOf(discriminatorKey = "species",
                        mappings = @OneOfMapping(type = Dog.class, when = "dog")) Animal pet) {
            super(pet);
        }
    }

    public static class InheritedParentBase {
        @OneOf(scope = OneOf.Scope.PARENT, discriminatorKey = "kind",
                mappings = @OneOfMapping(type = Dog.class, when = "dog"))
        protected final Animal pet;
        protected InheritedParentBase(Animal pet) { this.pet = pet; }
    }

    public static class InheritedParentZoo extends InheritedParentBase {
        @NodeCreator
        public InheritedParentZoo(@NodeProperty("pet") Animal pet) { super(pet); }
    }

    public record ParentZoo(@OneOf(scope = OneOf.Scope.PARENT, discriminatorKey = "kind",
            mappings = @OneOfMapping(type = Dog.class, when = "dog")) Animal pet) {}

    static Stream<Arguments> backends() {
        return Stream.of(
                Arguments.of("simple", new SimpleJsonBinder()),
                Arguments.of("jackson2", new Jackson2Binder()),
                Arguments.of("jackson3", new Jackson3Binder()),
                Arguments.of("fastjson2", new Fastjson2Binder()),
                Arguments.of("gson", new GsonBinder()),
                Arguments.of("jsonp", new JsonpBinder()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void supportsRecordConstructorAndCreatorBoundField(String name, Binder<?, ?> binder) {
        for (String animal : new String[]{
                "{\"kind\":\"dog\",\"name\":\"Rex\",\"age\":7}",
                "{\"name\":\"Rex\",\"age\":7,\"kind\":\"dog\"}",
                "{\"kind\":\"cat\",\"name\":\"Mog\",\"lives\":9}"}) {
            String json = "{\"pet\":" + animal + ",\"after\":19}";
            Zoo record = (Zoo) binder.readNode(json, Zoo.class);
            ConstructorZoo ctor = (ConstructorZoo) binder.readNode(json, ConstructorZoo.class);
            FieldZoo field = (FieldZoo) binder.readNode(json, FieldZoo.class);
            Class<?> subtype = animal.contains("\"dog\"") ? Dog.class : Cat.class;
            assertInstanceOf(subtype, record.pet(), name);
            assertInstanceOf(subtype, ctor.pet, name);
            assertInstanceOf(subtype, field.pet, name);
            assertEquals(19, record.after());
            assertEquals(19, ctor.after);
            assertEquals(19, field.after);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void resolvesInheritedCreatorOneOfAndRejectsConflicts(String name, Binder<?, ?> binder) {
        String json = "{\"animal\":{\"name\":\"Rex\",\"age\":7,\"kind\":\"dog\"}}";
        InheritedZoo inherited = (InheritedZoo) binder.readNode(json, InheritedZoo.class);
        assertEquals(7, assertInstanceOf(Dog.class, inherited.pet, name).age(), name);
        InheritedZoo mapped = (InheritedZoo) NodeMapper.convert(
                Map.of("animal", Map.of("kind", "cat", "name", "Mog", "lives", 9)),
                InheritedZoo.class, false, org.sjf4j.RuntimeContext.EMPTY);
        assertEquals(9, assertInstanceOf(Cat.class, mapped.pet, name).lives(), name);
        BindingException conflict = assertThrows(BindingException.class,
                () -> binder.readNode(json, InheritedConflictZoo.class), name);
        assertTrue(conflict.getMessage().contains("conflicting @OneOf"), name);
        BindingException parent = assertThrows(BindingException.class,
                () -> binder.readNode("{\"pet\":{\"kind\":\"dog\"}}", InheritedParentZoo.class), name);
        assertTrue(parent.getMessage().contains("scope=CURRENT"), name);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void supportsNodeMappingAndRejectsParent(String name, Binder<?, ?> binder) {
        Map<String, Object> json = Map.of("pet", Map.of("kind", "dog", "name", "Rex", "age", 7), "after", 19);
        Zoo mapped = (Zoo) NodeMapper.convert(json, Zoo.class, false, org.sjf4j.RuntimeContext.EMPTY);
        assertInstanceOf(Dog.class, mapped.pet(), name);
        PathZoo path = (PathZoo) binder.readNode(
                "{\"pet\":{\"meta\":{\"kind\":\"dog\"},\"kind\":\"dog\",\"name\":\"Rex\",\"age\":7}}",
                PathZoo.class);
        assertInstanceOf(Dog.class, path.pet(), name);
        NullableZoo missing = (NullableZoo) binder.readNode("{\"pet\":{\"kind\":\"cat\"}}", NullableZoo.class);
        assertNull(missing.pet(), name);
        assertThrows(BindingException.class,
                () -> binder.readNode("{\"pet\":{\"kind\":\"dog\"}}", ParentZoo.class), name);
    }
}
