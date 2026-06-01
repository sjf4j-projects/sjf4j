package org.sjf4j.annotation.path;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a direct single-path write method that creates missing intermediate
 * containers before writing the final value.
 *
 * <p>
 * Method parameters follow {@link PutByPath}. Missing intermediate object-like
 * nodes are allocated as {@code LinkedHashMap}, {@code JsonObject}, concrete map
 * or POJO types as appropriate; array-like nodes are allocated as
 * {@code ArrayList}, {@code JsonArray}, or concrete list types. Concrete
 * intermediate types must have an accessible no-argument constructor. The final
 * write returns the previous value when available; append targets return
 * {@code null}.</p>
 *
 * <p>The root parameter itself is required and is never created by the generated
 * method. Missing intermediate Java arrays are not created because array size is
 * fixed; use a list-like container for ensured array-shaped paths.</p>
 *
 * <p>If an ensured list index equals the current size, the generated method
 * appends the created container. Larger gaps are rejected with an SJF4J JSON
 * exception.</p>
 *
 * <pre>{@code
 * @CompiledPath
 * interface Users {
 *     @EnsurePutByPath("$.profile.contacts[0].email")
 *     String email(User root, String value);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface EnsurePutByPath {
    /**
     * Single-target JSONPath expression to create as needed and then write.
     */
    String value();
}
