package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeNaming;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.node.NamingStrategy;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Fastjson2FacadeTest {

    private static Stream<StreamingFacade.StreamingMode> allModes() {
        return Stream.of(
                StreamingFacade.StreamingMode.SHARED_IO,
                StreamingFacade.StreamingMode.EXCLUSIVE_IO,
                StreamingFacade.StreamingMode.PLUGIN_MODULE
        );
    }

    private static Fastjson2JsonFacade newFacade(StreamingFacade.StreamingMode mode) {
        return new Fastjson2JsonFacade(mode);
    }

    @FunctionalInterface
    private interface ModeCase {
        void run(Fastjson2JsonFacade facade) throws Exception;
    }

    private static Stream<DynamicTest> modeTests(String caseName, ModeCase caze) {
        return allModes().map(mode -> DynamicTest.dynamicTest(caseName + " mode=" + mode, () -> {
            Fastjson2JsonFacade facade = newFacade(mode);
            caze.run(facade);
        }));
    }

    @TestFactory
    Stream<DynamicTest> testSjf4jCasesAcrossModes() {
        return Stream.of(
                modeTests("serde", Fastjson2FacadeTest::assertSerDe),
                modeTests("read-module", Fastjson2FacadeTest::assertReadModule),
                modeTests("write", Fastjson2FacadeTest::assertWrite),
                modeTests("node-value", Fastjson2FacadeTest::assertNodeValue),
                modeTests("node-field", Fastjson2FacadeTest::assertNodeField),
                modeTests("node-naming", Fastjson2FacadeTest::assertNodeNaming),
                modeTests("creator-extra-field", Fastjson2FacadeTest::assertCreatorExtraField),
                modeTests("creator-alias", Fastjson2FacadeTest::assertCreatorAlias),
                modeTests("anyof", Fastjson2FacadeTest::assertAnyOf)
        ).flatMap(s -> s);
    }

    private static void assertSerDe(Fastjson2JsonFacade facade) {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";

        JsonObject jo1 = (JsonObject) facade.readNode(new StringReader(json1), JsonObject.class);
        JsonObject jo2 = (JsonObject) facade.readNode(json1, JsonObject.class);

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, jo1);
        String res1 = sw.toString();
        assertEquals(json1, res1);
        assertEquals(json1, facade.writeNodeAsString(jo2));
    }

    public static class Book extends JsonObject {
        private int id;
        private String name;
    }

    private static void assertReadModule(Fastjson2JsonFacade facade) {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        JsonObject jo1 = (JsonObject) facade.readNode(json1, JsonObject.class);

        Book jo2 = (Book) facade.readNode(json1, Book.class);
        assertEquals(20, jo2.getIntegerByPath("/friends/rose/age/1"));

        String json2 = "[1,2,3]";
        JsonArray ja1 = (JsonArray) facade.readNode(json2, JsonArray.class);
        assertEquals(3, ja1.size());

        Object ja2 = facade.readNode(new StringReader(json2), Object.class);
        assertTrue(ja2 instanceof List);

        Object ja3 = facade.readNode(new StringReader(json2), int[].class);
        assertEquals(int[].class, ja3.getClass());
    }

    private static void assertWrite(Fastjson2JsonFacade facade) {
        String json1 = "{\"id\":123,\"name\":\"han\",\"height\":175.3,\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        Book jo1 = (Book) facade.readNode(new StringReader(json1), Book.class);
        StringWriter output = new StringWriter();
        facade.writeNode(output, jo1);
        assertEquals(json1, output.toString());
    }


    @NodeValue
    public static class Ops {
        private final LocalDate localDate;

        public Ops(LocalDate localDate) {
            this.localDate = localDate;
        }
        @ValueToRaw
        public String encode() {
            return localDate.toString();
        }

        @RawToValue
        public static Ops decode(String raw) {
            return new Ops(LocalDate.parse(raw));
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertNodeValue(Fastjson2JsonFacade facade) {
        NodeRegistry.registerValueCodec(Ops.class);

        String json1 = "[\"2024-10-01\",\"2025-12-18\"]";
        List<Ops> list = (List<Ops>) facade.readNode(json1, new TypeReference<List<Ops>>() {}.getType());
        StringWriter sw = new StringWriter();
        facade.writeNode(sw, list);
        String json2 = sw.toString();
        List<Ops> list2 = (List<Ops>) facade.readNode(json2, new TypeReference<List<Ops>>() {}.getType());
        assertEquals(2, list2.size());
    }


    public static class BookField extends JsonObject {
        private int id;
        private String name;

        @NodeProperty("user_name")
        private String userName;
        private double height;
        private transient int transientHeight;
    }

    private static void assertNodeField(Fastjson2JsonFacade facade) {
        String json1 = "{\"id\":123,\"name\":null,\"user_name\":\"han\",\"height\":175.5,\"transientHeight\":189.9}";
        BookField jo1 = (BookField) facade.readNode(new StringReader(json1), BookField.class);
        assertEquals("han", jo1.userName);
        assertEquals("han", jo1.getString("user_name"));
        assertNull(jo1.getString("userName"));

        assertEquals(175.5, jo1.height);
        assertEquals(0, jo1.transientHeight);

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, jo1);
        String json2 = sw.toString();
        assertEquals(json1, json2);
    }

    @NodeNaming(NamingStrategy.SNAKE_CASE)
    public static class SnakeBook extends JsonObject {
        private String userName;
        private int loginCount;
    }

    @NodeNaming(NamingStrategy.SNAKE_CASE)
    public static class SnakePlainBook {
        private String userName;
        private int loginCount;
    }

    private static void assertNodeNaming(Fastjson2JsonFacade facade) {
        String json = "{\"user_name\":\"han\",\"login_count\":2}";
        SnakeBook jo1 = (SnakeBook) facade.readNode(new StringReader(json), SnakeBook.class);
        assertEquals("han", jo1.userName);
        assertEquals(2, jo1.loginCount);
        assertEquals("han", jo1.getString("user_name"));
        assertNull(jo1.getString("userName"));

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, jo1);
        assertEquals(json, sw.toString());

        SnakePlainBook pojo = (SnakePlainBook) facade.readNode(new StringReader(json), SnakePlainBook.class);
        assertEquals("han", pojo.userName);
        assertEquals(2, pojo.loginCount);
        sw = new StringWriter();
        facade.writeNode(sw, pojo);
        assertEquals(json, sw.toString());
    }


    /// Creator

    static class Ctor2PlusField {
        final String name;
        final int age;
        String city;

        @NodeCreator
        public Ctor2PlusField(@NodeProperty("name") String name,
                              @JSONField(name = "age") int age) {
            this.name = name;
            this.age = age;
        }
        public void setCity(String city) { this.city = city; }
    }

    private static void assertCreatorExtraField(Fastjson2JsonFacade facade) {
        String json = "{\"name\":\"a\",\"age\":1,\"city\":\"sh\"}";
        Ctor2PlusField pojo = (Ctor2PlusField) facade.readNode(json, Ctor2PlusField.class);
        assertEquals("a", pojo.name);
    }

    @Data
    static class CtorAlias {
        final String name;
        final int age;

        @NodeCreator
        public CtorAlias(@NodeProperty(value = "name", aliases = {"n"}) String name,
                         @JSONField(name = "age", alternateNames = {"old"}) int age) {
            this.name = name;
            this.age = age;
        }
    }

    private static void assertCreatorAlias(Fastjson2JsonFacade facade) {
        String json = "{\"n\":\"a\",\"age\":2}";
        CtorAlias pojo = (CtorAlias) facade.readNode(json, CtorAlias.class);
        assertEquals("a", pojo.name);
        assertTrue(facade.writeNodeAsString(pojo).contains("\"age\":2"));
    }

    static class User {
        String name;
        List<User> friends;
        Map<String, Object> ext;
    }

    interface Pet {}
    static class Cat implements TypedPet { public String meow; }
    static class Dog implements TypedPet { public String bark; }
    @AnyOf(value = {
            @AnyOf.Mapping(value = Cat.class, when = "cat"),
            @AnyOf.Mapping(value = Dog.class, when = "dog")
    }, key = "kind")
    interface TypedPet extends Pet {}
    static class PetHolder {
        public TypedPet pet;
    }
    static class ParentPetHolder {
        public String kind;
        @AnyOf(value = {
                @AnyOf.Mapping(value = Cat.class, when = "cat"),
                @AnyOf.Mapping(value = Dog.class, when = "dog")
        }, key = "kind", scope = AnyOf.Scope.PARENT)
        public TypedPet pet;
    }

    private static void assertSkipNode(Fastjson2JsonFacade facade) {
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
        User pojo = (User) facade.readNode(json, User.class);
        assertEquals("Jack", pojo.name);
    }

    @Test
    void testSkipNode1() {
        assertSkipNode(new Fastjson2JsonFacade(StreamingFacade.StreamingMode.EXCLUSIVE_IO));
    }

    private static void assertAnyOf(Fastjson2JsonFacade facade) {
        String anyOfJson = "{\"pet\":{\"kind\":\"cat\",\"meow\":\"m\"}}";
        PetHolder holder = (PetHolder) facade.readNode(anyOfJson, PetHolder.class);
        assertEquals(Cat.class, holder.pet.getClass());
        assertTrue(facade.writeNodeAsString(holder).contains("meow"));
    }

    @Test
    void testPluginModuleAnyOfFailureMessage() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        String json = "{\"kind\":\"cat\",\"pet\":{\"meow\":\"m\"}}";

        ParentPetHolder holder = (ParentPetHolder) facade.readNode(json, ParentPetHolder.class);
        log.info("holder={}", Nodes.inspect(holder));
        log.info("pet.class={}, pet={}", holder.pet.getClass(), holder.pet);
//        assertEquals(json, facade.writeNodeAsString(holder));
    }

    @Test
    void testPluginModuleNormalFailureMessageWithoutAnyOfHint() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade(StreamingFacade.StreamingMode.PLUGIN_MODULE);

        JsonException ex = assertThrows(JsonException.class, () -> facade.readNode("{", Book.class));
        assertTrue(ex.getMessage().contains("Failed to read JSON string into node type"));
        assertFalse(ex.getMessage().contains("AnyOf is not supported in Fastjson2 PLUGIN_MODULE mode"));
    }

}
