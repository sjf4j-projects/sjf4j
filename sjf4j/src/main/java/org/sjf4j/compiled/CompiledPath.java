package org.sjf4j.compiled;

import org.sjf4j.node.TypeReference;

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
//
//    V ensurePut(R root, V value);


    @SuppressWarnings("unchecked")
    static <R, V> CompiledPath<R, V> compile(String expr, TypeReference<R> rootType, TypeReference<V> valueType) {
        return (CompiledPath<R, V>) FallbackCompiledPath.compile(expr, rootType.getType(), valueType.getType());
    }

    @SuppressWarnings("unchecked")
    static <R, V> CompiledPath<R, V> compile(String expr, Class<R> rootClazz, Class<V> valueClazz) {
        return (CompiledPath<R, V>) FallbackCompiledPath.compile(expr, rootClazz, valueClazz);
    }


}
