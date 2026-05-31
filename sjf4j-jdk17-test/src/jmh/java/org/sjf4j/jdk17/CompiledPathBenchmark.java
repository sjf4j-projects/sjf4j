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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.compiled.CompiledNodes;
import org.sjf4j.annotation.compiled.EnsurePutByPath;
import org.sjf4j.annotation.compiled.EnsurePutIfAbsentByPath;
import org.sjf4j.annotation.compiled.GetByPath;
import org.sjf4j.annotation.compiled.PutByPath;
import org.sjf4j.compiled.BytecodePath;
import org.sjf4j.compiled.CompiledNodesRegistry;
import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Compares hand-written native access, runtime {@link JsonPath}, ASM-backed
 * {@link BytecodePath}, and annotation-processor generated compiled nodes.
 *
 * <p>The data model and paths mirror {@code BytecodePathBenchmark} so APT code
 * generation can be compared against the existing bytecode-path benchmark cases.
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
public class CompiledPathBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{
                CompiledPathBenchmark.class.getSimpleName()
        });
    }


    private static final int BOOK_INDEX = 1;
    private static final double NEXT_PRICE = 21.95d;
    private static final double NEXT_BOOK_PRICE = 13.49d;

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

    @CompiledNodes
    public interface CompiledPaths {
        @GetByPath("$.store.bicycle.color")
        String getColor(Root root);

        @GetByPath("$.store.bicycle.price")
        Double getPrice(Root root);

        @GetByPath("$.store.book[1].price")
        Double getBookPrice(Root root);

        @PutByPath("$.store.bicycle.price")
        Double putPrice(Root root, Double value);

        @PutByPath("$.store.book[1].price")
        Double putBookPrice(Root root, Double value);

        @EnsurePutByPath("$.store.bicycle.price")
        Double ensurePutPrice(Root root, Double value);

        @EnsurePutByPath("$.store.book[1].price")
        Double ensurePutBookPrice(Root root, Double value);

        @EnsurePutIfAbsentByPath("$.store.bicycle.price")
        Double ensurePutIfAbsentPrice(Root root, Double value);

        @EnsurePutIfAbsentByPath("$.store.book[1].price")
        Double ensurePutIfAbsentBookPrice(Root root, Double value);
    }

    @State(Scope.Thread)
    public static class GetState {
        public Root pojo;
        public JsonPath colorJsonPath;
        public JsonPath priceJsonPath;
        public JsonPath bookPriceJsonPath;
        public BytecodePath<Root, String> colorBytecode;
        public BytecodePath<Root, Double> priceBytecode;
        public BytecodePath<Root, Double> bookPriceBytecode;
        public CompiledPaths compiled;

        @Setup(Level.Trial)
        public void setup() {
            pojo = Sjf4j.global().fromJson(BOOKSTORE_JSON, Root.class);
            colorJsonPath = JsonPath.parse("$.store.bicycle.color");
            priceJsonPath = JsonPath.parse("$.store.bicycle.price");
            bookPriceJsonPath = JsonPath.parse("$.store.book[1].price");
            colorBytecode = BytecodePath.compile("$.store.bicycle.color", Root.class, String.class);
            priceBytecode = BytecodePath.compile("$.store.bicycle.price", Root.class, Double.class);
            bookPriceBytecode = BytecodePath.compile("$.store.book[1].price", Root.class, Double.class);
            compiled = CompiledNodesRegistry.of(CompiledPaths.class);

            if (!"red".equals(compiled.getColor(pojo)) || !compiled.getColor(pojo).equals(colorBytecode.get(pojo)) ||
                    Math.abs(compiled.getPrice(pojo) - 19.95d) > 0.001d ||
                    Math.abs(compiled.getBookPrice(pojo) - 12.99d) > 0.001d) {
                throw new AssertionError("compiled path setup mismatch");
            }
        }
    }

    @State(Scope.Thread)
    public static class PutState {
        public JsonPath priceJsonPath;
        public JsonPath bookPriceJsonPath;
        public BytecodePath<Root, Double> priceBytecode;
        public BytecodePath<Root, Double> bookPriceBytecode;
        public CompiledPaths compiled;
        public Root priceRoot;
        public Root bookPriceRoot;
        public Root lastPriceRoot;
        public Root lastBookPriceRoot;

        @Setup(Level.Trial)
        public void setup() {
            priceJsonPath = JsonPath.parse("$.store.bicycle.price");
            bookPriceJsonPath = JsonPath.parse("$.store.book[1].price");
            priceBytecode = BytecodePath.compile("$.store.bicycle.price", Root.class, Double.class);
            bookPriceBytecode = BytecodePath.compile("$.store.book[1].price", Root.class, Double.class);
            compiled = CompiledNodesRegistry.of(CompiledPaths.class);
        }

        @Setup(Level.Invocation)
        public void reset() {
            priceRoot = newFullRoot();
            bookPriceRoot = newFullRoot();
            lastPriceRoot = null;
            lastBookPriceRoot = null;
        }

        @TearDown(Level.Invocation)
        public void verify() {
            if (lastPriceRoot != null && Math.abs(lastPriceRoot.store.bicycle.price - NEXT_PRICE) > 0.001d) {
                throw new AssertionError("price mutation was not visible");
            }
            if (lastBookPriceRoot != null && Math.abs(lastBookPriceRoot.store.book.get(BOOK_INDEX).price - NEXT_BOOK_PRICE) > 0.001d) {
                throw new AssertionError("book price mutation was not visible");
            }
        }
    }

    @State(Scope.Thread)
    public static class EnsureMissingState extends PutState {
        @Setup(Level.Invocation)
        @Override
        public void reset() {
            priceRoot = new Root();
            bookPriceRoot = newBookAppendRoot();
            lastPriceRoot = null;
            lastBookPriceRoot = null;
        }
    }

    @State(Scope.Thread)
    public static class EnsureIfAbsentExistingState extends PutState {
        @TearDown(Level.Invocation)
        @Override
        public void verify() {
            if (lastPriceRoot != null && Math.abs(lastPriceRoot.store.bicycle.price - 19.95d) > 0.001d) {
                throw new AssertionError("existing price was overwritten");
            }
            if (lastBookPriceRoot != null && Math.abs(lastBookPriceRoot.store.book.get(BOOK_INDEX).price - 12.99d) > 0.001d) {
                throw new AssertionError("existing book price was overwritten");
            }
        }
    }

    // get($.store.bicycle.color)

    @Benchmark
    public String get_color_native(GetState s) {
        Root root = s.pojo;
        if (root == null || root.store == null || root.store.bicycle == null) return null;
        return root.store.bicycle.color;
    }

    @Benchmark
    public Object get_color_jsonpath(GetState s) {
        return s.colorJsonPath.getNode(s.pojo);
    }

    @Benchmark
    public String get_color_bytecodepath(GetState s) {
        return s.colorBytecode.get(s.pojo);
    }

    @Benchmark
    public String get_color_compiledpath(GetState s) {
        return s.compiled.getColor(s.pojo);
    }

    // get($.store.bicycle.price)

    @Benchmark
    public Double get_price_native(GetState s) {
        Root root = s.pojo;
        if (root == null || root.store == null || root.store.bicycle == null) return null;
        return root.store.bicycle.price;
    }

    @Benchmark
    public Object get_price_jsonpath(GetState s) {
        return s.priceJsonPath.getNode(s.pojo);
    }

    @Benchmark
    public Double get_price_bytecodepath(GetState s) {
        return s.priceBytecode.get(s.pojo);
    }

    @Benchmark
    public Double get_price_compiledpath(GetState s) {
        return s.compiled.getPrice(s.pojo);
    }

    // get($.store.book[1].price)

    @Benchmark
    public Double get_bookPrice_native(GetState s) {
        Root root = s.pojo;
        if (root == null || root.store == null || root.store.book == null) return null;
        int index = readListIndex(root.store.book, BOOK_INDEX);
        if (index < 0) return null;
        Book book = root.store.book.get(index);
        return book == null ? null : book.price;
    }

    @Benchmark
    public Object get_bookPrice_jsonpath(GetState s) {
        return s.bookPriceJsonPath.getNode(s.pojo);
    }

    @Benchmark
    public Double get_bookPrice_bytecodepath(GetState s) {
        return s.bookPriceBytecode.get(s.pojo);
    }

    @Benchmark
    public Double get_bookPrice_compiledpath(GetState s) {
        return s.compiled.getBookPrice(s.pojo);
    }

    // put($.store.bicycle.price)

    @Benchmark
    public Root put_price_native(PutState s) {
        Root root = s.priceRoot;
        if (root == null || root.store == null || root.store.bicycle == null) throw missingParent("$.store.bicycle.price");
        root.store.bicycle.price = NEXT_PRICE;
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root put_price_jsonpath(PutState s) {
        Root root = s.priceRoot;
        s.priceJsonPath.put(root, NEXT_PRICE);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root put_price_bytecodepath(PutState s) {
        Root root = s.priceRoot;
        s.priceBytecode.put(root, NEXT_PRICE);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root put_price_compiledpath(PutState s) {
        Root root = s.priceRoot;
        s.compiled.putPrice(root, NEXT_PRICE);
        s.lastPriceRoot = root;
        return root;
    }

    // put($.store.book[1].price)

    @Benchmark
    public Root put_bookPrice_native(PutState s) {
        Root root = s.bookPriceRoot;
        if (root == null || root.store == null || root.store.book == null) throw missingParent("$.store.book[1].price");
        int index = readListIndex(root.store.book, BOOK_INDEX);
        if (index < 0) throw missingParent("$.store.book[1].price");
        Book book = root.store.book.get(index);
        if (book == null) throw missingParent("$.store.book[1].price");
        book.price = NEXT_BOOK_PRICE;
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root put_bookPrice_jsonpath(PutState s) {
        Root root = s.bookPriceRoot;
        s.bookPriceJsonPath.put(root, NEXT_BOOK_PRICE);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root put_bookPrice_bytecodepath(PutState s) {
        Root root = s.bookPriceRoot;
        s.bookPriceBytecode.put(root, NEXT_BOOK_PRICE);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root put_bookPrice_compiledpath(PutState s) {
        Root root = s.bookPriceRoot;
        s.compiled.putBookPrice(root, NEXT_BOOK_PRICE);
        s.lastBookPriceRoot = root;
        return root;
    }

    // ensurePut missing($.store.bicycle.price)

    @Benchmark
    public Root ensurePut_missing_price_native(EnsureMissingState s) {
        Root root = s.priceRoot;
        if (root.store == null) root.store = new Store();
        if (root.store.bicycle == null) root.store.bicycle = new Bicycle();
        root.store.bicycle.price = NEXT_PRICE;
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_missing_price_jsonpath(EnsureMissingState s) {
        Root root = s.priceRoot;
        s.priceJsonPath.ensurePut(root, NEXT_PRICE);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_missing_price_bytecodepath(EnsureMissingState s) {
        Root root = s.priceRoot;
        s.priceBytecode.ensurePut(root, NEXT_PRICE);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_missing_price_compiledpath(EnsureMissingState s) {
        Root root = s.priceRoot;
        s.compiled.ensurePutPrice(root, NEXT_PRICE);
        s.lastPriceRoot = root;
        return root;
    }

    // ensurePut missing($.store.book[1].price)

    @Benchmark
    public Root ensurePut_missing_bookPrice_native(EnsureMissingState s) {
        Root root = s.bookPriceRoot;
        if (root.store == null) root.store = new Store();
        if (root.store.book == null) root.store.book = new ArrayList<>();
        int index = ensureListIndex(root.store.book, BOOK_INDEX, "$.store.book[1].price");
        Book book = index < root.store.book.size() ? root.store.book.get(index) : null;
        if (book == null) {
            book = new Book();
            if (index == root.store.book.size()) root.store.book.add(book);
            else root.store.book.set(index, book);
        }
        book.price = NEXT_BOOK_PRICE;
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_missing_bookPrice_jsonpath(EnsureMissingState s) {
        Root root = s.bookPriceRoot;
        s.bookPriceJsonPath.ensurePut(root, NEXT_BOOK_PRICE);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_missing_bookPrice_bytecodepath(EnsureMissingState s) {
        Root root = s.bookPriceRoot;
        s.bookPriceBytecode.ensurePut(root, NEXT_BOOK_PRICE);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePut_missing_bookPrice_compiledpath(EnsureMissingState s) {
        Root root = s.bookPriceRoot;
        s.compiled.ensurePutBookPrice(root, NEXT_BOOK_PRICE);
        s.lastBookPriceRoot = root;
        return root;
    }

    // ensurePutIfAbsent existing paths

    @Benchmark
    public Root ensurePutIfAbsent_existing_price_native(EnsureIfAbsentExistingState s) {
        Root root = java.util.Objects.requireNonNull(s.priceRoot, "root");
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
        Double old = bicycle.price;
        if (old == null) bicycle.price = NEXT_PRICE;
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_price_jsonpath(EnsureIfAbsentExistingState s) {
        Root root = s.priceRoot;
        s.priceJsonPath.ensurePutIfAbsent(root, NEXT_PRICE);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_price_bytecodepath(EnsureIfAbsentExistingState s) {
        Root root = s.priceRoot;
        s.priceBytecode.ensurePutIfAbsent(root, NEXT_PRICE);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_price_compiledpath(EnsureIfAbsentExistingState s) {
        Root root = s.priceRoot;
        s.compiled.ensurePutIfAbsentPrice(root, NEXT_PRICE);
        s.lastPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_bookPrice_native(EnsureIfAbsentExistingState s) {
        Root root = java.util.Objects.requireNonNull(s.bookPriceRoot, "root");
        Store store = root.store;
        if (store == null) {
            store = new Store();
            root.store = store;
        }
        List<Book> books = store.book;
        if (books == null) {
            books = new ArrayList<>();
            store.book = books;
        }
        int index = ensureListIndex(books, BOOK_INDEX, "$.store.book[1].price");
        Book book = index < books.size() ? books.get(index) : null;
        if (book == null) {
            book = new Book();
            if (index == books.size()) books.add(book);
            else books.set(index, book);
        }
        Double old = book.price;
        if (old == null) book.price = NEXT_BOOK_PRICE;
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_bookPrice_jsonpath(EnsureIfAbsentExistingState s) {
        Root root = s.bookPriceRoot;
        s.bookPriceJsonPath.ensurePutIfAbsent(root, NEXT_BOOK_PRICE);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_bookPrice_bytecodepath(EnsureIfAbsentExistingState s) {
        Root root = s.bookPriceRoot;
        s.bookPriceBytecode.ensurePutIfAbsent(root, NEXT_BOOK_PRICE);
        s.lastBookPriceRoot = root;
        return root;
    }

    @Benchmark
    public Root ensurePutIfAbsent_existing_bookPrice_compiledpath(EnsureIfAbsentExistingState s) {
        Root root = s.bookPriceRoot;
        s.compiled.ensurePutIfAbsentBookPrice(root, NEXT_BOOK_PRICE);
        s.lastBookPriceRoot = root;
        return root;
    }

    // helpers

    private static Root newFullRoot() {
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

    private static Root newBookAppendRoot() {
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

    private static int readListIndex(List<?> list, int index) {
        int size = list.size();
        if (index >= 0) return size <= index ? -1 : index;
        int effectiveIndex = size + index;
        return effectiveIndex < 0 ? -1 : effectiveIndex;
    }

    private static int ensureListIndex(List<?> list, int index, String expr) {
        int size = list.size();
        if (index >= 0) {
            if (index < size) return index;
            if (index == size) return size;
        } else {
            int effectiveIndex = size + index;
            if (effectiveIndex >= 0) return effectiveIndex;
        }
        throw new JsonException("cannot ensure path segment at index " + index + " at '" + expr + "': " +
                "indexed array access requires an existing element; use append path syntax instead");
    }

    private static JsonException missingParent(String expr) {
        return new JsonException("cannot put value at path '" + expr + "': parent container does not exist");
    }
}
