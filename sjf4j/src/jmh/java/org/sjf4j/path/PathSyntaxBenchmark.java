package org.sjf4j.path;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/** Isolates JSONPath and JSON Pointer syntax compilation from path execution. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class PathSyntaxBenchmark {
    private static final String SIMPLE_PATH = "$.store.book[1].price";
    private static final String QUOTED_PATH = "$['store']['book'][1]['price']";
    private static final String UNION_PATH = "$.store.book[1,2,3].price";
    private static final String COMPLEX_SLICE_PATH = "$.store.book[ -10 : 20 : 2 ].price";
    private static final String POINTER = "/store/book/1/price";
    private static final String OVERFLOW_POINTER = "/store/2147483648/price";

    @Benchmark
    public PathSegment[] parseSimplePath() {
        return PathSyntax.parsePath(SIMPLE_PATH);
    }

    @Benchmark
    public PathSegment[] parseQuotedPath() {
        return PathSyntax.parsePath(QUOTED_PATH);
    }

    @Benchmark
    public PathSegment[] parseUnionPath() {
        return PathSyntax.parsePath(UNION_PATH);
    }

    @Benchmark
    public PathSegment[] parseComplexSlicePath() {
        return PathSyntax.parsePath(COMPLEX_SLICE_PATH);
    }

    @Benchmark
    public PathSegment[] parsePointer() {
        return PathSyntax.parsePointer(POINTER);
    }

    @Benchmark
    public PathSegment[] parseOverflowPointer() {
        return PathSyntax.parsePointer(OVERFLOW_POINTER);
    }
}
