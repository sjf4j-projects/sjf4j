package org.sjf4j.annotation;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.SOURCE)
public @interface Prop {
    String name();
    Class<?> type();
    boolean getter() default true;
    boolean setter() default true;
    String defaultValue() default "";
    String comment() default "";
    boolean deprecated() default false;
}
