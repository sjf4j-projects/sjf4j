package org.sjf4j.testbench.binding.runtime;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.backend.snake.binding.SnakeBinder;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.io.IOException;
import java.time.Duration;
import java.time.Period;
import java.util.concurrent.TimeUnit;

/**
 * Measures the parser + typed conversion path across all seven backends.
 *
 * <p>The typedBinder methods are the current production baseline. The
 * jdkStreaming and experimentalStreaming methods isolate the conversion
 * strategy after the same backend has decoded the JSON string. The latter
 * approximates the streaming codec path that a production implementation
 * would use, without changing production code in this evaluation.</p>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(2)
public class DurationPeriodBindingBenchmark {

    @Param({"jackson2", "jackson3", "simple", "gson", "jsonp", "snake", "fastjson2"})
    public String backend;

    private Binder<?, ?> binder;
    private String durationJson;
    private String periodJson;

    @Setup(Level.Trial)
    public void setup() {
        switch (backend) {
            case "jackson2": binder = new Jackson2Binder(); break;
            case "jackson3": binder = new Jackson3Binder(); break;
            case "simple": binder = new SimpleJsonBinder(); break;
            case "gson": binder = new GsonBinder(); break;
            case "jsonp": binder = new JsonpBinder(); break;
            case "snake": binder = new SnakeBinder(); break;
            case "fastjson2": binder = new Fastjson2Binder(); break;
            default: throw new IllegalArgumentException(backend);
        }
        durationJson = "\"P2DT3H4M5.678S\"";
        periodJson = "\"P1Y2M3W4D\"";
    }

    @Benchmark
    public Object durationTypedBinder() {
        return binder.readNode(durationJson, Duration.class);
    }

    @Benchmark
    public Duration durationJdkStreaming() throws IOException {
        try (StreamingParser parser = binder.createParser(durationJson)) {
            parser.start();
            Duration value = Duration.parse(parser.readString());
            parser.end();
            return value;
        }
    }

    @Benchmark
    public Duration durationExperimentalStreaming() throws IOException {
        try (StreamingParser parser = binder.createParser(durationJson)) {
            parser.start();
            Duration value = ExperimentalTemporalParsers.parseDuration(parser.readString());
            parser.end();
            return value;
        }
    }

    @Benchmark
    public Object periodTypedBinder() {
        return binder.readNode(periodJson, Period.class);
    }

    @Benchmark
    public Period periodJdkStreaming() throws IOException {
        try (StreamingParser parser = binder.createParser(periodJson)) {
            parser.start();
            Period value = Period.parse(parser.readString());
            parser.end();
            return value;
        }
    }

    @Benchmark
    public Period periodExperimentalStreaming() throws IOException {
        try (StreamingParser parser = binder.createParser(periodJson)) {
            parser.start();
            Period value = ExperimentalTemporalParsers.parsePeriod(parser.readString());
            parser.end();
            return value;
        }
    }
}
