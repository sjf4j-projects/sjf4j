package org.sjf4j.testbench.binding.runtime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
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
import org.sjf4j.JsonObject;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JOJO write paths under the exact same static/mixed/dynamic JSON fixture as
 * {@link JojoReadBenchmark}, but with distinct model shapes so that emitted
 * fields really are 16/0, 8/8, or 2/14 (declared/dynamic).
 *
 * <p>The JOJO and native-extra models preserve identical JSON semantics.
 * Plain POJO drops dynamic fields in mixed/dynamic scenarios; its timing is
 * a static-only baseline, not an equivalent full serialization.</p>
 *
 * <p>Fixtures and output checks are prepared in trial setup and never measured.</p>
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

    /** static: 16/0; mixed: 8/8; dynamic: 2/14 declared/dynamic output keys. */
    @Param({"static", "mixed", "dynamic"})
    public String workload;

    private JsonObject jojo;
    private Object pojo;
    private Object nativeExtra;

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{JojoWriteBenchmark.class.getName()});
    }

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        int staticCount;
        int dynamicCount;
        Class<? extends JsonObject> jojoClass;
        Class<?> pojoClass;
        Class<?> nativeClass;

        switch (workload) {
            case "static":
                staticCount = 16;
                dynamicCount = 0;
                jojoClass = JojoReadBenchmark.JojoModel.class;
                pojoClass = JojoReadBenchmark.PojoModel.class;
                nativeClass = JojoReadBenchmark.ExtraModel.class;
                break;
            case "mixed":
                staticCount = 8;
                dynamicCount = 8;
                jojoClass = MixedJojo.class;
                pojoClass = MixedPojo.class;
                nativeClass = MixedExtra.class;
                break;
            case "dynamic":
                staticCount = 2;
                dynamicCount = 14;
                jojoClass = DynamicJojo.class;
                pojoClass = DynamicPojo.class;
                nativeClass = DynamicExtra.class;
                break;
            default:
                throw new IllegalArgumentException("unsupported JOJO workload: " + workload);
        }

        String fixture = JojoReadBenchmark.buildJson(staticCount, dynamicCount);
        jojo = (JsonObject) JACKSON2_BINDER.readNode(fixture, jojoClass);
        pojo = JACKSON2_BINDER.readNode(fixture, pojoClass);
        nativeExtra = JACKSON2.readValue(fixture, nativeClass);

        // Validate logical key sets before timing. All three models have exactly
        // staticCount declared properties; no default-valued extras are emitted.
        if (pojoClass.getDeclaredFields().length != staticCount
                || jojoClass.getDeclaredFields().length != staticCount) {
            throw new IllegalStateException("invalid declared property count for " + workload);
        }

        Map<String, Object> extras = nativeExtras(nativeExtra);
        if (jojo.dynamicProperties().size() != dynamicCount
                || extras.size() != dynamicCount
                || !jojo.dynamicProperties().keySet().equals(extras.keySet())) {
            throw new IllegalStateException("unexpected dynamic key set for " + workload);
        }

        Map<?, ?> expected = parse(fixture);
        Map<?, ?> expectedPojo = parse(JACKSON2_BINDER.writeNodeAsString(pojo));
        if (expected.size() != staticCount + dynamicCount
                || expectedPojo.size() != staticCount) {
            throw new IllegalStateException("invalid field count for " + workload);
        }
        for (Map.Entry<?, ?> entry : expectedPojo.entrySet()) {
            if (!java.util.Objects.equals(expected.get(entry.getKey()), entry.getValue())) {
                throw new IllegalStateException("POJO field value mismatch: " + entry.getKey());
            }
        }

        verify("fastjson2 JOJO", expected, fastjson2_jojo_runtime());
        verify("fastjson2 native-extra", expected, fastjson2_jojo_native_extra());
        verify("jackson2 JOJO", expected, jackson2_jojo_runtime());
        verify("jackson2 native-extra", expected, jackson2_jojo_native_extra());
        verify("jackson3 JOJO", expected, jackson3_jojo_runtime());
        verify("jackson3 native-extra", expected, jackson3_jojo_native_extra());
        verify("simple JOJO", expected, simple_jojo_runtime());

        verify("fastjson2 POJO", expectedPojo, fastjson2_pojo_runtime());
        verify("jackson2 POJO", expectedPojo, jackson2_pojo_runtime());
        verify("jackson3 POJO", expectedPojo, jackson3_pojo_runtime());
        verify("simple POJO", expectedPojo, simple_pojo_runtime());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nativeExtras(Object nativeExtra) {
        try {
            Field extra = nativeExtra.getClass().getField("extra");
            return (Map<String, Object>) extra.get(nativeExtra);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("native extra map is unavailable", e);
        }
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

    @Benchmark
    public String simple_jojo_runtime() {
        return SIMPLE_BINDER.writeNodeAsString(jojo);
    }

    @Benchmark
    public String simple_pojo_runtime() {
        return SIMPLE_BINDER.writeNodeAsString(pojo);
    }

    // Separate declared shapes ensure the output actually has the intended
    // ratio of statically declared and dynamically stored properties.
    public static class MixedPojo {
        public long id;
        public long createdAt;
        public long updatedAt;
        public long reputation;
        public int loginCount;
        public int age;
        public boolean active;
        public boolean verified;
    }

    public static class MixedJojo extends JsonObject {
        public long id;
        public long createdAt;
        public long updatedAt;
        public long reputation;
        public int loginCount;
        public int age;
        public boolean active;
        public boolean verified;
    }

    public static class MixedExtra {
        public long id;
        public long createdAt;
        public long updatedAt;
        public long reputation;
        public int loginCount;
        public int age;
        public boolean active;
        public boolean verified;

        @JsonAnyGetter
        @JsonAnySetter
        @JSONField(unwrapped = true)
        public Map<String, Object> extra = new LinkedHashMap<>();
    }

    public static class DynamicPojo {
        public long id;
        public long createdAt;
    }

    public static class DynamicJojo extends JsonObject {
        public long id;
        public long createdAt;
    }

    public static class DynamicExtra {
        public long id;
        public long createdAt;

        @JsonAnyGetter
        @JsonAnySetter
        @JSONField(unwrapped = true)
        public Map<String, Object> extra = new LinkedHashMap<>();
    }
}
