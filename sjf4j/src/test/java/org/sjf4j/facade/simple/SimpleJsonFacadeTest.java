package org.sjf4j.facade.simple;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.annotation.node.Encode;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.Decode;
import org.sjf4j.util.TypeReference;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
public class SimpleJsonFacadeTest {

    public static class Book extends JsonObject {
        private int id;
        private String name;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testOne() {
        SimpleJsonFacade facade = new SimpleJsonFacade();

        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";

        JsonObject jo1 = (JsonObject) facade.readNode(json1, JsonObject.class);
        log.info("jo1={}", jo1.inspect());

        Map<String, Object> map = (Map<String, Object>) facade.readNode(json1, Map.class);
        log.info("map={}", map);

        Book book = (Book) facade.readNode(new StringReader(json1), Book.class);
        log.info("book={}", book);

        String json2 = "[1,2,3]";
        JsonArray ja1 = (JsonArray) facade.readNode(json2, JsonArray.class);
        log.info("ja1={}", ja1);

        Object ja2 = facade.readNode(new StringReader(json2), List.class);
        log.info("ja2={}, type={}", ja2, ja2.getClass());

        Object ja3 = facade.readNode(new StringReader(json2), int[].class);
        log.info("ja3={}, type={}", ja3, ja3.getClass());
    }


    @Test
    public void testWrite1() {
        SimpleJsonFacade facade = new SimpleJsonFacade();

        String json1 = "{\"id\":123,\"name\":\"han\",\"height\":175.3,\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        Book jo1 = (Book) facade.readNode(new StringReader(json1), Book.class);

        StringWriter output;
        output = new StringWriter();
        facade.writeNode(output, jo1);
        String json2 = output.toString();
        assertEquals(json1, json2);

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
        SimpleJsonFacade facade = new SimpleJsonFacade();
        NodeRegistry.registerValueCodec(Ops.class);

        String json1 = "[\"2024-10-01\",\"2025-12-18\"]";
        List<Ops> list = (List<Ops>) facade.readNode(json1, new TypeReference<List<Ops>>() {}.getType());
        log.info("list={}", list);

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, list);
        String json2 = sw.toString();
        log.info("json2={}", json2);
        assertEquals(json1, json2);
    }


    static class User {
        String name;
        List<User> friends;
        Map<String, Object> ext;
    }

    @Test
    public void testPojo1() {
        String json = "{\"active\": true }";
        SimpleJsonFacade facade = new SimpleJsonFacade();
        assertThrows(JsonException.class, () -> facade.readNode(json, User.class));
    }


}
