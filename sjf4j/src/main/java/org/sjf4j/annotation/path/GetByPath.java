package org.sjf4j.annotation.path;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a direct single-path read method inside a {@link CompiledPath}
 * interface implementation.
 *
 * <p>
 * The first method parameter is the root object. Additional parameters may be
 * referenced from the path as {@code {name}} placeholders: {@code String}
 * parameters address object keys and {@code int} parameters address array/list
 * indexes. The method return type must be assignable from the resolved path
 * value type.</p>
 *
 * <p>Missing object/array parents return {@code null} for reference return
 * types. Primitive return types cannot represent missing values, so generated
 * methods throw an SJF4J JSON exception when the path is missing.</p>
 *
 * <p>The path must identify one value. Root-only and multi-target JSONPath
 * expressions are rejected by the annotation processor.</p>
 *
 * <pre>{@code
 * @CompiledPath
 * interface Users {
 *     @GetByPath("$.users[{idx}].name")
 *     String name(Model root, int idx);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface GetByPath {
    /**
     * Single-target JSONPath expression to read from the root parameter.
     */
    String value();
}
