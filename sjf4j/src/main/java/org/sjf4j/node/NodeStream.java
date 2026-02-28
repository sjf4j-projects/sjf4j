package org.sjf4j.node;


import org.sjf4j.JsonArray;
import org.sjf4j.path.JsonPath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stream wrapper for node processing with JSONPath helpers.
 * <p>
 * Wraps a single-use Java {@link Stream}; terminal operations consume it.
 */
public class NodeStream<T> {

    /**
     * The underlying Java Stream that contains the JSON nodes.
     */
    private final Stream<T> stream;

    /**
     * Creates a NodeStream from an existing stream.
     *
     * @throws IllegalArgumentException if stream is null
     */
    protected NodeStream(Stream<T> stream) {
        if (stream == null) throw new IllegalArgumentException("NodeStream must not be null");
        this.stream = stream;
    }

    /**
     * Creates a NodeStream from list elements.
     */
    public static <T> NodeStream<T> of(List<T> nodes) {
        if (nodes == null) throw new IllegalArgumentException("Nodes must not be null");
        return new NodeStream<>(nodes.stream());
    }

    /**
     * Creates a NodeStream from a single element.
     */
    public static <T> NodeStream<T> of(T node) {
        if (node == null) throw new IllegalArgumentException("Node must not be null");
        return new NodeStream<>(Stream.of(node));
    }

    /**
     * Reads one value by path per element using strict conversion.
     * <p>
     * Missing paths map to {@code null} elements.
     */
    public <R> NodeStream<R> getByPath(String path, Class<R> clazz) {
        JsonPath jp = JsonPath.compile(path);
        Stream<R> ns = stream.map(node -> jp.get(node, clazz));
        return new NodeStream<>(ns);
    }

    /**
     * Reads one value by path per element using lenient conversion.
     */
    public <R> NodeStream<R> asByPath(String path, Class<R> clazz) {
        JsonPath jp = JsonPath.compile(path);
        Stream<R> ns = stream.map(node -> jp.getAs(node, clazz));
        return new NodeStream<>(ns);
    }

    /**
     * Finds all matched values per element and flattens them (strict conversion).
     */
    public <R> NodeStream<R> findByPath(String path, Class<R> clazz) {
        JsonPath jp = JsonPath.compile(path);
        Stream<R> ns = stream.flatMap(node -> jp.find(node, clazz).stream());
        return new NodeStream<>(ns);
    }

    /**
     * Finds all matched values per element and flattens them (lenient conversion).
     */
    public <R> NodeStream<R> findAsByPath(String path, Class<R> clazz) {
        JsonPath jp = JsonPath.compile(path);
        Stream<R> ns = stream.flatMap(node -> jp.findAs(node, clazz).stream());
        return new NodeStream<>(ns);
    }

    /**
     * Evaluates a path per element and flattens results with strict conversion.
     */
    public <R> NodeStream<R> evalByPath(String path, Class<R> clazz) {
        JsonPath jp = JsonPath.compile(path);
        Stream<R> ns = stream.flatMap(node -> {
            Object result = jp.eval(node);
            if (result == null) {
                return Stream.empty();
            } else if (result instanceof List) {
                List<?> raw = (List<?>) result;
                List<R> list = new ArrayList<>(raw.size());
                for (Object o : raw) {
                    list.add(Nodes.to(o, clazz));
                }
                return list.stream();
            } else {
                return Stream.of(Nodes.to(result, clazz));
            }
        });
        return new NodeStream<>(ns);
    }

    /**
     * Evaluates a path per element and flattens results with lenient conversion.
     */
    public <R> NodeStream<R> evalAsByPath(String path, Class<R> clazz) {
        JsonPath jp = JsonPath.compile(path);
        Stream<R> ns = stream.flatMap(node -> {
            Object result = jp.eval(node);
            if (result == null) {
                return Stream.empty();
            } else if (result instanceof List) {
                List<?> raw = (List<?>) result;
                List<R> list = new ArrayList<>(raw.size());
                for (Object o : raw) {
                    list.add(Nodes.as(o, clazz));
                }
                return list.stream();
            } else {
                return Stream.of(Nodes.as(result, clazz));
            }
        });
        return new NodeStream<>(ns);
    }


    /// Java Stream

