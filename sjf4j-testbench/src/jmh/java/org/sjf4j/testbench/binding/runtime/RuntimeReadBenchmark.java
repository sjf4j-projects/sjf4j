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
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.node.ReflectUtil;
import org.sjf4j.testbench.model.User;
import org.sjf4j.testbench.model.UserJojo;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class RuntimeReadBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{RuntimeReadBenchmark.class.getName()});
//        Main.main(new String[]{"ReadBenchmark.json_fastjson2", "ReadBenchmark.json_jackson2"});
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
    private static final ObjectMapper JACKSON2_BLACKBIRD = createBlackbirdJackson2();
    private static final Gson GSON = createNativeGson();
    private static final JSONReader.Context FASTJSON2_NATIVE_CONTEXT = createFastjson2NativeContext();
    private static final Jackson2Binder JACKSON2_BINDER = new Jackson2Binder();
    private static final GsonBinder GSON_BINDER = new GsonBinder();
    private static final Fastjson2Binder FASTJSON2_BINDER = new Fastjson2Binder();
    private static final SimpleJsonBinder SIMPLE_JSON_BINDER = new SimpleJsonBinder();
    private static final JsonpBinder JSONP_BINDER = new JsonpBinder();

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


    // ----- Pure parser baselines (no binding, no materialized result) -----
    @Benchmark
    public void parse_jackson2_native(Blackhole bh) throws IOException {
        try (com.fasterxml.jackson.core.JsonParser parser = JACKSON2.getFactory().createParser(JSON_DATA2)) {
            com.fasterxml.jackson.core.JsonToken token;
            while ((token = parser.nextToken()) != null) {
                bh.consume(token);
                if (token == com.fasterxml.jackson.core.JsonToken.FIELD_NAME || token.isScalarValue()) {
                    bh.consume(parser.getText());
                }
            }
        }
    }

    @Benchmark
    public void parse_jsonp_native(Blackhole bh) {
        try (jakarta.json.stream.JsonParser parser = Json.createParser(new StringReader(JSON_DATA2))) {
            while (parser.hasNext()) {
                jakarta.json.stream.JsonParser.Event event = parser.next();
                bh.consume(event);
                switch (event) {
                    case KEY_NAME:
                    case VALUE_STRING:
                    case VALUE_NUMBER:
                        bh.consume(parser.getString());
                        break;
                    default:
                        break;
                }
            }
        }
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
    public Object json_jackson2_jojo_native() throws IOException {
        return JACKSON2.readValue(JSON_DATA2, UserJojo.class);
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


    // ----- Fastjson2 baselines -----
    @Benchmark
    public Object json_fastjson2_pojo_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(User.class);
        }
    }

    @Benchmark
    public Object json_fastjson2_jojo_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(UserJojo.class);
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

    // ----- JSON-P baselines -----
    @Benchmark
    public Object json_jsonp_map_native() {
        return Json.createReader(new StringReader(JSON_DATA2)).read();
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
