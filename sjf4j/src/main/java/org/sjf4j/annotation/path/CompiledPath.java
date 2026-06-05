package org.sjf4j.annotation.path;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface for compile-time JSON path implementation generation.
 *
 * <p>Abstract methods in the interface are implemented by the SJF4J annotation
 * processor when they are annotated with one path operation annotation such as
 * {@link GetByPath}, {@link PutByPath}, {@link PutIfParentPresentByPath},
 * {@link EnsurePutByPath}, or {@link EnsurePutIfAbsentByPath}. Default and
 * static methods are copied through by normal Java interface dispatch and are
 * not generated.</p>
 *
 * <p>The generated class is named after the interface with the {@code _Impl}
 * suffix. Runtime lookup is normally done through
 * {@code org.sjf4j.compiled.CompiledNodes}.</p>
 *
 * <pre>{@code
 * @CompiledPath
 * interface UserPaths {
 *     @GetByPath("$.profile.name")
 *     String name(User user);
 *
 *     @PutByPath("$.tags[{idx}]")
 *     String tag(User user, int idx, String value);
 * }
 * }</pre>
 *
 * <p>This annotation is retained only in source because it is consumed by the
 * annotation processor.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface CompiledPath {
}
