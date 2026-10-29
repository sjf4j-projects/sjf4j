package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Allocation and throughput comparison for raw JSON objects of different shapes.
 *
 * Each invocation includes parser construction and closing; inputs are created
 * once in trial setup. Native JSONObject/JSONArray are valid Map/List results.
 * The forced-JDK variant isolates the container implementation, not fairness.
 *
 * Compare with -prof gc to measure gc.alloc.rate.norm (bytes per operation).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Thread)
public class Fastjson2RawAllocationBenchmark {

    @Param({"small", "wide", "deep"})
    public String shape;

    private final Fastjson2Binder binder = new Fastjson2Binder();
    private final JSONReader.Context nativeContext =
            JSONFactory.createReadContext(JSONReader.Feature.UseDoubleForDecimals);
    private final JSONReader.Context jdkContext =
            JSONFactory.createReadContext(JSONReader.Feature.UseDoubleForDecimals);

    private String json;
    private byte[] utf8;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        jdkContext.setObjectSupplier(LinkedHashMap::new);
        jdkContext.setArraySupplier(ArrayList::new);

        switch (shape) {
            case "small":
                json = "{\"id\":7,\"name\":\"Ada\",\"values\":[1,true,null]}";
                break;
            case "wide":
                json = createWideObject();
                break;
            case "deep":
                json = createDeepObject();
                break;
            default:
                throw new IllegalStateException(shape);
        }
        utf8 = json.getBytes(StandardCharsets.UTF_8);

        Map<String, Object> expected = readRawString();
        verifyEquivalent(expected, nativeString());
        verifyEquivalent(expected, nativeUtf8());
        verifyEquivalent(expected, wrappedNativeString());
        verifyEquivalent(expected, wrappedNativeUtf8());
        verifyEquivalent(expected, nativeJdkString());
        verifyEquivalent(expected, nativeJdkUtf8());
        verifyEquivalent(expected, readRawUtf8());
    }

    private static String createWideObject() {
        StringBuilder s = new StringBuilder(2048).append('{');
        for (int i = 0; i < 64; i++) {
            if (i != 0) s.append(',');
            s.append('"').append("field_").append(i).append("\":");
            switch (i % 4) {
                case 0:
                    s.append(i);
                    break;
                case 1:
                    s.append('"').append("name_").append(i).append('"');
                    break;
                case 2:
                    s.append("{\"n\":").append(i).append(",\"ok\":true}");
                    break;
                default:
                    s.append('[').append(i).append(",null,false]");
            }
        }
        return s.append('}').toString();
    }

    private static String createDeepObject() {
        StringBuilder s = new StringBuilder(512);
        for (int i = 0; i < 16; i++) s.append("{\"level\":").append(i).append(",\"child\":");
        s.append("{\"value\":[0,1,2,\"leaf\"]}");
        for (int i = 0; i < 16; i++) s.append('}');
        return s.toString();
    }

    private static void verifyEquivalent(Map<String, Object> expected, Object actual) {
        if (!(actual instanceof Map) || !expected.equals(actual)) {
            throw new IllegalStateException("Raw graph differs: " + actual);
        }
    }

    private Map<String, Object> readRawString() throws IOException {
        try (Fastjson2Parser parser = binder.createParser(json)) {
            parser.start();
            Map<String, Object> result = parser.readRawObject();
            parser.end();
            return result;
        }
    }

    private Map<String, Object> readRawUtf8() throws IOException {
        try (Fastjson2Parser parser = binder.createParser(utf8)) {
            parser.start();
            Map<String, Object> result = parser.readRawObject();
            parser.end();
            return result;
        }
    }

    @Benchmark
    public Object rawString() throws IOException {
        return readRawString();
    }

    @Benchmark
    public Object rawUtf8() throws IOException {
        return readRawUtf8();
    }

    @Benchmark
    public Object wrappedNativeString() throws IOException {
        try (Fastjson2Parser parser = binder.createParser(json)) {
            parser.start();
            Map<String, Object> result = parser.rawReader().readObject();
            parser.end();
            return result;
        }
    }

    @Benchmark
    public Object wrappedNativeUtf8() throws IOException {
        try (Fastjson2Parser parser = binder.createParser(utf8)) {
            parser.start();
            Map<String, Object> result = parser.rawReader().readObject();
            parser.end();
            return result;
        }
    }

    @Benchmark
    public Object nativeString() {
        try (JSONReader reader = JSONReader.of(json, nativeContext)) {
            return reader.readObject();
        }
    }

    @Benchmark
    public Object nativeUtf8() {
        try (JSONReader reader = JSONReader.of(utf8, nativeContext)) {
            return reader.readObject();
        }
    }

    @Benchmark
    public Object nativeJdkString() {
        try (JSONReader reader = JSONReader.of(json, jdkContext)) {
            return reader.readObject();
        }
    }

    @Benchmark
    public Object nativeJdkUtf8() {
        try (JSONReader reader = JSONReader.of(utf8, jdkContext)) {
            return reader.readObject();
        }
    }
}
