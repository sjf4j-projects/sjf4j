package org.sjf4j.annotation.node;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Single subtype mapping inside {@link OneOf}.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface OneOfMapping {
    /**
     * Concrete target subtype, distinct from the declared target type.
     */
    Class<?> type();

    /**
     * Accepted discriminator values for this subtype.
     */
    String[] when() default {}; // "c" or {"a","b"}
}
