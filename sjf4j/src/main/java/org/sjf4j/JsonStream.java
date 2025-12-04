package org.sjf4j;


import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class JsonStream<T> {

    private final Stream<T> nodeStream;

    protected JsonStream(Stream<T> nodeStream) {
        if (nodeStream == null) throw new IllegalArgumentException("NodeStream must not be null");
        this.nodeStream = nodeStream;
    }

    public static <T>JsonStream<T> of(List<T> nodes) {
        if (nodes == null) throw new IllegalArgumentException("Nodes must not be null");
        return new JsonStream<>(nodes.stream());
    }

    public static <T>JsonStream<T> of(T node) {
        if (node == null) throw new IllegalArgumentException("Node must not be null");
        return new JsonStream<>(Stream.of(node));
    }

    public <R> JsonStream<R> find(String path, Class<R> clazz) {
        Stream<R> ns = nodeStream.map(node -> JsonPath.compile(path).find(node, clazz));
        return new JsonStream<>(ns);
    }

    public <R> JsonStream<R> findAs(String path, Class<R> clazz) {
        Stream<R> ns = nodeStream.map(node -> JsonPath.compile(path).findAs(node, clazz));
        return new JsonStream<>(ns);
    }

    public <R> JsonStream<R> findAll(String path, Class<R> clazz) {
        Stream<R> ns = nodeStream.flatMap(node -> JsonPath.compile(path).findAll(node, clazz).stream());
        return new JsonStream<>(ns);
    }

    public <R> JsonStream<R> findAllAs(String path, Class<R> clazz) {
        Stream<R> ns = nodeStream.flatMap(node -> JsonPath.compile(path).findAllAs(node, clazz).stream());
        return new JsonStream<>(ns);
    }

    public JsonStream<T> filter(Predicate<? super T> predicate) {
        return new JsonStream<>(nodeStream.filter(predicate));
    }

    public <R> JsonStream<R> map(Function<? super T, ? extends R> mapper) {
        return new JsonStream<>(nodeStream.map(mapper));
    }

    public JsonStream<T> distinct() {
        return new JsonStream<>(nodeStream.distinct());
    }

    public JsonStream<T> peek(Consumer<? super T> action) {
        return new JsonStream<>(nodeStream.peek(action));
    }

    public JsonStream<T> limit(long maxSize) {
        return new JsonStream<>(nodeStream.limit(maxSize));
    }

    public JsonStream<T> skip(long n) {
        return new JsonStream<>(nodeStream.skip(n));
    }

    public JsonStream<T> sorted(Comparator<? super T> comparator) {
        return new JsonStream<>(nodeStream.sorted(comparator));
    }

    public <R> JsonStream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return new JsonStream<>(nodeStream.flatMap(mapper));
    }

    public long count() {
        return nodeStream.count();
    }

    public boolean anyMatch(Predicate<? super T> predicate) {
        return nodeStream.anyMatch(predicate);
    }

    public boolean allMatch(Predicate<? super T> predicate) {
        return nodeStream.allMatch(predicate);
    }

    public boolean noneMatch(Predicate<? super T> predicate) {
        return nodeStream.noneMatch(predicate);
    }

    public List<T> toList() {
        return nodeStream.collect(Collectors.toList());
    }

    public JsonArray toJsonArray() {
        return new JsonArray(toList());
    }

    public Optional<T> findFirst() {
        return nodeStream.findFirst();
    }

    public Optional<T> findAny() {
        return nodeStream.findAny();
    }

    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return nodeStream.collect(collector);
    }



}
