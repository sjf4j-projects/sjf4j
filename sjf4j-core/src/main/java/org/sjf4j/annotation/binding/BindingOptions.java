package org.sjf4j.annotation.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-time options for an SJF4J compiled interface.
 *
 * <p>For @CompiledBinder, the interface itself and any explicitly listed
 * registry holders provide compile-time ValueCodec declarations. Static
 * bindings do not require global ValueRegistry registration. Other compiled
 * interface families preserve their existing runtime semantics.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface BindingOptions {

    /** Additional classes containing annotated registration fields. */
    Class<?>[] registries() default {};

    /** Include null-valued object members in generated binding writes. */
    boolean includeNulls() default true;

    /** Compile-time selections for value types with multiple codec formats. */
    CodecMapping[] valueCodecs() default {};
}
