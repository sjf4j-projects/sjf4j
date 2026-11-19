package org.sjf4j.annotation.binding;

import org.sjf4j.annotation.node.ValueCodecRegistry;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.value.ValueRegistry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes precompiled {@link ValueCodecRegistry} holders visible to the annotation processor.
 *
 * <p>{@link CompiledBinder} and
 * {@link org.sjf4j.annotation.mapping.CompiledMapper @CompiledMapper}
 * use these declarations for compile-time codec resolution. Registry holders
 * in the current compilation are discovered automatically. Other compiled
 * APIs do not currently consume this option.
 *
 * <p>This does not register codecs at runtime; use a
 * {@link org.sjf4j.value.ValueCodecProvider} SPI provider or call
 * {@link ValueRegistry#registerAnnotated(Class)} during initialization.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface BindingOptions {

    /**
     * Additional {@link ValueCodecRegistry} classes or interfaces.
     *
     * <p>Each holder may declare public static final {@link ValueCodec}
     * fields or public static no-argument factory methods returning
     * {@code ValueCodec<V, R>}. Member-level
     * {@link org.sjf4j.annotation.node.NodeValue @NodeValue} annotations
     * optionally select named or default formats.
     * This is not a general-purpose annotation registry.
     */
    Class<?>[] registries() default {};
}
