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
 * Binding operations are declared with {@link ReadFrom} and {@link WriteTo}.
 * Each abstract method must have exactly one of these annotations and declare
 * {@link java.io.IOException} or a supertype. The interface itself cannot be
 * generic; inherited abstract methods must resolve to concrete types.</p>
 *
 * <pre>{@code
 * @CompiledBinder
 * interface UserJson {
 *     @ReadFrom User read(String json) throws IOException;
 *     @WriteTo String write(User user) throws IOException;
 * }
 * }</pre>
 *
 * <p>Generated implementations have the {@code _Impl} suffix and can be
 * obtained through {@link org.sjf4j.CompiledInstances#global(Class)}
 * or created with custom settings using
 * {@link org.sjf4j.CompiledInstances#create(Class, org.sjf4j.RuntimeContext)}.
 * Native backend configuration may also be injected using
 * {@code CompiledInstances.create(UserJson.class, gson)} or the generated
 * implementation's strongly typed native-backend constructor.</p>
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
     * <p>For JSON, {@link Backend#AUTO} prefers Jackson 3, Jackson 2,
     * Gson, Fastjson2, JSON-P, then the built-in simple backend, selecting the
     * first usable backend visible on the compilation classpath.</p>
     */
    Backend backend() default Backend.AUTO;
}
