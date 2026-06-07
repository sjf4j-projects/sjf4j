package org.sjf4j.jdk17;

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
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.path.CompiledPath;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.path.JsonPath;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Compares annotation-processor generated {@link FindByPath} methods against
 * runtime {@link JsonPath#find(Object)} over the {@code JsonPathBenchmark}
 * bookstore data shape.
 *
 * <pre>{@code
 * ./gradlew :sjf4j-jdk17-test:jmh
 * }</pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
public class CompiledFindBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{
                CompiledFindBenchmark.class.getSimpleName()
        });
    }

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

    public static class Bookstore {
        public Store store;
        public Integer expensive;
        public Warehouse warehouse;
    }

    public static class Store {
        public List<Book> book;
        public Bicycle bicycle;
        public Featured featured;
    }

    public static class Book {
        public String category;
        public String author;
        public String title;
        public String isbn;
        public Double price;
        public List<Integer> ratings;
    }

    public static class Bicycle {
        public String color;
        public Double price;
    }

    public static class Featured {
        public Double price;
        public String author;
    }

    public static class Warehouse {
        public Double price;
        public List<Bin> bins;
    }

    public static class Bin {
        public Double price;
    }

    @CompiledPath
    public interface FindNodes {
        @FindByPath("$.store.book[*].author")
        List<String> authors(Bookstore root);

        @FindByPath(value = "$.store.book[?(@.price > 10)].title", allowFallback = true)
        List<String> expensiveTitles(Bookstore root);

        @FindByPath("$.store.book[0,2].title")
        List<String> unionTitles(Bookstore root);

        @FindByPath("$.store.book[1:4].title")
        List<String> sliceTitles(Bookstore root);

        @FindByPath(value = "$.store..price", allowFallback = true)
        List<Object> storePrices(Bookstore root);
    }

    @State(Scope.Thread)
    public static class FindState {
        public Bookstore pojo;
        public FindNodes compiled;
        public JsonPath authorsPath;
        public JsonPath expensiveTitlesPath;
        public JsonPath unionTitlesPath;
        public JsonPath sliceTitlesPath;
        public JsonPath storePricesPath;

        @Setup(Level.Trial)
        public void setup() {
            pojo = Sjf4j.global().fromJson(BOOKSTORE_JSON, Bookstore.class);
            compiled = CompiledNodes.of(FindNodes.class);
            authorsPath = JsonPath.parse("$.store.book[*].author");
            expensiveTitlesPath = JsonPath.parse("$.store.book[?(@.price > 10)].title");
            unionTitlesPath = JsonPath.parse("$.store.book[0,2].title");
            sliceTitlesPath = JsonPath.parse("$.store.book[1:4].title");
            storePricesPath = JsonPath.parse("$.store..price");

            assertEqual("authors", compiled.authors(pojo), authorsPath.find(pojo));
            assertEqual("expensiveTitles", compiled.expensiveTitles(pojo), expensiveTitlesPath.find(pojo));
            assertEqual("unionTitles", compiled.unionTitles(pojo), unionTitlesPath.find(pojo));
            assertEqual("sliceTitles", compiled.sliceTitles(pojo), sliceTitlesPath.find(pojo));
            assertEqual("storePrices", compiled.storePrices(pojo), storePricesPath.find(pojo));
        }
    }

    @Benchmark
    public List<String> find_authors_compiled(FindState s) {
        return s.compiled.authors(s.pojo);
    }

    @Benchmark
    public List<?> find_authors_jsonpath(FindState s) {
        return s.authorsPath.find(s.pojo);
    }

    @Benchmark
    public List<String> find_filter_titles_compiled(FindState s) {
        return s.compiled.expensiveTitles(s.pojo);
    }

    @Benchmark
    public List<?> find_filter_titles_jsonpath(FindState s) {
        return s.expensiveTitlesPath.find(s.pojo);
    }

    @Benchmark
    public List<String> find_union_titles_compiled(FindState s) {
        return s.compiled.unionTitles(s.pojo);
    }

    @Benchmark
    public List<?> find_union_titles_jsonpath(FindState s) {
        return s.unionTitlesPath.find(s.pojo);
    }

    @Benchmark
    public List<String> find_slice_titles_compiled(FindState s) {
        return s.compiled.sliceTitles(s.pojo);
    }

    @Benchmark
    public List<?> find_slice_titles_jsonpath(FindState s) {
        return s.sliceTitlesPath.find(s.pojo);
    }

    @Benchmark
    public List<Object> find_descendant_prices_compiled(FindState s) {
        return s.compiled.storePrices(s.pojo);
    }

    @Benchmark
    public List<?> find_descendant_prices_jsonpath(FindState s) {
        return s.storePricesPath.find(s.pojo);
    }

    private static void assertEqual(String name, Object compiled, Object jsonPath) {
        if (!Objects.equals(compiled, jsonPath)) {
            throw new IllegalStateException(name + " mismatch: compiled=" + compiled + ", jsonPath=" + jsonPath);
        }
    }
}
