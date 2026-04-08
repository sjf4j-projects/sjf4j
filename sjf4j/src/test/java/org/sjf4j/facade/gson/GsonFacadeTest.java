package org.sjf4j.facade.gson;

import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.annotation.node.NodeProperty;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class GsonFacadeTest {

    private static Stream<StreamingFacade.StreamingMode> allModes() {
        return Stream.of(
                StreamingFacade.StreamingMode.SHARED_IO,
                StreamingFacade.StreamingMode.EXCLUSIVE_IO,
                StreamingFacade.StreamingMode.PLUGIN_MODULE
        );
    }

    private static GsonJsonFacade newFacade(StreamingFacade.StreamingMode mode) {
        return new GsonJsonFacade(new GsonBuilder(), mode);
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
                modeTests("node-value", GsonFacadeTest::assertNodeValue),
                modeTests("node-field", GsonFacadeTest::assertNodeField),
                modeTests("node-naming", GsonFacadeTest::assertNodeNaming)
        ).flatMap(s -> s);
    }

    @Test
    void testSkipNode1() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder(), StreamingFacade.StreamingMode.SHARED_IO);
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
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder(), StreamingFacade.StreamingMode.PLUGIN_MODULE);
        PublicPlainBook book = (PublicPlainBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                PublicPlainBook.class);
        assertEquals("han", book.userName);
        assertEquals(2, book.loginCount);
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleAllowsBeanAccessorPojo() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder(), StreamingFacade.StreamingMode.PLUGIN_MODULE);
        AccessorBook book = (AccessorBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                AccessorBook.class);
        assertEquals("han", book.getUserName());
        assertEquals(2, book.getLoginCount());
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

    @Test
    void testPluginModuleBeanOnlySkipsNonPublicPlainPojo() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder(), StreamingFacade.StreamingMode.PLUGIN_MODULE);
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
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder(), StreamingFacade.StreamingMode.PLUGIN_MODULE);
        FieldBasedPrivateBook book = (FieldBasedPrivateBook) facade.readNode("{\"userName\":\"han\",\"loginCount\":2}",
                FieldBasedPrivateBook.class);
        assertEquals("han", book.userName);
        assertEquals(2, book.loginCount);
        assertEquals("{\"userName\":\"han\",\"loginCount\":2}", facade.writeNodeAsString(book));
    }

}
