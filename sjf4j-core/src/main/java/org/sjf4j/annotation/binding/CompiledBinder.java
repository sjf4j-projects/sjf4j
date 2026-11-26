package org.sjf4j.annotation.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface whose structural binding operations are generated at
 * compile time.
 *
 * <p>The format and backend are fixed for one generated binder interface.
 * Binding operations are declared with {@link Read} and {@link Write}.
 * Each abstract method must have exactly one of these annotations.
 * Binding failures, including I/O errors, are reported as unchecked
 * {@link org.sjf4j.exception.BindingException}. The interface itself cannot be
 * generic; inherited abstract methods must resolve to concrete types.</p>
 *
 * <pre>{@code
 * @CompiledBinder
 * interface UserJson {
 *     @Read User read(String json);
 *     @Write String write(User user);
 * }
 * }</pre>
 *
 * <p>Generated implementations have the {@code _Impl} suffix and can be
 * created through {@link org.sjf4j.CompiledInstances#create(Class)}.
 * A concrete backend Binder can still be injected using
 * {@code CompiledInstances.create(UserJson.class, jackson2Binder)} to customize
 * native parser and generator settings, not compiled binding semantics.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface CompiledBinder {

    /**
     * Structured format handled by this binder.
     */
    Format format() default Format.JSON;

    /**
     * Backend used by generated code.
     *
     * <p>For JSON, {@link Backend#AUTO} prefers Jackson 3, Jackson 2,
     * Gson, Fastjson2, JSON-P, then the built-in simple backend, selecting the
     * first usable backend visible on the compilation classpath.</p>
     */
    Backend backend() default Backend.AUTO;

    /**
     * Default null-property policy for generated {@link Write} operations.
     * This policy is fixed at compilation, including runtime fallback.
     */
    boolean includeNulls() default true;

    /** Default precision policy for generated read operations, including runtime fallback. */
    boolean exactNumbers() default false;

    /** Additional precompiled {@link org.sjf4j.annotation.node.NodeRegistry} interfaces. */
    Class<?>[] registries() default {};
}
