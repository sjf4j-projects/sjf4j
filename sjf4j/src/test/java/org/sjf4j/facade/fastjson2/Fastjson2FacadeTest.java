package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacadeTest;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.annotation.node.Encode;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.Decode;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Slf4j
public class Fastjson2FacadeTest {

    @Test
    public void testSerDe1() {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";

        Sjf4jConfig.global(new Sjf4jConfig.Builder().streamingMode(StreamingFacade.StreamingMode.SHARED_IO).build());
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade();
        JsonObject jo1 = (JsonObject) facade.readNode(new StringReader(json1), JsonObject.class);

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, jo1);
        String res1 = sw.toString();
        log.info("res1: {}", res1);
        assertEquals(json1, res1);
    }

    @Test
    public void testSerDe2() {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";

        Sjf4jConfig.global(new Sjf4jConfig.Builder().streamingMode(StreamingFacade.StreamingMode.EXCLUSIVE_IO).build());
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade();
        JsonObject jo1 = (JsonObject) facade.readNode(new StringReader(json1), JsonObject.class);

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, jo1);
        String res1 = sw.toString();
        log.info("res1: {}", res1);
        assertEquals(json1, res1);
    }

    public static class Book extends JsonObject {
        private int id;
        private String name;
    }

    @Test
    public void testReadModule1() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade();

        Sjf4jConfig.global(new Sjf4jConfig.Builder().streamingMode(StreamingFacade.StreamingMode.PLUGIN_MODULE).build());
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        JsonObject jo1 = (JsonObject) facade.readNode(new StringReader(json1), JsonObject.class);
        log.info("jo1={}", jo1.inspect());

        Book jo2 = (Book) facade.readNode(new StringReader(json1), Book.class);
        log.info("jo2={}", jo2.inspect());
        assertEquals(20, jo2.getIntegerByPath("/friends/rose/age/1"));

        String json2 = "[1,2,3]";
        JsonArray ja1 = (JsonArray) facade.readNode(new StringReader(json2), JsonArray.class);
        log.info("ja1={}", ja1);

        Object ja2 = facade.readNode(new StringReader(json2), Object.class);
        log.info("ja2={}, type={}", ja2, ja2.getClass());

        Object ja3 = facade.readNode(new StringReader(json2), int[].class);
        log.info("ja3={}, type={}", ja3, ja3.getClass());
    }


    @Test
    public void testWrite1() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade();

        String json1 = "{\"id\":123,\"name\":\"han\",\"height\":175.3,\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        Book jo1 = (Book) facade.readNode(new StringReader(json1), Book.class);

        Sjf4jConfig.global(new Sjf4jConfig.Builder().streamingMode(StreamingFacade.StreamingMode.SHARED_IO).build());
        StringWriter output;
        output = new StringWriter();
        facade.writeNode(output, jo1);
        String json2 = output.toString();
        assertEquals(json1, json2);

        Sjf4jConfig.global(new Sjf4jConfig.Builder().streamingMode(StreamingFacade.StreamingMode.EXCLUSIVE_IO).build());
        output = new StringWriter();
        facade.writeNode(output, jo1);
        String json3 = output.toString();
        assertEquals(json1, json3);

        Sjf4jConfig.global(new Sjf4jConfig.Builder().streamingMode(StreamingFacade.StreamingMode.PLUGIN_MODULE).build());
        output = new StringWriter();
        facade.writeNode(output, jo1);
        String json4 = output.toString();
        assertEquals(json1, json4);
    }


    @NodeValue
    public static class Ops {
        private final LocalDate localDate;

        public Ops(LocalDate localDate) {
            this.localDate = localDate;
        }
        @Encode
        public String encode() {
            return localDate.toString();
        }

        @Decode
        public static Ops decode(String raw) {
            return new Ops(LocalDate.parse(raw));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNodeValue1() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade();
        NodeRegistry.ValueCodecInfo ci = NodeRegistry.registerValueCodec(Ops.class);

        String json1 = "[\"2024-10-01\",\"2025-12-18\"]";
        List<Ops> list = (List<Ops>) facade.readNode(json1, new TypeReference<List<Ops>>() {}.getType());
        log.info("list={}", list);

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, list);
        String json2 = sw.toString();
        log.info("json2={}", json2);
        assertEquals(json1, json2);
    }


    public static class BookField extends JsonObject {
        private int id;
        private String name;

        @NodeProperty("user_name")
        private String userName;
        private double height;
        private transient int transientHeight;
    }

    @Test
    public void testNodeField1() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade();

        String json1 = "{\"id\":123,\"name\":null,\"user_name\":\"han\",\"height\":175.5,\"transientHeight\":189.9}";
        BookField jo1 = (BookField) facade.readNode(new StringReader(json1), BookField.class);
        log.info("jo1={}", jo1.inspect());
        assertEquals("han", jo1.userName);
        assertEquals("han", jo1.getString("user_name"));
        assertNull(jo1.getString("userName"));

        assertEquals(175.5, jo1.height);
        assertEquals(0, jo1.transientHeight);

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, jo1);
        String json2 = sw.toString();
        log.info("json2={}", json2);
        assertEquals(json1, json2);
    }


    public static class Note {
        @NodeProperty("user_name")
        private String userName;
        public String getUserName() {return userName;}
    }

    @Test
    public void testNodeField2() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade();

        Note note1 = new Note();
        note1.userName = "gua";
        StringWriter sw = new StringWriter();
        facade.writeNode(sw, note1);
        String json1 = sw.toString();
        log.info("json1={}", json1);
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

    @Test
    void ctor_with_extra_field_should_fill_by_setter() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade();
        String json = "{\"name\":\"a\",\"age\":1,\"city\":\"sh\"}";
        Ctor2PlusField pojo = (Ctor2PlusField) facade.readNode(json, Ctor2PlusField.class);
        log.info("pojo={}", Nodes.inspect(pojo));
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

    @Test
    void ctor_with_alias_param_should_bind() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade();
        String json = "{\"n\":\"a\",\"age\":2}";
        CtorAlias pojo = (CtorAlias) facade.readNode(json, CtorAlias.class);
        log.info("pojo={}", Nodes.inspect(pojo));
        assertEquals("a", pojo.name);

        String json2 = facade.writeNodeAsString(pojo);
        log.info("json2={}", json2);
    }

    static class User {
        String name;
        List<User> friends;
        Map<String, Object> ext;
    }

    @Test
    void testSkipNode1() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder().streamingMode(StreamingFacade.StreamingMode.EXCLUSIVE_IO).build());
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade();
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
