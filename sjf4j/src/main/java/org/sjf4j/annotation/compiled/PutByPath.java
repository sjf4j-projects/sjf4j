package org.sjf4j.annotation.compiled;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a direct single-path write method inside a {@link CompiledNodes}
 * interface implementation.
 * <p>
 * The first method parameter is the root object and the last parameter is the
 * value to write. Parameters between them may be referenced from the path as
 * {@code {name}} placeholders. The final parent must already exist; missing
 * parents fail with an SJF4J JSON exception. Non-void methods return the
 * previous value when the target shape exposes one, while append targets return
 * {@code null}.
 *
 * <pre>{@code
 * @CompiledNodes
 * interface Users {
 *     @PutByPath("$.users[{idx}].name")
 *     String name(Model root, int idx, String value);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface PutByPath {
    /**
     * Single-target JSONPath expression to write.
     */
    String value();
}
