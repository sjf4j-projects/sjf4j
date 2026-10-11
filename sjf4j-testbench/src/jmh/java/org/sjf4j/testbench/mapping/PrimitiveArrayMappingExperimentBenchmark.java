package org.sjf4j.testbench.mapping;

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
import org.sjf4j.TypeReference;
import org.sjf4j.mapping.NodeMapper;
import org.sjf4j.node.Numbers;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Short-lived JMH experiment to isolate Array.get and intermediate boxing
 * during a 32-element long[] / Long[] -> List<Integer> conversion.
 *
 * The manual variants are micro-components, NOT semantic replacements for
 * NodeMapper (which handles generics, OneOf, custom containers and errors).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 6, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class PrimitiveArrayMappingExperimentBenchmark {
    private static final Type INTEGER_LIST_TYPE = new TypeReference<List<Integer>>() {}.getType();

    // Above the Integer/Long caching range is important for attributing B/op.
    @Param({"0", "1000"})
    public int start;

    private long[] primitive;
    private Long[] boxed;

    @Setup(Level.Trial)
    public void setup() {
        primitive = new long[32];
        boxed = new Long[32];
        for (int i = 0; i < primitive.length; i++) {
            primitive[i] = start + i;
            boxed[i] = primitive[i];
        }
        List<?> mapped = (List<?>) NodeMapper.convert(primitive, INTEGER_LIST_TYPE, false);
        if (mapped.size() != 32 || !mapped.get(31).equals(start + 31)) {
            throw new AssertionError("NodeMapper long[] semantics mismatch");
        }
    }

    @Benchmark
    public Object mapper_primitive() {
        return NodeMapper.convert(primitive, INTEGER_LIST_TYPE, false);
    }

    @Benchmark
    public Object mapper_reference() {
        return NodeMapper.convert(boxed, INTEGER_LIST_TYPE, false);
    }

    @Benchmark
    public Object reflect_primitive() {
        List<Integer> result = new ArrayList<>(primitive.length);
        for (int i = 0; i < primitive.length; i++) {
            result.add(Numbers.toInt((Number) Array.get(primitive, i)));
        }
        return result;
    }

    @Benchmark
    public Object boxed_primitive() {
        List<Integer> result = new ArrayList<>(primitive.length);
        for (int i = 0; i < primitive.length; i++) {
            result.add(Numbers.toInt(Long.valueOf(primitive[i])));
        }
        return result;
    }

    @Benchmark
    public Object direct_primitive() {
        List<Integer> result = new ArrayList<>(primitive.length);
        for (int i = 0; i < primitive.length; i++) {
            result.add(Numbers.toInt(primitive[i]));
        }
        return result;
    }

    @Benchmark
    public Object reflect_reference() {
        List<Integer> result = new ArrayList<>(boxed.length);
        for (int i = 0; i < boxed.length; i++) {
            result.add(Numbers.toInt((Number) Array.get(boxed, i)));
        }
        return result;
    }

    @Benchmark
    public Object direct_reference() {
        List<Integer> result = new ArrayList<>(boxed.length);
        for (int i = 0; i < boxed.length; i++) {
            result.add(Numbers.toInt(boxed[i]));
        }
        return result;
    }
}
