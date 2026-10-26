package org.sjf4j.testbench.processor;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
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
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.node.NodeMapper;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Runtime OneOf annotations on Creator parameters, records and bound final fields. */
public class RuntimeCreatorOneOfTest {

    interface Animal {}

    public static class Dog implements Animal {
        public String kind;
        public String name;
        public int age;
    }

    public static class Cat implements Animal {
        public String kind;
        public String name;
        public int lives;
    }

    public record RecordZoo(
            @OneOf(discriminatorKey = "kind", mappings = {
                    @OneOfMapping(type = Dog.class, when = "dog"),
                    @OneOfMapping(type = Cat.class, when = "cat")
            }) Animal pet, int id) {}

    public static final class ConstructorZoo {
        public final Animal pet;
        public final int id;

        @NodeCreator
        public ConstructorZoo(
                @NodeProperty(value = "pet", aliases = "animal")
                @OneOf(discriminatorKey = "kind", mappings = {
                        @OneOfMapping(type = Dog.class, when = "dog"),
                        @OneOfMapping(type = Cat.class, when = "cat")
                }) Animal pet, @NodeProperty("id") int id) {
            this.pet = pet;
            this.id = id;
        }
    }

    public static final class FieldZoo {
        @OneOf(discriminatorKey = "kind", mappings = {
                @OneOfMapping(type = Dog.class, when = "dog"),
                @OneOfMapping(type = Cat.class, when = "cat")
        })
        private final Animal pet;

        @NodeCreator
        public FieldZoo(@NodeProperty("pet") Animal pet) { this.pet = pet; }

        public Animal getPet() { return pet; }
    }

    public static final class FactoryZoo {
        public final Animal pet;
        public final String title;

        private FactoryZoo(Animal pet, String title) {
            this.pet = pet;
            this.title = title;
        }

        @NodeCreator
        public static FactoryZoo create(
                @NodeProperty("pet")
                @OneOf(discriminatorKey = "kind", mappings = {
                        @OneOfMapping(type = Dog.class, when = "dog"),
                        @OneOfMapping(type = Cat.class, when = "cat")
                }) Animal pet,
                @NodeProperty("title") String title) {
            return new FactoryZoo(pet, title);
        }
    }

    public record PathZoo(
            @OneOf(discriminatorPath = "$.meta.kind", mappings = {
                    @OneOfMapping(type = Dog.class, when = "dog"),
                    @OneOfMapping(type = Cat.class, when = "cat")
            }) Animal pet) {}

    public record ShapeZoo(
            @OneOf(mappings = {
                    @OneOfMapping(type = String.class),
                    @OneOfMapping(type = Integer.class)
            }) Object value) {}

    public record NullableZoo(
            @OneOf(discriminatorKey = "kind", onNoMatch = OneOf.OnNoMatch.FAILBACK_NULL,
                    mappings = @OneOfMapping(type = Dog.class, when = "dog"))
            Animal pet, long after) {}

    public record ParentZoo(
            @OneOf(scope = OneOf.Scope.PARENT, discriminatorKey = "kind",
                    mappings = @OneOfMapping(type = Dog.class, when = "dog"))
            Animal pet) {}

    public static final class ParentFieldZoo {
        @OneOf(scope = OneOf.Scope.PARENT, discriminatorKey = "kind",
                mappings = @OneOfMapping(type = Dog.class, when = "dog"))
        private final Animal pet;
        @NodeCreator
        public ParentFieldZoo(@NodeProperty("pet") Animal pet) { this.pet = pet; }
    }

    public record RootZoo(
            @OneOf(scope = OneOf.Scope.ROOT, discriminatorKey = "kind",
                    mappings = @OneOfMapping(type = Dog.class, when = "dog"))
            Animal pet) {}

    public static final class ConflictingZoo {
        @OneOf(discriminatorKey = "kind", mappings = @OneOfMapping(type = Dog.class, when = "dog"))
        private final Animal pet;
        @NodeCreator
        public ConflictingZoo(@NodeProperty("pet")
                              @OneOf(discriminatorKey = "species",
                                      mappings = @OneOfMapping(type = Dog.class, when = "dog"))
                              Animal pet) {
            this.pet = pet;
        }
    }

    @OneOf(discriminatorKey = "outerKind", mappings =
            @OneOfMapping(type = NestedCreator.class, when = "nested"))
    public interface Outer {}

    public static final class NestedCreator implements Outer {
        public String outerKind;
        public final Animal pet;

        @NodeCreator
        public NestedCreator(@NodeProperty("pet")
                             @OneOf(discriminatorKey = "kind", mappings = {
                                     @OneOfMapping(type = Dog.class, when = "dog"),
                                     @OneOfMapping(type = Cat.class, when = "cat")
                             }) Animal pet) {
            this.pet = pet;
        }
    }

    static Stream<Arguments> backends() {
        return Stream.of(
                Arguments.of("simple", new SimpleJsonBinder()),
                Arguments.of("jackson2", new Jackson2Binder()),
                Arguments.of("jackson3", new Jackson3Binder()),
                Arguments.of("fastjson2", new Fastjson2Binder()),
                Arguments.of("gson", new GsonBinder()),
                Arguments.of("jsonp", new JsonpBinder()));
    }

