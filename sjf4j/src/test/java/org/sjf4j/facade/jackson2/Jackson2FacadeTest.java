package org.sjf4j.facade.jackson2;


import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
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
import org.sjf4j.facade.CodecFacadeAssertions;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.node.AccessStrategy;
import org.sjf4j.node.NamingStrategy;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Jackson2FacadeTest {

    private static StreamingContext ctx(StreamingContext.StreamingMode mode) {
        return new StreamingContext(mode);
    }

    private static Stream<StreamingContext.StreamingMode> allModes() {
        return Stream.of(
                StreamingContext.StreamingMode.SHARED_IO,
                StreamingContext.StreamingMode.EXCLUSIVE_IO,
                StreamingContext.StreamingMode.PLUGIN_MODULE
        );
    }

    private static Jackson2JsonFacade newFacade(StreamingContext.StreamingMode mode) {
        return new Jackson2JsonFacade(new ObjectMapper(), ctx(mode));
    }

    @FunctionalInterface
    private interface ModeCase {
        void run(Jackson2JsonFacade facade) throws Exception;
    }

    private static Stream<DynamicTest> modeTests(String caseName, ModeCase caze) {
        return allModes().map(mode -> DynamicTest.dynamicTest(caseName + " mode=" + mode, () -> {
            Jackson2JsonFacade facade = newFacade(mode);
            caze.run(facade);
        }));
    }

    @Test
    public void testNativeSerDe1() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        Object obj1 = objectMapper.readValue(new StringReader(json1), Object.class);
        String res1 = objectMapper.writeValueAsString(obj1);
        log.info("obj1 {}: {}", obj1.getClass().getName(), res1);
        assertEquals(json1, res1);
    }

    @Test
    void testCustomMapperIntrospectorIsPreserved() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setAnnotationIntrospector(new LegacyNameIntrospector());

        Jackson2JsonFacade facade = new Jackson2JsonFacade(objectMapper, ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        NativeLegacyBook book = (NativeLegacyBook) facade.readNode("{\"legacy_name\":\"legacy\"}",
                NativeLegacyBook.class);
        assertEquals("legacy", book.legacyName);

        String json = facade.writeNodeAsString(book);
        assertTrue(json.contains("\"legacy_name\":\"legacy\""));
        assertFalse(json.contains("legacyName"));
    }

    @Test
    void testSeparateMapperIntrospectorsArePreserved() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setAnnotationIntrospectors(
                new LegacySerializeOnlyIntrospector(),
                new LegacyDeserializeOnlyIntrospector()
        );

        Jackson2JsonFacade facade = new Jackson2JsonFacade(objectMapper, ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        SplitLegacyBook book = (SplitLegacyBook) facade.readNode("{\"legacy_in\":\"legacy\"}", SplitLegacyBook.class);
        assertEquals("legacy", book.legacyName);
        assertEquals("{\"legacy_out\":\"legacy\"}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleFallsBackForNonPublicPlainPojo() {
        Jackson2JsonFacade facade = new Jackson2JsonFacade(new ObjectMapper(), ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        PlainPrivateBook book = (PlainPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PlainPrivateBook.class);
        assertNull(book.userName);
        assertEquals(0, book.loginCount);
        assertEquals("{}", facade.writeNodeAsString(book));
    }

    @Test
    void testAutoModePrefersPluginModule() {
        Jackson2JsonFacade facade = new Jackson2JsonFacade(new ObjectMapper());
        PlainPrivateBook book = (PlainPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PlainPrivateBook.class);
        assertNull(book.userName);
        assertEquals(0, book.loginCount);
        assertEquals("{}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleAllowsNativeEquivalentPublicPojo() {
        Jackson2JsonFacade facade = new Jackson2JsonFacade(new ObjectMapper(), ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        PublicPlainBook book = (PublicPlainBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PublicPlainBook.class);
        assertEquals("han", book.userName);
        assertEquals(2, book.loginCount);
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleAllowsNativeEquivalentAccessorPojo() {
        Jackson2JsonFacade facade = new Jackson2JsonFacade(new ObjectMapper(), ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        AccessorBook book = (AccessorBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                AccessorBook.class);
        assertEquals("han", book.getUserName());
        assertEquals(2, book.getLoginCount());
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    @Test
    void testFieldBasedAnnotatedAllowsNonPublicPlainPojo() {
        Jackson2JsonFacade facade = new Jackson2JsonFacade(new ObjectMapper(), ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        FieldBasedPrivateBook book = (FieldBasedPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                FieldBasedPrivateBook.class);
        assertEquals("han", book.userName);
        assertEquals(2, book.loginCount);
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    private static void assertSerDe(Jackson2JsonFacade facade) {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        JsonObject jo1 = (JsonObject) facade.readNode(new StringReader(json1), JsonObject.class);
        StringWriter sw = new StringWriter();
        facade.writeNode(sw, jo1);
        assertEquals(json1, sw.toString());
    }


    public static class Book extends JsonObject {
        private int id;
        private String name;
    }

    @NodeBinding(readDynamic = false)
    static class ReadDisabledBook extends JsonObject {
        public int id;
        public String name;
    }

    @NodeBinding(writeDynamic = false)
    static class WriteDisabledBook extends JsonObject {
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

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface LegacyName {
        String value();
    }

    static class LegacyNameIntrospector extends JacksonAnnotationIntrospector {
        @Override
        public PropertyName findNameForSerialization(Annotated ann) {
            if (ann instanceof AnnotatedField) {
                LegacyName legacyName = ((AnnotatedField) ann).getAnnotated().getAnnotation(LegacyName.class);
                if (legacyName != null) {
                    return PropertyName.construct(legacyName.value());
                }
            }
            return super.findNameForSerialization(ann);
        }

        @Override
        public PropertyName findNameForDeserialization(Annotated ann) {
            if (ann instanceof AnnotatedField) {
                LegacyName legacyName = ((AnnotatedField) ann).getAnnotated().getAnnotation(LegacyName.class);
                if (legacyName != null) {
                    return PropertyName.construct(legacyName.value());
                }
            }
            return super.findNameForDeserialization(ann);
        }
    }

    static class LegacySerializeOnlyIntrospector extends JacksonAnnotationIntrospector {
        @Override
        public PropertyName findNameForSerialization(Annotated ann) {
            if (ann instanceof AnnotatedField && "legacyName".equals(((AnnotatedField) ann).getAnnotated().getName())) {
                return PropertyName.construct("legacy_out");
            }
            return super.findNameForSerialization(ann);
        }
    }

    static class LegacyDeserializeOnlyIntrospector extends JacksonAnnotationIntrospector {
        @Override
        public PropertyName findNameForDeserialization(Annotated ann) {
            if (ann instanceof AnnotatedField && "legacyName".equals(((AnnotatedField) ann).getAnnotated().getName())) {
                return PropertyName.construct("legacy_in");
            }
            return super.findNameForDeserialization(ann);
        }
    }

    static class NativeLegacyBook {
        @LegacyName("legacy_name")
        public String legacyName;
    }

    static class SplitLegacyBook {
        public String legacyName;
    }

    static class PublicPlainBook {
        public String userName;
        public int loginCount;
    }

    static class AccessorBook {
        private String userName;
        private int loginCount;

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public int getLoginCount() {
            return loginCount;
        }

        public void setLoginCount(int loginCount) {
            this.loginCount = loginCount;
        }
    }

    static class PlainPrivateBook {
        String userName;
        int loginCount;
    }

    private static void assertReadModule(Jackson2JsonFacade facade) {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        JsonObject jo1 = (JsonObject) facade.readNode(new StringReader(json1), JsonObject.class);

        Book jo2 = (Book) facade.readNode(new StringReader(json1), Book.class);
        assertEquals(20, jo2.getIntByPath("/friends/rose/age/1"));

        String json2 = "[1,2,3]";
        JsonArray ja1 = (JsonArray) facade.readNode(new StringReader(json2), JsonArray.class);
        assertEquals(3, ja1.size());

        Object ja2 = facade.readNode(new StringReader(json2), Object.class);
        assertTrue(ja2 instanceof List);

        Object ja3 = facade.readNode(new StringReader(json2), int[].class);
        assertEquals(int[].class, ja3.getClass());
    }

    private static void assertWrite(Jackson2JsonFacade facade) {
        String json1 = "{\"id\":123,\"name\":\"han\",\"height\":175.3,\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        Book jo1 = (Book) facade.readNode(new StringReader(json1), Book.class);

        StringWriter output = new StringWriter();
        facade.writeNode(output, jo1);
        assertEquals(json1, output.toString());
    }
    private static void assertNodeValue(Jackson2JsonFacade facade) {
        CodecFacadeAssertions.assertNodeValue(facade);
    }

    private static void assertNodeField(Jackson2JsonFacade facade) {
        CodecFacadeAssertions.assertNodeField(facade,
                "{\"id\":123,\"name\":null,\"user_name\":\"han\",\"height\":175.5,\"transientHeight\":189.9}",
                "{\"user_name\":\"han\",\"id\":123,\"name\":null,\"height\":175.5,\"transientHeight\":189.9}");
    }

    private static void assertValueFormat(Jackson2JsonFacade facade) {
        CodecFacadeAssertions.assertValueFormat(facade);

        Jackson2JsonFacade configured = new Jackson2JsonFacade(new ObjectMapper(),
                new StreamingContext(Collections.singletonMap(Instant.class, "epochMillis")));
        CodecFacadeAssertions.assertConfiguredInstantValueFormat(configured);
    }

    @Test
    public void testNow1() {
        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        long epochMillis = instant.toEpochMilli();
        Jackson2JsonFacade configured = new Jackson2JsonFacade(new ObjectMapper(),
                new StreamingContext(Collections.singletonMap(Instant.class, "epochMillis")));
        assertEquals(String.valueOf(epochMillis), configured.writeNodeAsString(instant));

        assertEquals(instant, configured.readNode(String.valueOf(epochMillis), Instant.class));
    }

    @Test
    public void testNow2() {
        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        long epochMillis = instant.toEpochMilli();
        Jackson2JsonFacade configured = new Jackson2JsonFacade(new ObjectMapper(),
                new StreamingContext(Collections.singletonMap(Instant.class, "epochMillis"),
                        StreamingContext.StreamingMode.SHARED_IO, true));
        assertEquals(String.valueOf(epochMillis), configured.writeNodeAsString(instant));
        assertEquals(instant, configured.readNode(String.valueOf(epochMillis), Instant.class));
    }

    public static class NativeInstantBook {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant createdAt;

        public Instant updatedAt;
    }

    @Test
    void testNativeInstantFieldValueFormatDeserializer() {
        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        long epochMillis = instant.toEpochMilli();
        Jackson2JsonFacade facade = new Jackson2JsonFacade(new ObjectMapper(),
                new StreamingContext(StreamingContext.StreamingMode.PLUGIN_MODULE));

        NativeInstantBook book = (NativeInstantBook) facade.readNode(
                "{\"createdAt\":" + epochMillis + ",\"updatedAt\":\"" + instant + "\"}",
                NativeInstantBook.class);

        assertEquals(instant, book.createdAt);
        assertEquals(instant, book.updatedAt);
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

    @NodeBinding(access = AccessStrategy.FIELD_BASED)
    static class FieldBasedPrivateBook {
        String userName;
        int loginCount;
    }

    private static void assertNodeNaming(Jackson2JsonFacade facade) {
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
                              @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
        public void setCity(String city) { this.city = city; }
    }

    private static void assertCreatorExtraField(Jackson2JsonFacade facade) {
        String json = "{\"name\":\"a\",\"age\":1,\"city\":\"sh\"}";
        Ctor2PlusField pojo = (Ctor2PlusField) facade.readNode(json, Ctor2PlusField.class);
        assertEquals("a", pojo.name);
    }

    @Data
    static class CtorAlias {
        final String name;
        final int age;

        @JsonCreator
        public CtorAlias(@NodeProperty(value = "name", aliases = {"n"}) String name,
                         @NodeProperty("age") @JsonAlias("old") int age) {
            this.name = name;
            this.age = age;
        }
    }

    private static void assertCreatorAlias(Jackson2JsonFacade facade) {
        String json = "{\"n\":\"a\",\"age\":2}";
        CtorAlias pojo = (CtorAlias) facade.readNode(json, CtorAlias.class);
        assertEquals("a", pojo.name);
    }

    private static void assertDynamicBinding(Jackson2JsonFacade facade) {
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

    private static void assertWriteOnlyMembersAreNotSerialized(Jackson2JsonFacade facade) {
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

    static class User {
        public String name;
        public List<User> friends;
        public Map<String, Object> ext;
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

    private static void assertAnyOf(Jackson2JsonFacade facade) {
        String anyOfJson = "{\"pet\":{\"kind\":\"cat\",\"meow\":\"m\"}}";
        PetHolder holder = (PetHolder) facade.readNode(anyOfJson, PetHolder.class);
        assertEquals(Cat.class, holder.pet.getClass());
        StringWriter sw2 = new StringWriter();
        facade.writeNode(sw2, holder);
        assertTrue(sw2.toString().contains("meow"));
    }

    @TestFactory
    Stream<DynamicTest> testSjf4jCasesAcrossModes() {
        return Stream.of(
//                modeTests("serde", Jackson2FacadeTest::assertSerDe),
//                modeTests("read-module", Jackson2FacadeTest::assertReadModule),
//                modeTests("write", Jackson2FacadeTest::assertWrite),
                modeTests("dynamic-binding", Jackson2FacadeTest::assertDynamicBinding),
                modeTests("write-only-hidden", Jackson2FacadeTest::assertWriteOnlyMembersAreNotSerialized),
                modeTests("node-value", Jackson2FacadeTest::assertNodeValue),
                modeTests("node-field", Jackson2FacadeTest::assertNodeField),
                modeTests("value-format", Jackson2FacadeTest::assertValueFormat),
                modeTests("node-naming", Jackson2FacadeTest::assertNodeNaming),
                modeTests("creator-extra-field", Jackson2FacadeTest::assertCreatorExtraField),
                modeTests("creator-alias", Jackson2FacadeTest::assertCreatorAlias),
                modeTests("anyof", Jackson2FacadeTest::assertAnyOf)
        ).flatMap(s -> s);
    }

    @Test
    void testSkipNode1() {
        Jackson2JsonFacade facade = new Jackson2JsonFacade(new ObjectMapper(), ctx(StreamingContext.StreamingMode.SHARED_IO));
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
        log.info("pojo={}", Nodes.inspect(pojo));
        assertEquals("Jack", pojo.name);
    }

}
