package org.sjf4j.annotation.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Marks a decoder for a {@link NodeValue} type. A constructor must accept
 * exactly one raw value; a method must be static, accept exactly one raw value,
 * and return the annotated value type. The raw parameter type must match the
 * boxed return type of {@link ValueToRaw}. JSON null is passed as {@code null}.
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface RawToValue {}
