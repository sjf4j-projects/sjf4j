package org.sjf4j.testbench.binding.uuid;

import com.fasterxml.jackson.core.JsonFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.sjf4j.backend.jackson2.binding.Jackson2Generator;
import org.sjf4j.value.FastUuid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Jackson 2 UUID output: its UUIDSerializer override versus the inherited
 * StreamingGenerator.writeUuid() equivalent, FastUuid.toString() + writeString().
 *
 * Both paths use the identical Jackson2Generator, JsonFactory, UUID inputs,
 * UTF-8 output sink and JSON array framing. Output bytes are consumed by JMH.
 * A setup-time check verifies exact byte equivalence, including null and edge UUIDs.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class Jackson2UuidWriteBenchmark {

    @Param({"1", "64", "512"})
    public int count;

    private UUID[] values;
    private JsonFactory factory;

    @Setup(Level.Trial)
    public void prepare() throws IOException {
        factory = new JsonFactory();
        values = new UUID[count];
        SplittableRandom random = new SplittableRandom(20261117L);
        for (int i = 0; i < count; i++) {
            values[i] = new UUID(random.nextLong(), random.nextLong());
        }

        UUID[] cases = {
                new UUID(0L, 0L),
                new UUID(-1L, -1L),
                new UUID(Long.MIN_VALUE, Long.MAX_VALUE),
                new UUID(0x123456789abcdef0L, 0x123456789abcdef0L),
                null,
                values[0]
        };
        if (!Arrays.equals(writeSerializer(cases), writeFastUuid(cases))) {
            throw new IllegalStateException("Jackson 2 UUIDSerializer and FastUuid differ in JSON output");
        }
    }

    @Benchmark
    public byte[] jacksonSerializer() throws IOException {
        return writeSerializer(values);
    }

    @Benchmark
    public byte[] fastUuid() throws IOException {
        return writeFastUuid(values);
    }

    private byte[] writeSerializer(UUID[] source) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream(source.length * 39 + 2);
        try (Jackson2Generator writer =
                     new Jackson2Generator(factory.createGenerator(sink))) {
            writer.writeArrayStart();
            for (UUID uuid : source) {
                writer.writeUuid(uuid);
            }
            writer.writeArrayEnd();
        }
        return sink.toByteArray();
    }

    private byte[] writeFastUuid(UUID[] source) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream(source.length * 39 + 2);
        try (Jackson2Generator writer =
                     new Jackson2Generator(factory.createGenerator(sink))) {
            writer.writeArrayStart();
            for (UUID uuid : source) {
                writer.writeString(uuid == null ? null : FastUuid.toString(uuid));
            }
            writer.writeArrayEnd();
        }
        return sink.toByteArray();
    }
}
