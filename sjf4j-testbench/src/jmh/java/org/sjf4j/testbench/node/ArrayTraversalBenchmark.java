package org.sjf4j.testbench.node;

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
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.TimeUnit;

/** Compares indexed and enhanced-for traversal of an ArrayList and a String array. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 15, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
public class ArrayTraversalBenchmark {

    public static void main(String[] args) throws IOException {
        Main.main(new String[]{ArrayTraversalBenchmark.class.getName()});
    }

    @Param({"0", "1", "8", "32", "1024"})
    public int size;

    private List<String> value;
    private String[] array;

    @Setup(Level.Trial)
    public void setup() {
        ArrayList<String> list = new ArrayList<>(size);
        long expected = 0;
        for (int i = 0; i < size; i++) {
            String element = "element-" + i;
            list.add(element);
            expected += element.length();
        }
        value = list;
        array = list.toArray(new String[0]);
        if (list_or() != expected || list_random() != expected
                || list_enhanced() != expected || array_random() != expected
                || array_enhanced() != expected) {
            throw new IllegalStateException("Traversal results differ at size=" + size);
        }
    }

    @Benchmark
    public long list_or() {
        long result = 0;
        if (value instanceof RandomAccess) {
            for (int i = 0, n = value.size(); i < n; i++) {
                result += value.get(i).length();
            }
        } else {
            for (String element : value) {
                result += element.length();
            }
        }
        return result;
    }

    @Benchmark
    public long list_random() {
        long result = 0;
        for (int i = 0, n = value.size(); i < n; i++) {
            result += value.get(i).length();
        }
        return result;
    }

    @Benchmark
    public long list_enhanced() {
        long result = 0;
        for (String element : value) {
            result += element.length();
        }
        return result;
    }

    @Benchmark
    public long array_random() {
        long result = 0;
        for (int i = 0, n = array.length; i < n; i++) {
            result += array[i].length();
        }
        return result;
    }

    @Benchmark
    public long array_enhanced() {
        long result = 0;
        for (String element : array) {
            result += element.length();
        }
        return result;
    }

}
