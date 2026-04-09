package org.sjf4j;


import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.json.Json;
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
import org.sjf4j.facade.gson.GsonModule;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.jsonp.JsonpJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.node.TypeReference;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 8, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2)
@Threads(1)
@State(Scope.Thread)
public class WriteBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{WriteBenchmark.class.getName()});
    }

    // Mixed structure JSON keeps nested objects/arrays so each framework covers the same workload.
    private static final String JSON_DATA2 = "{\n" +
            "  \"name\": \"Alice\",\n" +
            "  \"friends\": [\n" +
            "    {\"name\": \"Bill\", \"active\": true, \"score\": 88.5 },\n" +
            "    {\"name\": \"Eve\", \"active\": false, \"tags\": [\"x\",\"y\"] },\n" +
            "    {\n" +
            "      \"name\": \"Cindy\",\n" +
            "      \"friends\": [\n" +
            "        {\"name\": \"David\"},\n" +
            "        {\"id\": 5, \"info\": \"blabla\"},\n" +
            "        {\"name\": \"Frank\", \"friends\": [{\"name\": \"Gina\"}, {\"name\": \"Hank\"}]},\n" +
            "        {\"name\": \"Ivy\", \"meta\": {\"a\":1,\"b\":2}},\n" +
            "        {}\n" +
            "      ]\n" +
            "    },\n" +
            "    {\"name\": \"Jane\"},\n" +
            "    {\"name\": \"Kyle\", \"friends\": [{\"name\": \"Liam\"}, {\"name\": \"Mia\"}]},\n" +
            "    {\"name\": \"Nina\", \"age\": 19, \"city\": \"SG\"}\n" +
            "  ],\n" +
            "  \"age\": 18,\n" +
            "  \"city\": \"Singapore\",\n" +
            "  \"ext\": {\"k1\": 1, \"k2\": true, \"k3\": [1,2,3]},\n" +
            "  \"extra1\": 12345,\n" +
            "  \"extra2\": \"hello\",\n" +
            "  \"extra3\": {\"nested\": {\"x\": 1, \"y\": [1,2,3,4]}}\n" +
            "}\n";

    private static final ObjectMapper JACKSON2 = new ObjectMapper();
    private static final Gson GSON = createNativeGson();
    private static final SimpleJsonFacade SIMPLE_JSON_FACADE = new SimpleJsonFacade();
    private static final JsonpJsonFacade JSONP_JSON_FACADE = new JsonpJsonFacade();

    private static final UserPojo USER_POJO;
    private static final UserHasAny USER_HAS_ANY;
    private static final UserJojo USER_JOJO;
    private static final Map<String, Object> MAP_NODE;
    private static final jakarta.json.JsonObject JSONP_MAP_NODE;

    static {
        try {
            USER_POJO = Sjf4j.global().fromJson(JSON_DATA2, UserPojo.class);
            USER_HAS_ANY = Sjf4j.global().fromJson(JSON_DATA2, UserHasAny.class);
            USER_JOJO = Sjf4j.global().fromJson(JSON_DATA2, UserJojo.class);
            MAP_NODE = Sjf4j.global().fromJson(JSON_DATA2, new TypeReference<Map<String, Object>>() {});
            JSONP_MAP_NODE = Json.createReader(new StringReader(JSON_DATA2)).readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Gson createNativeGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
        builder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
        builder.setFieldNamingStrategy(field -> {
            String name = org.sjf4j.node.ReflectUtil.getExplicitName(field);
            return name != null ? name : field.getName();
        });
        return builder.create();
    }

    @State(Scope.Thread)
    public static class FacadeState {
        @Param({"SHARED_IO", "EXCLUSIVE_IO", "PLUGIN_MODULE"})
        public String streamingMode;

        public Jackson2JsonFacade jackson2Facade;
        public Fastjson2JsonFacade fastjson2Facade;

        @Setup(Level.Trial)
        public void setup() {
            StreamingFacade.StreamingMode mode = StreamingFacade.StreamingMode.valueOf(streamingMode);
            jackson2Facade = new Jackson2JsonFacade(new ObjectMapper(), mode);
            fastjson2Facade = new Fastjson2JsonFacade(mode);
        }
    }

    @State(Scope.Thread)
    public static class GsonFacadeState {
        @Param({"SHARED_IO", "PLUGIN_MODULE"})
        public String streamingMode;

        public GsonJsonFacade gsonFacade;

        @Setup(Level.Trial)
        public void setup() {
            StreamingFacade.StreamingMode mode = StreamingFacade.StreamingMode.valueOf(streamingMode);
            gsonFacade = new GsonJsonFacade(new GsonBuilder(), mode);
        }
    }


    // ----- Jackson2 baselines -----
    @Benchmark
    public Object json_jackson2_pojo_native() throws Exception {
        return JACKSON2.writeValueAsString(USER_POJO);
    }

    @Benchmark
    public Object json_jackson2_hasAny_native() throws Exception {
        return JACKSON2.writeValueAsString(USER_HAS_ANY);
    }

    @Benchmark
    public Object json_jackson2_map_native() throws Exception {
        return JACKSON2.writeValueAsString(MAP_NODE);
    }

    @Benchmark
    public Object json_jackson2_pojo_facade(FacadeState state) {
        return state.jackson2Facade.writeNodeAsString(USER_POJO);
    }

    @Benchmark
    public Object json_jackson2_jojo_facade(FacadeState state) {
        return state.jackson2Facade.writeNodeAsString(USER_JOJO);
    }

    @Benchmark
    public Object json_jackson2_hasAny_facade(FacadeState state) {
        return state.jackson2Facade.writeNodeAsString(USER_HAS_ANY);
    }

    @Benchmark
    public Object json_jackson2_map_facade(FacadeState state) {
        return state.jackson2Facade.writeNodeAsString(MAP_NODE);
    }


    // ----- Gson baselines -----
    @Benchmark
    public Object json_gson_pojo_native() {
        return GSON.toJson(USER_POJO);
    }

    @Benchmark
    public Object json_gson_hasAny_native() throws Exception {
        return GSON.toJson(USER_HAS_ANY);
    }

    @Benchmark
    public Object json_gson_map_native() {
        return GSON.toJson(MAP_NODE);
    }

    @Benchmark
    public Object json_gson_pojo_facade(GsonFacadeState state) {
        return state.gsonFacade.writeNodeAsString(USER_POJO);
    }

    @Benchmark
    public Object json_gson_jojo_facade(GsonFacadeState state) {
        return state.gsonFacade.writeNodeAsString(USER_JOJO);
    }

    @Benchmark
    public Object json_gson_hasAny_facade(GsonFacadeState state) {
        return state.gsonFacade.writeNodeAsString(USER_HAS_ANY);
    }

    @Benchmark
    public Object json_gson_map_facade(GsonFacadeState state) {
        return state.gsonFacade.writeNodeAsString(MAP_NODE);
    }


    // ----- Fastjson2 baselines -----
    @Benchmark
    public Object json_fastjson2_pojo_native() {
        return JSON.toJSONString(USER_POJO);
    }

    @Benchmark
    public Object json_fastjson2_hasAny_native() {
        return JSON.toJSONString(USER_HAS_ANY);
    }

    @Benchmark
    public Object json_fastjson2_map_native() {
        return JSON.toJSONString(MAP_NODE);
    }

    @Benchmark
    public Object json_fastjson2_pojo_facade(FacadeState state) {
        return state.fastjson2Facade.writeNodeAsString(USER_POJO);
    }

    @Benchmark
    public Object json_fastjson2_jojo_facade(FacadeState state) {
        return state.fastjson2Facade.writeNodeAsString(USER_JOJO);
    }

    @Benchmark
    public Object json_fastjson2_hasAny_facade(FacadeState state) {
        return state.fastjson2Facade.writeNodeAsString(USER_HAS_ANY);
    }

    @Benchmark
    public Object json_fastjson2_map_facade(FacadeState state) {
        return state.fastjson2Facade.writeNodeAsString(MAP_NODE);
    }

    // ----- JSON-P baselines -----
    @Benchmark
    public Object json_jsonp_map_native() {
        StringWriter sw = new StringWriter();
        Json.createWriter(sw).write(JSONP_MAP_NODE);
        return sw.toString();
    }

    @Benchmark
    public Object json_jsonp_pojo_facade() {
        return JSONP_JSON_FACADE.writeNodeAsString(USER_POJO);
    }

    @Benchmark
    public Object json_jsonp_map_facade() {
        return JSONP_JSON_FACADE.writeNodeAsString(MAP_NODE);
    }

    @Benchmark
    public Object json_jsonp_jojo_facade() {
        return JSONP_JSON_FACADE.writeNodeAsString(USER_JOJO);
    }

    // ----- Simple JSON baselines -----
    @Benchmark
    public Object json_simple_pojo_facade() {
        return SIMPLE_JSON_FACADE.writeNodeAsString(USER_POJO);
    }

    @Benchmark
    public Object json_simple_jojo_facade() {
        return SIMPLE_JSON_FACADE.writeNodeAsString(USER_JOJO);
    }


    static class UserPojo {
        public String name;
        public List<UserPojo> friends;
    }

    static class UserJojo extends JsonObject {
        public String name;
        public List<UserJojo> friends;
    }

    static class UserHasAny {
        public String name;
        public List<UserHasAny> friends;
        @JsonAnySetter @JsonAnyGetter
        public Map<String, Object> ext = new LinkedHashMap<>();
    }
}
