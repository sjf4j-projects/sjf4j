package org.sjf4j.testbench.binding.runtime;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import jakarta.json.Json;
import jakarta.json.JsonObject;
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
import org.sjf4j.Sjf4j;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.TypeReference;
import org.sjf4j.testbench.model.User;
import org.sjf4j.testbench.model.UserJojo;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 8, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2)
@Threads(1)
@State(Scope.Thread)
public class RuntimeWriteBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{RuntimeWriteBenchmark.class.getName()});
//        Main.main(new String[]{"WriteBenchmark.json_fastjson2"});
    }

    // Mixed structure JSON keeps nested objects/arrays so each framework covers the same workload.
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
    private static final Gson GSON = new Gson();
    private static final JSONWriter.Context FASTJSON2_WRITER_CONTEXT =
            JSONFactory.createWriteContext(JSONWriter.Feature.WriteNulls);
    private static final SimpleJsonBinder SIMPLE_JSON_BINDER = new SimpleJsonBinder();
    private static final JsonpBinder JSONP_BINDER = new JsonpBinder();

    private static final User USER;
    private static final UserJojo USER_JOJO;
    private static final Map<String, Object> MAP_NODE;
    private static final JsonObject JSONP_MAP_NODE;

    static {
        try {
            USER = Sjf4j.global().fromJson(JSON_DATA2, User.class);
            USER_JOJO = Sjf4j.global().fromJson(JSON_DATA2, UserJojo.class);
            MAP_NODE = Sjf4j.global().fromJson(JSON_DATA2, new TypeReference<Map<String, Object>>() {});
            JSONP_MAP_NODE = Json.createReader(new StringReader(JSON_DATA2)).readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ----- Jackson2 baselines -----
    @Benchmark
    public Object json_jackson2_pojo_native() throws Exception {
        return JACKSON2.writeValueAsString(USER);
    }

    @Benchmark
    public Object json_jackson2_map_native() throws Exception {
        return JACKSON2.writeValueAsString(MAP_NODE);
    }


    // ----- Gson baselines -----
    @Benchmark
    public Object json_gson_pojo_native() {
        return GSON.toJson(USER);
    }

    @Benchmark
    public Object json_gson_map_native() {
        return GSON.toJson(MAP_NODE);
    }


    // ----- Fastjson2 baselines -----
    @Benchmark
    public Object json_fastjson2_pojo_native() {
        return JSON.toJSONString(USER, FASTJSON2_WRITER_CONTEXT);
    }

    @Benchmark
    public Object json_fastjson2_map_native() {
        return JSON.toJSONString(MAP_NODE, FASTJSON2_WRITER_CONTEXT);
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
        return JSONP_BINDER.writeNodeAsString(USER);
    }

    @Benchmark
    public Object json_jsonp_map_facade() {
        return JSONP_BINDER.writeNodeAsString(MAP_NODE);
    }

    @Benchmark
    public Object json_jsonp_jojo_facade() {
        return JSONP_BINDER.writeNodeAsString(USER_JOJO);
    }

    // ----- Simple JSON baselines -----
    @Benchmark
    public Object json_simple_pojo_facade() {
        return SIMPLE_JSON_BINDER.writeNodeAsString(USER);
    }

    @Benchmark
    public Object json_simple_jojo_facade() {
        return SIMPLE_JSON_BINDER.writeNodeAsString(USER_JOJO);
    }

    @Benchmark
    public Object json_simple_map_facade() {
        return SIMPLE_JSON_BINDER.writeNodeAsString(MAP_NODE);
    }

    @Benchmark
    public Object json_binding_simple_map_native() {
        return SIMPLE_JSON_BINDER.writeNodeAsString(MAP_NODE);
    }
}
