package org.sjf4j.facade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@Execution(ExecutionMode.SAME_THREAD)
public class StreamingIOTest {

    private Sjf4j sjf4j = Sjf4j.global();

    private void useJackson2(StreamingContext.StreamingMode mode) {
        sjf4j = Sjf4j.builder(Sjf4j.global())
                .streamingMode(mode)
                .jsonFacadeProvider(Jackson2JsonFacade.provider(new ObjectMapper()))
                .build();
    }

    private void useGson(StreamingContext.StreamingMode mode) {
        sjf4j = Sjf4j.builder(Sjf4j.global())
                .streamingMode(mode)
                .jsonFacadeProvider(GsonJsonFacade.provider(new GsonBuilder()))
                .build();
    }

    private void useFastjson2(StreamingContext.StreamingMode mode) {
        sjf4j = Sjf4j.builder(Sjf4j.global())
                .streamingMode(mode)
                .jsonFacadeProvider(Fastjson2JsonFacade.provider())
                .build();
    }

    @Getter
    @Setter
    static class User {
        String name;
        List<User> friends;
        Map<String, Object> ext = new LinkedHashMap<>();
    }

    // Define a JOJO `User2`
    @Getter @Setter
    static class UserJojo extends JsonObject {
        String name;
        List<UserJojo> friends;
    }

    @Getter
    @Setter
    static class GenericBody {
        String name;
        int age;
    }

    static class PatchResponse<T> extends JsonObject {
        public int code;
        public String msg;
        public T body;
    }

    static class PatchResponseWithCreator<T> extends JsonObject {
        final int code;
        final String msg;
        final T body;

        @NodeCreator
        PatchResponseWithCreator(@NodeProperty("code") int code,
                                 @NodeProperty("msg") String msg,
                                 @NodeProperty("body") T body) {
            this.code = code;
            this.msg = msg;
            this.body = body;
        }
    }

    @Getter
    @Setter
    static class ConcreteContainers {
        HashMap<String, Integer> map;
        LinkedList<Integer> list;
        TreeSet<Integer> set;
    }

    @Getter
    @Setter
    @AnyOf(value = {
            @AnyOf.Mapping(value = Cat.class, when = "cat"),
            @AnyOf.Mapping(value = Dog.class, when = "dog")
    }, key = "kind")
    static class Animal {
        String kind;
        String name;
    }

    @Getter
    @Setter
    static class Cat extends Animal {
        int lives;
    }

    @Getter
    @Setter
    static class Dog extends Animal {
        int bark;
    }

    @Getter
    @Setter
    static class Zoo {
        Animal pet;
    }

    @Getter
    @Setter
    static class ZooGroup {
        List<Animal> pets;
        Map<String, Animal> petMap;
    }

    @Getter
    @Setter
    static class ParentZoo {
        String kind;
        @AnyOf(value = {
                @AnyOf.Mapping(value = Cat.class, when = "cat"),
                @AnyOf.Mapping(value = Dog.class, when = "dog")
        }, key = "kind", scope = AnyOf.Scope.PARENT)
        Animal pet;
    }

    @Getter
    @Setter
    static class ParentZooPath {
        String kind;
        @AnyOf(value = {
                @AnyOf.Mapping(value = Cat.class, when = "cat"),
                @AnyOf.Mapping(value = Dog.class, when = "dog")
        }, path = "$.kind", scope = AnyOf.Scope.PARENT)
        Animal pet;
    }

    @Getter
    @Setter
    @AnyOf(value = {
            @AnyOf.Mapping(value = PathCat.class, when = "cat"),
            @AnyOf.Mapping(value = PathDog.class, when = "dog")
    }, path = "$.meta.kind")
    static class PathAnimal {
        JsonObject meta;
        String name;
    }

    @Getter
    @Setter
    static class PathCat extends PathAnimal {
        int lives;
    }

    @Getter
    @Setter
    static class PathDog extends PathAnimal {
        int bark;
    }

