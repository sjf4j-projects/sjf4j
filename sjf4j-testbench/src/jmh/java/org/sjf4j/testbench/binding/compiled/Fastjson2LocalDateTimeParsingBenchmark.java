package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
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
import org.sjf4j.value.ValueCodec;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Compare Fastjson2's native date parser with the standard JDK parser.
 *
 * Both JSON-reading variants include JSONReader construction and read the same
 * quoted ISO-8601 input. A standalone JDK parse measures only the ISO string
 * conversion cost; readString-only isolates JSON string decoding overhead.
 *
 * No production SJF4J classes or backend-specific generated code are changed.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 7, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Thread)
public class Fastjson2LocalDateTimeParsingBenchmark {

    @Param({"seconds", "nanos"})
    public String precision;

    private String iso;
    private String json;
    private LocalDateTime expected;
    private JSONReader.Context context;

    @Setup(Level.Trial)
    public void setup() {
        iso = precision.equals("seconds")
                ? "2024-01-02T03:04:05"
                : "2024-01-02T03:04:05.123456789";
        json = "\"" + iso + "\"";
        expected = LocalDateTime.parse(iso);
        context = JSONFactory.createReadContext();

        if (!expected.equals(fastjson_readLocalDateTime_json())
                || !expected.equals(jdk_parse_after_readString_json())
                || !expected.equals(sjf4j_codec_after_readString_json())
                || !expected.equals(jdk_parse_plain_string())
                || !iso.equals(fastjson_readString_only_json())) {
            throw new AssertionError("LocalDateTime parsing variants returned different values");
        }
    }

    /** Native Fastjson2 parsing, including parser creation. */
    @Benchmark
    public LocalDateTime fastjson_readLocalDateTime_json() {
        try (JSONReader reader = JSONReader.of(json, context)) {
            return reader.readLocalDateTime();
        }
    }

    /** Identical input and parser creation, but parse the decoded Java String. */
    @Benchmark
    public LocalDateTime jdk_parse_after_readString_json() {
        try (JSONReader reader = JSONReader.of(json, context)) {
            return LocalDateTime.parse(reader.readString());
        }
    }

    /** Built-in SJF4J ValueCodec, still using the JDK date parser. */
    @Benchmark
    public LocalDateTime sjf4j_codec_after_readString_json() {
        try (JSONReader reader = JSONReader.of(json, context)) {
            return ValueCodec.LOCAL_DATE_TIME.rawToValue(reader.readString());
        }
    }

    /** The JDK parse itself, without any JSON tokenization or parser setup. */
    @Benchmark
    public LocalDateTime jdk_parse_plain_string() {
        return LocalDateTime.parse(iso);
    }

    /** Only Fastjson2 JSON string token decoding and reader setup. */
    @Benchmark
    public String fastjson_readString_only_json() {
        try (JSONReader reader = JSONReader.of(json, context)) {
            return reader.readString();
        }
    }
}
