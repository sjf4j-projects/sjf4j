package org.sjf4j.annotation.compiled;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a direct single-path write-if-absent method that creates missing
 * intermediate containers before checking the final value.
 * <p>
 * Method parameters follow {@link PutByPath}. The final value is written only
 * when the current target is absent or {@code null}; an existing non-null value
 * is returned unchanged. Append targets always append and return {@code null}.
 * Because absent writes return {@code null}, primitive method return types are
 * not supported.
 *
 * <pre>{@code
 * @CompiledNodes
 * interface Users {
 *     @EnsurePutIfAbsentByPath("$.settings.theme")
 *     String defaultTheme(User root, String value);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface EnsurePutIfAbsentByPath {
    /**
     * Single-target JSONPath expression to ensure and write if absent.
     */
    String value();
}
