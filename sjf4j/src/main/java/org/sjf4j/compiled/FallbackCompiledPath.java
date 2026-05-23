package org.sjf4j.compiled;

import org.sjf4j.exception.JsonException;
import org.sjf4j.node.Types;
import org.sjf4j.path.JsonPath;

import java.lang.reflect.Type;
import java.util.Objects;


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
    @SuppressWarnings("unchecked")
    public V ensurePut(R root, V value) {
        return (V) path.ensurePut(root, value);
    }


    /// static

    static CompiledPath<?, ?> compile(String expr, Type rootType, Type valueType) {
        Objects.requireNonNull(expr, "expr");
        Objects.requireNonNull(rootType, "rootType");
        Objects.requireNonNull(valueType, "valueType");
        Class<?> rootClazz = Types.rawClazz(rootType);
        Class<?> valueClazz = Types.rawClazz(valueType);

        JsonPath path = JsonPath.parse(expr);
        if (path.length() < 2) {
            throw new JsonException("CompiledPath requires a non-root target path: '" + path + "'");
        }
        if (!path.isSinglePut()) {
            throw new JsonException("CompiledPath only supports a single target path with Name/Index/Append segments: '" + path + "'");
        }
//
//        // validate class chain
//        Class<?> currentClazz = rootClazz;
//        PathSegment[] segments = path.segments();
//        for (int i = 1; i < segments.length; i++) {
//            PathSegment segment = segments[i];
//            if (segment instanceof PathSegment.Name) {
//
//            } else if (segment instanceof PathSegment.Index) {
//
//            } else if (segment instanceof PathSegment.Append) {
//
//            } else {
//                throw new AssertionError(CompiledPath.class);
//            }
//            if (currentClazz == Object.class) break;
//
//        }
//        if (currentClazz != Object.class && !valueClazz.isAssignableFrom(currentClazz)) {
//            throw new JsonException("");
//        }

        // Optional compilers may choose stricter fail-fast semantics than the reflective fallback,
        // including terminal type validation and unsupported-shape rejection.
        CompiledPath<?, ?> compiledPath = BytecodeCompilers.compilePath(path, rootType, valueType);
        if (compiledPath != null) return compiledPath;

        return new FallbackCompiledPath<>(path, rootClazz, valueClazz);
    }

}
