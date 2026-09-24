package org.sjf4j.testbench.binding.runtime;

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
import org.sjf4j.TypeReference;
import org.sjf4j.binding.simple.SimpleNodeBinder;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Phase-0 benchmarks for SimpleNodeBinder source-type conversion paths. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class SimpleNodeBinderConversionBenchmark {

    private static final Type INTEGER_LIST_TYPE = new TypeReference<List<Integer>>() {}.getType();

    private final SimpleNodeBinder binder = new SimpleNodeBinder();
    private SourceUser pojoSource;
    private LinkedHashSet<Long> setSource;

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{SimpleNodeBinderConversionBenchmark.class.getName()});
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
    public Object simpleNodeBinder_pojoToPojo() {
        return binder.readNode(pojoSource, TargetUser.class);
    }

    @Benchmark
    public Object simpleNodeBinder_setToList() {
        return binder.readNode(setSource, INTEGER_LIST_TYPE);
    }

    @Benchmark
    public Object simpleNodeBinder_setToPrimitiveArray() {
        return binder.readNode(setSource, int[].class);
    }

    @Benchmark
    public Object simpleNodeBinder_setToJsonArray() {
        return binder.readNode(setSource, JsonArray.class);
    }

    @SuppressWarnings("unchecked")
    private void verifySources() {
        TargetUser target = (TargetUser) binder.readNode(pojoSource, TargetUser.class);
        if (!"Ann".equals(target.name) || target.age != 42) throw new AssertionError();

        List<Integer> list = (List<Integer>) binder.readNode(setSource, INTEGER_LIST_TYPE);
        int[] array = (int[]) binder.readNode(setSource, int[].class);
        JsonArray jsonArray = (JsonArray) binder.readNode(setSource, JsonArray.class);
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
