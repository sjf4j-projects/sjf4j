package org.sjf4j;

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
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class GlobalConfigBenchmark {

    @Param({"10000"})
    public int iters;

    @Setup(Level.Trial)
    public void setup() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder().build());
    }

    @Benchmark
    public void global_per_access(Blackhole bh) {
        for (int i = 0; i < iters; i++) {
            bh.consume(Sjf4jConfig.global());
        }
    }

    @Benchmark
    public void global_cached(Blackhole bh) {
        Sjf4jConfig cfg = Sjf4jConfig.global();
        for (int i = 0; i < iters; i++) {
            bh.consume(cfg);
        }
    }

    @Benchmark
    public void global_mapSupplier(Blackhole bh) {
        for (int i = 0; i < iters; i++) {
            bh.consume(Sjf4jConfig.global().mapSupplier);
        }
    }

    @Benchmark
    public void global_mapSupplier_cached(Blackhole bh) {
        Object ms = Sjf4jConfig.global().mapSupplier;
        for (int i = 0; i < iters; i++) {
            bh.consume(ms);
        }
    }
}
