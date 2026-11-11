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
 * nested combinations and unions of POJO properties. Dynamically typed OBNT
 * nodes are traversed through NodeWalker, and descendant selection uses
 * generated recursive helpers. Filter traversal is generated, while its
 * predicate is a cached runtime expression when
 * {@link CompiledNavigator#fallback()} is enabled. There is no whole-path
 * runtime fallback; unsupported operations fail at compile time.</p>
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
