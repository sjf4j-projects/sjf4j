package org.sjf4j.annotation.mapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Target-path mapping that writes only when the final target parent exists.
 *
 * <p>This source-retained API mirrors the main {@link Mapping} value-selection
 * members for path-aware mapper generation. The {@link #target()} value must be
 * a target path: JSONPath beginning with {@code $} or JSON Pointer beginning
 * with {@code /}. Plain property names are handled by {@link Mapping}; plain
 * dotted names are literal property/key names, not nested target paths.</p>
 *
 * <p>Semantics are PutIfParentPresentByPath-like: if the final parent
 * object/container for the target path is missing, the write is skipped.
 * Earlier parents are still traversed according to normal strict target path
 * rules.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface MappingIfParentPresent {
    /** JSONPath or JSON Pointer target path to write. */
    String target() default "";

    /** Source property name or source path; defaults to the target leaf name. */
    String source() default "";

    /** Source property names or paths passed to {@link #compute()}. */
    String[] sources() default {};

    /** Inline expression or {@code this::helper} value computation. */
    String compute() default "";

    /**
     * Explicit local mapper method for the path value.
     *
     * <p>This path-specific hook is kept separate from method-level
     * {@link MapperOptions#using()} preferences. It supports a simple method
     * name on the current mapper and cannot be combined with {@link #compute()}.</p>
     */
    String nestedMapper() default "";

    /** Array-like update behavior for this target path on update methods. */
    ArrayPolicy array() default ArrayPolicy.SET;

    /** Object-like update behavior for this target path on update methods. */
    ObjectPolicy object() default ObjectPolicy.PUT;
}
