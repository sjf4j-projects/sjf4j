package org.sjf4j.facade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson.JacksonJsonFacade;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@Execution(ExecutionMode.SAME_THREAD)
public class StreamingIOTest {

    private Sjf4jConfig previousConfig;

    @BeforeEach
    void saveGlobalConfig() {
        previousConfig = Sjf4jConfig.global();
    }

    @AfterEach
    void restoreGlobalConfig() {
        if (previousConfig != null) {
            Sjf4jConfig.global(previousConfig);
        }
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
        int code;
        String msg;
        T body;
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

    @AnyOf(value = {
            @AnyOf.Mapping(PolyObj.class),
            @AnyOf.Mapping(PolyArr.class)
    })
    interface Poly {}

    static class PolyObj extends JsonObject implements Poly {}
    static class PolyArr extends JsonArray implements Poly {}


    @Test
    void testSkipNode1() {
        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
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
        User pojo = Sjf4j.fromJson(json, User.class);
        log.info("pojo={}", Nodes.inspect(pojo));
        assertEquals("Jack", pojo.name);
    }


    @Test
    public void testExceptionWithPathSegment1() {
        JacksonJsonFacade facade = new JacksonJsonFacade(new ObjectMapper(), StreamingFacade.StreamingMode.SHARED_IO);
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
//        assertTrue(inner.getMessage().contains("/@UserJojo{*friends/[1/@UserJojo{*friends/[0/@UserJojo{*name"));
    }

    @Test
    public void testExceptionWithPathSegment2() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder());

        UserJojo user1 = new UserJojo();
        user1.name = "Alice";
        user1.put("throws_key", new Object());
        UserJojo user2 = new UserJojo();
        user2.name = "Bill";
        user2.friends = new ArrayList<>();
        user2.friends.add(user1);

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
        Sjf4jConfig.useGsonAsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        String json = "{\"pet\":{\"kind\":\"cat\",\"name\":\"Mimi\",\"lives\":9}}";