    /**
     * Filters the stream using the specified predicate.
     *
     * @param predicate the predicate to apply to each element to determine if it should be included
     * @return a new JsonStream with the filtered elements
     */
    public NodeStream<T> filter(Predicate<? super T> predicate) {
        return new NodeStream<>(stream.filter(predicate));
    }

    /**
     * Maps the elements of the stream using the specified mapper function.
     *
     * @param mapper the function to apply to each element
     * @param <R> the type of the new elements
     * @return a new JsonStream with the mapped elements
     */
    public <R> NodeStream<R> map(Function<? super T, ? extends R> mapper) {
        return new NodeStream<>(stream.map(mapper));
    }

    /**
     * Returns a new stream consisting of the distinct elements of this stream.
     *
     * @return a new JsonStream with distinct elements
     */
    public NodeStream<T> distinct() {
        return new NodeStream<>(stream.distinct());
    }

    /**
     * Returns a new stream that consists of the elements of this stream, additionally performing
     * the provided action on each element as elements are consumed from the resulting stream.
     *
     * @param action the action to perform on each element
     * @return a new JsonStream with the peeked elements
     */
    public NodeStream<T> peek(Consumer<? super T> action) {
        return new NodeStream<>(stream.peek(action));
    }

    /**
     * Returns a stream consisting of the elements of this stream, truncated to be no longer than
     * {@code maxSize} in length.
     *
     * @param maxSize the maximum number of elements to include
     * @return a new JsonStream with the limited elements
     */
    public NodeStream<T> limit(long maxSize) {
        return new NodeStream<>(stream.limit(maxSize));
    }

    /**
     * Returns a stream consisting of the remaining elements of this stream after discarding the first
     * {@code n} elements of the stream.
     *
     * @param n the number of leading elements to skip
     * @return a new JsonStream with the skipped elements
     */
    public NodeStream<T> skip(long n) {
        return new NodeStream<>(stream.skip(n));
    }

    /**
     * Returns a stream consisting of the elements of this stream, sorted according to the provided
     * {@code Comparator}.
     *
     * @param comparator the comparator to determine the order of the stream
     * @return a new JsonStream with the sorted elements
     */
    public NodeStream<T> sorted(Comparator<? super T> comparator) {
        return new NodeStream<>(stream.sorted(comparator));
    }

    /**
     * Returns a stream consisting of the results of replacing each element of this stream with the
     * contents of a mapped stream produced by applying the provided mapping function to each element.
     *
     * @param mapper the function to apply to each element which produces a stream of new values
     * @param <R> the type of the new elements
     * @return a new JsonStream with the flattened elements
     */
    public <R> NodeStream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return new NodeStream<>(stream.flatMap(mapper));
    }

    /**
     * Returns the count of elements in this stream.
     *
     * @return the count of elements
     */
    public long count() {
        return stream.count();
    }

    /**
     * Returns whether any elements of this stream match the provided predicate.
     *
     * @param predicate the predicate to apply to elements of this stream
     * @return {@code true} if any elements of the stream match the predicate, otherwise {@code false}
     */
    public boolean anyMatch(Predicate<? super T> predicate) {
        return stream.anyMatch(predicate);
    }

    /**
     * Returns whether all elements of this stream match the provided predicate.
     *
     * @param predicate the predicate to apply to elements of this stream
     * @return {@code true} if all elements of the stream match the predicate, otherwise {@code false}
     */
    public boolean allMatch(Predicate<? super T> predicate) {
        return stream.allMatch(predicate);
    }

    /**
     * Returns whether no elements of this stream match the provided predicate.
     *
     * @param predicate the predicate to apply to elements of this stream
     * @return {@code true} if no elements of the stream match the predicate, otherwise {@code false}
     */
    public boolean noneMatch(Predicate<? super T> predicate) {
        return stream.noneMatch(predicate);
    }

    /**
     * Collects stream elements to a list.
     */
    public List<T> toList() {
        return stream.collect(Collectors.toList());
    }

    /**
     * Collects stream elements to a JsonArray.
     */
    public JsonArray toJsonArray() {
        return new JsonArray(toList());
    }

    /**
     * Returns the first element if present.
     */
    public Optional<T> findFirst() {
        return stream.findFirst();
    }

    /**
     * Returns any element if present.
     */
    public Optional<T> findAny() {
        return stream.findAny();
    }

    /**
     * Performs a terminal collect operation.
     */
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return stream.collect(collector);
    }



}
