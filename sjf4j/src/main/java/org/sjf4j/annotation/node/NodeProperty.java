package org.sjf4j.annotation.node;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares JSON property name and aliases for a field or parameter.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeProperty {
    String value() default "";

    String[] aliases() default {};
}
