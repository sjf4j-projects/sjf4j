package org.sjf4j.annotation.jojo;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Source-level annotation for code generation of JOJO models.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Jojo {
    Property[] value();
    Class<?> ref() default Object.class;
}
