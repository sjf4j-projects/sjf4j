package org.sjf4j.annotation.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Marks an optional non-static, no-argument copy method on a
 * {@link NodeValue} type. It must return the annotated type. Without a copy
 * method, value copying retains the existing value reference.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValueCopy {}
