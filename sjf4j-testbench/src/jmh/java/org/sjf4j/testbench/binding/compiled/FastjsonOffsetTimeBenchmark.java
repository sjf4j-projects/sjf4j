package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONReader;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetTime;
import java.util.concurrent.TimeUnit;

/** Compare native Fastjson2 OffsetTime decoding to generic JDK parsing. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@State(Scope.Thread)
public class FastjsonOffsetTimeBenchmark {

    @Param({"12:30:01.123456789+02:00", "12:30:01Z", "12:30+02:00"})
    public String time;

    private byte[] json;

    @Setup
    public void setup() throws IOException {
        json = ("\\"" + time + "\\"").getBytes(StandardCharsets.UTF_8);
        OffsetTime expected = OffsetTime.parse(time);
        OffsetTime actual = nativeOffsetTime();
        if (!expected.equals(actual)) {
            throw new IllegalStateException("OffsetTime mismatch: " + time + " => " + actual);
        }
    }

    @Benchmark
    public OffsetTime stringParse() throws IOException {
        try (Fastjson2Parser parser = new Fastjson2Parser(JSONReader.of(json))) {
            parser.start();
            OffsetTime value = OffsetTime.parse(parser.readString());
            parser.end();
            return value;
        }
    }

    @Benchmark
    public OffsetTime nativeOffsetTime() throws IOException {
        try (Fastjson2Parser parser = new Fastjson2Parser(JSONReader.of(json))) {
            parser.start();
            OffsetTime value = parser.readOffsetTime();
            parser.end();
            return value;
        }
    }
}
