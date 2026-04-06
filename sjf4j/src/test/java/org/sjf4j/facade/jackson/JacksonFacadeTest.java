package org.sjf4j.facade.jackson;


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
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeNaming;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.node.NamingStrategy;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class JacksonFacadeTest {

    private static Stream<StreamingFacade.StreamingMode> allModes() {
        return Stream.of(
                StreamingFacade.StreamingMode.SHARED_IO,
                StreamingFacade.StreamingMode.EXCLUSIVE_IO,
                StreamingFacade.StreamingMode.PLUGIN_MODULE
        );
    }

    private static JacksonJsonFacade newFacade(StreamingFacade.StreamingMode mode) {
        return new JacksonJsonFacade(new ObjectMapper(), mode);
    }

    @FunctionalInterface
    private interface ModeCase {
        void run(JacksonJsonFacade facade) throws Exception;
    }

    private static Stream<DynamicTest> modeTests(String caseName, ModeCase caze) {
        return allModes().map(mode -> DynamicTest.dynamicTest(caseName + " mode=" + mode, () -> {
            JacksonJsonFacade facade = newFacade(mode);
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

        JacksonJsonFacade facade = new JacksonJsonFacade(objectMapper, StreamingFacade.StreamingMode.PLUGIN_MODULE);
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

        JacksonJsonFacade facade = new JacksonJsonFacade(objectMapper, StreamingFacade.StreamingMode.PLUGIN_MODULE);
        SplitLegacyBook book = (SplitLegacyBook) facade.readNode("{\"legacy_in\":\"legacy\"}", SplitLegacyBook.class);
        assertEquals("legacy", book.legacyName);
        assertEquals("{\"legacy_out\":\"legacy\"}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleFallsBackForNonPublicPlainPojo() {
        JacksonJsonFacade facade = new JacksonJsonFacade(new ObjectMapper(), StreamingFacade.StreamingMode.PLUGIN_MODULE);
        PlainPrivateBook book = (PlainPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PlainPrivateBook.class);
        assertNull(book.userName);
        assertEquals(0, book.loginCount);
        assertEquals("{}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleAllowsNativeEquivalentPublicPojo() {
        JacksonJsonFacade facade = new JacksonJsonFacade(new ObjectMapper(), StreamingFacade.StreamingMode.PLUGIN_MODULE);
        PublicPlainBook book = (PublicPlainBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PublicPlainBook.class);
        assertEquals("han", book.userName);
        assertEquals(2, book.loginCount);
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleAllowsNativeEquivalentAccessorPojo() {
        JacksonJsonFacade facade = new JacksonJsonFacade(new ObjectMapper(), StreamingFacade.StreamingMode.PLUGIN_MODULE);
        AccessorBook book = (AccessorBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                AccessorBook.class);
        assertEquals("han", book.getUserName());
        assertEquals(2, book.getLoginCount());
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    @Test
    void testFieldBasedGlobalAllowsNonPublicPlainPojo() {
        Sjf4jConfig previous = Sjf4jConfig.global();
        try {
            Sjf4jConfig.global(new Sjf4jConfig.Builder(previous)
                    .plainPojoFieldAccess(Sjf4jConfig.PlainPojoFieldAccess.FIELD_BASED)
                    .build());
            JacksonJsonFacade facade = new JacksonJsonFacade(new ObjectMapper(), StreamingFacade.StreamingMode.PLUGIN_MODULE);
            PlainPrivateBook book = (PlainPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                    PlainPrivateBook.class);
            assertEquals("han", book.userName);
            assertEquals(2, book.loginCount);
            assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
        } finally {
            Sjf4jConfig.global(previous);
        }
    }

    private static void assertSerDe(JacksonJsonFacade facade) {
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

    private static void assertReadModule(JacksonJsonFacade facade) {
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

    private static void assertWrite(JacksonJsonFacade facade) {
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
    private static void assertNodeValue(JacksonJsonFacade facade) {
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

    private static void assertNodeField(JacksonJsonFacade facade) {
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

    @NodeNaming(NamingStrategy.SNAKE_CASE)
    public static class SnakeBook extends JsonObject {
        public String userName;
        public int loginCount;
    }

    @NodeNaming(NamingStrategy.SNAKE_CASE)
    public static class SnakePlainBook {
        public String userName;
        public int loginCount;
    }

    private static void assertNodeNaming(JacksonJsonFacade facade) {
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

    private static void assertCreatorExtraField(JacksonJsonFacade facade) {
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

    private static void assertCreatorAlias(JacksonJsonFacade facade) {
        String json = "{\"n\":\"a\",\"age\":2}";
        CtorAlias pojo = (CtorAlias) facade.readNode(json, CtorAlias.class);
        assertEquals("a", pojo.name);
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

    private static void assertAnyOf(JacksonJsonFacade facade) {
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
                modeTests("serde", JacksonFacadeTest::assertSerDe),
                modeTests("read-module", JacksonFacadeTest::assertReadModule),
                modeTests("write", JacksonFacadeTest::assertWrite),
                modeTests("node-value", JacksonFacadeTest::assertNodeValue),
                modeTests("node-field", JacksonFacadeTest::assertNodeField),
                modeTests("node-naming", JacksonFacadeTest::assertNodeNaming),
                modeTests("creator-extra-field", JacksonFacadeTest::assertCreatorExtraField),
                modeTests("creator-alias", JacksonFacadeTest::assertCreatorAlias),
                modeTests("anyof", JacksonFacadeTest::assertAnyOf)
        ).flatMap(s -> s);
    }

    @Test
    void testSkipNode1() {
        JacksonJsonFacade facade = new JacksonJsonFacade(new ObjectMapper(), StreamingFacade.StreamingMode.EXCLUSIVE_IO);
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
