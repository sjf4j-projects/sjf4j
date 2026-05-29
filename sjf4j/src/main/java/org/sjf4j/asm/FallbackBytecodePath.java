package org.sjf4j.asm;

import org.sjf4j.path.JsonPath;

import java.util.function.BiFunction;


public class FallbackBytecodePath<R, V> implements BytecodePath<R, V> {

    protected final JsonPath path;
    protected final Class<R> rootClazz;
    protected final Class<V> valueClazz;


    public FallbackBytecodePath(JsonPath path, Class<R> rootClazz, Class<V> valueClazz) {
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

    @Override
    @SuppressWarnings("unchecked")
    public V putIfParentPresent(R root, V value) {
        return (V) path.putIfParentPresent(root, value);
    }


    /**
     * Ensures intermediate containers exist and writes the target value.
     */
    @Override
    @SuppressWarnings("unchecked")
    public V ensurePut(R root, V value) {
        return (V) path.ensurePut(root, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public V ensurePutIfAbsent(R root, V value) {
        return (V) path.ensurePutIfAbsent(root, value);
    }

    @Override
    public int compute(R root, BiFunction<Object, Object, Object> computer) {
        return path.compute(root, computer);
    }


}
