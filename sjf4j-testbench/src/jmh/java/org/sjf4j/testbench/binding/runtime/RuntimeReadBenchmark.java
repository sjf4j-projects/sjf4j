package org.sjf4j.testbench.binding.runtime;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.json.Json;
import jakarta.json.JsonStructure;
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
import org.openjdk.jmh.infra.Blackhole;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.node.ReflectUtil;
import org.sjf4j.testbench.model.User;
import org.sjf4j.testbench.model.UserExtra;
import org.sjf4j.testbench.model.UserJojo;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 20, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class RuntimeReadBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{RuntimeReadBenchmark.class.getName()});
//        Main.main(new String[]{RuntimeReadBenchmark.class.getName() + ".*_jackson2"});
    }

//    private static final String JSON_DATA = "{\"name\":\"Alice\"}";
//    private static final String JSON_DATA = "{\"age\":25}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"city\":\"Singapore\"}}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"babies\":[{\"name\":\"Baby-0\",\"age\":1}]}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"}}";
    // Mixed structure JSON keeps nested objects/arrays so each framework covers the same workload.
    private static final String JSON_DATA = "{\"name\":\"Alice\",\"no_way\":99,\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";

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
    private static final ObjectMapper JACKSON2_BLACKBIRD = new ObjectMapper().registerModule(new BlackbirdModule());
    private static final Jackson2Binder JACKSON2_BINDER = new Jackson2Binder();

    private static final JsonMapper JACKSON3 = new JsonMapper();
    private static final JsonMapper JACKSON3_BLACKBIRD = JsonMapper.builder()
            .addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build();
    private static final Jackson3Binder JACKSON3_BINDER = new Jackson3Binder();

    private static final Gson GSON = new GsonBuilder().create();
    private static final GsonBinder GSON_BINDER = new GsonBinder();

    private static final JSONReader.Context FASTJSON2_NATIVE_CONTEXT =
            JSONFactory.createReadContext(new ObjectReaderProvider(), JSONReader.Feature.UseDoubleForDecimals);
    private static final Fastjson2Binder FASTJSON2_BINDER = new Fastjson2Binder();

    private static final JsonpBinder JSONP_BINDER = new JsonpBinder();

    private static final SimpleJsonBinder SIMPLE_JSON_BINDER = new SimpleJsonBinder();

    static {
        JACKSON2.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }


    private static JSONReader.Context createFastjson2NativeContext() {
        ObjectReaderProvider provider = new ObjectReaderProvider();
        return JSONFactory.createReadContext(provider, JSONReader.Feature.UseDoubleForDecimals);
    }


    // ----- Jackson2 baselines -----
    @Benchmark
    public Object pojo_jackson2_native() throws IOException {
        return JACKSON2.readValue(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object pojo_jackson2_blackbird() throws IOException {
        return JACKSON2_BLACKBIRD.readValue(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object map_jackson2_native() throws IOException {
        return JACKSON2.readValue(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object pojo_jackson2_runtime() throws IOException {
        return JACKSON2_BINDER.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object map_jackson2_runtime() throws IOException {
        return JACKSON2_BINDER.readNode(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object jojo_jackson2_native_extra() throws IOException {
        return JACKSON2.readValue(JSON_DATA2, UserExtra.class);
    }

    @Benchmark
    public Object jojo_jackson2_runtime() throws IOException {
        return JACKSON2_BINDER.readNode(JSON_DATA2, UserJojo.class);
    }


    // ----- Jackson3 baselines -----
    @Benchmark
    public Object pojo_jackson3_native() throws IOException {
        return JACKSON3.readValue(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object pojo_jackson3_blackbird() throws IOException {
        return JACKSON3_BLACKBIRD.readValue(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object map_jackson3_native() throws IOException {
        return JACKSON3.readValue(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object pojo_jackson3_runtime() throws IOException {
        return JACKSON3_BINDER.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object map_jackson3_runtime() throws IOException {
        return JACKSON3_BINDER.readNode(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object jojo_jackson3_native_extra() throws IOException {
        return JACKSON3.readValue(JSON_DATA2, UserExtra.class);
    }

    @Benchmark
    public Object jojo_jackson3_runtime() throws IOException {
        return JACKSON3_BINDER.readNode(JSON_DATA2, UserJojo.class);
    }


    // ----- Gson baselines -----
    @Benchmark
    public Object pojo_gson_native() {
        return GSON.fromJson(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object pojo_gson_runtime() throws IOException {
        return GSON_BINDER.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object map_gson_native() {
        return GSON.fromJson(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object map_gson_runtime() throws IOException {
        return GSON_BINDER.readNode(JSON_DATA2, Map.class);
    }


    // ----- Fastjson2 baselines -----
    @Benchmark
    public Object pojo_fastjson2_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(User.class);
        }
    }

    @Benchmark
    public Object pojo_fastjson2_runtime() throws IOException {
        return FASTJSON2_BINDER.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object jojo_fastjson2_native_extra() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(UserExtra.class);
        }
    }

    @Benchmark
    public Object jojo_fastjson2_runtime() throws IOException {
        return FASTJSON2_BINDER.readNode(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object map_fastjson2_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(Map.class);
        }
    }

    @Benchmark
    public Object map_fastjson2_runtime() throws IOException {
        return FASTJSON2_BINDER.readNode(JSON_DATA2, Map.class);
    }


    // ----- JSON-P baselines -----
    @Benchmark
    public Object tree_jsonp_native() {
        return Json.createReader(new StringReader(JSON_DATA2)).read();
    }

    @Benchmark
    public Object tree_jsonp_runtime() throws IOException {
        return JSONP_BINDER.readNode(JSON_DATA2, JsonStructure.class);
    }

    @Benchmark
    public Object map_jsonp_runtime() throws IOException {
        return JSONP_BINDER.readNode(JSON_DATA2, Map.class);
    }


    // ----- Simple JSON baselines -----
    @Benchmark
    public Object json_simple_pojo_facade() throws IOException {
        return SIMPLE_JSON_BINDER.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_simple_jojo_facade() throws IOException {
        return SIMPLE_JSON_BINDER.readNode(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_simple_map_facade() throws IOException {
        return SIMPLE_JSON_BINDER.readNode(JSON_DATA2, Map.class);
    }
}
