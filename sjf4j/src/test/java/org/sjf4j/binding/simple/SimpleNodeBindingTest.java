package org.sjf4j.binding.simple;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.exception.BindingException;
import org.sjf4j.TypeReference;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
class SimpleNodeBindingTest {

    private final SimpleNodeBinding binding = new SimpleNodeBinding();

    static class User {
        public String name;
        public int age;
        public List<User> friends;
        public Map<String, Integer> scores;
    }

    static class SourceUser {
        public String name;
        public long age;
    }

    static class TargetUser {
        public String name;
        public int age;
    }

    enum Status { ACTIVE, DISABLED }

    @OneOf(value = {
            @OneOf.Mapping(value = Cat.class, when = "cat"),
            @OneOf.Mapping(value = Dog.class, when = "dog")
    }, key = "kind")
    static class Animal {
        public String kind;
        public String name;
    }

    static class Cat extends Animal {
        public int lives;
    }

    static class Dog extends Animal {
        public int bark;
    }

    static class Zoo {
        @OneOf(value = {
                @OneOf.Mapping(value = Cat.class, when = "cat"),
                @OneOf.Mapping(value = Dog.class, when = "dog")
        }, key = "kind")
        public Animal pet;
    }

    @OneOf({
            @OneOf.Mapping(ObjectPolymorph.class),
            @OneOf.Mapping(ArrayPolymorph.class)
    })
    interface Polymorph {}

    static class ObjectPolymorph extends JsonObject implements Polymorph {}

    static class ArrayPolymorph extends JsonArray implements Polymorph {}

    static class UntypedArray extends JsonArray {}

    static class TypedIntegerArray extends JsonArray {
        @Override
        public Class<?> elementType() {
            return Integer.class;
        }
    }

    static class DeepPojo {
        public Map<String, List<User>> users;
        public Set<int[]> arrays;
        public JsonObject dynamic;
    }

    static class CreatedUser {
        final String name;
        final List<Integer> scores;
        String city;

        @NodeCreator CreatedUser(@NodeProperty(value = "name", aliases = "n") String name,
                                 @NodeProperty("scores") List<Integer> scores) {
            this.name = name;
            this.scores = scores;
        }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }

    static class AnimalContainers {
        public List<Animal> animals;
        public Map<String, Animal> byName;
        public Animal[] array;
    }

    @Test
    void readsGenericPojoContainersAndConcreteTargets() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("first", JsonObject.of("name", "Ann", "age", 7));
        source.put("second", JsonObject.of("name", "Ben", "age", 8));

        Map<String, User> users = (Map<String, User>) binding.readNode(source,
                new TypeReference<Map<String, User>>() {}.getType());

        assertEquals("Ann", users.get("first").name);
        assertEquals(8, users.get("second").age);

        List<Integer> integers = (List<Integer>) binding.readNode(JsonArray.of(1L, 2L),
                new TypeReference<List<Integer>>() {}.getType());
        assertEquals(Arrays.asList(1, 2), integers);

