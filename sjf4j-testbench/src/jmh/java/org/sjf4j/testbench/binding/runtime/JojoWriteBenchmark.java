package org.sjf4j.testbench.binding.runtime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JOJO write paths under exactly the same static/mixed/dynamic JSON fixture as
 * {@link JojoReadBenchmark}. Object construction and JSON validation happen in
 * JMH trial setup, never in measured operations.
 *
 * <p>JOJO runtime and native-extra serialize identical logical objects and
 * retain every unknown property. Plain POJO drops unknown fields, so its
 * mixed/dynamic scores are only a static-property write baseline, NOT a
 * semantically equivalent comparison.</p>
 *
 * <p>Run {@code ./gradlew :sjf4j-testbench:jmhJar}, then
 * {@code java -jar sjf4j-testbench/build/libs/*-jmh.jar
 * '.*JojoWriteBenchmark.*' -prof gc}.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 20, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class JojoWriteBenchmark {

    private static final Jackson2Binder JACKSON2_BINDER = new Jackson2Binder();
    private static final Jackson3Binder JACKSON3_BINDER = new Jackson3Binder();
    private static final Fastjson2Binder FASTJSON2_BINDER = new Fastjson2Binder();
    private static final SimpleJsonBinder SIMPLE_BINDER = new SimpleJsonBinder();

    private static final ObjectMapper JACKSON2 = new ObjectMapper();
    private static final JsonMapper JACKSON3 = new JsonMapper();
    private static final JSONWriter.Context FASTJSON2_CONTEXT =
            JSONFactory.createWriteContext(JSONWriter.Feature.WriteNulls);

    /** 16/0, 8/8, or 2/14 declared/dynamic fields. */
    @Param({"static", "mixed", "dynamic"})
    public String workload;

    private JojoReadBenchmark.JojoModel jojo;
    private JojoReadBenchmark.PojoModel pojo;
    private JojoReadBenchmark.ExtraModel nativeExtra;

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{JojoWriteBenchmark.class.getName()});
    }

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        int staticCount;
        int dynamicCount;
        switch (workload) {
            case "static":
                staticCount = 16;
                dynamicCount = 0;
                break;
            case "mixed":
                staticCount = 8;
                dynamicCount = 8;
                break;
            case "dynamic":
                staticCount = 2;
                dynamicCount = 14;
                break;
            default:
                throw new IllegalArgumentException("unsupported JOJO workload: " + workload);
        }

        String fixture = JojoReadBenchmark.buildJson(staticCount, dynamicCount);
        jojo = (JojoReadBenchmark.JojoModel) JACKSON2_BINDER.readNode(
                fixture, JojoReadBenchmark.JojoModel.class);
        pojo = (JojoReadBenchmark.PojoModel) JACKSON2_BINDER.readNode(
                fixture, JojoReadBenchmark.PojoModel.class);
        nativeExtra = JACKSON2.readValue(fixture, JojoReadBenchmark.ExtraModel.class);

        if (jojo.dynamicProperties().size() != dynamicCount
                || nativeExtra.extra.size() != dynamicCount
                || !jojo.dynamicProperties().keySet().equals(nativeExtra.extra.keySet())) {
            throw new IllegalStateException("unexpected dynamic key set for " + workload);
        }

        // Every model declares all 16 typed fields, and serialization includes
        // unset fields' Java defaults. The input fixture contains only staticCount
        // typed properties, so comparing written output directly with the input
        // would incorrectly reject mixed/dynamic workloads.
        //
        // Use native-extra as the independent complete-object reference and
        // verify every input field still has its original value.
        Map<?, ?> expectedJojo = parse(JACKSON2.writeValueAsString(nativeExtra));
        Map<?, ?> expectedPojo = parse(JACKSON2_BINDER.writeNodeAsString(pojo));
        Map<?, ?> input = parse(fixture);
        int declaredCount = JojoReadBenchmark.PojoModel.class.getDeclaredFields().length;
        if (expectedJojo.size() != declaredCount + dynamicCount
                || expectedPojo.size() != declaredCount) {
            throw new IllegalStateException("invalid JOJO write field count for " + workload);
        }
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (!java.util.Objects.equals(expectedJojo.get(entry.getKey()), entry.getValue())) {
                throw new IllegalStateException("input field not preserved: " + entry.getKey());
            }
        }

        verify("fastjson2 JOJO", expectedJojo, fastjson2_jojo_runtime());
        verify("fastjson2 native-extra", expectedJojo, fastjson2_jojo_native_extra());
        verify("jackson2 JOJO", expectedJojo, jackson2_jojo_runtime());
        verify("jackson2 native-extra", expectedJojo, jackson2_jojo_native_extra());
        verify("jackson3 JOJO", expectedJojo, jackson3_jojo_runtime());
        verify("jackson3 native-extra", expectedJojo, jackson3_jojo_native_extra());
        verify("simple JOJO", expectedJojo, simple_jojo_runtime());

        verify("fastjson2 POJO", expectedPojo, fastjson2_pojo_runtime());
        verify("jackson2 POJO", expectedPojo, jackson2_pojo_runtime());
        verify("jackson3 POJO", expectedPojo, jackson3_pojo_runtime());
        verify("simple POJO", expectedPojo, simple_pojo_runtime());
    }

    private static Map<?, ?> parse(String json) throws IOException {
        return JACKSON2.readValue(json, Map.class);
    }

    private static void verify(String label, Map<?, ?> expected, String actual)
            throws IOException {
        Map<?, ?> parsed = parse(actual);
        if (!expected.equals(parsed)) {
            throw new IllegalStateException(label + " output mismatch: expected=" +
                    expected + ", actual=" + parsed);
        }
    }

    // ----- Fastjson2 -----

    @Benchmark
    public String fastjson2_jojo_runtime() {
        return FASTJSON2_BINDER.writeNodeAsString(jojo);
    }

    @Benchmark
    public String fastjson2_jojo_native_extra() {
        return JSON.toJSONString(nativeExtra, FASTJSON2_CONTEXT);
    }

    @Benchmark
    public String fastjson2_pojo_runtime() {
        return FASTJSON2_BINDER.writeNodeAsString(pojo);
    }

    // ----- Jackson 2 -----

    @Benchmark
    public String jackson2_jojo_runtime() {
        return JACKSON2_BINDER.writeNodeAsString(jojo);
    }

    @Benchmark
    public String jackson2_jojo_native_extra() throws IOException {
        return JACKSON2.writeValueAsString(nativeExtra);
    }

    @Benchmark
    public String jackson2_pojo_runtime() {
        return JACKSON2_BINDER.writeNodeAsString(pojo);
    }

    // ----- Jackson 3 -----

    @Benchmark
    public String jackson3_jojo_runtime() {
        return JACKSON3_BINDER.writeNodeAsString(jojo);
    }

    @Benchmark
    public String jackson3_jojo_native_extra() throws IOException {
        return JACKSON3.writeValueAsString(nativeExtra);
    }

    @Benchmark
    public String jackson3_pojo_runtime() {
        return JACKSON3_BINDER.writeNodeAsString(pojo);
    }

    // ----- Simple JSON (runtime only) -----

    @Benchmark
    public String simple_jojo_runtime() {
        return SIMPLE_BINDER.writeNodeAsString(jojo);
    }

    @Benchmark
    public String simple_pojo_runtime() {
        return SIMPLE_BINDER.writeNodeAsString(pojo);
    }
}
