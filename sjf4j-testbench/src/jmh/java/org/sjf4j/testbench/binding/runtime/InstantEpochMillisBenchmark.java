package org.sjf4j.testbench.binding.runtime;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.backend.snake.binding.SnakeBinder;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.StreamingGenerator;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.value.ValueCodec;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Compares the old boxed Long codec path and the new primitive streaming path
 * for Instant epochMillis, including parser/generator lifecycle.
 *
 * The old path uses the exact same registered codec's rawToValue/valueToRaw
 * methods; no control changes the codec's representation or accepted values.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(2)
public class InstantEpochMillisBenchmark {

    @Param({"jackson2", "jackson3", "simple", "gson", "jsonp", "snake", "fastjson2"})
    public String backend;

    @Param({"positive", "negative"})
    public String value;

    private Binder<?, ?> binder;
    private String json;
    private Instant instant;
    private ValueCodec<Instant, Long> codec;
    private ValueCodec.StreamingValueCodec.Parser<Instant> directReader;
    private ValueCodec.StreamingValueCodec.Generator<Instant> directWriter;

    @SuppressWarnings("unchecked")
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
        long millis = "positive".equals(value) ? 1_782_908_096_123L : -1_234_567_890_123L;
        json = Long.toString(millis);
        instant = Instant.ofEpochMilli(millis);
        codec = ValueCodec.INSTANT_EPOCH_MILLIS;
        ValueCodec.StreamingValueCodec<Instant, Long> streaming =
                (ValueCodec.StreamingValueCodec<Instant, Long>) codec;
        directReader = streaming.streamParser();
        directWriter = streaming.streamGenerator();
    }

    @Benchmark
    public Instant readBoxed() throws IOException {
        try (StreamingParser parser = binder.createParser(json)) {
            parser.start();
            Instant result = codec.rawToValue(parser.readLong());
            parser.end();
            return result;
        }
    }

    @Benchmark
    public Instant readStreaming() throws IOException {
        try (StreamingParser parser = binder.createParser(json)) {
            parser.start();
            Instant result = directReader.read(parser);
            parser.end();
            return result;
        }
    }

    @Benchmark
    public String writeBoxed() throws IOException {
        StringWriter output = new StringWriter();
        try (StreamingGenerator generator = binder.createGenerator(output)) {
            generator.start();
            generator.writeLong(codec.valueToRaw(instant));
            generator.end();
        }
        return output.toString();
    }

    @Benchmark
    public String writeStreaming() throws IOException {
        StringWriter output = new StringWriter();
        try (StreamingGenerator generator = binder.createGenerator(output)) {
            generator.start();
            directWriter.write(generator, instant);
            generator.end();
        }
        return output.toString();
    }
}
