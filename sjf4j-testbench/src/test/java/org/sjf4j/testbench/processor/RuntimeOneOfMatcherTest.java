package org.sjf4j.testbench.processor;

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

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Regression tests for cached Creator NameMatcher in runtime OneOf reads. */
public class RuntimeOneOfMatcherTest {

    private static final UUID ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @OneOf(key = "kind", mappings = @OneOfMapping(type = CreatorPet.class, when = "pet"))
    public interface Pet {}

    public static final class CreatorPet implements Pet {
        public String kind;
        public String extra;
        public final UUID id;
        public final int age;

        @NodeCreator
        public CreatorPet(@NodeProperty(value = "id", codecName = "standard") UUID id,
                          @NodeProperty(value = "age", aliases = {"years"}) int age) {
            this.id = id;
            this.age = age;
        }
    }

    public static final class Envelope {
        public Pet pet;
        public int after;
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void readsCreatorAliasesAndValueCodecsInEitherDiscriminatorOrder(
            String backend, Binder<?, ?> binder) {
        // The first input uses the explicitly selected streaming UUID codec; the second buffers
        // the UUID and alias before learning the concrete subtype.
        String[] objects = {
                "{\"kind\":\"pet\",\"id\":\"" + ID + "\",\"years\":7,\"extra\":\"after\"}",
                "{\"extra\":\"before\",\"id\":\"" + ID + "\",\"years\":7,\"kind\":\"pet\"}",
                "{\"id\":\"" + ID + "\",\"kind\":\"pet\",\"age\":7,\"extra\":\"after\"}"
        };
        for (String object : objects) {
            Envelope result = (Envelope) binder.readNode("{\"pet\":" + object + ",\"after\":19}",
                    Envelope.class);
            CreatorPet pet = assertInstanceOf(CreatorPet.class, result.pet, backend);
            assertEquals("pet", pet.kind, backend);
            assertEquals(ID, pet.id, backend);
            assertEquals(7, pet.age, backend);
            assertEquals(object.contains("\"before\"") ? "before" : "after", pet.extra, backend);
            assertEquals(19, result.after, backend);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void keepsDuplicateDiscriminatorAndCreatorArgumentChecks(
            String backend, Binder<?, ?> binder) {
        String duplicateKind = "{\"pet\":{\"kind\":\"pet\",\"id\":\"" + ID
                + "\",\"kind\":\"pet\",\"age\":7}}";
        assertThrows(BindingException.class,
                () -> binder.readNode(duplicateKind, Envelope.class), backend);

        String duplicateAlias = "{\"pet\":{\"kind\":\"pet\",\"id\":\"" + ID
                + "\",\"age\":7,\"years\":8}}";
        assertThrows(BindingException.class,
                () -> binder.readNode(duplicateAlias, Envelope.class), backend);
    }
}
