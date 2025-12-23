package org.sjf4j.facade.fastjson2;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.annotation.convertible.Convert;
import org.sjf4j.annotation.convertible.NodeConvertible;
import org.sjf4j.annotation.convertible.Unconvert;
import org.sjf4j.util.TypeReference;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class Fastjson2FacadeTest {

    @Test
    public void testSerDe1() {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";

        Sjf4jConfig.global(new Sjf4jConfig.Builder().readMode(Sjf4jConfig.ReadMode.STREAMING_GENERAL).build());
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

        Sjf4jConfig.global(new Sjf4jConfig.Builder().readMode(Sjf4jConfig.ReadMode.USE_MODULE).build());
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

        Sjf4jConfig.global(new Sjf4jConfig.Builder().writeMode(Sjf4jConfig.WriteMode.STREAMING_GENERAL).build());
        StringWriter output;
        output = new StringWriter();
        facade.writeNode(output, jo1);
        String json2 = output.toString();
        assertEquals(json1, json2);

        Sjf4jConfig.global(new Sjf4jConfig.Builder().writeMode(Sjf4jConfig.WriteMode.STREAMING_SPECIFIC).build());
        output = new StringWriter();
        facade.writeNode(output, jo1);
        String json3 = output.toString();
        assertEquals(json1, json3);

        Sjf4jConfig.global(new Sjf4jConfig.Builder().writeMode(Sjf4jConfig.WriteMode.USE_MODULE).build());
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
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade();
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
