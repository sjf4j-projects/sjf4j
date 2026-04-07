package org.sjf4j;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.fastjson2.Fastjson2StreamingIO;

import java.lang.reflect.Type;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 6, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
public class Fastjson2ReadBreakdownBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{Fastjson2ReadBreakdownBenchmark.class.getName()});
    }

    private static final String JSON_FLAT =
            "{\"name\":\"Alice\",\"age\":30}";

    private static final String JSON_FLAT_UNKNOWN =
            "{\"name\":\"Alice\",\"age\":30,\"x1\":1,\"x2\":2,\"x3\":3,\"x4\":4,\"x5\":5}";

    private static final String JSON_NESTED =
            "{\"name\":\"Alice\",\"info\":{\"city\":\"Singapore\",\"zip\":\"018956\"}}";

    private static final String JSON_LIST =
            "{\"name\":\"Alice\",\"friends\":[{\"name\":\"Bill\",\"age\":20},{\"name\":\"Cindy\",\"age\":21},{\"name\":\"David\",\"age\":22}]}";

    private static final String JSON_MAP_FIELD =
            "{\"name\":\"Alice\",\"ext\":{\"k1\":1,\"k2\":true,\"k3\":[1,2,3],\"k4\":{\"a\":1,\"b\":2}}}";

    private static final String JSON_MAP_ROOT =
            "{\"k1\":1,\"k2\":true,\"k3\":[1,2,3],\"k4\":{\"a\":1,\"b\":2},\"k5\":\"hello\"}";

    private static final JSONReader.Context NATIVE_READER_CONTEXT = JSONFactory.createReadContext();
    private static final JSONReader.Context EXTRA_NOOP_CONTEXT = JSONFactory.createReadContext();
    private static final JSONReader.Context EXTRA_JSONOBJECT_ONLY_CONTEXT = JSONFactory.createReadContext();

    static {
        EXTRA_NOOP_CONTEXT.setExtraProcessor((object, key, value) -> {
        });
        EXTRA_JSONOBJECT_ONLY_CONTEXT.setExtraProcessor((object, key, value) -> {
            if (object instanceof JsonObject) {
                ((JsonObject) object).put(key, value);
            }
        });
    }

    @State(Scope.Thread)
    public static class S {
        Fastjson2JsonFacade plugin;

        @Setup(Level.Trial)
        public void setup() {
            plugin = new Fastjson2JsonFacade(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        }
    }

    private static Object readExclusive(String json, Type type) {
        try (JSONReader reader = JSONReader.of(json, NATIVE_READER_CONTEXT)) {
            return Fastjson2StreamingIO.readNode(reader, type);
        }
    }

    public static class FlatPojo {
        public String name;
        public int age;
    }

    public static class NestedPojo {
        public String name;
        public Info info;

        public static class Info {
            public String city;
            public String zip;
        }
    }

    public static class ListPojo {
        public String name;
        public List<Friend> friends;
    }

    public static class Friend {
        public String name;
        public int age;
    }

    public static class MapFieldPojo {
        public String name;
        public Map<String, Object> ext;
    }

    @Benchmark
    public Object native_flat() {
        return JSON.parseObject(JSON_FLAT, FlatPojo.class);
    }

    @Benchmark
    public Object facade_exclusive_flat(S s) {
        return readExclusive(JSON_FLAT, FlatPojo.class);
    }

    @Benchmark
    public Object facade_plugin_flat(S s) {
        return s.plugin.readNode(JSON_FLAT, FlatPojo.class);
    }

    @Benchmark
    public Object native_flat_unknown() {
        return JSON.parseObject(JSON_FLAT_UNKNOWN, FlatPojo.class);
    }

    @Benchmark
    public Object native_reader_flat_unknown() {
        try (JSONReader reader = JSONReader.of(JSON_FLAT_UNKNOWN, NATIVE_READER_CONTEXT)) {
            return reader.read(FlatPojo.class);
        }
    }

    @Benchmark
    public Object native_reader_flat_unknown_extra_noop() {
        try (JSONReader reader = JSONReader.of(JSON_FLAT_UNKNOWN, EXTRA_NOOP_CONTEXT)) {
            return reader.read(FlatPojo.class);
        }
    }

    @Benchmark
    public Object native_reader_flat_unknown_extra_jsonobject_only() {
        try (JSONReader reader = JSONReader.of(JSON_FLAT_UNKNOWN, EXTRA_JSONOBJECT_ONLY_CONTEXT)) {
            return reader.read(FlatPojo.class);
        }
    }

    @Benchmark
    public Object facade_exclusive_flat_unknown(S s) {
        return readExclusive(JSON_FLAT_UNKNOWN, FlatPojo.class);
    }

    @Benchmark
    public Object facade_plugin_flat_unknown(S s) {
        return s.plugin.readNode(JSON_FLAT_UNKNOWN, FlatPojo.class);
    }

    @Benchmark
    public Object native_nested() {
        return JSON.parseObject(JSON_NESTED, NestedPojo.class);
    }

    @Benchmark
    public Object facade_exclusive_nested(S s) {
        return readExclusive(JSON_NESTED, NestedPojo.class);
    }

    @Benchmark
    public Object facade_plugin_nested(S s) {
        return s.plugin.readNode(JSON_NESTED, NestedPojo.class);
    }

    @Benchmark
    public Object native_list() {
        return JSON.parseObject(JSON_LIST, ListPojo.class);
    }

    @Benchmark
    public Object facade_exclusive_list(S s) {
        return readExclusive(JSON_LIST, ListPojo.class);
    }

    @Benchmark
    public Object facade_plugin_list(S s) {
        return s.plugin.readNode(JSON_LIST, ListPojo.class);
    }

    @Benchmark
    public Object native_map_field() {
        return JSON.parseObject(JSON_MAP_FIELD, MapFieldPojo.class);
    }

    @Benchmark
    public Object facade_exclusive_map_field(S s) {
        return readExclusive(JSON_MAP_FIELD, MapFieldPojo.class);
    }

    @Benchmark
    public Object facade_plugin_map_field(S s) {
        return s.plugin.readNode(JSON_MAP_FIELD, MapFieldPojo.class);
    }

    @Benchmark
    public Object native_map_root() {
        return JSON.parseObject(JSON_MAP_ROOT, Map.class);
    }

    @Benchmark
    public Object facade_exclusive_map_root(S s) {
        return readExclusive(JSON_MAP_ROOT, Map.class);
    }

    @Benchmark
    public Object facade_plugin_map_root(S s) {
        return s.plugin.readNode(JSON_MAP_ROOT, Map.class);
    }
}
