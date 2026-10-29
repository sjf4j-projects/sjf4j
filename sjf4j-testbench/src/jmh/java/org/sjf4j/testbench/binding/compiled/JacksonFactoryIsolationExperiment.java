package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson2.binding.Jackson2Parser;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Parser;
import org.sjf4j.binding.StreamingParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Factory-isolation diagnosis with identical document, parser lifetime,
 * map/list semantics, and EOF validation. Raw-shared wraps the mapper's
 * JsonFactory parser; Raw-independent uses an SJF4J Binder-owned factory.
 * Separates independent factory symbol table effects from Raw materialization.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 180, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 6, time = 180, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class JacksonFactoryIsolationExperiment {
    @Param({"j2", "j3"}) public String backend;
    @Param({"string", "utf8"}) public String input;
    @Param({"flat", "nested", "listmap"}) public String shape;

    private final ObjectMapper mapper2 = new ObjectMapper();
    private final JsonMapper mapper3 = new JsonMapper();
    private final Jackson2Binder separate2 = new Jackson2Binder();
    private final Jackson3Binder separate3 = new Jackson3Binder();
    private com.fasterxml.jackson.core.JsonFactory factory2;
    private tools.jackson.core.json.JsonFactory factory3;
    private String json;
    private byte[] bytes;
    private Class<?> target;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        factory2 = mapper2.getFactory();
        factory3 = (tools.jackson.core.json.JsonFactory) mapper3.tokenStreamFactory();
        if ("nested".equals(shape)) {
            json = CompiledReadBenchmark.JSON_DATA;
            target = Map.class;
        } else if ("flat".equals(shape)) {
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < 32; ++i) {
                if (i > 0) sb.append(',');
                sb.append('"').append("field").append(i).append("\":").append(i);
            }
            json = sb.append('}').toString();
            target = Map.class;
        } else {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < 16; ++i) {
                if (i > 0) sb.append(',');
                sb.append("{\"id\":").append(i)
                        .append(",\"name\":\"item").append(i)
                        .append("\",\"value\":3,\"active\":true,\"age\":32,\"city\":\"San Francisco\",\"tag\":\"x\",\"status\":null}");
            }
            json = sb.append(']').toString();
            target = List.class;
        }
        bytes = json.getBytes(StandardCharsets.UTF_8);
        Object expected = nativeDirect();
        for (Object actual : new Object[]{nativeCursor(), nativeCursorEnd(), rawShared(), rawIndependent()}) {
            if (!expected.equals(actual)) {
                throw new IllegalStateException("Results differ: " + backend + " " + shape
                        + " " + input + " " + actual);
            }
        }
    }

    private JsonParser nativeParser2() throws IOException {
        return "string".equals(input) ? factory2.createParser(json) : factory2.createParser(bytes);
    }
    private tools.jackson.core.JsonParser nativeParser3() {
        return "string".equals(input)
                ? factory3.createParser(ObjectReadContext.empty(), json)
                : factory3.createParser(ObjectReadContext.empty(), bytes);
    }
    private StreamingParser separateParser() throws IOException {
        if ("j2".equals(backend)) {
            return "string".equals(input) ? separate2.createParser(json) : separate2.createParser(bytes);
        }
        return "string".equals(input) ? separate3.createParser(json) : separate3.createParser(bytes);
    }
    private Object readRaw(StreamingParser parser) throws IOException {
        parser.start();
        Object result = target == Map.class ? parser.readRawObject() : parser.readRawArray();
        parser.end();
        return result;
    }

    @Benchmark
    public Object nativeDirect() throws Exception {
        if ("j2".equals(backend)) {
            return "string".equals(input) ? mapper2.readValue(json, target)
                    : mapper2.readValue(bytes, target);
        }
        return "string".equals(input) ? mapper3.readValue(json, target)
                : mapper3.readValue(bytes, target);
    }

    @Benchmark
    public Object nativeCursor() throws Exception {
        if ("j2".equals(backend)) {
            try (JsonParser parser = nativeParser2()) {
                return mapper2.readValue(parser, target);
            }
        }
        try (tools.jackson.core.JsonParser parser = nativeParser3()) {
            return mapper3.readValue(parser, target);
        }
    }

    @Benchmark
    public Object nativeCursorEnd() throws Exception {
        if ("j2".equals(backend)) {
            try (JsonParser parser = nativeParser2()) {
                Object result = mapper2.readValue(parser, target);
                if (parser.nextToken() != null) throw new IOException("trailing tokens");
                return result;
            }
        }
        try (tools.jackson.core.JsonParser parser = nativeParser3()) {
            Object result = mapper3.readValue(parser, target);
            if (parser.nextToken() != null) throw new IllegalStateException("trailing tokens");
            return result;
        }
    }

    @Benchmark
    public Object rawShared() throws IOException {
        if ("j2".equals(backend)) {
            try (Jackson2Parser parser = new Jackson2Parser(nativeParser2())) {
                return readRaw(parser);
            }
        }
        try (Jackson3Parser parser = new Jackson3Parser(nativeParser3())) {
            return readRaw(parser);
        }
    }

    @Benchmark
    public Object rawIndependent() throws IOException {
        try (StreamingParser parser = separateParser()) {
            return readRaw(parser);
        }
    }
}
