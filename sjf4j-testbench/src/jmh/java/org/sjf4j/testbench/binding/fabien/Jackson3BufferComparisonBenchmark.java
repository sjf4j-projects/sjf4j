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
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.binding.PooledByteArrayOutputStream;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.testbench.model.fabien.Users;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Direct sink-only A/B: hold the SAME output writer and Java model constant,
 * change only the OutputStream collecting the finished Jackson3 UTF-8 bytes.
 *
 * compiled uses the SAME generated Jackson3Stream_Impl across all stream sinks.
 * runtime uses the SAME Jackson3Binder across all stream sinks.
 * mapper uses the SAME JsonMapper across all stream sinks.
 *
 * The nativeBytes reference preserves each writer's normal byte[] entrypoint,
 * which is not directly comparable as a pure sink change.
 *
 * ByteArrayBuilder is Jackson3's segment-based OutputStream, not SJF4J's
 * contiguous ThreadLocal pool. jacksonRecycler reuses ONE BufferRecycler per
 * benchmark thread; jacksonDefault has no recycler; jacksonSized preallocates
 * the initial segment without a recycler (Jackson ignores size if recycler is passed).
 * Trial setup checks output correctness; no comparisons run in the measured path.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
public class Jackson3BufferComparisonBenchmark {

    @Param({"compiled", "runtime", "mapper"})
    public String writer;

    @Param({"pooled", "jacksonRecycler", "jacksonDefault", "jacksonSized", "baos", "nativeBytes"})
    public String sink;

    @Param({"users", "clients"})
    public String model;

    @Param({"12345", "324"})
    public long seed;

    @Param({"100"})
    public int sizeKb;

    @FunctionalInterface
    private interface Operation {
        byte[] write() throws Exception;
    }

    @FunctionalInterface
    private interface StreamWriter {
        void write(OutputStream out) throws Exception;
    }

    private Operation operation;
    // JMH Scope.Thread guarantees one reusable recycler per benchmark thread.
    private BufferRecycler recycler;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        recycler = new BufferRecycler();
        FabienFixtures.Fixture fixture = FabienFixtures.create("jackson3", model, "pojo", sizeKb, seed);
        Object value = fixture.value;

        // SJF4J compiled bytes are the canonical byte-identical expectation.
        byte[] expected = FabienBindings.writer("jackson3", "compiled", model, "pojo").write(value);
        FabienFixtures.verifyOutput("reference", fixture, expected);

        final StreamWriter stream;
        final Operation nativeBytes;
        if ("compiled".equals(writer)) {
            JacksonOutputSinkBenchmark.Jackson3Stream binder =
                    CompiledInstances.create(JacksonOutputSinkBenchmark.Jackson3Stream.class);
            if ("users".equals(model)) {
                stream = out -> binder.usersStream((Users) value, out);
                nativeBytes = () -> binder.usersBytes((Users) value);
            } else {
                stream = out -> binder.clientsStream((Clients) value, out);
                nativeBytes = () -> binder.clientsBytes((Clients) value);
            }
        } else if ("runtime".equals(writer)) {
            Jackson3Binder binder = new Jackson3Binder();
            stream = out -> binder.writeNode(out, value);
            nativeBytes = () -> binder.writeNodeAsBytes(value);
        } else if ("mapper".equals(writer)) {
            JsonMapper mapper = new JsonMapper();
            stream = out -> mapper.writeValue(out, value);
            nativeBytes = () -> mapper.writeValueAsBytes(value);
        } else {
            throw new IllegalArgumentException("writer=" + writer);
        }

        switch (sink) {
            case "pooled":
                operation = () -> {
                    PooledByteArrayOutputStream out = new PooledByteArrayOutputStream();
                    try {
                        stream.write(out);
                        return out.toByteArray();
                    } finally {
                        out.recycle();
                    }
                };
                break;
            case "jacksonRecycler":
                operation = () -> {
                    // Acquire a reusable concatenation buffer from the SAME recycler.
                    // ByteArrayBuilder.release() returns its current block for reuse.
                    ByteArrayBuilder out = new ByteArrayBuilder(recycler);
                    try {
                        stream.write(out);
                        return out.toByteArray();
                    } finally {
                        out.release();
                    }
                };
                break;
            case "jacksonDefault":
                operation = () -> {
                    ByteArrayBuilder out = new ByteArrayBuilder();
                    try {
                        stream.write(out);
                        return out.toByteArray();
                    } finally {
                        out.release();
                    }
                };
                break;
            case "jacksonSized":
                operation = () -> {
                    ByteArrayBuilder out = new ByteArrayBuilder(expected.length);
                    try {
                        stream.write(out);
                        return out.toByteArray();
                    } finally {
                        out.release();
                    }
                };
                break;
            case "baos":
                operation = () -> {
                    ByteArrayOutputStream out = new ByteArrayOutputStream(expected.length);
                    stream.write(out);
                    return out.toByteArray();
                };
                break;
            case "nativeBytes":
                operation = nativeBytes;
                break;
            default:
                throw new IllegalArgumentException("sink=" + sink);
        }

        byte[] actual = operation.write();
        FabienFixtures.verifyOutput(writer + "/" + sink + "/" + model + "/" + seed, fixture, actual);
        if (!"mapper".equals(writer) && !Arrays.equals(expected, actual)) {
            throw new IllegalStateException("SJF4J serialized bytes changed: "
                    + writer + "/" + sink + "/" + model + "/" + seed);
        }
    }

    @Benchmark
    public byte[] write() throws Exception {
        return operation.write();
    }
}
