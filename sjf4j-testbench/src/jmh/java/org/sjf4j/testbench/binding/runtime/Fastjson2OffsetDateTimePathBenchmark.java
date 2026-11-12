package org.sjf4j.testbench.binding.runtime;

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
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Parser;
import org.sjf4j.value.FastOffsetDateTime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Experimental comparison with identical JSONReader.Context and input:
 * native = Fastjson2's date parser;
 * sharedFast = Fastjson2 string decoding followed by SJF4J FastOffsetDateTime;
 * adapter = the production Fastjson2Parser.readOffsetDateTime() implementation.
 *
 * All measured operations construct and close the JSON reader. The adapter
 * additionally checks parser start/end, as the actual SJF4J binding path does.
 * Check end-to-end Native/Compiled effects with FabienReadBenchmark (Clients/POJO).
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(3)
public class Fastjson2OffsetDateTimePathBenchmark {

    @Param({"string", "utf8"})
    public String encoding;

    @Param({"secondsZ", "secondsOffset", "millisOffset", "nanosOffset"})
    public String shape;

    private JSONReader.Context readerContext;
    private Fastjson2Binder binder;
    private String json;
    private byte[] bytes;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        readerContext = JSONFactory.createReadContext();
        binder = new Fastjson2Binder(readerContext, JSONFactory.createWriteContext());

        String text;
        switch (shape) {
            case "secondsZ":
                text = "2026-10-31T12:34:56Z";
                break;
            case "secondsOffset":
                text = "2026-10-31T12:34:56+05:30";
                break;
            case "millisOffset":
                text = "2026-10-31T12:34:56.123-07:00";
                break;
            case "nanosOffset":
                text = "2026-10-31T12:34:56.123456789+05:30";
                break;
            default:
                throw new IllegalArgumentException(shape);
        }
        json = "\"" + text + "\"";
        bytes = json.getBytes(StandardCharsets.UTF_8);

        OffsetDateTime expected = OffsetDateTime.parse(text);
        verify("native", expected, nativeFastjson2());
        verify("sharedFast", expected, sharedFast());
        verify("adapter", expected, adapter());
    }

    private static void verify(String path, OffsetDateTime expected, OffsetDateTime actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(path + " produced " + actual + " instead of " + expected);
        }
    }

    private JSONReader newReader() {
        return "utf8".equals(encoding)
                ? JSONReader.of(bytes, readerContext)
                : JSONReader.of(json, readerContext);
    }

    @Benchmark
    public OffsetDateTime nativeFastjson2() {
        try (JSONReader reader = newReader()) {
            return reader.readOffsetDateTime();
        }
    }

    @Benchmark
    public OffsetDateTime sharedFast() {
        try (JSONReader reader = newReader()) {
            return FastOffsetDateTime.parse(reader.readString());
        }
    }

    @Benchmark
    public OffsetDateTime adapter() throws IOException {
        try (Fastjson2Parser parser = binder.createParser(newReader())) {
            parser.start();
            OffsetDateTime result = parser.readOffsetDateTime();
            parser.end();
            return result;
        }
    }
}
