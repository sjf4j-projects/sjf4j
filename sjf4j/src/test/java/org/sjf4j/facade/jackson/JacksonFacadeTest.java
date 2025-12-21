package org.sjf4j.facade.jackson;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeRegistry;
import org.sjf4j.annotation.convertible.Convert;
import org.sjf4j.annotation.convertible.NodeConvertible;
import org.sjf4j.annotation.convertible.Unconvert;
import org.sjf4j.util.TypeReference;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;

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

        JsonConfig.global(new JsonConfig.Builder().readMode(JsonConfig.ReadMode.USE_MODULE).build());
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
        JacksonJsonFacade facade = new JacksonJsonFacade(new ObjectMapper());

        String json1 = "{\"id\":123,\"name\":\"han\",\"height\":175.3,\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        Book jo1 = (Book) facade.readNode(new StringReader(json1), Book.class);

        JsonConfig.global(new JsonConfig.Builder().writeMode(JsonConfig.WriteMode.STREAMING_GENERAL).build());
        StringWriter output;
        output = new StringWriter();
        facade.writeNode(output, jo1);
        String json2 = output.toString();
        assertEquals(json1, json2);

        JsonConfig.global(new JsonConfig.Builder().writeMode(JsonConfig.WriteMode.STREAMING_SPECIFIC).build());
        output = new StringWriter();
        facade.writeNode(output, jo1);
        String json3 = output.toString();
        assertEquals(json1, json3);

        JsonConfig.global(new JsonConfig.Builder().writeMode(JsonConfig.WriteMode.USE_MODULE).build());
        output = new StringWriter();
        facade.writeNode(output, jo1);
        String json4 = output.toString();
        assertEquals(json1, json4);
    }


    @NodeConvertible
    public static class Ops {
        private final LocalDate localDate;

        public Ops(LocalDate localDate) {
            this.localDate = localDate;
        }
        @Convert
        public String convert() {
            return localDate.toString();
        }

        @Unconvert
        public static Ops unconvert(String raw) {
            return new Ops(LocalDate.parse(raw));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testConvertible1() {
        JacksonJsonFacade facade = new JacksonJsonFacade(new ObjectMapper());
        NodeRegistry.ConvertibleInfo ci = NodeRegistry.registerConvertible(Ops.class);
        facade.registerConvertible(ci);

        String json1 = "[\"2024-10-01\",\"2025-12-18\"]";
        List<Ops> list = (List<Ops>) facade.readNode(json1, new TypeReference<List<Ops>>() {}.getType());
        log.info("list={}", list);

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, list);
        String json2 = sw.toString();
        log.info("json2={}", json2);
        assertEquals(json1, json2);
    }

}