    private static final String FIRST =
            "{\"pet\":{\"kind\":\"dog\",\"name\":\"Rex\",\"age\":7},\"id\":42}";
    private static final String LAST =
            "{\"id\":42,\"pet\":{\"name\":\"Rex\",\"age\":7,\"kind\":\"dog\"}}";

    private static void checkDog(Animal value) {
        Dog dog = assertInstanceOf(Dog.class, value);
        assertEquals("Rex", dog.name);
        assertEquals(7, dog.age);
        assertEquals("dog", dog.kind);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void recordAndConstructorParametersHandleEitherDiscriminatorOrder(String backend, Binder<?, ?> binder) {
        for (String json : new String[]{FIRST, LAST}) {
            RecordZoo record = (RecordZoo) binder.readNode(json, RecordZoo.class);
            checkDog(record.pet());
            assertEquals(42, record.id(), backend);

            ConstructorZoo constructor = (ConstructorZoo) binder.readNode(json, ConstructorZoo.class);
            checkDog(constructor.pet);
            assertEquals(42, constructor.id, backend);
        }

        ConstructorZoo alias = (ConstructorZoo) binder.readNode(
                "{\"animal\":{\"kind\":\"cat\",\"name\":\"Mog\",\"lives\":9},\"id\":3}",
                ConstructorZoo.class);
        assertEquals(9, assertInstanceOf(Cat.class, alias.pet).lives, backend);
        assertEquals(3, alias.id, backend);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void finalFieldAndStaticFactoryOverrideTheParameterType(String backend, Binder<?, ?> binder) {
        FieldZoo field = (FieldZoo) binder.readNode(
                "{\"pet\":{\"age\":7,\"name\":\"Rex\",\"kind\":\"dog\"}}", FieldZoo.class);
        checkDog(field.getPet());
        FactoryZoo factory = (FactoryZoo) binder.readNode(
                "{\"title\":\"ok\",\"pet\":{\"kind\":\"dog\",\"name\":\"Rex\",\"age\":7}}",
                FactoryZoo.class);
        checkDog(factory.pet);
        assertEquals("ok", factory.title, backend);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void pathShapeNullAndNoMatch(String backend, Binder<?, ?> binder) {
        PathZoo path = (PathZoo) binder.readNode(
                "{\"pet\":{\"name\":\"Rex\",\"age\":7,\"meta\":{\"kind\":\"dog\"}}}", PathZoo.class);
        Dog dog = assertInstanceOf(Dog.class, path.pet(), backend);
        assertEquals("Rex", dog.name, backend);

        assertEquals("hello", ((ShapeZoo) binder.readNode("{\"value\":\"hello\"}", ShapeZoo.class)).value());
        assertEquals(12, ((ShapeZoo) binder.readNode("{\"value\":12}", ShapeZoo.class)).value());

        NullableZoo unmatched = (NullableZoo) binder.readNode(
                "{\"pet\":{\"kind\":\"other\"},\"after\":8}", NullableZoo.class);
        NullableZoo missing = (NullableZoo) binder.readNode(
                "{\"pet\":{\"name\":\"Rex\"},\"after\":9}", NullableZoo.class);
        assertNull(unmatched.pet(), backend);
        assertNull(missing.pet(), backend);
        assertEquals(8L, unmatched.after(), backend);
        assertEquals(9L, missing.after(), backend);

        assertThrows(BindingException.class,
                () -> binder.readNode("{\"pet\":{\"kind\":\"other\"}}", RecordZoo.class), backend);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void readsCreatorOverridesInsideStreamingSubtypeBeforeAndAfterOuterDiscriminator(
            String backend, Binder<?, ?> binder) {
        for (String json : new String[]{
                "{\"outerKind\":\"nested\",\"pet\":{\"kind\":\"dog\",\"name\":\"Rex\",\"age\":7}}",
                "{\"pet\":{\"name\":\"Rex\",\"age\":7,\"kind\":\"dog\"},\"outerKind\":\"nested\"}"
        }) {
            NestedCreator nested = assertInstanceOf(NestedCreator.class,
                    binder.readNode(json, Outer.class), backend);
            checkDog(nested.pet);
        }
    }

    @Test
    void convertsEntryBackedCreatorArgumentsWithoutLosingMemberOverrides() {
        Map<String, Object> dog = Map.of("kind", "dog", "name", "Rex", "age", 7);
        Map<String, Object> source = Map.of("pet", dog, "id", 42);
        RecordZoo record = (RecordZoo) NodeMapper.convert(source, RecordZoo.class, false, RuntimeContext.EMPTY);
        checkDog(record.pet());
        FieldZoo field = (FieldZoo) NodeMapper.convert(Map.of("pet", dog),
                FieldZoo.class, false, RuntimeContext.EMPTY);
        checkDog(field.getPet());
    }

    @Test
    void rejectsParentRootAndConflictingCreatorAnnotationsAtMetadataAnalysis() {
        for (Class<?> type : new Class<?>[]{ParentZoo.class, ParentFieldZoo.class, RootZoo.class}) {
            BindingException e = assertThrows(BindingException.class,
                    () -> TypeRegistry.requireRegisteredPojoInfo(type), type.getName());
            assertTrue(e.getMessage().contains("scope=CURRENT"), e.getMessage());
        }
        BindingException e = assertThrows(BindingException.class,
                () -> TypeRegistry.requireRegisteredPojoInfo(ConflictingZoo.class));
        assertTrue(e.getMessage().contains("conflicting @OneOf"), e.getMessage());
    }
}
