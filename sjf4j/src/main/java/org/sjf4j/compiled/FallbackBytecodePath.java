package org.sjf4j.compiled;

import org.sjf4j.path.JsonPath;

import java.util.function.BiFunction;


/**
 * Reflective {@link BytecodePath} implementation backed by a parsed {@link JsonPath}.
 *
 * <p>This implementation requires no optional bytecode compiler module and delegates every
 * operation to {@code JsonPath}. Use it when dynamic path behavior or the full missing-path
 * semantics of {@code JsonPath} are required instead of compiled direct access.
 *
 * <p>The supplied root and value classes define the declared types used for path reads. Instances
 * are reusable when the underlying path and accessed object graph are safe for concurrent use.
 */
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
