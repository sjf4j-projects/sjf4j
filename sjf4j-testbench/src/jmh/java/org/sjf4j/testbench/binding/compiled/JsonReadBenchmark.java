package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.json.Json;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.BindingBackend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.facade.gson.GsonModule;
import org.sjf4j.facade.jsonp.JsonpJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.node.ReflectUtil;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class JsonReadBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{JsonReadBenchmark.class.getName()});
//        Main.main(new String[]{"ReadBenchmark.json_fastjson2", "ReadBenchmark.json_jackson2"});
    }

    private static final String JSON_DATA2 = "{\n" +
            "  \"id\": 839201, \"createdAt\": 1700000000123, \"updatedAt\": 1701234567890,\n" +
            "  \"reputation\": 9876543210, \"loginCount\": 421, \"age\": 34,\n" +
            "  \"active\": true, \"verified\": true, \"admin\": false, \"suspended\": false,\n" +
            "  \"score\": 98.75, \"latitude\": 37.7749, \"longitude\": -122.4194,\n" +
            "  \"username\": \"alice.builder\", \"email\": null,\n" +
            "  \"displayName\": \"Alice \\\"the Builder\\\" n Line\", \"passwordHash\": \"$2a$12$abcdef\",\n" +
            "  \"bio\": \"Builds fast JSON with control chars\", \"website\": \"https://example.test/a?b=1\",\n" +
            "  \"department\": \"Platform Engineering\",\n" +
            "  \"address\": {\"street\": \"1 Main St\", \"city\": \"San Francisco\", \"state\": \"CA\", \"zip\": \"94105\", \"country\": \"US\"},\n" +
            "  \"tags\": [\"tag-0-value\", null],\n" +
            "  \"friends\": [\n" +
            "    {\"id\": 1000, \"name\": \"BillBackslash\", \"since\": 1600000000000, \"close\": true},\n" +
            "    null,\n" +
            "    {\"id\": 1001, \"name\": \"Cindy Line\", \"since\": 1600000000001, \"close\": false}\n" +
            "  ]\n" +
            "}\n";

    private static final ObjectMapper JACKSON2 = new ObjectMapper();
    private static final ObjectMapper JACKSON2_BLACKBIRD = createBlackbirdJackson2();
    private static final Gson GSON = createNativeGson();
    private static final JSONReader.Context FASTJSON2_NATIVE_CONTEXT = createFastjson2NativeContext();

    private static final Jackson2Binder JACKSON2_BINDER = new Jackson2Binder(new JsonFactory());
    private static final GsonBinder GSON_BINDER = new GsonBinder(new Gson());
    private static final Fastjson2Binder FASTJSON2_BINDER = new Fastjson2Binder();
    private static final JsonpBinder JSONP_BINDER = new JsonpBinder();
    private static final SimpleJsonBinder SIMPLE_BINDER = new SimpleJsonBinder();

    private static final Jackson2CompiledBinder JACKSON2_COMPILED = CompiledInstances.of(Jackson2CompiledBinder.class);
    private static final GsonCompiledBinder GSON_COMPILED = CompiledInstances.of(GsonCompiledBinder.class);
    private static final Fastjson2CompiledBinder FASTJSON2_COMPILED = CompiledInstances.of(Fastjson2CompiledBinder.class);
    private static final JsonpCompiledBinder JSONP_COMPILED = CompiledInstances.of(JsonpCompiledBinder.class);
    private static final SimpleCompiledBinder SIMPLE_COMPILED = CompiledInstances.of(SimpleCompiledBinder.class);

    static {
        JACKSON2.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static ObjectMapper createBlackbirdJackson2() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new BlackbirdModule());
        return mapper;
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


    // ----- Jackson2 baselines -----
    @Benchmark
    public Object json_jackson2_pojo_native() throws IOException {
        return JACKSON2.readValue(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_jackson2_pojo_blackbird() throws IOException {
        return JACKSON2_BLACKBIRD.readValue(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_jackson2_map_native() throws IOException {
        return JACKSON2.readValue(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jackson2_pojo_runtime() throws IOException {
        return JACKSON2_BINDER.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_jackson2_map_runtime() throws IOException {
        return JACKSON2_BINDER.readNode(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jackson2_pojo_compiled() throws IOException {
        return JACKSON2_COMPILED.readPojo(JSON_DATA2);
    }

//    @Benchmark
//    public Object json_jackson2_jojo_compiled() throws IOException {
//        return JACKSON2_BINDER.readJojo(JSON_DATA2);
//    }

    @Benchmark
    public Object json_jackson2_map_compiled() throws IOException {
        return JACKSON2_COMPILED.readMap(JSON_DATA2);
    }

    @CompiledBinder(backend = BindingBackend.JACKSON2)
    public static interface Jackson2CompiledBinder {

        @ReadFrom
        User readPojo(String json) throws IOException;

//        @ReadFrom
//        UserJojo readJojo(String json) throws IOException;

        @ReadFrom
        Map<String, Object> readMap(String json) throws IOException;
    }



    // ----- Gson baselines -----

    @Benchmark
    public Object json_gson_pojo_native() {
        return GSON.fromJson(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_gson_map_native() {
        return GSON.fromJson(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_gson_pojo_runtime() throws IOException {
        return GSON_BINDER.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_gson_map_runtime() throws IOException {
        return GSON_BINDER.readNode(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_gson_pojo_compiled() throws IOException {
        return GSON_COMPILED.readPojo(JSON_DATA2);
    }

//    @Benchmark
//    public Object json_gson_jojo_compiled() throws IOException {
//        return GSON_BINDER.readJojo(JSON_DATA2);
//    }

    @Benchmark
    public Object json_gson_map_compiled() throws IOException {
        return GSON_COMPILED.readMap(JSON_DATA2);
    }

    @CompiledBinder(backend = BindingBackend.GSON)
    public static interface GsonCompiledBinder {

        @ReadFrom
        User readPojo(String json) throws IOException;

//        @ReadFrom
//        UserJojo readJojo(String json) throws IOException;

        @ReadFrom
        Map<String, Object> readMap(String json) throws IOException;
    }


    // ----- Fastjson2 baselines -----
    @Benchmark
    public Object json_fastjson2_pojo_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(User.class);
        }
    }

    @Benchmark
    public Object json_fastjson2_map_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(Map.class);
        }
    }

    @Benchmark
    public Object json_fastjson2_pojo_runtime() throws IOException {
        return FASTJSON2_BINDER.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_fastjson2_map_runtime() throws IOException {
        return FASTJSON2_BINDER.readNode(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_fastjson2_pojo_compiled() throws IOException {
        return FASTJSON2_COMPILED.readPojo(JSON_DATA2);
    }

//    @Benchmark
//    public Object json_fastjson2_jojo_compiled() throws IOException {
//        return FASTJSON2_BINDER.readJojo(JSON_DATA2);
//    }

    @Benchmark
    public Object json_fastjson2_map_compiled() throws IOException {
        return FASTJSON2_COMPILED.readMap(JSON_DATA2);
    }


    @CompiledBinder(backend = BindingBackend.FASTJSON2)
    public static interface Fastjson2CompiledBinder {

        @ReadFrom
        User readPojo(String json) throws IOException;

//        @ReadFrom
//        UserJojo readJojo(String json) throws IOException;

        @ReadFrom
        Map<String, Object> readMap(String json) throws IOException;
    }


    // ----- JSON-P baselines -----
    @Benchmark
    public Object json_jsonp_map_native() {
        return Json.createReader(new StringReader(JSON_DATA2)).read();
    }

    @Benchmark
    public Object json_jsonp_pojo_runtime() throws IOException {
        return JSONP_BINDER.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_jsonp_map_runtime() throws IOException {
        return JSONP_BINDER.readNode(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jsonp_pojo_compiled() throws IOException {
        return JSONP_COMPILED.readPojo(JSON_DATA2);
    }

//    @Benchmark
//    public Object json_jsonp_jojo_compiled() throws IOException {
//        return JSONP_BINDER.readJojo(JSON_DATA2);
//    }

    @Benchmark
    public Object json_jsonp_map_compiled() throws IOException {
        return JSONP_COMPILED.readMap(JSON_DATA2);
    }

    @CompiledBinder(backend = BindingBackend.JSONP)
    public static interface JsonpCompiledBinder {

        @ReadFrom
        User readPojo(String json) throws IOException;

//        @ReadFrom
//        UserJojo readJojo(String json) throws IOException;

        @ReadFrom
        Map<String, Object> readMap(String json) throws IOException;
    }


    // ----- Simple JSON baselines -----

    @Benchmark
    public Object json_simple_pojo_runtime() throws IOException {
        return SIMPLE_BINDER.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_simple_map_runtime() throws IOException {
        return SIMPLE_BINDER.readNode(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_simple_pojo_compiled() throws IOException {
        return SIMPLE_COMPILED.readPojo(JSON_DATA2);
    }

//    @Benchmark
//    public Object json_simple_jojo_compiled() throws IOException {
//        return SIMPLE_BINDER.readJojo(JSON_DATA2);
//    }

    @Benchmark
    public Object json_simple_map_compiled() throws IOException {
        return SIMPLE_COMPILED.readMap(JSON_DATA2);
    }


    @CompiledBinder(backend = BindingBackend.SIMPLE)
    public static interface SimpleCompiledBinder {

        @ReadFrom
        User readPojo(String json) throws IOException;

//        @ReadFrom
//        UserJojo readJojo(String json) throws IOException;

        @ReadFrom
        Map<String, Object> readMap(String json) throws IOException;
    }

}
