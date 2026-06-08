package org.sjf4j.annotation.mapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for repeated {@link MappingCreator} declarations.
 *
 * <p>Users normally write repeated {@code @MappingCreator} annotations directly
 * on a {@link CompiledMapper} interface. The Java compiler uses this annotation
 * as the repeatable container.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface MappingCreators {
    /** Creator declarations for one mapper interface. */
    MappingCreator[] value();
}
