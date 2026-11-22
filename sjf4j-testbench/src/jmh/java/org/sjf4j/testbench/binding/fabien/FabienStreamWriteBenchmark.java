package org.sjf4j.testbench.binding.fabien;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Write;
import org.sjf4j.testbench.model.fabien.Users;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/** Diagnostic: exclude byte[] result materialization for every implementation. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class FabienStreamWriteBenchmark {
    private static final JsonMapper NATIVE = new JsonMapper();
    private static final JsonMapper BLACKBIRD = JsonMapper.builder()
            .addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build();

    @Param({"native", "blackbird", "compiled"})
    public String implementation;
    @Param({"1", "10", "100"})
    public int sizeKb;
    @Param({"12345", "324"})
    public long seed;

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3Stream {
        @Write void writeUsers(Users value, OutputStream output) throws IOException;
    }

    @FunctionalInterface
    interface StreamWriter {
        void write(Users users, OutputStream output) throws IOException;
    }

    private Users users;
    private StreamWriter writer;
    private CountingOutputStream sink;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        FabienFixtures.Fixture fixture =
                FabienFixtures.create("jackson3", "users", "pojo", sizeKb, seed);
        users = (Users) fixture.value;
        switch (implementation) {
            case "native": writer = NATIVE::writeValue; break;
            case "blackbird": writer = BLACKBIRD::writeValue; break;
            case "compiled": {
                Jackson3Stream compiled = CompiledInstances.create(Jackson3Stream.class);
                writer = compiled::writeUsers;
                break;
            }
            default: throw new IllegalArgumentException(implementation);
        }
        ByteArrayOutputStream verify = new ByteArrayOutputStream();
        writer.write(users, verify);
        FabienFixtures.verifyOutput("stream/" + implementation, fixture, verify.toByteArray());
        sink = new CountingOutputStream();
    }

    @Benchmark
    public long stream() throws IOException {
        sink.reset();
        writer.write(users, sink);
        return sink.bytesWritten;
    }

    /** Reused destination; count is returned so output cannot be discarded. */
    static final class CountingOutputStream extends OutputStream {
        long bytesWritten;
        void reset() { bytesWritten = 0L; }
        @Override public void write(int b) { bytesWritten++; }
        @Override public void write(byte[] b, int off, int len) { bytesWritten += len; }
        @Override public void flush() {}
        @Override public void close() {}
    }
}