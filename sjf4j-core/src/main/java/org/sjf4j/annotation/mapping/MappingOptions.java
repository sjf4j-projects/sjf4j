package org.sjf4j.annotation.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures one {@link CompiledMapper} method.
 *
 * <p>Options are source-retained because they affect only generation of the
 * method currently being compiled. They are not inherited across already
 * compiled mapper interfaces; use {@link MappingCreator} for inherited target
 * creation rules.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface MappingOptions {

    /**
     * Preferred converter methods for automatic conversion points in this method.
     *
     * <p>References use the form {@code "Mapper::method"}. Use
     * {@code "this::method"} for a method declared by the current mapper, or
     * the simple or qualified name of an imported mapper.
     * These are preferences, not forced conversions. If a source value is already
     * assignable to the target type, it is assigned directly. Otherwise the
     * processor tries these references in array order before normal automatic
     * local/imported mapper lookup and built-in strict conversions. References
     * that exist but are incompatible with a given conversion point are skipped;
     * references that cannot be resolved are errors.</p>
     */
    String[] using() default {};


    /**
     * Null handling for mutable create targets and update targets.
     *
     * <p>{@link NullValuePolicy#IGNORE} skips null source values when assigning
     * mutable properties. Constructor and record create targets cannot use
     * {@code IGNORE} because constructor arguments must be supplied.</p>
     */
    NullValuePolicy nulls() default NullValuePolicy.SET_TO_NULL;

    /** Default array-like update behavior for target properties and nested containers. */
    ArrayPolicy arrays() default ArrayPolicy.CLEAR_ADD;

    /** Default object-like update behavior for target properties and nested containers. */
    ObjectPolicy objects() default ObjectPolicy.PUT;

}
