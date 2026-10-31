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
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * Full parser creation + LocalDate decoding (before/after fast path) for each
 * backend. Baseline explicitly uses the previous readString()+JDK parser path.
 * Compare each backend's baseline with its optimized result, not across backends.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class LocalDateBackendBenchmark {

    private static final String JSON = "\"2026-10-31\"";

    @Param({"jackson2", "jackson3", "simple", "gson", "jsonp", "snake", "fastjson2"})
    public String backend;

    private Binder<?, ?> binder;

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
    }

    @Benchmark
    public LocalDate baseline() throws IOException {
        try (StreamingParser parser = binder.createParser(JSON)) {
            parser.start();
            LocalDate value = LocalDate.parse(parser.readString());
            parser.end();
            return value;
        }
    }

    @Benchmark
    public LocalDate optimized() throws IOException {
        try (StreamingParser parser = binder.createParser(JSON)) {
            parser.start();
            LocalDate value = parser.readLocalDate();
            parser.end();
            return value;
        }
    }
}
