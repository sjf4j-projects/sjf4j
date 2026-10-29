package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson2.binding.Jackson2Parser;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Parser;
import org.sjf4j.binding.StreamingParser;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Diagnose original CompiledReadBenchmark Raw method dispatch:
 * declared StreamingParser versus concrete Jackson parser, with the
 * compiled implementation injected with the SAME Binder as both Raw paths.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations=6, time=250, timeUnit=TimeUnit.MILLISECONDS)
@Measurement(iterations=8, time=250, timeUnit=TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
public class JacksonCallSiteExperiment {
    @Param({"j2", "j3"}) public String backend;
    @Param({"string", "utf8"}) public String input;

    private static final String JSON = CompiledReadBenchmark.JSON_DATA;
    private static final byte[] BYTES = CompiledReadBenchmark.JSON_UTF8;
    private final ObjectMapper native2 = new ObjectMapper();
    private final JsonMapper native3 = new JsonMapper();
    private Jackson2Binder binder2;
    private Jackson3Binder binder3;
    private Compiled2 compiled2;
    private Compiled3 compiled3;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        binder2 = new Jackson2Binder();
        binder3 = new Jackson3Binder();
        compiled2 = CompiledInstances.create(Compiled2.class, binder2);
        compiled3 = CompiledInstances.create(Compiled3.class, binder3);
        Object expected = nativeDirect();
        for (Object v : new Object[]{rawBase(), rawConcrete(), compiledShared()}) {
            if (!expected.equals(v)) throw new IllegalStateException("Results differ");
        }
    }

    @Benchmark public Object nativeDirect() throws Exception {
        if ("j2".equals(backend)) {
            return "string".equals(input) ? native2.readValue(JSON, Map.class)
                    : native2.readValue(BYTES, Map.class);
        }
        return "string".equals(input) ? native3.readValue(JSON, Map.class)
                : native3.readValue(BYTES, Map.class);
    }

    // Reproduces the original benchmark's declared StreamingParser local variable.
    @Benchmark public Object rawBase() throws IOException {
        if ("j2".equals(backend)) {
            try (StreamingParser p = "string".equals(input) ? binder2.createParser(JSON)
                    : binder2.createParser(BYTES)) {
                p.start();
                Map<String,Object> v = p.readRawObject();
                p.end();
                return v;
            }
        }
        try (StreamingParser p = "string".equals(input) ? binder3.createParser(JSON)
                : binder3.createParser(BYTES)) {
            p.start();
            Map<String,Object> v = p.readRawObject();
            p.end();
            return v;
        }
    }

    // Identical work, but the JIT sees Jackson2Parser/Jackson3Parser statically.
    @Benchmark public Object rawConcrete() throws IOException {
        if ("j2".equals(backend)) {
            try (Jackson2Parser p = "string".equals(input) ? binder2.createParser(JSON)
                    : binder2.createParser(BYTES)) {
                p.start();
                Map<String,Object> v = p.readRawObject();
                p.end();
                return v;
            }
        }
        try (Jackson3Parser p = "string".equals(input) ? binder3.createParser(JSON)
                : binder3.createParser(BYTES)) {
            p.start();
            Map<String,Object> v = p.readRawObject();
            p.end();
            return v;
        }
    }

    // Shares the very same Binder instance/JsonFactory with both Raw methods.
    @Benchmark public Object compiledShared() throws IOException {
        if ("j2".equals(backend)) {
            return "string".equals(input) ? compiled2.map(JSON) : compiled2.map(BYTES);
        }
        return "string".equals(input) ? compiled3.map(JSON) : compiled3.map(BYTES);
    }

    @CompiledBinder(backend=Backend.JACKSON2)
    public interface Compiled2 {
        @ReadFrom Map<String,Object> map(String input) throws IOException;
        @ReadFrom Map<String,Object> map(byte[] input) throws IOException;
    }

    @CompiledBinder(backend=Backend.JACKSON3)
    public interface Compiled3 {
        @ReadFrom Map<String,Object> map(String input) throws IOException;
        @ReadFrom Map<String,Object> map(byte[] input) throws IOException;
    }
}
