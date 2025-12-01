package org.sjf4j.facades.jackson;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.fastjson2.Fastjson2JsonFacade;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class JacksonFacadeTest {

    @Test
    public void testSerDe1() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        Object obj1 = objectMapper.readValue(new StringReader(json1), Object.class);
        String res1 = objectMapper.writeValueAsString(obj1);
        log.info("obj1 {}: {}", obj1.getClass().getName(), res1);
        assertEquals(json1, res1);
    }


    public static class Book extends JsonObject {
        private int id;
        private String name;
    }

    @Test
    public void testWithModule1() throws IOException {
        JacksonJsonFacade facade = new JacksonJsonFacade(new ObjectMapper());

        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        JsonObject jo1 = (JsonObject) facade.readNodeWithModule(new StringReader(json1), JsonObject.class);
        log.info("jo1={}", jo1.inspect());

        Book jo2 = (Book) facade.readNodeWithModule(new StringReader(json1), Book.class);
        log.info("jo2={}", jo2.inspect());
        assertEquals(20, jo2.getIntegerByPath("/friends/rose/age/1"));

        String json2 = "[1,2,3]";
        JsonArray ja1 = (JsonArray) facade.readNodeWithModule(new StringReader(json2), JsonArray.class);
        log.info("ja1={}", ja1);

        Object ja2 = facade.readNodeWithModule(new StringReader(json2), Object.class);
        log.info("ja2={}, type={}", ja2, ja2.getClass());

        Object ja3 = facade.readNodeWithModule(new StringReader(json2), int[].class);
        log.info("ja3={}, type={}", ja3, ja3.getClass());

    }

    @Test
    public void testWrite1() {
        JacksonJsonFacade facade = new JacksonJsonFacade(new ObjectMapper());

        String json1 = "{\"id\":123,\"name\":\"han\",\"height\":175.3,\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        Book jo1 = (Book) facade.readNode(new StringReader(json1), Book.class);

        StringWriter output;
        output = new StringWriter();
        facade.writeNodeWithGeneral(output, jo1);
        String json2 = output.toString();
        assertEquals(json1, json2);

        output = new StringWriter();
        facade.writeNodeWithSpecific(output, jo1);
        String json3 = output.toString();
        assertEquals(json1, json3);

        output = new StringWriter();
        facade.writeNodeWithModule(output, jo1);
        String json4 = output.toString();
        assertEquals(json1, json4);
    }


}