    @Getter
    @Setter
    @AnyOf(value = {
            @AnyOf.Mapping(value = NullableCat.class, when = "cat"),
            @AnyOf.Mapping(value = NullableDog.class, when = "dog")
    }, key = "kind", onNoMatch = AnyOf.OnNoMatch.FAILBACK_NULL)
    static class NullableAnimal {
        String kind;
        String name;
    }

    @Getter
    @Setter
    static class NullableCat extends NullableAnimal {
        int lives;
    }

    @Getter
    @Setter
    static class NullableDog extends NullableAnimal {
        int bark;
    }

    @AnyOf(value = {
            @AnyOf.Mapping(PolyObj.class),
            @AnyOf.Mapping(PolyArr.class)
    })
    interface Poly {}

    static class PolyObj extends JsonObject implements Poly {}
    static class PolyArr extends JsonArray implements Poly {}


    @Test
    void testSkipNode1() {
        useFastjson2(StreamingContext.StreamingMode.SHARED_IO);
        String json = "{\n" +
                "  \"id\": 7,\n" +
                "  \"skipObj\": {\n" +
                "    \"x\": [true, false, null, {\"deep\": \"v, }\"}],\n" +
                "    \"y\": 2\n" +
                "  },\n" +
                "  \"skipArr\": [1,2,{\"a\":[3,4]}],\n" +
                "  \"skipStr\": \"wa,w[]{}a\",\n" +
                "  \"skipNumber\": -334455,\n" +
                "  \"skipBoolean\": false,\n" +
                "  \"skipNull\": null,\n" +
                "  \"name\": \"Jack\"\n" +
                "}";
        User pojo = sjf4j.fromJson(json, User.class);
        log.info("pojo={}", Nodes.inspect(pojo));
        assertEquals("Jack", pojo.name);
    }


    @Test
    public void testExceptionWithPathSegment1() {
        Jackson2JsonFacade facade = new Jackson2JsonFacade(new ObjectMapper(),
                new StreamingContext(StreamingContext.StreamingMode.SHARED_IO));
        String json = "{\n" +
                "  \"name\": \"Alice\",\n" +
                "  \"friends\": [\n" +
                "    {\"name\": \"Bill\", \"active\": true },\n" +
                "    {\n" +
                "      \"name\": \"Cindy\",\n" +
                "      \"friends\": [\n" +
                "        {\"name\": false},\n" +
                "        {\"id\": 5, \"info\": \"blabla\"}\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"age\": 18\n" +
                "}\n";

        Throwable cause = assertThrows(JsonException.class, () -> {
            Object node = facade.readNode(json, UserJojo.class);
            log.info("node={}", node);
        });
        BindingException inner = null;
        while (cause != null) {
            if (cause instanceof BindingException) {
                inner = (BindingException) cause;
                break;
            }
            cause = cause.getCause();
        }
        assertNotNull(inner, "JsonBindingException not found in cause chain");
        log.info("inner:", inner);
    }

    @Test
    public void testExceptionWithPathSegment2() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder(),
                new StreamingContext(StreamingContext.StreamingMode.SHARED_IO));

        UserJojo user1 = new UserJojo();
        user1.name = "Alice";
        user1.put("throws_key", new Object());

        UserJojo user2 = new UserJojo();
        user2.name = "Bill";
        user2.friends = new ArrayList<>();
        user2.friends.add(user1);

//        System.out.println(user1.inspect());
//        System.out.println(facade.writeNodeAsString(user1));
        Throwable cause = assertThrows(JsonException.class, () -> facade.writeNodeAsString(user1));
        BindingException inner = null;
        while (cause != null) {
            if (cause instanceof BindingException) {
                inner = (BindingException) cause;
                break;
            }
            cause = cause.getCause();
        }
        assertNotNull(inner, "JsonBindingException not found in cause chain");
