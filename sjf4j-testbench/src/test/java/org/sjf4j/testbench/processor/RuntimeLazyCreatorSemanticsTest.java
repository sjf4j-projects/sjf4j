package org.sjf4j.testbench.processor;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sjf4j.TypeReference;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Creator type metadata is shared by raw class, but generic argument resolution
 * must remain contextual to each TypeReference. Recursive OneOf metadata must
 * also be resolved only after the referring creator has been analyzed.
 */
@SuppressWarnings("unchecked")
class RuntimeLazyCreatorSemanticsTest {

    static Stream<Arguments> backends() {
        return Stream.of(
                Arguments.of("simple", new SimpleJsonBinder()),
                Arguments.of("jackson2", new Jackson2Binder()),
                Arguments.of("jackson3", new Jackson3Binder()),
                Arguments.of("fastjson2", new Fastjson2Binder()),
                Arguments.of("gson", new GsonBinder()),
                Arguments.of("jsonp", new JsonpBinder()));
    }

    public static final class GenericCreator<T> {
        public final T value;
        public final List<T> list;
        public final Map<String, T> map;

        @NodeCreator
        public GenericCreator(@NodeProperty("value") T value,
                              @NodeProperty("list") List<T> list,
                              @NodeProperty("map") Map<String, T> map) {
            this.value = value;
            this.list = list;
            this.map = map;
        }
    }

    public static class Base<T> {
        public T inherited;
    }

    public static final class InheritedCreator extends Base<UUID> {
        public final UUID id;

        @NodeCreator
        public InheritedCreator(@NodeProperty("id") UUID id) {
            this.id = id;
        }
    }

    @OneOf(key = "kind", mappings = {
            @OneOfMapping(type = Leaf.class, when = "leaf"),
            @OneOfMapping(type = Branch.class, when = "branch")
    })
    public interface RecursiveNode {}

    public record Leaf(String value) implements RecursiveNode {}

    public record Branch(List<RecursiveNode> children, RecursiveNode direct)
            implements RecursiveNode {}


    public interface ParentPet {}

    public record ParentDog(String name) implements ParentPet {}

    public record ParentCat(int lives) implements ParentPet {}

    public static final class ParentEnvelope {
        public String kind;
        @OneOf(scope = OneOf.Scope.PARENT, key = "kind", mappings = {
                @OneOfMapping(type = ParentDog.class, when = "dog"),
                @OneOfMapping(type = ParentCat.class, when = "cat")
        })
        public ParentPet pet;
    }

