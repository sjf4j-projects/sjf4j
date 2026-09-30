package org.sjf4j.testbench.node;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.node.Numbers;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/** Isolates numeric hashing allocation and throughput for common JSON number shapes. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class NumbersHashBenchmark {
    private static final Integer INTEGER = 123456789;
    private static final Long LONG = 1234567890123456789L;
    private static final Double INTEGRAL_DOUBLE = 1000000.0d;
    private static final Double DECIMAL_DOUBLE = 1234567.25d;
    private static final BigDecimal DECIMAL = new BigDecimal("1234567.2500");

    @Benchmark
    public int integer() {
        return Numbers.hash(INTEGER);
    }

    @Benchmark
    public int longValue() {
        return Numbers.hash(LONG);
    }

    @Benchmark
    public int integralDouble() {
        return Numbers.hash(INTEGRAL_DOUBLE);
    }

    @Benchmark
    public int decimalDouble() {
        return Numbers.hash(DECIMAL_DOUBLE);
    }

    @Benchmark
    public int decimal() {
        return Numbers.hash(DECIMAL);
    }
}
