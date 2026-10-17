package org.sjf4j.testbench.binding.compiled;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Writer;
import org.sjf4j.binding.CompiledName;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Isolates the Runtime prepared-name dispatch while keeping native writer
 * allocation and byte[] output in the timed operation.
 *
 * Run the same JMH binary pattern against two builds of Fastjson2Writer:
 * the fixed-width switch and the original raw UTF-8 array copy.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 8, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class Fastjson2RuntimeNameBenchmark {

    private static final String[] NAMES = {
            "id", "createdAt", "updatedAt", "reputation", "loginCount",
            "age", "active", "verified", "admin", "suspended",
            "score", "latitude", "longitude", "username", "email",
            "displayName", "passwordHash", "bio", "website", "department",
            "address", "tags", "friends"
    };

    private final Fastjson2Binder binder = new Fastjson2Binder();
    private CompiledName[] preparedNames;

    @Setup
    public void setUp() {
        preparedNames = new CompiledName[NAMES.length];
        try (Fastjson2Writer writer = binder.createWriterUtf8()) {
            for (int i = 0; i < NAMES.length; i++) {
                preparedNames[i] = writer.createCompiledName(NAMES[i]);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Benchmark
    public byte[] runtimePreparedNamesUtf8() throws IOException {
        try (Fastjson2Writer writer = binder.createWriterUtf8()) {
            writer.startObject();
            for (int i = 0; i < preparedNames.length; i++) {
                writer.writeName(preparedNames[i]);
                writer.writeIntValue(i);
            }
            writer.endObject();
            return writer.toBytes();
        }
    }
}