    public static final class JsonShapeEnvelope {
        @OneOf(mappings = {
                @OneOfMapping(type = String.class),
                @OneOfMapping(type = Integer.class)
        })
        public Object value;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void parentScopeDispatchHandlesBeforeAndAfterDiscriminator(String backend, Binder<?, ?> binder) {
        for (String json : new String[]{
                "{\"kind\":\"dog\",\"pet\":{\"name\":\"Rex\"}}",
                "{\"pet\":{\"name\":\"Rex\"},\"kind\":\"dog\"}"
        }) {
            ParentEnvelope root = (ParentEnvelope) binder.readNode(json, ParentEnvelope.class);
            assertEquals("Rex", assertInstanceOf(ParentDog.class, root.pet, backend).name(), backend);
            assertEquals("dog", root.kind, backend);
        }
        ParentEnvelope cat = (ParentEnvelope) binder.readNode(
                "{\"pet\":{\"lives\":9},\"kind\":\"cat\"}", ParentEnvelope.class);
        assertEquals(9, assertInstanceOf(ParentCat.class, cat.pet, backend).lives(), backend);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void discriminatorFreeOneOfReadsScalarTargets(String backend, Binder<?, ?> binder) {
        JsonShapeEnvelope text = (JsonShapeEnvelope) binder.readNode(
                "{\"value\":\"hello\"}", JsonShapeEnvelope.class);
        assertEquals("hello", text.value, backend);
        JsonShapeEnvelope number = (JsonShapeEnvelope) binder.readNode(
                "{\"value\":42}", JsonShapeEnvelope.class);
        assertEquals(42, number.value, backend);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void resolvesParameterizedCreatorArgumentsPerCall(String backend, Binder<?, ?> binder) {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        GenericCreator<UUID> uuids = (GenericCreator<UUID>) binder.readNode(
                "{\"value\":\"" + id + "\",\"list\":[\"" + id + "\"],\"map\":{\"first\":\"" + id + "\"}}",
                new TypeReference<GenericCreator<UUID>>() {}.getType());
        assertEquals(id, uuids.value, backend);
        assertEquals(id, uuids.list.get(0), backend);
        assertEquals(id, uuids.map.get("first"), backend);

        // Read the same generic raw Class with different arguments after the
        // first invocation has initialized and cached its CreatorInfo.
        GenericCreator<LocalDate> dates = (GenericCreator<LocalDate>) binder.readNode(
                "{\"value\":\"2024-01-15\",\"list\":[\"2024-02-16\"],\"map\":{\"first\":\"2024-03-17\"}}",
                new TypeReference<GenericCreator<LocalDate>>() {}.getType());
        assertEquals(LocalDate.of(2024, 1, 15), dates.value, backend);
        assertEquals(LocalDate.of(2024, 2, 16), dates.list.get(0), backend);
        assertEquals(LocalDate.of(2024, 3, 17), dates.map.get("first"), backend);

        GenericCreator<Integer> ints = (GenericCreator<Integer>) binder.readNode(
                "{\"value\":4,\"list\":[5],\"map\":{\"first\":6}}",
                new TypeReference<GenericCreator<Integer>>() {}.getType());
        assertEquals(4, ints.value, backend);
        assertEquals(5, ints.list.get(0), backend);
        assertEquals(6, ints.map.get("first"), backend);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void resolvesInheritedGenericPropertyWithConcreteCreator(String backend, Binder<?, ?> binder) {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        InheritedCreator result = (InheritedCreator) binder.readNode(
                "{\"id\":\"" + id + "\",\"inherited\":\"" + id + "\"}",
                InheritedCreator.class);
        assertEquals(id, result.id, backend);
        assertEquals(id, result.inherited, backend);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void bindsRecursiveRecordCreatorsWithDiscriminatorBeforeOrAfterProperties(
            String backend, Binder<?, ?> binder) {
        String[] branches = {
                "{\"kind\":\"branch\",\"children\":[{\"kind\":\"leaf\",\"value\":\"a\"},"
                        + "{\"kind\":\"branch\",\"children\":[{\"kind\":\"leaf\",\"value\":\"b\"}],"
                        + "\"direct\":{\"kind\":\"leaf\",\"value\":\"c\"}}],"
                        + "\"direct\":{\"kind\":\"leaf\",\"value\":\"d\"}}",
                "{\"children\":[{\"value\":\"a\",\"kind\":\"leaf\"}],"
                        + "\"direct\":{\"value\":\"d\",\"kind\":\"leaf\"},\"kind\":\"branch\"}"
        };
        for (String input : branches) {
            Branch root = assertInstanceOf(Branch.class, binder.readNode(input, RecursiveNode.class), backend);
            assertEquals("a", assertInstanceOf(Leaf.class, root.children().get(0), backend).value(), backend);
            assertEquals("d", assertInstanceOf(Leaf.class, root.direct(), backend).value(), backend);
            if (root.children().size() == 2) {
                Branch nested = assertInstanceOf(Branch.class, root.children().get(1), backend);
                assertEquals("b", assertInstanceOf(Leaf.class, nested.children().get(0), backend).value(), backend);
                assertEquals("c", assertInstanceOf(Leaf.class, nested.direct(), backend).value(), backend);
            }
        }
    }
}
