package org.sjf4j.annotation.path;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a direct single-path write method that writes only when the final
 * parent container already exists.
 * <p>
 * Method parameters follow {@link PutByPath}: root first, value last, and any
 * path placeholders in between. If an intermediate parent is missing or
 * {@code null}, the generated method returns {@code null} for non-void methods
 * or returns immediately for {@code void} methods. Once the parent exists, the
 * final write follows {@link PutByPath} semantics.
 *
 * <pre>{@code
 * @CompiledPath
 * interface Users {
 *     @PutIfParentPresentByPath("$.profile.email")
 *     String email(User root, String value);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface PutIfParentPresentByPath {
    /**
     * Single-target JSONPath expression to write when the parent exists.
     */
    String value();
}
