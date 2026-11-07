package org.sjf4j.testbench.binding.fabien;

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
 * Complete POJO/Map serialization into a newly materialized byte[].
 * The timed operation includes output-buffer management and materialization.
 */
public class FabienWriteBenchmark {

    /** Run directly from IDEA; edit the defaults here for local experiments. */
    public static void main(String[] args) throws Exception {
        Main.main(args.length != 0 ? args : new String[]{
                FabienWriteBenchmark.class.getName() + ".write",
                "-p", "backend=jackson2",
                "-p", "implementation=runtime,compiled,native,blackbird",
                "-p", "model=users,clients",
                "-p", "shape=pojo",
                "-p", "sizeKb=1",
                "-p", "seed=12345",
                "-f", "1", "-wi", "3", "-i", "3",
                "-w", "500ms", "-r", "500ms"
        });
    }

    @Param({"jackson2", "jackson3", "fastjson2", "gson", "jsonp", "simple", "snake"})
    public String backend;

    @Param({"runtime", "compiled", "native", "blackbird"})
    public String implementation;

    @Param({"users", "clients"})
    public String model;

    @Param({"pojo", "map"})
    public String shape;

    @Param({"1", "10", "100"})
    public int sizeKb;

    @Param({"12345", "324", "43201"})
    public long seed;

    private Object value;
    private FabienBindings.Writer writer;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        FabienFixtures.Fixture fixture =
                FabienFixtures.create(backend, model, shape, sizeKb, seed);
        value = fixture.value;
        writer = FabienBindings.writer(backend, implementation, model, shape);
        FabienFixtures.verifyOutput(backend + "/" + implementation + "/" + model + "/" + shape,
                fixture, writer.write(value));
    }

    @Benchmark
    public byte[] write() throws Exception {
        return writer.write(value);
    }
}
