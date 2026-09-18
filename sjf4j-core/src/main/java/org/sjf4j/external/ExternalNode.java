package org.sjf4j.external;

import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.Nodes;
import org.sjf4j.exception.NodeException;

import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Direct structural operations for nodes owned by an external JSON model.
 *
 * <p>Only {@link #rootType()} and {@link #jsonType(Object)} are mandatory.
 * All operational defaults fail fast with {@link NodeException}; adapters must
 * override every operation they expose. Defaults never traverse, convert,
 * inspect, allocate, mutate, or infer external nodes.</p>
 *
 * <p>{@code N} is the external root/container type. Native child values are
 * exposed as {@link Object}.</p>
 *
 * @param <N> external root/container type handled by this adapter
 */
public interface ExternalNode<N> {
    /**
     * Returns the external root/container class handled by this adapter.
     */
    Class<N> rootType();

    /**
     * Returns the JSON-semantic type of {@code node}.
     */
    JsonType jsonType(N node);

    /**
     * Returns the JSON-semantic type implied by {@code nodeType}, or {@link JsonType#UNKNOWN} by default.
     */
    default JsonType jsonTypeOfClass(Class<?> nodeType) {
        return JsonType.UNKNOWN;
    }

    /**
     * Returns the SJF4J node kind corresponding to {@link #jsonType(Object)} for {@code node}.
     */
    default NodeKind nodeKind(N node) {
        switch (jsonType(node)) {
            case OBJECT: return NodeKind.OBJECT_EXTERNAL;
            case ARRAY: return NodeKind.ARRAY_EXTERNAL;
            case STRING: return NodeKind.VALUE_STRING_EXTERNAL;
            case NUMBER: return NodeKind.VALUE_NUMBER_EXTERNAL;
            case BOOLEAN: return NodeKind.VALUE_BOOLEAN_EXTERNAL;
            case NULL: return NodeKind.VALUE_NULL;
            default: return NodeKind.UNKNOWN;
        }
    }

    /**
     * Returns the fail-fast exception for the named unsupported operation.
     */
    static NodeException unsupported(String operation) {
        return new NodeException("unsupported external node operation '" + operation + "'");
    }

    /**
     * Returns {@code node} as a string using strict conversion.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default String toString(N node) {
        throw unsupported("toString");
    }

    /**
     * Returns {@code node} as a string using lenient conversion.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default String asString(N node) {
        throw unsupported("asString");
    }

    /**
     * Returns {@code node} as a number using strict conversion.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default Number toNumber(N node) {
        throw unsupported("toNumber");
    }

    /**
     * Returns {@code node} as a number using lenient conversion.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default Number asNumber(N node) {
        throw unsupported("asNumber");
    }

    /**
     * Returns {@code node} as a boolean using strict conversion.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default Boolean toBoolean(N node) {
        throw unsupported("toBoolean");
    }

    /**
     * Returns {@code node} as a boolean using lenient conversion.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default Boolean asBoolean(N node) {
        throw unsupported("asBoolean");
    }

    /**
     * Invokes {@code consumer} for each key and value in object {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default void forEachObject(N node, BiConsumer<String, Object> consumer) {
        throw unsupported("forEachObject");
    }

    /**
     * Returns whether {@code predicate} matches any key and value in object {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default boolean anyMatchObject(N node, BiPredicate<String, Object> predicate) {
        throw unsupported("anyMatchObject");
    }

    /**
     * Replaces object values with results from {@code mapper} and returns whether object {@code node} changed.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default boolean replaceInObject(N node, BiFunction<String, Object, Object> mapper) {
        throw unsupported("replaceInObject");
    }

    /**
     * Removes entries from object {@code node} whose key and value match {@code predicate}, returning whether it changed.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default boolean removeIfInObject(N node, BiPredicate<String, Object> predicate) {
        throw unsupported("removeIfInObject");
    }

    /**
     * Invokes {@code consumer} for each index and value in array {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default void forEachArray(N node, BiConsumer<Integer, Object> consumer) {
        throw unsupported("forEachArray");
    }

    /**
     * Returns whether {@code predicate} matches any index and value in array {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default boolean anyMatchArray(N node, BiPredicate<Integer, Object> predicate) {
        throw unsupported("anyMatchArray");
    }

    /**
     * Returns the entry count of object {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default int sizeInObject(N node) {
        throw unsupported("sizeInObject");
    }

    /**
     * Returns the element count of array {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default int sizeInArray(N node) {
        throw unsupported("sizeInArray");
    }

    /**
     * Returns an iterator over the values in array {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default Iterator<Object> iteratorInArray(N node) {
        throw unsupported("iteratorInArray");
    }

    /**
     * Returns whether object {@code node} contains {@code key}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default boolean containsInObject(N node, String key) {
        throw unsupported("containsInObject");
    }

    /**
     * Returns the value for {@code key} in object {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default Object getInObject(N node, String key) {
        throw unsupported("getInObject");
    }

    /**
     * Returns the value at {@code idx} in array {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default Object getInArray(N node, int idx) {
        throw unsupported("getInArray");
    }

    /**
     * Fills {@code out} with readable metadata for {@code key} in object {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default void getAccessInObject(N node, String key, Nodes.Access out) {
        throw unsupported("getAccessInObject");
    }

    /**
     * Fills {@code out} with writable metadata for {@code key} in object {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default void putAccessInObject(N node, String key, Nodes.Access out) {
        throw unsupported("putAccessInObject");
    }

    /**
     * Fills {@code out} with readable metadata for {@code idx} in array {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default void getAccessInArray(N node, int idx, Nodes.Access out) {
        throw unsupported("getAccessInArray");
    }

    /**
     * Fills {@code out} with writable metadata for {@code idx} in array {@code node}; a null index denotes
     * append access.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default void putAccessInArray(N node, Integer idx, Nodes.Access out) {
        throw unsupported("putAccessInArray");
    }

    /**
     * Associates {@code value} with {@code key} in object {@code node} and returns the previous value.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default Object putInObject(N node, String key, Object value) {
        throw unsupported("putInObject");
    }

    /**
     * Replaces the value at {@code idx} in array {@code node} with {@code value} and returns the previous
     * value.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default Object setInArray(N node, int idx, Object value) {
        throw unsupported("setInArray");
    }

    /**
     * Appends {@code value} to array {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default void addInArray(N node, Object value) {
        throw unsupported("addInArray");
    }

    /**
     * Inserts {@code value} at {@code idx} in array {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default void addInArray(N node, int idx, Object value) {
        throw unsupported("addInArray");
    }

    /**
     * Removes and returns the value for {@code key} from object {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default Object removeInObject(N node, String key) {
        throw unsupported("removeInObject");
    }

    /**
     * Removes and returns the value at {@code idx} from array {@code node}.
     *
     * <p>The default throws {@link NodeException} and must be overridden to expose this capability.</p>
     */
    default Object removeInArray(N node, int idx) {
        throw unsupported("removeInArray");
    }
}
