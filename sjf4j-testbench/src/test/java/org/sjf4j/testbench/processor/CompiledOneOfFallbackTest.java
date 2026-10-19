package org.sjf4j.testbench.processor;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.exception.BindingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** OneOf hands off exactly one runtime subtree without de-optimizing its compiled ancestors. */
public class CompiledOneOfFallbackTest {

    @OneOf(key = "kind", value = {
            @OneOf.Mapping(value = Dog.class, when = "dog"),
            @OneOf.Mapping(value = Cat.class, when = "cat")
    })
    public static class Animal extends JsonObject {
        public String name;
    }

    public static class Dog extends Animal {
        public boolean barks;
    }

    public static class Cat extends Animal {
        public int lives;
    }

    public static class Holder {
        public long id;

        // This member annotation deliberately overrides Animal's class-level "kind".
        @OneOf(key = "species", value = {
                @OneOf.Mapping(value = Dog.class, when = "dog"),
                @OneOf.Mapping(value = Cat.class, when = "cat")
        })
        public Animal pet;

        public List<Animal> pets;
        public Map<String, Animal> byName;
        public int after;
    }

    public static class Parent {
        public String kind;
        @OneOf(key = "kind", scope = OneOf.Scope.PARENT, value = {
                @OneOf.Mapping(value = Dog.class, when = "dog"),
                @OneOf.Mapping(value = Cat.class, when = "cat")
        })
        public Animal pet;
    }

    public static class Root {
        public int before;
        public Parent parent;
        public int after;
    }

    public static class ShapeHolder {
        @OneOf({
                @OneOf.Mapping(String.class),
                @OneOf.Mapping(Integer.class)
        })
        public Object value;
        public int after;
    }

    public static class PathHolder {
        @OneOf(path = "$.meta.kind", value = {
                @OneOf.Mapping(value = Dog.class, when = "dog"),
                @OneOf.Mapping(value = Cat.class, when = "cat")
        })
        public Animal pet;
        public int after;
    }

    public static class NullableHolder {
        @OneOf(key = "kind", onNoMatch = OneOf.OnNoMatch.FAILBACK_NULL, value = {
                @OneOf.Mapping(value = Dog.class, when = "dog")
        })
        public Animal pet;
        public int after;
    }

    public record RecordHolder(long id, Animal pet) {}

    public interface OneOfBinder {
        @ReadFrom Animal readAnimal(String json) throws IOException;
        @ReadFrom Holder readHolder(String json) throws IOException;
        @ReadFrom Holder readHolder(byte[] json) throws IOException;
        @ReadFrom Root readRoot(String json) throws IOException;
        @ReadFrom ShapeHolder readShape(String json) throws IOException;
        @ReadFrom PathHolder readPath(String json) throws IOException;
        @ReadFrom NullableHolder readNullable(String json) throws IOException;
        @ReadFrom RecordHolder readRecord(String json) throws IOException;
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleOneOfBinder extends OneOfBinder {}
    @CompiledBinder(backend = Backend.GSON)
    public interface GsonOneOfBinder extends OneOfBinder {}
    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2OneOfBinder extends OneOfBinder {}
    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3OneOfBinder extends OneOfBinder {}
    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2OneOfBinder extends OneOfBinder {}
    @CompiledBinder(backend = Backend.JSONP)
    public interface JsonpOneOfBinder extends OneOfBinder {}

