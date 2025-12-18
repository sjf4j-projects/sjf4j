package org.sjf4j.annotation.jojo;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Jojo {
    Property[] value();
    Class<?> ref() default Object.class;
}

