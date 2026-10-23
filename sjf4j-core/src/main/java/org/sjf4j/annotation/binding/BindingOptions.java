package org.sjf4j.annotation.binding;

import org.sjf4j.value.ValueRegistry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-time options for an SJF4J compiled interface.
 *
 * <p>Registry holder classes may be declared in the current module or an
 * already-compiled dependency. This option only exposes their declarations
 * to the annotation processor. Application initialization is still responsible
 * for runtime registration (for example, via
 * {@link ValueRegistry#registerAnnotated(Class)}).
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
