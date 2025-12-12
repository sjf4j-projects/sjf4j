package org.sjf4j.facade.simple;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.facade.jackson.JacksonFacadeTest;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        JacksonFacadeTest.Book jo1 = (JacksonFacadeTest.Book) facade.readNode(new StringReader(json1), JacksonFacadeTest.Book.class);

        StringWriter output;
        output = new StringWriter();
        facade.writeNode(output, jo1);
        String json2 = output.toString();
        assertEquals(json1, json2);

    }


}
