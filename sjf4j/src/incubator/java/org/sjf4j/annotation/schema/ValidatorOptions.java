package org.sjf4j.annotation.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface ValidatorOptions {
    /**
     * Enables strict JSON Schema format assertion in runtime fallback paths.
     */
    boolean strictFormat() default true;

    /**
     * Allows runtime fallback when a schema cannot be fully compiled safely.
     */
    boolean fallback() default true;
}
