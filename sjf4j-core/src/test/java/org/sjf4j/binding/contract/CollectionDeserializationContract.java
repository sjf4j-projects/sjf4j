package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.JsonBinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Portable collection cases from Jackson's CollectionDeserializationTest. */
public abstract class CollectionDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** Source: CollectionDeserializationTest#testUntypedList. */
    @Test void testUntypedList() {
        List<?> value = assertInstanceOf(List.class, binding(StreamingContext.EMPTY).readNode("[\"text!\",true,null,23]", Object.class));
        assertEquals("text!", value.get(0)); assertEquals(true, value.get(1)); assertNull(value.get(2)); assertEquals(23, ((Number) value.get(3)).intValue());
    }
    /** Source: CollectionDeserializationTest#testExactStringCollection. */
    @Test void testExactStringCollection() { assertEquals(List.of("a", "b"), binding(StreamingContext.EMPTY).readNode("[\"a\",\"b\"]", new TypeReference<ArrayList<String>>() {}.getType())); }
    /** Source: CollectionDeserializationTest#testAbstractListAndSet (structural: source requests AbstractSet). */
    @Test void testSet() {
        assertEquals(Set.of("a", "b"), binding(StreamingContext.EMPTY).readNode("[\"a\",\"b\"]", new TypeReference<Set<String>>() {}.getType()));
    }
    /** Source: CollectionDeserializationTest#testJava6Types. */
    @Test void testDeque() {
        assertEquals(List.of(1, 2), new java.util.ArrayList<>((Deque<Integer>) binding(StreamingContext.EMPTY).readNode("[1,2]", new TypeReference<Deque<Integer>>() {}.getType())));
    }
    /** Retained SJF4J default concrete-collection coverage; no Jackson source attribution. */
    @Test void testConcreteCollections() {
        assertEquals(List.of("a", "b"), binding(StreamingContext.EMPTY).readNode("[\"a\",\"b\"]", new TypeReference<LinkedList<String>>() {}.getType()));
        assertEquals(Set.of("a", "b"), binding(StreamingContext.EMPTY).readNode("[\"a\",\"b\"]", new TypeReference<HashSet<String>>() {}.getType()));
        assertEquals(Set.of("a", "b"), binding(StreamingContext.EMPTY).readNode("[\"a\",\"b\"]", new TypeReference<LinkedHashSet<String>>() {}.getType()));
        assertEquals(List.of("a", "b"), new java.util.ArrayList<>((ArrayDeque<String>) binding(StreamingContext.EMPTY).readNode("[\"a\",\"b\"]", new TypeReference<ArrayDeque<String>>() {}.getType())));
    }
    /** Retained SJF4J list-interface coverage; no Jackson source attribution. */
    @Test void testList() { assertEquals(List.of("a", "b"), binding(StreamingContext.EMPTY).readNode("[\"a\",\"b\"]", new TypeReference<List<String>>() {}.getType())); }
    /** Retained SJF4J queue-interface coverage; no Jackson source attribution. */
    @Test void testQueue() { assertEquals(List.of(1, 2), new ArrayList<>((Queue<Integer>) binding(StreamingContext.EMPTY).readNode("[1,2]", new TypeReference<Queue<Integer>>() {}.getType()))); }
}
