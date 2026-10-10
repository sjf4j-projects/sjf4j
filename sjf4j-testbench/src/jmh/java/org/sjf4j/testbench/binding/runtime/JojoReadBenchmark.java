package org.sjf4j.testbench.binding.runtime;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
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
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Measures the cost of JOJO's static-property fast path and its dynamic-property fallback.
 *
 * <p>Each workload uses the same JSON text for all implementations. POJO, JOJO,
 * and native-extra models declare the same 16 typed properties. The plain POJO
 * intentionally skips unknown properties, so it is a dispatch/skip baseline,
 * not a semantically equivalent alternative for dynamic-heavy input.</p>
 *
 * <p>The native-extra model eagerly allocates an extra map, matching the
 * existing {@link RuntimeReadBenchmark} baseline. In contrast, JOJO allocates
 * its dynamic map only if unknown properties occur.</p>
 *
 * <p>Build with {@code ./gradlew :sjf4j-testbench:jmhJar}, then use
 * the resulting {@code *-jmh.jar} to run {@code JojoReadBenchmark},
 * optionally passing {@code -p workload=mixed -prof gc}.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 20, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class JojoReadBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{JojoReadBenchmark.class.getName()});
    }

    private static final String[] STATIC_FIELDS = {
            "\"id\":839201",
            "\"createdAt\":1700000000123",
            "\"updatedAt\":1701234567890",
            "\"reputation\":9876543210",
            "\"loginCount\":421",
            "\"age\":34",
            "\"active\":true",
            "\"verified\":true",
            "\"admin\":false",
            "\"suspended\":false",
            "\"score\":98.75",
            "\"latitude\":37.7749",
            "\"longitude\":-122.4194",
            "\"username\":\"alice.builder\"",
            "\"email\":null",
            "\"department\":\"Platform Engineering\""
    };

    private static final String[] DYNAMIC_FIELDS = {
            "\"extra00\":123",
            "\"extra01\":\"alpha\"",
            "\"extra02\":true",
            "\"extra03\":null",
            "\"extra04\":{\"city\":\"Singapore\",\"zip\":null}",
            "\"extra05\":[1,2,null]",
            "\"extra06\":1.5",
            "\"extra07\":\"beta\"",
            "\"extra08\":false",
            "\"extra09\":-33",
            "\"extra10\":{\"nested\":{\"n\":2}}",
            "\"extra11\":[\"a\",\"b\"]",
            "\"extra12\":null",
            "\"extra13\":9999999999"
    };

    private static final Jackson2Binder JACKSON2_BINDER = new Jackson2Binder();
    private static final Jackson3Binder JACKSON3_BINDER = new Jackson3Binder();
    private static final Fastjson2Binder FASTJSON2_BINDER = new Fastjson2Binder();

    private static final ObjectMapper JACKSON2 = new ObjectMapper();
    private static final JsonMapper JACKSON3 = new JsonMapper();
    private static final JSONReader.Context FASTJSON2_NATIVE_CONTEXT =
            JSONFactory.createReadContext(new ObjectReaderProvider(), JSONReader.Feature.UseDoubleForDecimals);

    /** static: 16/0; mixed: 8/8; dynamic: 2/14 (declared/unknown field count). */
    @Param({"static", "mixed", "dynamic"})
    public String workload;

    private String json;

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

        // Interleave declared/unknown properties to exercise matcher re-entry.
        json = buildJson(staticCount, dynamicCount);

        // Verify that native-extra and JOJO really retain the same dynamic keys.
        // This runs once per trial, outside of the measured hot path.
        verify("jackson2",
                (PojoModel) JACKSON2_BINDER.readNode(json, PojoModel.class),
                (JojoModel) JACKSON2_BINDER.readNode(json, JojoModel.class),
                JACKSON2.readValue(json, ExtraModel.class), dynamicCount);
        verify("jackson3",
                (PojoModel) JACKSON3_BINDER.readNode(json, PojoModel.class),
                (JojoModel) JACKSON3_BINDER.readNode(json, JojoModel.class),
                JACKSON3.readValue(json, ExtraModel.class), dynamicCount);
        verify("fastjson2",
                (PojoModel) FASTJSON2_BINDER.readNode(json, PojoModel.class),
                (JojoModel) FASTJSON2_BINDER.readNode(json, JojoModel.class),
                readFastjson2Extra(), dynamicCount);
    }

    private static String buildJson(int staticCount, int dynamicCount) {
        StringBuilder out = new StringBuilder(512).append('{');
        int size = Math.max(staticCount, dynamicCount);
        boolean first = true;
        for (int i = 0; i < size; i++) {
            if (i < staticCount) {
                if (!first) out.append(',');
                out.append(STATIC_FIELDS[i]);
                first = false;
            }
            if (i < dynamicCount) {
                if (!first) out.append(',');
                out.append(DYNAMIC_FIELDS[i]);
                first = false;
            }
        }
        return out.append('}').toString();
    }

    private ExtraModel readFastjson2Extra() {
        try (JSONReader reader = JSONReader.of(json, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(ExtraModel.class);
        }
    }

    private static void verify(String backend, PojoModel pojo, JojoModel jojo,
                               ExtraModel extra, int dynamicCount) {
        if (pojo == null || jojo == null || extra == null
                || pojo.id != 839201L || jojo.id != pojo.id || extra.id != pojo.id
                || pojo.createdAt != 1700000000123L
                || jojo.createdAt != pojo.createdAt
                || extra.createdAt != pojo.createdAt) {
            throw new IllegalStateException(backend + ": static property binding mismatch");
        }
        Map<String, Object> jojoDynamic = jojo.dynamicProperties();
        if (extra.extra == null || jojoDynamic.size() != dynamicCount
                || extra.extra.size() != dynamicCount
                || !jojoDynamic.keySet().equals(extra.extra.keySet())) {
            throw new IllegalStateException(backend + ": dynamic fields were not retained equivalently"
                    + " (expected " + dynamicCount + ", jojo=" + jojoDynamic.keySet()
                    + ", native=" + (extra.extra == null ? null : extra.extra.keySet()) + ")");
        }
    }

    // ----- Fastjson2 -----

    @Benchmark
    public Object jojo_fastjson2_runtime() {
        return FASTJSON2_BINDER.readNode(json, JojoModel.class);
    }

    @Benchmark
    public Object pojo_fastjson2_runtime() {
        return FASTJSON2_BINDER.readNode(json, PojoModel.class);
    }

    @Benchmark
    public Object jojo_fastjson2_native_extra() {
        return readFastjson2Extra();
    }

    // ----- Jackson 2 -----

    @Benchmark
    public Object jojo_jackson2_runtime() {
        return JACKSON2_BINDER.readNode(json, JojoModel.class);
    }

    @Benchmark
    public Object pojo_jackson2_runtime() {
        return JACKSON2_BINDER.readNode(json, PojoModel.class);
    }

    @Benchmark
    public Object jojo_jackson2_native_extra() throws IOException {
        return JACKSON2.readValue(json, ExtraModel.class);
    }

    // ----- Jackson 3 -----

    @Benchmark
    public Object jojo_jackson3_runtime() {
        return JACKSON3_BINDER.readNode(json, JojoModel.class);
    }

    @Benchmark
    public Object pojo_jackson3_runtime() {
        return JACKSON3_BINDER.readNode(json, PojoModel.class);
    }

    @Benchmark
    public Object jojo_jackson3_native_extra() throws IOException {
        return JACKSON3.readValue(json, ExtraModel.class);
    }

    /** Same declared fields as JojoModel, without the JsonObject superclass. */
    public static class PojoModel {
        public long id;
        public long createdAt;
        public long updatedAt;
        public long reputation;
        public int loginCount;
        public int age;
        public boolean active;
        public boolean verified;
        public boolean admin;
        public boolean suspended;
        public double score;
        public double latitude;
        public double longitude;
        public String username;
        public String email;
        public String department;
    }

    /** Typed properties take precedence; unmatched names go to the dynamic map. */
    public static class JojoModel extends JsonObject {
        public long id;
        public long createdAt;
        public long updatedAt;
        public long reputation;
        public int loginCount;
        public int age;
        public boolean active;
        public boolean verified;
        public boolean admin;
        public boolean suspended;
        public double score;
        public double latitude;
        public double longitude;
        public String username;
        public String email;
        public String department;
    }

    /** Native equivalent using Jackson any-setter and Fastjson2 unwrapped map. */
    public static class ExtraModel {
        public long id;
        public long createdAt;
        public long updatedAt;
        public long reputation;
        public int loginCount;
        public int age;
        public boolean active;
        public boolean verified;
        public boolean admin;
        public boolean suspended;
        public double score;
        public double latitude;
        public double longitude;
        public String username;
        public String email;
        public String department;

        @JsonAnyGetter
        @JsonAnySetter
        @JSONField(unwrapped = true)
        public Map<String, Object> extra = new LinkedHashMap<>();
    }
}
