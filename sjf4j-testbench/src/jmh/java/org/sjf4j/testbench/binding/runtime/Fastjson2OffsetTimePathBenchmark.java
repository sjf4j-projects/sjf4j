package org.sjf4j.testbench.binding.runtime;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Parser;

import java.io.IOException;
import java.time.OffsetTime;
import java.util.concurrent.TimeUnit;

/**
 * Same Fastjson2 native parser and SJF4J parser lifecycle, comparing
 * the adapter's shared FastOffsetTime path with native readOffsetTime().
 * Both paths use the same JSONReader.Context.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(2)
public class Fastjson2OffsetTimePathBenchmark {

    @Param({"minutesZ", "secondsOffset", "millisOffset", "nanosOffset"})
    public String shape;

    private JSONReader.Context readerContext;
    private Fastjson2Binder binder;
    private String json;

    @Setup(Level.Trial)
    public void setup() {
        readerContext = JSONFactory.createReadContext();
        binder = new Fastjson2Binder(readerContext, JSONFactory.createWriteContext());
        switch (shape) {
            case "minutesZ": json = "\"12:34Z\""; break;
            case "secondsOffset": json = "\"12:34:56+05:30\""; break;
            case "millisOffset": json = "\"12:34:56.123-07:00\""; break;
            case "nanosOffset": json = "\"12:34:56.123456789+05:30\""; break;
            default: throw new IllegalArgumentException(shape);
        }
    }

    @Benchmark
    public OffsetTime sharedFastOffsetTime() throws IOException {
        try (Fastjson2Parser parser = binder.createParser(json)) {
            parser.start();
            OffsetTime result = parser.readOffsetTime();
            parser.end();
            return result;
        }
    }

    @Benchmark
    public OffsetTime nativeFastjson2() throws IOException {
        JSONReader jsonReader = JSONReader.of(json, readerContext);
        try (Fastjson2Parser parser = binder.createParser(jsonReader)) {
            parser.start();
            OffsetTime result = jsonReader.readOffsetTime();
            parser.end();
            return result;
        }
    }
}