//        assertTrue(inner.getMessage().contains("/@UserJojo{throws_key"));
    }

    @Test
    void testAnyOfByDiscriminatorOnField() {
        useGson(StreamingContext.StreamingMode.SHARED_IO);
        String json = "{\"pet\":{\"kind\":\"cat\",\"name\":\"Mimi\",\"lives\":9}}";

        Zoo zoo = sjf4j.fromJson(json, Zoo.class);
        assertNotNull(zoo);
        assertInstanceOf(Cat.class, zoo.pet);
        assertEquals("Mimi", zoo.pet.getName());
        assertEquals(9, ((Cat) zoo.pet).getLives());
    }

    @Test
    void testAnyOfByDiscriminatorOnRootType() {
        useJackson2(StreamingContext.StreamingMode.SHARED_IO);
        String json = "{\"kind\":\"dog\",\"name\":\"Lucky\",\"bark\":3}";

        Animal animal = sjf4j.fromJson(json, Animal.class);
        assertNotNull(animal);
        assertInstanceOf(Dog.class, animal);
        assertEquals("Lucky", animal.getName());
        assertEquals(3, ((Dog) animal).getBark());
    }

    @Test
    void testAnyOfByJsonTypeWithoutDiscriminator() {
        useFastjson2(StreamingContext.StreamingMode.SHARED_IO);

        Poly p1 = sjf4j.fromJson("{\"a\":1}", Poly.class);
        Poly p2 = sjf4j.fromJson("[1,2,3]", Poly.class);

        assertInstanceOf(PolyObj.class, p1);
        assertInstanceOf(PolyArr.class, p2);
    }

    @Test
    void testAnyOfParentDiscriminatorEarly() {
        useJackson2(StreamingContext.StreamingMode.SHARED_IO);
        String json = "{\"kind\":\"cat\",\"pet\":{\"name\":\"Mimi\",\"lives\":9}}";

        ParentZoo zoo = sjf4j.fromJson(json, ParentZoo.class);
        assertNotNull(zoo);
        assertInstanceOf(Cat.class, zoo.pet);
        assertEquals("Mimi", zoo.pet.getName());
        assertEquals(9, ((Cat) zoo.pet).getLives());
    }

    @Test
    void testAnyOfParentDiscriminatorLate() {
        useFastjson2(StreamingContext.StreamingMode.SHARED_IO);
        String json = "{\"pet\":{\"name\":\"Lucky\",\"bark\":3},\"kind\":\"dog\"}";

        ParentZoo zoo = sjf4j.fromJson(json, ParentZoo.class);
        assertNotNull(zoo);
        assertInstanceOf(Dog.class, zoo.pet);
        assertEquals("Lucky", zoo.pet.getName());
        assertEquals(3, ((Dog) zoo.pet).getBark());
    }

    @Test
    void testAnyOfParentPathNotSupported() {
        useJackson2(StreamingContext.StreamingMode.SHARED_IO);
        System.out.println(Nodes.inspect(sjf4j));
        String json = "{\"kind\":\"cat\",\"pet\":{\"name\":\"Mimi\",\"lives\":9}}";

        assertThrows(JsonException.class, () -> sjf4j.fromJson(json, ParentZooPath.class));
    }

    private void assertAnyOfByDiscriminatorOnField() {
        String json = "{\"pet\":{\"kind\":\"cat\",\"name\":\"Mimi\",\"lives\":9}}";
        Zoo zoo = sjf4j.fromJson(json, Zoo.class);
        assertNotNull(zoo);
        assertInstanceOf(Cat.class, zoo.pet);
        assertEquals("Mimi", zoo.pet.getName());
        assertEquals(9, ((Cat) zoo.pet).getLives());
    }

    private void assertAnyOfInContainers() {
        String json = "{\"pets\":[{\"kind\":\"cat\",\"name\":\"Mimi\",\"lives\":9},"
                + "{\"kind\":\"dog\",\"name\":\"Lucky\",\"bark\":3}],"
                + "\"petMap\":{\"a\":{\"kind\":\"cat\",\"name\":\"Mini\",\"lives\":7}}}";
        ZooGroup zoo = sjf4j.fromJson(json, ZooGroup.class);
        assertNotNull(zoo);
        assertInstanceOf(Cat.class, zoo.pets.get(0));
        assertInstanceOf(Dog.class, zoo.pets.get(1));
        assertInstanceOf(Cat.class, zoo.petMap.get("a"));
    }

    private void assertAnyOfByJsonTypeOnRoot() {
        Poly p1 = sjf4j.fromJson("{\"a\":1}", Poly.class);
        Poly p2 = sjf4j.fromJson("[1,2,3]", Poly.class);
        assertInstanceOf(PolyObj.class, p1);
        assertInstanceOf(PolyArr.class, p2);
    }

    private void assertAnyOfByCurrentPath() {
        String json = "{\"meta\":{\"kind\":\"cat\"},\"name\":\"Mimi\",\"lives\":9}";
        PathAnimal animal = sjf4j.fromJson(json, PathAnimal.class);
        assertNotNull(animal);
        assertInstanceOf(PathCat.class, animal);
        assertEquals("Mimi", animal.getName());
        assertEquals(9, ((PathCat) animal).getLives());
    }

    private void assertAnyOfFailbackNull() {
        NullableAnimal animal = sjf4j.fromJson("{\"kind\":\"bird\",\"name\":\"Sky\"}", NullableAnimal.class);
        assertNull(animal);
    }

    private void assertAnyOfParentDiscriminatorLateCase() {
        String json = "{\"pet\":{\"name\":\"Lucky\",\"bark\":3},\"kind\":\"dog\"}";
        ParentZoo zoo = sjf4j.fromJson(json, ParentZoo.class);
        assertNotNull(zoo);
        assertInstanceOf(Dog.class, zoo.pet);
        assertEquals("Lucky", zoo.pet.getName());
        assertEquals(3, ((Dog) zoo.pet).getBark());
    }

    @Test
    void testAnyOfPluginModuleByDiscriminatorOnFieldAllBackends() {
        useJackson2(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByDiscriminatorOnField();

        useGson(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByDiscriminatorOnField();

        useFastjson2(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByDiscriminatorOnField();
    }

    @Test
    void testAnyOfPluginModuleByJsonTypeOnRootAllBackends() {
        useJackson2(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByJsonTypeOnRoot();

        useGson(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByJsonTypeOnRoot();

        useFastjson2(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByJsonTypeOnRoot();
    }

    @Test
    void testAnyOfCurrentPathSharedIoAllBackends() {
        useJackson2(StreamingContext.StreamingMode.SHARED_IO);
        assertAnyOfByCurrentPath();

        useGson(StreamingContext.StreamingMode.SHARED_IO);
        assertAnyOfByCurrentPath();

        useFastjson2(StreamingContext.StreamingMode.SHARED_IO);
        assertAnyOfByCurrentPath();
    }

    @Test
    void testAnyOfCurrentPathPluginModuleAllBackends() {
        useJackson2(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByCurrentPath();

        useGson(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByCurrentPath();

        useFastjson2(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByCurrentPath();
    }

    @Test
    void testAnyOfFailbackNullSharedIoAllBackends() {
        useJackson2(StreamingContext.StreamingMode.SHARED_IO);
        assertAnyOfFailbackNull();

        useGson(StreamingContext.StreamingMode.SHARED_IO);
        assertAnyOfFailbackNull();

        useFastjson2(StreamingContext.StreamingMode.SHARED_IO);
        assertAnyOfFailbackNull();
    }

    @Test
    void testAnyOfFailbackNullPluginModuleAllBackends() {
        useJackson2(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfFailbackNull();

        useGson(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfFailbackNull();

        useFastjson2(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfFailbackNull();
    }

    @Test
    void testAnyOfPluginModuleParentDiscriminatorLateAllBackends() {
        useJackson2(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfParentDiscriminatorLateCase();

        useGson(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfParentDiscriminatorLateCase();

        useFastjson2(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertAnyOfParentDiscriminatorLateCase();
    }

    @Test
    void testAnyOfInContainersSharedIoAllBackends() {
        useJackson2(StreamingContext.StreamingMode.SHARED_IO);
        assertAnyOfInContainers();

        useGson(StreamingContext.StreamingMode.SHARED_IO);
        assertAnyOfInContainers();

        useFastjson2(StreamingContext.StreamingMode.SHARED_IO);
        assertAnyOfInContainers();
    }

    private void assertGenericPatchResponse() {
        String json = "{\"code\":200,\"msg\":\"ok\",\"body\":{\"name\":\"Han\",\"age\":18}}";
        PatchResponse<GenericBody> response = sjf4j.fromJson(json, new TypeReference<PatchResponse<GenericBody>>() {});
        assertEquals(200, response.code);
        assertEquals("ok", response.msg);
        assertInstanceOf(GenericBody.class, response.body);
        assertEquals("Han", response.body.name);
        assertEquals(18, response.body.age);
    }

    private void assertGenericPatchResponseWithCreator() {
        String json = "{\"code\":200,\"msg\":\"ok\",\"body\":{\"name\":\"Han\",\"age\":18}}";
        PatchResponseWithCreator<GenericBody> response = sjf4j.fromJson(json,
                new TypeReference<PatchResponseWithCreator<GenericBody>>() {});
        assertEquals(200, response.code);
        assertEquals("ok", response.msg);
        assertInstanceOf(GenericBody.class, response.body);
        assertEquals("Han", response.body.name);
        assertEquals(18, response.body.age);
    }

    private void assertConcreteContainerTargets() {
        HashMap<String, Integer> map = sjf4j.fromJson("{\"b\":2,\"a\":1}",
                new TypeReference<HashMap<String, Integer>>() {});
        assertInstanceOf(HashMap.class, map);
        assertEquals(2, map.get("b"));

        LinkedList<Integer> list = sjf4j.fromJson("[2,1,3]",
                new TypeReference<LinkedList<Integer>>() {});
        assertInstanceOf(LinkedList.class, list);
        assertEquals(Arrays.asList(2, 1, 3), list);

        Set<Integer> set = sjf4j.fromJson("[2,1,3]",
                new TypeReference<TreeSet<Integer>>() {});
        assertInstanceOf(TreeSet.class, set);
        assertEquals(Arrays.asList(1, 2, 3), new ArrayList<>(set));

        ConcreteContainers holder = sjf4j.fromJson("{\"map\":{\"a\":1},\"list\":[2,1,3],\"set\":[2,1,3]}",
                ConcreteContainers.class);
        assertInstanceOf(HashMap.class, holder.map);
        assertInstanceOf(LinkedList.class, holder.list);
        assertInstanceOf(TreeSet.class, holder.set);

        assertThrows(JsonException.class, () -> sjf4j.fromJson("{\"a\":1}", SortedMap.class));
        assertThrows(JsonException.class, () -> sjf4j.fromJson("[2,1,3]", SortedSet.class));
    }

    @Test
    void testGenericJojoBindingSharedIo() {
        useJackson2(StreamingContext.StreamingMode.SHARED_IO);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();

        useGson(StreamingContext.StreamingMode.SHARED_IO);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();

        useFastjson2(StreamingContext.StreamingMode.SHARED_IO);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();
    }

    @Test
    void testConcreteContainerTargetsSharedIo() {
        useJackson2(StreamingContext.StreamingMode.SHARED_IO);
        assertConcreteContainerTargets();

        useGson(StreamingContext.StreamingMode.SHARED_IO);
        assertConcreteContainerTargets();

        useFastjson2(StreamingContext.StreamingMode.SHARED_IO);
        assertConcreteContainerTargets();
    }

    @Test
    void testGenericJojoBindingPluginModuleJackson2() {
        useJackson2(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();
    }

    @Test
    void testGenericJojoBindingPluginModuleGson() {
        useGson(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();
    }

    @Test
    void testGenericJojoBindingPluginModuleFastjson2() {
        useFastjson2(StreamingContext.StreamingMode.PLUGIN_MODULE);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();
    }


}
