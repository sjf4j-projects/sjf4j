package org.sjf4j.annotation.binding;

/**
 * Backend baked into a generated {@link CompiledBinder} implementation.
 *
 * <p>{@link #AUTO} is resolved by the annotation processor from the backends
 * visible on the current compilation classpath. Backend compatibility is
 * format-specific and is validated during compilation.</p>
 */
public enum Backend {
    AUTO,

    JACKSON3,
    JACKSON2,
    GSON,
    FASTJSON2,
    JSONP,
    SNAKE,

    SIMPLE
}
