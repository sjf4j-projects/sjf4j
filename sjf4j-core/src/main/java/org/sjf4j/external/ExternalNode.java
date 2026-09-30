package org.sjf4j.external;

import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.Nodes;
import org.sjf4j.exception.NodeException;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Optional adapter for a backend-native JSON tree.
 *
 * <p>{@code N} is the root external type; every child and scalar value is
 * exposed unchanged as {@link Object}. Entry points expect a non-null Java
 * node. JSON null is instead a native node with {@link JsonType#NULL}: scalar
 * conversions return Java {@code null}, while object fields and array elements
 * expose that native null node. Invalid shapes and external write values throw
 * {@link NodeException}.</p>
 *
 * <p>Strict conversions accept only their matching scalar type or JSON null.
 * Lenient number and boolean conversions follow {@link Nodes#asNumber(Object)}
 * and {@link Nodes#asBoolean(Object)}. Objects and arrays cannot be converted
 * to numbers or booleans. {@link #asString(Object)} may use the native
 * backend's normal lenient string behavior.</p>
 *
 * <p>Adapters may provide traversal and mutation operations. When they provide
 * object key or entry sets, those sets are live backing views: their iterators
 * and set removal mutate the object, and entry {@code setValue} validates
 * writes. Array indexes normalize negative values with {@code size + index};
 * invalid gets return {@code null}, while invalid set/remove and indexed add
 * throw. Indexed add accepts normalized indexes from {@code 0} through
 * {@code size}. Access helpers distinguish missing from present JSON null. A
 * null array write index denotes append; existing indexes and {@code size} are
 * puttable.</p>
 *
 * <p>Supported mutations and mapped or entry values canonicalize Java
 * {@code null} to the native JSON null node. An adapter that supports
 * {@link #copy(Object)} should shallow-copy only the outer container and share
 * its children. The default {@code copy} implementation is identity. Factories
 * create native object or array containers for the adapter root or its
 * corresponding concrete shape. Only classification is mandatory; unsupported
 * operations fail fast by default, except where a method specifies another
 * default.</p>
 *
 * @param <N> external root type handled by this adapter
 */
public interface ExternalNode<N> {
    /**
     * Returns the external Java representation root class handled by this adapter.
     */
    Class<N> nodeType();

    /** Returns the JSON-semantic type of {@code node}. */
    JsonType jsonType(N node);

    /** Returns the type implied by a concrete node class, or {@link JsonType#UNKNOWN}. */
    default JsonType jsonTypeOfClass(Class<?> nodeType) {
        return JsonType.UNKNOWN;
    }

    /** Returns the runtime classification corresponding to {@link #jsonType(Object)}. */
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

    /** Returns the fail-fast exception for an unsupported operation. */
    static NodeException unsupported(String operation) {
        return new NodeException("unsupported external node operation '" + operation + "'");
    }

    /** Returns a strict string conversion. */
    default String toString(N node) {
        throw unsupported("toString");
    }

    /** Returns a lenient string conversion. */
    default String asString(N node) {
        throw unsupported("asString");
    }

    /** Returns a strict number conversion. */
    default Number toNumber(N node) {
        throw unsupported("toNumber");
    }

    /** Returns a lenient number conversion. */
    default Number asNumber(N node) {
        throw unsupported("asNumber");
    }

    /** Returns a strict boolean conversion. */
    default Boolean toBoolean(N node) {
        throw unsupported("toBoolean");
    }

    /** Returns a lenient boolean conversion. */
    default Boolean asBoolean(N node) {
        throw unsupported("asBoolean");
    }

    /** Visits object entries. */
    default void forEachObject(N node, BiConsumer<String, Object> consumer) {
        throw unsupported("forEachObject");
    }

    /** Returns whether an object entry matches. */
    default boolean anyMatchObject(N node, BiPredicate<String, Object> predicate) {
        throw unsupported("anyMatchObject");
    }

    /** Replaces object values and reports identity changes. */
    default boolean replaceAllInObject(N node, BiFunction<String, Object, Object> mapper) {
        throw unsupported("replaceAllInObject");
    }

    /** Removes matching object entries. */
    default boolean removeIfInObject(N node, BiPredicate<String, Object> predicate) {
        throw unsupported("removeIfInObject");
    }

    /** Visits array elements. */
    default void forEachArray(N node, BiConsumer<Integer, Object> consumer) {
        throw unsupported("forEachArray");
    }

    /** Returns whether an array element matches. */
    default boolean anyMatchInArray(N node, BiPredicate<Integer, Object> predicate) {
        throw unsupported("anyMatchArray");
    }

    /** Returns the object entry count. */
    default int sizeInObject(N node) {
        throw unsupported("sizeInObject");
    }

    /** Returns the array element count. */
    default int sizeInArray(N node) {
        throw unsupported("sizeInArray");
    }

    /** Returns an iterator over raw array elements. */
    default Iterator<Object> iteratorInArray(N node) {
        throw unsupported("iteratorInArray");
    }

    /** Returns whether the object contains a key. */
    default boolean containsInObject(N node, String key) {
        throw unsupported("containsInObject");
    }

    /** Returns a live object key view. */
    default Set<String> keySetInObject(N node) {
        throw unsupported("keySetInObject");
    }

    /** Returns a live object entry view. */
    default Set<Map.Entry<String, Object>> entrySetInObject(N node) {
        throw unsupported("entrySetInObject");
    }

    /** Returns the raw value for an object key. */
    default Object getInObject(N node, String key) {
        throw unsupported("getInObject");
    }

    /** Returns the raw value at an array index. */
    default Object getInArray(N node, int idx) {
        throw unsupported("getInArray");
    }

    /** Fills {@code out} with readable object-child metadata. */
    default void getAccessInObject(N node, String key, Nodes.Access out) {
        throw unsupported("getAccessInObject");
    }

    /** Fills {@code out} with writable object-child metadata. */
    default void putAccessInObject(N node, String key, Nodes.Access out) {
        throw unsupported("putAccessInObject");
    }

    /** Fills {@code out} with readable array-child metadata. */
    default void getAccessInArray(N node, int idx, Nodes.Access out) {
        throw unsupported("getAccessInArray");
    }

    /** Fills {@code out} with writable array-child metadata; null index means append. */
    default void putAccessInArray(N node, Integer idx, Nodes.Access out) {
        throw unsupported("putAccessInArray");
    }

    /** Associates a value with an object key and returns the previous value. */
    default Object putInObject(N node, String key, Object value) {
        throw unsupported("putInObject");
    }

    /** Replaces an array value and returns the previous value. */
    default Object setInArray(N node, int idx, Object value) {
        throw unsupported("setInArray");
    }

    /** Appends an array value. */
    default void addInArray(N node, Object value) {
        throw unsupported("addInArray");
    }

    /** Inserts an array value. */
    default void addInArray(N node, int idx, Object value) {
        throw unsupported("addInArray");
    }

    /** Removes and returns an object value. */
    default Object removeInObject(N node, String key) {
        throw unsupported("removeInObject");
    }

    /** Removes and returns an array value. */
    default Object removeInArray(N node, int idx) {
        throw unsupported("removeInArray");
    }

    /**
     * Returns a copy of {@code node}; adapters that support copying should
     * shallow-copy containers and share their children. The default returns
     * {@code node} unchanged.
     */
    default N copy(N node) {
        return node;
    }

    /**
     * Returns a recursive copy of {@code node}.
     *
     * <p>Adapters for mutable object or array nodes must override this method.
     * Immutable trees may return the original node. The default rejects mutable
     * container shapes so framework deep-copy operations never silently alias a
     * subtree.</p>
     */
    default N deepCopy(N node) {
        JsonType type = jsonType(node);
        if (type.isObject() || type.isArray()) {
            throw unsupported("deepCopy");
        }
        return node;
    }

    /** Creates a native object container. */
    default Object createObjectNode(Class<?> clazz) {
        throw unsupported("createObjectNode");
    }

    /** Creates a native array container. */
    default Object createArrayNode(Class<?> clazz) {
        throw unsupported("createArrayNode");
    }


}
