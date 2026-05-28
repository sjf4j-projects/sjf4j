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
import org.openjdk.jmh.annotations.TearDown;
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
import java.util.function.BiFunction;

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
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
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


    private static final BiFunction<Object, Object, Object> INCREMENT_DOUBLE =
            new BiFunction<Object, Object, Object>() {
                @Override
                public Object apply(Object parent, Object current) {
                    return ((Double) current) + 1.0d;
                }
            };

    private static final int BOOK_INDEX = 1;

    private static int _readListIndex(List<?> list, int index) {
        int size = list.size();
        if (index >= 0) {
            return size <= index ? -1 : index;
        }
        int effectiveIndex = size + index;
        return effectiveIndex < 0 ? -1 : effectiveIndex;
    }

    private static int _ensureListIndex(List<?> list, int index, String expr) {
        int size = list.size();
        if (index >= 0) {
            if (index < size) {
                return index;
            }
            if (index == size) {
                return size;
            }
        } else {
            int effectiveIndex = size + index;
            if (effectiveIndex >= 0) {
                return effectiveIndex;
            }
        }
        throw new JsonException("cannot ensure path segment at index " + index + " at '" + expr + "': " +
                "indexed array access requires an existing element; use append path syntax instead");
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

            emptyRoot = new Root();

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
        public Root lastPriceRoot;
        public Root lastBookPriceRoot;

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
            lastPriceRoot = null;
            lastBookPriceRoot = null;
            nextPrice = 21.95d;
            nextBookPrice = 13.49d;
        }

        @TearDown(Level.Invocation)
        public void verify() {
            if (lastPriceRoot != null && Math.abs(lastPriceRoot.store.bicycle.price - nextPrice) > 0.001d) {
                throw new AssertionError("price put result was not visible");
            }
            if (lastBookPriceRoot != null &&
                    Math.abs(lastBookPriceRoot.store.book.get(1).price - nextBookPrice) > 0.001d) {
                throw new AssertionError("book[1].price put result was not visible");
            }
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

    @State(Scope.Thread)
    public static class EnsurePutBenchmarkState {
        public JsonPath priceRaw;
        public FallbackCompiledPath<Root, Double> priceFallback;
        public CompiledPath<Root, Double> priceAsm;

        public JsonPath bookPriceRaw;
        public FallbackCompiledPath<Root, Double> bookPriceFallback;
        public CompiledPath<Root, Double> bookPriceAsm;

        public Root pricePojo;
        public Root bookPricePojo;
        public Root lastPriceRoot;
        public Root lastBookPriceRoot;

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

            Root probe = new Root();
            Object priceOldAsm = priceAsm.ensurePut(probe, 21.95d);
            if (priceOldAsm != null || Math.abs(probe.store.bicycle.price - 21.95d) > 0.001d) {
                throw new AssertionError("price ensurePut mismatch");
            }

            probe = _newBookAppendRoot();
            Object bookPriceOldAsm = bookPriceAsm.ensurePut(probe, 13.49d);
            if (bookPriceOldAsm != null || probe.store.book.size() != 2 ||
                    Math.abs(probe.store.book.get(1).price - 13.49d) > 0.001d) {
                throw new AssertionError("book[1].price ensurePut mismatch");
            }
        }

        @Setup(Level.Invocation)
        public void reset() {
            pricePojo = new Root();
            bookPricePojo = _newBookAppendRoot();
            lastPriceRoot = null;
            lastBookPriceRoot = null;
            nextPrice = 21.95d;
            nextBookPrice = 13.49d;
        }

        @TearDown(Level.Invocation)
        public void verify() {
            if (lastPriceRoot != null && (lastPriceRoot.store == null || lastPriceRoot.store.bicycle == null ||
                    Math.abs(lastPriceRoot.store.bicycle.price - nextPrice) > 0.001d)) {
                throw new AssertionError("price ensurePut result was not visible");
            }
            if (lastBookPriceRoot != null && (lastBookPriceRoot.store == null || lastBookPriceRoot.store.book == null ||
                    lastBookPriceRoot.store.book.size() <= 1 || lastBookPriceRoot.store.book.get(1) == null ||
                    Math.abs(lastBookPriceRoot.store.book.get(1).price - nextBookPrice) > 0.001d)) {
                throw new AssertionError("book[1].price ensurePut result was not visible");
            }
        }

        private Root _newBookAppendRoot() {
            Book first = new Book();
            first.title = "A";
            first.price = 8.95d;

            Store store = new Store();
            store.book = new ArrayList<>();
            store.book.add(first);

            Root root = new Root();
            root.store = store;
            return root;
        }
    }

    @State(Scope.Thread)
    public static class EnsurePutExistingBenchmarkState extends PutBenchmarkState {
        public Root lastPriceRoot;
        public Root lastBookPriceRoot;

        @Setup(Level.Invocation)
        @Override
        public void reset() {
            super.reset();
            lastPriceRoot = null;
            lastBookPriceRoot = null;
        }

        @TearDown(Level.Invocation)
        public void verify() {
            if (lastPriceRoot != null && Math.abs(lastPriceRoot.store.bicycle.price - nextPrice) > 0.001d) {
                throw new AssertionError("existing price ensurePut result was not visible");
            }
            if (lastBookPriceRoot != null &&
                    Math.abs(lastBookPriceRoot.store.book.get(1).price - nextBookPrice) > 0.001d) {
                throw new AssertionError("existing book[1].price ensurePut result was not visible");
            }
        }
    }

    @State(Scope.Thread)
    public static class EnsurePutIfAbsentExistingBenchmarkState extends PutBenchmarkState {
        @TearDown(Level.Invocation)
        @Override
        public void verify() {
            if (lastPriceRoot != null && Math.abs(lastPriceRoot.store.bicycle.price - 19.95d) > 0.001d) {
                throw new AssertionError("existing price ensurePutIfAbsent overwrote value");
            }
            if (lastBookPriceRoot != null &&
                    Math.abs(lastBookPriceRoot.store.book.get(1).price - 12.99d) > 0.001d) {
                throw new AssertionError("existing book[1].price ensurePutIfAbsent overwrote value");
            }
        }
    }

    @State(Scope.Thread)
    public static class ComputeBenchmarkState extends PutBenchmarkState {
        @Setup(Level.Invocation)
        @Override
        public void reset() {
            super.reset();
            nextPrice = 20.95d;
            nextBookPrice = 13.99d;
        }

        @TearDown(Level.Invocation)
        @Override
        public void verify() {
            if (lastPriceRoot != null && Math.abs(lastPriceRoot.store.bicycle.price - nextPrice) > 0.001d) {
                throw new AssertionError("price compute result was not visible");
            }
            if (lastBookPriceRoot != null &&
                    Math.abs(lastBookPriceRoot.store.book.get(1).price - nextBookPrice) > 0.001d) {
                throw new AssertionError("book[1].price compute result was not visible");
            }
        }
    }

    @State(Scope.Thread)
    public static class PutIfParentPresentMissingBenchmarkState {
        public JsonPath priceRaw;
        public FallbackCompiledPath<Root, Double> priceFallback;
        public CompiledPath<Root, Double> priceAsm;

        public JsonPath bookPriceRaw;
        public FallbackCompiledPath<Root, Double> bookPriceFallback;
        public CompiledPath<Root, Double> bookPriceAsm;

        public Root pricePojo;
        public Root bookPricePojo;
        public Root lastPriceRoot;
        public Root lastBookPriceRoot;

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

            Root probe = new Root();
            Object priceOldAsm = priceAsm.putIfParentPresent(probe, 21.95d);
            if (priceOldAsm != null || probe.store != null) {
                throw new AssertionError("missing price putIfParentPresent mismatch");
            }

            probe = _newBookMissingParentRoot();
            Object bookPriceOldAsm = bookPriceAsm.putIfParentPresent(probe, 13.49d);
            if (bookPriceOldAsm != null || probe.store.book != null) {
                throw new AssertionError("missing book[1].price putIfParentPresent mismatch");
            }
        }

        @Setup(Level.Invocation)
        public void reset() {
            pricePojo = new Root();
            bookPricePojo = _newBookMissingParentRoot();
            lastPriceRoot = null;
            lastBookPriceRoot = null;
            nextPrice = 21.95d;
            nextBookPrice = 13.49d;
        }

        @TearDown(Level.Invocation)
        public void verify() {
            if (lastPriceRoot != null && lastPriceRoot.store != null) {
                throw new AssertionError("missing price putIfParentPresent created a parent");
            }
            if (lastBookPriceRoot != null && (lastBookPriceRoot.store == null || lastBookPriceRoot.store.book != null)) {
                throw new AssertionError("missing book[1].price putIfParentPresent created a parent list");
            }
        }

        private Root _newBookMissingParentRoot() {
            Store store = new Store();

            Root root = new Root();
            root.store = store;
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
        int index = _readListIndex(book, BOOK_INDEX);
        if (index < 0) {
            return null;
        }
        Book item = book.get(index);
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
    public Root put_price_native(PutBenchmarkState s) {
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
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root put_price_bytecode(PutBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceAsm.put(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root put_price_fallback(PutBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceFallback.put(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root put_price_rawJsonPath(PutBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceRaw.put(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }


    // ═══════════════════════════════════════════════════════
    //  put($.store.book[1].price)
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Root put_bookPrice_native(PutBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.bookPricePojo, "container");
        Store store = root.store;
        if (store == null) {
            throw new JsonException("Cannot put value at path '$.store.book[1].price': parent container does not exist");
        }
        List<Book> book = store.book;
        if (book == null) {
            throw new JsonException("Cannot put value at path '$.store.book[1].price': parent container does not exist");
        }
        int index = _readListIndex(book, BOOK_INDEX);
        if (index < 0) {
            throw new JsonException("Cannot put value at path '$.store.book[1].price': parent container does not exist");
        }
        Book item = book.get(index);
        if (item == null) {
            throw new JsonException("Cannot put value at path '$.store.book[1].price': parent container does not exist");
        }
        item.price = s.nextBookPrice;
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root put_bookPrice_bytecode(PutBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceAsm.put(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root put_bookPrice_fallback(PutBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceFallback.put(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root put_bookPrice_rawJsonPath(PutBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceRaw.put(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }


    // ═══════════════════════════════════════════════════════
    //  compute existing($.store.bicycle.price)
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public int compute_existing_price_native(ComputeBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.pricePojo, "container");
        Store store = root.store;
        if (store == null) {
            return 0;
        }
        Bicycle bicycle = store.bicycle;
        if (bicycle == null) {
            return 0;
        }
        BiFunction<Object, Object, Object> computer = java.util.Objects.requireNonNull(INCREMENT_DOUBLE, "computer");
        bicycle.price = (Double) computer.apply(bicycle, bicycle.price);
        s.lastPriceRoot = root;
        return 1;
    }

    @Benchmark
    public int compute_existing_price_bytecode(ComputeBenchmarkState s) {
        Root root = s.pricePojo;
        int count = s.priceAsm.compute(root, INCREMENT_DOUBLE);
        s.lastPriceRoot = root;
        return count;
    }

    @Benchmark
    public int compute_existing_price_fallback(ComputeBenchmarkState s) {
        Root root = s.pricePojo;
        int count = s.priceFallback.compute(root, INCREMENT_DOUBLE);
        s.lastPriceRoot = root;
        return count;
    }

    @Benchmark
    public int compute_existing_price_rawJsonPath(ComputeBenchmarkState s) {
        Root root = s.pricePojo;
        int count = s.priceRaw.compute(root, INCREMENT_DOUBLE);
        s.lastPriceRoot = root;
        return count;
    }


    // ═══════════════════════════════════════════════════════
    //  compute existing($.store.book[1].price)
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public int compute_existing_bookPrice_native(ComputeBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.bookPricePojo, "container");
        Store store = root.store;
        if (store == null) {
            return 0;
        }
        List<Book> book = store.book;
        if (book == null) {
            return 0;
        }
        int index = _readListIndex(book, BOOK_INDEX);
        if (index < 0) {
            return 0;
        }
        Book item = book.get(index);
        if (item == null) {
            return 0;
        }
        BiFunction<Object, Object, Object> computer = java.util.Objects.requireNonNull(INCREMENT_DOUBLE, "computer");
        item.price = (Double) computer.apply(item, item.price);
        s.lastBookPriceRoot = root;
        return 1;
    }

    @Benchmark
    public int compute_existing_bookPrice_bytecode(ComputeBenchmarkState s) {
        Root root = s.bookPricePojo;
        int count = s.bookPriceAsm.compute(root, INCREMENT_DOUBLE);
        s.lastBookPriceRoot = root;
        return count;
    }

    @Benchmark
    public int compute_existing_bookPrice_fallback(ComputeBenchmarkState s) {
        Root root = s.bookPricePojo;
        int count = s.bookPriceFallback.compute(root, INCREMENT_DOUBLE);
        s.lastBookPriceRoot = root;
        return count;
    }

    @Benchmark
    public int compute_existing_bookPrice_rawJsonPath(ComputeBenchmarkState s) {
        Root root = s.bookPricePojo;
        int count = s.bookPriceRaw.compute(root, INCREMENT_DOUBLE);
        s.lastBookPriceRoot = root;
        return count;
    }


    // ═══════════════════════════════════════════════════════
    //  compute missing parents
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public int compute_missing_price_native(PutIfParentPresentMissingBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.pricePojo, "container");
        Store store = root.store;
        BiFunction<Object, Object, Object> computer = java.util.Objects.requireNonNull(INCREMENT_DOUBLE, "computer");
        if (store != null && store.bicycle != null) {
            Bicycle bicycle = store.bicycle;
            bicycle.price = (Double) computer.apply(bicycle, bicycle.price);
            s.lastPriceRoot = root;
            return 1;
        }
        s.lastPriceRoot = root;
        return 0;
    }

    @Benchmark
    public int compute_missing_price_bytecode(PutIfParentPresentMissingBenchmarkState s) {
        Root root = s.pricePojo;
        int count = s.priceAsm.compute(root, INCREMENT_DOUBLE);
        s.lastPriceRoot = root;
        return count;
    }

    @Benchmark
    public int compute_missing_price_fallback(PutIfParentPresentMissingBenchmarkState s) {
        Root root = s.pricePojo;
        int count = s.priceFallback.compute(root, INCREMENT_DOUBLE);
        s.lastPriceRoot = root;
        return count;
    }

    @Benchmark
    public int compute_missing_price_rawJsonPath(PutIfParentPresentMissingBenchmarkState s) {
        Root root = s.pricePojo;
        int count = s.priceRaw.compute(root, INCREMENT_DOUBLE);
        s.lastPriceRoot = root;
        return count;
    }

    @Benchmark
    public int compute_missing_bookPrice_native(PutIfParentPresentMissingBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.bookPricePojo, "container");
        Store store = root.store;
        BiFunction<Object, Object, Object> computer = java.util.Objects.requireNonNull(INCREMENT_DOUBLE, "computer");
        if (store != null && store.book != null) {
            List<Book> book = store.book;
            int index = _readListIndex(book, BOOK_INDEX);
            if (index < 0) {
                s.lastBookPriceRoot = root;
                return 0;
            }
            Book item = book.get(index);
            if (item != null) {
                item.price = (Double) computer.apply(item, item.price);
                s.lastBookPriceRoot = root;
                return 1;
            }
        }
        s.lastBookPriceRoot = root;
        return 0;
    }

    @Benchmark
    public int compute_missing_bookPrice_bytecode(PutIfParentPresentMissingBenchmarkState s) {
        Root root = s.bookPricePojo;
        int count = s.bookPriceAsm.compute(root, INCREMENT_DOUBLE);
        s.lastBookPriceRoot = root;
        return count;
    }

    @Benchmark
    public int compute_missing_bookPrice_fallback(PutIfParentPresentMissingBenchmarkState s) {
        Root root = s.bookPricePojo;
        int count = s.bookPriceFallback.compute(root, INCREMENT_DOUBLE);
        s.lastBookPriceRoot = root;
        return count;
    }

    @Benchmark
    public int compute_missing_bookPrice_rawJsonPath(PutIfParentPresentMissingBenchmarkState s) {
        Root root = s.bookPricePojo;
        int count = s.bookPriceRaw.compute(root, INCREMENT_DOUBLE);
        s.lastBookPriceRoot = root;
        return count;
    }


    // ═══════════════════════════════════════════════════════
    //  putIfParentPresent existing($.store.bicycle.price)
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Root putIfParentPresent_existing_price_native(PutBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.pricePojo, "container");
        Store store = root.store;
        if (store == null) {
            return root;
        }
        Bicycle bicycle = store.bicycle;
        if (bicycle == null) {
            return root;
        }
        bicycle.price = s.nextPrice;
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_existing_price_bytecode(PutBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceAsm.putIfParentPresent(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_existing_price_fallback(PutBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceFallback.putIfParentPresent(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_existing_price_rawJsonPath(PutBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceRaw.putIfParentPresent(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }


    // ═══════════════════════════════════════════════════════
    //  putIfParentPresent existing($.store.book[1].price)
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Root putIfParentPresent_existing_bookPrice_native(PutBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.bookPricePojo, "container");
        Store store = root.store;
        if (store == null) {
            return root;
        }
        List<Book> book = store.book;
        if (book == null) {
            return root;
        }
        int index = _readListIndex(book, BOOK_INDEX);
        if (index < 0) {
            return root;
        }
        Book item = book.get(index);
        if (item == null) {
            return root;
        }
        item.price = s.nextBookPrice;
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_existing_bookPrice_bytecode(PutBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceAsm.putIfParentPresent(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_existing_bookPrice_fallback(PutBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceFallback.putIfParentPresent(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_existing_bookPrice_rawJsonPath(PutBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceRaw.putIfParentPresent(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }


    // ═══════════════════════════════════════════════════════
    //  putIfParentPresent missing parents
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Root putIfParentPresent_missing_price_native(PutIfParentPresentMissingBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.pricePojo, "container");
        Store store = root.store;
        if (store != null) {
            Bicycle bicycle = store.bicycle;
            if (bicycle != null) {
                bicycle.price = s.nextPrice;
            }
        }
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_missing_price_bytecode(PutIfParentPresentMissingBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceAsm.putIfParentPresent(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_missing_price_fallback(PutIfParentPresentMissingBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceFallback.putIfParentPresent(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_missing_price_rawJsonPath(PutIfParentPresentMissingBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceRaw.putIfParentPresent(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_missing_bookPrice_native(PutIfParentPresentMissingBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.bookPricePojo, "container");
        Store store = root.store;
        if (store != null) {
            List<Book> book = store.book;
            if (book != null) {
                int index = _readListIndex(book, BOOK_INDEX);
                if (index < 0) {
                    s.lastBookPriceRoot = root;
                    return root;
                }
                Book item = book.get(index);
                if (item != null) {
                    item.price = s.nextBookPrice;
                }
            }
        }
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_missing_bookPrice_bytecode(PutIfParentPresentMissingBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceAsm.putIfParentPresent(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_missing_bookPrice_fallback(PutIfParentPresentMissingBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceFallback.putIfParentPresent(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root putIfParentPresent_missing_bookPrice_rawJsonPath(PutIfParentPresentMissingBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceRaw.putIfParentPresent(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }


    // ═══════════════════════════════════════════════════════
    //  ensurePut missing($.store.bicycle.price)
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Root ensurePut_missing_price_native(EnsurePutBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.pricePojo, "container");
        Store store = root.store;
        if (store == null) {
            store = new Store();
            root.store = store;
        }
        Bicycle bicycle = store.bicycle;
        if (bicycle == null) {
            bicycle = new Bicycle();
            store.bicycle = bicycle;
        }
        bicycle.price = s.nextPrice;
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_missing_price_bytecode(EnsurePutBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceAsm.ensurePut(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_missing_price_fallback(EnsurePutBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceFallback.ensurePut(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_missing_price_rawJsonPath(EnsurePutBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceRaw.ensurePut(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }


    // ═══════════════════════════════════════════════════════
    //  ensurePut missing($.store.book[1].price)
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Root ensurePut_missing_bookPrice_native(EnsurePutBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.bookPricePojo, "container");
        Store store = root.store;
        if (store == null) {
            store = new Store();
            root.store = store;
        }
        List<Book> book = store.book;
        if (book == null) {
            book = new ArrayList<>();
            store.book = book;
        }
        int index = _ensureListIndex(book, BOOK_INDEX, "$.store.book[1].price");
        if (index < book.size()) {
            Book item = book.get(index);
            if (item == null) {
                item = new Book();
                book.set(index, item);
            }
            item.price = s.nextBookPrice;
            s.lastBookPriceRoot = root;
            return root;
        }
        if (index == book.size()) {
            Book item = new Book();
            item.price = s.nextBookPrice;
            book.add(item);
            s.lastBookPriceRoot = root;
            return root;
        }
        throw new AssertionError("unreachable");
    }

    @Benchmark
    public Root ensurePut_missing_bookPrice_bytecode(EnsurePutBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceAsm.ensurePut(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_missing_bookPrice_fallback(EnsurePutBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceFallback.ensurePut(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_missing_bookPrice_rawJsonPath(EnsurePutBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceRaw.ensurePut(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }


    // ═══════════════════════════════════════════════════════
    //  ensurePut with existing parents (same roots as put)
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Root ensurePut_existing_price_native(EnsurePutExistingBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.pricePojo, "container");
        Store store = root.store;
        if (store == null) {
            store = new Store();
            root.store = store;
        }
        Bicycle bicycle = store.bicycle;
        if (bicycle == null) {
            bicycle = new Bicycle();
            store.bicycle = bicycle;
        }
        bicycle.price = s.nextPrice;
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_existing_price_bytecode(EnsurePutExistingBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceAsm.ensurePut(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_existing_price_fallback(EnsurePutExistingBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceFallback.ensurePut(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_existing_price_rawJsonPath(EnsurePutExistingBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceRaw.ensurePut(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_existing_bookPrice_native(EnsurePutExistingBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.bookPricePojo, "container");
        Store store = root.store;
        if (store == null) {
            store = new Store();
            root.store = store;
        }
        List<Book> book = store.book;
        if (book == null) {
            book = new ArrayList<>();
            store.book = book;
        }
        int index = _ensureListIndex(book, BOOK_INDEX, "$.store.book[1].price");
        if (index == book.size()) {
            Book item = new Book();
            item.price = s.nextBookPrice;
            book.add(item);
            s.lastBookPriceRoot = root;
            return root;
        }
        Book item = book.get(index);
        if (item == null) {
            item = new Book();
            book.set(index, item);
        }
        item.price = s.nextBookPrice;
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_existing_bookPrice_bytecode(EnsurePutExistingBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceAsm.ensurePut(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_existing_bookPrice_fallback(EnsurePutExistingBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceFallback.ensurePut(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_existing_bookPrice_rawJsonPath(EnsurePutExistingBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceRaw.ensurePut(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }


    // ═══════════════════════════════════════════════════════
    //  ensurePutIfAbsent existing paths
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Root ensurePutIfAbsent_existing_price_native(EnsurePutIfAbsentExistingBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.pricePojo, "container");
        Store store = root.store;
        if (store == null) {
            store = new Store();
            root.store = store;
        }
        Bicycle bicycle = store.bicycle;
        if (bicycle == null) {
            bicycle = new Bicycle();
            store.bicycle = bicycle;
        }
        if (bicycle.price == null) {
            bicycle.price = s.nextPrice;
        }
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_price_bytecode(EnsurePutIfAbsentExistingBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceAsm.ensurePutIfAbsent(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_price_fallback(EnsurePutIfAbsentExistingBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceFallback.ensurePutIfAbsent(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_price_rawJsonPath(EnsurePutIfAbsentExistingBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceRaw.ensurePutIfAbsent(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_bookPrice_native(EnsurePutIfAbsentExistingBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.bookPricePojo, "container");
        Store store = root.store;
        if (store == null) {
            store = new Store();
            root.store = store;
        }
        List<Book> book = store.book;
        if (book == null) {
            book = new ArrayList<>();
            store.book = book;
        }
        int index = _ensureListIndex(book, BOOK_INDEX, "$.store.book[1].price");
        Book item;
        if (index == book.size()) {
            item = new Book();
            book.add(item);
        } else {
            item = book.get(index);
            if (item == null) {
                item = new Book();
                book.set(index, item);
            }
        }
        if (item.price == null) {
            item.price = s.nextBookPrice;
        }
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_bookPrice_bytecode(EnsurePutIfAbsentExistingBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceAsm.ensurePutIfAbsent(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_bookPrice_fallback(EnsurePutIfAbsentExistingBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceFallback.ensurePutIfAbsent(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_bookPrice_rawJsonPath(EnsurePutIfAbsentExistingBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceRaw.ensurePutIfAbsent(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }


    // ═══════════════════════════════════════════════════════
    //  ensurePutIfAbsent missing/absent paths
    // ═══════════════════════════════════════════════════════

    @Benchmark
    public Root ensurePutIfAbsent_missing_price_native(EnsurePutBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.pricePojo, "container");
        Store store = root.store;
        if (store == null) {
            store = new Store();
            root.store = store;
        }
        Bicycle bicycle = store.bicycle;
        if (bicycle == null) {
            bicycle = new Bicycle();
            store.bicycle = bicycle;
        }
        if (bicycle.price == null) {
            bicycle.price = s.nextPrice;
        }
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_missing_price_bytecode(EnsurePutBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceAsm.ensurePutIfAbsent(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_missing_price_fallback(EnsurePutBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceFallback.ensurePutIfAbsent(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_missing_price_rawJsonPath(EnsurePutBenchmarkState s) {
        Root root = s.pricePojo;
        s.priceRaw.ensurePutIfAbsent(root, s.nextPrice);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_missing_bookPrice_native(EnsurePutBenchmarkState s) {
        Root root = java.util.Objects.requireNonNull(s.bookPricePojo, "container");
        Store store = root.store;
        if (store == null) {
            store = new Store();
            root.store = store;
        }
        List<Book> book = store.book;
        if (book == null) {
            book = new ArrayList<>();
            store.book = book;
        }
        int index = _ensureListIndex(book, BOOK_INDEX, "$.store.book[1].price");
        Book item;
        if (index == book.size()) {
            item = new Book();
            book.add(item);
        } else {
            item = book.get(index);
            if (item == null) {
                item = new Book();
                book.set(index, item);
            }
        }
        if (item.price == null) {
            item.price = s.nextBookPrice;
        }
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_missing_bookPrice_bytecode(EnsurePutBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceAsm.ensurePutIfAbsent(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_missing_bookPrice_fallback(EnsurePutBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceFallback.ensurePutIfAbsent(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_missing_bookPrice_rawJsonPath(EnsurePutBenchmarkState s) {
        Root root = s.bookPricePojo;
        s.bookPriceRaw.ensurePutIfAbsent(root, s.nextBookPrice);
        s.lastBookPriceRoot = root;
        return root;
    }


}
