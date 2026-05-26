package org.sjf4j.compiled;

import org.sjf4j.path.JsonPath;


public class FallbackCompiledPath<R, V> implements CompiledPath<R, V> {

    protected final JsonPath path;
    protected final Class<R> rootClazz;
    protected final Class<V> valueClazz;


    public FallbackCompiledPath(JsonPath path, Class<R> rootClazz, Class<V> valueClazz) {
        this.path = path;
        this.rootClazz = rootClazz;
        this.valueClazz = valueClazz;
    }

    @Override
    public String expr() {
        return path.toExpr();
    }

    @Override
    public V get(R root) {
        return path.get(root, valueClazz);
    }

    @Override
    @SuppressWarnings("unchecked")
    public V put(R root, V value) {
        return (V) path.put(root, value);
    }


    /**
     * Ensures intermediate containers exist and writes the target value.
     */
    @Override
    @SuppressWarnings("unchecked")
    public V ensurePut(R root, V value) {
        return (V) path.ensurePut(root, value);
    }


}
