package org.sjf4j.annotation.mapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Configures an update {@link CompiledMapper} method. */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface MappingConfig {
    NullValuePolicy nulls() default NullValuePolicy.SET;
}
