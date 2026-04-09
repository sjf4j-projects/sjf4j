package org.sjf4j;

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
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.path.JsonPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2)
@Threads(1)
@State(Scope.Thread)
public class JsonPathCompareBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{JsonPathCompareBenchmark.class.getName()});
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Configuration JAYWAY_JACKSON_CONFIG = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .build();

    private static final Configuration JAYWAY_MAP_LIST_CONFIG = Configuration.defaultConfiguration();

    private static final String BOOKSTORE_JSON = "{\n" +
            "  \"store\": {\n" +
            "    \"book\": [\n" +
            "      {\"category\":\"reference\",\"author\":\"Nigel Rees\",\"title\":\"Sayings of the Century\",\"price\":8.95,\"ratings\":[4,5,4]},\n" +
            "      {\"category\":\"fiction\",\"author\":\"Evelyn Waugh\",\"title\":\"Sword of Honour\",\"price\":12.99,\"ratings\":[5,4,5]},\n" +
            "      {\"category\":\"fiction\",\"author\":\"Herman Melville\",\"title\":\"Moby Dick\",\"isbn\":\"0-553-21311-3\",\"price\":8.99,\"ratings\":[5,5,4]},\n" +
            "      {\"category\":\"fiction\",\"author\":\"J. R. R. Tolkien\",\"title\":\"The Lord of the Rings\",\"isbn\":\"0-395-19395-8\",\"price\":22.99,\"ratings\":[5,5,5]},\n" +
            "      {\"category\":\"fiction\",\"author\":\"George Orwell\",\"title\":\"1984\",\"price\":9.99,\"ratings\":[4,4,5]}\n" +
            "    ],\n" +
            "    \"bicycle\": {\"color\":\"red\",\"price\":19.95},\n" +
            "    \"featured\": {\"price\":15.5,\"author\":\"staff\"}\n" +
            "  },\n" +
            "  \"expensive\": 10,\n" +
            "  \"warehouse\": {\"price\":1.25,\"bins\":[{\"price\":2.5},{\"price\":3.75}]}\n" +
            "}";

    @State(Scope.Thread)
    public static class CompileState {
        @Param({
                "$.store.book[1].price",
                "$.store.bicycle.color",
                "$.store.book[*].author",
                "$..price",
                "$.store.book[?(@.price > 10)].title",
                "$.store.book[0,2].title"
        })
        public String expr;
    }

    @State(Scope.Thread)
    public static class DefiniteQueryState {
        @Param({
                "$.store.book[1].price",
                "$.store.bicycle.color"
        })
        public String expr;

        public JsonNode document;
        public JsonPath sjf4jPath;
        public com.jayway.jsonpath.JsonPath jaywayPath;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            document = MAPPER.readTree(BOOKSTORE_JSON);
            sjf4jPath = JsonPath.compile(expr);
            jaywayPath = com.jayway.jsonpath.JsonPath.compile(expr);
            assertEquivalent(expr, runSjf4j(sjf4jPath, document, false), runJayway(jaywayPath, document, JAYWAY_JACKSON_CONFIG));
        }
    }

    @State(Scope.Thread)
    public static class IndefiniteQueryState {
        @Param({
                "$.store.book[*].author",
                "$..price",
                "$.store.book[?(@.price > 10)].title",
                "$.store.book[0,2].title"
        })
        public String expr;

        public JsonNode document;
        public JsonPath sjf4jPath;
        public com.jayway.jsonpath.JsonPath jaywayPath;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            document = MAPPER.readTree(BOOKSTORE_JSON);
            sjf4jPath = JsonPath.compile(expr);
            jaywayPath = com.jayway.jsonpath.JsonPath.compile(expr);
            assertEquivalent(expr, runSjf4j(sjf4jPath, document, true), runJayway(jaywayPath, document, JAYWAY_JACKSON_CONFIG));
        }
    }

    @State(Scope.Thread)
    public static class DefiniteMapListQueryState {
        @Param({
                "$.store.book[1].price",
                "$.store.bicycle.color"
        })
        public String expr;

        public Object document;
        public JsonPath sjf4jPath;
        public com.jayway.jsonpath.JsonPath jaywayPath;

        @Setup(Level.Trial)
        public void setup() {
            document = Sjf4j.global().fromJson(BOOKSTORE_JSON);
            sjf4jPath = JsonPath.compile(expr);
            jaywayPath = com.jayway.jsonpath.JsonPath.compile(expr);
            assertEquivalent(expr, runSjf4j(sjf4jPath, document, false), runJayway(jaywayPath, document, JAYWAY_MAP_LIST_CONFIG));
        }
    }

    @State(Scope.Thread)
    public static class IndefiniteMapListQueryState {
        @Param({
                "$.store.book[*].author",
                "$..price",
                "$.store.book[?(@.price > 10)].title",
                "$.store.book[0,2].title"
        })
        public String expr;

        public Object document;
        public JsonPath sjf4jPath;
        public com.jayway.jsonpath.JsonPath jaywayPath;

        @Setup(Level.Trial)
        public void setup() {
            document = Sjf4j.global().fromJson(BOOKSTORE_JSON);
            sjf4jPath = JsonPath.compile(expr);
            jaywayPath = com.jayway.jsonpath.JsonPath.compile(expr);
            assertEquivalent(expr, runSjf4j(sjf4jPath, document, true), runJayway(jaywayPath, document, JAYWAY_MAP_LIST_CONFIG));
        }
    }

    @State(Scope.Thread)
    public static class DefinitePojoQueryState {
        @Param({
                "$.store.book[1].price",
                "$.store.bicycle.color"
        })
        public String expr;

        public BookstorePojo document;
        public Object mapListDocument;
        public JsonPath sjf4jPath;

        @Setup(Level.Trial)
        public void setup() {
            document = Sjf4j.global().fromJson(BOOKSTORE_JSON, BookstorePojo.class);
            mapListDocument = Sjf4j.global().fromJson(BOOKSTORE_JSON);
            sjf4jPath = JsonPath.compile(expr);
            assertEquivalent(expr, runSjf4j(sjf4jPath, document, false), runSjf4j(sjf4jPath, mapListDocument, false));
        }
    }

    @State(Scope.Thread)
    public static class IndefinitePojoQueryState {
        @Param({
                "$.store.book[*].author",
                "$..price",
                "$.store.book[?(@.price > 10)].title",
                "$.store.book[0,2].title"
        })
        public String expr;

        public BookstorePojo document;
        public Object mapListDocument;
        public JsonPath sjf4jPath;

        @Setup(Level.Trial)
        public void setup() {
            document = Sjf4j.global().fromJson(BOOKSTORE_JSON, BookstorePojo.class);
            mapListDocument = Sjf4j.global().fromJson(BOOKSTORE_JSON);
            sjf4jPath = JsonPath.compile(expr);
            assertEquivalent(expr, runSjf4j(sjf4jPath, document, true), runSjf4j(sjf4jPath, mapListDocument, true));
        }
    }

    @State(Scope.Thread)
    public static class DefiniteJojoQueryState {
        @Param({
                "$.store.book[1].price",
                "$.store.bicycle.color"
        })
        public String expr;

        public BookstoreJojo document;
        public Object mapListDocument;
        public JsonPath sjf4jPath;

        @Setup(Level.Trial)
        public void setup() {
            document = Sjf4j.global().fromJson(BOOKSTORE_JSON, BookstoreJojo.class);
            mapListDocument = Sjf4j.global().fromJson(BOOKSTORE_JSON);
            sjf4jPath = JsonPath.compile(expr);
            assertEquivalent(expr, runSjf4j(sjf4jPath, document, false), runSjf4j(sjf4jPath, mapListDocument, false));
        }
    }

    @State(Scope.Thread)
    public static class IndefiniteJojoQueryState {
        @Param({
                "$.store.book[*].author",
                "$..price",
                "$.store.book[?(@.price > 10)].title",
                "$.store.book[0,2].title"
        })
        public String expr;

        public BookstoreJojo document;
        public Object mapListDocument;
        public JsonPath sjf4jPath;

        @Setup(Level.Trial)
        public void setup() {
            document = Sjf4j.global().fromJson(BOOKSTORE_JSON, BookstoreJojo.class);
            mapListDocument = Sjf4j.global().fromJson(BOOKSTORE_JSON);
            sjf4jPath = JsonPath.compile(expr);
            assertEquivalent(expr, runSjf4j(sjf4jPath, document, true), runSjf4j(sjf4jPath, mapListDocument, true));
        }
    }

    @State(Scope.Thread)
    public static class ParseAndQueryState {
        @Param({
                "$.store.book[1].price",
                "$.store.book[*].author",
                "$..price",
                "$.store.book[?(@.price > 10)].title"
        })
        public String expr;

        public boolean indefinite;
        public JsonPath sjf4jPath;
        public com.jayway.jsonpath.JsonPath jaywayPath;

        @Setup(Level.Trial)
        public void setup() {
            indefinite = isIndefinite(expr);
            sjf4jPath = JsonPath.compile(expr);
            jaywayPath = com.jayway.jsonpath.JsonPath.compile(expr);

            Object sjf4jDocument = Sjf4j.global().fromJson(BOOKSTORE_JSON);
            Object jaywayDocument = JAYWAY_MAP_LIST_CONFIG.jsonProvider().parse(BOOKSTORE_JSON);
            Object sjf4jResult = runSjf4j(sjf4jPath, sjf4jDocument, indefinite);
            Object jaywayResult = runJayway(jaywayPath, jaywayDocument, JAYWAY_MAP_LIST_CONFIG);
            assertEquivalent(expr, sjf4jResult, jaywayResult);
        }
    }

    @Benchmark
    public Object compile_sjf4j(CompileState state) {
        return JsonPath.compile(state.expr);
    }

    @Benchmark
    public Object compile_jayway(CompileState state) {
        return com.jayway.jsonpath.JsonPath.compile(state.expr);
    }

    @Benchmark
    public Object parse_only_sjf4j() {
        return Sjf4j.global().fromJson(BOOKSTORE_JSON);
    }

    @Benchmark
    public Object parse_only_jackson_plain() throws IOException {
        return MAPPER.readValue(BOOKSTORE_JSON, Object.class);
    }

    @Benchmark
    public Object parse_only_jackson_tree() throws IOException {
        return MAPPER.readTree(BOOKSTORE_JSON);
    }

    @Benchmark
    public Object parse_only_jayway() {
        return JAYWAY_MAP_LIST_CONFIG.jsonProvider().parse(BOOKSTORE_JSON);
    }

    // JsonNode
    @Benchmark
    public Object query_jsonnode_definite_sjf4j(DefiniteQueryState state) {
        return state.sjf4jPath.getNode(state.document);
    }

    @Benchmark
    public Object query_jsonnode_definite_jayway(DefiniteQueryState state) {
        return runJayway(state.jaywayPath, state.document, JAYWAY_JACKSON_CONFIG);
    }

    @Benchmark
    public Object query_jsonnode_indefinite_sjf4j(IndefiniteQueryState state) {
        return state.sjf4jPath.find(state.document);
    }

    @Benchmark
    public Object query_jsonnode_indefinite_jayway(IndefiniteQueryState state) {
        return runJayway(state.jaywayPath, state.document, JAYWAY_JACKSON_CONFIG);
    }

    @Benchmark
    public Object query_maplist_definite_sjf4j(DefiniteMapListQueryState state) {
        return runSjf4j(state.sjf4jPath, state.document, false);
    }

    @Benchmark
    public Object query_maplist_definite_jayway(DefiniteMapListQueryState state) {
        return runJayway(state.jaywayPath, state.document, JAYWAY_MAP_LIST_CONFIG);
    }

    @Benchmark
    public Object query_maplist_indefinite_sjf4j(IndefiniteMapListQueryState state) {
        return runSjf4j(state.sjf4jPath, state.document, true);
    }

    @Benchmark
    public Object query_maplist_indefinite_jayway(IndefiniteMapListQueryState state) {
        return runJayway(state.jaywayPath, state.document, JAYWAY_MAP_LIST_CONFIG);
    }

    @Benchmark
    public Object query_pojo_definite_sjf4j(DefinitePojoQueryState state) {
        return runSjf4j(state.sjf4jPath, state.document, false);
    }

    @Benchmark
    public Object query_pojo_indefinite_sjf4j(IndefinitePojoQueryState state) {
        return runSjf4j(state.sjf4jPath, state.document, true);
    }

    @Benchmark
    public Object query_jojo_definite_sjf4j(DefiniteJojoQueryState state) {
        return runSjf4j(state.sjf4jPath, state.document, false);
    }

    @Benchmark
    public Object query_jojo_indefinite_sjf4j(IndefiniteJojoQueryState state) {
        return runSjf4j(state.sjf4jPath, state.document, true);
    }

