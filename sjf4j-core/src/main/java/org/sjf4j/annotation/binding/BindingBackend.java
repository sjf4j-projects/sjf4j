package org.sjf4j.annotation.binding;

/**
 * Runtime backend selected for a {@link CompiledBinder}.
 *
 * <p>{@link #AUTO} is resolved by the annotation processor from the backends
 * visible on the current compilation classpath. Backend compatibility is
 * format-specific and is validated during compilation.</p>
 */
public enum BindingBackend {
    AUTO,

    JACKSON3,
    JACKSON2,
    GSON,
    FASTJSON2,
    JSONP,
    SNAKE,

    SIMPLE
}
