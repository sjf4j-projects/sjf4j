package org.sjf4j.annotation.path;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a direct single-path write method inside a {@link CompiledPath}
 * interface implementation.
 *
 * <p>
 * The first method parameter is the root object and the last parameter is the
 * value to write. Parameters between them may be referenced from the path as
 * {@code {name}} placeholders. The final parent must already exist; missing
 * parents fail with an SJF4J JSON exception. Non-void methods return the
 * previous value when the target shape exposes one, while append targets return
 * {@code null}.</p>
 *
 * <p>Static container types are used directly in generated code: maps use
 * {@code put}, lists use {@code set} or append-at-size, Java arrays use direct
 * index assignment, and {@code JsonObject}/{@code JsonArray} use their native
 * operations. Only {@code Object}-typed parents fall back to dynamic node
 * helpers.</p>
 *
 * <p>The value parameter type must be assignable to the final target value type.
 * Java arrays cannot be appended to with {@code [+]}.</p>
 *
 * <pre>{@code
 * @CompiledPath
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
     * Single-target JSONPath expression identifying the value to replace or append.
     */
    String value();
}
