package org.sjf4j.compiled;

import org.sjf4j.node.TypeReference;

/**
 * Bytecode-compiled accessor for one concrete JSON path expression.
 *
 * <p>The static {@code compile(...)} methods require an optional bytecode compiler module,
 * such as {@code sjf4j-bytecode}, to be present on the runtime classpath. Core-only or fully
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

    String expr();

    default V get(R root, V defaultValue) {
        V v = get(root);
        return v == null ? defaultValue : v;
    }

    V get(R root);

    default V put(R root, V value) {
        throw new UnsupportedOperationException("put() is not implemented");
    }

    default V putIfParentPresent(R root, V value) {
        throw new UnsupportedOperationException("putIfParentPresent() is not implemented");
    }

    default V ensurePut(R root, V value) {
        throw new UnsupportedOperationException("ensurePut() is not implemented");
    }

    default V ensurePutIfAbsent(R root, V value) {
        throw new UnsupportedOperationException("ensurePutIfAbsent() is not implemented");
    }

    default int compute(R root, java.util.function.BiFunction<Object, Object, Object> computer) {
        throw new UnsupportedOperationException("compute() is not implemented");
    }


    /// static

    @SuppressWarnings("unchecked")
    static <R, V> BytecodePath<R, V> compile(String pathExpr, Class<R> rootClazz, Class<V> valueClazz) {
        return (BytecodePath<R, V>) BytecodeCompilers.compilePath(pathExpr, rootClazz, valueClazz, false);
    }

    @SuppressWarnings("unchecked")
    static <R, V> BytecodePath<R, V> compile(String pathExpr, TypeReference<R> rootType, TypeReference<V> valueType) {
        return (BytecodePath<R, V>) BytecodeCompilers.compilePath(pathExpr, rootType.getType(), valueType.getType(), false);
    }


}
