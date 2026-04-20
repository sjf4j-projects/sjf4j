package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.node.AccessStrategy;
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
                modeTests("dynamic-binding", Fastjson2FacadeTest::assertDynamicBinding),
                modeTests("write-only-hidden", Fastjson2FacadeTest::assertWriteOnlyMembersAreNotSerialized),
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

    @NodeBinding(readDynamic = false)
    public static class ReadDisabledBook extends JsonObject {
        public int id;
        public String name;
    }

    @NodeBinding(writeDynamic = false)
    public static class WriteDisabledBook extends JsonObject {
        public int id;
        public String name;
    }

    static class WriteOnlyPojo {
        private String name;
        private String secret;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    @NodeBinding(writeDynamic = false)
    static class WriteOnlyJojo extends JsonObject {
        private String name;
        private String secret;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    private static void assertReadModule(Fastjson2JsonFacade facade) {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        JsonObject jo1 = (JsonObject) facade.readNode(json1, JsonObject.class);

        Book jo2 = (Book) facade.readNode(json1, Book.class);
        assertEquals(20, jo2.getIntByPath("/friends/rose/age/1"));

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

    private static void assertDynamicBinding(Fastjson2JsonFacade facade) {
        ReadDisabledBook readBook = (ReadDisabledBook) facade.readNode("{\"id\":1,\"name\":\"a\",\"extra\":2}",
                ReadDisabledBook.class);
        assertNull(readBook.get("extra"));
        readBook.put("runtime", 3);
        assertEquals("{\"id\":1,\"name\":\"a\",\"runtime\":3}", facade.writeNodeAsString(readBook));

        WriteDisabledBook writeBook = (WriteDisabledBook) facade.readNode("{\"id\":1,\"name\":\"a\",\"extra\":2}",
                WriteDisabledBook.class);
        assertEquals(2, writeBook.getInt("extra"));
        writeBook.put("runtime", 3);
        assertEquals("{\"id\":1,\"name\":\"a\"}", facade.writeNodeAsString(writeBook));
    }

    private static void assertWriteOnlyMembersAreNotSerialized(Fastjson2JsonFacade facade) {
        WriteOnlyPojo pojo = new WriteOnlyPojo();
        pojo.setName("han");
        pojo.setSecret("hidden");
        assertEquals("{\"name\":\"han\"}", facade.writeNodeAsString(pojo));

        WriteOnlyJojo jojo = new WriteOnlyJojo();
        jojo.setName("han");
        jojo.setSecret("hidden");
        jojo.put("runtime", 1);
        assertEquals("{\"name\":\"han\"}", facade.writeNodeAsString(jojo));
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
        NodeRegistry.registerTypeInfo(Ops.class);

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
        String json2 = "{\"user_name\":\"han\",\"id\":123,\"name\":null,\"height\":175.5,\"transientHeight\":189.9}";
        BookField jo1 = (BookField) facade.readNode(new StringReader(json1), BookField.class);
        assertEquals("han", jo1.userName);
        assertEquals("han", jo1.getString("user_name"));
        assertNull(jo1.getString("userName"));

        assertEquals(0.0, jo1.height);
        assertEquals(0, jo1.transientHeight);

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, jo1);
        assertEquals(json2, sw.toString());
    }

    @NodeBinding(naming = NamingStrategy.SNAKE_CASE)
    public static class SnakeBook extends JsonObject {
        public String userName;
        public int loginCount;
    }

    @NodeBinding(naming = NamingStrategy.SNAKE_CASE)
    public static class SnakePlainBook {
        public String userName;
        public int loginCount;
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
        public String name;
        public List<User> friends;
        public Map<String, Object> ext;
    }

    static class PublicPlainBook {
        public String userName;
        public int loginCount;
    }

    static class AccessorBook {
        private String userName;
        private int loginCount;

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public int getLoginCount() { return loginCount; }
        public void setLoginCount(int loginCount) { this.loginCount = loginCount; }
    }

    static class PlainPrivateBook {
        String userName;
        int loginCount;
    }

    @NodeBinding(access = AccessStrategy.FIELD_BASED)
    static class FieldBasedPrivateBook {
        String userName;
        int loginCount;
    }

    @NodeBinding(access = AccessStrategy.FIELD_BASED)
    static class IsolatedFieldBasedPrivateBook {
        String userName;
        int loginCount;
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
        assertSkipNode(new Fastjson2JsonFacade(StreamingFacade.StreamingMode.SHARED_IO));
    }

    @Test
    void testPluginModuleAllowsNativeEquivalentPublicPojo() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        PublicPlainBook book = (PublicPlainBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PublicPlainBook.class);
        assertEquals("han", book.userName);
        assertEquals(2, book.loginCount);
        assertEquals("{\"loginCount\":2,\"userName\":\"han\"}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleAllowsBeanAccessorPojo() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        AccessorBook book = (AccessorBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                AccessorBook.class);
        assertEquals("han", book.getUserName());
        assertEquals(2, book.getLoginCount());
        assertEquals("{\"loginCount\":2,\"userName\":\"han\"}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleBeanOnlySkipsNonPublicPlainPojo() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        PlainPrivateBook book = (PlainPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PlainPrivateBook.class);
        assertNull(book.userName);
        assertEquals(0, book.loginCount);
        assertEquals("{}", facade.writeNodeAsString(book));
    }

    @Test
    void testAutoModePrefersPluginModule() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade();
        PlainPrivateBook book = (PlainPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PlainPrivateBook.class);
        assertNull(book.userName);
        assertEquals(0, book.loginCount);
        assertEquals("{}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleFieldBasedAnnotatedAllowsNonPublicPlainPojo() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        FieldBasedPrivateBook book = (FieldBasedPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                FieldBasedPrivateBook.class);
        assertEquals("han", book.userName);
        assertEquals(2, book.loginCount);
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleDoesNotPolluteGlobalFastjson2Providers() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade(StreamingFacade.StreamingMode.PLUGIN_MODULE);

        IsolatedFieldBasedPrivateBook local = (IsolatedFieldBasedPrivateBook) facade.readNode(
                "{\"userName\":\"han\",\"loginCount\":2}", IsolatedFieldBasedPrivateBook.class);
        assertEquals("han", local.userName);
        assertEquals(2, local.loginCount);

        IsolatedFieldBasedPrivateBook global = JSON.parseObject(
                "{\"userName\":\"han\",\"loginCount\":2}", IsolatedFieldBasedPrivateBook.class);
        assertNull(global.userName);
        assertEquals(0, global.loginCount);

        local.userName = "han";
        local.loginCount = 2;
        assertEquals("{}", JSON.toJSONString(local));
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
        assertTrue(ex.getMessage().contains("Failed to read JSON"));
        assertFalse(ex.getMessage().contains("AnyOf is not supported in Fastjson2 PLUGIN_MODULE mode"));
    }

}
