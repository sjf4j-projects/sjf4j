package org.sjf4j.testbench.binding;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
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
import org.openjdk.jmh.infra.Blackhole;
import org.sjf4j.binding.FastStringReader;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.gson.GsonModule;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.jackson2.Jackson2Module;
import org.sjf4j.facade.jsonp.JsonpJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonReader;
import org.sjf4j.node.ReflectUtil;
import org.sjf4j.testbench.model.User;
import org.sjf4j.testbench.model.UserJojo;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class JsonReadBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{JsonReadBenchmark.class.getName()});
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
    private static final SimpleJsonFacade SIMPLE_JSON_FACADE = new SimpleJsonFacade();
    private static final JsonpJsonFacade JSONP_JSON_FACADE = new JsonpJsonFacade();

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

    private static ObjectMapper createJackson2PluginMapper(SimpleModule module) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        AnnotationIntrospector serializationAi = objectMapper.getSerializationConfig().getAnnotationIntrospector();
        AnnotationIntrospector deserializationAi = objectMapper.getDeserializationConfig().getAnnotationIntrospector();
        objectMapper.setAnnotationIntrospectors(
                AnnotationIntrospectorPair.create(new Jackson2Module.NodePropertyAnnotationIntrospector(), serializationAi),
                AnnotationIntrospectorPair.create(new Jackson2Module.NodePropertyAnnotationIntrospector(), deserializationAi)
        );
        objectMapper.registerModule(module);
        return objectMapper;
    }

    @State(Scope.Thread)
    public static class FacadeState {
        @Param({"SHARED_IO", "EXCLUSIVE_IO", "PLUGIN_MODULE"})
        public String streamingMode;

        public Jackson2JsonFacade jackson2Facade;
        public Fastjson2JsonFacade fastjson2Facade;

        @Setup(Level.Trial)
        public void setup() {
            StreamingContext.StreamingMode mode = StreamingContext.StreamingMode.valueOf(streamingMode);
            StreamingContext context = new StreamingContext(mode);
            jackson2Facade = new Jackson2JsonFacade(new ObjectMapper(), context);
            fastjson2Facade = new Fastjson2JsonFacade(null, null, context);
        }
    }

    @State(Scope.Thread)
    public static class FacadeState2 {
        @Param({"SHARED_IO", "PLUGIN_MODULE"})
        public String streamingMode;

        public GsonJsonFacade gsonFacade;

        @Setup(Level.Trial)
        public void setup() {
            StreamingContext.StreamingMode mode = StreamingContext.StreamingMode.valueOf(streamingMode);
            StreamingContext context = new StreamingContext(mode);
            gsonFacade = new GsonJsonFacade(new GsonBuilder(), context);
        }
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
    public void parse_gson_native(Blackhole bh) throws IOException {
        try (com.google.gson.stream.JsonReader reader = GSON.newJsonReader(new StringReader(JSON_DATA2))) {
            traverseGson(reader, bh);
        }
    }

    @Benchmark
    public void parse_fastjson2_native(Blackhole bh) {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            traverseFastjson2(reader, bh);
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

    @Benchmark
    public void parse_simple_native(Blackhole bh) throws IOException {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(JSON_DATA2))) {
            traverseStreamingReader(reader, bh);
        }
    }

    @Benchmark
    public void parse_simple_fast_string_reader(Blackhole bh) throws IOException {
        try (SimpleJsonReader reader = new SimpleJsonReader(new FastStringReader(JSON_DATA2))) {
            traverseStreamingReader(reader, bh);
        }
    }

    private static void traverseGson(com.google.gson.stream.JsonReader reader, Blackhole bh) throws IOException {
        com.google.gson.stream.JsonToken token = reader.peek();
        bh.consume(token);
        switch (token) {
            case BEGIN_OBJECT:
                reader.beginObject();
                while (reader.hasNext()) {
                    bh.consume(reader.nextName());
                    traverseGson(reader, bh);
                }
                reader.endObject();
                return;
            case BEGIN_ARRAY:
                reader.beginArray();
                while (reader.hasNext()) {
                    traverseGson(reader, bh);
                }
                reader.endArray();
                return;
            case STRING:
            case NUMBER:
                bh.consume(reader.nextString());
                return;
            case BOOLEAN:
                bh.consume(reader.nextBoolean());
                return;
            case NULL:
                reader.nextNull();
                return;
            default:
                throw new IOException("Unexpected token: " + token);
        }
    }

    private static void traverseFastjson2(JSONReader reader, Blackhole bh) {
        char ch = reader.current();
        bh.consume(ch);
        if (reader.nextIfObjectStart()) {
            while (!reader.nextIfObjectEnd()) {
                bh.consume(reader.readFieldName());
                traverseFastjson2(reader, bh);
            }
        } else if (reader.nextIfArrayStart()) {
            while (!reader.nextIfArrayEnd()) {
                traverseFastjson2(reader, bh);
            }
        } else if (ch == '"') {
            bh.consume(reader.readString());
        } else if (ch == 't' || ch == 'f') {
            bh.consume(reader.readBoolValue());
        } else if (ch == 'n') {
            reader.readNull();
        } else {
            bh.consume(reader.readNumber());
        }
    }

    private static void traverseStreamingReader(StreamingReader reader, Blackhole bh) throws IOException {
        StreamingReader.Token token = reader.peekToken();
        bh.consume(token);
        switch (token) {
            case START_OBJECT:
                reader.startObject();
                while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
                    bh.consume(reader.nextName());
                    traverseStreamingReader(reader, bh);
                }
                reader.endObject();
                return;
            case START_ARRAY:
                reader.startArray();
                while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
                    traverseStreamingReader(reader, bh);
                }
                reader.endArray();
                return;
            case STRING:
                bh.consume(reader.nextString());
                return;
            case NUMBER:
                bh.consume(reader.nextNumber());
                return;
            case BOOLEAN:
                bh.consume(reader.nextBoolean());
                return;
            case NULL:
                reader.nextNull();
                return;
            default:
                throw new IOException("Unexpected token: " + token);
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
    public Object json_jackson2_pojo_facade(FacadeState state) throws IOException {
        return state.jackson2Facade.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_jackson2_jojo_facade(FacadeState state) throws IOException {
        return state.jackson2Facade.readNode(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_jackson2_map_facade(FacadeState state) throws IOException {
        return state.jackson2Facade.readNode(JSON_DATA2, Map.class);
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
    public Object json_gson_pojo_facade(FacadeState2 state) {
        return state.gsonFacade.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_gson_jojo_facade(FacadeState2 state) {
        return state.gsonFacade.readNode(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_gson_map_facade(FacadeState2 state) {
        return state.gsonFacade.readNode(JSON_DATA2, Map.class);
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
    public Object json_fastjson2_pojo_facade(FacadeState state) throws IOException {
        return state.fastjson2Facade.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_fastjson2_jojo_facade(FacadeState state) throws IOException {
        return state.fastjson2Facade.readNode(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_fastjson2_map_facade(FacadeState state) throws IOException {
        return state.fastjson2Facade.readNode(JSON_DATA2, Map.class);
    }

    // ----- JSON-P baselines -----
    @Benchmark
    public Object json_jsonp_map_native() {
        return Json.createReader(new StringReader(JSON_DATA2)).read();
    }

    @Benchmark
    public Object json_jsonp_pojo_facade() {
        return JSONP_JSON_FACADE.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_jsonp_map_facade() {
        return JSONP_JSON_FACADE.readNode(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jsonp_jojo_facade() {
        return JSONP_JSON_FACADE.readNode(JSON_DATA2, UserJojo.class);
    }

    // ----- Simple JSON baselines -----
    @Benchmark
    public Object json_simple_pojo_facade() throws IOException {
        return SIMPLE_JSON_FACADE.readNode(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_simple_jojo_facade() throws IOException {
        return SIMPLE_JSON_FACADE.readNode(JSON_DATA2, UserJojo.class);
    }
}
