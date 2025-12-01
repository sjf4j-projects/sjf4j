package org.sjf4j;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.facades.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facades.fastjson2.Fastjson2Walker;
import org.sjf4j.facades.gson.GsonJsonFacade;
import org.sjf4j.facades.gson.GsonWalker;
import org.sjf4j.facades.jackson.JacksonJsonFacade;
import org.sjf4j.facades.jackson.JacksonWalker;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
public class JsonReadBenchmark {

//    private static final String JSON_DATA = "{\"name\":\"Alice\"}";
//    private static final String JSON_DATA = "{\"age\":25}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"city\":\"Singapore\"}}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"babies\":[{\"name\":\"Baby-0\",\"age\":1}]}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"}}";
    // Mixed structure JSON keeps nested objects/arrays so each framework covers the same workload.
    private static final String JSON_DATA = "{\"name\":\"Alice\",\"no_way\":99,\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";

    private static final ObjectMapper JACKSON = new ObjectMapper();
    private static final GsonBuilder GSON_BUILDER = new GsonBuilder();
    private static final Gson GSON = GSON_BUILDER.create();

    private static final JacksonJsonFacade JACKSON_FACADE = new JacksonJsonFacade(JACKSON);
    private static final GsonJsonFacade GSON_FACADE = new GsonJsonFacade(GSON_BUILDER);
    private static final Fastjson2JsonFacade FASTJSON2_FACADE = new Fastjson2JsonFacade();


    // ----- Jackson baselines -----
    @Benchmark
    public Object json_jackson_pojo() throws IOException {
        return JACKSON.readValue(new StringReader(JSON_DATA), Person.class);
    }

    @Benchmark
    public Object json_jackson_node() throws IOException {
        return JACKSON.readTree(new StringReader(JSON_DATA));
    }

    @Benchmark
    public Object json_jackson_walk2Map() throws IOException {
        JsonParser p = JACKSON.getFactory().createParser(new StringReader(JSON_DATA));
        return JacksonWalker.walk2Map(p);
    }

    @Benchmark
    public Object json_jackson_walk2Jo() throws IOException {
        JsonParser p = JACKSON.getFactory().createParser(new StringReader(JSON_DATA));
        return JacksonWalker.walk2Jo(p);
    }

    @Benchmark
    public Object json_jackson_facade_spec() throws IOException {
        return JACKSON_FACADE.readNodeWithSpecific(new StringReader(JSON_DATA), Person.class);
    }

    @Benchmark
    public Object json_jackson_facade_general() throws IOException {
        return JACKSON_FACADE.readNodeWithGeneral(new StringReader(JSON_DATA), Person.class);
    }

    @Benchmark
    public Object json_jackson_facade_extra() throws IOException {
        return JACKSON_FACADE.readNodeWithModule(new StringReader(JSON_DATA), Person.class);
    }


    // ----- Gson baselines -----
    @Benchmark
    public Object json_gson_pojo() {
        return GSON.fromJson(new StringReader(JSON_DATA), Person.class);
    }

    @Benchmark
    public void json_gson_walk2Null() throws IOException {
        GsonWalker.walk2Null(GSON.newJsonReader(new StringReader(JSON_DATA)));
    }

    @Benchmark
    public void json_gson_walk2Jo() throws IOException {
        GsonWalker.walk2Jo(GSON.newJsonReader(new StringReader(JSON_DATA)));
    }

    @Benchmark
    public Object json_gson_facade_general() {
        return GSON_FACADE.readNodeWithGeneral(new StringReader(JSON_DATA), Person.class);
    }

    @Benchmark
    public Object json_gson_facade_spec() {
        return GSON_FACADE.readNodeWithSpecific(new StringReader(JSON_DATA), Person.class);
    }

    @Benchmark
    public Object json_gson_facade_extra() {
        return GSON_FACADE.readNodeWithModule(new StringReader(JSON_DATA), Person.class);
    }

    @Test
    public void test_facade_extra() {
        GSON_FACADE.readNodeWithModule(new StringReader(JSON_DATA), Person.class);
    }


    // ----- Fastjson2 baselines -----
    @Benchmark
    public Object json_fastjson2_pojo() {
        return JSON.parseObject(new StringReader(JSON_DATA), Person.class);
    }

    @Benchmark
    public void json_fastjson2_walk() throws IOException {
        Fastjson2Walker.walk2Jo(JSONReader.of(JSON_DATA));
    }

    @Benchmark
    public Object json_fastjson2_facade_general() throws IOException {
        return FASTJSON2_FACADE.readNodeWithGeneral(new StringReader(JSON_DATA), Person.class);
    }

    @Benchmark
    public Object json_fastjson2_facade_extra() throws IOException {
        return FASTJSON2_FACADE.readNodeWithModule(new StringReader(JSON_DATA), Person.class);
    }

    @Benchmark
    public Object json_fastjson2_facade_spec() throws IOException {
        return FASTJSON2_FACADE.readNodeWithSpecific(new StringReader(JSON_DATA), Person.class);
    }



    // --------- 模拟的 POJO ------------
    // Extend JsonObject so every framework can reuse the same helper methods when populating nested structures.
    public static class Person extends JsonObject {
        public String name;
        public String nick;
        public int age;
        public Info info;
        public List<Baby> babies;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNick() {
            return nick;
        }

        public void setNick(String nick) {
            this.nick = nick;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public Info getInfo() {
            return info;
        }

        public void setInfo(Info info) {
            this.info = info;
        }

        public List<Baby> getBabies() {
            return babies;
        }

        public void setBabies(List<Baby> babies) {
            this.babies = babies;
        }
    }

    @Getter @Setter
    public static class Info extends JsonObject {
        public String email;
        public String city;
    }

    @Getter @Setter
    public static class Baby extends JsonObject {
        public String name;
//        public int age;
    }


    ///

//    @Benchmark
//    public Object fastjson2_pojo2() {
////        return JSONReader.of(new StringReader(JSON_DATA)).read(Person.class);
//        final JSONReader.Context context = JSONFactory.createReadContext(new JSONReader.Feature[]{});
//        final ObjectReader<Object> objectReader = context.getObjectReader(Person.class);
//        JSONReader reader = JSONReader.of(new StringReader(JSON_DATA));
//        Object object = objectReader.readObject(reader, Person.class, null, 0);
//        return object;
//
//    }

//    @Benchmark
//    public Object fastjson2_pojo3() {
//        final JSONReader.Context context = JSONFactory.createReadContext(new JSONReader.Feature[]{});
//        final ObjectReader<Object> objectReader = context.getObjectReader(Person.class);
//        try (JSONReader reader = JSONReader.of(new StringReader(JSON_DATA), context)) {
//            if (reader.isEnd()) {
//                return null;
//            }
//            Object object = objectReader.readObject(reader, Person.class, null, 0);
//            return object;
//        }
//    }

}