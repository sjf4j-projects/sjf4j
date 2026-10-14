package org.sjf4j.bytecode;

import org.sjf4j.TypeReference;

/**
 * Bytecode-compiled accessor for one concrete JSON path expression.
 *
 * <p>{@code R} is the declared root type and {@code V} is the declared type of the path target.
 * Implementations expose reads through {@link #get(Object)} and may implement mutation methods.
 * The default mutation methods throw {@link UnsupportedOperationException}.
 *
 * <p>The static {@code compile(...)} methods require an optional bytecode compiler module,
 * such as {@code sjf4j-asm}, to be present on the runtime classpath. Core-only or fully
 * dynamic users can instantiate {@link FallbackBytecodePath} with a parsed
 * {@link org.sjf4j.path.JsonPath} when reflective path access is desired.
 *
 * <p>Bytecode-compiled paths produced by {@code compile(...)} support negative array indexes
 * such as {@code [-1]} for tail-relative access. For performance, bytecode-compiled paths use
 * direct list/array access for indexed segments. Out-of-range intermediate indexes may therefore
 * throw instead of resolving to {@code null}, including for {@link #putIfParentPresent(Object, Object)}; use
 * {@link FallbackBytecodePath} or direct {@link org.sjf4j.path.JsonPath} access when full
 * missing-index JSON path semantics are required.
 */
public interface BytecodePath<R, V> {

    /** Returns the compiled path as a JSONPath expression. */
    String expr();

    /** Returns the path value, or the default when missing or null. */
    default V get(R root, V defaultValue) {
        V v = get(root);
        return v == null ? defaultValue : v;
    }

    /** Reads one value from the supplied root. */
    V get(R root);

    /** Writes at the path and returns the previous value when available. */
    default V put(R root, V value) {
        throw new UnsupportedOperationException("put() is not implemented");
    }

    /** Writes only when the final segment's parent exists. */
    default V putIfParentPresent(R root, V value) {
        throw new UnsupportedOperationException("putIfParentPresent() is not implemented");
    }

    /** Creates supported intermediate containers before writing the value. */
    default V ensurePut(R root, V value) {
        throw new UnsupportedOperationException("ensurePut() is not implemented");
    }

    /** Writes only when the target value is absent or null. */
    default V ensurePutIfAbsent(R root, V value) {
        throw new UnsupportedOperationException("ensurePutIfAbsent() is not implemented");
    }

    /**
     * Recomputes matched locations from their parent and current value.
     * @return the number of locations written
     */
    default int compute(R root, java.util.function.BiFunction<Object, Object, Object> computer) {
        throw new UnsupportedOperationException("compute() is not implemented");
    }


    /*
     * --------------------------------------------------------------
     * Static
     * --------------------------------------------------------------
     */

    /**
     * Compiles a typed single-target path through the optional runtime provider.
     *
     * @param pathExpr a JSONPath or JSON Pointer expression
     * @param rootClazz declared root class
     * @param valueClazz declared value class
     * @return the compiled accessor
     * @throws org.sjf4j.exception.NodeException if no provider is installed or the path is unsupported
     */
    @SuppressWarnings("unchecked")
    static <R, V> BytecodePath<R, V> compile(String pathExpr, Class<R> rootClazz, Class<V> valueClazz) {
        return (BytecodePath<R, V>) BytecodeCompilers.compilePath(pathExpr, rootClazz, valueClazz, false);
    }

    /**
     * Compiles a typed single-target path with generic root and value types.
     * The requested types are passed to the compiler without erasing them.
     *
     * @param pathExpr a JSONPath or JSON Pointer expression
     * @param rootType declared generic root type
     * @param valueType declared generic value type
     * @return the compiled accessor
     * @throws org.sjf4j.exception.NodeException if no provider is installed or the path is unsupported
     */
    @SuppressWarnings("unchecked")
    static <R, V> BytecodePath<R, V> compile(String pathExpr, TypeReference<R> rootType, TypeReference<V> valueType) {
        return (BytecodePath<R, V>) BytecodeCompilers.compilePath(pathExpr, rootType.getType(), valueType.getType(), false);
    }


}
