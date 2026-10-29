package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.binding.StreamingParser;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * List<Map<String,Object>> diagnostic. Each map has the same field layout.
 * Compare native, compiled typed, runtime typed, raw array and two initial
 * LinkedHashMap capacities. Manual capacity variants are not production code.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class JacksonListMapExperiment {
    @Param({"j2", "j3"}) public String backend;
    @Param({"4", "24"}) public int items;
    @Param({"2", "8"}) public int fields;

    private static final Type LIST_MAP_TYPE =
            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}.getType();
    private final ObjectMapper native2 = new ObjectMapper();
    private final JsonMapper native3 = new JsonMapper();
    private Jackson2Binder binder2;
    private Jackson3Binder binder3;
    private Compiled2 compiled2;
    private Compiled3 compiled3;
    private String json;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        binder2 = new Jackson2Binder(native2.getFactory());
        binder3 = new Jackson3Binder((tools.jackson.core.json.JsonFactory) native3.tokenStreamFactory());
        compiled2 = CompiledInstances.create(Compiled2.class, binder2);
        compiled3 = CompiledInstances.create(Compiled3.class, binder3);
        StringBuilder s = new StringBuilder("[");
        for (int i = 0; i < items; ++i) {
            if (i != 0) s.append(',');
            s.append('{');
            for (int j = 0; j < fields; ++j) {
                if (j != 0) s.append(',');
                s.append('"').append('k').append(j).append('"').append(':');
                switch (j % 4) {
                    case 0: s.append(i + j); break;
                    case 1: s.append('"').append('v').append(i).append('"'); break;
                    case 2: s.append("[1,null,3]"); break;
                    default: s.append("{\"nested\":true}"); break;
                }
            }
            s.append('}');
        }
        json = s.append(']').toString();
        Object expected = readNative();
        for (Object actual : new Object[] {
                compiled(), runtimeTyped(), rawArray(), manualCapacity4(), manualCapacity16()
        }) {
            if (!expected.equals(actual)) {
                throw new IllegalStateException("List<Map> graph differs: " + actual);
            }
        }
    }

    private StreamingParser parser() throws IOException {
        return "j2".equals(backend) ? binder2.createParser(json) : binder3.createParser(json);
    }

    @Benchmark public Object nativeDeserialize() throws Exception { return readNative(); }

    private Object readNative() throws Exception {
        return "j2".equals(backend) ? native2.readValue(json, List.class)
                : native3.readValue(json, List.class);
    }

    @Benchmark public Object compiled() throws IOException {
        return "j2".equals(backend) ? compiled2.maps(json) : compiled3.maps(json);
    }

    @Benchmark public Object runtimeTyped() {
        return "j2".equals(backend) ? binder2.readNode(json, LIST_MAP_TYPE)
                : binder3.readNode(json, LIST_MAP_TYPE);
    }

    @Benchmark public Object rawArray() throws IOException {
        try (StreamingParser p = parser()) {
            p.start();
            Object result = p.readRawArray();
            p.end();
            return result;
        }
    }

    @Benchmark public Object manualCapacity4() throws IOException {
        try (StreamingParser p = parser()) {
            return readMaps(p, 4);
        }
    }

    @Benchmark public Object manualCapacity16() throws IOException {
        try (StreamingParser p = parser()) {
            return readMaps(p, 16);
        }
    }

    private static List<Map<String,Object>> readMaps(StreamingParser p, int capacity)
            throws IOException {
        p.start();
        p.readArrayStart();
        List<Map<String,Object>> result = new ArrayList<>();
        while (!p.nextIfArrayEnd()) {
            p.readObjectStart();
            Map<String,Object> map = new LinkedHashMap<>(capacity);
            String name;
            while ((name = p.nextName()) != null) {
                map.put(name, p.readRaw());
            }
            result.add(map);
        }
        p.end();
        return result;
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Compiled2 {
        @ReadFrom List<Map<String,Object>> maps(String json) throws IOException;
    }
    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Compiled3 {
        @ReadFrom List<Map<String,Object>> maps(String json) throws IOException;
    }
}
