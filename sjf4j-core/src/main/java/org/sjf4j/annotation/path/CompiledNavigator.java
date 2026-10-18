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
 * {@link GetByPath}, {@link FindByPath}, {@link PutByPath},
 * {@link PutIfParentPresentByPath}, {@link EnsurePutByPath}, or
 * {@link EnsurePutIfAbsentByPath}. Default methods remain inherited by the
 * generated implementation; static interface methods stay on the interface.
 * Neither kind needs a generated implementation. The annotated interface and
 * generated methods cannot declare type parameters.</p>
 *
 * <p>The generated class is named after the interface with the {@code _Impl}
 * suffix. Runtime lookup is normally done through
 * {@code org.sjf4j.CompiledInstances.global(...)}.</p>
 *
 * <pre>{@code
 * @CompiledNavigator
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
public @interface CompiledNavigator {
}
