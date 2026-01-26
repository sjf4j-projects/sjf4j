package org.sjf4j;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.fastjson2.Fastjson2Reader;
import org.sjf4j.facade.fastjson2.Fastjson2StreamingIO;
import org.sjf4j.facade.fastjson2.Fastjson2Walker;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.gson.GsonReader;
import org.sjf4j.facade.gson.GsonStreamingIO;
import org.sjf4j.facade.gson.GsonWalker;
import org.sjf4j.facade.jackson.JacksonJsonFacade;
import org.sjf4j.facade.StreamingIO;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class CompareWriteTest {

    // --------- 模拟的 POJO ------------
    @ToString
    public static class Person {
        public String name;
        public int age;
        public Info info;
        public List<Baby> babies;
    }

    @ToString
    public static class Info {
        public String email;
        public String city;
    }

    @ToString
    public static class Baby {
        public String name;
        public int age;
    }

    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"}}";

//    @Test
//    public void testCompare1() {
//        JsonObject jo = Sjf4j.readObjectFromJson(new StringReader(JSON_DATA));
//
//        new JacksonJsonFacade(new ObjectMapper()).w
//        Person p5 = new GsonJsonFacade(new Gson()).readObject(new StringReader(JSON_DATA), Person.class);
//        Person p6 = new Fastjson2JsonFacade().readObject(new StringReader(JSON_DATA), Person.class);
//
//        log.info("p1={}", p1);
//        log.info("p2={}", p2);
//        log.info("p3={}", p3);
//        log.info("p4={}", p4);
//        log.info("p5={}", p5);
//        log.info("p6={}", p6);
//        assertEquals(p1.toString(), p2.toString());
//        assertEquals(p1.toString(), p3.toString());
//        assertEquals(p1.toString(), p4.toString());
//        assertEquals(p1.toString(), p5.toString());
//        assertEquals(p1.toString(), p6.toString());
//    }


//    @Test
//    public void profilingCreateReader1() throws IOException {
//        ObjectMapper om = new ObjectMapper();
//        JacksonJsonFacade jacksonFacade = new JacksonJsonFacade(om);
//        long start, end;
//
//        for (long i = 0; i < 10000; i++) {
//            new JacksonReader(om.getFactory().createParser(new StringReader(JSON_DATA)));
//        }
//
//        start = System.nanoTime();
//        for (long i = 0; i < 100000; i++) {
//            new JacksonReader(om.getFactory().createParser(new StringReader(JSON_DATA)));
//        }
//        end = System.nanoTime();
//        log.info("createReader0: {}", (end - start)/1000_000);
//
//        start = System.nanoTime();
//        for (long i = 0; i < 100000; i++) {
//            jacksonFacade.createReader(new StringReader(JSON_DATA));
//        }
//        end = System.nanoTime();
//        log.info("createReader1: {}", (end - start)/1000_000);
//
//        start = System.nanoTime();
//        for (long i = 0; i < 100000; i++) {
//            jacksonFacade.createReader2(new StringReader(JSON_DATA));
//        }
//        end = System.nanoTime();
//        log.info("createReader2: {}", (end - start)/1000_000);
//
//    }


    @Test
    public void profilingJackson0() throws IOException {
        ObjectMapper om = new ObjectMapper();
        JacksonJsonFacade jacksonFacade = new JacksonJsonFacade(new ObjectMapper());
        long start, end;

        for (long i = 0; i < 100_000; i++) {
            Sjf4j.fromJson(new StringReader(JSON_DATA), Object.class);
        }
        start = System.nanoTime();
        for (long i = 0; i < 100_000; i++) {
            Sjf4j.fromJson(new StringReader(JSON_DATA), Object.class);
        }
        end = System.nanoTime();
        log.info("Jackson-Sjf4j-jojo1: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 100_000; i++) {
            Sjf4j.fromJson(new StringReader(JSON_DATA), Person.class);
        }
        end = System.nanoTime();
        log.info("Jackson-Sjf4j-pojo1: {}", (end - start) / 1000_000);

        for (long i = 0; i < 100_000; i++) {
            om.readTree(new StringReader(JSON_DATA));
        }
        start = System.nanoTime();
        for (long i = 0; i < 100_000; i++) {
            om.readTree(new StringReader(JSON_DATA));
        }
        end = System.nanoTime();
        log.info("Jackson-node: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 100_000; i++) {
            om.readValue(new StringReader(JSON_DATA), Person.class);
        }
        end = System.nanoTime();
        log.info("Jackson-pojo: {}", (end - start) / 1000_000);

    }


    @Test
    public void profilingFastjson20() throws IOException {
        Fastjson2JsonFacade fastFacade = new Fastjson2JsonFacade();
        long start, end;

//        start = System.nanoTime();
//        for (long i = 0; i < 1000_000; i++) {
//            Fastjson2Walker.walk2Map(JSONReader.of(JSON_DATA)));
//        }
//        end = System.nanoTime();
//        log.info("Fastjson2-walk2Map: {}", (end - start) / 1000_000);

        for (long i = 0; i < 1000; i++) {
            JSON.parseObject(new StringReader(JSON_DATA));
        }

        for (long i = 0; i < 1000; i++) {
            JSON.parseObject(new StringReader(JSON_DATA), Person.class);
        }

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            JSON.parseObject(new StringReader(JSON_DATA));
        }
        end = System.nanoTime();
        log.info("Fastjson2-node: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            JSON.parseObject(new StringReader(JSON_DATA), Person.class);
        }
        end = System.nanoTime();
        log.info("Fastjson2-pojo: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            Fastjson2Walker.walk2Null(JSONReader.of(new StringReader(JSON_DATA)));
        }
        end = System.nanoTime();
        log.info("Fastjson2-walk2Jo: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            JSONReader reader = JSONReader.of(new StringReader(JSON_DATA));
            Fastjson2StreamingIO.readNode(reader, Object.class);
        }
        end = System.nanoTime();
        log.info("Fastjson2-Only-jojo1: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            fastFacade.readNode(new StringReader(JSON_DATA), Object.class);
        }
        end = System.nanoTime();
        log.info("Fastjson2-Only-jojo2: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            JSONReader reader = JSONReader.of(new StringReader(JSON_DATA));
            Fastjson2StreamingIO.readNode(reader, Person.class);
        }
        end = System.nanoTime();
        log.info("Fastjson2-Only-pojo1: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            JSONReader reader = JSONReader.of(new StringReader(JSON_DATA));
            StreamingIO.readNode(new Fastjson2Reader(reader), Object.class);
        }
        end = System.nanoTime();
        log.info("Fastjson2-Uni-jojo1: {}", (end - start) / 1000_000);

        for (long i = 0; i < 1000; i++) {
            Sjf4j.fromJson(new StringReader(JSON_DATA), Object.class);
        }
        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            Sjf4j.fromJson(new StringReader(JSON_DATA), Object.class);
        }
        end = System.nanoTime();
        log.info("Jackson-Sjf4j-jojo1: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            Sjf4j.fromJson(new StringReader(JSON_DATA), Person.class);
        }
        end = System.nanoTime();
        log.info("Jackson-Sjf4j-pojo1: {}", (end - start) / 1000_000);

    }


    @Test
    public void profilingGson0() throws IOException {
        Gson gson = new Gson();
        GsonJsonFacade gsonFacade = new GsonJsonFacade(new GsonBuilder());
        long start, end;

//        start = System.nanoTime();
//        for (long i = 0; i < 1000_000; i++) {
//            Fastjson2Walker.walk2Map(JSONReader.of(JSON_DATA)));
//        }
//        end = System.nanoTime();
//        log.info("Fastjson2-walk2Map: {}", (end - start) / 1000_000);

        for (long i = 0; i < 1000; i++) {
            gson.fromJson(new StringReader(JSON_DATA), com.google.gson.JsonObject.class);
        }

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            gson.fromJson(new StringReader(JSON_DATA), com.google.gson.JsonObject.class);
        }
        end = System.nanoTime();
        log.info("Gson-node: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            gson.fromJson(new StringReader(JSON_DATA), Person.class);
        }
        end = System.nanoTime();
        log.info("Gson-pojo: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            GsonWalker.walk2Null(gson.newJsonReader(new StringReader(JSON_DATA)));
        }
        end = System.nanoTime();
        log.info("Gson-walk2Null: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            GsonWalker.walk2Jo(gson.newJsonReader(new StringReader(JSON_DATA)));
        }
        end = System.nanoTime();
        log.info("Gson-walk2Jo: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            JsonReader reader = gson.newJsonReader(new StringReader(JSON_DATA));
            GsonStreamingIO.readNode(reader, Object.class);
        }
        end = System.nanoTime();
        log.info("Gson-Only-jojo1: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            gsonFacade.readNode(new StringReader(JSON_DATA), Object.class);
        }
        end = System.nanoTime();
        log.info("Gson-Only-jojo2: {}", (end - start) / 1000_000);

        for (long i = 0; i < 1000; i++) {
            JsonReader reader = gson.newJsonReader(new StringReader(JSON_DATA));
            GsonStreamingIO.readNode(reader, Person.class);
        }

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            JsonReader reader = gson.newJsonReader(new StringReader(JSON_DATA));
            GsonStreamingIO.readNode(reader, Person.class);
        }
        end = System.nanoTime();
        log.info("Gson-Only-pojo1: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            gsonFacade.readNode(new StringReader(JSON_DATA), Person.class);
        }
        end = System.nanoTime();
        log.info("Gson-Only-pojo2: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            JsonReader reader = gson.newJsonReader(new StringReader(JSON_DATA));
            StreamingIO.readNode(new GsonReader(reader), Object.class);
        }
        end = System.nanoTime();
        log.info("Gson-Uni-jojo1: {}", (end - start) / 1000_000);


        for (long i = 0; i < 1000; i++) {
            Sjf4j.fromJson(new StringReader(JSON_DATA), Object.class);
        }
        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            Sjf4j.fromJson(new StringReader(JSON_DATA), Object.class);
        }
        end = System.nanoTime();
        log.info("Jackson-Sjf4j-jojo1: {}", (end - start) / 1000_000);

        start = System.nanoTime();
        for (long i = 0; i < 1000; i++) {
            Sjf4j.fromJson(new StringReader(JSON_DATA), Person.class);
        }
        end = System.nanoTime();
        log.info("Jackson-Sjf4j-pojo1: {}", (end - start) / 1000_000);

    }


}

