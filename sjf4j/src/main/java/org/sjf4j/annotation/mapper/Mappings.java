package org.sjf4j.annotation.mapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for repeated {@link Mapping} declarations.
 *
 * <p>Users normally write repeated {@code @Mapping} annotations directly on a
 * {@link CompiledMapper} method. The Java compiler uses this annotation as the
 * repeatable container.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Mappings {
    /**
     * Repeated mapping customizations for one mapper method.
     */
    Mapping[] value();
}
