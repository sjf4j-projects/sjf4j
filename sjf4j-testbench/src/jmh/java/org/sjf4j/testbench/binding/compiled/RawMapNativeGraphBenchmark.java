package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Parser;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Compare Fastjson2 native reading with exact nested LinkedHashMap/ArrayList
 * suppliers against SJF4J raw (same recursive graph). Never change production code.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
public class RawMapNativeGraphBenchmark {
    private static final JSONReader.Context DEFAULT_CONTEXT =
            JSONFactory.createReadContext(JSONReader.Feature.UseDoubleForDecimals);
    private static final JSONReader.Context SUPPLIED_CONTEXT =
            JSONFactory.createReadContext(JSONReader.Feature.UseDoubleForDecimals);
    static {
        SUPPLIED_CONTEXT.setObjectSupplier(LinkedHashMap::new);
        SUPPLIED_CONTEXT.setArraySupplier(ArrayList::new);
    }

    private static final Fastjson2Binder BINDER = new Fastjson2Binder();

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Compiled {
        @ReadFrom Map<String, Object> read(String input) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] input) throws IOException;
    }
    private static final Compiled COMPILED = CompiledInstances.create(Compiled.class);

    @State(Scope.Thread)
    public static class Input {
        @Param({"numbers","strings","user"})
        public String shape;
        String json;
        byte[] utf8;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            json = shape.equals("user") ? User.JSON_DATA
                    : createWide(shape.equals("strings"));
            utf8 = json.getBytes(StandardCharsets.UTF_8);
            Map<String,Object> expected = COMPILED.read(json);
            Map<String,Object> suppliedTyped = nativeTyped(json, SUPPLIED_CONTEXT);
            Map<String,Object> suppliedRaw = nativeObject(json, SUPPLIED_CONTEXT);
            requireEquivalent(expected, suppliedTyped);
            requireEquivalent(expected, suppliedRaw);
            assertExactContainers(suppliedTyped);
            assertExactContainers(suppliedRaw);
        }
    }

    private static String createWide(boolean stringValues) {
        StringBuilder s = new StringBuilder(512).append('{');
        for (int i = 0; i < 24; i++) {
            if (i != 0) s.append(',');
            s.append('"').append("field").append(i).append("\":");
            if (stringValues) s.append('"').append("value").append(i).append('"');
            else if (i % 4 == 0) s.append(i+1000);
            else if (i % 4 == 1) s.append(i+1700000000123L);
            else if (i % 4 == 2) s.append("98.75");
            else s.append("-122.4194");
        }
        return s.append('}').toString();
    }

    private static void assertExactContainers(Object value) {
        if (value instanceof Map) {
            if (value.getClass() != LinkedHashMap.class)
                throw new AssertionError("Unexpected native map class: " + value.getClass());
            for (Object entry : ((Map<?,?>) value).values()) assertExactContainers(entry);
        } else if (value instanceof List) {
            if (value.getClass() != ArrayList.class)
                throw new AssertionError("Unexpected native array class: " + value.getClass());
            for (Object entry : (List<?>) value) assertExactContainers(entry);
        }
    }
    private static void requireEquivalent(Object left, Object right) {
        if (left == right) return;
        if (left instanceof Map && right instanceof Map) {
            Map<?,?> a=(Map<?,?>)left,b=(Map<?,?>)right;
            if(!a.keySet().equals(b.keySet())) throw new AssertionError("keys differ");
            for(Map.Entry<?,?> e:a.entrySet()) requireEquivalent(e.getValue(), b.get(e.getKey()));
        } else if (left instanceof List && right instanceof List) {
            List<?> a=(List<?>)left,b=(List<?>)right;
            if(a.size()!=b.size())throw new AssertionError("list sizes differ");
            for(int i=0;i<a.size();i++) requireEquivalent(a.get(i),b.get(i));
        } else if(left instanceof Number && right instanceof Number) {
            if(new java.math.BigDecimal(left.toString()).compareTo(new java.math.BigDecimal(right.toString()))!=0)
                throw new AssertionError("numbers differ: "+left+" vs "+right);
        } else if(left == null || !left.equals(right)) throw new AssertionError("values differ");
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> nativeTyped(String json, JSONReader.Context ctx) {
        try(JSONReader reader=JSONReader.of(json,ctx)) {
            return (Map<String,Object>) reader.read(LinkedHashMap.class);
        }
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> nativeTyped(byte[] json, JSONReader.Context ctx) {
        try(JSONReader reader=JSONReader.of(json,ctx)) {
            return (Map<String,Object>) reader.read(LinkedHashMap.class);
        }
    }
    private static Map<String,Object> nativeObject(String json, JSONReader.Context ctx) {
        try(JSONReader reader=JSONReader.of(json,ctx)) {
            return reader.readObject();
        }
    }
    private static Map<String,Object> nativeObject(byte[] json, JSONReader.Context ctx) {
        try(JSONReader reader=JSONReader.of(json,ctx)) {
            return reader.readObject();
        }
    }
    private static Map<String,Object> directRaw(String json) throws IOException {
        try(Fastjson2Parser p=BINDER.createParser(json)) {
            p.start();
            Map<String,Object> m=p.readRawObject();
            p.end();
            return m;
        }
    }
    private static Map<String,Object> directRaw(byte[] json) throws IOException {
        try(Fastjson2Parser p=BINDER.createParser(json)) {
            p.start();
            Map<String,Object> m=p.readRawObject();
            p.end();
            return m;
        }
    }

    @Benchmark
    public Object nativeLinkedString(Input f) throws IOException {
        return nativeTyped(f.json, DEFAULT_CONTEXT);
    }

    @Benchmark
    public Object nativeLinkedUtf8(Input f) throws IOException {
        return nativeTyped(f.utf8, DEFAULT_CONTEXT);
    }

    @Benchmark
    public Object nativeExactTypedString(Input f) throws IOException {
        return nativeTyped(f.json, SUPPLIED_CONTEXT);
    }

    @Benchmark
    public Object nativeExactTypedUtf8(Input f) throws IOException {
        return nativeTyped(f.utf8, SUPPLIED_CONTEXT);
    }

    @Benchmark
    public Object nativeExactRawString(Input f) throws IOException {
        return nativeObject(f.json, SUPPLIED_CONTEXT);
    }

    @Benchmark
    public Object nativeExactRawUtf8(Input f) throws IOException {
        return nativeObject(f.utf8, SUPPLIED_CONTEXT);
    }

    @Benchmark
    public Object directRawString(Input f) throws IOException {
        return directRaw(f.json);
    }

    @Benchmark
    public Object directRawUtf8(Input f) throws IOException {
        return directRaw(f.utf8);
    }

    @Benchmark
    public Object compiledString(Input f) throws IOException {
        return COMPILED.read(f.json);
    }

    @Benchmark
    public Object compiledUtf8(Input f) throws IOException {
        return COMPILED.readBytes(f.utf8);
    }

}
