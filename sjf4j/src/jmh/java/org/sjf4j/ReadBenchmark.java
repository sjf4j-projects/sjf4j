package org.sjf4j;


import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson.JacksonJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.node.TypeReference;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class ReadBenchmark {

//    private static final String JSON_DATA = "{\"name\":\"Alice\"}";
//    private static final String JSON_DATA = "{\"age\":25}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"city\":\"Singapore\"}}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"babies\":[{\"name\":\"Baby-0\",\"age\":1}]}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"}}";
    // Mixed structure JSON keeps nested objects/arrays so each framework covers the same workload.
    private static final String JSON_DATA = "{\"name\":\"Alice\",\"no_way\":99,\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";

    //    private static final String JSON_DATA2 = "{\n" +
//            "  \"name\": \"Alice\",\n" +
//            "  \"friends\": []" +
//            "}\n";
    private static final String JSON_DATA2 = "{\n" +
            "  \"name\": \"Alice\",\n" +
            "  \"friends\": [\n" +
            "    {\"name\": \"Bill\", \"active\": true },\n" +
            "    {\n" +
            "      \"name\": \"Cindy\",\n" +
            "      \"friends\": [\n" +
            "        {\"name\": \"David\"},\n" +
            "        {\"id\": 5, \"info\": \"blabla\"}\n" +
            "      ]\n" +
            "    }\n" +
            "  ],\n" +
            "  \"age\": 18\n" +
            "}\n";

    // Same structure as JSON_DATA2 but without unknown fields for User2 (no dynamic map).
    private static final String JSON_DATA2_NO_DYN = "{\n" +
            "  \"name\": \"Alice\",\n" +
            "  \"friends\": [\n" +
            "    {\"name\": \"Bill\"},\n" +
            "    {\n" +
            "      \"name\": \"Cindy\",\n" +
            "      \"friends\": [\n" +
            "        {\"name\": \"David\"},\n" +
            "        {}\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}\n";

    // Pure list of User2 to focus on array/collection parsing overhead.
    private static final String JSON_DATA2_LIST = "[\n" +
            "  {\"name\": \"Alice\", \"friends\": [\n" +
            "    {\"name\": \"Bill\", \"friends\": []},\n" +
            "    {\"name\": \"Cindy\", \"friends\": []}\n" +
            "  ]},\n" +
            "  {\"name\": \"David\", \"friends\": []}\n" +
            "]\n";

    private static final java.lang.reflect.Type USER2_LIST_TYPE =
            new TypeReference<List<User2>>() {}.getType();

    private static final ObjectMapper JACKSON = new ObjectMapper();
    private static final GsonBuilder GSON_BUILDER = new GsonBuilder();
    private static final Gson GSON = GSON_BUILDER.create();

    private static final SimpleJsonFacade SIMPLE_JSON_FACADE = new SimpleJsonFacade();

    static {
        JACKSON.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//        Sjf4jConfig.useBindingPath(false);
    }

    @State(Scope.Thread)
    public static class FacadeState {
        @Param({"SHARED_IO", "EXCLUSIVE_IO", "PLUGIN_MODULE"})
        public String streamingMode;

        @Param({"true", "false"})
        public String useBindingPath;

        public JacksonJsonFacade jacksonFacade;
        public GsonJsonFacade gsonFacade;
        public Fastjson2JsonFacade fastjson2Facade;

        @Setup(Level.Trial)
        public void setup() {
            Sjf4jConfig.global(new Sjf4jConfig.Builder()
                    .streamingMode(StreamingFacade.StreamingMode.valueOf(streamingMode))
                    .bindingPath(Boolean.parseBoolean(useBindingPath))
                    .build());
            jacksonFacade = new JacksonJsonFacade(new ObjectMapper());
            gsonFacade = new GsonJsonFacade(new GsonBuilder());
            fastjson2Facade = new Fastjson2JsonFacade();
        }
    }



    // ----- Simple JSON baselines -----
    @Benchmark
    public Object json_simple_facade_pojo() throws IOException {
        return SIMPLE_JSON_FACADE.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_simple_facade_jojo() throws IOException {
        return SIMPLE_JSON_FACADE.readNode(JSON_DATA2, User2.class);
    }

    // ----- Jackson baselines -----
    @Benchmark
    public Object json_jackson_native_has_any() throws IOException {
        return JACKSON.readValue(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_jackson_native_no_any() throws IOException {
        return JACKSON.readValue(JSON_DATA2, User2.class);
    }

    @Benchmark
    public Object json_jackson_native_map() throws IOException {
        return JACKSON.readValue(JSON_DATA2, Object.class);
    }

    @Benchmark
    public Object json_jackson_facade_pojo(FacadeState state) throws IOException {
        return state.jacksonFacade.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_jackson_facade_jojo(FacadeState state) throws IOException {
        return state.jacksonFacade.readNode(JSON_DATA2, User2.class);
    }

//    @Benchmark
//    public Object json_jackson_facade_jojo_no_dyn(FacadeState state) throws IOException {
//        return state.jacksonFacade.readNode(JSON_DATA2_NO_DYN, User2.class);
//    }
//
//    @Benchmark
//    public Object json_jackson_facade_jojo_list(FacadeState state) throws IOException {
//        return state.jacksonFacade.readNode(JSON_DATA2_LIST, USER2_LIST_TYPE);
//    }


    // ----- Gson baselines -----
    @Benchmark
    public Object json_gson_native_no_any() {
        return GSON.fromJson(JSON_DATA2, User2.class);
    }

    @Benchmark
    public Object json_gson_native_map() {
        return GSON.fromJson(JSON_DATA2, Object.class);
    }

    @Benchmark
    public Object json_gson_facade_pojo(FacadeState state) {
        return state.gsonFacade.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_gson_facade_jojo(FacadeState state) {
        return state.gsonFacade.readNode(JSON_DATA2, User2.class);
    }

//    @Benchmark
//    public Object json_gson_facade_jojo_no_dyn(FacadeState state) {
//        return state.gsonFacade.readNode(JSON_DATA2_NO_DYN, User2.class);
//    }
//
//    @Benchmark
//    public Object json_gson_facade_jojo_list(FacadeState state) {
//        return state.gsonFacade.readNode(JSON_DATA2_LIST, USER2_LIST_TYPE);
//    }

    // ----- Fastjson2 baselines -----
    @Benchmark
    public Object json_fastjson2_native_has_any() {
        return JSON.parseObject(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_fastjson2_native_no_any() {
        return JSON.parseObject(JSON_DATA2, User2.class);
    }

    @Benchmark
    public Object json_fastjson2_native_map() {
        return JSON.parseObject(JSON_DATA2, Object.class);
    }

    @Benchmark
    public Object json_fastjson2_facade_pojo(FacadeState state) throws IOException {
        return state.fastjson2Facade.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_fastjson2_facade_jojo(FacadeState state) throws IOException {
        return state.fastjson2Facade.readNode(JSON_DATA2, User2.class);
    }

//    @Benchmark
//    public Object json_fastjson2_facade_jojo_no_dyn(FacadeState state) throws IOException {
//        return state.fastjson2Facade.readNode(JSON_DATA2_NO_DYN, User2.class);
//    }
//
//    @Benchmark
//    public Object json_fastjson2_facade_jojo_list(FacadeState state) throws IOException {
//        return state.fastjson2Facade.readNode(JSON_DATA2_LIST, USER2_LIST_TYPE);
//    }

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


    // Define a POJO `User`
    @Getter @Setter
    static class User {
        String name;
        List<User> friends;
        @JsonAnySetter @JsonAnyGetter
        Map<String, Object> ext = new LinkedHashMap<>();
    }

    // Define a JOJO `User2`
    @Getter @Setter
    static class User2 extends JsonObject {
        String name;
        List<User2> friends;
    }


}
