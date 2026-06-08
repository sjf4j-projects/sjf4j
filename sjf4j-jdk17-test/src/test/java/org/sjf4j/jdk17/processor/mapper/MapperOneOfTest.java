package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.exception.BindingException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MapperOneOfTest {

    @Test
    public void dispatchesRootPojoSourceByDiscriminator() {
        OneOfMapper mapper = CompiledNodes.of(OneOfMapper.class);

        Animal cat = mapper.animal(new AnimalSource("cat", "Milo", 9, null));
        Animal dog = mapper.animal(new AnimalSource("dog", "Rex", null, Boolean.TRUE));
        Animal catWithLocalConverters = mapper.animalWithLocalSubtypeConverters(new AnimalSource("cat", "LocalCat", 5, null));
        Animal dogWithLocalConverters = mapper.animalWithLocalSubtypeConverters(new AnimalSource("dog", "LocalDog", null, Boolean.FALSE));

        assertInstanceOf(Cat.class, cat);
        assertEquals(new Cat("Milo", 9), cat);
        assertInstanceOf(Dog.class, dog);
        assertEquals(new Dog("Rex", true), dog);
        assertEquals(new Cat("LocalCat", 5), catWithLocalConverters);
        assertEquals(new Dog("LocalDog", false), dogWithLocalConverters);
        assertNull(mapper.animal(null));
    }

    @Test
    public void dispatchesMapAndJsonObjectSources() {
        OneOfMapper mapper = CompiledNodes.of(OneOfMapper.class);

        Animal fromMap = mapper.animalMap(Map.of("type", "cat", "name", "MapCat", "lives", 7));
        Animal fromJson = mapper.animalJson(JsonObject.of("type", "dog", "name", "JsonDog", "goodDog", Boolean.TRUE));

        assertEquals(new Cat("MapCat", 7), fromMap);
        assertEquals(new Dog("JsonDog", true), fromJson);
    }

    @Test
    public void dispatchesContainerElementsAndNestedProperties() {
        OneOfMapper mapper = CompiledNodes.of(OneOfMapper.class);

        List<Animal> animals = mapper.animals(List.of(
                Map.of("type", "cat", "name", "A", "lives", 8),
                Map.of("type", "dog", "name", "B", "goodDog", Boolean.FALSE)));

        assertEquals(List.of(new Cat("A", 8), new Dog("B", false)), animals);

        ZooSource source = new ZooSource();
        source.animals = List.of(
                Map.of("type", "cat", "name", "NestedCat", "lives", 6),
                Map.of("type", "dog", "name", "NestedDog", "goodDog", Boolean.TRUE));

        Zoo zoo = mapper.zoo(source);
        assertEquals(List.of(new Cat("NestedCat", 6), new Dog("NestedDog", true)), zoo.animals);
    }

    @Test
    public void appliesOnNoMatchPolicies() {
        OneOfMapper mapper = CompiledNodes.of(OneOfMapper.class);

        assertNull(mapper.nullableAnimal(Map.of("type", "bird", "name", "Sky")));
        BindingException ex = assertThrows(BindingException.class,
                () -> mapper.animalMap(Map.of("type", "bird", "name", "Sky")));
        assertEquals("Cannot resolve @OneOf target 'org.sjf4j.jdk17.processor.mapper.MapperOneOfTest.Animal' from discriminator key 'type' value 'bird'", ex.getMessage());
    }

    @Test
    public void dispatchesByShapeWithoutDiscriminator() {
        OneOfMapper mapper = CompiledNodes.of(OneOfMapper.class);

        ShapeAnimal fromMap = mapper.shapeMap(Map.<String, Object>of("name", "MapCat"));
        ShapeAnimal fromJson = mapper.shapeJson(JsonObject.of("name", "JsonCat"));
        ShapeAnimal fromString = mapper.shapeString("ShapeText");

        assertEquals(new ShapeCat("MapCat"), fromMap);
        assertEquals(new ShapeCat("JsonCat"), fromJson);
        assertEquals(new ShapeText("ShapeText"), fromString);
        assertNull(mapper.shapeMap(null));
    }

    @Test
    public void appliesShapeOnNoMatchPolicies() {
        OneOfMapper mapper = CompiledNodes.of(OneOfMapper.class);

        assertNull(mapper.nullableShape(Boolean.TRUE));
        BindingException ex = assertThrows(BindingException.class, () -> mapper.shapeFail(Boolean.TRUE));
        assertEquals("Cannot resolve @OneOf target 'org.sjf4j.jdk17.processor.mapper.MapperOneOfTest.ShapeAnimal' from runtime JsonType 'BOOLEAN'", ex.getMessage());
    }

    @CompiledMapper
    interface OneOfMapper {
        Animal animal(AnimalSource source);

        Animal animalWithLocalSubtypeConverters(AnimalSource source);

        Cat cat(AnimalSource source);

        Dog dog(AnimalSource source);

        Animal animalMap(Map<String, Object> source);

        Animal animalJson(JsonObject source);

        ShapeAnimal shapeMap(Map<String, Object> source);

        ShapeAnimal shapeJson(JsonObject source);

        ShapeAnimal shapeString(String source);

        ShapeAnimal shapeFail(Boolean source);

        List<Animal> animals(List<Map<String, Object>> source);

        Zoo zoo(ZooSource source);

        NullableAnimal nullableAnimal(Map<String, Object> source);

        NullableShapeAnimal nullableShape(Boolean source);
    }

    public record AnimalSource(String type, String name, Integer lives, Boolean goodDog) {}

    public static final class ZooSource {
        public List<Map<String, Object>> animals;
    }

    public static final class Zoo {
        public List<Animal> animals;
    }

    @OneOf(key = "type", value = {
            @OneOf.Mapping(value = Cat.class, when = "cat"),
            @OneOf.Mapping(value = Dog.class, when = "dog")
    })
    interface Animal {}

    public record Cat(String name, int lives) implements Animal {}

    public record Dog(String name, boolean goodDog) implements Animal {}

    @OneOf(key = "type", onNoMatch = OneOf.OnNoMatch.FAILBACK_NULL, value = {
            @OneOf.Mapping(value = NullableCat.class, when = "cat"),
            @OneOf.Mapping(value = NullableDog.class, when = "dog")
    })
    interface NullableAnimal {}

    public record NullableCat(String name, int lives) implements NullableAnimal {}

    public record NullableDog(String name, boolean goodDog) implements NullableAnimal {}

    @OneOf({
            @OneOf.Mapping(value = ShapeCat.class),
            @OneOf.Mapping(value = ShapeText.class)
    })
    interface ShapeAnimal {}

    public record ShapeCat(String name) implements ShapeAnimal {}

    @NodeValue
    public record ShapeText(String value) implements ShapeAnimal {
        @RawToValue
        public static ShapeText of(String value) {
            return new ShapeText(value);
        }

        @ValueToRaw
        public String raw() {
            return value;
        }
    }

    @OneOf(onNoMatch = OneOf.OnNoMatch.FAILBACK_NULL, value = {
            @OneOf.Mapping(value = NullableShapeCat.class),
            @OneOf.Mapping(value = NullableShapeText.class)
    })
    interface NullableShapeAnimal {}

    public record NullableShapeCat(String name) implements NullableShapeAnimal {}

    @NodeValue
    public record NullableShapeText(String value) implements NullableShapeAnimal {
        @RawToValue
        public static NullableShapeText of(String value) {
            return new NullableShapeText(value);
        }

        @ValueToRaw
        public String raw() {
            return value;
        }
    }
}
