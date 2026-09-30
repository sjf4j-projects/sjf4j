package org.sjf4j.testbench.mapping;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.JsonArray;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.mapping.NodeMapper;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Benchmarks NodeMapper source-type conversion paths. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class NodeMapperConversionBenchmark {

    private static final Type INTEGER_LIST_TYPE = new TypeReference<List<Integer>>() {}.getType();

    private SourceUser pojoSource;
    private LinkedHashSet<Long> setSource;

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{NodeMapperConversionBenchmark.class.getName()});
    }

    @Setup(Level.Trial)
    public void setup() {
        pojoSource = new SourceUser();
        pojoSource.name = "Ann";
        pojoSource.age = 42L;

        setSource = new LinkedHashSet<>();
        for (long i = 0; i < 32; i++) {
            setSource.add(i);
        }

        verifySources();
    }

    @Benchmark
    public Object nodeMapper_pojoToPojo() {
        return NodeMapper.convert(pojoSource, TargetUser.class, false);
    }

    @Benchmark
    public Object nodeMapper_setToList() {
        return NodeMapper.convert(setSource, INTEGER_LIST_TYPE, false, RuntimeContext.EMPTY);
    }

    @Benchmark
    public Object nodeMapper_setToPrimitiveArray() {
        return NodeMapper.convert(setSource, int[].class, false);
    }

    @Benchmark
    public Object nodeMapper_setToJsonArray() {
        return NodeMapper.convert(setSource, JsonArray.class, false);
    }

    @SuppressWarnings("unchecked")
    private void verifySources() {
        TargetUser target = (TargetUser) NodeMapper.convert(pojoSource, TargetUser.class, false);
        if (!"Ann".equals(target.name) || target.age != 42) throw new AssertionError();

        List<Integer> list = (List<Integer>) NodeMapper.convert(
                setSource, INTEGER_LIST_TYPE, false, RuntimeContext.EMPTY);
        int[] array = (int[]) NodeMapper.convert(setSource, int[].class, false);
        JsonArray jsonArray = (JsonArray) NodeMapper.convert(setSource, JsonArray.class, false);
        if (list.size() != 32 || array.length != 32 || jsonArray.size() != 32) throw new AssertionError();
    }

    public static class SourceUser {
        public String name;
        public long age;
    }

    public static class TargetUser {
        public String name;
        public int age;
    }
}
