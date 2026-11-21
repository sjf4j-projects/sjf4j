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
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Read;
import org.sjf4j.testbench.model.fabien.Users;
import tools.jackson.databind.json.JsonMapper;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Compare native Compiled codegen with the existing wrapper on byte[], String
 * and Reader input. Reader allocation is measured for all implementations.
 * Data generation, binding construction, and result checks are outside JMH.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 2, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class FabienJackson3InputBenchmark {

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface CompiledJackson3 {
        @Read Users fromBytes(byte[] input);
        @Read Users fromString(String input);
        @Read Users fromReader(java.io.Reader input);
    }

    @FunctionalInterface
    private interface ReadOperation {
        Users read() throws Exception;
    }

    @Param({"bytes", "string", "reader"})
    public String source;

    @Param({"compiled", "native", "blackbird"})
    public String implementation;

    @Param({"10"})
    public int sizeKb;

    @Param({"12345", "324"})
    public long seed;

    private ReadOperation operation;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        FabienFixtures.Fixture fixture =
                FabienFixtures.create("jackson3", "users", "pojo", sizeKb, seed);
        final byte[] bytes = fixture.input;
        final String json = new String(bytes, StandardCharsets.UTF_8);

        if ("compiled".equals(implementation)) {
            final CompiledJackson3 binder = CompiledInstances.create(CompiledJackson3.class);
            switch (source) {
                case "bytes": operation = () -> binder.fromBytes(bytes); break;
                case "string": operation = () -> binder.fromString(json); break;
                case "reader": operation = () -> binder.fromReader(new StringReader(json)); break;
                default: throw new IllegalArgumentException(source);
            }
        } else {
            final JsonMapper mapper = "blackbird".equals(implementation)
                    ? JsonMapper.builder()
                        .addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build()
                    : new JsonMapper();
            switch (source) {
                case "bytes": operation = () -> mapper.readValue(bytes, Users.class); break;
                case "string": operation = () -> mapper.readValue(json, Users.class); break;
                case "reader": operation = () -> mapper.readValue(new StringReader(json), Users.class); break;
                default: throw new IllegalArgumentException(source);
            }
        }
        FabienFixtures.verify(source + "/" + implementation, fixture.value, operation.read());
    }

    @Benchmark
    public Object read() throws Exception {
        return operation.read();
    }
}