    static Stream<Arguments> backends() {
        return Stream.of(
                Arguments.of("simple", CompiledInstances.global(SimpleOneOfBinder.class), new SimpleJsonBinder()),
                Arguments.of("gson", CompiledInstances.global(GsonOneOfBinder.class), new GsonBinder()),
                Arguments.of("jackson2", CompiledInstances.global(Jackson2OneOfBinder.class), new Jackson2Binder()),
                Arguments.of("jackson3", CompiledInstances.global(Jackson3OneOfBinder.class), new Jackson3Binder()),
                Arguments.of("fastjson2", CompiledInstances.global(Fastjson2OneOfBinder.class), new Fastjson2Binder()),
                Arguments.of("jsonp", CompiledInstances.global(JsonpOneOfBinder.class), new JsonpBinder()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void classAndMemberOneOfKeepCompiledSurroundings(String backend,
            OneOfBinder compiled, Binder<?, ?> runtime) throws Exception {
        Dog root = assertInstanceOf(Dog.class,
                compiled.readAnimal("{\"name\":\"Rex\",\"kind\":\"dog\",\"barks\":true}"), backend);
        assertEquals("Rex", root.name, backend);
        assertTrue(root.barks, backend);

        String json = "{\"id\":42,\"pet\":{\"name\":\"Mog\",\"species\":\"cat\",\"lives\":9},"
                + "\"pets\":[{\"kind\":\"dog\",\"name\":\"Rex\",\"barks\":true}],"
                + "\"byName\":{\"one\":{\"kind\":\"cat\",\"name\":\"Kit\",\"lives\":7}},\"after\":19}";
        for (Holder value : List.of(compiled.readHolder(json),
                compiled.readHolder(json.getBytes(StandardCharsets.UTF_8)))) {
            Holder expected = (Holder) runtime.readNode(json, Holder.class);
            assertEquals(expected.id, value.id, backend);
            assertEquals(expected.after, value.after, backend);
            assertEquals(expected.pet.getClass(), value.pet.getClass(), backend);
            assertEquals(expected.pets.get(0).getClass(), value.pets.get(0).getClass(), backend);
            assertEquals(expected.byName.get("one").getClass(), value.byName.get("one").getClass(), backend);
            assertEquals(9, assertInstanceOf(Cat.class, value.pet, backend).lives, backend);
            assertTrue(assertInstanceOf(Dog.class, value.pets.get(0), backend).barks, backend);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void parentScopeFallsBackAtNearestPojo(String backend,
            OneOfBinder compiled, Binder<?, ?> runtime) throws Exception {
        for (String parent : List.of(
                "{\"kind\":\"cat\",\"pet\":{\"name\":\"Mog\",\"lives\":9}}",
                "{\"pet\":{\"name\":\"Mog\",\"lives\":9},\"kind\":\"cat\"}")) {
            String json = "{\"before\":5,\"parent\":" + parent + ",\"after\":8}";
            Root actual = compiled.readRoot(json);
            Root expected = (Root) runtime.readNode(json, Root.class);
            assertEquals(expected.before, actual.before, backend);
            assertEquals(expected.after, actual.after, backend);
            assertEquals(expected.parent.kind, actual.parent.kind, backend);
            assertEquals(9, assertInstanceOf(Cat.class, actual.parent.pet, backend).lives, backend);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void shapePathNullAndCreatorSubtrees(String backend,
            OneOfBinder compiled, Binder<?, ?> runtime) throws Exception {
        ShapeHolder shape = compiled.readShape("{\"value\":12,\"after\":2}");
        ShapeHolder expected = (ShapeHolder) runtime.readNode("{\"value\":12,\"after\":2}", ShapeHolder.class);
        assertEquals(expected.value, shape.value, backend);
        assertEquals(2, shape.after, backend);

        PathHolder path = compiled.readPath(
                "{\"pet\":{\"meta\":{\"kind\":\"cat\"},\"name\":\"Mog\",\"lives\":7},\"after\":3}");
        assertEquals(7, assertInstanceOf(Cat.class, path.pet, backend).lives, backend);
        assertEquals(3, path.after, backend);

        NullableHolder nullable = compiled.readNullable(
                "{\"pet\":{\"kind\":\"unmapped\",\"name\":\"Unknown\"},\"after\":4}");
        assertNull(nullable.pet, backend);
        assertEquals(4, nullable.after, backend);

        RecordHolder record = compiled.readRecord(
                "{\"pet\":{\"kind\":\"dog\",\"name\":\"Rex\",\"barks\":true},\"id\":9}");
        assertEquals(9, record.id(), backend);
        assertTrue(assertInstanceOf(Dog.class, record.pet(), backend).barks, backend);
        assertThrows(BindingException.class,
                () -> compiled.readAnimal("{\"kind\":\"unmapped\"}"), backend);
    }
}
