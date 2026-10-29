package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.jackson2.binding.Jackson2Parser;
import org.sjf4j.backend.jackson3.binding.Jackson3Parser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Diagnostic benchmark: Native and Raw share the identical Jackson JsonFactory.
 * The native cursor variant measures Databind only, and variants with end
 * separately account for the trailing-EOF token check performed by SJF4J.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 6, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 6, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class JacksonStringEndExperiment {
    @Param({"j2", "j3"}) public String backend;
    @Param({"string", "utf8"}) public String input;

    private final String json = CompiledReadBenchmark.JSON_DATA;
    private final byte[] bytes = CompiledReadBenchmark.JSON_UTF8;
    private final ObjectMapper native2 = new ObjectMapper();
    private final JsonMapper native3 = new JsonMapper();
    private com.fasterxml.jackson.core.JsonFactory factory2;
    private JsonFactory factory3;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        factory2 = native2.getFactory();
        factory3 = (JsonFactory) native3.tokenStreamFactory();
        Object expected = nativeDirect();
        for (Object actual : new Object[] { nativeCursor(), nativeCursorEnd(),
                rawNoEnd(), rawWithEnd() }) {
            if (!expected.equals(actual)) {
                throw new IllegalStateException("different parsed map: " + actual);
            }
        }
    }

    private com.fasterxml.jackson.core.JsonParser new2() throws IOException {
        return "string".equals(input) ? factory2.createParser(json) : factory2.createParser(bytes);
    }

    private tools.jackson.core.JsonParser new3() {
        return "string".equals(input)
                ? factory3.createParser(ObjectReadContext.empty(), json)
                : factory3.createParser(ObjectReadContext.empty(), bytes);
    }

    @Benchmark
    public Object nativeDirect() throws Exception {
        if ("j2".equals(backend)) {
            return "string".equals(input) ? native2.readValue(json, Map.class)
                    : native2.readValue(bytes, Map.class);
        }
        return "string".equals(input) ? native3.readValue(json, Map.class)
                : native3.readValue(bytes, Map.class);
    }

    @Benchmark
    public Object nativeCursor() throws Exception {
        if ("j2".equals(backend)) {
            try (com.fasterxml.jackson.core.JsonParser p = new2()) {
                return native2.readValue(p, Map.class);
            }
        }
        try (tools.jackson.core.JsonParser p = new3()) {
            return native3.readValue(p, Map.class);
        }
    }

    @Benchmark
    public Object nativeCursorEnd() throws Exception {
        if ("j2".equals(backend)) {
            try (com.fasterxml.jackson.core.JsonParser p = new2()) {
                Object value = native2.readValue(p, Map.class);
                if (p.nextToken() != null) throw new IOException("trailing content");
                return value;
            }
        }
        try (tools.jackson.core.JsonParser p = new3()) {
            Object value = native3.readValue(p, Map.class);
            if (p.nextToken() != null) throw new IllegalStateException("trailing content");
            return value;
        }
    }

    @Benchmark
    public Object rawNoEnd() throws Exception {
        if ("j2".equals(backend)) {
            try (Jackson2Parser p = new Jackson2Parser(new2())) {
                p.start();
                return p.readRawObject();
            }
        }
        try (Jackson3Parser p = new Jackson3Parser(new3())) {
            p.start();
            return p.readRawObject();
        }
    }

    @Benchmark
    public Object rawWithEnd() throws Exception {
        if ("j2".equals(backend)) {
            try (Jackson2Parser p = new Jackson2Parser(new2())) {
                p.start();
                Object value = p.readRawObject();
                p.end();
                return value;
            }
        }
        try (Jackson3Parser p = new Jackson3Parser(new3())) {
            p.start();
            Object value = p.readRawObject();
            p.end();
            return value;
        }
    }
}
