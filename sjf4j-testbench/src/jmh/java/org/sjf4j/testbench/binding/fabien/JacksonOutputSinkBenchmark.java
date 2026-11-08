package org.sjf4j.testbench.binding.fabien;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Write;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.binding.PooledByteArrayOutputStream;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.testbench.model.fabien.Users;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Diagnostic only: does the output sink explain Jackson3's compiled Users POJO
 * write slowdown? Keep backend, implementation, model, seed, and sink together
 * in the same hosted-runner job. No production writer or pool is modified.
 *
 * baseline: original byte[] API used by FabienWriteBenchmark.
 * pooled:   OutputStream API with PooledByteArrayOutputStream + copy.
 * baos:     OutputStream API with pre-sized ByteArrayOutputStream + copy.
 * discard:  OutputStream API with a counting, allocation-free-data sink.
 *
 * The three stream experiments differ from baseline in public writer overload.
 * Compare implementations WITHIN each sink, not baseline vs stream directly.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
public class JacksonOutputSinkBenchmark {

    @Param({"jackson2", "jackson3"})
    public String backend;

    @Param({"native", "blackbird", "runtime", "compiled"})
    public String implementation;

    @Param({"users", "clients"})
    public String model;

    @Param({"baseline", "pooled", "baos", "discard"})
    public String sink;

    @Param({"12345", "324"})
    public long seed;

    @Param({"100"})
    public int sizeKb;

    private Operation operation;

    @FunctionalInterface
    private interface Operation {
        Object run() throws Exception;
    }

    @FunctionalInterface
    private interface StreamWriter {
        void write(Object value, OutputStream output) throws Exception;
    }

    private static final class CountingSink extends OutputStream {
        private int count;

        @Override
        public void write(int value) {
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            if (offset < 0 || length < 0 || offset > bytes.length - length) {
                throw new IndexOutOfBoundsException();
            }
            count = Math.addExact(count, length);
        }

        int bytesWritten() {
            return count;
        }
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2Stream {
        @Write byte[] usersBytes(Users value);
        @Write byte[] clientsBytes(Clients value);
        @Write void usersStream(Users value, OutputStream output);
        @Write void clientsStream(Clients value, OutputStream output);
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3Stream {
        @Write byte[] usersBytes(Users value);
        @Write byte[] clientsBytes(Clients value);
        @Write void usersStream(Users value, OutputStream output);
        @Write void clientsStream(Clients value, OutputStream output);
    }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        FabienFixtures.Fixture fixture = FabienFixtures.create(backend, model, "pojo", sizeKb, seed);
        Object value = fixture.value;

        // The same baseline implementation as the full binding benchmark.
        FabienBindings.Writer original = FabienBindings.writer(backend, implementation, model, "pojo");
        byte[] expected = original.write(value);
        FabienFixtures.verifyOutput(backend + "/" + implementation + "/baseline", fixture, expected);

        if ("baseline".equals(sink)) {
            operation = () -> original.write(value);
            return;
        }

        StreamWriter stream = streamWriter();
        if ("pooled".equals(sink)) {
            operation = () -> {
                PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
                try {
                    stream.write(value, output);
                    return output.toByteArray();
                } finally {
                    output.recycle();
                }
            };
        } else if ("baos".equals(sink)) {
            // Pre-sizing removes resize noise but retains the toByteArray copy.
            operation = () -> {
                ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
                stream.write(value, output);
                return output.toByteArray();
            };
        } else if ("discard".equals(sink)) {
            // Counting makes the full streamed bytes observable to the JIT.
            operation = () -> {
                CountingSink output = new CountingSink();
                stream.write(value, output);
                return output.bytesWritten();
            };
        } else {
            throw new IllegalArgumentException("unknown sink: " + sink);
        }

        Object check = operation.run();
        if ("discard".equals(sink)) {
            if (((Integer) check).intValue() != expected.length) {
                throw new IllegalStateException("discarded length differs from original for "
                        + backend + "/" + implementation + "/" + model);
            }
        } else {
            byte[] bytes = (byte[]) check;
            FabienFixtures.verifyOutput(backend + "/" + implementation + "/" + sink, fixture, bytes);
            if (!Arrays.equals(expected, bytes)) {
                throw new IllegalStateException("stream output differs byte-for-byte from baseline for "
                        + backend + "/" + implementation + "/" + model + "/" + sink);
            }
        }
    }

    private StreamWriter streamWriter() {
        if ("runtime".equals(implementation)) {
            if ("jackson2".equals(backend)) {
                Jackson2Binder binder = new Jackson2Binder();
                return (v, out) -> binder.writeNode(out, v);
            }
            Jackson3Binder binder = new Jackson3Binder();
            return (v, out) -> binder.writeNode(out, v);
        }

        if ("compiled".equals(implementation)) {
            if ("jackson2".equals(backend)) {
                Jackson2Stream binder = CompiledInstances.create(Jackson2Stream.class);
                return "users".equals(model)
                        ? (v, out) -> binder.usersStream((Users) v, out)
                        : (v, out) -> binder.clientsStream((Clients) v, out);
            }
            Jackson3Stream binder = CompiledInstances.create(Jackson3Stream.class);
            return "users".equals(model)
                    ? (v, out) -> binder.usersStream((Users) v, out)
                    : (v, out) -> binder.clientsStream((Clients) v, out);
        }

        if ("native".equals(implementation) || "blackbird".equals(implementation)) {
            boolean blackbird = "blackbird".equals(implementation);
            if ("jackson2".equals(backend)) {
                ObjectMapper mapper = new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                if (blackbird) mapper.registerModule(new BlackbirdModule());
                return mapper::writeValue;
            }
            JsonMapper mapper = blackbird
                    ? JsonMapper.builder().addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build()
                    : new JsonMapper();
            return mapper::writeValue;
        }
        throw new IllegalArgumentException("unknown implementation: " + implementation);
    }

    @Benchmark
    public Object write() throws Exception {
        return operation.run();
    }
}
