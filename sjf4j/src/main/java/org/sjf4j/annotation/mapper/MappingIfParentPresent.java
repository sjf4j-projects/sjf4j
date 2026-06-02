package org.sjf4j.annotation.mapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Target-path mapping that writes only when the final target parent exists.
 *
 * <p>This source-retained API mirrors the main {@link Mapping} value-selection
 * members for path-aware mapper generation. The {@link #target()} value
 * must be a target path: JSONPath beginning with {@code $} or JSON Pointer
 * beginning with {@code /}. Plain property names are handled by
 * {@link Mapping}; plain dotted names are literal property/key names, not
 * nested target paths.</p>
 *
 * <p>Semantics are PutIfParentPresentByPath-like: if the final parent
 * object/container for the target path is missing, the write is skipped.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface MappingIfParentPresent {
    String target() default "";
    String source() default "";
    String[] sources() default {};
    String compute() default "";
    String nestedMapper() default "";
    ArrayPolicy array() default ArrayPolicy.SET;
    ObjectPolicy object() default ObjectPolicy.PUT;
}
