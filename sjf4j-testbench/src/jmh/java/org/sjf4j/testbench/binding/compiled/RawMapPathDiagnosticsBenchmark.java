package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Parser;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.gson.binding.GsonParser;
import org.sjf4j.testbench.binding.handwritten.Fastjson2HandMapV2Reader;
import org.sjf4j.testbench.binding.handwritten.GsonHandMapReader;
import org.sjf4j.testbench.model.User;
import org.sjf4j.util.Numbers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Isolates root-container choice, number strategy, and SJF4J adapter overhead.
 * All paths parse the entire document for every invocation.
 * Gson native-default deliberately changes number classes; native-matched does not.
 * Compare same backend/shape/encoding, including gc.alloc.rate.norm B/op.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
public class RawMapPathDiagnosticsBenchmark {

    private static final Fastjson2Binder FAST_BINDER = new Fastjson2Binder();
    private static final GsonBinder GSON_BINDER = new GsonBinder();
    // SJF4J Fastjson2Binder configures exactly this decimal strategy.
    private static final JSONReader.Context FAST_NATIVE_CONTEXT =
            JSONFactory.createReadContext(JSONReader.Feature.UseDoubleForDecimals);
    private static final Gson GSON_DEFAULT = new GsonBuilder().create();
    private static final Gson GSON_MATCHED = new GsonBuilder()
            .setObjectToNumberStrategy(in -> Numbers.parseNumber(in.nextString()))
            .create();

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface FastCompiled {
        @ReadFrom Map<String, Object> read(String input) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] input) throws IOException;
    }

    @CompiledBinder(backend = Backend.GSON)
    public interface GsonCompiled {
        @ReadFrom Map<String, Object> read(String input) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] input) throws IOException;
    }

    private static final FastCompiled FAST_COMPILED = CompiledInstances.create(FastCompiled.class);
    private static final GsonCompiled GSON_COMPILED = CompiledInstances.create(GsonCompiled.class);

    @State(Scope.Thread)
    public static class Input {
        @Param({"two", "numbers", "strings", "user"})
        public String shape;
        String json;
        byte[] utf8;
        int rootSize;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            switch (shape) {
                case "two":
                    json = "{\"id\":7,\"name\":\"Ada\"}";
                    rootSize = 2;
                    break;
                case "numbers":
                    json = createWide(false);
                    rootSize = 24;
                    break;
                case "strings":
                    json = createWide(true);
                    rootSize = 24;
                    break;
                case "user":
                    json = User.JSON_DATA;
                    rootSize = 23;
                    break;
                default:
                    throw new IllegalArgumentException(shape);
            }
            utf8 = json.getBytes(StandardCharsets.UTF_8);

            // Work is not part of any timed invocation.
            Map<?, ?> fastCompiled = FAST_COMPILED.read(json);
            Map<?, ?> fastNative = nativeFastMap(json, Map.class);
            Map<?, ?> fastLinked = nativeFastMap(json, LinkedHashMap.class);
            Map<?, ?> fastHand = (Map<?, ?>) handwrittenFast(json);
            Map<?, ?> gsonCompiled = GSON_COMPILED.read(json);
            Map<?, ?> gsonMatched = GSON_MATCHED.fromJson(json, Map.class);
            Map<?, ?> gsonLinked = GSON_MATCHED.fromJson(json, LinkedHashMap.class);
            Map<?, ?> gsonHand = (Map<?, ?>) handwrittenGson(json);
            for (Map<?, ?> m : new Map<?, ?>[]{fastCompiled, fastNative, fastLinked,
                    fastHand, gsonCompiled, gsonMatched, gsonLinked, gsonHand}) {
                if (m == null || m.size() != rootSize) {
                    throw new AssertionError(shape + ": incorrect root map " + m);
                }
            }
            requireEquivalent(fastCompiled, fastNative);
            requireEquivalent(fastCompiled, fastLinked);
            requireEquivalent(fastCompiled, fastHand);
            requireEquivalent(gsonCompiled, gsonMatched);
            requireEquivalent(gsonCompiled, gsonLinked);
            requireEquivalent(gsonCompiled, gsonHand);
            // Deliberately do NOT require default Gson numeric classes to match.
        }
    }

    private static String createWide(boolean strings) {
        StringBuilder b = new StringBuilder(512).append('{');
        for (int i = 0; i < 24; i++) {
            if (i > 0) b.append(',');
            b.append('"').append("field").append(i).append("\":");
            if (strings) b.append('"').append("value").append(i).append('"');
            else if (i % 4 == 0) b.append(i + 1000);
            else if (i % 4 == 1) b.append(i + 1700000000123L);
            else if (i % 4 == 2) b.append("98.75");
            else b.append("-122.4194");
        }
        return b.append('}').toString();
    }

    private static void requireEquivalent(Object a, Object b) {
        if (a == b) return;
        if (a instanceof Map && b instanceof Map) {
            Map<?, ?> ma = (Map<?, ?>) a, mb = (Map<?, ?>) b;
            if (!ma.keySet().equals(mb.keySet())) throw new AssertionError("different keys");
            for (Map.Entry<?, ?> e : ma.entrySet())
                requireEquivalent(e.getValue(), mb.get(e.getKey()));
        } else if (a instanceof java.util.List && b instanceof java.util.List) {
            java.util.List<?> la = (java.util.List<?>) a, lb = (java.util.List<?>) b;
            if (la.size() != lb.size()) throw new AssertionError("different list sizes");
            for (int i = 0; i < la.size(); i++) requireEquivalent(la.get(i), lb.get(i));
        } else if (a instanceof Number && b instanceof Number) {
            if (new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString())) != 0)
                throw new AssertionError("different numeric values: " + a + " / " + b);
        } else if (a == null || !a.equals(b)) {
            throw new AssertionError("different scalar values: " + a + " / " + b);
        }
    }

    private static Map<String, Object> nativeFastMap(String text, Class<?> target) {
        try (JSONReader r = JSONReader.of(text, FAST_NATIVE_CONTEXT)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) r.read(target);
            return value;
        }
    }

    private static Object handwrittenFast(String text) {
        try (JSONReader r = JSONReader.of(text, FAST_NATIVE_CONTEXT)) {
            return Fastjson2HandMapV2Reader.read(r);
        }
    }

    private static Object handwrittenGson(String text) throws IOException {
        try (JsonReader r = GSON_MATCHED.newJsonReader(new StringReader(text))) {
            return GsonHandMapReader.read(r);
        }
    }

    private static Object readFastRaw(String json) throws IOException {
        try (Fastjson2Parser p = FAST_BINDER.createParser(json)) {
            p.start();
            Object value = p.readRawObject();
            p.end();
            return value;
        }
    }
    private static Object readFastRaw(byte[] json) throws IOException {
        try (Fastjson2Parser p = FAST_BINDER.createParser(json)) {
            p.start();
            Object value = p.readRawObject();
            p.end();
            return value;
        }
    }
    private static Object nativeFastMap(byte[] data, Class<?> target) {
        try (JSONReader r = JSONReader.of(data, FAST_NATIVE_CONTEXT)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) r.read(target);
            return value;
        }
    }
    private static Object handwrittenFast(byte[] data) {
        try (JSONReader r = JSONReader.of(data, FAST_NATIVE_CONTEXT)) {
            return Fastjson2HandMapV2Reader.read(r);
        }
    }
    private static Object readGsonRaw(String json) throws IOException {
        try (GsonParser p = GSON_BINDER.createParser(json)) {
            p.start();
            Object value = p.readRawObject();
            p.end();
            return value;
        }
    }
    private static Object readGsonRaw(byte[] json) throws IOException {
        try (GsonParser p = GSON_BINDER.createParser(json)) {
            p.start();
            Object value = p.readRawObject();
            p.end();
            return value;
        }
    }
    private static Object handwrittenGson(byte[] json) throws IOException {
        try (JsonReader r = GSON_MATCHED.newJsonReader(new InputStreamReader(
                new ByteArrayInputStream(json), StandardCharsets.UTF_8))) {
            return GsonHandMapReader.read(r);
        }
    }

    @Benchmark
    public Object fastjsonCompiledString(Input f) throws IOException {
        return FAST_COMPILED.read(f.json);
    }

    @Benchmark
    public Object fastjsonCompiledUtf8(Input f) throws IOException {
        return FAST_COMPILED.readBytes(f.utf8);
    }

    @Benchmark
    public Object fastjsonRuntimeString(Input f) throws IOException {
        return FAST_BINDER.readNode(f.json, Map.class);
    }

    @Benchmark
    public Object fastjsonRuntimeUtf8(Input f) throws IOException {
        return FAST_BINDER.readNode(f.utf8, Map.class);
    }

    @Benchmark
    public Object fastjsonDirectRawString(Input f) throws IOException {
        return readFastRaw(f.json);
    }

    @Benchmark
    public Object fastjsonDirectRawUtf8(Input f) throws IOException {
        return readFastRaw(f.utf8);
    }

    @Benchmark
    public Object fastjsonNativeMapString(Input f) throws IOException {
        return nativeFastMap(f.json, Map.class);
    }

    @Benchmark
    public Object fastjsonNativeMapUtf8(Input f) throws IOException {
        return nativeFastMap(f.utf8, Map.class);
    }

    @Benchmark
    public Object fastjsonNativeLinkedString(Input f) throws IOException {
        return nativeFastMap(f.json, LinkedHashMap.class);
    }

    @Benchmark
    public Object fastjsonNativeLinkedUtf8(Input f) throws IOException {
        return nativeFastMap(f.utf8, LinkedHashMap.class);
    }

    @Benchmark
    public Object fastjsonHandwrittenString(Input f) throws IOException {
        return handwrittenFast(f.json);
    }

    @Benchmark
    public Object fastjsonHandwrittenUtf8(Input f) throws IOException {
        return handwrittenFast(f.utf8);
    }

    @Benchmark
    public Object gsonCompiledString(Input f) throws IOException {
        return GSON_COMPILED.read(f.json);
    }

    @Benchmark
    public Object gsonCompiledUtf8(Input f) throws IOException {
        return GSON_COMPILED.readBytes(f.utf8);
    }

    @Benchmark
    public Object gsonRuntimeString(Input f) throws IOException {
        return GSON_BINDER.readNode(f.json, Map.class);
    }

    @Benchmark
    public Object gsonRuntimeUtf8(Input f) throws IOException {
        return GSON_BINDER.readNode(f.utf8, Map.class);
    }

    @Benchmark
    public Object gsonDirectRawString(Input f) throws IOException {
        return readGsonRaw(f.json);
    }

    @Benchmark
    public Object gsonDirectRawUtf8(Input f) throws IOException {
        return readGsonRaw(f.utf8);
    }

    @Benchmark
    public Object gsonNativeDefaultString(Input f) throws IOException {
        return GSON_DEFAULT.fromJson(f.json, Map.class);
    }

    @Benchmark
    public Object gsonNativeDefaultUtf8(Input f) throws IOException {
        return GSON_DEFAULT.fromJson(new InputStreamReader(new ByteArrayInputStream(f.utf8), StandardCharsets.UTF_8), Map.class);
    }

    @Benchmark
    public Object gsonNativeMatchedString(Input f) throws IOException {
        return GSON_MATCHED.fromJson(f.json, Map.class);
    }

    @Benchmark
    public Object gsonNativeMatchedUtf8(Input f) throws IOException {
        return GSON_MATCHED.fromJson(new InputStreamReader(new ByteArrayInputStream(f.utf8), StandardCharsets.UTF_8), Map.class);
    }

    @Benchmark
    public Object gsonNativeLinkedString(Input f) throws IOException {
        return GSON_MATCHED.fromJson(f.json, LinkedHashMap.class);
    }

    @Benchmark
    public Object gsonNativeLinkedUtf8(Input f) throws IOException {
        return GSON_MATCHED.fromJson(new InputStreamReader(new ByteArrayInputStream(f.utf8), StandardCharsets.UTF_8), LinkedHashMap.class);
    }

    @Benchmark
    public Object gsonHandwrittenString(Input f) throws IOException {
        return handwrittenGson(f.json);
    }

    @Benchmark
    public Object gsonHandwrittenUtf8(Input f) throws IOException {
        return handwrittenGson(f.utf8);
    }

}
