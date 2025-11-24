package org.sjf4j;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.sjf4j.facades.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facades.gson.GsonJsonFacade;
import org.sjf4j.facades.jackson.JacksonJsonFacade;
import org.sjf4j.facades.jackson.JacksonReader;
import org.sjf4j.facades.jackson.JacksonStreamingUtil;
import org.sjf4j.facades.jackson.JacksonWalker;
import org.sjf4j.util.StreamingUtil;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
public class JsonBenchmark {

//    private static final String JSON_DATA = "{\"name\":\"Alice\"}";
//    private static final String JSON_DATA = "{\"age\":25}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"city\":\"Singapore\"}}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"babies\":[{\"name\":\"Baby-0\",\"age\":1}]}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"}}";
    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";

    private static final ObjectMapper JACKSON = new ObjectMapper();
    private static final Gson GSON = new Gson();

    private static final JacksonJsonFacade JACKSON_FACADE = new JacksonJsonFacade(JACKSON);
    private static final GsonJsonFacade GSON_FACADE = new GsonJsonFacade(GSON);
    private static final Fastjson2JsonFacade FASTJSON2_FACADE = new Fastjson2JsonFacade();

//    @Benchmark
//    public void jackson_pojo(Blackhole bh) throws IOException {
//        for (int i = 0; i < 1000; i++) {
//            Person v = JACKSON.readValue(new StringReader(JSON_DATA), Person.class);
//            bh.consume(v);
//        }
//    }
//
//    @Benchmark
//    public void jackson_node(Blackhole bh) throws IOException {
//        for (int i = 0; i < 1000; i++) {
//            JsonNode v = JACKSON.readTree(new StringReader(JSON_DATA));
//            bh.consume(v);
//        }
//    }
//
//    @Benchmark
//    public void jackson_walk2Map(Blackhole bh) throws IOException {
//        for (int i = 0; i < 1000; i++) {
//            JsonParser p = JACKSON.getFactory().createParser(new StringReader(JSON_DATA));
//            Object v = JacksonWalker.walk2Map(p);
//            bh.consume(v);
//        }
//    }
//
//    @Benchmark
//    public void jackson_walk2Jo(Blackhole bh) throws IOException {
//        for (int i = 0; i < 1000; i++) {
//            JsonParser p = JACKSON.getFactory().createParser(new StringReader(JSON_DATA));
//            Object v = JacksonWalker.walk2Jo(p);
//            bh.consume(v);
//        }
//    }
//
//    @Benchmark
//    public void jackson_only_jojo1(Blackhole bh) throws IOException {
//        for (int i = 0; i < 1000; i++) {
//            JsonParser parser = JACKSON.getFactory().createParser(new StringReader(JSON_DATA));
//            Object v = JacksonStreamingUtil.readNode(parser, Object.class);
//            bh.consume(v);
//        }
//    }
//
//    @Benchmark
//    public void jackson_only_pojo1(Blackhole bh) throws IOException {
//        for (int i = 0; i < 1000; i++) {
//            JsonParser parser = JACKSON.getFactory().createParser(new StringReader(JSON_DATA));
//            Object v = JacksonStreamingUtil.readNode(parser, Person.class);
//            bh.consume(v);
//        }
//    }
//
//    @Benchmark
//    public void jackson_uni_jojo1(Blackhole bh) throws IOException {
//        for (int i = 0; i < 1000; i++) {
//            Object v = JACKSON_FACADE.readNode(new StringReader(JSON_DATA), Object.class);
//            bh.consume(v);
//        }
//    }
//
//    @Benchmark
//    public void jackson_uni_pojo1(Blackhole bh) throws IOException {
//        for (int i = 0; i < 1000; i++) {
//            Object v = JACKSON_FACADE.readNode(new StringReader(JSON_DATA), Person.class);
//            bh.consume(v);
//        }
//    }
//
//    @Benchmark
//    public void jackson_uni_jojo2(Blackhole bh) throws IOException {
//        for (int i = 0; i < 1000; i++) {
//            JsonParser parser = JACKSON.getFactory().createParser(new StringReader(JSON_DATA));
//            Object v = StreamingUtil.readNode(new JacksonReader(parser), Object.class);
//            bh.consume(v);
//        }
//    }
//
//    @Benchmark
//    public void jackson_sjf4j_jojo1(Blackhole bh) throws IOException {
//        for (int i = 0; i < 1000; i++) {
//            Object v = Sjf4j.fromJson(new StringReader(JSON_DATA));
//            bh.consume(v);
//        }
//    }



//    @Benchmark
//    public Object gson_pojo() {
//        return GSON.fromJson(JSON_DATA, Person.class);
//    }
//
//    @Benchmark
//    public void gson_walk2Null() throws IOException {
//        GsonWalker.walk2Null(GSON.newJsonReader(new StringReader(JSON_DATA)));
//    }
//
//    @Benchmark
//    public void gson_walk2Jo() throws IOException {
//        GsonWalker.walk2Jo(GSON.newJsonReader(new StringReader(JSON_DATA)));
//    }
//
//    @Benchmark
//    public Object gson_facade_pojo() {
//        return GSON_FACADE.readNode(new StringReader(JSON_DATA), Person.class);
//    }
//
//    @Benchmark
//    public Object gson_facade_jojo() {
//        return GSON_FACADE.readNode(new StringReader(JSON_DATA), Object.class);
//    }
//
//    @Benchmark
//    public Object gson_uni_jojo() throws IOException {
//        JsonReader reader = GSON.newJsonReader(new StringReader(JSON_DATA));
//        return StreamingUtil.readNode(new GsonReader(reader), Object.class);
//    }


//    @Benchmark
//    public Object fastjson2_pojo() {
//        return JSON.parseObject(JSON_DATA, Person.class);
//    }
//
//    @Benchmark
//    public void fastjson2_walk() throws IOException {
//        Fastjson2Walker.walk2Jo(JSONReader.of(JSON_DATA));
//    }
//
//    @Benchmark
//    public Object fastjson2_facade_jojo() {
//        return FASTJSON2_FACADE.readObject(new StringReader(JSON_DATA));
//    }
//
//    @Benchmark
//    public Object fastjson2_facade_jojo1() throws IOException {
//        JSONReader reader = JSONReader.of(new StringReader(JSON_DATA));
//        return Fastjson2StreamingUtil2.readNode(reader, Object.class);
//    }
//
//    @Benchmark
//    public Object fastjson2_facade_jojo2() throws IOException {
//        JSONReader reader = JSONReader.of(new StringReader(JSON_DATA));
//        return Fastjson2StreamingUtil2.readNode(reader, Object.class);
//    }
//
//
//    @Benchmark
//    public Object fastjson2_facade_pojo() {
//        return FASTJSON2_FACADE.readObject(new StringReader(JSON_DATA), Person.class);
//    }
//
//    @Benchmark
//    public Object fastjson2_uni_jojo() throws IOException {
//        JSONReader reader = JSONReader.of(new StringReader(JSON_DATA));
//        return StreamingUtil.readNode(new Fastjson2Reader(reader), Object.class);
//    }



    // --------- 模拟的 POJO ------------
    public static class Person {
        public String name;
        public String nick;
        public int age;
        public Info info;
        public List<Baby> babies;
    }

    public static class Info {
        public String email;
        public String city;
    }

    public static class Baby {
        public String name;
        public int age;
    }

}