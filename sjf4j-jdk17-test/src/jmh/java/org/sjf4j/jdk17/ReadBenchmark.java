package org.sjf4j.jdk17;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
import org.sjf4j.facade.jackson.JacksonJsonFacade;
import org.sjf4j.facade.jackson3.Jackson3JsonFacade;

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
        org.openjdk.jmh.Main.main(new String[]{ReadBenchmark.class.getName()});
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

    @State(Scope.Thread)
    public static class FacadeState {
        @Param({"SHARED_IO", "PLUGIN_MODULE"})
        public String streamingMode;

        public com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper;
        public tools.jackson.databind.ObjectMapper jackson3Mapper;
        public JacksonJsonFacade jackson2Facade;
        public Jackson3JsonFacade jackson3Facade;

        @Setup(Level.Trial)
        public void setup() {
            StreamingFacade.StreamingMode mode = StreamingFacade.StreamingMode.valueOf(streamingMode);

            jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            jackson2Mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            jackson3Mapper = tools.jackson.databind.json.JsonMapper.builderWithJackson2Defaults()
                    .configure(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .build();

            jackson2Facade = new JacksonJsonFacade(jackson2Mapper, mode);
            jackson3Facade = new Jackson3JsonFacade(jackson3Mapper, mode);
        }
    }

    @Benchmark
    public Object json_jackson2_native_pojo(FacadeState state) throws Exception {
        return state.jackson2Mapper.readValue(JSON_DATA2, UserPlain.class);
    }

    @Benchmark
    public Object json_jackson2_facade_pojo(FacadeState state) {
        return state.jackson2Facade.readNode(JSON_DATA2, UserPlain.class);
    }

    @Benchmark
    public Object json_jackson2_native_map(FacadeState state) throws Exception {
        return state.jackson2Mapper.readValue(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jackson2_facade_map(FacadeState state) {
        return state.jackson2Facade.readNode(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jackson2_facade_jojo(FacadeState state) {
        return state.jackson2Facade.readNode(JSON_DATA2, User2.class);
    }

    @Benchmark
    public Object json_jackson3_native_pojo(FacadeState state) throws Exception {
        return state.jackson3Mapper.readValue(JSON_DATA2, UserPlain.class);
    }

    @Benchmark
    public Object json_jackson3_facade_pojo(FacadeState state) {
        return state.jackson3Facade.readNode(JSON_DATA2, UserPlain.class);
    }

    @Benchmark
    public Object json_jackson3_native_map(FacadeState state) throws Exception {
        return state.jackson3Mapper.readValue(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jackson3_facade_map(FacadeState state) {
        return state.jackson3Facade.readNode(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jackson3_facade_jojo(FacadeState state) {
        return state.jackson3Facade.readNode(JSON_DATA2, User2.class);
    }

    static class UserPlain {
        String name;
        List<UserPlain> friends;
    }

    static class User2 extends JsonObject {
        String name;
        List<User2> friends;
    }
}
