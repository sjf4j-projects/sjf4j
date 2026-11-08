package org.sjf4j.testbench.binding.fabien;

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
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.binding.PooledByteArrayOutputStream;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.testbench.model.fabien.Users;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Follow-up to JacksonOutputSinkBenchmark. Its baseline and stream cases use
 * DIFFERENT generated binder classes and cannot isolate the output overload.
 *
 * These cases compare:
 * - Original byte[] writer from FabienBindings$Jackson[23]_Impl;
 * - Sibling byte[] writer from JacksonOutputSinkBenchmark$Jackson[23]Stream_Impl;
 * - Sibling OutputStream writer from that EXACT same generated binder instance.
 *
 * This isolates generated class shape from byte[] versus OutputStream API.
 * Check output identity once per trial, outside the measured operation.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
public class JacksonWriterEntrypointBenchmark {
    @Param({"jackson2", "jackson3"})
    public String backend;

    @Param({"users", "clients"})
    public String model;

    @Param({"12345", "324"})
    public long seed;

    @Param({"100"})
    public int sizeKb;

    @Param({"native", "blackbird", "runtimeBytes", "runtimePooled",
            "originalBytes", "originalDirect", "siblingBytes", "siblingWrapped", "siblingPooled",
            "siblingBaos", "siblingDiscard"})
    public String variant;

    @FunctionalInterface
    private interface Operation { Object call() throws Exception; }

    @FunctionalInterface
    private interface StreamWriter { void write(OutputStream output) throws Exception; }

    private Operation operation;

    private static final class CountingSink extends OutputStream {
        private int count;
        @Override public void write(int value) { count++; }
        @Override public void write(byte[] bytes, int off, int len) {
            if (off < 0 || len < 0 || off > bytes.length - len)
                throw new IndexOutOfBoundsException();
            count = Math.addExact(count, len);
        }
        int size() { return count; }
    }

    private static Operation pooled(StreamWriter writer) {
        return () -> {
            PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
            try {
                writer.write(output);
                return output.toByteArray();
            } finally {
                output.recycle();
            }
        };
    }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        FabienFixtures.Fixture fixture =
                FabienFixtures.create(backend, model, "pojo", sizeKb, seed);
        Object node = fixture.value;
        // Same baseline as production of the original benchmark.
        byte[] expected = FabienBindings.writer(backend, "compiled", model, "pojo").write(node);
        FabienFixtures.verifyOutput("expected-original", fixture, expected);

        if ("native".equals(variant) || "blackbird".equals(variant)) {
            FabienBindings.Writer writer = FabienBindings.writer(backend, variant, model, "pojo");
            operation = () -> writer.write(node);
        } else if ("runtimeBytes".equals(variant) || "runtimePooled".equals(variant)) {
            if ("jackson2".equals(backend)) {
                Jackson2Binder binder = new Jackson2Binder();
                operation = "runtimeBytes".equals(variant)
                        ? () -> binder.writeNodeAsBytes(node)
                        : pooled(out -> binder.writeNode(out, node));
            } else {
                Jackson3Binder binder = new Jackson3Binder();
                operation = "runtimeBytes".equals(variant)
                        ? () -> binder.writeNodeAsBytes(node)
                        : pooled(out -> binder.writeNode(out, node));
            }
        } else if ("originalBytes".equals(variant)) {
            FabienBindings.Writer writer = FabienBindings.writer(backend, "compiled", model, "pojo");
            operation = () -> writer.write(node);
        } else if ("originalDirect".equals(variant)) {
            // Same generated implementation as originalBytes, but remove the
            // FabienBindings.Writer indirection to match siblingBytes.
            if ("jackson2".equals(backend)) {
                FabienBindings.Jackson2 binder = CompiledInstances.create(FabienBindings.Jackson2.class);
                operation = "users".equals(model)
                        ? () -> binder.writeUsers((Users) node)
                        : () -> binder.writeClients((Clients) node);
            } else {
                FabienBindings.Jackson3 binder = CompiledInstances.create(FabienBindings.Jackson3.class);
                operation = "users".equals(model)
                        ? () -> binder.writeUsers((Users) node)
                        : () -> binder.writeClients((Clients) node);
            }
        } else if ("jackson2".equals(backend)) {
            JacksonOutputSinkBenchmark.Jackson2Stream binder =
                    CompiledInstances.create(JacksonOutputSinkBenchmark.Jackson2Stream.class);
            if ("siblingBytes".equals(variant) || "siblingWrapped".equals(variant)) {
                FabienBindings.Writer writer = "users".equals(model)
                        ? v -> binder.usersBytes((Users) v)
                        : v -> binder.clientsBytes((Clients) v);
                operation = "siblingWrapped".equals(variant)
                        ? () -> writer.write(node)
                        : "users".equals(model)
                            ? () -> binder.usersBytes((Users) node)
                            : () -> binder.clientsBytes((Clients) node);
            } else {
                StreamWriter stream = "users".equals(model)
                        ? out -> binder.usersStream((Users) node, out)
                        : out -> binder.clientsStream((Clients) node, out);
                configureStreamVariant(stream, expected.length);
            }
        } else if ("jackson3".equals(backend)) {
            JacksonOutputSinkBenchmark.Jackson3Stream binder =
                    CompiledInstances.create(JacksonOutputSinkBenchmark.Jackson3Stream.class);
            if ("siblingBytes".equals(variant) || "siblingWrapped".equals(variant)) {
                FabienBindings.Writer writer = "users".equals(model)
                        ? v -> binder.usersBytes((Users) v)
                        : v -> binder.clientsBytes((Clients) v);
                operation = "siblingWrapped".equals(variant)
                        ? () -> writer.write(node)
                        : "users".equals(model)
                            ? () -> binder.usersBytes((Users) node)
                            : () -> binder.clientsBytes((Clients) node);
            } else {
                StreamWriter stream = "users".equals(model)
                        ? out -> binder.usersStream((Users) node, out)
                        : out -> binder.clientsStream((Clients) node, out);
                configureStreamVariant(stream, expected.length);
            }
        } else {
            throw new IllegalArgumentException("unknown backend " + backend);
        }

        Object actual = operation.call();
        if ("siblingDiscard".equals(variant)) {
            if (!(actual instanceof Integer) || ((Integer) actual) != expected.length) {
                throw new IllegalStateException("discard output length mismatch");
            }
        } else {
            byte[] result = (byte[]) actual;
            FabienFixtures.verifyOutput(backend + "/" + variant + "/" + model, fixture, result);
            // Native/Blackbird may legally use a different property order from
            // SJF4J while preserving the same JSON meaning. Compare bytes only
            // for the SJF4J entrypoint variants, where byte identity is expected.
            if (!"native".equals(variant) && !"blackbird".equals(variant)
                    && !Arrays.equals(expected, result)) {
                throw new IllegalStateException("output differs byte-for-byte: " + backend
                        + "/" + model + "/" + variant + "/" + seed);
            }
        }
    }

    private void configureStreamVariant(StreamWriter writer, int length) {
        switch (variant) {
            case "siblingPooled":
                operation = pooled(writer);
                break;
            case "siblingBaos":
                operation = () -> {
                    ByteArrayOutputStream output = new ByteArrayOutputStream(length);
                    writer.write(output);
                    return output.toByteArray();
                };
                break;
            case "siblingDiscard":
                operation = () -> {
                    CountingSink output = new CountingSink();
                    writer.write(output);
                    return output.size();
                };
                break;
            default:
                throw new IllegalArgumentException("unknown variant " + variant);
        }
    }

    @Benchmark
    public Object write() throws Exception {
        return operation.call();
    }
}
