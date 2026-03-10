package org.sjf4j;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.json.Json;
import lombok.Getter;
import lombok.Setter;
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
@Warmup(iterations = 15, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class WriteBenchmark {

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

    private static final ObjectMapper JACKSON = new ObjectMapper();
    private static final GsonBuilder GSON_BUILDER = new GsonBuilder();
    private static final Gson GSON = GSON_BUILDER.create();

    private static final SimpleJsonFacade SIMPLE_JSON_FACADE = new SimpleJsonFacade();

    private static final User USER_HAS_ANY;
    private static final UserPlain USER_PLAIN;
    private static final User2 USER_JOJO;
    private static final Map<String, Object> MAP_NODE;
    private static final jakarta.json.JsonObject JSONP_MAP_NODE;

    static {
        try {
            USER_HAS_ANY = Sjf4j.fromJson(JSON_DATA2_NO_DYN, User.class);
            USER_PLAIN = Sjf4j.fromJson(JSON_DATA2_NO_DYN, UserPlain.class);
            MAP_NODE = Sjf4j.fromJson(JSON_DATA2_NO_DYN, new TypeReference<Map<String, Object>>() {});
            JSONP_MAP_NODE = Json.createReader(new StringReader(JSON_DATA2_NO_DYN)).readObject();
            USER_JOJO = Sjf4j.fromJson(JSON_DATA2_NO_DYN, User2.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @State(Scope.Thread)
    public static class FacadeState {
        @Param({"SHARED_IO", "EXCLUSIVE_IO", "PLUGIN_MODULE"})
        public String streamingMode;

        @Param({/*"true",*/ "false"})
        public String useBindingPath;

        public JacksonJsonFacade jacksonFacade;
        public GsonJsonFacade gsonFacade;
        public Fastjson2JsonFacade fastjson2Facade;
        public JsonpJsonFacade jsonpFacade;

        @Setup(Level.Trial)
        public void setup() {
            Sjf4jConfig.global(new Sjf4jConfig.Builder()
                    .bindingPath(Boolean.parseBoolean(useBindingPath))
                    .build());
            jacksonFacade = new JacksonJsonFacade(new ObjectMapper(), StreamingFacade.StreamingMode.valueOf(streamingMode));
            gsonFacade = new GsonJsonFacade(new GsonBuilder(), StreamingFacade.StreamingMode.valueOf(streamingMode));
            fastjson2Facade = new Fastjson2JsonFacade(StreamingFacade.StreamingMode.valueOf(streamingMode));
            jsonpFacade = new JsonpJsonFacade(StreamingFacade.StreamingMode.valueOf(streamingMode));
        }
    }


    // ----- Simple JSON baselines -----
    @Benchmark
    public Object json_simple_facade_jojo() {
        return SIMPLE_JSON_FACADE.writeNodeAsString(USER_JOJO);
    }

    // ----- Jackson baselines -----
    @Benchmark
    public Object json_jackson_native_pojo() throws Exception {
        return JACKSON.writeValueAsString(USER_PLAIN);
    }

    @Benchmark
    public Object json_jackson_facade_pojo(FacadeState state) {
        return state.jacksonFacade.writeNodeAsString(USER_PLAIN);
    }

    @Benchmark
    public Object json_jackson_native_has_any() throws Exception {
        return JACKSON.writeValueAsString(USER_HAS_ANY);
    }

    @Benchmark
    public Object json_jackson_native_map() throws Exception {
        return JACKSON.writeValueAsString(MAP_NODE);
    }

    @Benchmark
    public Object json_jackson_facade_map(FacadeState state) {
        return state.jacksonFacade.writeNodeAsString(MAP_NODE);
    }

    @Benchmark
    public Object json_jackson_facade_jojo(FacadeState state) {
        return state.jacksonFacade.writeNodeAsString(USER_JOJO);
    }

    // ----- Gson baselines -----
    @Benchmark
    public Object json_gson_native_pojo() {
        return GSON.toJson(USER_PLAIN);
    }

    @Benchmark
    public Object json_gson_facade_pojo(FacadeState state) {
        return state.gsonFacade.writeNodeAsString(USER_PLAIN);
    }

    @Benchmark
    public Object json_gson_native_map() {
        return GSON.toJson(MAP_NODE);
    }

    @Benchmark
    public Object json_gson_facade_map(FacadeState state) {
        return state.gsonFacade.writeNodeAsString(MAP_NODE);
    }

    @Benchmark
    public Object json_gson_facade_jojo(FacadeState state) {
        return state.gsonFacade.writeNodeAsString(USER_JOJO);
    }

    // ----- Fastjson2 baselines -----
    @Benchmark
    public Object json_fastjson2_native_pojo() {
        return JSON.toJSONString(USER_PLAIN);
    }

    @Benchmark
    public Object json_fastjson2_facade_pojo(FacadeState state) {
        return state.fastjson2Facade.writeNodeAsString(USER_PLAIN);
    }

    @Benchmark
    public Object json_fastjson2_native_has_any() {
        return JSON.toJSONString(USER_HAS_ANY);
    }

    @Benchmark
    public Object json_fastjson2_native_map() {
        return JSON.toJSONString(MAP_NODE);
    }

    @Benchmark
    public Object json_fastjson2_facade_map(FacadeState state) {
        return state.fastjson2Facade.writeNodeAsString(MAP_NODE);
    }

    @Benchmark
    public Object json_fastjson2_facade_jojo(FacadeState state) {
        return state.fastjson2Facade.writeNodeAsString(USER_JOJO);
    }

    // ----- JSON-P baselines -----
    @Benchmark
    public Object json_jsonp_native_map() {
        StringWriter sw = new StringWriter();
        Json.createWriter(sw).write(JSONP_MAP_NODE);
        return sw.toString();
    }

    @Benchmark
    public Object json_jsonp_facade_pojo(FacadeState state) {
        return state.jsonpFacade.writeNodeAsString(USER_PLAIN);
    }

    @Benchmark
    public Object json_jsonp_facade_map(FacadeState state) {
        return state.jsonpFacade.writeNodeAsString(MAP_NODE);
    }

    @Benchmark
    public Object json_jsonp_facade_jojo(FacadeState state) {
        return state.jsonpFacade.writeNodeAsString(USER_JOJO);
    }


    @Getter @Setter
    public static class User {
        public String name;
        public List<User> friends;
        @JsonAnySetter @JsonAnyGetter
        public Map<String, Object> ext = new LinkedHashMap<>();
    }

    @Getter @Setter
    public static class UserPlain {
        public String name;
        public List<UserPlain> friends;
    }

    @Getter @Setter
    public static class User2 extends JsonObject {
        public String name;
        public List<User2> friends;
    }
}
