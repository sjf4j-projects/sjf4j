package org.sjf4j.node;


import org.sjf4j.JsonArray;
import org.sjf4j.path.JsonPath;

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
 * Provides a streaming interface for processing JSON nodes using Java Streams API.
 * <p>
 * This class wraps a Java Stream of JSON nodes and provides additional methods for working with JSON data,
 * including path-based value extraction, filtering, mapping, and other stream operations.
 * <p>
 * JsonStream allows for fluent and functional-style processing of JSON data structures, enabling
 * operations like finding values by path, filtering nodes, mapping to different types, and more.
 *
 * @param <T> the type of JSON nodes in the stream
 */
public class JsonStream<T> {

    /**
     * The underlying Java Stream that contains the JSON nodes.
     */
    private final Stream<T> stream;

    /**
     * Creates a JsonStream from an existing Stream of JSON nodes.
     *
     * @param stream the stream of JSON nodes to wrap
     * @throws IllegalArgumentException if nodeStream is null
     */
    protected JsonStream(Stream<T> stream) {
        if (stream == null) throw new IllegalArgumentException("NodeStream must not be null");
        this.stream = stream;
    }

    /**
     * Creates a JsonStream from a list of JSON nodes.
     *
     * @param nodes the list of JSON nodes
     * @param <T> the type of JSON nodes
     * @return a new JsonStream containing the nodes
     * @throws IllegalArgumentException if nodes is null
     */
    public static <T>JsonStream<T> of(List<T> nodes) {
        if (nodes == null) throw new IllegalArgumentException("Nodes must not be null");
        return new JsonStream<>(nodes.stream());
    }

    /**
     * Creates a JsonStream from a single JSON node.
     *
     * @param node the JSON node (= Object)
     * @param <T> the type of the JSON node
     * @return a new JsonStream containing the single node
     * @throws IllegalArgumentException if node is null
     */
    public static <T>JsonStream<T> of(T node) {
        if (node == null) throw new IllegalArgumentException("Node must not be null");
        return new JsonStream<>(Stream.of(node));
    }

    /**
     * Finds a value at the specified path in each node of the stream and returns a new stream of found values.
     *
     * @param path the JSON path expression
     * @param clazz the class type of the returned values
     * @param <R> the type of the returned values
     * @return a new JsonStream containing the found values
     */
    public <R> JsonStream<R> find(String path, Class<R> clazz) {
        Stream<R> ns = stream.map(node -> JsonPath.compile(path).find(node, clazz));
        return new JsonStream<>(ns);
    }

    /**
     * Finds a value at the specified path in each node of the stream, converts it to the specified type,
     * and returns a new stream of found values.
     *
     * @param path the JSON path expression
     * @param clazz the class type to convert the found values to
     * @param <R> the type of the returned values
     * @return a new JsonStream containing the found and converted values
     */
    public <R> JsonStream<R> findAs(String path, Class<R> clazz) {
        Stream<R> ns = stream.map(node -> JsonPath.compile(path).findAs(node, clazz));
        return new JsonStream<>(ns);
    }

    /**
     * Finds all values at the specified path in each node of the stream and returns a new stream of all found values.
     *
     * @param path the JSON path expression
     * @param clazz the class type of the returned values
     * @param <R> the type of the returned values
     * @return a new JsonStream containing all found values
     */
    public <R> JsonStream<R> findAll(String path, Class<R> clazz) {
        Stream<R> ns = stream.flatMap(node -> JsonPath.compile(path).findAll(node, clazz).stream());
        return new JsonStream<>(ns);
    }

    /**
     * Finds all values at the specified path in each node of the stream, converts them to the specified type,
     * and returns a new stream of all found values.
     *
     * @param path the JSON path expression
     * @param clazz the class type to convert the found values to
     * @param <R> the type of the returned values
     * @return a new JsonStream containing all found and converted values
     */
    public <R> JsonStream<R> findAllAs(String path, Class<R> clazz) {
        Stream<R> ns = stream.flatMap(node -> JsonPath.compile(path).findAllAs(node, clazz).stream());
        return new JsonStream<>(ns);
    }

    /**
     * Filters the stream using the specified predicate.
     *
     * @param predicate the predicate to apply to each element to determine if it should be included
     * @return a new JsonStream with the filtered elements
     */
    public JsonStream<T> filter(Predicate<? super T> predicate) {
        return new JsonStream<>(stream.filter(predicate));
    }

    /**
     * Maps the elements of the stream using the specified mapper function.
     *
     * @param mapper the function to apply to each element
     * @param <R> the type of the new elements
     * @return a new JsonStream with the mapped elements
     */
    public <R> JsonStream<R> map(Function<? super T, ? extends R> mapper) {
        return new JsonStream<>(stream.map(mapper));
    }

    /**
     * Returns a new stream consisting of the distinct elements of this stream.
     *
     * @return a new JsonStream with distinct elements
     */
    public JsonStream<T> distinct() {
        return new JsonStream<>(stream.distinct());
    }

    /**
     * Returns a new stream that consists of the elements of this stream, additionally performing
     * the provided action on each element as elements are consumed from the resulting stream.
     *
     * @param action the action to perform on each element
     * @return a new JsonStream with the peeked elements
     */
    public JsonStream<T> peek(Consumer<? super T> action) {
        return new JsonStream<>(stream.peek(action));
    }

    /**
     * Returns a stream consisting of the elements of this stream, truncated to be no longer than
     * {@code maxSize} in length.
     *
     * @param maxSize the maximum number of elements to include
     * @return a new JsonStream with the limited elements
     */
    public JsonStream<T> limit(long maxSize) {
        return new JsonStream<>(stream.limit(maxSize));
    }

    /**
     * Returns a stream consisting of the remaining elements of this stream after discarding the first
     * {@code n} elements of the stream.
     *
     * @param n the number of leading elements to skip
     * @return a new JsonStream with the skipped elements
     */
    public JsonStream<T> skip(long n) {
        return new JsonStream<>(stream.skip(n));
    }

    /**
     * Returns a stream consisting of the elements of this stream, sorted according to the provided
     * {@code Comparator}.
     *
     * @param comparator the comparator to determine the order of the stream
     * @return a new JsonStream with the sorted elements
     */
    public JsonStream<T> sorted(Comparator<? super T> comparator) {
        return new JsonStream<>(stream.sorted(comparator));
    }

    /**
     * Returns a stream consisting of the results of replacing each element of this stream with the
     * contents of a mapped stream produced by applying the provided mapping function to each element.
     *
     * @param mapper the function to apply to each element which produces a stream of new values
     * @param <R> the type of the new elements
     * @return a new JsonStream with the flattened elements
     */
    public <R> JsonStream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return new JsonStream<>(stream.flatMap(mapper));
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

    public List<T> toList() {
        return stream.collect(Collectors.toList());
    }

    public JsonArray toJsonArray() {
        return new JsonArray(toList());
    }

    public Optional<T> findFirst() {
        return stream.findFirst();
    }

    public Optional<T> findAny() {
        return stream.findAny();
    }

    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return stream.collect(collector);
    }



}
