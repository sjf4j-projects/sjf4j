package org.sjf4j.path;

import org.sjf4j.exception.JsonException;

import java.util.Objects;

/**
 * Typed single-target path with optional compiled/native access.
 *
 * <p>CompiledPath is the typed counterpart of {@link JsonPath}. It is restricted
 * to a single rooted target path made only of {@link PathSegment.Root},
 * {@link PathSegment.Name}, {@link PathSegment.Index}, and
 * {@link PathSegment.Append} segments, so later runtime code generation can
 * replace the fallback implementation with generated POJO/JOJO-native access code.
 *
 * <p>The base implementation delegates to a validated fallback {@link JsonPath}.
 * Generated subclasses may override hot operations directly while preserving the
 * same public contract.
 */
public abstract class CompiledPath<R, V> {

    protected final String expr;
    protected final JsonPath fallbackPath;
    protected final PathSegment lastSegment;
    protected final boolean append;

    protected CompiledPath(String expr, JsonPath fallbackPath) {
        Objects.requireNonNull(expr, "expr");
        Objects.requireNonNull(fallbackPath, "fallbackPath");
        _validateFallback(fallbackPath);
        this.expr = expr;
        this.fallbackPath = fallbackPath;
        this.lastSegment = fallbackPath.segments[fallbackPath.segments.length - 1];
        this.append = lastSegment instanceof PathSegment.Append;
    }

    /**
     * Parses a textual path into a fallback-backed compiled path.
     */
    public static <R, V> CompiledPath<R, V> parse(String expr) {
        JsonPath path = JsonPath.parse(expr);
        return new RuntimeCompiledPath<>(path.toString(), path);
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
        return fallbackPath;
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
        return (V) fallbackPath.getNode(root);
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
        return fallbackPath.contains(root);
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
        return (V) fallbackPath.put(root, value);
    }

    /**
     * Ensures intermediate containers exist and writes the target value.
     */
    @SuppressWarnings("unchecked")
    public V ensurePut(R root, V value) {
        return (V) fallbackPath.ensurePut(root, value);
    }

    /**
     * Writes only when the target location does not already exist.
     */
    @SuppressWarnings("unchecked")
    public V ensurePutIfAbsent(R root, V value) {
        return (V) fallbackPath.ensurePutIfAbsent(root, value);
    }

    /**
     * Applies JSON Patch add semantics at the target location.
     */
    public void add(R root, V value) {
        fallbackPath.add(root, value);
    }

    /**
     * Replaces the existing target value and returns the previous value.
     */
    @SuppressWarnings("unchecked")
    public V replace(R root, V value) {
        _ensureReplaceable("replace()");
        return (V) fallbackPath.replace(root, value);
    }

    /**
     * Removes the target value and returns the previous value.
     */
    @SuppressWarnings("unchecked")
    public V remove(R root) {
        _ensureReplaceable("remove()");
        return (V) fallbackPath.remove(root);
    }

    @Override
    public final String toString() {
        return expr;
    }

    private static void _validateFallback(JsonPath fallbackPath) {
        if (fallbackPath.depth() < 2) {
            throw new JsonException("CompiledPath requires a non-root target path: '" + fallbackPath + "'");
        }
        if (!(fallbackPath.head() instanceof PathSegment.Root)) {
            throw new JsonException("CompiledPath requires a rooted path starting from '$' or '/': '" + fallbackPath + "'");
        }
        if (!fallbackPath.isSingle()) {
            throw new JsonException("CompiledPath only supports a single target path with Name/Index/Append segments: '" + fallbackPath + "'");
        }
        PathSegment last = fallbackPath.segments[fallbackPath.segments.length - 1];
        if (!(last instanceof PathSegment.Name || last instanceof PathSegment.Index || last instanceof PathSegment.Append)) {
            throw new JsonException("CompiledPath requires a terminal Name/Index/Append segment: '" + fallbackPath + "'");
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

    private static final class RuntimeCompiledPath<R, V> extends CompiledPath<R, V> {
        private RuntimeCompiledPath(String expr, JsonPath fallbackPath) {
            super(expr, fallbackPath);
        }
    }
}
