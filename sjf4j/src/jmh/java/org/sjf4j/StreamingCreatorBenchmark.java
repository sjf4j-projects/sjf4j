package org.sjf4j;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 6, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
public class StreamingCreatorBenchmark {

    private static final String JSON3 = "{\"a1\":\"x\",\"a2\":\"y\",\"a3\":\"z\"}";
    private static final String JSON6 = "{\"a1\":\"x\",\"a2\":\"y\",\"a3\":\"z\",\"a4\":\"u\",\"a5\":\"v\",\"a6\":\"w\"}";

    private static final ObjectMapper JACKSON2 = new ObjectMapper();
    private static final Gson GSON = new GsonBuilder().create();
    private static final JSONReader.Context FASTJSON2_NATIVE_CONTEXT = JSONFactory.createReadContext();

    static {
        FASTJSON2_NATIVE_CONTEXT.config(JSONReader.Feature.UseDoubleForDecimals);
    }

    @State(Scope.Thread)
    public static class FacadeState {
        @Param({"SHARED_IO", "PLUGIN_MODULE"})
        public String streamingMode;

        public Jackson2JsonFacade jackson2;
        public GsonJsonFacade gson;
        public Fastjson2JsonFacade fastjson2;

        @Setup(Level.Trial)
        public void setup() {
            StreamingFacade.StreamingMode mode = StreamingFacade.StreamingMode.valueOf(streamingMode);
            jackson2 = new Jackson2JsonFacade(new ObjectMapper(), mode);
            gson = new GsonJsonFacade(new GsonBuilder(), mode);
            fastjson2 = new Fastjson2JsonFacade(mode);
        }
    }

    public static class Ctor3 {
        public final Object a1;
        public final Object a2;
        public final Object a3;

        @NodeCreator
        @JsonCreator
        public Ctor3(@NodeProperty("a1") @JsonProperty("a1") Object a1,
                     @NodeProperty("a2") @JsonProperty("a2") Object a2,
                     @NodeProperty("a3") @JsonProperty("a3") Object a3) {
            this.a1 = a1;
            this.a2 = a2;
            this.a3 = a3;
        }
    }

    public static class Ctor6 {
        public final Object a1;
        public final Object a2;
        public final Object a3;
        public final Object a4;
        public final Object a5;
        public final Object a6;

        @NodeCreator
        @JsonCreator
        public Ctor6(@NodeProperty("a1") @JsonProperty("a1") Object a1,
                     @NodeProperty("a2") @JsonProperty("a2") Object a2,
                     @NodeProperty("a3") @JsonProperty("a3") Object a3,
                     @NodeProperty("a4") @JsonProperty("a4") Object a4,
                     @NodeProperty("a5") @JsonProperty("a5") Object a5,
                     @NodeProperty("a6") @JsonProperty("a6") Object a6) {
            this.a1 = a1;
            this.a2 = a2;
            this.a3 = a3;
            this.a4 = a4;
            this.a5 = a5;
            this.a6 = a6;
        }
    }

    @Benchmark
    public Object streaming_jackson2_ctor3(FacadeState s) {
        return s.jackson2.readNode(JSON3, Ctor3.class);
    }

    @Benchmark
    public Object streaming_jackson2_native_ctor3() throws Exception {
        return JACKSON2.readValue(JSON3, Ctor3.class);
    }

    @Benchmark
    public Object streaming_jackson2_ctor6(FacadeState s) {
        return s.jackson2.readNode(JSON6, Ctor6.class);
    }

    @Benchmark
    public Object streaming_jackson2_native_ctor6() throws Exception {
        return JACKSON2.readValue(JSON6, Ctor6.class);
    }

    @Benchmark
    public Object streaming_gson_ctor3(FacadeState s) {
        return s.gson.readNode(JSON3, Ctor3.class);
    }

    @Benchmark
    public Object streaming_gson_native_ctor3() {
        return GSON.fromJson(JSON3, Ctor3.class);
    }

    @Benchmark
    public Object streaming_gson_ctor6(FacadeState s) {
        return s.gson.readNode(JSON6, Ctor6.class);
    }

    @Benchmark
    public Object streaming_gson_native_ctor6() {
        return GSON.fromJson(JSON6, Ctor6.class);
    }

    @Benchmark
    public Object streaming_fastjson2_ctor3(FacadeState s) {
        return s.fastjson2.readNode(JSON3, Ctor3.class);
    }

    @Benchmark
    public Object streaming_fastjson2_native_ctor3() {
        return JSON.parseObject(JSON3, Ctor3.class);
    }

    @Benchmark
    public Object streaming_fastjson2_native_reader_ctor3() {
        try (JSONReader reader = JSONReader.of(JSON3, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(Ctor3.class);
        }
    }

    @Benchmark
    public Object streaming_fastjson2_ctor6(FacadeState s) {
        return s.fastjson2.readNode(JSON6, Ctor6.class);
    }

    @Benchmark
    public Object streaming_fastjson2_native_ctor6() {
        return JSON.parseObject(JSON6, Ctor6.class);
    }

    @Benchmark
    public Object streaming_fastjson2_native_reader_ctor6() {
        try (JSONReader reader = JSONReader.of(JSON6, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(Ctor6.class);
        }
    }
}
