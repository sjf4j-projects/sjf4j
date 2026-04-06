package org.sjf4j.annotation.node;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares polymorphic binding across multiple candidate subtypes.
 * <p>
 * Use this when a target type may deserialize into one of several concrete Java
 * types. SJF4J can resolve the target either by discriminator values from the
 * input object, or by the raw JSON/OBNT shape when no discriminator is used.
 *
 * <p>Common usage styles:
 * <ul>
 *     <li>Annotate a base type to make reads of that type polymorphic</li>
 *     <li>Annotate a field or creator parameter to make only that location polymorphic</li>
 *     <li>Use {@link #key()} or {@link #path()} with {@link Mapping#when()} for discriminator-based selection</li>
 *     <li>Omit discriminators to resolve by raw JSON kind when candidate shapes are distinct</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AnyOf {
    /**
     * Candidate subtype mappings.
     */
    Mapping[] value();

    /**
     * Single subtype mapping inside {@link AnyOf}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Mapping {
        /**
         * Concrete target subtype.
         */
        Class<?> value();

        /**
         * Accepted discriminator values for this subtype.
         */
        String[] when() default {}; // "c" or {"a","b"}
    }

    /**
     * Discriminator key read from the current object.
     */
    String key() default "";

    /**
     * Discriminator path read from the current scope.
     */
    String path() default "";   // Only works in Scope.CURRENT

    /**
     * Scope used when resolving {@link #path()}.
     */
    Scope scope() default Scope.CURRENT;

    /**
     * Path resolution scope for discriminator lookup.
     *
     * TODO: ROOT is currently not supported.
     */
    enum Scope {CURRENT, PARENT, ROOT }

    /**
     * Behavior when no mapping matches the input.
     */
    OnNoMatch onNoMatch() default OnNoMatch.FAIL;

    /**
     * No-match handling policy.
     */
    enum OnNoMatch { FAIL, FAILBACK_NULL }

}
