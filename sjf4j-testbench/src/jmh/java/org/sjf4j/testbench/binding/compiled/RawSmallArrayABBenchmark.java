package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Paired baseline/specialized A/B: -Dsjf4j.test.smallRawArray=false/true.
 * Native is an unchanged reference for detecting runner drift.
 * User Map input exercises nested tags[2] and friends[3] arrays.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class RawSmallArrayABBenchmark {

    @Param({"array0", "array1", "array2", "array3", "nested", "user"})
    public String shape;

    @Param({"string", "utf8"})
    public String encoding;

    private static final JSONReader.Context NATIVE_CONTEXT =
            JSONFactory.createReadContext(JSONReader.Feature.UseDoubleForDecimals);

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Compiled {
        @ReadFrom List<Object> readList(String input) throws IOException;
        @ReadFrom List<Object> readListBytes(byte[] input) throws IOException;
        @ReadFrom Map<String, Object> readMap(String input) throws IOException;
        @ReadFrom Map<String, Object> readMapBytes(byte[] input) throws IOException;
    }
    private static final Compiled COMPILED = CompiledInstances.create(Compiled.class);

    private String json;
    private byte[] bytes;
    private boolean isMap;
    private int size;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        switch (shape) {
            case "array0": json = "[]"; size = 0; break;
            case "array1": json = "[1]"; size = 1; break;
            case "array2": json = "[1,null]"; size = 2; break;
            case "array3": json = "[1,null,3]"; size = 3; break;
            case "nested": json = "[[1],[2,3],[],[4,5,6]]"; size = 4; break;
            case "user": json = User.JSON_DATA; size = 23; isMap = true; break;
            default: throw new IllegalArgumentException(shape);
        }
        bytes = json.getBytes(StandardCharsets.UTF_8);
        Object c = compiled();
        Object n = nativeControl();
        if (isMap) {
            Map<?, ?> cm = (Map<?, ?>) c, nm = (Map<?, ?>) n;
            if (cm.size() != size || nm.size() != size
                    || !cm.keySet().equals(nm.keySet())) {
                throw new AssertionError("Invalid User Map");
            }
            if (((List<?>) cm.get("tags")).size() != 2
                    || ((List<?>) cm.get("friends")).size() != 3) {
                throw new AssertionError("Invalid nested arrays");
            }
        } else {
            if (((List<?>) c).size() != size || ((List<?>) n).size() != size) {
                throw new AssertionError("Wrong array size");
            }
        }
    }

    @Benchmark
    public Object compiled() throws IOException {
        if (isMap) {
            return "utf8".equals(encoding)
                    ? COMPILED.readMapBytes(bytes) : COMPILED.readMap(json);
        }
        return "utf8".equals(encoding)
                ? COMPILED.readListBytes(bytes) : COMPILED.readList(json);
    }

    @Benchmark
    public Object nativeControl() {
        try (JSONReader r = "utf8".equals(encoding)
                ? JSONReader.of(bytes, NATIVE_CONTEXT) : JSONReader.of(json, NATIVE_CONTEXT)) {
            return isMap ? r.read(LinkedHashMap.class) : r.readArray();
        }
    }
}
