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
 * Binding operations are declared with {@link ReadFrom} and {@link WriteTo}.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface CompiledBinder {

    /**
     * Structured format handled by this binder.
     */
    BindingFormat format() default BindingFormat.JSON;

    /**
     * Backend used by generated code.
     *
     * <p>For JSON, {@link BindingBackend#AUTO} prefers Jackson 3, Jackson 2,
     * Gson, Fastjson2, JSON-P, then the built-in simple backend, selecting the
     * first usable backend visible on the compilation classpath.</p>
     */
    BindingBackend backend() default BindingBackend.AUTO;
}
