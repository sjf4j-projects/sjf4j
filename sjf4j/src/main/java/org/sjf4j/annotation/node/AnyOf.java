package org.sjf4j.annotation.node;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AnyOf {
    Mapping[] value();
    @Retention(RetentionPolicy.RUNTIME)
    @interface Mapping {
        Class<?> value();
        String[] when() default {};         // "c" or {"a","b"}
    }

    String key() default "";

    String path() default "";               // Only works in Scope.SELF

    Scope scope() default Scope.SELF;
    enum Scope { SELF, PARENT /*, ROOT*/ }

    OnNoMatch onNoMatch() default OnNoMatch.FAIL;
    enum OnNoMatch { FAIL, FAILBACK_NULL }

}
