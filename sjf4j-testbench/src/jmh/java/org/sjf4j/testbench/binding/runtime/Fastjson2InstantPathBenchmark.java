package org.sjf4j.testbench.binding.runtime;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.util.FastInstant;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Isolate three Instant decoding choices for the Fastjson2 backend, including
 * the full parser lifecycle. The native path has a potential seconds-only
 * performance cliff; do not choose by a single fractional-second case.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(2)
public class Fastjson2InstantPathBenchmark {

    @Param({"seconds", "millis", "nanos"})
    public String shape;

    private final Fastjson2Binder binder = new Fastjson2Binder();
    private String json;

    @Setup(Level.Trial)
    public void setup() {
        switch (shape) {
            case "seconds": json = "\"2026-10-31T12:34:56Z\""; break;
            case "millis": json = "\"2026-10-31T12:34:56.123Z\""; break;
            case "nanos": json = "\"2026-10-31T12:34:56.123456789Z\""; break;
            default: throw new IllegalArgumentException(shape);
        }
    }

    @Benchmark
    public Instant nativeReadInstant() throws IOException {
        try (StreamingParser parser = binder.createParser(json)) {
            parser.start();
            Instant result = parser.readInstant();
            parser.end();
            return result;
        }
    }

    @Benchmark
    public Instant fastString() throws IOException {
        try (StreamingParser parser = binder.createParser(json)) {
            parser.start();
            Instant result = FastInstant.parse(parser.readString());
            parser.end();
            return result;
        }
    }

    @Benchmark
    public Instant jdkString() throws IOException {
        try (StreamingParser parser = binder.createParser(json)) {
            parser.start();
            Instant result = Instant.parse(parser.readString());
            parser.end();
            return result;
        }
    }
}
