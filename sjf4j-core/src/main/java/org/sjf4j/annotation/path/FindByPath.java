package org.sjf4j.annotation.path;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a multi-target find method inside a {@link CompiledNavigator}
 * interface implementation.
 *
 * <p>The first method parameter is the root object. The method return type
 * must be {@code List<T>} where {@code T} is the element type resolved from
 * the path expression.</p>
 *
 * <p>Direct code generation supports statically typed name/index accesses and
 * composable wildcard ({@code [*]}), slice, and union selectors, including
 * nested combinations and unions of POJO properties. A filter can generate
 * direct iteration with its predicate evaluated by a cached runtime expression
 * when {@link CompiledNavigator#fallback()} is enabled. Descendant and dynamic
 * paths require explicit runtime fallback; unsupported shapes fail at compile
 * time when fallback is disabled.</p>
 *
 * <pre>{@code
 * @CompiledNavigator
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
