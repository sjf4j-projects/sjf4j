package org.sjf4j.testbench.mapping;

import org.mapstruct.factory.Mappers;
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
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.MappingOptions;
import org.sjf4j.annotation.mapping.RawPolicy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Direct comparison of Java CAST and OBNT raw-value adaptation for three
 * fixed fields in Map/JsonObject-to-record projection.
 *
 * <p>Unlike wide-object mapping benchmarks, no unmapped payload size changes
 * are included: each invocation performs the same three key lookups and one
 * record construction. Both mapper implementations and equivalence checks
 * are prepared outside the measured path.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@Threads(1)
public class MappingRawPolicyBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args.length == 0
                ? new String[] {MappingRawPolicyBenchmark.class.getSimpleName()}
                : args);
    }

    @State(Scope.Thread)
    public static class RawState {
        public Map<String, Object> map;
        public JsonObject jsonObject;
        public MapStructMapper mapstruct;
        public Sjf4jMapper compiled;

        @Setup(Level.Trial)
        public void setup() {
            map = new LinkedHashMap<>();
            map.put("sku", "sku-123");
            map.put("name", "widget");
            map.put("count", 7);
            jsonObject = new JsonObject();
            jsonObject.put("sku", "sku-123");
            jsonObject.put("name", "widget");
            jsonObject.put("count", 7);

            mapstruct = Mappers.getMapper(MapStructMapper.class);
            compiled = CompiledInstances.create(Sjf4jMapper.class);
            JsonTarget expectedMap = mapstruct.map(map);
            JsonTarget expectedJson = mapstruct.json(jsonObject);
            if (!Objects.equals(expectedMap, expectedJson)
                    || !Objects.equals(expectedMap, compiled.mapCast(map))
                    || !Objects.equals(expectedMap, compiled.mapStrict(map))
                    || !Objects.equals(expectedMap, compiled.mapLenient(map))
                    || !Objects.equals(expectedJson, compiled.jsonCast(jsonObject))
                    || !Objects.equals(expectedJson, compiled.jsonStrict(jsonObject))
                    || !Objects.equals(expectedJson, compiled.jsonLenient(jsonObject))) {
                throw new AssertionError("Raw mapping policies disagree for compatible input");
            }
        }
    }

    @Benchmark public JsonTarget map_mapstruct(RawState s) { return s.mapstruct.map(s.map); }
    @Benchmark public JsonTarget map_cast(RawState s) { return s.compiled.mapCast(s.map); }
    @Benchmark public JsonTarget map_strict(RawState s) { return s.compiled.mapStrict(s.map); }
    @Benchmark public JsonTarget map_lenient(RawState s) { return s.compiled.mapLenient(s.map); }

    @Benchmark public JsonTarget json_mapstruct(RawState s) { return s.mapstruct.json(s.jsonObject); }
    @Benchmark public JsonTarget json_cast(RawState s) { return s.compiled.jsonCast(s.jsonObject); }
    @Benchmark public JsonTarget json_strict(RawState s) { return s.compiled.jsonStrict(s.jsonObject); }
    @Benchmark public JsonTarget json_lenient(RawState s) { return s.compiled.jsonLenient(s.jsonObject); }

    @CompiledMapper
    public interface Sjf4jMapper {
        @MappingOptions(raws = RawPolicy.CAST)
        JsonTarget mapCast(Map<String, Object> source);

        @MappingOptions(raws = RawPolicy.STRICT)
        JsonTarget mapStrict(Map<String, Object> source);

        @MappingOptions(raws = RawPolicy.LENIENT)
        JsonTarget mapLenient(Map<String, Object> source);

        @MappingOptions(raws = RawPolicy.CAST)
        JsonTarget jsonCast(JsonObject source);

        @MappingOptions(raws = RawPolicy.STRICT)
        JsonTarget jsonStrict(JsonObject source);

        @MappingOptions(raws = RawPolicy.LENIENT)
        JsonTarget jsonLenient(JsonObject source);
    }

    @org.mapstruct.Mapper
    public interface MapStructMapper {
        @org.mapstruct.Mapping(target = "sku", expression = "java((String) source.get(\"sku\"))")
        @org.mapstruct.Mapping(target = "name", expression = "java((String) source.get(\"name\"))")
        @org.mapstruct.Mapping(target = "count", expression = "java(source.get(\"count\"))")
        JsonTarget map(Map<String, Object> source);

        @org.mapstruct.Mapping(target = "sku", expression = "java((String) source.getNode(\"sku\"))")
        @org.mapstruct.Mapping(target = "name", expression = "java((String) source.getNode(\"name\"))")
        @org.mapstruct.Mapping(target = "count", expression = "java(source.getNode(\"count\"))")
        JsonTarget json(JsonObject source);
    }

    public record JsonTarget(String sku, String name, Object count) {}
}
