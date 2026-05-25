package org.sjf4j.compiled;

import org.sjf4j.node.TypeReference;

/**
 * Bytecode-compiled accessor for one concrete JSON path expression.
 *
 * <p>The static {@code compile(...)} methods require an optional bytecode compiler module,
 * such as {@code sjf4j-bytecode}, to be present on the runtime classpath. Core-only or fully
 * dynamic users can instantiate {@link FallbackCompiledPath} with a parsed
 * {@link org.sjf4j.path.JsonPath} when reflective path access is desired.
 */
public interface CompiledPath<R, V> {

    String expr();

    default V get(R root, V defaultValue) {
        V v = get(root);
        return v == null ? defaultValue : v;
    }

    V get(R root);

    default V put(R root, V value) {
        throw new UnsupportedOperationException("put() is not implemented");
    }

//    V ensurePut(R root, V value);


    /// static

    @SuppressWarnings("unchecked")
    static <R, V> CompiledPath<R, V> compile(String pathExpr, Class<R> rootClazz, Class<V> valueClazz) {
        return (CompiledPath<R, V>) BytecodeCompilers.compilePath(pathExpr, rootClazz, valueClazz, false);
    }

    @SuppressWarnings("unchecked")
    static <R, V> CompiledPath<R, V> compile(String pathExpr, TypeReference<R> rootType, TypeReference<V> valueType) {
        return (CompiledPath<R, V>) BytecodeCompilers.compilePath(pathExpr, rootType.getType(), valueType.getType(), false);
    }


}
