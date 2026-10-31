package org.sjf4j.testbench.binding.runtime;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.backend.snake.binding.SnakeBinder;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Full parser lifecycle: baseline readString() + Instant.parse() versus
 * readInstant(). Fastjson2's optimized implementation is pre-existing native.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(2)
public class InstantBackendBenchmark {

    @Param({"jackson2", "jackson3", "simple", "gson", "jsonp", "snake", "fastjson2"})
    public String backend;

    @Param({"seconds", "millis", "nanos"})
    public String shape;

    private Binder<?, ?> binder;
    private String json;

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
        switch (shape) {
            case "seconds": json = "\"2026-10-31T12:34:56Z\""; break;
            case "millis": json = "\"2026-10-31T12:34:56.123Z\""; break;
            case "nanos": json = "\"2026-10-31T12:34:56.123456789Z\""; break;
            default: throw new IllegalArgumentException(shape);
        }
    }

    @Benchmark
    public Instant baseline() throws IOException {
        try (StreamingParser parser = binder.createParser(json)) {
            parser.start();
            Instant value = Instant.parse(parser.readString());
            parser.end();
            return value;
        }
    }

    @Benchmark
    public Instant optimized() throws IOException {
        try (StreamingParser parser = binder.createParser(json)) {
            parser.start();
            Instant value = parser.readInstant();
            parser.end();
            return value;
        }
    }
}
