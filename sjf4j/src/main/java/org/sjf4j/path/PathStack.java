package org.sjf4j.path;

import org.sjf4j.exception.JsonException;

import java.util.Arrays;
import java.util.Objects;

/**
 * Mutable path builder used by hot paths to avoid allocating PathSegment
 * objects during successful traversal. PathSegment chain is materialized
 * only when needed (typically on error).
 */
public final class PathStack {

    private static final byte KIND_NAME = 1;
    private static final byte KIND_INDEX = 2;

    private byte[] kinds;
    private String[] names;
    private int[] indices;
    private Class<?>[] clazzes;
    private int size;

    /**
     * Creates a PathStack with default capacity.
     */
    public PathStack() {
        this(16);
    }

    /**
     * Creates a PathStack with the given initial capacity.
     */
    public PathStack(int capacity) {
        if (capacity <= 0) throw new JsonException("capacity must be > 0");
        this.kinds = new byte[capacity];
        this.names = new String[capacity];
        this.indices = new int[capacity];
        this.clazzes = new Class<?>[capacity];
    }

    /**
     * Returns number of pushed segments.
     */
    public int size() {
        return size;
    }

    /**
     * Returns true if the stack is empty.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Clears all segments without releasing arrays.
     */
    public void clear() {
        size = 0;
    }

    /**
     * Pushes a name segment with optional owning class.
     */
    public void pushName(Class<?> clazz, String name) {
        Objects.requireNonNull(name, "name is null");
        ensureCapacity(size + 1);
        kinds[size] = KIND_NAME;
        names[size] = name;
        clazzes[size] = clazz;
        size++;
    }

    /**
     * Pushes an index segment with optional owning class.
     */
    public void pushIndex(Class<?> clazz, int index) {
        ensureCapacity(size + 1);
        kinds[size] = KIND_INDEX;
        indices[size] = index;
        clazzes[size] = clazz;
        size++;
    }

    /**
     * Pops the last segment.
     */
    public void pop() {
        if (size == 0) {
            throw new IllegalStateException("PathStack is empty");
        }
        size--;
    }

    /**
     * Materialize current stack into an immutable PathSegment chain.
     */
    public PathSegment toPathSegment() {
        PathSegment ps = PathSegment.Root.INSTANCE;
        for (int i = 0; i < size; i++) {
            if (kinds[i] == KIND_NAME) {
                ps = new PathSegment.Name(ps, clazzes[i], names[i]);
            } else if (kinds[i] == KIND_INDEX) {
                ps = new PathSegment.Index(ps, clazzes[i], indices[i]);
            } else {
                throw new IllegalStateException("Unsupported path kind: " + kinds[i]);
            }
        }
        return ps;
    }

    private void ensureCapacity(int minCapacity) {
        int oldCap = kinds.length;
        if (minCapacity <= oldCap) return;

        int newCap = oldCap + (oldCap >>> 1);
        if (newCap < minCapacity) newCap = minCapacity;
        kinds = Arrays.copyOf(kinds, newCap);
        names = Arrays.copyOf(names, newCap);
        indices = Arrays.copyOf(indices, newCap);
        clazzes = Arrays.copyOf(clazzes, newCap);
    }
}
