package org.sjf4j.annotation.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Marks a non-static, no-argument encoder on a {@link NodeValue} type. Its
 * result must be a supported raw OBNT type such as String, Number, Boolean,
 * Map, or List; returning {@code null} represents JSON null at runtime.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValueToRaw {}