//    @Benchmark
//    public Object parse_and_query_sjf4j(ParseAndQueryState state) {
//        Object document = Sjf4j.global().fromJson(BOOKSTORE_JSON);
//        return runSjf4j(state.sjf4jPath, document, state.indefinite);
//    }
//
//    @Benchmark
//    public Object parse_and_query_jayway(ParseAndQueryState state) {
//        Object document = JAYWAY_MAP_LIST_CONFIG.jsonProvider().parse(BOOKSTORE_JSON);
//        return runJayway(state.jaywayPath, document, JAYWAY_MAP_LIST_CONFIG);
//    }

    private static boolean isIndefinite(String expr) {
        return expr.indexOf('*') >= 0 || expr.indexOf("..") >= 0 || expr.indexOf('?') >= 0;
    }

    private static Object runSjf4j(JsonPath path, Object document, boolean indefinite) {
        return indefinite ? path.find(document) : path.getNode(document);
    }

    private static Object runJayway(com.jayway.jsonpath.JsonPath path, Object document, Configuration config) {
        return path.read(document, config);
    }

    private static void assertEquivalent(String expr, Object left, Object right) {
        Object normalizedLeft = normalize(left);
        Object normalizedRight = normalize(right);
        if (!Objects.equals(normalizedLeft, normalizedRight)) {
            throw new IllegalStateException("JSONPath result mismatch for '" + expr + "': left=" +
                    normalizedLeft + ", right=" + normalizedRight);
        }
    }

    private static Object normalize(Object value) {
        if (value == null) return null;
        if (value instanceof JsonNode) {
            return MAPPER.convertValue(value, Object.class);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(normalize(item));
            }
            return out;
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<Object, Object> out = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(entry.getKey(), normalize(entry.getValue()));
            }
            return out;
        }
        return value;
    }

    public static class BookstorePojo {
        public StorePojo store;
        public Integer expensive;
        public WarehousePojo warehouse;
    }

    public static class StorePojo {
        public List<BookPojo> book;
        public BicyclePojo bicycle;
        public FeaturedPojo featured;
    }

    public static class BookPojo {
        public String category;
        public String author;
        public String title;
        public String isbn;
        public Double price;
        public List<Integer> ratings;
    }

    public static class BicyclePojo {
        public String color;
        public Double price;
    }

    public static class FeaturedPojo {
        public Double price;
        public String author;
    }

    public static class WarehousePojo {
        public Double price;
        public List<BinPojo> bins;
    }

    public static class BinPojo {
        public Double price;
    }

    public static class BookstoreJojo extends JsonObject {
        public StoreJojo store;
        public Integer expensive;
        public WarehouseJojo warehouse;
    }

    public static class StoreJojo extends JsonObject {
        public List<BookJojo> book;
        public BicycleJojo bicycle;
        public FeaturedJojo featured;
    }

    public static class BookJojo extends JsonObject {
        public String category;
        public String author;
        public String title;
        public String isbn;
        public Double price;
        public List<Integer> ratings;
    }

    public static class BicycleJojo extends JsonObject {
        public String color;
        public Double price;
    }

    public static class FeaturedJojo extends JsonObject {
        public Double price;
        public String author;
    }

    public static class WarehouseJojo extends JsonObject {
        public Double price;
        public List<BinJojo> bins;
    }

    public static class BinJojo extends JsonObject {
        public Double price;
    }
}
