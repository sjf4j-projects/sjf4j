package org.sjf4j.jdk17.facade;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.FacadeFactory;
import org.sjf4j.facade.FacadeNodes;
import org.sjf4j.facade.jackson3.Jackson3JsonFacade;
import org.sjf4j.node.AccessStrategy;
import org.sjf4j.node.NamingStrategy;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.TypeReference;
import org.sjf4j.node.ValueFormatMapping;
import org.sjf4j.path.JsonPath;
import org.sjf4j.exception.JsonException;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.AnnotatedField;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jackson3FacadeTest {

    private final Sjf4j sjf4j = Sjf4j.builder()
            .jsonFacadeProvider(Jackson3JsonFacade.provider())
            .build();

    static class Book extends JsonObject {
        public int id;

        @NodeProperty("user_name")
        String userName;
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

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface LegacyName {
        String value();
    }

    static class LegacyNameIntrospector extends JacksonAnnotationIntrospector {
        @Override
        public PropertyName findNameForSerialization(MapperConfig<?> config, Annotated ann) {
            if (ann instanceof AnnotatedField) {
                LegacyName legacyName = ((AnnotatedField) ann)
                        .getAnnotated().getAnnotation(LegacyName.class);
                if (legacyName != null) {
                    return PropertyName.construct(legacyName.value());
                }
            }
            return super.findNameForSerialization(config, ann);
        }

        @Override
        public PropertyName findNameForDeserialization(MapperConfig<?> config, Annotated ann) {
            if (ann instanceof AnnotatedField) {
                LegacyName legacyName = ((AnnotatedField) ann)
                        .getAnnotated().getAnnotation(LegacyName.class);
                if (legacyName != null) {
                    return PropertyName.construct(legacyName.value());
                }
            }
            return super.findNameForDeserialization(config, ann);
        }
    }

    static class NativeLegacyBook {
        @LegacyName("legacy_name")
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

    @NodeValue
    static class Ops {
        private final LocalDate localDate;

        Ops(LocalDate localDate) {
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

    static class BookField extends JsonObject {
        int id;

        @NodeProperty("user_name")
        String userName;

        double height;
        transient int transientHeight;
    }

    static class InstantFieldBook {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant createdAt;
        public Instant updatedAt;
    }

    static class InstantCreatorBook {
        public final Instant createdAt;
        public final Instant updatedAt;

        @NodeCreator
        public InstantCreatorBook(@NodeProperty(value = "createdAt", valueFormat = "epochMillis") Instant createdAt,
                                  @NodeProperty("updatedAt") Instant updatedAt) {
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    @NodeBinding(naming = NamingStrategy.SNAKE_CASE)
    static class SnakeBook extends JsonObject {
        public String userName;
        public int loginCount;
    }

    @NodeBinding(naming = NamingStrategy.SNAKE_CASE)
    static class SnakePlainBook {
        public String userName;
        public int loginCount;
    }

    @NodeBinding(access = AccessStrategy.FIELD_BASED)
    static class FieldBasedPrivateBook {
        String userName;
        int loginCount;
    }

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

        public void setCity(String city) {
            this.city = city;
        }
    }

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

    interface Pet {}

    @AnyOf(value = {
            @AnyOf.Mapping(value = Cat.class, when = "cat"),
            @AnyOf.Mapping(value = Dog.class, when = "dog")
    }, key = "kind")
    interface TypedPet extends Pet {}

    static class Cat implements TypedPet {
        public String meow;
    }

    static class Dog implements TypedPet {
        public String bark;
    }

    static class PetHolder {
        public TypedPet pet;
    }

    static class User {
        public String name;
        public List<User> friends;
        public Map<String, Object> ext;
    }

    private static StreamingContext ctx(StreamingContext.StreamingMode mode) {
        return new StreamingContext(mode);
    }

    private static Stream<StreamingContext.StreamingMode> allModes() {
        return Stream.of(
                StreamingContext.StreamingMode.SHARED_IO,
                StreamingContext.StreamingMode.PLUGIN_MODULE
        );
    }

    @FunctionalInterface
    private interface ModeCase {
        void run(Jackson3JsonFacade facade) throws Exception;
    }

    private static Jackson3JsonFacade newFacade(StreamingContext.StreamingMode mode) {
        return new Jackson3JsonFacade(JsonMapper.builderWithJackson2Defaults().build(), ctx(mode));
    }

    private static Stream<DynamicTest> modeTests(String caseName, ModeCase caze) {
        return modeTests(caseName, allModes(), caze);
    }

    private static Stream<DynamicTest> modeTests(String caseName,
                                                 Stream<StreamingContext.StreamingMode> modes,
                                                 ModeCase caze) {
        return modes.map(mode -> DynamicTest.dynamicTest(caseName + " mode=" + mode, () -> {
            Jackson3JsonFacade facade = newFacade(mode);
            caze.run(facade);
        }));
    }

    private static void assertNodeValue(Jackson3JsonFacade facade) {
        @SuppressWarnings("unchecked")
        List<Ops> list = (List<Ops>) facade.readNode("[\"2024-10-01\",\"2025-12-18\"]",
                new TypeReference<List<Ops>>() {}.getType());
        assertEquals(2, list.size());

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, list);
        @SuppressWarnings("unchecked")
        List<Ops> list2 = (List<Ops>) facade.readNode(sw.toString(),
                new TypeReference<List<Ops>>() {}.getType());
        assertEquals(2, list2.size());
    }

    private static void assertNodeField(Jackson3JsonFacade facade) {
        String json = "{\"id\":123,\"user_name\":\"han\",\"height\":175.5,\"transientHeight\":189.9}";
        String expected = "{\"user_name\":\"han\",\"id\":123,\"height\":175.5,\"transientHeight\":189.9}";
        BookField jo1 = (BookField) facade.readNode(new StringReader(json), BookField.class);
        assertEquals("han", jo1.userName);
        assertEquals("han", jo1.getString("user_name"));
        assertNull(jo1.getString("userName"));
        assertEquals(0.0, jo1.height);
        assertEquals(0, jo1.transientHeight);

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, jo1);
        assertEquals(expected, sw.toString());
    }

    private static void assertValueFormat(Jackson3JsonFacade facade) {
        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        long epochMillis = instant.toEpochMilli();
        String json = "{\"createdAt\":" + epochMillis + ",\"updatedAt\":\"" + instant + "\"}";

        InstantFieldBook book = (InstantFieldBook) facade.readNode(new StringReader(json), InstantFieldBook.class);
        assertEquals(instant, book.createdAt);
        assertEquals(instant, book.updatedAt);
        assertEquals(json, facade.writeNodeAsString(book));

        InstantCreatorBook creatorBook = (InstantCreatorBook) facade.readNode(new StringReader(json), InstantCreatorBook.class);
        assertEquals(instant, creatorBook.createdAt);
        assertEquals(instant, creatorBook.updatedAt);

        Jackson3JsonFacade configured = new Jackson3JsonFacade(JsonMapper.builderWithJackson2Defaults().build(),
                new StreamingContext(ValueFormatMapping.of(Collections.singletonMap(Instant.class, "epochMillis")),
                        facade.realStreamingMode(), true));
        assertEquals(String.valueOf(epochMillis), configured.writeNodeAsString(instant));
        assertEquals(instant, configured.readNode(String.valueOf(epochMillis), Instant.class));
    }

    private static void assertNodeNaming(Jackson3JsonFacade facade) {
        String json = "{\"user_name\":\"han\",\"login_count\":2}";
        SnakeBook jo1 = (SnakeBook) facade.readNode(json, SnakeBook.class);
        assertEquals("han", jo1.userName);
        assertEquals(2, jo1.loginCount);
        assertEquals("han", jo1.getString("user_name"));
        assertNull(jo1.getString("userName"));

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, jo1);
        assertEquals(json, sw.toString());

        SnakePlainBook pojo = (SnakePlainBook) facade.readNode(json, SnakePlainBook.class);
        assertEquals("han", pojo.userName);
        assertEquals(2, pojo.loginCount);
        sw = new StringWriter();
        facade.writeNode(sw, pojo);
        assertEquals(json, sw.toString());
    }

    private static void assertDynamicBinding(Jackson3JsonFacade facade) {
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

    private static void assertCreatorExtraField(Jackson3JsonFacade facade) {
        Ctor2PlusField pojo = (Ctor2PlusField) facade.readNode("{\"name\":\"a\",\"age\":1,\"city\":\"sh\"}",
                Ctor2PlusField.class);
        assertEquals("a", pojo.name);
        assertEquals(1, pojo.age);
        assertEquals("sh", pojo.city);
    }

    private static void assertCreatorAlias(Jackson3JsonFacade facade) {
        CtorAlias pojo = (CtorAlias) facade.readNode("{\"n\":\"a\",\"old\":2}", CtorAlias.class);
        assertEquals("a", pojo.name);
        assertEquals(2, pojo.age);
    }

    private static void assertAnyOf(Jackson3JsonFacade facade) {
        PetHolder holder = (PetHolder) facade.readNode("{\"pet\":{\"kind\":\"cat\",\"meow\":\"m\"}}",
                PetHolder.class);
        assertEquals(Cat.class, holder.pet.getClass());

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, holder);
        assertTrue(sw.toString().contains("meow"));
    }

    private static void assertSkipNestedNode(Jackson3JsonFacade facade) {
        String json = "{\n"
                + "  \"id\": 7,\n"
                + "  \"skipObj\": {\n"
                + "    \"x\": [true, false, null, {\"deep\": \"v, }\"}],\n"
                + "    \"y\": 2\n"
                + "  },\n"
                + "  \"skipArr\": [1,2,{\"a\":[3,4]}],\n"
                + "  \"skipStr\": \"wa,w[]{}a\",\n"
                + "  \"skipNumber\": -334455,\n"
                + "  \"skipBoolean\": false,\n"
                + "  \"skipNull\": null,\n"
                + "  \"name\": \"Jack\"\n"
                + "}";
        User pojo = (User) facade.readNode(json, User.class);
        assertEquals("Jack", pojo.name);
    }

    @TestFactory
    Stream<DynamicTest> testJsonObjectRoundTripAcrossModes() {
        return allModes().map(mode -> DynamicTest.dynamicTest("mode=" + mode, () -> {
            Jackson3JsonFacade facade = newFacade(mode);
            String json = "{\"id\":123,\"user_name\":\"han\"}";

            Book book = (Book) facade.readNode(json, Book.class);
            assertEquals(123, book.id);
            assertEquals("han", book.userName);
            assertEquals("han", book.getString("user_name"));
            assertEquals(json, facade.writeNodeAsString(book));
        }));
    }

    @TestFactory
    Stream<DynamicTest> testSjf4jCasesAcrossModes() {
        return Stream.of(
                modeTests("node-value", Jackson3FacadeTest::assertNodeValue),
                modeTests("node-field", Jackson3FacadeTest::assertNodeField),
                modeTests("value-format", Jackson3FacadeTest::assertValueFormat),
                modeTests("node-naming", Jackson3FacadeTest::assertNodeNaming),
                modeTests("dynamic-binding", Jackson3FacadeTest::assertDynamicBinding),
                modeTests("creator-extra-field", Jackson3FacadeTest::assertCreatorExtraField),
                modeTests("creator-alias", Jackson3FacadeTest::assertCreatorAlias),
                modeTests("anyof", Jackson3FacadeTest::assertAnyOf),
                modeTests("skip-nested-node", Jackson3FacadeTest::assertSkipNestedNode)
        ).flatMap(s -> s);
    }

    @Test
    void testJackson3Builder() {
        Book book = sjf4j.fromJson("{\"id\":7,\"user_name\":\"jack\"}", Book.class);
        assertEquals(7, book.id);
        assertEquals("jack", book.userName);
    }

    @Test
    void testCustomMapperIntrospectorIsPreserved() {
        JsonMapper mapper = JsonMapper.builderWithJackson2Defaults()
                .annotationIntrospector(new LegacyNameIntrospector())
                .build();

        Jackson3JsonFacade facade = new Jackson3JsonFacade(mapper, ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        NativeLegacyBook book = (NativeLegacyBook) facade.readNode("{\"legacy_name\":\"legacy\"}",
                NativeLegacyBook.class);
        assertEquals("legacy", book.legacyName);

        String json = facade.writeNodeAsString(book);
        assertTrue(json.contains("\"legacy_name\":\"legacy\""));
        assertFalse(json.contains("legacyName"));
    }

    @Test
    void testPluginModuleFallsBackForNonPublicPlainPojo() {
        Jackson3JsonFacade facade = new Jackson3JsonFacade(JsonMapper.builderWithJackson2Defaults().build(),
                ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        PlainPrivateBook book = (PlainPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PlainPrivateBook.class);
        assertNull(book.userName);
        assertEquals(0, book.loginCount);
        assertEquals("{}", facade.writeNodeAsString(book));
    }

    @Test
    void testAutoModePrefersPluginModule() {
        Jackson3JsonFacade facade = new Jackson3JsonFacade(JsonMapper.builderWithJackson2Defaults().build());
        PlainPrivateBook book = (PlainPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PlainPrivateBook.class);
        assertNull(book.userName);
        assertEquals(0, book.loginCount);
        assertEquals("{}", facade.writeNodeAsString(book));
    }

    @Test
    void testExclusiveIoUnsupportedAtRuntime() {
        Jackson3JsonFacade facade = new Jackson3JsonFacade(JsonMapper.builderWithJackson2Defaults().build(),
                ctx(StreamingContext.StreamingMode.EXCLUSIVE_IO));
        JsonException ex = assertThrows(JsonException.class,
                () -> facade.readNode("{}", Object.class));
        assertTrue(ex.getMessage().contains("Unsupported"));
    }

    @Test
    void testPluginModuleAllowsNativeEquivalentPublicPojo() {
        Jackson3JsonFacade facade = new Jackson3JsonFacade(JsonMapper.builderWithJackson2Defaults().build(),
                ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        PublicPlainBook book = (PublicPlainBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PublicPlainBook.class);
        assertEquals("han", book.userName);
        assertEquals(2, book.loginCount);
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleAllowsNativeEquivalentAccessorPojo() {
        Jackson3JsonFacade facade = new Jackson3JsonFacade(JsonMapper.builderWithJackson2Defaults().build(),
                ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        AccessorBook book = (AccessorBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                AccessorBook.class);
        assertEquals("han", book.getUserName());
        assertEquals(2, book.getLoginCount());
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    @Test
    void testFieldBasedAnnotatedAllowsNonPublicPlainPojo() {
        Jackson3JsonFacade facade = new Jackson3JsonFacade(JsonMapper.builderWithJackson2Defaults().build(),
                ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        FieldBasedPrivateBook book = (FieldBasedPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                FieldBasedPrivateBook.class);
        assertEquals("han", book.userName);
        assertEquals(2, book.loginCount);
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    @Test
    void testDefaultFacadePrefersJackson3() {
        assertTrue(FacadeFactory.jsonFacadeProvider().create(StreamingContext.EMPTY) instanceof Jackson3JsonFacade);
    }

    @Test
    void testFacadeNodesDispatchJackson3() {
        JsonMapper mapper = JsonMapper.builderWithJackson2Defaults().build();
        ObjectNode objectNode = mapper.createObjectNode();
        objectNode.put("name", "han");
        objectNode.put("age", 18);
        ArrayNode arrayNode = mapper.createArrayNode().add("x").add(2).add(false);

        assertTrue(FacadeNodes.isJackson3NodesPresent());
        assertTrue(FacadeNodes.isNode(objectNode));
        assertEquals(NodeKind.OBJECT_FACADE, FacadeNodes.kindOf(objectNode));
        assertEquals(NodeKind.ARRAY_FACADE, FacadeNodes.kindOf(arrayNode));
        assertEquals(NodeKind.VALUE_STRING_FACADE, FacadeNodes.kindOf(StringNode.valueOf("x")));
        assertEquals(NodeKind.VALUE_BOOLEAN_FACADE, FacadeNodes.kindOf(BooleanNode.TRUE));
        assertEquals(NodeKind.VALUE_NULL, FacadeNodes.kindOf(JsonNodeFactory.instance.nullNode()));
        assertEquals("han", FacadeNodes.toString(objectNode.get("name")));
        assertEquals(18, FacadeNodes.toNumber(objectNode.get("age")).intValue());
        assertEquals("x", FacadeNodes.toJsonArray(arrayNode).getString(0));
    }

    @Test
    void testJsonPathWritesJackson3Nodes() {
        JsonMapper mapper = JsonMapper.builderWithJackson2Defaults().build();
        ObjectNode objectNode = mapper.createObjectNode().put("0", "zero");
        ArrayNode arrayNode = mapper.createArrayNode().add("a");

        assertNull(JsonPath.compile("/0").ensurePutIfAbsent(objectNode, StringNode.valueOf("ignored")));
        assertEquals("zero", ((tools.jackson.databind.JsonNode) JsonPath.compile("/0").replace(objectNode, StringNode.valueOf("one"))).asString());
        assertEquals("one", JsonPath.compile("/0").getString(objectNode));
        assertEquals("one", ((tools.jackson.databind.JsonNode) JsonPath.compile("/0").remove(objectNode)).asString());
        JsonPath.compile("/0").add(objectNode, StringNode.valueOf("again"));
        assertEquals("again", JsonPath.compile("/0").getString(objectNode));
        assertThrows(JsonException.class, () -> JsonPath.compile("/missing/name").ensurePut(objectNode, StringNode.valueOf("created")));

        assertNull(JsonPath.compile("/0").ensurePutIfAbsent(arrayNode, StringNode.valueOf("ignored")));
        assertEquals("a", ((tools.jackson.databind.JsonNode) JsonPath.compile("/0").replace(arrayNode, StringNode.valueOf("b"))).asString());
        JsonPath.compile("/1").add(arrayNode, StringNode.valueOf("c"));
        assertEquals("c", JsonPath.compile("/1").getString(arrayNode));
        assertThrows(JsonException.class, () -> JsonPath.compile("/-/name").ensurePut(arrayNode, StringNode.valueOf("created")));
    }
}
