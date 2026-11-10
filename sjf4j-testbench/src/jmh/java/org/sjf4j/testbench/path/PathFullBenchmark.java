package org.sjf4j.testbench.path;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
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
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.path.JsonPath;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Read-only path comparison on identical prebuilt Bookstore instances.
 * Both engines use precompiled expressions; JSON parsing and setup are excluded.
 * The JMH entry points are separate so a per-operation dispatch does not inflate
 * the very small direct-access cost.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@Threads(1)
public class PathFullBenchmark {
    private static final ObjectMapper JACKSON = new ObjectMapper();
    private static final Configuration JAYWAY_MAP_CONFIG = Configuration.defaultConfiguration();
    private static final Configuration JAYWAY_NODE_CONFIG = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .build();

    private static final String[] QUERIES = {
            "$.store.book[1].price",
            "$.store.book[*].author",
            "$.store.book[?(@.price > 10)].title",
            "$..price",
            "$.store.book[1:4].title"
    };

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{PathFullBenchmark.class.getName()});
    }

    // Keep the Full baseline self-contained: do not reuse JsonPathBenchmark
    // states or parsing methods, even when comparing the same expressions.
    @State(Scope.Thread)
    public static class ParseData {
        @Param({
                "$.store.book[1].price",
                "$.store.bicycle.color",
                "$.store.book[*].author",
                "$..price",
                "$.store.book[?(@.price > 10)].title",
                "$.store.book[0,2].title",
                "$.store.book[1:4].title"
        })
        public String expr;
    }

    @Benchmark
    public JsonPath parse_sjf4j(ParseData data) {
        return JsonPath.parse(data.expr);
    }

    @Benchmark
    public com.jayway.jsonpath.JsonPath parse_jayway(ParseData data) {
        return com.jayway.jsonpath.JsonPath.compile(data.expr);
    }

    public static class Bookstore {
        public Store store;
    }

    public static class Store {
        public List<Book> book;
    }

    public static class Book {
        public String category;
        public String author;
        public String title;
        public Double price;
    }

    @CompiledNavigator(fallback = true)
    public interface PojoPaths {
        @GetByPath("$.store.book[1].price")
        Double direct(Bookstore root);

        @FindByPath("$.store.book[*].author")
        List<String> wildcard(Bookstore root);

        // Filter uses a cached FilterExpr; descendant traversal is generated.
        @FindByPath("$.store.book[?(@.price > 10)].title")
        List<String> filter(Bookstore root);

        @FindByPath("$..price")
        List<Object> descendant(Bookstore root);

        @FindByPath("$.store.book[1:4].title")
        List<String> slice(Bookstore root);
    }

    // Object-typed Map values and external JsonNode shapes use compiled OBNT
    // dispatch. Only the cached FilterExpr predicate requires fallback=true.
    @CompiledNavigator(fallback = true)
    public interface MapPaths {
        @GetByPath("$.store.book[1].price")
        Object direct(Map<String, Object> root);

        @FindByPath("$.store.book[*].author")
        List<Object> wildcard(Map<String, Object> root);

        @FindByPath("$.store.book[?(@.price > 10)].title")
        List<Object> filter(Map<String, Object> root);

        @FindByPath("$..price")
        List<Object> descendant(Map<String, Object> root);

        @FindByPath("$.store.book[1:4].title")
        List<Object> slice(Map<String, Object> root);
    }

    @CompiledNavigator(fallback = true)
    public interface JsonNodePaths {
        @GetByPath("$.store.book[1].price")
        Object direct(JsonNode root);

        @FindByPath("$.store.book[*].author")
        List<Object> wildcard(JsonNode root);

        @FindByPath("$.store.book[?(@.price > 10)].title")
        List<Object> filter(JsonNode root);

        @FindByPath("$..price")
        List<Object> descendant(JsonNode root);

        @FindByPath("$.store.book[1:4].title")
        List<Object> slice(JsonNode root);
    }

    @State(Scope.Thread)
    public static class Data {
        @Param({"1", "10", "100"})
        public int sizeKb;

        public Bookstore pojo;
        public Map<String, Object> map;
        public JsonNode jsonNode;
        public PojoPaths compiledPojo;
        public MapPaths compiledMap;
        public JsonNodePaths compiledJsonNode;
        public JsonPath[] sjf4jPaths;
        public com.jayway.jsonpath.JsonPath[] jaywayPaths;
        public int actualBytes;
        public int bookCount;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            int targetBytes = sizeKb * 1024;
            int count = Math.max(5, targetBytes / 100);
            byte[] bytes;
            for (int attempt = 0; ; attempt++) {
                pojo = fixture(count);
                bytes = JACKSON.writeValueAsBytes(pojo);
                if (attempt >= 5 || Math.abs(bytes.length - targetBytes) <= targetBytes / 20) {
                    break;
                }
                int next = Math.max(5, (int) Math.round(count * ((double) targetBytes / bytes.length)));
                if (next == count) break;
                count = next;
            }
            actualBytes = bytes.length;
            bookCount = count;
            map = JACKSON.readValue(bytes, new TypeReference<Map<String, Object>>() {});
            jsonNode = JACKSON.readTree(bytes);
            compiledPojo = CompiledInstances.create(PojoPaths.class);
            compiledMap = CompiledInstances.create(MapPaths.class);
            compiledJsonNode = CompiledInstances.create(JsonNodePaths.class);
            sjf4jPaths = new JsonPath[QUERIES.length];
            jaywayPaths = new com.jayway.jsonpath.JsonPath[QUERIES.length];
            for (int index = 0; index < QUERIES.length; index++) {
                String query = QUERIES[index];
                sjf4jPaths[index] = JsonPath.parse(query);
                jaywayPaths[index] = com.jayway.jsonpath.JsonPath.compile(query);

                boolean multi = index != 0;
                Object jaywayMap = jaywayPaths[index].read(map, JAYWAY_MAP_CONFIG);
                Object jaywayNode = jaywayPaths[index].read(jsonNode, JAYWAY_NODE_CONFIG);
                Object runtimeMap = runtime(sjf4jPaths[index], map, multi);
                Object runtimeNode = runtime(sjf4jPaths[index], jsonNode, multi);
                Object runtimePojo = runtime(sjf4jPaths[index], pojo, multi);
                assertSame(query + " Map/Jayway-Runtime", jaywayMap, runtimeMap);
                assertSame(query + " JsonNode/Jayway-Runtime", jaywayNode, runtimeNode);
                assertSame(query + " Map/Compiled", runtimeMap, compiledMap(index));
                assertSame(query + " JsonNode/Compiled", runtimeNode, compiledJsonNode(index));
                assertSame(query + " POJO/Runtime-Map", runtimeMap, runtimePojo);
                assertSame(query + " POJO/Compiled", runtimePojo, compiledPojo(index));
            }
            System.out.printf("Path fixture %dKB: %d JSON bytes, %d books, filter matches %d%n",
                    sizeKb, actualBytes, bookCount, (bookCount / 10) * 3 + Math.min(3, bookCount % 10));
        }

        private Object compiledMap(int index) {
            switch (index) {
                case 0: return compiledMap.direct(map);
                case 1: return compiledMap.wildcard(map);
                case 2: return compiledMap.filter(map);
                case 3: return compiledMap.descendant(map);
                case 4: return compiledMap.slice(map);
                default: throw new AssertionError(index);
            }
        }

        private Object compiledJsonNode(int index) {
            switch (index) {
                case 0: return compiledJsonNode.direct(jsonNode);
                case 1: return compiledJsonNode.wildcard(jsonNode);
                case 2: return compiledJsonNode.filter(jsonNode);
                case 3: return compiledJsonNode.descendant(jsonNode);
                case 4: return compiledJsonNode.slice(jsonNode);
                default: throw new AssertionError(index);
            }
        }

        private Object compiledPojo(int index) {
            switch (index) {
                case 0: return compiledPojo.direct(pojo);
                case 1: return compiledPojo.wildcard(pojo);
                case 2: return compiledPojo.filter(pojo);
                case 3: return compiledPojo.descendant(pojo);
                case 4: return compiledPojo.slice(pojo);
                default: throw new AssertionError(index);
            }
        }

    }

    private static Bookstore fixture(int count) {
        Bookstore root = new Bookstore();
        root.store = new Store();
        root.store.book = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Book book = new Book();
            book.category = (i % 2 == 0) ? "fiction" : "reference";
            book.author = "Author-" + String.format("%06d", i);
            book.title = "Book-" + String.format("%06d", i);
            // Exactly 30% pass the filter over complete groups of ten.
            book.price = (i % 10 < 3) ? 20.5 : 5.5;
            root.store.book.add(book);
        }
        return root;
    }

    private static Object runtime(JsonPath path, Object document, boolean multi) {
        return multi ? path.find(document) : path.getNode(document);
    }

    private static void assertSame(String title, Object expected, Object actual) {
        Object a = normalize(expected);
        Object b = normalize(actual);
        if (!Objects.equals(a, b)) {
            throw new IllegalStateException("Path mismatch (" + title + "): " + a + " != " + b);
        }
    }

    private static Object normalize(Object value) {
        if (value instanceof JsonNode) return normalize(JACKSON.convertValue(value, Object.class));
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue()).stripTrailingZeros();
        }
        if (value instanceof List<?>) {
            List<?> values = (List<?>) value;
            List<Object> output = new ArrayList<>(values.size());
            for (Object item : values) output.add(normalize(item));
            return output;
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> values = (Map<?, ?>) value;
            Map<Object, Object> output = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                output.put(entry.getKey(), normalize(entry.getValue()));
            }
            return output;
        }
        return value;
    }

    // map: prebuilt document, precompiled query.

    @Benchmark
    public Object map_direct_jayway(Data s) {
        return s.jaywayPaths[0].read(s.map, JAYWAY_MAP_CONFIG);
    }

    @Benchmark
    public Object map_direct_runtime(Data s) {
        return runtime(s.sjf4jPaths[0], s.map, false);
    }

    @Benchmark
    public Object map_direct_compiled(Data s) {
        return s.compiledMap.direct(s.map);
    }

    @Benchmark
    public Object map_wildcard_jayway(Data s) {
        return s.jaywayPaths[1].read(s.map, JAYWAY_MAP_CONFIG);
    }

    @Benchmark
    public Object map_wildcard_runtime(Data s) {
        return runtime(s.sjf4jPaths[1], s.map, true);
    }

    @Benchmark
    public Object map_wildcard_compiled(Data s) {
        return s.compiledMap.wildcard(s.map);
    }


    @Benchmark
    public Object map_filter_jayway(Data s) {
        return s.jaywayPaths[2].read(s.map, JAYWAY_MAP_CONFIG);
    }

    @Benchmark
    public Object map_filter_runtime(Data s) {
        return runtime(s.sjf4jPaths[2], s.map, true);
    }

    @Benchmark
    public Object map_filter_compiled(Data s) {
        return s.compiledMap.filter(s.map);
    }


    @Benchmark
    public Object map_descendant_jayway(Data s) {
        return s.jaywayPaths[3].read(s.map, JAYWAY_MAP_CONFIG);
    }

    @Benchmark
    public Object map_descendant_runtime(Data s) {
        return runtime(s.sjf4jPaths[3], s.map, true);
    }

    @Benchmark
    public Object map_descendant_compiled(Data s) {
        return s.compiledMap.descendant(s.map);
    }


    @Benchmark
    public Object map_slice_jayway(Data s) {
        return s.jaywayPaths[4].read(s.map, JAYWAY_MAP_CONFIG);
    }

    @Benchmark
    public Object map_slice_runtime(Data s) {
        return runtime(s.sjf4jPaths[4], s.map, true);
    }

    @Benchmark
    public Object map_slice_compiled(Data s) {
        return s.compiledMap.slice(s.map);
    }


    // jsonnode: prebuilt document, precompiled query.

    @Benchmark
    public Object jsonnode_direct_jayway(Data s) {
        return s.jaywayPaths[0].read(s.jsonNode, JAYWAY_NODE_CONFIG);
    }

    @Benchmark
    public Object jsonnode_direct_runtime(Data s) {
        return runtime(s.sjf4jPaths[0], s.jsonNode, false);
    }

    @Benchmark
    public Object jsonnode_direct_compiled(Data s) {
        return s.compiledJsonNode.direct(s.jsonNode);
    }

    @Benchmark
    public Object jsonnode_wildcard_jayway(Data s) {
        return s.jaywayPaths[1].read(s.jsonNode, JAYWAY_NODE_CONFIG);
    }

    @Benchmark
    public Object jsonnode_wildcard_runtime(Data s) {
        return runtime(s.sjf4jPaths[1], s.jsonNode, true);
    }

    @Benchmark
    public Object jsonnode_wildcard_compiled(Data s) {
        return s.compiledJsonNode.wildcard(s.jsonNode);
    }

    @Benchmark
    public Object jsonnode_filter_jayway(Data s) {
        return s.jaywayPaths[2].read(s.jsonNode, JAYWAY_NODE_CONFIG);
    }

    @Benchmark
    public Object jsonnode_filter_runtime(Data s) {
        return runtime(s.sjf4jPaths[2], s.jsonNode, true);
    }

    @Benchmark
    public Object jsonnode_filter_compiled(Data s) {
        return s.compiledJsonNode.filter(s.jsonNode);
    }

    @Benchmark
    public Object jsonnode_descendant_jayway(Data s) {
        return s.jaywayPaths[3].read(s.jsonNode, JAYWAY_NODE_CONFIG);
    }

    @Benchmark
    public Object jsonnode_descendant_runtime(Data s) {
        return runtime(s.sjf4jPaths[3], s.jsonNode, true);
    }

    @Benchmark
    public Object jsonnode_descendant_compiled(Data s) {
        return s.compiledJsonNode.descendant(s.jsonNode);
    }

    @Benchmark
    public Object jsonnode_slice_jayway(Data s) {
        return s.jaywayPaths[4].read(s.jsonNode, JAYWAY_NODE_CONFIG);
    }

    @Benchmark
    public Object jsonnode_slice_runtime(Data s) {
        return runtime(s.sjf4jPaths[4], s.jsonNode, true);
    }

    @Benchmark
    public Object jsonnode_slice_compiled(Data s) {
        return s.compiledJsonNode.slice(s.jsonNode);
    }

    // pojo: prebuilt document, precompiled query.

    @Benchmark
    public Object pojo_direct_runtime(Data s) {
        return runtime(s.sjf4jPaths[0], s.pojo, false);
    }

    @Benchmark
    public Object pojo_direct_compiled(Data s) {
        return s.compiledPojo.direct(s.pojo);
    }

    @Benchmark
    public Object pojo_wildcard_runtime(Data s) {
        return runtime(s.sjf4jPaths[1], s.pojo, true);
    }

    @Benchmark
    public Object pojo_wildcard_compiled(Data s) {
        return s.compiledPojo.wildcard(s.pojo);
    }

    @Benchmark
    public Object pojo_filter_runtime(Data s) {
        return runtime(s.sjf4jPaths[2], s.pojo, true);
    }

    @Benchmark
    public Object pojo_filter_compiled(Data s) {
        return s.compiledPojo.filter(s.pojo);
    }

    @Benchmark
    public Object pojo_descendant_runtime(Data s) {
        return runtime(s.sjf4jPaths[3], s.pojo, true);
    }

    @Benchmark
    public Object pojo_descendant_compiled(Data s) {
        return s.compiledPojo.descendant(s.pojo);
    }

    @Benchmark
    public Object pojo_slice_runtime(Data s) {
        return runtime(s.sjf4jPaths[4], s.pojo, true);
    }

    @Benchmark
    public Object pojo_slice_compiled(Data s) {
        return s.compiledPojo.slice(s.pojo);
    }

}