        TreeSet<Integer> sorted = (TreeSet<Integer>) binding.readNode(new int[]{3, 1, 3}, TreeSet.class);
        assertEquals(new TreeSet<>(Arrays.asList(1, 3)), sorted);
        assertArrayEquals(new int[]{1, 2}, (int[]) binding.readNode(Arrays.asList(1L, 2L), int[].class));
    }

    @Test
    void convertsPojoSourcesToPojoMapAndJsonObjectTargets() {
        SourceUser source = new SourceUser();
        source.name = "Ann";
        source.age = 7L;

        TargetUser target = (TargetUser) binding.readNode(source, TargetUser.class);
        assertEquals("Ann", target.name);
        assertEquals(7, target.age);

        Map<String, Object> map = (Map<String, Object>) binding.readNode(source,
                new TypeReference<Map<String, Object>>() {}.getType());
        assertEquals("Ann", map.get("name"));
        assertEquals(7L, map.get("age"));
        JsonObject object = (JsonObject) binding.readNode(source, JsonObject.class);
        assertEquals("Ann", object.getString("name"));
        assertEquals(7L, object.getLong("age"));
    }

    @Test
    void convertsSetSourcesThroughArraySourceTargets() {
        LinkedHashSet<Long> source = new LinkedHashSet<>(Arrays.asList(3L, 1L, 3L));

        List<Integer> list = (List<Integer>) binding.readNode(source,
                new TypeReference<List<Integer>>() {}.getType());
        assertEquals(Arrays.asList(3, 1), list);
        assertArrayEquals(new int[]{3, 1}, (int[]) binding.readNode(source, int[].class));
        JsonArray array = (JsonArray) binding.readNode(source, JsonArray.class);
        assertEquals(3L, array.getLong(0));
        assertEquals(1L, array.getLong(1));
    }

    @Test
    void readsScalarsNullsAndReportsConversionPaths() {
        assertEquals(5L, binding.readNode(5.9d, long.class));
        assertEquals('x', binding.readNode("xyz", char.class));
        assertEquals(Status.ACTIVE, binding.readNode("ACTIVE", Status.class));
        assertEquals(Optional.empty(), binding.readNode(null, new TypeReference<Optional<String>>() {}.getType()));
        assertNull(binding.readNode(null, String.class));

        BindingException exception = assertThrows(BindingException.class, () -> binding.readNode(
                JsonObject.of("friends", JsonArray.of(JsonObject.of("age", "not-a-number"))), User.class));
        assertTrue(exception.getMessage().contains("$.friends[0].age"));
        assertThrows(BindingException.class, () -> binding.readNode(true, Integer.class));
        assertThrows(BindingException.class, () -> binding.readNode(JsonArray.of(1), Map.class));
    }

    @Test
    void readDeepCopySwitchPreservesOrIsolatesNestedNodes() {
        JsonObject source = JsonObject.of("meta", JsonObject.of("tags", JsonArray.of("a")));

        assertSame(source, binding.readNode(source, Object.class, false));

        JsonObject copied = (JsonObject) binding.readNode(source, Object.class, true);
        assertNotSame(source, copied);
        assertNotSame(source.getJsonObject("meta"), copied.getJsonObject("meta"));
        copied.getJsonObject("meta").getJsonArray("tags").set(0, "b");
        assertEquals("a", source.getJsonObject("meta").getJsonArray("tags").getString(0));

        User user = (User) binding.readNode(JsonObject.of("name", "Ann", "friends",
                JsonArray.of(JsonObject.of("name", "Ben"))), User.class);
        User copiedUser = (User) binding.readNode(user, User.class, true);
        assertNotSame(user, copiedUser);
        assertNotSame(user.friends, copiedUser.friends);
        assertNotSame(user.friends.get(0), copiedUser.friends.get(0));
    }

    @Test
    void deepCopiesNestedMapsListsSetsArraysJsonNodesAndPojos() {
        User user = new User();
        user.name = "Ann";
        DeepPojo source = new DeepPojo();
        source.users = new LinkedHashMap<>();
        source.users.put("team", new java.util.ArrayList<>(List.of(user)));
        source.arrays = new LinkedHashSet<>();
        source.arrays.add(new int[]{1, 2});
        source.dynamic = JsonObject.of("items", JsonArray.of(new LinkedHashMap<>(Map.of("v", 1))));

        DeepPojo copy = (DeepPojo) binding.readNode(source, DeepPojo.class, true);
        assertNotSame(source, copy);
        assertNotSame(source.users, copy.users);
        assertNotSame(source.users.get("team"), copy.users.get("team"));
        assertNotSame(source.users.get("team").get(0), copy.users.get("team").get(0));
        assertNotSame(source.arrays.iterator().next(), copy.arrays.iterator().next());
        assertNotSame(source.dynamic, copy.dynamic);
        assertNotSame(source.dynamic.getJsonArray("items"), copy.dynamic.getJsonArray("items"));

        copy.users.get("team").get(0).name = "Beth";
        copy.arrays.iterator().next()[0] = 9;
        copy.dynamic.getJsonArray("items").getJsonObject(0).put("v", 2);
        assertEquals("Ann", source.users.get("team").get(0).name);
        assertEquals(1, source.arrays.iterator().next()[0]);
        assertEquals(1, source.dynamic.getJsonArray("items").getJsonObject(0).getInt("v"));
    }

    @Test
    void convertsCreatorPojosAndTypedOneOfContainers() {
        Map<String, Object> creatorSource = new LinkedHashMap<>();
        creatorSource.put("city", "Shanghai");
        creatorSource.put("n", "Ann");
        creatorSource.put("scores", Arrays.asList(1L, 2L));
        CreatedUser created = (CreatedUser) binding.readNode(creatorSource, CreatedUser.class);
        assertEquals("Ann", created.name);
        assertEquals(Arrays.asList(1, 2), created.scores);
        assertEquals("Shanghai", created.city);

        AnimalContainers containers = (AnimalContainers) binding.readNode(JsonObject.of(
                "animals", JsonArray.of(JsonObject.of("kind", "cat", "lives", 9)),
                "byName", JsonObject.of("rex", JsonObject.of("kind", "dog", "bark", 3)),
                "array", JsonArray.of(JsonObject.of("kind", "cat", "lives", 7))), AnimalContainers.class);
        assertInstanceOf(Cat.class, containers.animals.get(0));
        assertInstanceOf(Dog.class, containers.byName.get("rex"));
        assertInstanceOf(Cat.class, containers.array[0]);
    }

    @Test
    void writesJsonCompatibleTreeWithoutRetainingMutableContainers() {
        User user = new User();
        user.name = "Ann";
        user.age = 7;
        user.scores = new LinkedHashMap<>();
        user.scores.put("math", 100);
        user.friends = Arrays.asList(new User());
        user.friends.get(0).name = "Ben";

        Map<String, Object> written = (Map<String, Object>) binding.writeNode(user);
        assertEquals("Ann", written.get("name"));
        assertEquals(100, ((Map<?, ?>) written.get("scores")).get("math"));
        assertEquals("Ben", ((Map<?, ?>) ((List<?>) written.get("friends")).get(0)).get("name"));
        assertNotSame(user.scores, written.get("scores"));

        assertEquals(Arrays.asList("x", "ACTIVE"), binding.writeNode(new Object[]{'x', Status.ACTIVE}));
        assertEquals(Arrays.asList("a", "b"), binding.writeNode(new LinkedHashSet<>(Arrays.asList("a", "b"))));
        assertNull(binding.writeNode(null));
        assertThrows(BindingException.class, () -> binding.writeNode(new Object()));
    }

    @Test
    void usesStreamingContextValueFormatForValueCodecs() {
        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        SimpleNodeBinding configured = new SimpleNodeBinding(
                new StreamingContext(Map.of(Instant.class, "epochMillis")));

        assertEquals(instant.toEpochMilli(), configured.writeNode(instant));
        assertEquals(instant, configured.readNode(instant.toEpochMilli(), Instant.class));
    }

    @Test
    void rejectsNullStreamingContext() {
        assertThrows(NullPointerException.class, () -> new SimpleNodeBinding(null));
    }

    @Test
    void readsOneOfByRootAndFieldDiscriminators() {
        Animal animal = (Animal) binding.readNode(
                JsonObject.of("kind", "cat", "name", "Nana", "lives", 7), Animal.class);
        assertInstanceOf(Cat.class, animal);
        assertEquals("Nana", animal.name);
        assertEquals(7, ((Cat) animal).lives);

        Zoo zoo = (Zoo) binding.readNode(JsonObject.of(
                "pet", JsonObject.of("kind", "dog", "name", "Bobo", "bark", 3)), Zoo.class);
        assertInstanceOf(Dog.class, zoo.pet);
        assertEquals("Bobo", zoo.pet.name);
        assertEquals(3, ((Dog) zoo.pet).bark);
    }

    @Test
    void readsOneOfByJsonTypeDiscriminator() {
        assertInstanceOf(ObjectPolymorph.class,
                binding.readNode(JsonObject.of("name", "object"), Polymorph.class));
        assertInstanceOf(ArrayPolymorph.class,
                binding.readNode(JsonArray.of(1, 2), Polymorph.class));
    }

    @Test
    void readsJsonArraySubclass() {
        UntypedArray array = (UntypedArray) binding.readNode(Arrays.asList("a", 2), UntypedArray.class);

        assertInstanceOf(UntypedArray.class, array);
        assertEquals(2, array.size());
        assertEquals("a", array.getNode(0));
        assertEquals(2, array.getNode(1));
    }

    @Test
    void readsTypedJsonArraySubclassWithElementConversion() {
        TypedIntegerArray array = (TypedIntegerArray) binding.readNode(JsonArray.of(1L, 2L), TypedIntegerArray.class);

        assertEquals(Integer.class, array.elementType());
        assertEquals(1, array.getNode(0));
        assertEquals(2, array.getNode(1));
    }
}
