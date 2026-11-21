package org.sjf4j.testbench.binding.fabien;

import com.alibaba.fastjson2.JSON;
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
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Mixed native/fallback POJO with direct scalar fields interleaved with
 * BigDecimal, enum, Map and nested object fields. Input preparation and
 * semantic verification are outside the measured operation.
 *
 * Same compiled method is benchmarked with nativePojo=false and with
 * nativePojo=true, hybridPojo=true, using matched library baselines.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class FabienJackson3HybridBenchmark {

    public enum Status { NEW, ACTIVE }

    public static class Detail {
        public String region;
        public BigDecimal rate;
    }

    public static class Order {
        public int id;
        public String title;
        public BigDecimal amount;
        public long timestamp;
        public Status status;
        public boolean enabled;
        public String code;
        public Map<String, String> labels;
        public double weight;
        public Detail detail;
        public int count;
        public String note;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface HybridReader {
        @Read Order fromBytes(byte[] input) throws IOException;
    }

    @FunctionalInterface
    private interface ReadOperation {
        Order read() throws Exception;
    }

    @Param({"compiled", "native", "blackbird", "fastjson2"})
    public String implementation;

    @Param({"1", "10"})
    public int sizeKb;

    @Param({"12345", "324"})
    public long seed;

    private ReadOperation operation;

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
        if (result.id != 42 || result.status != Status.ACTIVE
                || result.amount.compareTo(new BigDecimal("1234.50")) != 0
                || result.detail == null
                || result.detail.rate.compareTo(new BigDecimal("2.75")) != 0
                || !"west".equals(result.labels.get("region"))
                || result.note == null || result.note.length() < 500) {
            throw new IllegalStateException("Incorrect " + implementation + " mixed-POJO binding");
        }
    }

    @Benchmark
    public Order read() throws Exception {
        return operation.read();
    }

    private static byte[] fixture(int sizeKb, long seed) {
        String prefix = "{\"id\":42,\"title\":\"mixed-" + seed
                + "\",\"amount\":1234.50,\"timestamp\":1720000123456,"
                + "\"status\":\"ACTIVE\",\"enabled\":true,\"code\":\"ABC\","
                + "\"labels\":{\"region\":\"west\",\"priority\":\"high\"},"
                + "\"weight\":2.5,\"detail\":{\"region\":\"west\",\"rate\":2.75},"
                + "\"count\":17,\"note\":\"";
        String suffix = "\"}";
        int count = Math.max(500, sizeKb * 1000
                - prefix.getBytes(StandardCharsets.UTF_8).length - suffix.length());
        StringBuilder json = new StringBuilder(prefix.length() + count + suffix.length());
        json.append(prefix);
        for (int i = 0; i < count; i++) {
            json.append((char) ('a' + (int) ((seed + i * 7) % 26)));
        }
        json.append(suffix);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }
}
