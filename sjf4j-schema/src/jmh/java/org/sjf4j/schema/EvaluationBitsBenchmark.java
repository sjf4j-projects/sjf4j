package org.sjf4j.schema;

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
import java.util.BitSet;
import java.util.concurrent.TimeUnit;

/**
 * Compares the evaluated-location representation independently of schema traversal.
 * Both implementations perform identical operations with the same indices.
 *
 * <p>Use -prof gc to compare allocation (B/op). set/range/or allocate a fresh
 * destination per invocation; get operates on pre-built sets and returns a
 * consumed result. The 64/65 sizes expose the inline-to-overflow boundary.
 *
 * <p>For more stable comparisons use -f 2 with the same JDK and machine.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
public class EvaluationBitsBenchmark {
    @Param({"8", "32", "64", "65", "128"})
    public int size;

    private BitSet reference;
    private BitSet otherReference;
    private EvaluationBits optimized;
    private EvaluationBits otherOptimized;

    public static void main(String[] args) throws IOException {
        Main.main(new String[]{EvaluationBitsBenchmark.class.getName(), "-prof", "gc"});
    }

    @Setup(Level.Trial)
    public void setup() {
        reference = new BitSet();
        otherReference = new BitSet();
        optimized = new EvaluationBits();
        otherOptimized = new EvaluationBits();
        for (int i = 0; i < size; i += 2) {
            reference.set(i);
            optimized.set(i);
        }
        for (int i = 1; i < size; i += 2) {
            otherReference.set(i);
            otherOptimized.set(i);
        }
    }

    @Benchmark
    public BitSet bitSet_set() {
        BitSet bits = new BitSet();
        for (int i = 0; i < size; i += 2) bits.set(i);
        return bits;
    }

    @Benchmark
    public EvaluationBits evaluationBits_set() {
        EvaluationBits bits = new EvaluationBits();
        for (int i = 0; i < size; i += 2) bits.set(i);
        return bits;
    }

    @Benchmark
    public int bitSet_get() {
        int count = 0;
        for (int i = 0; i < size; i++) if (reference.get(i)) count++;
        return count;
    }

    @Benchmark
    public int evaluationBits_get() {
        int count = 0;
        for (int i = 0; i < size; i++) if (optimized.get(i)) count++;
        return count;
    }

    @Benchmark
    public BitSet bitSet_range() {
        BitSet bits = new BitSet();
        bits.set(0, size);
        return bits;
    }

    @Benchmark
    public EvaluationBits evaluationBits_range() {
        EvaluationBits bits = new EvaluationBits();
        bits.set(0, size);
        return bits;
    }

    @Benchmark
    public BitSet bitSet_or() {
        BitSet bits = new BitSet();
        bits.or(reference);
        bits.or(otherReference);
        return bits;
    }

    @Benchmark
    public EvaluationBits evaluationBits_or() {
        EvaluationBits bits = new EvaluationBits();
        bits.or(optimized);
        bits.or(otherOptimized);
        return bits;
    }
}