        Zoo zoo = Sjf4j.fromJson(json, Zoo.class);
        assertNotNull(zoo);
        assertTrue(zoo.pet instanceof Cat);
        assertEquals("Mimi", zoo.pet.getName());
        assertEquals(9, ((Cat) zoo.pet).getLives());
    }

    @Test
    void testAnyOfByDiscriminatorOnRootType() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        String json = "{\"kind\":\"dog\",\"name\":\"Lucky\",\"bark\":3}";

        Animal animal = Sjf4j.fromJson(json, Animal.class);
        assertNotNull(animal);
        assertTrue(animal instanceof Dog);
        assertEquals("Lucky", animal.getName());
        assertEquals(3, ((Dog) animal).getBark());
    }

    @Test
    void testAnyOfByJsonTypeWithoutDiscriminator() {
        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.SHARED_IO);

        Poly p1 = Sjf4j.fromJson("{\"a\":1}", Poly.class);
        Poly p2 = Sjf4j.fromJson("[1,2,3]", Poly.class);

        assertTrue(p1 instanceof PolyObj);
        assertTrue(p2 instanceof PolyArr);
    }

    @Test
    void testAnyOfParentDiscriminatorEarly() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        String json = "{\"kind\":\"cat\",\"pet\":{\"name\":\"Mimi\",\"lives\":9}}";

        ParentZoo zoo = Sjf4j.fromJson(json, ParentZoo.class);
        assertNotNull(zoo);
        assertTrue(zoo.pet instanceof Cat);
        assertEquals("Mimi", zoo.pet.getName());
        assertEquals(9, ((Cat) zoo.pet).getLives());
    }

    @Test
    void testAnyOfParentDiscriminatorLate() {
        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        String json = "{\"pet\":{\"name\":\"Lucky\",\"bark\":3},\"kind\":\"dog\"}";

        ParentZoo zoo = Sjf4j.fromJson(json, ParentZoo.class);
        assertNotNull(zoo);
        assertTrue(zoo.pet instanceof Dog);
        assertEquals("Lucky", zoo.pet.getName());
        assertEquals(3, ((Dog) zoo.pet).getBark());
    }

    @Test
    void testAnyOfParentPathNotSupported() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        System.out.println(Sjf4jConfig.global().inspect());
        String json = "{\"kind\":\"cat\",\"pet\":{\"name\":\"Mimi\",\"lives\":9}}";

        assertThrows(JsonException.class, () -> Sjf4j.fromJson(json, ParentZooPath.class));
    }

    private static void assertAnyOfByDiscriminatorOnField() {
        String json = "{\"pet\":{\"kind\":\"cat\",\"name\":\"Mimi\",\"lives\":9}}";
        Zoo zoo = Sjf4j.fromJson(json, Zoo.class);
        assertNotNull(zoo);
        assertTrue(zoo.pet instanceof Cat);
        assertEquals("Mimi", zoo.pet.getName());
        assertEquals(9, ((Cat) zoo.pet).getLives());
    }

    private static void assertAnyOfInContainers() {
        String json = "{\"pets\":[{\"kind\":\"cat\",\"name\":\"Mimi\",\"lives\":9},"
                + "{\"kind\":\"dog\",\"name\":\"Lucky\",\"bark\":3}],"
                + "\"petMap\":{\"a\":{\"kind\":\"cat\",\"name\":\"Mini\",\"lives\":7}}}";
        ZooGroup zoo = Sjf4j.fromJson(json, ZooGroup.class);
        assertNotNull(zoo);
        assertTrue(zoo.pets.get(0) instanceof Cat);
        assertTrue(zoo.pets.get(1) instanceof Dog);
        assertTrue(zoo.petMap.get("a") instanceof Cat);
    }

    private static void assertAnyOfByJsonTypeOnRoot() {
        Poly p1 = Sjf4j.fromJson("{\"a\":1}", Poly.class);
        Poly p2 = Sjf4j.fromJson("[1,2,3]", Poly.class);
        assertTrue(p1 instanceof PolyObj);
        assertTrue(p2 instanceof PolyArr);
    }

    private static void assertAnyOfParentDiscriminatorLateCase() {
        String json = "{\"pet\":{\"name\":\"Lucky\",\"bark\":3},\"kind\":\"dog\"}";
        ParentZoo zoo = Sjf4j.fromJson(json, ParentZoo.class);
        assertNotNull(zoo);
        assertTrue(zoo.pet instanceof Dog);
        assertEquals("Lucky", zoo.pet.getName());
        assertEquals(3, ((Dog) zoo.pet).getBark());
    }

    @Test
    void testAnyOfPluginModuleByDiscriminatorOnFieldAllBackends() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByDiscriminatorOnField();

        Sjf4jConfig.useGsonAsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByDiscriminatorOnField();

        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByDiscriminatorOnField();
    }

    @Test
    void testAnyOfPluginModuleByJsonTypeOnRootAllBackends() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByJsonTypeOnRoot();

        Sjf4jConfig.useGsonAsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByJsonTypeOnRoot();

        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        assertAnyOfByJsonTypeOnRoot();
    }

    @Test
    void testAnyOfPluginModuleParentDiscriminatorLateAllBackends() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        assertAnyOfParentDiscriminatorLateCase();

        Sjf4jConfig.useGsonAsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        assertAnyOfParentDiscriminatorLateCase();

        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        assertAnyOfParentDiscriminatorLateCase();
    }

    @Test
    void testAnyOfInContainersSharedIoAllBackends() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        assertAnyOfInContainers();

        Sjf4jConfig.useGsonAsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        assertAnyOfInContainers();

        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        assertAnyOfInContainers();
    }

    @Test
    void testAnyOfInContainersExclusiveIoAllBackends() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.EXCLUSIVE_IO);
        assertAnyOfInContainers();

        Sjf4jConfig.useGsonAsGlobal(StreamingFacade.StreamingMode.EXCLUSIVE_IO);
        assertAnyOfInContainers();

        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.EXCLUSIVE_IO);
        assertAnyOfInContainers();
    }

    private static void assertGenericPatchResponse() {
        String json = "{\"code\":200,\"msg\":\"ok\",\"body\":{\"name\":\"Han\",\"age\":18}}";
        PatchResponse<GenericBody> response = Sjf4j.fromJson(json, new TypeReference<PatchResponse<GenericBody>>() {});
        assertEquals(200, response.code);
        assertEquals("ok", response.msg);
        assertInstanceOf(GenericBody.class, response.body);
        assertEquals("Han", response.body.name);
        assertEquals(18, response.body.age);
    }

    private static void assertGenericPatchResponseWithCreator() {
        String json = "{\"code\":200,\"msg\":\"ok\",\"body\":{\"name\":\"Han\",\"age\":18}}";
        PatchResponseWithCreator<GenericBody> response = Sjf4j.fromJson(json,
                new TypeReference<PatchResponseWithCreator<GenericBody>>() {});
        assertEquals(200, response.code);
        assertEquals("ok", response.msg);
        assertInstanceOf(GenericBody.class, response.body);
        assertEquals("Han", response.body.name);
        assertEquals(18, response.body.age);
    }

    private static void assertConcreteContainerTargets() {
        HashMap<String, Integer> map = Sjf4j.fromJson("{\"b\":2,\"a\":1}",
                new TypeReference<HashMap<String, Integer>>() {});
        assertInstanceOf(HashMap.class, map);
        assertEquals(2, map.get("b"));

        LinkedList<Integer> list = Sjf4j.fromJson("[2,1,3]",
                new TypeReference<LinkedList<Integer>>() {});
        assertInstanceOf(LinkedList.class, list);
        assertEquals(Arrays.asList(2, 1, 3), list);

        Set<Integer> set = Sjf4j.fromJson("[2,1,3]",
                new TypeReference<TreeSet<Integer>>() {});
        assertInstanceOf(TreeSet.class, set);
        assertEquals(Arrays.asList(1, 2, 3), new ArrayList<>(set));

        ConcreteContainers holder = Sjf4j.fromJson("{\"map\":{\"a\":1},\"list\":[2,1,3],\"set\":[2,1,3]}",
                ConcreteContainers.class);
        assertInstanceOf(HashMap.class, holder.map);
        assertInstanceOf(LinkedList.class, holder.list);
        assertInstanceOf(TreeSet.class, holder.set);

        assertThrows(JsonException.class, () -> Sjf4j.fromJson("{\"a\":1}", SortedMap.class));
        assertThrows(JsonException.class, () -> Sjf4j.fromJson("[2,1,3]", SortedSet.class));
    }

    @Test
    void testGenericJojoBindingSharedIo() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();

        Sjf4jConfig.useGsonAsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();

        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();
    }

    @Test
    void testConcreteContainerTargetsSharedIo() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        assertConcreteContainerTargets();

        Sjf4jConfig.useGsonAsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        assertConcreteContainerTargets();

        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.SHARED_IO);
        assertConcreteContainerTargets();
    }

    @Test
    void testConcreteContainerTargetsExclusiveIo() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.EXCLUSIVE_IO);
        assertConcreteContainerTargets();

        Sjf4jConfig.useGsonAsGlobal(StreamingFacade.StreamingMode.EXCLUSIVE_IO);
        assertConcreteContainerTargets();

        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.EXCLUSIVE_IO);
        assertConcreteContainerTargets();
    }

    @Test
    void testGenericJojoBindingExclusiveIo() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.EXCLUSIVE_IO);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();

        Sjf4jConfig.useGsonAsGlobal(StreamingFacade.StreamingMode.EXCLUSIVE_IO);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();

        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.EXCLUSIVE_IO);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();
    }

    @Test
    void testGenericJojoBindingPluginModuleJackson() {
        Sjf4jConfig.useJacksonAsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();
    }

    @Test
    void testGenericJojoBindingPluginModuleGson() {
        Sjf4jConfig.useGsonAsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();
    }

    @Test
    void testGenericJojoBindingPluginModuleFastjson2() {
        Sjf4jConfig.useFastjson2AsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        assertGenericPatchResponse();
        assertGenericPatchResponseWithCreator();
    }


}
