package org.sjf4j.facade.gson;

import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.node.AccessStrategy;
import org.sjf4j.node.NamingStrategy;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;
import org.sjf4j.node.ValueFormatMapping;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class GsonFacadeTest {

    private static StreamingContext ctx(StreamingContext.StreamingMode mode) {
        return new StreamingContext(mode);
    }

    private static Stream<StreamingContext.StreamingMode> allModes() {
        return Stream.of(
                StreamingContext.StreamingMode.SHARED_IO,
                StreamingContext.StreamingMode.PLUGIN_MODULE
        );
    }

    private static GsonJsonFacade newFacade(StreamingContext.StreamingMode mode) {
        return new GsonJsonFacade(new GsonBuilder(), ctx(mode));
    }

    @FunctionalInterface
    private interface ModeCase {
        void run(GsonJsonFacade facade) throws Exception;
    }

    private static Stream<DynamicTest> modeTests(String caseName, ModeCase caze) {
        return allModes().map(mode -> DynamicTest.dynamicTest(caseName + " mode=" + mode, () -> {
            GsonJsonFacade facade = newFacade(mode);
            caze.run(facade);
        }));
    }

    private static void assertSerDe(GsonJsonFacade facade) {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        JsonObject jo1 = (JsonObject) facade.readNode(new StringReader(json1), JsonObject.class);
        StringWriter sw = new StringWriter();
        facade.writeNode(sw, jo1);
        String res1 = sw.toString();
        assertEquals(json1, res1);
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

    private static void assertReadModule(GsonJsonFacade facade) {
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

    private static void assertWrite(GsonJsonFacade facade) {
        String json1 = "{\"id\":123,\"name\":\"han\",\"height\":175.3,\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        Book jo1 = (Book) facade.readNode(new StringReader(json1), Book.class);

        StringWriter output = new StringWriter();
        facade.writeNode(output, jo1);
        assertEquals(json1, output.toString());
    }

    private static void assertDynamicBinding(GsonJsonFacade facade) {
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

    private static void assertWriteOnlyMembersAreNotSerialized(GsonJsonFacade facade) {
        WriteOnlyPojo pojo = new WriteOnlyPojo();
        pojo.setName("han");
        pojo.setSecret("hidden");
        if (facade.realStreamingMode() != StreamingContext.StreamingMode.PLUGIN_MODULE) {
            assertEquals("{\"name\":\"han\"}", facade.writeNodeAsString(pojo));
        }

        WriteOnlyJojo jojo = new WriteOnlyJojo();
        jojo.setName("han");
        jojo.setSecret("hidden");
        jojo.put("runtime", 1);
        assertEquals("{\"name\":\"han\"}", facade.writeNodeAsString(jojo));
    }

    @Test
    void testExclusiveIoIsRejectedAtRuntime() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder(), ctx(StreamingContext.StreamingMode.EXCLUSIVE_IO));
        JsonException ex = assertThrows(JsonException.class,
                () -> facade.readNode("{}", Object.class));
        assertTrue(ex.getMessage().contains("EXCLUSIVE_IO"));
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
    private static void assertNodeValue(GsonJsonFacade facade) {
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

    public static class InstantFieldBook {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant createdAt;
        public Instant updatedAt;
    }

    public static class InstantCreatorBook {
        public final Instant createdAt;
        public final Instant updatedAt;

        @org.sjf4j.annotation.node.NodeCreator
        public InstantCreatorBook(@NodeProperty(value = "createdAt", valueFormat = "epochMillis") Instant createdAt,
                                  @NodeProperty("updatedAt") Instant updatedAt) {
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    private static void assertNodeField(GsonJsonFacade facade) {
        String json1 = "{\"id\":123,\"user_name\":\"han\",\"height\":175.5,\"transientHeight\":189.9}";
        String json2 = "{\"user_name\":\"han\",\"id\":123,\"height\":175.5,\"transientHeight\":189.9}";
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

    private static void assertValueFormat(GsonJsonFacade facade) {
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

        GsonJsonFacade configured = new GsonJsonFacade(new GsonBuilder(),
                new StreamingContext(ValueFormatMapping.of(Collections.singletonMap(Instant.class, "epochMillis")),
                        facade.realStreamingMode(), true));
        assertEquals(String.valueOf(epochMillis), configured.writeNodeAsString(instant));
        assertEquals(instant, configured.readNode(String.valueOf(epochMillis), Instant.class));
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

    private static void assertNodeNaming(GsonJsonFacade facade) {
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


    static class User {
        public String name;
        public List<User> friends;
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

    @NodeBinding(access = AccessStrategy.FIELD_BASED)
    static class FieldBasedPrivateBook {
        String userName;
        int loginCount;
    }

    @TestFactory
    Stream<DynamicTest> testSjf4jCasesAcrossModes() {
        return Stream.of(
                modeTests("serde", GsonFacadeTest::assertSerDe),
                modeTests("read-module", GsonFacadeTest::assertReadModule),
                modeTests("write", GsonFacadeTest::assertWrite),
                modeTests("dynamic-binding", GsonFacadeTest::assertDynamicBinding),
                modeTests("write-only-hidden", GsonFacadeTest::assertWriteOnlyMembersAreNotSerialized),
                modeTests("node-value", GsonFacadeTest::assertNodeValue),
                modeTests("node-field", GsonFacadeTest::assertNodeField),
                modeTests("value-format", GsonFacadeTest::assertValueFormat),
                modeTests("node-naming", GsonFacadeTest::assertNodeNaming)
        ).flatMap(s -> s);
    }

    @Test
    void testSkipNode1() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder(), ctx(StreamingContext.StreamingMode.SHARED_IO));
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

    @Test
    void testPluginModuleAllowsNativeEquivalentPublicPojo() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder(), ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        PublicPlainBook book = (PublicPlainBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PublicPlainBook.class);
        assertEquals("han", book.userName);
        assertEquals(2, book.loginCount);
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleAllowsBeanAccessorPojo() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder(), ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        AccessorBook book = (AccessorBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                AccessorBook.class);
        assertEquals("han", book.getUserName());
        assertEquals(2, book.getLoginCount());
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleBeanOnlySkipsNonPublicPlainPojo() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder(), ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        PlainPrivateBook book = (PlainPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PlainPrivateBook.class);
        assertNull(book.userName);
        assertEquals(0, book.loginCount);
        assertEquals("{}", facade.writeNodeAsString(book));
    }

    @Test
    void testAutoModePrefersPluginModule() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder());
        PlainPrivateBook book = (PlainPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PlainPrivateBook.class);
        assertNull(book.userName);
        assertEquals(0, book.loginCount);
        assertEquals("{}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleFieldBasedAnnotatedAllowsNonPublicPlainPojo() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder(), ctx(StreamingContext.StreamingMode.PLUGIN_MODULE));
        FieldBasedPrivateBook book = (FieldBasedPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                FieldBasedPrivateBook.class);
        assertEquals("han", book.userName);
        assertEquals(2, book.loginCount);
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

}
