package org.sjf4j.testbench.path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.path.JsonPath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Dynamic OBNT Find baseline. All roots and JsonPath expressions are prepared
 * once per trial; methods measure only traversal and result collection.
 *
 * <p>Keep this benchmark independent of PathFullBenchmark and
 * JsonPathBenchmark, including its own data model and queries.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
public class CompiledFindObntBenchmark {

    @CompiledNavigator
    public interface Compiled {
        @FindByPath("$.books[*].name.title")
        List<String> mapWildcard(Map<String, Object> root);

        @FindByPath("$.books..name.title")
        List<String> mapDescendant(Map<String, Object> root);

        @FindByPath("$.books[*].name.title")
        List<Object> externalWildcard(JsonNode root);

        @FindByPath("$.books..name.title")
        List<Object> externalDescendant(JsonNode root);
    }

    @CompiledNavigator(fallback = true)
    public interface CompiledFilter {
        @FindByPath("$.books[?(@.score >= $.limit)].name.title")
        List<Object> externalFilter(JsonNode root);
    }

    @State(Scope.Thread)
    public static class Data {
        @Param({"16", "128", "1024"})
        public int bookCount;

        public Map<String, Object> map;
        public JsonNode external;
        public Compiled compiled;
        public CompiledFilter compiledFilter;
        public JsonPath wildcard;
        public JsonPath descendant;
        public JsonPath filter;

        @Setup(Level.Trial)
        public void setup() {
            List<Object> books = new ArrayList<>(bookCount);
            for (int i = 0; i < bookCount; i++) {
                Map<String, Object> book = new LinkedHashMap<>();
                book.put("name", Map.of("title", "Book-" + i));
                book.put("score", i % 10);
                book.put("child", Map.of("name", Map.of("title", "Extra-" + i)));
                books.add(book);
            }
            map = new LinkedHashMap<>();
            map.put("limit", 5);
            map.put("books", books);
            external = new ObjectMapper().valueToTree(map);

            compiled = CompiledInstances.create(Compiled.class);
            compiledFilter = CompiledInstances.create(CompiledFilter.class);
            wildcard = JsonPath.parse("$.books[*].name.title");
            descendant = JsonPath.parse("$.books..name.title");
            filter = JsonPath.parse("$.books[?(@.score >= $.limit)].name.title");

            verify("map wildcard", wildcard.find(map), compiled.mapWildcard(map));
            verify("map descendant", descendant.find(map), compiled.mapDescendant(map));
            verify("external wildcard", wildcard.find(external),
                    compiled.externalWildcard(external));
            verify("external descendant", descendant.find(external),
                    compiled.externalDescendant(external));
            verify("external filter", filter.find(external),
                    compiledFilter.externalFilter(external));
        }

        private static void verify(String name, Object runtime, Object generated) {
            if (!Objects.equals(runtime, generated)) {
                throw new IllegalStateException(name + ": runtime=" + runtime
                        + ", compiled=" + generated);
            }
        }
    }

    @Benchmark
    public List<?> map_wildcard_runtime(Data data) {
        return data.wildcard.find(data.map);
    }

    @Benchmark
    public List<String> map_wildcard_compiled(Data data) {
        return data.compiled.mapWildcard(data.map);
    }

    @Benchmark
    public List<?> map_descendant_runtime(Data data) {
        return data.descendant.find(data.map);
    }

    @Benchmark
    public List<String> map_descendant_compiled(Data data) {
        return data.compiled.mapDescendant(data.map);
    }

    @Benchmark
    public List<?> external_wildcard_runtime(Data data) {
        return data.wildcard.find(data.external);
    }

    @Benchmark
    public List<Object> external_wildcard_compiled(Data data) {
        return data.compiled.externalWildcard(data.external);
    }

    @Benchmark
    public List<?> external_descendant_runtime(Data data) {
        return data.descendant.find(data.external);
    }

    @Benchmark
    public List<Object> external_descendant_compiled(Data data) {
        return data.compiled.externalDescendant(data.external);
    }

    @Benchmark
    public List<?> external_filter_runtime(Data data) {
        return data.filter.find(data.external);
    }

    @Benchmark
    public List<Object> external_filter_compiled(Data data) {
        return data.compiledFilter.externalFilter(data.external);
    }
}
