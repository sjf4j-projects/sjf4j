package org.sjf4j.facade.gson;

import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.annotation.node.Encode;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.Decode;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Slf4j
public class GsonFacadeTest {

    @Test
    public void testSerDe1() {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";

        Sjf4jConfig.global(new Sjf4jConfig.Builder().streamingMode(StreamingFacade.StreamingMode.SHARED_IO).build());
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder());
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
    public void testWithModule1() throws IOException {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder());

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
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder());

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
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder());
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
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder());

        String json1 = "{\"id\":123,\"user_name\":\"han\",\"height\":175.5,\"transientHeight\":189.9}";
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
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder());

        Note note1 = new Note();
        note1.userName = "gua";
        StringWriter sw = new StringWriter();
        facade.writeNode(sw, note1);
        String json1 = sw.toString();
        log.info("json1={}", json1);
    }


    static class User {
        String name;
        List<User> friends;
        Map<String, Object> ext;
    }

    @Test
    void testSkipNode1() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder().streamingMode(StreamingFacade.StreamingMode.EXCLUSIVE_IO).build());
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder());
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
