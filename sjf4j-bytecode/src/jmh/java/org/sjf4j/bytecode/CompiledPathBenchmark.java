package org.sjf4j.bytecode;

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
import org.sjf4j.compiled.CompiledPath;
import org.sjf4j.compiled.FallbackCompiledPath;
import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark: bytecode-optimized CompiledPath vs. native access vs. fallback vs. raw JsonPath.
 *
 * <p>Tests POJO property chains on a deserialized bookstore model.
 * Expected result: bytecode-optimized get() is 5-20x faster than the
 * fallback because it replaces runtime segment iteration + reflective
 * dispatch with direct INVOKEVIRTUAL instructions.
 *
 * <p>Run:
 * <pre>{@code
 *   ./gradlew :sjf4j-bytecode:jmh
 * }</pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class CompiledPathBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{
                CompiledPathBenchmark.class.getSimpleName()
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

    // ═══════════════════════════════════════════════════════
    //  内联 POJO 类型（与 JSON 结构匹配）
    // ═══════════════════════════════════════════════════════

    public static class Store {
        public Bicycle bicycle;
        public List<Book> book;
        public Featured featured;
    }
    public static class Bicycle {
        public String color;
        public Double price;
    }
    public static class Book {
        public String title;
        public Double price;
    }
    public static class Featured {
        public Double price;
    }
    public static class Root {
        public Store store;
        public Integer expensive;
    }


    @State(Scope.Thread)
    public static class BenchmarkState {
        public Root pojo;
        public Object mapList;

        // $.store.bicycle.color
        public JsonPath colorRaw;
        public FallbackCompiledPath<Root, String> colorFallback;
        public CompiledPath<Root, String> colorAsm;

        // $.store.bicycle.price
        public JsonPath priceRaw;
        public FallbackCompiledPath<Root, Double> priceFallback;
        public CompiledPath<Root, Double> priceAsm;

        // $.store.book[1].price
        public JsonPath bookPriceRaw;
        public FallbackCompiledPath<Root, Double> bookPriceFallback;
        public CompiledPath<Root, Double> bookPriceAsm;

        // 空的 Root 对象（中间字段全 null）
        public Root emptyRoot;

        @Setup(Level.Trial)
        public void setup() {
            pojo = Sjf4j.global().fromJson(BOOKSTORE_JSON, Root.class);
            mapList = Sjf4j.global().fromJson(BOOKSTORE_JSON);

            // $.store.bicycle.color
            colorRaw = JsonPath.parse("$.store.bicycle.color");
            colorFallback = new FallbackCompiledPath<>(colorRaw, Root.class, String.class);
            colorAsm = CompiledPath.compile("$.store.bicycle.color", Root.class, String.class);

            // $.store.bicycle.price
            priceRaw = JsonPath.parse("$.store.bicycle.price");
            priceFallback = new FallbackCompiledPath<>(priceRaw, Root.class, Double.class);
            priceAsm = CompiledPath.compile("$.store.bicycle.price", Root.class, Double.class);

            // $.store.book[1].price
            bookPriceRaw = JsonPath.parse("$.store.book[1].price");
            bookPriceFallback = new FallbackCompiledPath<>(bookPriceRaw, Root.class, Double.class);
            bookPriceAsm = CompiledPath.compile("$.store.book[1].price", Root.class, Double.class);

            // 空对象
            emptyRoot = new Root();

            // 验证一致性
            String cOpt = colorAsm.get(pojo);
            String cFb = colorFallback.get(pojo);
            String cRaw = (String) colorRaw.getNode(pojo);
            if (!"red".equals(cOpt) || !cOpt.equals(cFb) || !cFb.equals(cRaw)) {
                throw new AssertionError("color mismatch");
            }
            Double pOpt = priceAsm.get(pojo);
            Double pFb = priceFallback.get(pojo);
            Double pRaw = (Double) priceRaw.getNode(pojo);
            if (Math.abs(pOpt - 19.95) > 0.001 || Math.abs(pOpt - pFb) > 0.001 || Math.abs(pFb - pRaw) > 0.001) {
                throw new AssertionError("price mismatch");
            }
            Double bpOpt = bookPriceAsm.get(pojo);
            Double bpFb = bookPriceFallback.get(pojo);
            Double bpRaw = (Double) bookPriceRaw.getNode(pojo);
            if (Math.abs(bpOpt - 12.99) > 0.001 || Math.abs(bpOpt - bpFb) > 0.001 || Math.abs(bpFb - bpRaw) > 0.001) {
                System.out.println("bpOpt: " + bpOpt + " bpFb: " + bpFb + " bpRaw: " + bpRaw);
                throw new AssertionError("book[1].price mismatch");
            }
        }
    }

    @State(Scope.Thread)
    public static class PutBenchmarkState {
        public JsonPath priceRaw;
        public FallbackCompiledPath<Root, Double> priceFallback;
        public CompiledPath<Root, Double> priceAsm;

        public JsonPath bookPriceRaw;
        public FallbackCompiledPath<Root, Double> bookPriceFallback;
        public CompiledPath<Root, Double> bookPriceAsm;

        public Root pricePojo;
        public Root bookPricePojo;

        public double nextPrice;
        public double nextBookPrice;

        @Setup(Level.Trial)
        public void setup() {
            priceRaw = JsonPath.parse("$.store.bicycle.price");
            priceFallback = new FallbackCompiledPath<>(priceRaw, Root.class, Double.class);
            priceAsm = CompiledPath.compile("$.store.bicycle.price", Root.class, Double.class);

            bookPriceRaw = JsonPath.parse("$.store.book[1].price");
            bookPriceFallback = new FallbackCompiledPath<>(bookPriceRaw, Root.class, Double.class);
            bookPriceAsm = CompiledPath.compile("$.store.book[1].price", Root.class, Double.class);

            Root probe = _newRoot();
            Object priceOldAsm = priceAsm.put(probe, 21.95d);
            if (priceOldAsm != null || Math.abs(probe.store.bicycle.price - 21.95d) > 0.001d) {
                throw new AssertionError("price put mismatch");
            }

            probe = _newRoot();
            Object bookPriceOldAsm = bookPriceAsm.put(probe, 13.49d);
            if (bookPriceOldAsm != null || Math.abs(probe.store.book.get(1).price - 13.49d) > 0.001d) {
                throw new AssertionError("book[1].price put mismatch");
            }
        }

        @Setup(Level.Invocation)
        public void reset() {
            pricePojo = _newRoot();
            bookPricePojo = _newRoot();
            nextPrice = 21.95d;
            nextBookPrice = 13.49d;
        }

        private Root _newRoot() {
            Bicycle bicycle = new Bicycle();
            bicycle.color = "red";
            bicycle.price = 19.95d;

            Book first = new Book();
            first.title = "A";
            first.price = 8.95d;
            Book second = new Book();
            second.title = "B";
            second.price = 12.99d;

            Store store = new Store();
            store.bicycle = bicycle;
            store.book = new ArrayList<>();
            store.book.add(first);
            store.book.add(second);
            store.featured = new Featured();
            store.featured.price = 15.5d;

            Root root = new Root();
            root.store = store;
            root.expensive = 10;
            return root;
        }
    }


    // ═══════════════════════════════════════════════════════
    //  $.store.bicycle.color
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public String get_color_native(BenchmarkState s) {
        Root root = s.pojo;
        if (root == null) {
            return null;
        }
        Store store = root.store;
        if (store == null) {
            return null;
        }
        Bicycle bicycle = store.bicycle;
        if (bicycle == null) {
            return null;
        }
        return bicycle.color;
    }

    @Benchmark
    public String get_color_bytecode(BenchmarkState s) {
        return s.colorAsm.get(s.pojo);
    }

    @Benchmark
    public String get_color_fallback(BenchmarkState s) {
        return s.colorFallback.get(s.pojo);
    }

    @Benchmark
    public Object get_color_rawJsonPath(BenchmarkState s) {
        return s.colorRaw.getNode(s.pojo);
    }

    /** null 中间路径测试 */
    @Benchmark
    public String get_color_bytecode_empty(BenchmarkState s) {
        return s.colorAsm.get(s.emptyRoot);
    }

    @Benchmark
    public String get_color_fallback_empty(BenchmarkState s) {
        return s.colorFallback.get(s.emptyRoot);
    }

    /** null root 测试 */
    @Benchmark
    public String get_color_bytecode_null(BenchmarkState s) {
        return s.colorAsm.get(null);
    }


    // ═══════════════════════════════════════════════════════
    //  $.store.bicycle.price
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Double get_price_native(BenchmarkState s) {
        Root root = s.pojo;
        if (root == null) {
            return null;
        }
        Store store = root.store;
        if (store == null) {
            return null;
        }
        Bicycle bicycle = store.bicycle;
        if (bicycle == null) {
            return null;
        }
        return bicycle.price;
    }

    @Benchmark
    public Double get_price_bytecode(BenchmarkState s) {
        return s.priceAsm.get(s.pojo);
    }

    @Benchmark
    public Double get_price_fallback(BenchmarkState s) {
        return s.priceFallback.get(s.pojo);
    }

    @Benchmark
    public Object get_price_rawJsonPath(BenchmarkState s) {
        return s.priceRaw.getNode(s.pojo);
    }


    // ═══════════════════════════════════════════════════════
    //  $.store.book[1].price
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Double get_bookPrice_native(BenchmarkState s) {
        Root root = s.pojo;
        if (root == null) {
            return null;
        }
        Store store = root.store;
        if (store == null) {
            return null;
        }
        List<Book> book = store.book;
        if (book == null) {
            return null;
        }
        Book item = book.get(1);
        if (item == null) {
            return null;
        }
        return item.price;
    }

    @Benchmark
    public Double get_bookPrice_bytecode(BenchmarkState s) {
        return s.bookPriceAsm.get(s.pojo);
    }

    @Benchmark
    public Double get_bookPrice_fallback(BenchmarkState s) {
        return s.bookPriceFallback.get(s.pojo);
    }

    @Benchmark
    public Object get_bookPrice_rawJsonPath(BenchmarkState s) {
        return s.bookPriceRaw.getNode(s.pojo);
    }


    // ═══════════════════════════════════════════════════════
    //  put($.store.bicycle.price)
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Double put_price_native(PutBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.pricePojo, "container");
        Store store = root.store;
        if (store == null) {
            throw new JsonException("Cannot put value at path '$.store.bicycle.price': parent container does not exist");
        }
        Bicycle bicycle = store.bicycle;
        if (bicycle == null) {
            throw new JsonException("Cannot put value at path '$.store.bicycle.price': parent container does not exist");
        }
        bicycle.price = s.nextPrice;
        return null;
    }

    @Benchmark
    public Double put_price_bytecode(PutBenchmarkState s) {
        return s.priceAsm.put(s.pricePojo, s.nextPrice);
    }

    @Benchmark
    public Double put_price_fallback(PutBenchmarkState s) {
        return s.priceFallback.put(s.pricePojo, s.nextPrice);
    }

    @Benchmark
    public Object put_price_rawJsonPath(PutBenchmarkState s) {
        return s.priceRaw.put(s.pricePojo, s.nextPrice);
    }


    // ═══════════════════════════════════════════════════════
    //  put($.store.book[1].price)
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Double put_bookPrice_native(PutBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.bookPricePojo, "container");
        Store store = root.store;
        if (store == null) {
            throw new JsonException("Cannot put value at path '$.store.book[1].price': parent container does not exist");
        }
        List<Book> book = store.book;
        if (book == null) {
            throw new JsonException("Cannot put value at path '$.store.book[1].price': parent container does not exist");
        }
        Book item = book.get(1);
        if (item == null) {
            throw new JsonException("Cannot put value at path '$.store.book[1].price': parent container does not exist");
        }
        item.price = s.nextBookPrice;
        return null;
    }

    @Benchmark
    public Double put_bookPrice_bytecode(PutBenchmarkState s) {
        return s.bookPriceAsm.put(s.bookPricePojo, s.nextBookPrice);
    }

    @Benchmark
    public Double put_bookPrice_fallback(PutBenchmarkState s) {
        return s.bookPriceFallback.put(s.bookPricePojo, s.nextBookPrice);
    }

    @Benchmark
    public Object put_bookPrice_rawJsonPath(PutBenchmarkState s) {
        return s.bookPriceRaw.put(s.bookPricePojo, s.nextBookPrice);
    }


}
