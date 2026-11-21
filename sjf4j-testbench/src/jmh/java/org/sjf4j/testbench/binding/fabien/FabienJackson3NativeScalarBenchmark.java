package org.sjf4j.testbench.binding.fabien;

import com.alibaba.fastjson2.JSON;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Read;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Scalar/enum native-coverage A/B: identical byte[] JSON and model for wrapper,
 * generated native, Jackson native, Blackbird and Fastjson2.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class FabienJackson3NativeScalarBenchmark {

    public enum StateValue { NEW, ACTIVE, PAUSED }

    public static class Order {
        public int id;
        public String title;
        public long timestamp;
        public BigDecimal amount;
        public BigInteger identifier;
        public byte severity;
        public short quantity;
        public StateValue state;
        public char category;
        public double weight;
        public boolean enabled;
        public String note;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Reader {
        @Read Order fromBytes(byte[] input) throws IOException;
    }

    @FunctionalInterface
    private interface Operation {
        Order read() throws Exception;
    }

    @Param({"compiled", "native", "blackbird", "fastjson2"})
    public String implementation;

    @Param({"1", "10"})
    public int sizeKb;

    @Param({"12345", "324"})
    public long seed;

    private Operation operation;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        final byte[] input = fixture(sizeKb, seed);
        switch (implementation) {
            case "compiled": {
                Reader binder = CompiledInstances.create(Reader.class);
                operation = () -> binder.fromBytes(input);
                break;
            }
            case "native":
            case "blackbird": {
                JsonMapper mapper = "blackbird".equals(implementation)
                        ? JsonMapper.builder()
                            .addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build()
                        : new JsonMapper();
                operation = () -> mapper.readValue(input, Order.class);
                break;
            }
            case "fastjson2":
                operation = () -> JSON.parseObject(input, Order.class);
                break;
            default:
                throw new IllegalArgumentException(implementation);
        }
        Order result = operation.read();
        if (result.id != 42 || result.timestamp != 1720000123456L
                || result.severity != 127 || result.quantity != 32000
                || result.amount.compareTo(new BigDecimal("1234.50")) != 0
                || !new BigInteger("123456789012345678901234567890").equals(result.identifier)
                || result.state != StateValue.ACTIVE || result.category != 'Z'
                || result.weight != 2.5 || !result.enabled
                || result.title == null || !result.title.contains(String.valueOf(seed))
                || result.note == null || result.note.length() < 500) {
            throw new IllegalStateException("Incorrect " + implementation
                    + " native scalar benchmark binding");
        }
    }

    @Benchmark
    public Order read() throws Exception {
        return operation.read();
    }

    private static byte[] fixture(int sizeKb, long seed) {
        String prefix = "{\"id\":42,\"title\":\"order-" + seed
                + "\",\"timestamp\":1720000123456,\"amount\":1234.50,"
                + "\"identifier\":123456789012345678901234567890,\"severity\":127,"
                + "\"quantity\":32000,\"state\":\"ACTIVE\",\"category\":\"Z\","
                + "\"weight\":2.5,\"enabled\":true,\"note\":\"";
        String suffix = "\"}";
        int length = Math.max(500, sizeKb * 1000
                - prefix.getBytes(StandardCharsets.UTF_8).length - suffix.length());
        StringBuilder out = new StringBuilder(prefix.length() + length + suffix.length());
        out.append(prefix);
        for (int i = 0; i < length; i++) {
            out.append((char) ('a' + (int) ((seed + i * 7) % 26)));
        }
        out.append(suffix);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
