package org.sjf4j.testbench.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Compiled creator-bound polymorphism must retain Runtime OneOf semantics. */
public class CompiledCreatorOneOfTest {
    private static final JacksonReader JACKSON = CompiledInstances.global(JacksonReader.class);
    private static final SimpleReader SIMPLE = CompiledInstances.global(SimpleReader.class);
    private static final Jackson2Binder RUNTIME = new Jackson2Binder();

    public interface Animal {}
    public record Dog(String kind, String name, int age) implements Animal {}
    public record Cat(String kind, String name, int lives) implements Animal {}

    public record RecordZoo(@OneOf(discriminatorKey = "kind", mappings = {
            @OneOfMapping(type = Dog.class, when = "dog"),
            @OneOfMapping(type = Cat.class, when = "cat")
    }) Animal pet, int after) {}

    public static final class ConstructorZoo {
        private final Animal pet;
        private final int after;

        @NodeCreator
        public ConstructorZoo(
                @NodeProperty("pet") @OneOf(discriminatorKey = "kind", mappings = {
                        @OneOfMapping(type = Dog.class, when = "dog"),
                        @OneOfMapping(type = Cat.class, when = "cat")
                }) Animal pet,
                @NodeProperty("after") int after) {
            this.pet = pet;
            this.after = after;
        }

        public Animal getPet() { return pet; }
        public int getAfter() { return after; }
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
        private final Animal pet;
        private FactoryZoo(Animal pet) { this.pet = pet; }

        @NodeCreator
        public static FactoryZoo create(@NodeProperty("pet")
                @OneOf(discriminatorKey = "kind", mappings = {
                        @OneOfMapping(type = Dog.class, when = "dog"),
                        @OneOfMapping(type = Cat.class, when = "cat")
                }) Animal pet) {
            return new FactoryZoo(pet);
        }

        public Animal getPet() { return pet; }
    }

    public static class InheritedBase {
        @NodeProperty("animal")
        @OneOf(discriminatorKey = "kind", mappings = {
                @OneOfMapping(type = Dog.class, when = "dog"),
                @OneOfMapping(type = Cat.class, when = "cat")
        })
        protected final Animal pet;
        protected InheritedBase(Animal pet) { this.pet = pet; }
        public Animal getPet() { return pet; }
    }

    public static final class InheritedZoo extends InheritedBase {
        @NodeCreator
        public InheritedZoo(@NodeProperty("animal") Animal animal) {
            super(animal);
        }
    }

    public record PathZoo(@OneOf(discriminatorPath = "$.meta.kind", mappings = {
            @OneOfMapping(type = Dog.class, when = "dog"),
            @OneOfMapping(type = Cat.class, when = "cat")
    }) Animal pet) {}

    public record ShapeZoo(@OneOf(mappings = {
            @OneOfMapping(type = String.class),
            @OneOfMapping(type = Integer.class)
    }) Object item) {}

    public record NullableZoo(@OneOf(discriminatorKey = "kind",
            onNoMatch = OneOf.OnNoMatch.FAILBACK_NULL,
            mappings = @OneOfMapping(type = Dog.class, when = "dog")) Animal pet) {}

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface JacksonReader {
        @ReadFrom RecordZoo readRecord(String json) throws IOException;
        @ReadFrom RecordZoo readRecord(byte[] json) throws IOException;
        @ReadFrom ConstructorZoo readConstructor(String json) throws IOException;
        @ReadFrom FieldZoo readField(String json) throws IOException;
        @ReadFrom FactoryZoo readFactory(String json) throws IOException;
        @ReadFrom InheritedZoo readInherited(String json) throws IOException;
        @ReadFrom PathZoo readPath(String json) throws IOException;
        @ReadFrom ShapeZoo readShape(String json) throws IOException;
        @ReadFrom NullableZoo readNullable(String json) throws IOException;
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleReader {
        @ReadFrom RecordZoo readRecord(String json) throws IOException;
        @ReadFrom ConstructorZoo readConstructor(String json) throws IOException;
        @ReadFrom FieldZoo readField(String json) throws IOException;
        @ReadFrom FactoryZoo readFactory(String json) throws IOException;
        @ReadFrom InheritedZoo readInherited(String json) throws IOException;
        @ReadFrom PathZoo readPath(String json) throws IOException;
        @ReadFrom ShapeZoo readShape(String json) throws IOException;
    }

    @Test
    void readsRecordAndCreatorParametersForBothOrders() throws IOException {
        String[] pets = {
                "{\"kind\":\"dog\",\"name\":\"Rex\",\"age\":7}",
                "{\"name\":\"Rex\",\"age\":7,\"kind\":\"dog\"}",
                "{\"kind\":\"cat\",\"name\":\"Mog\",\"lives\":9}"
        };
        for (String pet : pets) {
            String json = "{\"pet\":" + pet + ",\"after\":19}";
            RecordZoo runtime = (RecordZoo) RUNTIME.readNode(json, RecordZoo.class);
            RecordZoo compiled = JACKSON.readRecord(json);
            assertEquals(runtime, compiled);
            assertEquals(runtime, JACKSON.readRecord(json.getBytes(StandardCharsets.UTF_8)));
            assertEquals(runtime, SIMPLE.readRecord(json));
            assertEquals(runtime.pet(), JACKSON.readConstructor(json).getPet());
            assertEquals(19, JACKSON.readConstructor(json).getAfter());
            assertEquals(runtime.pet(), SIMPLE.readConstructor(json).getPet());
            String single = "{\"pet\":" + pet + "}";
            assertEquals(runtime.pet(), JACKSON.readField(single).getPet());
            assertEquals(runtime.pet(), SIMPLE.readField(single).getPet());
            assertEquals(runtime.pet(), JACKSON.readFactory(single).getPet());
            assertEquals(runtime.pet(), SIMPLE.readFactory(single).getPet());
        }
    }

    @Test
    void readsInheritedFieldsPathsShapesAndFallbackNull() throws IOException {
        String inherited = "{\"animal\":{\"name\":\"Rex\",\"age\":7,\"kind\":\"dog\"}}";
        assertEquals(7, assertInstanceOf(Dog.class, JACKSON.readInherited(inherited).getPet()).age());
        assertEquals(7, assertInstanceOf(Dog.class, SIMPLE.readInherited(inherited).getPet()).age());
        String path = "{\"pet\":{\"meta\":{\"kind\":\"cat\"},\"kind\":\"cat\",\"name\":\"Mog\",\"lives\":9}}";
        assertEquals(9, assertInstanceOf(Cat.class, JACKSON.readPath(path).pet()).lives());
        assertEquals(9, assertInstanceOf(Cat.class, SIMPLE.readPath(path).pet()).lives());
        assertEquals("text", JACKSON.readShape("{\"item\":\"text\"}").item());
        assertEquals(7, JACKSON.readShape("{\"item\":7}").item());
        assertEquals("text", SIMPLE.readShape("{\"item\":\"text\"}").item());
        assertNull(JACKSON.readNullable("{\"pet\":{\"kind\":\"unknown\"}}").pet());
    }
}
