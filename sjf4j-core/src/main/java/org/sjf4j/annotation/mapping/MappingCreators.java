package org.sjf4j.annotation.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for repeated {@link MappingCreator} declarations.
 *
 * <p>Users normally repeat {@code @MappingCreator} directly on a compiled
 * mapper interface or one of its abstract methods. The Java compiler uses
 * this annotation as their repeatable container.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface MappingCreators {
    /** Creator declarations for one mapper interface or method. */
    MappingCreator[] value();
}
