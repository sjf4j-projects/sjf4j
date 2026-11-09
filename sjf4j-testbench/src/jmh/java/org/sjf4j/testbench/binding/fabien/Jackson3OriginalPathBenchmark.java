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
import org.sjf4j.backend.jackson3.binding.Jackson3Generator;
import org.sjf4j.binding.PooledByteArrayOutputStream;
import org.sjf4j.testbench.model.fabien.Users;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Original Users POJO cross-check. NO projection model or write-logic changes:
 *
 *  originalBytes: FabienBindings.Jackson3_Impl writeUsers(byte[])
 *  siblingBytes:  JacksonOutputSinkBenchmark.Jackson3Stream_Impl usersBytes(byte[])
 *  siblingStream: same sibling generated class, usersStream(OutputStream), Pooled sink
 *  manual:        manually implemented JsonGenerator calls, Pooled sink
 *
 * Each path uses the identical original Users object, size, seed and Jackson3
 * binder configuration. All output validation happens in trial setup only.
 * This is a diagnostic benchmark, not a replacement for production binding.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
public class Jackson3OriginalPathBenchmark {

    @Param({"originalBytes", "siblingBytes", "siblingStream", "manual"})
    public String path;

    @Param({"12345", "324"})
    public long seed;

    @Param({"100"})
    public int sizeKb;

    @FunctionalInterface
    private interface WriteOperation {
        byte[] run() throws Exception;
    }

    @FunctionalInterface
    private interface StreamOperation {
        void write(OutputStream out) throws Exception;
    }

    private WriteOperation operation;

    private static byte[] pooled(StreamOperation stream) throws Exception {
        PooledByteArrayOutputStream out = new PooledByteArrayOutputStream();
        try {
            stream.write(out);
            return out.toByteArray();
        } finally {
            out.recycle();
        }
    }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        FabienFixtures.Fixture fixture = FabienFixtures.create(
                "jackson3", "users", "pojo", sizeKb, seed);
        Users value = (Users) fixture.value;

        // Both generated types are held as concrete interfaces; the timed
        // operation is a single monomorphic lambda for each JMH trial.
        FabienBindings.Jackson3 original =
                CompiledInstances.create(FabienBindings.Jackson3.class);
        JacksonOutputSinkBenchmark.Jackson3Stream sibling =
                CompiledInstances.create(JacksonOutputSinkBenchmark.Jackson3Stream.class);
        Jackson3Binder manualBinder = new Jackson3Binder();

        switch (path) {
            case "originalBytes":
                operation = () -> original.writeUsers(value);
                break;
            case "siblingBytes":
                operation = () -> sibling.usersBytes(value);
                break;
            case "siblingStream":
                operation = () -> pooled(out -> sibling.usersStream(value, out));
                break;
            case "manual":
                operation = () -> pooled(out -> {
                    try (Jackson3Generator generator = manualBinder.createGenerator(out)) {
                        Jackson3UserFieldAblationBenchmark.manualOriginal(generator.nativeWriter(), value);
                    }
                });
                break;
            default:
                throw new IllegalArgumentException("path=" + path);
        }

        // Verify the original benchmark input and every path's serialized JSON
        // represents the same model. Byte-order differences must not be mistaken
        // for different semantics; record byte identity separately in logs.
        byte[] reference = original.writeUsers(value);
        byte[] result = operation.run();
        FabienFixtures.verifyOutput("Jackson3 original path reference", fixture, reference);
        FabienFixtures.verifyOutput("Jackson3 original path " + path, fixture, result);
        if (!Arrays.equals(reference, result)) {
            throw new IllegalStateException("Different output bytes for " + path
                    + " (reference=" + reference.length + ", actual=" + result.length + ")");
        }
        // Hashing is exclusively in @Setup and enables cross-JAR byte checks.
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(result);
        StringBuilder hash = new StringBuilder(digest.length * 2);
        for (byte b : digest) hash.append(String.format("%02x", b & 0xff));
        System.out.println("PATH_CHECK path=" + path + " seed=" + seed
                + " bytes=" + result.length
                + " byteIdentical=true sha256=" + hash);
    }

    @Benchmark
    public byte[] write() throws Exception {
        return operation.run();
    }
}
