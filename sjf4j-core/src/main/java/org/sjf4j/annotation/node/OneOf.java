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
 *     <li>Annotate a regular field to make only that location polymorphic</li>
 *     <li>Creator parameters and their bound fields may override subtype mappings with {@code scope=CURRENT}; {@code PARENT} and {@code ROOT} are unsupported for creators</li>
 *     <li>CURRENT-scope OneOf may be nested in subtype properties, including through lists and maps or self-recursively</li>
 *     <li>Selected subtypes must not declare another class-level OneOf or direct PARENT-scope OneOf fields</li>
 *     <li>Use {@link #discriminatorKey()} or {@link #discriminatorPath()} with {@link OneOfMapping#when()} for discriminator-based selection</li>
 *     <li>Omit discriminators to resolve by raw JSON kind when candidate shapes are distinct</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface OneOf {
    /**
     * Candidate subtype mappings.
     */
    OneOfMapping[] mappings();

    /**
     * Discriminator property name read from the current object.
     */
    String discriminatorKey() default "";

    /**
     * JSONPath used to locate the discriminator value.
     * Supported only with {@link Scope#CURRENT}.
     */
    String discriminatorPath() default "";

    /**
     * Scope for discriminator lookup.
     */
    Scope scope() default Scope.CURRENT;

    /**
     * Scope for discriminator lookup.
     *
     * {@link Scope#PARENT} is supported for property-level discriminators in
     * streaming binding. {@link Scope#ROOT} is declared but not supported.
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
