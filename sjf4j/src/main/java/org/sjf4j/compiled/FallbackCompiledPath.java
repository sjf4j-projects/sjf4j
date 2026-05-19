package org.sjf4j.compiled;

import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;

import java.util.Objects;


public class NonCompiledPath<R, V> {

    protected final String expr;
    protected final JsonPath path;
    protected final PathSegment lastSegment;
    protected final boolean append;
    protected final Class<R> rootType;
    protected final Class<V> valueType;


    protected NonCompiledPath(String expr, Class<R> rootClazz, Class<V> valueClazz) {
        Objects.requireNonNull(expr, "expr");
        JsonPath path = JsonPath.parse(expr);
        _validatePath(path);
        this.expr = expr;
        this.path = path;
        this.lastSegment = path.tail();
        this.append = lastSegment instanceof PathSegment.Append;
        this.rootType = rootClazz;
        this.valueType = valueClazz;
    }

    
    public static <R, V> NonCompiledPath<R, V> compile(String expr, Class<R> rootClazz, Class<V> valueClazz) {
        return new NonCompiledPath<>(expr, rootClazz, valueClazz);
    }


    @SuppressWarnings("unchecked")
    public static <R, V> NonCompiledPath<R, V> compile(String expr, Class<R> rootType) {
        JsonPath fallback = JsonPath.parse(expr);
        if (OPTIMIZER != null) {
            // Core can access protected JsonPath.segments because they share package
            PathSegment[] segments = fallback.segments;
            try {
                NonCompiledPath<R, V> opt = OPTIMIZER.optimize(expr, fallback, segments, rootType);
                if (opt != null) return opt;
            } catch (Exception ignored) {
                // optimization failed — fall through to RuntimeCompiledPath
            }
        }
        return new RuntimeCompiledPath<>(fallback.toString(), fallback);
    }

    /**
     * Returns the original path expression.
     */
    public final String expr() {
        return expr;
    }

    /**
     * Returns the validated runtime fallback path.
     */
    public final JsonPath fallbackPath() {
        return path;
    }

    /**
     * Returns true when the terminal segment is append-only.
     */
    public final boolean isAppend() {
        return append;
    }

    /**
     * Reads the terminal value without conversion.
     */
    @SuppressWarnings("unchecked")
    public V get(R root) {
        _ensureReadable("get()");
        return (V) path.getNode(root);
    }

    /**
     * Reads the terminal value or returns the default when missing.
     */
    public V get(R root, V defaultValue) {
        V value = get(root);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns true when the target location exists.
     */
    public boolean contains(R root) {
        if (append) {
            return false;
        }
        return path.contains(root);
    }

    /**
     * Returns true when the target location exists and is non-null.
     */
    public boolean hasNonNull(R root) {
        _ensureReadable("hasNonNull()");
        return get(root) != null;
    }

    /**
     * Writes a value at the target location and returns the previous value when available.
     */
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

    /**
     * Writes only when the target location does not already exist.
     */
    @SuppressWarnings("unchecked")
    public V ensurePutIfAbsent(R root, V value) {
        return (V) path.ensurePutIfAbsent(root, value);
    }

    /**
     * Applies JSON Patch add semantics at the target location.
     */
    public void add(R root, V value) {
        path.add(root, value);
    }

    /**
     * Replaces the existing target value and returns the previous value.
     */
    @SuppressWarnings("unchecked")
    public V replace(R root, V value) {
        _ensureReplaceable("replace()");
        return (V) path.replace(root, value);
    }

    /**
     * Removes the target value and returns the previous value.
     */
    @SuppressWarnings("unchecked")
    public V remove(R root) {
        _ensureReplaceable("remove()");
        return (V) path.remove(root);
    }

    @Override
    public final String toString() {
        return expr;
    }

    private static void _validatePath(JsonPath path) {
        if (path.depth() < 2) {
            throw new JsonException("CompiledPath requires a non-root target path: '" + path + "'");
        }
        if (!(path.head() instanceof PathSegment.Root)) {
            throw new JsonException("CompiledPath requires a rooted path starting from '$' or '/': '" + path + "'");
        }
        if (!path.isSingle()) {
            throw new JsonException("CompiledPath only supports a single target path with Name/Index/Append segments: '" + path + "'");
        }
        PathSegment last = path.segments[path.segments.length - 1];
        if (!(last instanceof PathSegment.Name || last instanceof PathSegment.Index || last instanceof PathSegment.Append)) {
            throw new JsonException("CompiledPath requires a terminal Name/Index/Append segment: '" + path + "'");
        }
    }

    protected final void _ensureReadable(String opName) {
        if (append) {
            throw new JsonException("Cannot call " + opName + " on append-only CompiledPath '" + expr + "'");
        }
    }

    protected final void _ensureReplaceable(String opName) {
        if (append) {
            throw new JsonException("Cannot call " + opName + " on append-only CompiledPath '" + expr + "'");
        }
    }

    protected final R _requireRoot(R root) {
        return Objects.requireNonNull(root, "root");
    }

    private static final class RuntimeCompiledPath<R, V> extends NonCompiledPath<R, V> {
        private RuntimeCompiledPath(String expr, JsonPath fallbackPath) {
            super(expr, fallbackPath);
        }
    }
}
