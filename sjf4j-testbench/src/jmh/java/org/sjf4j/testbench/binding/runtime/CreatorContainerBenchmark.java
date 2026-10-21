package org.sjf4j.testbench.binding.runtime;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.TypeReference;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.binding.Binder;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Small Creator container workloads isolate per-container type resolution.
 *
 * Run on both the baseline branch and optimized branch with identical JMH
 * settings. This benchmark is for runtime readers (not compiled/native).
 * Compare -prof gc allocation rates as well as average latency.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CreatorContainerBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{CreatorContainerBenchmark.class.getName()});
    }

    private static final String ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final Jackson2Binder JACKSON2 = new Jackson2Binder();
    private static final Fastjson2Binder FASTJSON2 = new Fastjson2Binder();

    // Several short containers: fixed type resolution is a larger proportion of work.
    private static final String FIXED_JSON = "{\"ids\":[\"" + ID + "\"],\"byName\":{\"a\":\"" + ID
            + "\"},\"flags\":[\"" + ID + "\"]}";
    private static final String CONCRETE_JSON = "{\"ids\":[\"" + ID + "\"]}";
    private static final String MIXED_JSON = "{\"fixed\":[\"" + ID
            + "\"],\"values\":[7,8]}";
    private static final Type MIXED_TYPE = new TypeReference<Mixed<Integer>>() {}.getType();

    public record Fixed(List<UUID> ids, Map<String, UUID> byName, Set<UUID> flags) {}

    public record Concrete(ArrayList<UUID> ids) {}

    public static final class Mixed<T> {
        public final List<UUID> fixed;
        public final List<T> values;

        @NodeCreator
        public Mixed(@NodeProperty("fixed") List<UUID> fixed,
                     @NodeProperty("values") List<T> values) {
            this.fixed = fixed;
            this.values = values;
        }
    }

    @Setup
    public void verify() {
        for (Binder<?, ?> binder : new Binder[]{JACKSON2, FASTJSON2}) {
            Fixed fixed = (Fixed) binder.readNode(FIXED_JSON, Fixed.class);
            if (!UUID.fromString(ID).equals(fixed.ids().get(0))
                    || !UUID.fromString(ID).equals(fixed.byName().get("a"))
                    || !fixed.flags().contains(UUID.fromString(ID))) {
                throw new AssertionError("incorrect Fixed Creator binding");
            }
            Concrete concrete = (Concrete) binder.readNode(CONCRETE_JSON, Concrete.class);
            if (!UUID.fromString(ID).equals(concrete.ids().get(0))) {
                throw new AssertionError("incorrect concrete container binding");
            }
            Mixed<?> mixed = (Mixed<?>) binder.readNode(MIXED_JSON, MIXED_TYPE);
            if (!UUID.fromString(ID).equals(mixed.fixed.get(0))
                    || !List.of(7, 8).equals(mixed.values)) {
                throw new AssertionError("incorrect fixed/dynamic mixed Creator binding");
            }
        }
    }

    @Benchmark
    public Object jackson2_fixed() {
        return JACKSON2.readNode(FIXED_JSON, Fixed.class);
    }

    @Benchmark
    public Object fastjson2_fixed() {
        return FASTJSON2.readNode(FIXED_JSON, Fixed.class);
    }

    @Benchmark
    public Object jackson2_concrete() {
        return JACKSON2.readNode(CONCRETE_JSON, Concrete.class);
    }

    @Benchmark
    public Object fastjson2_concrete() {
        return FASTJSON2.readNode(CONCRETE_JSON, Concrete.class);
    }

    @Benchmark
    public Object jackson2_mixed() {
        return JACKSON2.readNode(MIXED_JSON, MIXED_TYPE);
    }

    @Benchmark
    public Object fastjson2_mixed() {
        return FASTJSON2.readNode(MIXED_JSON, MIXED_TYPE);
    }
}
