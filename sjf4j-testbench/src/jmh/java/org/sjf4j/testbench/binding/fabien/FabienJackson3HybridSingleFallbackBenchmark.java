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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Native-dominant flat POJO: sixteen native scalars/strings and exactly
 * one non-native BigDecimal. No containers, collection allocations or
 * nested cursor transfers. The payload and result checks are outside JMH.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class FabienJackson3HybridSingleFallbackBenchmark {

    public static class FlatOrder {
        public int id;
        public String title;
        public long timestamp;
        public boolean enabled;
        public String region;
        public double latitude;
        public int count;
        public String code;
        public long epoch;
        public boolean archived;
        public String owner;
        public String category;
        public double weight;
        public int revision;
        public String source;
        public String note;
        public BigDecimal amount;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface HybridReader {
        @Read FlatOrder fromBytes(byte[] input) throws IOException;
    }

    @FunctionalInterface
    private interface Operation {
        FlatOrder read() throws Exception;
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
                HybridReader binder = CompiledInstances.create(HybridReader.class);
                operation = () -> binder.fromBytes(input);
                break;
            }
            case "native":
            case "blackbird": {
                JsonMapper mapper = "blackbird".equals(implementation)
                        ? JsonMapper.builder()
                            .addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build()
                        : new JsonMapper();
                operation = () -> mapper.readValue(input, FlatOrder.class);
                break;
            }
            case "fastjson2":
                operation = () -> JSON.parseObject(input, FlatOrder.class);
                break;
            default:
                throw new IllegalArgumentException(implementation);
        }
        FlatOrder order = operation.read();
        if (order.id != 42 || order.timestamp != 1720000123456L
                || !order.enabled || order.archived
                || order.count != 17 || order.revision != 3
                || order.latitude != 37.75 || order.weight != 2.5
                || !"west".equals(order.region)
                || !"ABC".equals(order.code)
                || order.title == null || !order.title.contains(String.valueOf(seed))
                || order.amount == null
                || order.amount.compareTo(new BigDecimal("1234.50")) != 0
                || order.note == null || order.note.length() < 500) {
            throw new IllegalStateException("Incorrect " + implementation
                    + " single-fallback binding");
        }
    }

    @Benchmark
    public FlatOrder read() throws Exception {
        return operation.read();
    }

    private static byte[] fixture(int sizeKb, long seed) {
        String prefix = "{\"id\":42,\"title\":\"order-" + seed
                + "\",\"timestamp\":1720000123456,\"enabled\":true,"
                + "\"region\":\"west\",\"latitude\":37.75,\"count\":17,"
                + "\"code\":\"ABC\",\"epoch\":1720000123456,\"archived\":false,"
                + "\"owner\":\"alice\",\"category\":\"retail\",\"weight\":2.5,"
                + "\"revision\":3,\"source\":\"online\",\"amount\":1234.50,\"note\":\"";
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
