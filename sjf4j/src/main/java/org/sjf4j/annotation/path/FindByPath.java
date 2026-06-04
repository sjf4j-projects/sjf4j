package org.sjf4j.annotation.path;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a multi-target find method inside a {@link CompiledPath}
 * interface implementation.
 *
 * <p>The first method parameter is the root object. The method return type
 * must be {@code List<T>} where {@code T} is the element type resolved from
 * the path expression.</p>
 *
 * <p>Currently supports root, simple wildcard ({@code [*]}), and union
 * ({@code [0,2]} / {@code ['a','b']}) path expressions that the annotation
 * processor can compile directly. Unsupported path shapes fail at compile time.</p>
 *
 * <pre>{@code
 * @CompiledPath
 * interface Users {
 *     @FindByPath("$.users[*].name")
 *     List<String> names(Model root);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface FindByPath {
    /**
     * JSONPath expression that may match multiple target locations.
     */
    String value();
}
