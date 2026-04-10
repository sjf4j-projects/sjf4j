package org.sjf4j.jdk17;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.json.Json;
import org.openjdk.jmh.Main;
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
import org.sjf4j.JsonObject;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.gson.GsonModule;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.jackson3.Jackson3JsonFacade;
import org.sjf4j.facade.jsonp.JsonpJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.node.ReflectUtil;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 8, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class ReadBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{ReadBenchmark.class.getName()});
    }

    private static final String JSON_DATA2 = "{\n"
            + "  \"name\": \"Alice\",\n"
            + "  \"friends\": [\n"
            + "    {\"name\": \"Bill\", \"active\": true, \"score\": 88.5 },\n"
            + "    {\"name\": \"Eve\", \"active\": false, \"tags\": [\"x\",\"y\"] },\n"
            + "    {\n"
            + "      \"name\": \"Cindy\",\n"
            + "      \"friends\": [\n"
            + "        {\"name\": \"David\"},\n"
            + "        {\"id\": 5, \"info\": \"blabla\"},\n"
            + "        {\"name\": \"Frank\", \"friends\": [{\"name\": \"Gina\"}, {\"name\": \"Hank\"}]},\n"
            + "        {\"name\": \"Ivy\", \"meta\": {\"a\":1,\"b\":2}},\n"
            + "        {}\n"
            + "      ]\n"
            + "    },\n"
            + "    {\"name\": \"Jane\"},\n"
            + "    {\"name\": \"Kyle\", \"friends\": [{\"name\": \"Liam\"}, {\"name\": \"Mia\"}]},\n"
            + "    {\"name\": \"Nina\", \"age\": 19, \"city\": \"SG\"}\n"
            + "  ],\n"
            + "  \"age\": 18,\n"
            + "  \"city\": \"Singapore\",\n"
            + "  \"ext\": {\"k1\": 1, \"k2\": true, \"k3\": [1,2,3]},\n"
            + "  \"extra1\": 12345,\n"
            + "  \"extra2\": \"hello\",\n"
            + "  \"extra3\": {\"nested\": {\"x\": 1, \"y\": [1,2,3,4]}}\n"
            + "}\n";

    private static final ObjectMapper JACKSON2 = createJackson2();
    private static final JsonMapper JACKSON3 = createJackson3();
    private static final Gson GSON = createNativeGson();
    private static final JSONReader.Context FASTJSON2_NATIVE_CONTEXT = createFastjson2NativeContext();
    private static final SimpleJsonFacade SIMPLE_JSON_FACADE = new SimpleJsonFacade();
    private static final JsonpJsonFacade JSONP_JSON_FACADE = new JsonpJsonFacade();

    private static ObjectMapper createJackson2() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private static JsonMapper createJackson3() {
        return JsonMapper.builderWithJackson2Defaults()
                .configure(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    private static Gson createNativeGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
        builder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
        builder.setFieldNamingStrategy(field -> {
            String name = ReflectUtil.getExplicitName(field);
            return name != null ? name : field.getName();
        });
        return builder.create();
    }

    private static JSONReader.Context createFastjson2NativeContext() {
        ObjectReaderProvider provider = new ObjectReaderProvider();
        return JSONFactory.createReadContext(provider, JSONReader.Feature.UseDoubleForDecimals);
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
    public static class FacadeState2 {
        @Param({"SHARED_IO", "PLUGIN_MODULE"})
        public String streamingMode;

        public GsonJsonFacade gsonFacade;
        public Jackson3JsonFacade jackson3Facade;

        @Setup(Level.Trial)
        public void setup() {
            StreamingFacade.StreamingMode mode = StreamingFacade.StreamingMode.valueOf(streamingMode);
            jackson3Facade = new Jackson3JsonFacade(createJackson3(), mode);
            gsonFacade = new GsonJsonFacade(new GsonBuilder(), mode);
        }
    }

    // Jackson2
    @Benchmark
    public Object json_jackson2_pojo_native() throws Exception {
        return JACKSON2.readValue(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_jackson2_jojo_native() throws Exception {
        return JACKSON2.readValue(JSON_DATA2, UserHasAny.class);
    }

    @Benchmark
    public Object json_jackson2_map_native() throws Exception {
        return JACKSON2.readValue(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jackson2_pojo_facade(FacadeState state) {
        return state.jackson2Facade.readNode(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_jackson2_jojo_facade(FacadeState state) {
        return state.jackson2Facade.readNode(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_jackson2_map_facade(FacadeState state) {
        return state.jackson2Facade.readNode(JSON_DATA2, Map.class);
    }

    // Jackson3
    @Benchmark
    public Object json_jackson3_pojo_native() throws Exception {
        return JACKSON3.readValue(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_jackson3_jojo_native() throws Exception {
        return JACKSON3.readValue(JSON_DATA2, UserHasAny.class);
    }

    @Benchmark
    public Object json_jackson3_map_native() throws Exception {
        return JACKSON3.readValue(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jackson3_pojo_facade(FacadeState2 state) {
        return state.jackson3Facade.readNode(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_jackson3_jojo_facade(FacadeState2 state) {
        return state.jackson3Facade.readNode(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_jackson3_map_facade(FacadeState2 state) {
        return state.jackson3Facade.readNode(JSON_DATA2, Map.class);
    }

    // Gson
    @Benchmark
    public Object json_gson_pojo_native() {
        return GSON.fromJson(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_gson_map_native() {
        return GSON.fromJson(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_gson_jojo_facade(FacadeState2 state) {
        return state.gsonFacade.readNode(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_gson_pojo_facade(FacadeState2 state) {
        return state.gsonFacade.readNode(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_gson_map_facade(FacadeState2 state) {
        return state.gsonFacade.readNode(JSON_DATA2, Map.class);
    }


    // Fastjson2
    @Benchmark
    public Object json_fastjson2_pojo_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(UserPojo.class);
        }
    }

    @Benchmark
    public Object json_fastjson2_jojo_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(UserHasAny.class);
        }
    }

    @Benchmark
    public Object json_fastjson2_map_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(Map.class);
        }
    }

    @Benchmark
    public Object json_fastjson2_pojo_facade(FacadeState state) throws IOException {
        return state.fastjson2Facade.readNode(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_fastjson2_jojo_facade(FacadeState state) throws IOException {
        return state.fastjson2Facade.readNode(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_fastjson2_map_facade(FacadeState state) throws IOException {
        return state.fastjson2Facade.readNode(JSON_DATA2, Map.class);
    }

    // JSONP
    @Benchmark
    public Object json_jsonp_map_native() {
        return Json.createReader(new StringReader(JSON_DATA2)).read();
    }

    @Benchmark
    public Object json_jsonp_pojo_facade() {
        return JSONP_JSON_FACADE.readNode(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_jsonp_map_facade() {
        return JSONP_JSON_FACADE.readNode(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jsonp_jojo_facade() {
        return JSONP_JSON_FACADE.readNode(JSON_DATA2, UserJojo.class);
    }


    // Define a POJO `User`
    static class UserPojo {
        public String name;
        public List<UserPojo> friends;

    }

    // Define a JOJO `JojoUser`
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
