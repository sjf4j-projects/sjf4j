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
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Reads a single ISO date-time, including parser creation/start/end/close.
 * Baseline is readString()+JDK parse; optimized is the backend's readLocalDateTime.
 * Fastjson2 keeps its pre-existing native parser; do not count it as a new win.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(2)
public class LocalDateTimeBackendBenchmark {

    @Param({"jackson2", "jackson3", "simple", "gson", "jsonp", "snake", "fastjson2"})
    public String backend;

    @Param({"minutes", "seconds", "millis", "nanos"})
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
            case "minutes": json = "\"2026-10-31T12:34\""; break;
            case "seconds": json = "\"2026-10-31T12:34:56\""; break;
            case "millis": json = "\"2026-10-31T12:34:56.123\""; break;
            case "nanos": json = "\"2026-10-31T12:34:56.123456789\""; break;
            default: throw new IllegalArgumentException(shape);
        }
    }

    @Benchmark
    public LocalDateTime baseline() throws IOException {
        try (StreamingParser parser = binder.createParser(json)) {
            parser.start();
            LocalDateTime value = LocalDateTime.parse(parser.readString());
            parser.end();
            return value;
        }
    }

    @Benchmark
    public LocalDateTime optimized() throws IOException {
        try (StreamingParser parser = binder.createParser(json)) {
            parser.start();
            LocalDateTime value = parser.readLocalDateTime();
            parser.end();
            return value;
        }
    }
}
