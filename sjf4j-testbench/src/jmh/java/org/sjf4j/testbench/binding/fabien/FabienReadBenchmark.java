package org.sjf4j.testbench.binding.fabien;

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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
/**
 * Complete input parsing into a new POJO or Map. Fixture generation, backend
 * selection and semantic verification are outside measurement.
 * Do not change the workload without incrementing the suite version.
 */
public class FabienReadBenchmark {

    @Param({"jackson2", "jackson3", "fastjson2", "gson", "jsonp", "simple", "snake"})
    public String backend;

    @Param({"runtime", "compiled"})
    public String implementation;

    @Param({"users", "clients"})
    public String model;

    @Param({"pojo", "map"})
    public String shape;

    @Param({"1", "10", "100"})
    public int sizeKb;

    @Param({"12345", "324", "43201"})
    public long seed;

    private byte[] input;
    private FabienBindings.Reader reader;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        FabienFixtures.Fixture fixture =
                FabienFixtures.create(backend, model, shape, sizeKb, seed);
        input = fixture.input;
        reader = FabienBindings.reader(backend, implementation, model, shape);
        FabienFixtures.verify(backend + "/" + implementation + "/" + model + "/" + shape,
                fixture.value, reader.read(input));
    }

    @Benchmark
    public Object read() {
        return reader.read(input);
    }
}
